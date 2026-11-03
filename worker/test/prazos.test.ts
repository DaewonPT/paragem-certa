import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { limparCache } from "../src/cache";
import { correrCron } from "./ajudas";

const DIA = 24 * 60 * 60_000;
// Longe da hora verdadeira: se o Cron usasse o Date.now() em vez da hora do controlador, nada passaria do prazo.
const AGORA = Date.UTC(2030, 0, 10, 3, 0);

beforeEach(async () => {
  await env.DB.batch([
    env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES ('ana', 'ana@exemplo.pt', 1), ('rui', 'rui@exemplo.pt', 1)"),
  ]);
});

afterEach(() => {
  vi.restoreAllMocks();
});

async function contar(sql: string, ...valores: unknown[]): Promise<number> {
  return (await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${sql}`).bind(...valores).first<number>("n"))!;
}

function inserirRelato(id: string, paragem: string, expiraEm: number): Promise<unknown> {
  return env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES (?, 'desvio', ?, '736', 'ana', ?, ?)")
    .bind(id, paragem, expiraEm - 3 * 60 * 60_000, expiraEm)
    .run();
}

function inserirFoto(id: string, paragem: string, estado: string, chave: string | null, criadaEm: number): Promise<unknown> {
  return env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em) VALUES (?, ?, 'ana', ?, ?, ?)")
    .bind(id, paragem, chave, estado, criadaEm)
    .run();
}

async function noBucket(bucket: R2Bucket, chave: string): Promise<boolean> {
  return (await bucket.head(chave)) !== null;
}

describe("prazos no Cron", () => {
  it("cronApagaRelatosExpiradosHa30DiasComConfirmacoes", async () => {
    await inserirRelato("velho", "CM:1", AGORA - 30 * DIA - 1);
    await inserirRelato("recente", "CM:2", AGORA - 29 * DIA);
    await env.DB.prepare("INSERT INTO confirmacoes (relato_id, conta_id, voto, em) VALUES ('velho', 'ana', 'ainda_esta', 1), ('velho', 'rui', 'ja_nao_esta', 1), ('recente', 'rui', 'ainda_esta', 1)").run();

    await correrCron({ agora: AGORA });

    expect(await contar("relatos WHERE id = 'velho'")).toBe(0);
    expect(await contar("confirmacoes WHERE relato_id = 'velho'")).toBe(0);
    expect(await contar("relatos WHERE id = 'recente'")).toBe(1);
    expect(await contar("confirmacoes WHERE relato_id = 'recente'")).toBe(1);
  });

  it("cronApagaFotosPorRever30DiasNoR2", async () => {
    await inserirFoto("velha", "CM:1", "pendente", "velha.jpg", AGORA - 30 * DIA - 1);
    await inserirFoto("recente", "CM:2", "pendente", "recente.jpg", AGORA - 29 * DIA);
    await inserirFoto("aprovada", "CM:3", "aprovada", "aprovada.jpg", AGORA - 400 * DIA);
    await env.FOTOS_PENDENTES.put("velha.jpg", "jpeg");
    await env.FOTOS_PENDENTES.put("recente.jpg", "jpeg");
    // Uma aprovação a meio já tinha copiado a velha para o bucket público: também sai de lá.
    await env.FOTOS.put("velha.jpg", "jpeg");
    await env.FOTOS.put("aprovada.jpg", "jpeg");

    await correrCron({ agora: AGORA });

    expect(await contar("fotos WHERE id = 'velha'")).toBe(0);
    expect(await noBucket(env.FOTOS_PENDENTES, "velha.jpg")).toBe(false);
    expect(await noBucket(env.FOTOS, "velha.jpg")).toBe(false);
    expect(await contar("fotos WHERE id IN ('recente', 'aprovada')")).toBe(2);
    expect(await noBucket(env.FOTOS_PENDENTES, "recente.jpg")).toBe(true);
    expect(await noBucket(env.FOTOS, "aprovada.jpg")).toBe(true);
  });

  it("cronApagaDenunciasDecisoesEBloqueiosAos12Meses", async () => {
    const velho = AGORA - 366 * DIA;
    const recente = AGORA - 364 * DIA;
    await env.DB.batch([
      env.DB.prepare("INSERT INTO denuncias (id, conteudo, motivo, email_hash, criada_em) VALUES ('d1', 'relato:1', 'falso', 'h1', ?), ('d2', 'relato:2', 'falso', 'h1', ?)").bind(velho, recente),
      env.DB.prepare("INSERT INTO decisoes (id, acao, email_hash, motivo, regra, criada_em) VALUES ('x1', 'apagar', 'h1', 'm', 'r', ?), ('x2', 'apagar', 'h1', 'm', 'r', ?)").bind(velho, recente),
      env.DB.prepare("INSERT INTO bloqueios (email_hash, motivo, criado_em) VALUES ('h1', 'm', ?), ('h2', 'm', ?)").bind(velho, recente),
    ]);

    await correrCron({ agora: AGORA });

    expect(await contar("denuncias WHERE id = 'd1'")).toBe(0);
    expect(await contar("decisoes WHERE id = 'x1'")).toBe(0);
    expect(await contar("bloqueios WHERE email_hash = 'h1'")).toBe(0);
    expect(await contar("denuncias WHERE id = 'd2'")).toBe(1);
    expect(await contar("decisoes WHERE id = 'x2'")).toBe(1);
    expect(await contar("bloqueios WHERE email_hash = 'h2'")).toBe(1);
  });

  it("cronApagaFotosRejeitadasAoFimDeUmDia", async () => {
    await inserirFoto("velha", "CM:1", "rejeitada", null, AGORA - DIA - 1);
    await inserirFoto("recente", "CM:1", "rejeitada", null, AGORA - 23 * 60 * 60_000);

    await correrCron({ agora: AGORA });

    expect(await contar("fotos WHERE id = 'velha'")).toBe(0);
    expect(await contar("fotos WHERE id = 'recente'")).toBe(1);
  });

  it("cronApagaEmailsPorEnviarAos30Dias", async () => {
    await env.DB.prepare("INSERT INTO emails_por_enviar (id, para, assunto, texto, criado_em) VALUES ('e1', 'a@exemplo.pt', 'a', 't', ?), ('e2', 'a@exemplo.pt', 'a', 't', ?)")
      .bind(AGORA - 31 * DIA, AGORA - 29 * DIA)
      .run();

    await correrCron({ agora: AGORA });

    expect(await contar("emails_por_enviar WHERE id = 'e1'")).toBe(0);
    expect(await contar("emails_por_enviar WHERE id = 'e2'")).toBe(1);
  });

  it("cronApagaEmLotesAteNaoSobrarNada", async () => {
    // Mais do que um lote de 500: o Cron dá as voltas que precisar (até 10).
    await env.DB.prepare(
      `WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1234)
       INSERT INTO emails_por_enviar (id, para, assunto, texto, criado_em) SELECT 'e' || i, 'a@exemplo.pt', 'a', 't', ? FROM n`,
    )
      .bind(AGORA - 31 * DIA)
      .run();

    await correrCron({ agora: AGORA });

    expect(await contar("emails_por_enviar")).toBe(0);
  });

  it("cronApagaContadoresVelhos", async () => {
    // AGORA é 10/01/2030: ficam hoje, ontem e anteontem.
    await env.DB.batch([
      env.DB.prepare("INSERT INTO contadores (dia, nome, quantos) VALUES ('2030-01-07', 'fotos', 1), ('2030-01-07', 'emails', 1), ('2030-01-08', 'fotos', 1), ('2030-01-09', 'emails', 1), ('2030-01-10', 'fotos', 1)"),
      env.DB.prepare("INSERT INTO envios_email (dia, quantos) VALUES ('2030-01-07', 1), ('2030-01-08', 1), ('2030-01-10', 1)"),
    ]);

    await correrCron({ agora: AGORA });

    const contadores = await env.DB.prepare("SELECT dia FROM contadores ORDER BY dia").all<{ dia: string }>();
    expect(contadores.results.map((linha) => linha.dia)).toEqual(["2030-01-08", "2030-01-09", "2030-01-10"]);
    const envios = await env.DB.prepare("SELECT dia FROM envios_email ORDER BY dia").all<{ dia: string }>();
    expect(envios.results.map((linha) => linha.dia)).toEqual(["2030-01-08", "2030-01-10"]);
  });
});

describe("limpar a cache", () => {
  it("limparCacheMandaAsUrlsEmGruposDe100", async () => {
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response('{"success":true}', { status: 200 }));
    const urls = Array.from({ length: 150 }, (_, i) => `https://fotos.pixellusitano.com/${i}.jpg`);

    expect(await limparCache({ ...env, CF_PURGE_TOKEN: "t", CF_ZONA: "z" } as Env, urls)).toBe(true);

    expect(fetchFalso).toHaveBeenCalledTimes(2);
    const grupos = fetchFalso.mock.calls.map(([endereco, opcoes]) => {
      expect(String(endereco)).toBe("https://api.cloudflare.com/client/v4/zones/z/purge_cache");
      expect(opcoes?.method).toBe("POST");
      expect(new Headers(opcoes?.headers).get("authorization")).toBe("Bearer t");
      return (JSON.parse(String(opcoes?.body)) as { files: string[] }).files;
    });
    expect(grupos).toEqual([urls.slice(0, 100), urls.slice(100)]);
  });

  it("limparCacheSemTokenOuComFalha", async () => {
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response("{}", { status: 500 }));
    const url = ["https://fotos.pixellusitano.com/a.jpg"];

    // Sem o token (wrangler dev, testes) ou sem nada para limpar não se pede nada.
    expect(await limparCache({ ...env, CF_PURGE_TOKEN: undefined } as unknown as Env, url)).toBe(true);
    expect(await limparCache({ ...env, CF_PURGE_TOKEN: "t", CF_ZONA: "z" } as Env, [])).toBe(true);
    expect(fetchFalso).not.toHaveBeenCalled();

    expect(await limparCache({ ...env, CF_PURGE_TOKEN: "t", CF_ZONA: "z" } as Env, url)).toBe(false);
    fetchFalso.mockRejectedValue(new Error("sem rede"));
    expect(await limparCache({ ...env, CF_PURGE_TOKEN: "t", CF_ZONA: "z" } as Env, url)).toBe(false);
  });
});
