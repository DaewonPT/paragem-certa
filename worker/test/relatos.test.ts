import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import config from "../public/dados/config.json";
import { criarConta, LIGADO, pedir } from "./ajudas";

type Relato = { id: string; tipo: string; paragem: string; linha: string; criadoEm: number; expiraEm: number; confirmacoes: number; confirmado: boolean };

const MINUTO = 60_000;
const HORA = 60 * MINUTO;
const DIA = 24 * HORA;
const INICIO = Date.UTC(2026, 10, 3, 8, 0);
const PARAGEM = "CM:060419";
const DESVIO = { tipo: "desvio", paragem: PARAGEM, linha: "736" };

// Ana relata; Rui e Eva confirmam ou dizem "Já não está". Todas criadas há 2 dias (já contam nos votos).
let ana: string;
let rui: string;
let eva: string;

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(INICIO);
  ana = await criarConta("ana", "ana@exemplo.pt");
  rui = await criarConta("rui", "rui@exemplo.pt");
  eva = await criarConta("eva", "eva@exemplo.pt");
});

afterEach(() => {
  vi.useRealTimers();
});

function avancar(ms: number): void {
  vi.setSystemTime(Date.now() + ms);
}

function relatar(token: string, corpo: unknown = DESVIO, opcoes: { cabecalhos?: Record<string, string> } = {}): Promise<Response> {
  return pedir("/api/relatos", { token, corpo, segredos: LIGADO, ...opcoes });
}

async function relatado(token: string, corpo: unknown = DESVIO): Promise<{ id: string; novo: boolean }> {
  const resposta = await relatar(token, corpo);
  expect(resposta.status, JSON.stringify(corpo)).toBe(201);
  return (await resposta.json()) as { id: string; novo: boolean };
}

function votar(token: string, relato: string, voto: string): Promise<Response> {
  return pedir("/api/votos", { token, corpo: { relato, voto }, segredos: LIGADO });
}

async function votado(token: string, relato: string, voto: string): Promise<{ confirmacoes: number; confirmado: boolean; ativo: boolean }> {
  const resposta = await votar(token, relato, voto);
  expect(resposta.status).toBe(200);
  return (await resposta.json()) as { confirmacoes: number; confirmado: boolean; ativo: boolean };
}

function lerComunidade(paragens: string[], segredos: Partial<Env> = LIGADO): Promise<Response> {
  return pedir(`/api/comunidade?paragens=${encodeURIComponent(paragens.join(","))}`, { metodo: "GET", segredos });
}

async function relatosDe(paragens: string[] = [PARAGEM]): Promise<Relato[]> {
  const resposta = await lerComunidade(paragens);
  expect(resposta.status).toBe(200);
  return ((await resposta.json()) as { relatos: Relato[] }).relatos;
}

function lerAvisos(linhas: string[], segredos: Partial<Env> = LIGADO): Promise<Response> {
  return pedir(`/api/avisos?linhas=${encodeURIComponent(linhas.join(","))}`, { metodo: "GET", segredos });
}

function lerComunidadeDeLinhas(linhas: string[], segredos: Partial<Env> = LIGADO): Promise<Response> {
  return pedir(`/api/comunidade?linhas=${encodeURIComponent(linhas.join(","))}`, { metodo: "GET", segredos });
}

async function erroDe(resposta: Response): Promise<{ estado: number; erro: string }> {
  return { estado: resposta.status, erro: ((await resposta.json()) as { erro: string }).erro };
}

async function linhasEmRelatos(contaId?: string): Promise<number> {
  const consulta = contaId
    ? env.DB.prepare("SELECT COUNT(*) AS n FROM relatos WHERE conta_id = ?").bind(contaId)
    : env.DB.prepare("SELECT COUNT(*) AS n FROM relatos");
  return (await consulta.first<number>("n"))!;
}

