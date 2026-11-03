import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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

/** Ana: um relato com o voto do Rui, um voto no relato do Rui, uma foto pendente e outra aprovada (com os ficheiros) e uma correção. O Rui: o seu relato e uma foto aprovada. */
async function comunidadeDaAnaEDoRui(): Promise<void> {
  await env.DB.batch([
    env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES ('r-ana', 'desvio', 'CM:1', '736', 'ana', 1, 2), ('r-rui', 'desvio', 'CM:2', '736', 'rui', 1, 2)"),
    env.DB.prepare("INSERT INTO confirmacoes (relato_id, conta_id, voto, em) VALUES ('r-ana', 'ana', 'ainda_esta', 1), ('r-ana', 'rui', 'ainda_esta', 1), ('r-rui', 'ana', 'ja_nao_esta', 1), ('r-rui', 'rui', 'ainda_esta', 1)"),
    env.DB.prepare(
      "INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em) VALUES ('f-pendente', 'CM:3', 'ana', 'pendente.jpg', 'pendente', 1), ('f-aprovada', 'CM:4', 'ana', 'aprovada.jpg', 'aprovada', 1), ('f-rui', 'CM:5', 'rui', 'rui.jpg', 'aprovada', 1)",
    ),
    env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k-ana', 'CM:1', 'ana', 'nome', 'Outro nome', 1)"),
  ]);
  await env.FOTOS_PENDENTES.put("pendente.jpg", "jpeg");
  await env.FOTOS.put("aprovada.jpg", "jpeg");
  await env.FOTOS.put("rui.jpg", "jpeg");
}

async function chavesNoR2(bucket: R2Bucket): Promise<string[]> {
  return (await bucket.list()).objects.map((objeto) => objeto.key);
}

const PURGA: Partial<Env> = { CF_PURGE_TOKEN: "t", CF_ZONA: "z" };

describe("apagar conta com a comunidade", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("apagarContaApagaRelatosFotosR2ELimpaACache", async () => {
    await comunidadeDaAnaEDoRui();
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response('{"success":true}', { status: 200 }));

    const resposta = await pedir("/api/conta", { metodo: "DELETE", token: tokenDaAna, segredos: PURGA });
    expect(resposta.status).toBe(204);

    for (const tabela of ["contas", "relatos", "confirmacoes", "fotos", "correcoes"]) {
      expect(await contar(tabela, "ana"), tabela).toBe(0);
    }
    expect(await chavesNoR2(env.FOTOS_PENDENTES)).toEqual([]);
    expect(await chavesNoR2(env.FOTOS)).toEqual(["rui.jpg"]);
    // Só a aprovada esteve no ar; a pendente nunca passou pela cache.
    expect(fetchFalso).toHaveBeenCalledTimes(1);
    const [endereco, opcoes] = fetchFalso.mock.calls[0];
    expect(String(endereco)).toBe("https://api.cloudflare.com/client/v4/zones/z/purge_cache");
    expect(JSON.parse(String(opcoes?.body))).toEqual({ files: ["https://fotos.pixellusitano.com/aprovada.jpg"] });

    // O que é do Rui fica.
    expect(await contar("relatos", "rui")).toBe(1);
    expect(await contar("fotos", "rui")).toBe(1);
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

  it("r2EmBaixoNaoApagaMetade", async () => {
    await comunidadeDaAnaEDoRui();
    const fetchFalso = vi.spyOn(globalThis, "fetch");
    const r2EmBaixo = {
      delete: async () => {
        throw new Error("R2 em baixo");
      },
    } as unknown as R2Bucket;

    const resposta = await pedir("/api/conta", { metodo: "DELETE", token: tokenDaAna, segredos: { ...PURGA, FOTOS: r2EmBaixo } });
    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ erro: "apagar_falhou" });

    for (const tabela of ["contas", "sessoes", "relatos", "fotos", "correcoes"]) {
      expect(await contar(tabela, "ana"), tabela).toBeGreaterThan(0);
    }
    expect(await chavesNoR2(env.FOTOS)).toContain("aprovada.jpg");
    expect(fetchFalso).not.toHaveBeenCalled();
    // A Ana pode tentar outra vez, e da segunda já vai.
    expect((await pedir("/api/conta", { metodo: "DELETE", token: tokenDaAna })).status).toBe(204);
    expect(await contar("fotos", "ana")).toBe(0);
  });

  it("cacheEmBaixoNaoImpedeApagar", async () => {
    await comunidadeDaAnaEDoRui();
    vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response('{"success":false}', { status: 500 }));

    expect((await pedir("/api/conta", { metodo: "DELETE", token: tokenDaAna, segredos: PURGA })).status).toBe(204);

    for (const tabela of ["contas", "relatos", "confirmacoes", "fotos", "correcoes"]) {
      expect(await contar(tabela, "ana"), tabela).toBe(0);
    }
    expect(await chavesNoR2(env.FOTOS)).toEqual(["rui.jpg"]);
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
