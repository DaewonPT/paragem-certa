import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { describe, expect, it, vi } from "vitest";
import config from "../public/dados/config.json";
import configDaApp from "../../app/src/main/assets/config.json";
import apagarContaHtml from "../public/apagar-conta.html";
import termosHtml from "../public/termos.html";
import { REGRAS } from "../src/explicacoes";
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

/** O texto de uma página sem as etiquetas e com os espaços juntos, dividido na parte em português e na parte em inglês. */
function emDuasLinguas(html: string): { pt: string; en: string } {
  const [pt, en] = html.split('<section id="english"');
  const texto = (parte: string) => parte.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ");
  return { pt: texto(pt), en: texto(en) };
}

/** O wrangler*.jsonc só com comentários em linhas inteiras, que é como estão escritos. */
function lerJsonc(texto: string): Record<string, any> {
  return JSON.parse(texto.replace(/^\s*\/\/.*$/gm, ""));
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

  it("rotasDoCodigoJaNaoExistem", async () => {
    // Nem com o travão por IP cheio: um 429 mostrava um texto errado nas apps antigas.
    for (const limite of [limitador(Infinity), limitador(0)]) {
      for (const rota of ["/api/auth/email/pedir", "/api/auth/email/confirmar"]) {
        const resposta = await chamar(rota, { corpo: { email: "ana@exemplo.pt", codigo: "123456" }, segredos: { LIMITE_AUTH: limite } });
        expect(resposta.status, rota).toBe(404);
        expect(await resposta.json(), rota).toEqual({ erro: "nao_existe" });
      }
    }
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
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k1', 'CM:060419', 'c1', 'nome', 'Outro nome', 1)"),
    ]);
    await env.DB.prepare("DELETE FROM contas WHERE id = 'c1'").run();
    for (const tabela of ["sessoes", "favoritos", "aceites_termos", "relatos", "confirmacoes", "correcoes"]) {
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
    // O estado publicado dos interruptores. A app traz uma cópia deste ficheiro: muda os dois ao mesmo tempo (docs/servidor.md, passos 9 e 16).
    expect(config.versao).toBe(1);
    expect(config.interruptores).toEqual({ conta: true, comunidade: false, correcoes: false, carrisAoVivo: false, metro: false });
    expect(config.emails.codigosPorDia).toBe(70);
    expect(config.canaisReclamacao).toHaveLength(5);
    for (const canal of config.canaisReclamacao) {
      expect(canal.nome.length).toBeGreaterThan(0);
      expect(canal.url).toMatch(/^https:\/\//);
    }
  });

  it("semR2NemTokenDaCache", () => {
    for (const c of [lerJsonc(env.TEST_CONFIG), lerJsonc(env.TEST_ADMIN_CONFIG)]) {
      expect(c.r2_buckets).toBeUndefined();
      expect(c.secrets.required).not.toContain("CF_PURGE_TOKEN");
      expect(c.secrets.required).not.toContain("CF_ZONA");
      expect(c.secrets.required).toContain("CHAVE_MARCA_EMAIL");
    }
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

  it("mapasOficiaisNasDuasCopias", () => {
    // Só os interruptores podem diferir entre a cópia do servidor e a da app (como no ConfiguracaoTest do :core).
    const { interruptores: _doServidor, ...servidor } = config;
    const { interruptores: _daApp, ...app } = configDaApp;
    expect(app).toEqual(servidor);

    expect(config.mapasOficiais.length).toBeGreaterThan(0);
    const operadores = config.mapasOficiais.map((mapa) => mapa.operador);
    expect(new Set(operadores).size).toBe(operadores.length);
    for (const mapa of config.mapasOficiais) {
      expect(mapa.operador).toMatch(/^(CM|pt-\S+)$/);
      expect(mapa.url).toMatch(/^https:\/\/[^\s]+$/);
    }
  });

  it("configTemAsRegrasDaComunidade", () => {
    expect(config.interruptores).toEqual({ conta: true, comunidade: false, correcoes: false, carrisAoVivo: false, metro: false });
    expect(config.versaoTermos).toBe("1");
    expect(config.comunidade).toEqual({
      confirmadoCom: 2,
      desapareceCom: 2,
      duracaoMinutos: { vai_cheio: 30, nao_passou: 45, acidente: 120, desvio: 180, evento: 240, elevador: 1440, problema_paragem: 10080 },
      porContaPorDia: { relatos: 20, correcoes: 10 },
    });
    expect(config.emails).toEqual({ codigosPorDia: 70, outrosPorDia: 30 });
  });
});

describe("política de privacidade, termos e guia do autor", () => {
  async function politica(): Promise<{ pt: string; en: string }> {
    return emDuasLinguas(await (await pedir("/privacidade", { RESPONSAVEL: "Nome de Teste" })).text());
  }

  it("privacidadeDizQueORelatoRegistaAConta", async () => {
    // Guarda de regressão: o que diz aqui tem de continuar a ser verdade.
    const { pt, en } = await politica();
    expect(pt).toContain("um relato regista que a tua conta estava junto dessa paragem a essa hora");
    expect(pt).toContain("30 dias");
    expect(en).toContain("a report records that your account was near that stop at that time");
    expect(en).toContain("30 days");
  });

  it("privacidadeDizQuandoSaemONomeEOEmailDaDenuncia", async () => {
    const { pt, en } = await politica();
    expect(pt).toContain("O nome e o email de quem denuncia");
    expect(pt).toContain("Até a decisão lhe ser enviada; depois só fica a marca do email (um hash com chave secreta)");
    expect(en).toContain("The name and email of whoever files a report");
    expect(en).toContain("Until the decision is sent to them; after that only a hash of the email made with a secret key remains");
  });

  it("privacidadeDizOQueFicaDepoisDeApagarAConta", async () => {
    const { pt, en } = await politica();
    expect(pt).toContain("Se apagares a conta com uma denúncia tua ainda por decidir, o nome e o email dessa denúncia ficam até ela ser decidida");
    expect(pt).toContain("Os emails por enviar para o teu endereço ficam até saírem, no máximo 30 dias, mesmo depois de apagares a conta");
    expect(en).toContain("If you delete the account while a report you filed is still undecided, the name and email on that report stay until it is decided");
    expect(en).toContain("Queued emails to your address stay until they are sent, 30 days at most, even after you delete the account");
  });

  it("apagarContaDizUmMes", () => {
    const [pt, en] = apagarContaHtml.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").split("Delete your Paragem Certa account");
    expect(pt).toContain("no prazo de um mês");
    expect(en).toContain("within one month");
  });

  it("apagarContaDizOQueFicaDeUmaDenunciaPorDecidir", () => {
    const [pt, en] = apagarContaHtml.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").split("Delete your Paragem Certa account");
    expect(pt).toContain("Se apagares a conta com uma denúncia tua ainda por decidir, o nome e o email dessa denúncia ficam até ela ser decidida");
    expect(pt).toContain("Os emails por enviar para o teu endereço ficam até saírem, no máximo 30 dias");
    expect(en).toContain("If you delete the account while a report you filed is still undecided, the name and email on that report stay until it is decided");
    expect(en).toContain("Queued emails to your address stay until they are sent, 30 days at most");
  });

  it("privacidadeDizQueOcultarFicaNoTelemovel", async () => {
    const { pt, en } = await politica();
    expect(pt).toContain('O que ocultas com "Ocultar este relato" fica só no teu telemóvel');
    expect(en).toContain('What you hide with "Hide this report" stays only on your phone');
    for (const t of [pt, en]) {
      expect(t).not.toContain("fotos.pixellusitano.com");
      expect(t).not.toMatch(/Ocultar esta foto|Hide this photo|CC BY-SA/);
    }
  });

  it("politicaFalaDaCarrisEDaEmel", async () => {
    // O Metro está fora da 0.4 (a porta 8243 dá 526): a página não pode dizer que os seus dados passam pelo servidor.
    const { pt, en } = await politica();
    expect(pt).toContain("As posições ao vivo dos autocarros da Carris passam pelo servidor da Paragem Certa");
    expect(pt).toContain("não guarda nada de quem as pede");
    expect(pt).toContain("A Carris só recebe pedidos do servidor, sem nada sobre ti");
    expect(pt).toContain("o GitHub Actions lê os ficheiros GTFS (horários e linhas) que o Transitous publica");
    expect(pt).toContain("EMEL (dados.emel.pt): a lista das estações GIRA, que o telemóvel lê quando abres o mapa");
    expect(pt).toContain("não leva a tua posição nem nada sobre ti");
    expect(pt).not.toMatch(/\bMetro\b/);
    expect(en).toContain("The live positions of Carris buses go through the Paragem Certa server");
    expect(en).toContain("it stores nothing about who asks for them");
    expect(en).toContain("Carris only receives requests from the server, with nothing about you");
    expect(en).toContain("GitHub Actions reads the GTFS files (timetables and lines) that Transitous publishes");
    expect(en).toContain("EMEL (dados.emel.pt): the list of GIRA stations, which your phone reads when you open the map");
    expect(en).toContain("it carries neither your position nor anything about you");
    expect(en).not.toMatch(/\bMetro\b/);
  });

  it("politicaFalaDoPanoramaxEDoWidget", async () => {
    const { pt, en } = await politica();
    expect(pt).toContain("as miniaturas de fotos de rua que vês");
    expect(pt).toContain("o GitHub Actions também lê o Panoramax e a lista de paragens da Carris Metropolitana para escolher as fotos de rua");
    expect(pt).toContain('Os widgets "Chegadas na paragem" e "Casa e Trabalho" só vão buscar dados quando lhes tocas (↻) ou quando abres a app; fora disso o Android pode arrancá-la por instantes para redesenhar os últimos dados guardados');
    expect(en).toContain("the street photo thumbnails you view");
    expect(en).toContain("GitHub Actions also reads Panoramax and the Carris Metropolitana stop list to choose the street photos");
    expect(en).toContain('The "Chegadas na paragem" and "Casa e Trabalho" widgets only fetch data when you tap them (↻) or when you open the app; otherwise Android may start the app for a moment to redraw the last saved data');
  });

  it("politicaDizQueOTransitousRecebeAPosicao", async () => {
    const { pt, en } = await politica();
    expect(pt).toContain("as paragens das próximas partidas. Também recebe a zona do mapa que estás a ver, o ponto onde fazes um toque longo no mapa");
    expect(pt).toContain("com a localização ligada, a tua posição, para mostrar as paragens perto de ti, ordenar os resultados das pesquisas e sugerir rotas para Casa e Trabalho.");
    expect(en).toContain("the stops for upcoming departures. It also receives the map area you are viewing, the point you long-press on the map");
    expect(en).toContain("with location on, your position, to show the stops near you, to sort search results and to suggest routes to your saved home and work.");
  });

  it("termosTemAIdadeEOsMecanismos", () => {
    // Guarda de regressão: o que os termos têm de explicar.
    const { pt, en } = emDuasLinguas(termosHtml);
    for (const frase of ["13 anos", "Confirmado", "Já não está", "expira"]) expect(pt, frase).toContain(frase);
    for (const frase of ["at least 13", "Confirmed", "No longer there", "expires"]) expect(en, frase).toContain(frase);
  });

  it("termosTemTodasAsRegrasDasDecisoes", () => {
    // Estes textos vão tal e qual nos emails das decisões (explicacoes.ts): têm de ser os dos termos, palavra por palavra.
    const { pt, en } = emDuasLinguas(termosHtml);
    expect(Object.keys(REGRAS)).toHaveLength(4);
    for (const [chave, regra] of Object.entries(REGRAS)) expect(pt, chave).toContain(regra);
    expect(pt).toContain("Não publiques nada que a lei proíba.");
    expect(pt).toContain("24 horas");
    // Em inglês não há o texto dos emails (só em português), mas as quatro regras têm de estar lá, traduzidas.
    for (const frase of [
      "Report only what you saw",
      "Do not report false things",
      "Corrections describe only what exists on site.",
      "Do not publish anything that the law prohibits.",
      "24 hours",
    ]) {
      expect(en, frase).toContain(frase);
    }
  });

  it("termosDizOQueFazUmBloqueioOsVotosEAsDenuncias", () => {
    const { pt, en } = emDuasLinguas(termosHtml);
    expect(pt).toContain("Uma conta bloqueada não relata, não confirma nem envia correções durante 12 meses");
    expect(pt).toContain('O "Ainda está" de quem relatou não renova o tempo');
    expect(pt).toContain("Os votos de uma conta só contam depois de ela ter 24 horas");
    expect(pt).toContain("no máximo 5 denúncias por dia");
    expect(en).toContain("A blocked account cannot report, confirm or send corrections for 12 months");
    expect(en).toContain('"Still there" from the person who reported does not renew the time');
    expect(en).toContain("An account's votes only count once it is 24 hours old");
    expect(en).toContain("at most 5 reports of abuse per day");
  });

  it("guiaNomeiaOsSegredosDosDoisWorkers", () => {
    const guia = env.TEST_GUIA;
    // Os passos do pc-admin vêm a seguir à secção 9; os segredos do pc-admin só se põem lá.
    const inicioDoAdmin = guia.indexOf("\n## 10. ");
    expect(inicioDoAdmin, "o guia tem de ter a secção 10").toBeGreaterThan(0);
    const admin = guia.slice(inicioDoAdmin);
    for (const nome of lerJsonc(env.TEST_CONFIG).secrets.required) expect(guia, `paragem-certa: ${nome}`).toContain(nome);
    for (const nome of lerJsonc(env.TEST_ADMIN_CONFIG).secrets.required) expect(admin, `pc-admin: ${nome}`).toContain(nome);
    expect(admin).toContain("secret put");
    expect(admin).toContain("-c wrangler.admin.jsonc");
    expect(guia).not.toContain("ficam para o guia da etapa 3");
  });
});

// A zona pixellusitano.com só tem uma regra grátis de limite de pedidos, e é a do site: o limite das entradas fica no Worker.
describe("limite das entradas por IP", () => {
  const ip = { "cf-connecting-ip": "203.0.113.7" };

  it("sextoPedidoDeEntradaDoMesmoIpDa429", async () => {
    const limite = limitador(5);
    for (let i = 0; i < 5; i++) {
      expect((await chamar("/api/auth/google/nonce", { cabecalhos: ip, segredos: { LIMITE_AUTH: limite } })).status).toBe(200);
    }
    const recusado = await chamar("/api/auth/google/nonce", { cabecalhos: ip, segredos: { LIMITE_AUTH: limite } });
    expect(recusado.status).toBe(429);
    expect(await recusado.json()).toEqual({ erro: "espera_um_pouco" });
    expect(limite.chaves).toEqual(Array(6).fill("203.0.113.7"));
    // O sexto nem chegou à base: só há os cinco nonces que passaram.
    const { n } = (await env.DB.prepare("SELECT COUNT(*) AS n FROM nonces").first<{ n: number }>())!;
    expect(n).toBe(5);
  });

  it("soAsEntradasContam", async () => {
    const limite = limitador(0);
    expect((await chamar("/api/saude", { metodo: "GET", cabecalhos: ip, segredos: { LIMITE_AUTH: limite } })).status).toBe(200);
    expect(limite.chaves).toEqual([]);
  });

  it("configTemOLimiteDasEntradas", () => {
    const publico = lerJsonc(env.TEST_CONFIG);
    expect(publico.ratelimits).toContainEqual({ name: "LIMITE_AUTH", namespace_id: "1002", simple: { limit: 5, period: 10 } });
  });
});
