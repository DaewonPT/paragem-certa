import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { criarConta, LIGADO, pedir, sha256 } from "./ajudas";

type Correcao = { paragem: string; tipo: string; lat?: number; lon?: number; nome?: string };

const DIA = 24 * 60 * 60_000;
const INICIO = Date.UTC(2026, 10, 3, 8, 0);
const PARAGEM = "CM:060419";
const POSICAO = { paragem: PARAGEM, tipo: "posicao", lat: 38.7223, lon: -9.1393 };
const NOME = { paragem: PARAGEM, tipo: "nome", nome: "Praça do Comércio" };

let ana: string;
let rui: string;

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(INICIO);
  ana = await criarConta("ana", "ana@exemplo.pt");
  rui = await criarConta("rui", "rui@exemplo.pt");
});

afterEach(() => {
  vi.useRealTimers();
});

function avancar(ms: number): void {
  vi.setSystemTime(Date.now() + ms);
}

function corrigir(token: string | undefined, corpo: unknown, segredos: Partial<Env> = LIGADO): Promise<Response> {
  return pedir("/api/correcoes", { token, corpo, segredos });
}

function lerComunidade(paragens: string[], segredos: Partial<Env> = LIGADO): Promise<Response> {
  return pedir(`/api/comunidade?paragens=${encodeURIComponent(paragens.join(","))}`, { metodo: "GET", segredos });
}

async function correcoesDe(paragens: string[] = [PARAGEM], segredos: Partial<Env> = LIGADO): Promise<Correcao[]> {
  const resposta = await lerComunidade(paragens, segredos);
  expect(resposta.status).toBe(200);
  return ((await resposta.json()) as { correcoes: Correcao[] }).correcoes;
}

async function erroDe(resposta: Response): Promise<{ estado: number; erro: string }> {
  return { estado: resposta.status, erro: ((await resposta.json()) as { erro: string }).erro };
}

async function linhasEmCorrecoes(): Promise<number> {
  return (await env.DB.prepare("SELECT COUNT(*) AS n FROM correcoes").first<number>("n"))!;
}

function inserir(id: string, contaId: string, paragem: string, tipo: string, estado: string, decididaEm: number | null, extra: { lat?: number; lon?: number; nome?: string } = {}) {
  return env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, lat, lon, nome, estado, criada_em, decidida_em) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    .bind(id, paragem, contaId, tipo, extra.lat ?? null, extra.lon ?? null, extra.nome ?? null, estado, Date.now(), decididaEm)
    .run();
}

