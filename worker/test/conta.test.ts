import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { contaDaSessao, criarSessao } from "../src/sessoes";
import { pedidoComToken, pedir } from "./ajudas";

let tokenDaAna: string;
let tokenDoRui: string;

beforeEach(async () => {
  await env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES ('ana', 'ana@exemplo.pt', 1), ('rui', 'rui@exemplo.pt', 1)").run();
  tokenDaAna = await criarSessao(env, "ana");
  tokenDoRui = await criarSessao(env, "rui");
});

async function contar(tabela: string, contaId: string): Promise<number> {
  const coluna = tabela === "contas" ? "id" : "conta_id";
  const linha = await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${tabela} WHERE ${coluna} = ?`).bind(contaId).first<{ n: number }>();
  return linha!.n;
}

function aceitar(token: string | undefined, versao: unknown): Promise<Response> {
  return pedir("/api/termos", { token, corpo: { versao } });
}

describe("apagar conta", () => {
  it("apagarContaApagaTudo", async () => {
    await criarSessao(env, "ana"); // um segundo telemóvel da Ana
    for (const id of ["ana", "rui"]) {
      await env.DB.prepare("INSERT INTO favoritos (conta_id, chave, tipo, nome, lat, lon, ordem, apagado, revisao) VALUES (?1, 'CM:1', 'PARAGEM', 'Paragem', 38.7, -9.1, 0, 0, 1), (?1, 'casa-1', 'CASA', 'Casa', 38.7, -9.1, 1, 1, 2)")
        .bind(id)
        .run();
      await env.DB.prepare("INSERT INTO aceites_termos (conta_id, versao_termos, aceite_em) VALUES (?, '1', 1)").bind(id).run();
      // As alterações aos favoritos contadas em dois dias, que o Cron ainda não apagou.
      await env.DB.prepare("INSERT INTO contadores (dia, nome, quantos) VALUES ('2030-01-01', ?1, 3), ('2030-01-02', ?1, 1)").bind(`favoritos:${id}`).run();
    }
    expect(await contar("sessoes", "ana")).toBe(2);

    const resposta = await pedir("/api/conta", { metodo: "DELETE", token: tokenDaAna });
    expect(resposta.status).toBe(204);

    for (const tabela of ["contas", "sessoes", "favoritos", "aceites_termos"]) {
      expect(await contar(tabela, "ana"), tabela).toBe(0);
    }
    // A conta de outra pessoa não se mexe.
    expect(await contar("contas", "rui")).toBe(1);
    expect(await contar("sessoes", "rui")).toBe(1);
    expect(await contar("favoritos", "rui")).toBe(2);
    expect(await contar("aceites_termos", "rui")).toBe(1);
    const { results } = await env.DB.prepare("SELECT nome FROM contadores ORDER BY dia").all<{ nome: string }>();
    expect(results.map((linha) => linha.nome)).toEqual(["favoritos:rui", "favoritos:rui"]);

    // O token da conta apagada deixa de servir, em todas as rotas.
    expect(await contaDaSessao(pedidoComToken(tokenDaAna), env)).toBeNull();
    expect((await pedir("/api/sincronizar", { token: tokenDaAna, corpo: { desde: 0, alteracoes: [] } })).status).toBe(401);
    expect((await pedir("/api/conta", { metodo: "DELETE", token: tokenDaAna })).status).toBe(401);
    expect(await contaDaSessao(pedidoComToken(tokenDoRui), env)).toEqual({ contaId: "rui" });
  });

  it("apagarContaSemSessaoDa401", async () => {
    for (const token of [undefined, "nao-existe"]) {
      const resposta = await pedir("/api/conta", { metodo: "DELETE", token });
      expect(resposta.status).toBe(401);
      expect(await resposta.json()).toEqual({ erro: "sem_sessao" });
    }
    expect(await contar("contas", "ana")).toBe(1);
    expect(await contar("contas", "rui")).toBe(1);
  });

  it("apagarContaSoComDelete", async () => {
    for (const metodo of ["GET", "POST"]) {
      expect((await pedir("/api/conta", { metodo, token: tokenDaAna })).status, metodo).toBe(404);
    }
    expect(await contar("contas", "ana")).toBe(1);
  });
});

/** Ana: um relato com o voto do Rui, um voto no relato do Rui e uma correção. O Rui: o seu relato. */
async function comunidadeDaAnaEDoRui(): Promise<void> {
  await env.DB.batch([
    env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES ('r-ana', 'desvio', 'CM:1', '736', 'ana', 1, 2), ('r-rui', 'desvio', 'CM:2', '736', 'rui', 1, 2)"),
    env.DB.prepare("INSERT INTO confirmacoes (relato_id, conta_id, voto, em) VALUES ('r-ana', 'ana', 'ainda_esta', 1), ('r-ana', 'rui', 'ainda_esta', 1), ('r-rui', 'ana', 'ja_nao_esta', 1), ('r-rui', 'rui', 'ainda_esta', 1)"),
    env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k-ana', 'CM:1', 'ana', 'nome', 'Outro nome', 1)"),
  ]);
}

describe("apagar conta com a comunidade", () => {
  it("apagarContaApagaRelatosECorrecoes", async () => {
    await comunidadeDaAnaEDoRui();

    const resposta = await pedir("/api/conta", { metodo: "DELETE", token: tokenDaAna });
    expect(resposta.status).toBe(204);

    for (const tabela of ["contas", "relatos", "confirmacoes", "correcoes"]) {
      expect(await contar(tabela, "ana"), tabela).toBe(0);
    }

    // O que é do Rui fica.
    expect(await contar("relatos", "rui")).toBe(1);
    expect(await env.DB.prepare("SELECT conta_id FROM confirmacoes").all()).toMatchObject({ results: [{ conta_id: "rui" }] });
  });

  it("apagarContaApagaOsVotosDosOutrosNosSeusRelatos", async () => {
    await comunidadeDaAnaEDoRui();

    expect((await pedir("/api/conta", { metodo: "DELETE", token: tokenDaAna })).status).toBe(204);

    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM confirmacoes WHERE relato_id = 'r-ana'").first<number>("n")).toBe(0);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM confirmacoes WHERE relato_id = 'r-rui' AND conta_id = 'rui'").first<number>("n")).toBe(1);
    expect(await contar("contas", "rui")).toBe(1);
  });

  it("apagarContaGuardaDenunciasEBloqueios", async () => {
    await comunidadeDaAnaEDoRui();
    await env.DB.batch([
      env.DB.prepare("INSERT INTO denuncias (id, conteudo, motivo, nome, email, email_hash, criada_em) VALUES ('d1', 'relato:r-rui', 'falso', 'Ana', 'ana@exemplo.pt', 'h-ana', 1)"),
      env.DB.prepare("INSERT INTO decisoes (id, acao, conteudo, email_hash, motivo, regra, criada_em) VALUES ('x1', 'bloquear', 'relato:r-ana', 'h-ana', 'm', 'r', 1)"),
      env.DB.prepare("INSERT INTO bloqueios (email_hash, motivo, criado_em) VALUES ('h-ana', 'm', 1)"),
    ]);
    const antes = await Promise.all(["denuncias", "decisoes", "bloqueios"].map((tabela) => env.DB.prepare(`SELECT * FROM ${tabela}`).all()));

    expect((await pedir("/api/conta", { metodo: "DELETE", token: tokenDaAna })).status).toBe(204);

    const depois = await Promise.all(["denuncias", "decisoes", "bloqueios"].map((tabela) => env.DB.prepare(`SELECT * FROM ${tabela}`).all()));
    expect(depois.map((linhas) => linhas.results)).toEqual(antes.map((linhas) => linhas.results));
    expect(await contar("contas", "ana")).toBe(0);
  });
});

describe("migração das contas só de código", () => {
  it("migracaoApagaAsContasSoDeCodigo", async () => {
    // A Ana e o Rui do beforeEach também são só de código.
    await env.DB.batch([
      env.DB.prepare("INSERT INTO contas (id, email, google_sub, criada_em) VALUES ('codigo', 'c@exemplo.pt', NULL, 1), ('gmail', 'g@gmail.com', 'sub-gmail', 1), ('google', 'o@exemplo.pt', 'sub-google', 1)"),
      env.DB.prepare("INSERT INTO aceites_termos (conta_id, versao_termos, aceite_em) VALUES ('codigo', '1', 1), ('google', '1', 1)"),
      env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES ('r-codigo', 'desvio', 'CM:1', '736', 'codigo', 1, 2), ('r-google', 'desvio', 'CM:2', '736', 'google', 1, 2)"),
      env.DB.prepare("INSERT INTO confirmacoes (relato_id, conta_id, voto, em) VALUES ('r-codigo', 'google', 'ainda_esta', 1), ('r-google', 'codigo', 'ja_nao_esta', 1), ('r-google', 'google', 'ainda_esta', 1)"),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k-codigo', 'CM:1', 'codigo', 'nome', 'Outro nome', 1), ('k-google', 'CM:2', 'google', 'nome', 'Outro nome', 1)"),
      env.DB.prepare("INSERT INTO codigos (email, hash, sal, criado_em) VALUES ('c@exemplo.pt', 'hash', 'sal', 1)"),
      env.DB.prepare("INSERT INTO envios_email (dia, quantos) VALUES ('2026-11-12', 1)"),
    ]);
    for (const id of ["codigo", "gmail", "google"]) {
      await env.DB.prepare("INSERT INTO favoritos (conta_id, chave, tipo, nome, lat, lon, ordem, revisao) VALUES (?, 'CM:1', 'PARAGEM', 'Paragem', 38.7, -9.1, 0, 1)").bind(id).run();
      await criarSessao(env, id);
    }

    const migracao = env.TEST_MIGRATIONS.find((m) => m.name.startsWith("0003"))!;
    for (const consulta of migracao.queries) await env.DB.prepare(consulta).run();

    const { results } = await env.DB.prepare("SELECT id FROM contas ORDER BY id").all<{ id: string }>();
    expect(results.map((linha) => linha.id)).toEqual(["gmail", "google"]);
    for (const tabela of ["favoritos", "sessoes", "aceites_termos", "relatos", "correcoes", "confirmacoes"]) {
      expect(await contar(tabela, "codigo"), tabela).toBe(0);
    }
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM confirmacoes WHERE relato_id = 'r-codigo'").first<number>("n")).toBe(0);
    for (const id of ["gmail", "google"]) {
      expect(await contar("favoritos", id), id).toBe(1);
      expect(await contar("sessoes", id), id).toBe(1);
    }
    // O voto da conta apagada sai; o relato, o voto, os termos e a correção da conta Google ficam.
    expect(await env.DB.prepare("SELECT conta_id FROM confirmacoes WHERE relato_id = 'r-google'").all()).toMatchObject({ results: [{ conta_id: "google" }] });
    expect(await env.DB.prepare("SELECT id FROM relatos").all()).toMatchObject({ results: [{ id: "r-google" }] });
    for (const tabela of ["aceites_termos", "correcoes", "confirmacoes"]) expect(await contar(tabela, "google"), tabela).toBe(1);
    // Já não se pedem códigos: os pedidos (com o email em claro) e a contagem dos envios saem todos.
    for (const tabela of ["codigos", "envios_email"]) {
      expect(await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${tabela}`).first<number>("n"), tabela).toBe(0);
    }
  });
});

