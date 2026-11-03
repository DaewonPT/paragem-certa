import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { describe, expect, it, vi } from "vitest";
import config from "../public/dados/config.json";
import worker from "../src/index";
import { contaDaSessao } from "../src/sessoes";
import { criarConta, LIGADO, limitador, pedidoComToken, pedir as chamar } from "./ajudas";

const servidor: Required<Pick<ExportedHandler<Env>, "fetch">> = worker;

/** Chama o Worker como a Cloudflare chama; `segredos` sobrepõe os segredos do ambiente de teste. */
async function pedir(caminho: string, segredos: Partial<Env> = {}): Promise<Response> {
  const ctx = createExecutionContext();
  const resposta = await servidor.fetch(new Request(`https://exemplo.test${caminho}`), { ...env, ...segredos }, ctx);
  await waitOnExecutionContext(ctx);
  return resposta;
}

describe("base do servidor", () => {
  it("saudeResponde", async () => {
    const resposta = await pedir("/api/saude");
    expect(resposta.status).toBe(200);
    expect(resposta.headers.get("content-type")).toContain("application/json");
    expect(await resposta.json()).toEqual({ ok: true });
  });

  it("apiDesconhecidaDa404", async () => {
    const resposta = await pedir("/api/nao-existe");
    expect(resposta.status).toBe(404);
    expect(await resposta.json()).toEqual({ erro: "nao_existe" });
  });

  it("privacidadeTemOResponsavel", async () => {
    const resposta = await pedir("/privacidade", { RESPONSAVEL: "Nome de Teste" });
    expect(resposta.status).toBe(200);
    expect(resposta.headers.get("content-type")).toContain("text/html");
    const pagina = await resposta.text();
    expect(pagina).toContain("Nome de Teste");
    expect(pagina).not.toContain("{{RESPONSAVEL}}");
  });

  it("responsavelEscapado", async () => {
    // O "$&" apanha quem troca o texto com uma cadeia em vez de uma função.
    const resposta = await pedir("/privacidade", { RESPONSAVEL: "<b>Nome</b> & $&" });
    const pagina = await resposta.text();
    expect(pagina).toContain("&lt;b&gt;Nome&lt;/b&gt; &amp; $&amp;");
    expect(pagina).not.toContain("<b>Nome</b>");
  });

  it("semResponsavelDa503", async () => {
    expect((await pedir("/privacidade", { RESPONSAVEL: undefined })).status).toBe(503);
    expect((await pedir("/privacidade", { RESPONSAVEL: "   " })).status).toBe(503);
  });

  it("migracoesCriamAsTabelas", async () => {
    const { results } = await env.DB.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").all<{ name: string }>();
    const tabelas = results.map((linha) => linha.name);
    for (const tabela of [
      "contas", "sessoes", "codigos", "nonces", "favoritos", "aceites_termos", "envios_email",
      "relatos", "confirmacoes", "fotos", "correcoes", "denuncias", "decisoes", "bloqueios", "emails_por_enviar", "contadores",
    ]) {
      expect(tabelas).toContain(tabela);
    }
  });

  it("apagarContaApagaEmCascata", async () => {
    await env.DB.batch([
      env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES ('c1', 'a@exemplo.pt', 1)"),
      env.DB.prepare("INSERT INTO sessoes (hash, conta_id, criada_em, usada_em) VALUES ('h1', 'c1', 1, 1)"),
      env.DB.prepare("INSERT INTO favoritos (conta_id, chave, tipo, nome, lat, lon, ordem, revisao) VALUES ('c1', 'CM:060419', 'PARAGEM', 'Paragem', 38.7, -9.1, 0, 1)"),
      env.DB.prepare("INSERT INTO aceites_termos (conta_id, versao_termos, aceite_em) VALUES ('c1', '1', 1)"),
      env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES ('r1', 'desvio', 'CM:060419', '736', 'c1', 1, 2)"),
      env.DB.prepare("INSERT INTO confirmacoes (relato_id, conta_id, voto, em) VALUES ('r1', 'c1', 'ainda_esta', 1)"),
      env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, criada_em) VALUES ('f1', 'CM:060419', 'c1', 'abc.jpg', 1)"),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k1', 'CM:060419', 'c1', 'nome', 'Outro nome', 1)"),
    ]);
    await env.DB.prepare("DELETE FROM contas WHERE id = 'c1'").run();
    for (const tabela of ["sessoes", "favoritos", "aceites_termos", "relatos", "confirmacoes", "fotos", "correcoes"]) {
      const linha = await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${tabela}`).first<{ n: number }>();
      expect(linha?.n, tabela).toBe(0);
    }
  });

  it("denunciasDecisoesEBloqueiosNaoDependemDasContas", async () => {
    // Sem chaves estrangeiras: apagar a conta não pode levar o registo das decisões (DSA) nem o bloqueio.
    for (const t of ["denuncias", "decisoes", "bloqueios"]) {
      expect((await env.DB.prepare(`PRAGMA table_info(${t})`).all()).results.length, `${t} existe`).toBeGreaterThan(0);
      expect((await env.DB.prepare(`PRAGMA foreign_key_list(${t})`).all()).results, t).toEqual([]);
    }
  });

  it("soUmRelatoAtivoPorParagemTipoELinha", async () => {
    await env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES ('c1', 'a@exemplo.pt', 1)").run();
    const inserir = (id: string, estado: string) =>
      env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em, estado) VALUES (?, 'desvio', 'CM:060419', '736', 'c1', 1, 2, ?)")
        .bind(id, estado)
        .run();
    await inserir("r1", "ativo");
    await expect(inserir("r2", "ativo")).rejects.toThrow(/UNIQUE/);
    await env.DB.prepare("UPDATE relatos SET estado = 'terminado' WHERE id = 'r1'").run();
    await inserir("r2", "ativo");
    // Outra linha, ou outro tipo, é outro relato.
    await env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES ('r3', 'desvio', 'CM:060419', '15E', 'c1', 1, 2)").run();
    await env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, conta_id, criado_em, expira_em) VALUES ('r4', 'elevador', 'CM:060419', 'c1', 1, 2)").run();
  });

  it("fotoRejeitadaNaoOcupaAParagem", async () => {
    await env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES ('c1', 'a@exemplo.pt', 1)").run();
    await env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em) VALUES ('f1', 'CM:060419', 'c1', NULL, 'rejeitada', 1)").run();
    await env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em) VALUES ('f2', 'CM:060419', 'c1', 'a.jpg', 'pendente', 2)").run();
    await expect(
      env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em) VALUES ('f3', 'CM:060419', 'c1', 'b.jpg', 'pendente', 3)").run(),
    ).rejects.toThrow(/UNIQUE/);
  });

  it("d1NoLimiteDa503EmPausa", async () => {
    // O texto da Cloudflare quando passa o limite grátis do dia; o contaDaSessao é o primeiro a tocar no D1.
    const d1Cheio = {
      prepare() {
        throw new Error("D1_ERROR: Your account has exceeded D1's free tier daily row read limit. Please upgrade to Workers Paid.");
      },
    } as unknown as D1Database;
    const resposta = await chamar("/api/sincronizar", { token: "x", corpo: { desde: 0, alteracoes: [] }, segredos: { DB: d1Cheio } });
    expect(resposta.status).toBe(503);
    expect(await resposta.json()).toEqual({ erro: "em_pausa" });
  });

  it("outroErroDoD1SobeComoAntes", async () => {
    const d1Avariado = {
      prepare() {
        throw new Error("D1_ERROR: outra coisa");
      },
    } as unknown as D1Database;
    await expect(chamar("/api/sincronizar", { token: "x", corpo: { desde: 0, alteracoes: [] }, segredos: { DB: d1Avariado } })).rejects.toThrow("outra coisa");
  });

  // Estes dois correm um a seguir ao outro: o segundo prova que o beforeEach de apply-migrations.ts limpou o que o primeiro escreveu.
  it("baseDeTesteComecaVaziaEmCadaTeste", async () => {
    await env.DB.prepare("INSERT INTO nonces (valor, criado_em) VALUES ('x', 1)").run();
  });

  it("baseDeTesteNaoGuardaOQueOTesteAnteriorEscreveu", async () => {
    const linha = await env.DB.prepare("SELECT COUNT(*) AS n FROM nonces").first<{ n: number }>();
    expect(linha?.n).toBe(0);
  });

  it("configTemOsInterruptoresDesligados", () => {
    // A app traz uma cópia deste ficheiro (etapa 2, Task 6): as funções só se ligam quando o servidor estiver publicado.
    expect(config.versao).toBe(1);
    expect(config.interruptores).toEqual({ conta: false, comunidade: false, fotos: false, correcoes: false });
    expect(config.emails.codigosPorDia).toBe(70);
    expect(config.canaisReclamacao).toHaveLength(5);
    for (const canal of config.canaisReclamacao) {
      expect(canal.nome.length).toBeGreaterThan(0);
      expect(canal.url).toMatch(/^https:\/\//);
    }
  });

  // Estes dois também correm um a seguir ao outro: o reset() do apply-migrations.ts tem de esvaziar os dois buckets de R2.
  it("r2DasFotosGuardaELe", async () => {
    await env.FOTOS_PENDENTES.put("a.jpg", new Uint8Array([1, 2, 3]), { httpMetadata: { contentType: "image/jpeg" } });
    await env.FOTOS.put("b.jpg", new Uint8Array([4]));
    const lido = await env.FOTOS_PENDENTES.get("a.jpg");
    expect(new Uint8Array(await lido!.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
    expect(lido!.httpMetadata?.contentType).toBe("image/jpeg");
    expect(await env.FOTOS.get("a.jpg")).toBeNull();
  });

  it("r2DasFotosComecaVazioEmCadaTeste", async () => {
    expect((await env.FOTOS_PENDENTES.list()).objects).toEqual([]);
    expect((await env.FOTOS.list()).objects).toEqual([]);
  });

  it("limitadorDeTesteDeixaNPedidosERegistaAsChaves", async () => {
    const lim = limitador(2);
    expect((await lim.limit({ key: "ana" })).success).toBe(true);
    expect((await lim.limit({ key: "ana" })).success).toBe(true);
    expect((await lim.limit({ key: "rui" })).success).toBe(false);
    expect(lim.chaves).toEqual(["ana", "ana", "rui"]);
    expect((await LIGADO.LIMITADOR!.limit({ key: "x" })).success).toBe(true);
  });

  it("criarContaDaUmaContaComTermosEUmaSessaoQueServe", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    try {
      vi.setSystemTime(10 * 24 * 60 * 60_000);
      const token = await criarConta("ana", "Ana+1@Exemplo.pt");
      const conta = await env.DB.prepare("SELECT email, criada_em FROM contas WHERE id = 'ana'").first<{ email: string; criada_em: number }>();
      expect(conta).toEqual({ email: "Ana+1@Exemplo.pt", criada_em: 8 * 24 * 60 * 60_000 });
      const termos = await env.DB.prepare("SELECT versao_termos FROM aceites_termos WHERE conta_id = 'ana'").all<{ versao_termos: string }>();
      expect(termos.results.map((linha) => linha.versao_termos)).toEqual(["1"]);
      expect(await contaDaSessao(pedidoComToken(token), env)).toEqual({ contaId: "ana" });

      await criarConta("rui", "rui@exemplo.pt", { termos: "0", criadaEm: 123 });
      expect(await env.DB.prepare("SELECT criada_em FROM contas WHERE id = 'rui'").first("criada_em")).toBe(123);
      expect(await env.DB.prepare("SELECT versao_termos FROM aceites_termos WHERE conta_id = 'rui'").first("versao_termos")).toBe("0");
    } finally {
      vi.useRealTimers();
    }
  });

  it("pedirMandaUint8ArrayEmBrutoEOsCabecalhos", async () => {
    const token = await criarConta("ana", "ana@exemplo.pt");
    // Se o corpo fosse para o JSON.stringify, o servidor lia `{"0":123,…}` e recusava o pedido.
    const resposta = await chamar("/api/termos", {
      corpo: new TextEncoder().encode('{"versao":"9"}'),
      cabecalhos: { authorization: `Bearer ${token}`, "content-type": "application/json" },
    });
    expect(resposta.status).toBe(204);
    expect(await env.DB.prepare("SELECT 1 AS um FROM aceites_termos WHERE conta_id = 'ana' AND versao_termos = '9'").first("um")).toBe(1);
  });

  it("configTemAsRegrasDaComunidade", () => {
    expect(config.interruptores).toEqual({ conta: false, comunidade: false, fotos: false, correcoes: false });
    expect(config.versaoTermos).toBe("1");
    expect(config.comunidade).toEqual({
      confirmadoCom: 2,
      desapareceCom: 2,
      duracaoMinutos: { vai_cheio: 30, nao_passou: 45, acidente: 120, desvio: 180, evento: 240, elevador: 1440, problema_paragem: 10080 },
      porContaPorDia: { relatos: 20, fotos: 1, correcoes: 10 },
      fotosPorDia: 100,
    });
    expect(config.emails).toEqual({ codigosPorDia: 70, outrosPorDia: 30 });
  });
});