describe("relatos e votos", () => {
  it("relatoNovoFicaPorConfirmar", async () => {
    const criado = await relatado(ana);
    expect(criado.novo).toBe(true);
    expect(await relatosDe()).toEqual([
      { id: criado.id, tipo: "desvio", paragem: PARAGEM, linha: "736", criadoEm: INICIO, expiraEm: INICIO + 3 * HORA, confirmacoes: 1, confirmado: false },
    ]);
  });

  it("segundaContaConfirma", async () => {
    const { id } = await relatado(ana);
    expect(await votado(rui, id, "ainda_esta")).toEqual({ confirmacoes: 2, confirmado: true, ativo: true });
    expect(await relatosDe()).toMatchObject([{ confirmacoes: 2, confirmado: true }]);
  });

  it("contaNovaNaoConfirma", async () => {
    // Um relato de 7 dias, para ainda estar ativo quando as contas novas fizerem 24 h.
    const { id } = await relatado(ana, { tipo: "problema_paragem", paragem: PARAGEM });
    const expiraEm = INICIO + 7 * DIA;
    const teo = await criarConta("teo", "teo@exemplo.pt", { criadaEm: Date.now() });
    const ze = await criarConta("ze", "ze@exemplo.pt", { criadaEm: Date.now() });

    expect(await votado(teo, id, "ainda_esta")).toEqual({ confirmacoes: 1, confirmado: false, ativo: true });
    expect(await relatosDe()).toMatchObject([{ expiraEm, confirmacoes: 1 }]);

    expect(await votado(teo, id, "ja_nao_esta")).toMatchObject({ ativo: true });
    expect(await votado(ze, id, "ja_nao_esta")).toMatchObject({ ativo: true });
    expect(await relatosDe()).toMatchObject([{ expiraEm }]);

    expect(await votado(teo, id, "ainda_esta")).toEqual({ confirmacoes: 1, confirmado: false, ativo: true });
    avancar(DIA);
    expect(await relatosDe()).toMatchObject([{ expiraEm, confirmacoes: 2, confirmado: true }]);
    // O "Já não está" do Zé também passou a contar: com o da Eva são dois.
    expect(await votado(eva, id, "ja_nao_esta")).toEqual({ confirmacoes: 2, confirmado: true, ativo: false });
  });

  it("quemRelatouNaoContaDuasVezes", async () => {
    const { id } = await relatado(ana);
    expect(await votado(ana, id, "ainda_esta")).toEqual({ confirmacoes: 1, confirmado: false, ativo: true });
    expect(await votado(ana, id, "ainda_esta")).toEqual({ confirmacoes: 1, confirmado: false, ativo: true });
    expect(await relatosDe()).toMatchObject([{ confirmacoes: 1, confirmado: false }]);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM confirmacoes WHERE relato_id = ?").bind(id).first("n")).toBe(1);
  });

  it("aindaEstaDeQuemRelatouNaoRenova", async () => {
    const { id } = await relatado(ana);
    avancar(20 * MINUTO);
    await votado(ana, id, "ainda_esta");
    expect(await relatosDe()).toMatchObject([{ expiraEm: INICIO + 3 * HORA }]);
    await votado(rui, id, "ainda_esta");
    expect(await relatosDe()).toMatchObject([{ expiraEm: Date.now() + 3 * HORA }]);
  });

  it("expiraSozinho", async () => {
    const { id } = await relatado(ana, { tipo: "vai_cheio", paragem: PARAGEM, linha: "736" });
    avancar(30 * MINUTO - 1);
    expect(await relatosDe()).toHaveLength(1);
    avancar(2);
    expect(await relatosDe()).toEqual([]);
    expect(await erroDe(await votar(rui, id, "ainda_esta"))).toEqual({ estado: 404, erro: "nao_existe" });
  });

  it("duracoesDaConfig", async () => {
    const deLinha = new Set(["acidente", "desvio", "evento", "nao_passou", "vai_cheio"]);
    for (const tipo of Object.keys(config.comunidade.duracaoMinutos)) {
      await relatado(ana, deLinha.has(tipo) ? { tipo, paragem: PARAGEM, linha: "736" } : { tipo, paragem: PARAGEM });
    }
    const relatos = await relatosDe();
    expect(relatos).toHaveLength(7);
    for (const relato of relatos) {
      const minutos = config.comunidade.duracaoMinutos[relato.tipo as keyof typeof config.comunidade.duracaoMinutos];
      expect(relato.expiraEm - relato.criadoEm, relato.tipo).toBe(minutos * MINUTO);
    }
  });

  it("doisJaNaoEstaFazemDesaparecer", async () => {
    const { id } = await relatado(ana);
    expect(await votado(rui, id, "ja_nao_esta")).toMatchObject({ ativo: true });
    expect(await votado(eva, id, "ja_nao_esta")).toMatchObject({ ativo: false });
    expect(await relatosDe()).toEqual([]);
    expect(await erroDe(await votar(rui, id, "ainda_esta"))).toEqual({ estado: 404, erro: "nao_existe" });

    // O "Já não está" de quem relatou também conta para Y.
    const outro = await relatado(ana);
    expect(outro.id).not.toBe(id);
    expect(await votado(ana, outro.id, "ja_nao_esta")).toMatchObject({ ativo: true });
    expect(await votado(rui, outro.id, "ja_nao_esta")).toMatchObject({ ativo: false });
  });

  it("jaNaoEstaEmParaleloFazemDesaparecer", async () => {
    const { id } = await relatado(ana);
    await Promise.all([votar(rui, id, "ja_nao_esta"), votar(eva, id, "ja_nao_esta")]);
    expect(await relatosDe()).toEqual([]);
  });

  it("relatoRepetidoNaoGastaOLimite", async () => {
    const { id } = await relatado(ana);
    const resposta = await relatar(rui);
    expect(resposta.status).toBe(200);
    expect(await resposta.json()).toEqual({ id, novo: false });
    expect(await relatosDe()).toMatchObject([{ id, confirmacoes: 2, confirmado: true }]);
    expect(await linhasEmRelatos("rui")).toBe(0);
  });

  it("repetirOPedidoNaoDuplica", async () => {
    const { id } = await relatado(ana);
    const resposta = await relatar(ana);
    expect(resposta.status).toBe(200);
    expect(await resposta.json()).toEqual({ id, novo: false });
    expect(await linhasEmRelatos()).toBe(1);
    expect(await relatosDe()).toMatchObject([{ id, confirmacoes: 1, expiraEm: INICIO + 3 * HORA }]);
  });

  it("relatosIguaisEmParaleloFicamUm", async () => {
    const respostas = await Promise.all([relatar(ana), relatar(rui), relatar(eva), relatar(ana)]);
    const corpos = (await Promise.all(respostas.map((r) => r.json()))) as { id: string; novo: boolean }[];
    expect(respostas.map((r) => r.status).filter((s) => s === 201)).toHaveLength(1);
    expect(respostas.every((r) => r.status === 200 || r.status === 201)).toBe(true);
    expect(new Set(corpos.map((c) => c.id)).size).toBe(1);
    expect(await linhasEmRelatos()).toBe(1);
    expect(await relatosDe()).toMatchObject([{ confirmacoes: 3, confirmado: true }]);
  });

  it("vigesimoPrimeiroRelatoDa429", async () => {
    const elevador = (n: number) => ({ tipo: "elevador", paragem: `CM:${n}` });
    for (let n = 1; n <= 20; n++) await relatado(ana, elevador(n));
    expect(await erroDe(await relatar(ana, elevador(21)))).toEqual({ estado: 429, erro: "muitos_pedidos" });
    // Repetir um que já está ativo não é um relato novo: passa mesmo no limite.
    expect((await relatar(ana, elevador(1))).status).toBe(200);
    expect(await linhasEmRelatos("ana")).toBe(20);
    avancar(DIA);
    await relatado(ana, elevador(21));
  });

  it("relatosEmParaleloNaoPassamDosVinte", async () => {
    const respostas = await Promise.all(Array.from({ length: 25 }, (_, n) => relatar(ana, { tipo: "elevador", paragem: `CM:${n}` })));
    const estados = respostas.map((r) => r.status);
    expect(estados.filter((s) => s === 201)).toHaveLength(20);
    expect(estados.filter((s) => s === 429)).toHaveLength(5);
    expect(await linhasEmRelatos("ana")).toBe(20);
  });

  it("tipoDeLinhaSemLinhaDa400", async () => {
    const invalidos: unknown[] = [
      { tipo: "desvio", paragem: PARAGEM },
      { tipo: "desvio", paragem: PARAGEM, linha: "" },
      { tipo: "desvio", paragem: PARAGEM, linha: "<b>" },
      { tipo: "desvio", paragem: PARAGEM, linha: 736 },
      { tipo: "elevador", paragem: PARAGEM, linha: "736" },
      { tipo: "outro", paragem: PARAGEM },
      { tipo: "toString", paragem: PARAGEM },
      { tipo: "elevador", paragem: "x" },
      { tipo: "elevador", paragem: "CM:<script>" },
      { tipo: "elevador" },
      "não é JSON",
    ];
    for (const corpo of invalidos) {
      expect(await erroDe(await relatar(ana, corpo)), JSON.stringify(corpo)).toEqual({ estado: 400, erro: "pedido_invalido" });
    }
    await relatado(ana, { tipo: "elevador", paragem: "TRANSITOUS:pt-Comboios-de-Portugal(CP)_94_69005" });
    await relatado(ana, { tipo: "elevador", paragem: PARAGEM, linha: null });
    await relatado(ana, { tipo: "desvio", paragem: PARAGEM, linha: "15e" });
    expect((await relatosDe()).map((r) => r.linha).sort()).toEqual(["", "15E"]);

    for (const corpo of [{ relato: 1, voto: "ainda_esta" }, { relato: "x", voto: "talvez" }, {}]) {
      expect(await erroDe(await pedir("/api/votos", { token: ana, corpo, segredos: LIGADO })), JSON.stringify(corpo)).toEqual({
        estado: 400,
        erro: "pedido_invalido",
      });
    }
    expect(await erroDe(await votar(ana, "nao-existe", "ainda_esta"))).toEqual({ estado: 404, erro: "nao_existe" });
  });

  it("avisosSoTrazemConfirmadosDasLinhas", async () => {
    const { id } = await relatado(ana);
    await votado(rui, id, "ainda_esta");
    await relatado(ana, { tipo: "acidente", paragem: PARAGEM, linha: "15E" });
    // Confirmado, mas de uma linha que não se pediu.
    const outra = await relatado(ana, { tipo: "acidente", paragem: "CM:1", linha: "999" });
    await votado(rui, outra.id, "ainda_esta");

    const resposta = await lerAvisos(["736", "15e"]);
    expect(resposta.status).toBe(200);
    expect(((await resposta.json()) as { relatos: Relato[] }).relatos).toEqual([
      { id, tipo: "desvio", paragem: PARAGEM, linha: "736", criadoEm: INICIO, expiraEm: INICIO + 3 * HORA, confirmacoes: 2, confirmado: true },
    ]);
  });

  it("avisosSemComunidadeTrazemPerturbacoes", async () => {
    const { id } = await relatado(ana);
    await votado(rui, id, "ainda_esta");

    // Sem `comunidade` já não há 403: os relatos vêm vazios (e as perturbações do Metro, que só a Task 2 liga).
    const semComunidade = await lerAvisos(["736"], {});
    expect(semComunidade.status).toBe(200);
    expect(await semComunidade.json()).toEqual({ relatos: [], perturbacoes: [] });
    // O pedido mal feito continua a dar 400, com ou sem `comunidade`.
    expect(await erroDe(await lerAvisos([], {}))).toEqual({ estado: 400, erro: "pedido_invalido" });

    // Ligada a comunidade, o mesmo pedido traz o relato confirmado.
    const comComunidade = await lerAvisos(["736"]);
    expect(comComunidade.status).toBe(200);
    expect(await comComunidade.json()).toEqual({
      relatos: [{ id, tipo: "desvio", paragem: PARAGEM, linha: "736", criadoEm: INICIO, expiraEm: INICIO + 3 * HORA, confirmacoes: 2, confirmado: true }],
      perturbacoes: [],
    });
  });

  it("relatosDaLinhaTrazemPorConfirmar", async () => {
    const confirmado = await relatado(ana);
    await votado(rui, confirmado.id, "ainda_esta");
    avancar(MINUTO);
    const porConfirmar = await relatado(ana, { tipo: "acidente", paragem: "CM:1", linha: "15E" });
    // Outra linha, e um relato da paragem sem linha: não são de nenhuma das pedidas.
    await relatado(ana, { tipo: "acidente", paragem: PARAGEM, linha: "999" });
    await relatado(ana, { tipo: "elevador", paragem: PARAGEM });

    const resposta = await lerComunidadeDeLinhas(["736", "15e"]);
    expect(resposta.status).toBe(200);
    // Os dois, o mais recente primeiro; ao contrário do /api/avisos, também o que ainda não tem 2 confirmações.
    expect(await resposta.json()).toEqual({
      relatos: [
        { id: porConfirmar.id, tipo: "acidente", paragem: "CM:1", linha: "15E", criadoEm: INICIO + MINUTO, expiraEm: INICIO + MINUTO + 2 * HORA, confirmacoes: 1, confirmado: false },
        { id: confirmado.id, tipo: "desvio", paragem: PARAGEM, linha: "736", criadoEm: INICIO, expiraEm: INICIO + 3 * HORA, confirmacoes: 2, confirmado: true },
      ],
    });

    // Só os ativos: passadas 4 horas, os dois já expiraram.
    avancar(4 * HORA);
    expect(await (await lerComunidadeDeLinhas(["736", "15E"])).json()).toEqual({ relatos: [] });

    const linhas = Array.from({ length: 21 }, (_, n) => `L${n}`);
    expect(await erroDe(await lerComunidadeDeLinhas(linhas))).toEqual({ estado: 400, erro: "pedido_invalido" });
    expect((await lerComunidadeDeLinhas(linhas.slice(0, 20))).status).toBe(200);
    expect(await erroDe(await lerComunidadeDeLinhas([]))).toEqual({ estado: 400, erro: "pedido_invalido" });
    expect(await erroDe(await lerComunidadeDeLinhas(["<b>"]))).toEqual({ estado: 400, erro: "pedido_invalido" });
  });

  it("relatosDaLinhaSemComunidadeDa403", async () => {
    const desligado = { estado: 403, erro: "desligado" };
    expect(await erroDe(await lerComunidadeDeLinhas(["736"], {}))).toEqual(desligado);
    // As fotos ou as correções ligadas não chegam: os relatos por linha são só da comunidade.
    const soFotos = { ...LIGADO, TESTE_INTERRUPTORES: JSON.stringify({ fotos: true, correcoes: true }) };
    expect(await erroDe(await lerComunidadeDeLinhas(["736"], soFotos))).toEqual(desligado);
  });

  it("relatosDaLinhaTemOLimiteDe100", async () => {
    const agora = Date.now();
    const relatos = Array.from({ length: 101 }, (_, n) =>
      env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES (?, 'desvio', ?, '736', ?, ?, ?)").bind(
        `r${n}`,
        `CM:${n}`,
        "ana",
        agora - n,
        agora + HORA,
      ),
    );
    await env.DB.batch(relatos);
    const { relatos: lidos } = (await (await lerComunidadeDeLinhas(["736"])).json()) as { relatos: Relato[] };
    expect(lidos).toHaveLength(100);
    expect(lidos[0].id).toBe("r0");
  });

  it("leiturasNaoMostramQuemRelatou", async () => {
    const { id } = await relatado(ana);
    await votado(rui, id, "ainda_esta");
    for (const resposta of [await lerComunidade([PARAGEM]), await lerAvisos(["736"]), await lerComunidadeDeLinhas(["736"])]) {
      const texto = await resposta.text();
      expect(texto).toContain(id);
      for (const proibido of ["ana", "rui", "exemplo", "conta"]) expect(texto, proibido).not.toContain(proibido);
    }
  });

  it("servidorNaoGuardaIpNemPosicao", async () => {
    const resposta = await relatar(ana, { ...DESVIO, lat: 38.7123, lon: -9.1456, posicao: "38.7123,-9.1456" }, { cabecalhos: { "cf-connecting-ip": "203.0.113.77" } });
    expect(resposta.status).toBe(201);
    const { id } = (await resposta.json()) as { id: string };
    await pedir("/api/votos", { token: rui, corpo: { relato: id, voto: "ainda_esta", lat: 38.7123, lon: -9.1456 }, segredos: LIGADO, cabecalhos: { "cf-connecting-ip": "203.0.113.78" } });

    for (const tabela of ["relatos", "confirmacoes"]) {
      const colunas = (await env.DB.prepare(`PRAGMA table_info(${tabela})`).all<{ name: string }>()).results.map((c) => c.name);
      for (const proibida of ["lat", "lon", "ip", "posicao"]) expect(colunas, tabela).not.toContain(proibida);
      const linhas = JSON.stringify((await env.DB.prepare(`SELECT * FROM ${tabela}`).all()).results);
      for (const valor of ["203.0.113", "38.71", "9.14"]) expect(linhas, tabela).not.toContain(valor);
    }
  });

  it("comunidadeDesligadaDa403", async () => {
    const { id } = await relatado(ana);
    const desligado = { estado: 403, erro: "desligado" };
    expect(await erroDe(await pedir("/api/relatos", { token: ana, corpo: DESVIO }))).toEqual(desligado);
    expect(await erroDe(await pedir("/api/votos", { token: rui, corpo: { relato: id, voto: "ainda_esta" } }))).toEqual(desligado);
    expect(await erroDe(await lerComunidade([PARAGEM], {}))).toEqual(desligado);

    // Com só as fotos ligadas a leitura responde, mas sem os relatos.
    const soFotos = { ...LIGADO, TESTE_INTERRUPTORES: JSON.stringify({ fotos: true }) };
    const resposta = await lerComunidade([PARAGEM], soFotos);
    expect(resposta.status).toBe(200);
    expect(await resposta.json()).toEqual({ relatos: [], fotos: [], emRevisao: [], correcoes: [] });
  });

  it("muitasParagensDa400", async () => {
    const paragens = Array.from({ length: 101 }, (_, n) => `CM:${n}`);
    expect(await erroDe(await lerComunidade(paragens))).toEqual({ estado: 400, erro: "pedido_invalido" });
    expect((await lerComunidade(paragens.slice(0, 100))).status).toBe(200);
    expect(await erroDe(await lerComunidade([]))).toEqual({ estado: 400, erro: "pedido_invalido" });
    expect(await erroDe(await lerComunidade(["CM:<b>"]))).toEqual({ estado: 400, erro: "pedido_invalido" });
    expect(await erroDe(await pedir("/api/comunidade", { metodo: "GET", segredos: LIGADO }))).toEqual({ estado: 400, erro: "pedido_invalido" });

    const linhas = Array.from({ length: 21 }, (_, n) => `L${n}`);
    expect(await erroDe(await lerAvisos(linhas))).toEqual({ estado: 400, erro: "pedido_invalido" });
    expect((await lerAvisos(linhas.slice(0, 20))).status).toBe(200);
    expect(await erroDe(await lerAvisos([]))).toEqual({ estado: 400, erro: "pedido_invalido" });
  });

  it("fluxoCompletoDeUmRelato", async () => {
    // A relata; B vê "por confirmar".
    const { id } = await relatado(ana);
    expect(await relatosDe()).toMatchObject([{ id, confirmacoes: 1, confirmado: false }]);
    // B confirma: "Confirmado por 2".
    avancar(10 * MINUTO);
    expect(await votado(rui, id, "ainda_esta")).toEqual({ confirmacoes: 2, confirmado: true, ativo: true });
    expect(await relatosDe()).toMatchObject([{ id, confirmacoes: 2, confirmado: true, expiraEm: Date.now() + 3 * HORA }]);
    // Expira.
    avancar(3 * HORA);
    expect(await relatosDe()).toEqual([]);

    // Um relato novo igual: duas contas dizem "Já não está" e some.
    const novo = await relatado(rui);
    expect(novo).toEqual({ id: expect.any(String), novo: true });
    expect(novo.id).not.toBe(id);
    expect(await votado(ana, novo.id, "ja_nao_esta")).toMatchObject({ ativo: true });
    expect(await votado(eva, novo.id, "ja_nao_esta")).toMatchObject({ ativo: false });
    expect(await relatosDe()).toEqual([]);
    expect(await linhasEmRelatos()).toBe(2);
  });
});