describe("termos", () => {
  it("termosGuardamAVersao", async () => {
    const antes = Date.now();
    const resposta = await aceitar(tokenDaAna, "2026-11-02");
    expect(resposta.status).toBe(204);

    const linha = await env.DB.prepare("SELECT conta_id, versao_termos, aceite_em FROM aceites_termos").first<{ conta_id: string; versao_termos: string; aceite_em: number }>();
    expect(linha).toMatchObject({ conta_id: "ana", versao_termos: "2026-11-02" });
    expect(linha!.aceite_em).toBeGreaterThanOrEqual(antes);
    expect(linha!.aceite_em).toBeLessThanOrEqual(Date.now());
  });

  it("aceitarOutraVezGuardaOPrimeiroMomento", async () => {
    await aceitar(tokenDaAna, "1");
    await env.DB.prepare("UPDATE aceites_termos SET aceite_em = 5").run();
    expect((await aceitar(tokenDaAna, "1")).status).toBe(204);
    expect((await aceitar(tokenDaAna, "2")).status).toBe(204);

    const { results } = await env.DB.prepare("SELECT versao_termos, aceite_em FROM aceites_termos ORDER BY versao_termos").all();
    expect(results).toHaveLength(2);
    expect(results[0]).toEqual({ versao_termos: "1", aceite_em: 5 });
  });

  it("termosSemSessaoDa401", async () => {
    const resposta = await aceitar(undefined, "1");
    expect(resposta.status).toBe(401);
    expect(await resposta.json()).toEqual({ erro: "sem_sessao" });
    expect((await aceitar("nao-existe", "1")).status).toBe(401);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM aceites_termos").first()).toEqual({ n: 0 });
  });

  it("versaoInvalidaDa400", async () => {
    for (const versao of [undefined, "", "   ", 1, null, ["1"], "x".repeat(41)]) {
      const resposta = await aceitar(tokenDaAna, versao);
      expect(resposta.status, String(versao)).toBe(400);
      expect(await resposta.json(), String(versao)).toEqual({ erro: "pedido_invalido" });
    }
    expect((await pedir("/api/termos", { token: tokenDaAna, corpo: "{" })).status).toBe(400);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM aceites_termos").first()).toEqual({ n: 0 });
  });
});
