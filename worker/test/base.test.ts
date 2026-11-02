import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import config from "../public/dados/config.json";
import worker from "../src/index";

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
    for (const tabela of ["contas", "sessoes", "codigos", "nonces", "favoritos", "aceites_termos", "envios_email"]) {
      expect(tabelas).toContain(tabela);
    }
  });

  it("apagarContaApagaEmCascata", async () => {
    await env.DB.batch([
      env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES ('c1', 'a@exemplo.pt', 1)"),
      env.DB.prepare("INSERT INTO sessoes (hash, conta_id, criada_em, usada_em) VALUES ('h1', 'c1', 1, 1)"),
      env.DB.prepare("INSERT INTO favoritos (conta_id, chave, tipo, nome, lat, lon, ordem, revisao) VALUES ('c1', 'CM:060419', 'PARAGEM', 'Paragem', 38.7, -9.1, 0, 1)"),
      env.DB.prepare("INSERT INTO aceites_termos (conta_id, versao_termos, aceite_em) VALUES ('c1', '1', 1)"),
    ]);
    await env.DB.prepare("DELETE FROM contas WHERE id = 'c1'").run();
    for (const tabela of ["sessoes", "favoritos", "aceites_termos"]) {
      const linha = await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${tabela}`).first<{ n: number }>();
      expect(linha?.n, tabela).toBe(0);
    }
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
});