describe("correções aos dados", () => {
  it("correcaoFicaPendente", async () => {
    const resposta = await corrigir(ana, POSICAO);
    expect(resposta.status).toBe(201);
    const { id } = (await resposta.json()) as { id: string };
    expect(id).toEqual(expect.any(String));

    expect(await env.DB.prepare("SELECT id, paragem, conta_id, tipo, lat, lon, nome, estado, criada_em, decidida_em FROM correcoes").all()).toMatchObject({
      results: [{ id, paragem: PARAGEM, conta_id: "ana", tipo: "posicao", lat: 38.7223, lon: -9.1393, nome: null, estado: "pendente", criada_em: INICIO, decidida_em: null }],
    });
    expect(await correcoesDe()).toEqual([]);
  });

  it("cadaTipoGuardaSoOQueLheDizRespeito", async () => {
    // Os campos que o tipo não usa são ignorados, como os que o servidor não conhece.
    expect((await corrigir(ana, { ...NOME, lat: 38.7, lon: -9.1, extra: "x" })).status).toBe(201);
    expect((await corrigir(ana, { paragem: "CM:2", tipo: "ja_nao_existe", lat: 38.7, lon: -9.1, nome: "x" })).status).toBe(201);
    expect((await corrigir(ana, { paragem: "CM:3", tipo: "posicao", lat: 38.7, lon: -9.1, nome: "x" })).status).toBe(201);
    const { results } = await env.DB.prepare("SELECT tipo, lat, lon, nome FROM correcoes ORDER BY paragem").all();
    expect(results).toEqual([
      { tipo: "nome", lat: null, lon: null, nome: "Praça do Comércio" },
      { tipo: "ja_nao_existe", lat: null, lon: null, nome: null },
      { tipo: "posicao", lat: 38.7, lon: -9.1, nome: null },
    ]);
  });

  it("nomeVemCortadoEContaCaracteresENaoBytes", async () => {
    expect((await corrigir(ana, { paragem: PARAGEM, tipo: "nome", nome: "  Rua do Ouro \n" })).status).toBe(201);
    expect(await env.DB.prepare("SELECT nome FROM correcoes").first<string>("nome")).toBe("Rua do Ouro");
    // 80 caracteres de 2 bytes (e um fora do plano básico, que ocupa 2 unidades em UTF-16) cabem; 81 não.
    expect((await corrigir(rui, { paragem: "CM:2", tipo: "nome", nome: "ã".repeat(79) + "😀" })).status).toBe(201);
    expect(await erroDe(await corrigir(rui, { paragem: "CM:3", tipo: "nome", nome: "ã".repeat(80) + "😀" }))).toEqual({ estado: 400, erro: "pedido_invalido" });
  });

  it("aprovadaAparecePorCimaDaFonte", async () => {
    await inserir("velha", "ana", PARAGEM, "nome", "aprovada", INICIO - 2 * DIA, { nome: "Nome antigo" });
    await inserir("nova", "rui", PARAGEM, "nome", "aprovada", INICIO - DIA, { nome: "Nome novo" });
    await inserir("mais-velha-da-posicao", "rui", PARAGEM, "posicao", "aprovada", INICIO - 3 * DIA, { lat: 38, lon: -9 });
    await inserir("posicao", "ana", PARAGEM, "posicao", "aprovada", INICIO - 2 * DIA, { lat: 38.5, lon: -9.5 });
    await inserir("fechada", "ana", PARAGEM, "ja_nao_existe", "aprovada", INICIO - DIA);

    const lidas = await correcoesDe();
    expect(lidas).toHaveLength(3);
    expect(lidas).toEqual(
      expect.arrayContaining([
        { paragem: PARAGEM, tipo: "nome", nome: "Nome novo" },
        { paragem: PARAGEM, tipo: "posicao", lat: 38.5, lon: -9.5 },
        { paragem: PARAGEM, tipo: "ja_nao_existe" },
      ]),
    );
  });

  it("aprovadasNoMesmoInstanteDaUmaSo", async () => {
    await inserir("a", "ana", PARAGEM, "nome", "aprovada", INICIO, { nome: "A" });
    await inserir("b", "rui", PARAGEM, "nome", "aprovada", INICIO, { nome: "B" });
    expect(await correcoesDe()).toEqual([{ paragem: PARAGEM, tipo: "nome", nome: "B" }]);
  });

  it("soAsAprovadasDasParagensPedidas", async () => {
    await inserir("pendente", "ana", PARAGEM, "nome", "pendente", null, { nome: "Pendente" });
    await inserir("rejeitada", "ana", PARAGEM, "posicao", "rejeitada", INICIO, { lat: 38, lon: -9 });
    await inserir("outra", "ana", "CM:outra", "nome", "aprovada", INICIO, { nome: "Outra" });
    await inserir("transitous", "rui", "TRANSITOUS:pt-X(CP)_1", "ja_nao_existe", "aprovada", INICIO);

    expect(await correcoesDe([PARAGEM])).toEqual([]);
    expect(await correcoesDe([PARAGEM, "TRANSITOUS:pt-X(CP)_1"])).toEqual([{ paragem: "TRANSITOUS:pt-X(CP)_1", tipo: "ja_nao_existe" }]);
  });

  it("leituraNaoMostraQuemCorrigiu", async () => {
    await inserir("f1", "ana", PARAGEM, "nome", "aprovada", INICIO, { nome: "Nome" });
    const corpo = await (await lerComunidade([PARAGEM])).text();
    expect(corpo).toContain("Nome");
    expect(corpo).not.toContain("ana");
    expect(corpo).not.toContain("conta");
  });

  it("posicaoForaDePortugalDa400", async () => {
    const mau = { estado: 400, erro: "pedido_invalido" };
    expect(await erroDe(await corrigir(ana, { ...POSICAO, lat: 48 }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...POSICAO, lon: 2.35 }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...POSICAO, lat: 31.9 }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...POSICAO, lon: -31.6 }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...POSICAO, lat: "38.7" }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { paragem: PARAGEM, tipo: "posicao" }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...POSICAO, lat: null }))).toEqual(mau);

    expect(await erroDe(await corrigir(ana, { ...NOME, nome: "" }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...NOME, nome: "   " }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...NOME, nome: "a".repeat(81) }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...NOME, nome: 7 }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { paragem: PARAGEM, tipo: "nome" }))).toEqual(mau);

    expect(await erroDe(await corrigir(ana, { ...NOME, tipo: "outro" }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { paragem: PARAGEM }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...NOME, paragem: "x" }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { ...NOME, paragem: "CM:<script>" }))).toEqual(mau);
    expect(await erroDe(await corrigir(ana, { tipo: "ja_nao_existe" }))).toEqual(mau);
    expect(await erroDe(await pedir("/api/correcoes", { token: ana, corpo: "isto não é json", segredos: LIGADO }))).toEqual(mau);
    expect(await linhasEmCorrecoes()).toBe(0);
  });

  it("fronteirasDePortugalEntram", async () => {
    for (const [i, [lat, lon]] of ([[32, -31.5], [42.2, -6.1]] as const).entries()) {
      expect((await corrigir(ana, { paragem: `CM:${i}`, tipo: "posicao", lat, lon })).status, `${lat},${lon}`).toBe(201);
    }
  });

  it("semSessaoDa401", async () => {
    expect(await erroDe(await corrigir(undefined, NOME))).toEqual({ estado: 401, erro: "sem_sessao" });
    expect(await linhasEmCorrecoes()).toBe(0);
  });

  it("decimaPrimeiraCorrecaoDa429", async () => {
    for (let i = 0; i < 10; i++) expect((await corrigir(ana, { ...NOME, paragem: `CM:${i}` })).status, `correção ${i + 1}`).toBe(201);
    expect(await erroDe(await corrigir(ana, { ...NOME, paragem: "CM:10" }))).toEqual({ estado: 429, erro: "muitos_pedidos" });
    expect(await linhasEmCorrecoes()).toBe(10);
    // O limite é por conta: outra conta não é afetada.
    expect((await corrigir(rui, NOME)).status).toBe(201);

    avancar(DIA);
    expect((await corrigir(ana, { ...NOME, paragem: "CM:10" })).status).toBe(201);
  });

  it("dozeEmParaleloSoDezEntram", async () => {
    const respostas = await Promise.all(Array.from({ length: 12 }, (_, i) => corrigir(ana, { ...NOME, paragem: `CM:${i}` })));
    expect(respostas.filter((r) => r.status === 201)).toHaveLength(10);
    expect(respostas.filter((r) => r.status === 429)).toHaveLength(2);
    expect(await linhasEmCorrecoes()).toBe(10);
  });

  it("correcoesDesligadasDa403EFicamForaDaLeitura", async () => {
    const soComunidade = { ...LIGADO, TESTE_INTERRUPTORES: JSON.stringify({ comunidade: true }) };
    expect(await erroDe(await corrigir(ana, NOME, soComunidade))).toEqual({ estado: 403, erro: "desligado" });
    await inserir("f1", "ana", PARAGEM, "nome", "aprovada", INICIO, { nome: "Nome" });
    expect(await correcoesDe([PARAGEM], soComunidade)).toEqual([]);
    expect(await correcoesDe([PARAGEM])).toHaveLength(1);
    expect(await linhasEmCorrecoes()).toBe(1);
  });

  it("contaBloqueadaDa403", async () => {
    await env.DB.prepare("INSERT INTO bloqueios (email_hash, motivo, criado_em) VALUES (?, 'teste', ?)")
      .bind(await sha256("ana@exemplo.pt"), Date.now())
      .run();
    expect(await erroDe(await corrigir(ana, NOME))).toEqual({ estado: 403, erro: "conta_bloqueada" });
    expect((await corrigir(rui, NOME)).status).toBe(201);
    expect(await linhasEmCorrecoes()).toBe(1);
  });
});
