import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { contaDaSessao, criarSessao } from "../src/sessoes";
import { correrCron, pedidoComToken, pedir, sha256 } from "./ajudas";

const MINUTO = 60_000;
const HORA = 60 * MINUTO;
const DIA = 24 * HORA;

beforeEach(async () => {
  await env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES ('c1', 'ana@exemplo.pt', 1)").run();
});

async function linhaDaSessao(): Promise<{ hash: string; criada_em: number; usada_em: number }> {
  const linha = await env.DB.prepare("SELECT hash, criada_em, usada_em FROM sessoes").first<{ hash: string; criada_em: number; usada_em: number }>();
  return linha!;
}

describe("sessões", () => {
  it("sessaoGuardaSoOHash", async () => {
    const token = await criarSessao(env, "c1");
    expect(token).toMatch(/^[A-Za-z0-9_-]{43}$/); // 32 bytes em base64url
    const { results } = await env.DB.prepare("SELECT * FROM sessoes").all();
    expect(results).toHaveLength(1);
    expect(results[0].hash).toBe(await sha256(token));
    expect(JSON.stringify(results)).not.toContain(token);
    expect(await criarSessao(env, "c1")).not.toBe(token);
  });

  it("sessaoValidaDaAConta", async () => {
    const token = await criarSessao(env, "c1");
    expect(await contaDaSessao(pedidoComToken(token), env)).toEqual({ contaId: "c1" });
  });

  it("semTokenOuTokenDesconhecidoNaoServe", async () => {
    await criarSessao(env, "c1");
    expect(await contaDaSessao(pedidoComToken(), env)).toBeNull();
    expect(await contaDaSessao(pedidoComToken("nao-existe"), env)).toBeNull();
    expect(await contaDaSessao(new Request("https://exemplo.test/", { headers: { authorization: "Basic abc" } }), env)).toBeNull();
    // O hash em si não é o token: quem lesse a base de dados não consegue entrar com ele.
    expect(await contaDaSessao(pedidoComToken((await linhaDaSessao()).hash), env)).toBeNull();
  });

  it("sessaoSemUso60DiasMorre", async () => {
    const token = await criarSessao(env, "c1");
    await env.DB.prepare("UPDATE sessoes SET usada_em = ?").bind(Date.now() - 60 * DIA - MINUTO).run();
    expect(await contaDaSessao(pedidoComToken(token), env)).toBeNull();

    await env.DB.prepare("UPDATE sessoes SET usada_em = ?").bind(Date.now() - 59 * DIA).run();
    expect(await contaDaSessao(pedidoComToken(token), env)).toEqual({ contaId: "c1" });
  });

  it("sessaoComMais180DiasMorreMesmoUsada", async () => {
    const token = await criarSessao(env, "c1");
    await env.DB.prepare("UPDATE sessoes SET criada_em = ?").bind(Date.now() - 180 * DIA - MINUTO).run();
    expect(await contaDaSessao(pedidoComToken(token), env)).toBeNull();

    await env.DB.prepare("UPDATE sessoes SET criada_em = ?").bind(Date.now() - 179 * DIA).run();
    expect(await contaDaSessao(pedidoComToken(token), env)).toEqual({ contaId: "c1" });
  });

  it("usadaEmSoAtualizaUmaVezPorHora", async () => {
    const token = await criarSessao(env, "c1");
    const haMeiaHora = Date.now() - 30 * MINUTO;
    await env.DB.prepare("UPDATE sessoes SET usada_em = ?").bind(haMeiaHora).run();
    await contaDaSessao(pedidoComToken(token), env);
    expect((await linhaDaSessao()).usada_em).toBe(haMeiaHora);

    await env.DB.prepare("UPDATE sessoes SET usada_em = ?").bind(Date.now() - 2 * HORA).run();
    await contaDaSessao(pedidoComToken(token), env);
    expect((await linhaDaSessao()).usada_em).toBeGreaterThan(Date.now() - MINUTO);
  });

  it("sairApagaASessao", async () => {
    const token = await criarSessao(env, "c1");
    const outra = await criarSessao(env, "c1");
    const resposta = await pedir("/api/auth/sair", { token });
    expect(resposta.status).toBe(204);
    expect(await contaDaSessao(pedidoComToken(token), env)).toBeNull();
    // Só sai este telemóvel: as outras sessões da conta continuam.
    expect(await contaDaSessao(pedidoComToken(outra), env)).toEqual({ contaId: "c1" });
    const { n } = (await env.DB.prepare("SELECT COUNT(*) AS n FROM sessoes").first<{ n: number }>())!;
    expect(n).toBe(1);
  });

  it("sairSemSessaoTambemDa204", async () => {
    expect((await pedir("/api/auth/sair")).status).toBe(204);
    expect((await pedir("/api/auth/sair", { token: "ja-nao-existe" })).status).toBe(204);
  });

  it("cronLimpaOQueExpirou", async () => {
    const agora = Date.now();
    const velho = agora - 11 * MINUTO;
    const recente = agora - MINUTO;
    await env.DB.batch([
      env.DB.prepare("INSERT INTO codigos (email, hash, sal, criado_em) VALUES ('velho@exemplo.pt', 'h', 's', ?)").bind(velho),
      env.DB.prepare("INSERT INTO codigos (email, hash, sal, criado_em) VALUES ('novo@exemplo.pt', 'h', 's', ?)").bind(recente),
      env.DB.prepare("INSERT INTO nonces (valor, criado_em) VALUES ('velho', ?)").bind(velho),
      env.DB.prepare("INSERT INTO nonces (valor, criado_em) VALUES ('novo', ?)").bind(recente),
      env.DB.prepare("INSERT INTO sessoes (hash, conta_id, criada_em, usada_em) VALUES ('sem-uso', 'c1', ?, ?)").bind(agora - 100 * DIA, agora - 61 * DIA),
      env.DB.prepare("INSERT INTO sessoes (hash, conta_id, criada_em, usada_em) VALUES ('muito-velha', 'c1', ?, ?)").bind(agora - 181 * DIA, agora - MINUTO),
      env.DB.prepare("INSERT INTO sessoes (hash, conta_id, criada_em, usada_em) VALUES ('viva', 'c1', ?, ?)").bind(agora - 100 * DIA, agora - 59 * DIA),
    ]);

    await correrCron();

    const emails = (await env.DB.prepare("SELECT email FROM codigos").all<{ email: string }>()).results.map((linha) => linha.email);
    const nonces = (await env.DB.prepare("SELECT valor FROM nonces").all<{ valor: string }>()).results.map((linha) => linha.valor);
    const sessoes = (await env.DB.prepare("SELECT hash FROM sessoes").all<{ hash: string }>()).results.map((linha) => linha.hash);
    expect(emails).toEqual(["novo@exemplo.pt"]);
    expect(nonces).toEqual(["novo"]);
    expect(sessoes).toEqual(["viva"]);
  });
});
