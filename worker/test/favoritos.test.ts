import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { criarSessao } from "../src/sessoes";
import { limitador, pedir } from "./ajudas";

type Favorito = { chave: string; tipo: string; nome: string; lat: number; lon: number; ordem: number; apagado: boolean };
type Resposta = { revisao: number; alteracoes: (Favorito & { revisao: number })[] };

// Dois telemóveis da mesma conta (Ana) e um de outra pessoa (Rui).
let telemovelA: string;
let telemovelB: string;
let telemovelDoRui: string;

beforeEach(async () => {
  await env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES ('ana', 'ana@exemplo.pt', 1), ('rui', 'rui@exemplo.pt', 1)").run();
  telemovelA = await criarSessao(env, "ana");
  telemovelB = await criarSessao(env, "ana");
  telemovelDoRui = await criarSessao(env, "rui");
});

function favorito(chave: string, extra: Partial<Favorito> = {}): Favorito {
  return { chave, tipo: "PARAGEM", nome: `Paragem ${chave}`, lat: 38.72, lon: -9.14, ordem: 0, apagado: false, ...extra };
}

function sincronizar(token: string, desde: unknown, alteracoes: unknown = []): Promise<Response> {
  return pedir("/api/sincronizar", { token, corpo: { desde, alteracoes } });
}

async function sincronizado(token: string, desde: number, alteracoes: Favorito[] = []): Promise<Resposta> {
  const resposta = await sincronizar(token, desde, alteracoes);
  expect(resposta.status).toBe(200);
  return (await resposta.json()) as Resposta;
}

async function revisaoDaConta(id: string): Promise<number> {
  return (await env.DB.prepare("SELECT revisao FROM contas WHERE id = ?").bind(id).first<{ revisao: number }>())!.revisao;
}

async function linhas(contaId: string): Promise<{ chave: string; apagado: number; revisao: number }[]> {
  const { results } = await env.DB.prepare("SELECT chave, apagado, revisao FROM favoritos WHERE conta_id = ? ORDER BY chave").bind(contaId).all<{
    chave: string;
    apagado: number;
    revisao: number;
  }>();
  return results;
}

/** Põe `quantos` paragens favoritas na conta (revisões 1..quantos), sem passar pelo servidor. */
async function semear(contaId: string, quantos: number): Promise<void> {
  await env.DB.batch([
    env.DB.prepare(
      `WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < ?2)
       INSERT INTO favoritos (conta_id, chave, tipo, nome, lat, lon, ordem, revisao) SELECT ?1, 'CM:' || i, 'PARAGEM', 'P', 38.7, -9.1, i, i FROM n`,
    ).bind(contaId, quantos),
    env.DB.prepare("UPDATE contas SET revisao = ? WHERE id = ?").bind(quantos, contaId),
  ]);
}

describe("sincronizar favoritos", () => {
  it("primeiraSincronizacaoDevolveTudo", async () => {
    const doA = await sincronizado(telemovelA, 0, [favorito("CM:1"), favorito("CM:2", { ordem: 1 }), favorito("u-3", { tipo: "OUTRO", nome: "Café", ordem: 2 })]);
    expect(doA.revisao).toBe(3);
    expect(doA.alteracoes.map((f) => [f.chave, f.revisao])).toEqual([["CM:1", 1], ["CM:2", 2], ["u-3", 3]]);

    // Um telemóvel novo da mesma conta, com dois favoritos seus: junta o que tem com o que está no servidor.
    const doB = await sincronizado(telemovelB, 0, [favorito("CM:9"), favorito("CM:10")]);
    expect(doB.revisao).toBe(5);
    expect(doB.alteracoes.map((f) => f.chave).sort()).toEqual(["CM:1", "CM:10", "CM:2", "CM:9", "u-3"]);
    expect(doB.alteracoes.find((f) => f.chave === "u-3")).toEqual({ ...favorito("u-3", { tipo: "OUTRO", nome: "Café", ordem: 2 }), revisao: 3 });
    expect(await revisaoDaConta("ana")).toBe(5);
  });

  it("soDevolveODesdeARevisao", async () => {
    await sincronizado(telemovelA, 0, [favorito("CM:1"), favorito("CM:2"), favorito("CM:3")]);

    const depois = await sincronizado(telemovelB, 2);
    expect(depois.alteracoes.map((f) => f.chave)).toEqual(["CM:3"]);
    expect(depois.revisao).toBe(3);

    // Sem nada de novo: lista vazia, mesma revisão, e a revisão da conta não sobe.
    const nada = await sincronizado(telemovelB, 3);
    expect(nada).toEqual({ revisao: 3, alteracoes: [] });
    expect(await revisaoDaConta("ana")).toBe(3);
  });

  it("aRespostaIncluiAsAlteracoesQueAcabaramDeChegar", async () => {
    await sincronizado(telemovelA, 0, [favorito("CM:1")]);
    const resposta = await sincronizado(telemovelB, 1, [favorito("CM:2")]);
    expect(resposta.alteracoes.map((f) => [f.chave, f.revisao])).toEqual([["CM:2", 2]]);
    expect(resposta.revisao).toBe(2);
  });

  it("ultimaAChegarGanha", async () => {
    await sincronizado(telemovelA, 0, [favorito("CM:1", { nome: "Do telemóvel A", ordem: 1 })]);
    await sincronizado(telemovelB, 1, [favorito("CM:1", { nome: "Do telemóvel B", ordem: 4 })]);

    expect(await linhas("ana")).toEqual([{ chave: "CM:1", apagado: 0, revisao: 2 }]);
    const doA = await sincronizado(telemovelA, 1);
    expect(doA.alteracoes).toEqual([{ ...favorito("CM:1", { nome: "Do telemóvel B", ordem: 4 }), revisao: 2 }]);

    // Dentro do mesmo pedido também ganha a última.
    await sincronizado(telemovelA, 2, [favorito("CM:7", { nome: "Primeira" }), favorito("CM:7", { nome: "Segunda" })]);
    expect((await sincronizado(telemovelB, 2)).alteracoes.filter((f) => f.chave === "CM:7").map((f) => f.nome)).toEqual(["Segunda"]);
  });

  it("novaCasaApagaAAntiga", async () => {
    await sincronizado(telemovelA, 0, [
      favorito("casa-1", { tipo: "CASA", nome: "Casa" }),
      favorito("trabalho-1", { tipo: "TRABALHO", nome: "Trabalho" }),
      favorito("CM:1"),
      favorito("outro-1", { tipo: "OUTRO", nome: "Ginásio" }),
    ]);
    await sincronizado(telemovelDoRui, 0, [favorito("casa-do-rui", { tipo: "CASA", nome: "Casa" })]);

    // Outro telemóvel muda a Casa: a antiga desaparece com uma revisão nova, ganha a que chegou por último.
    const resposta = await sincronizado(telemovelB, 4, [favorito("casa-2", { tipo: "CASA", nome: "Casa nova" })]);
    const porChave = new Map(resposta.alteracoes.map((f) => [f.chave, f]));
    expect([...porChave.keys()].sort()).toEqual(["casa-1", "casa-2"]);
    expect(porChave.get("casa-1")).toMatchObject({ tipo: "CASA", apagado: true });
    // A antiga apagada chega com a mesma revisão da nova, que é a 5.ª alteração da conta.
    expect(porChave.get("casa-1")!.revisao).toBe(5);
    expect(porChave.get("casa-2")).toMatchObject({ apagado: false, nome: "Casa nova", revisao: 5 });
    expect(resposta.revisao).toBe(5);

    // O Trabalho, a paragem, o outro sítio e a Casa de outra conta ficam como estavam.
    expect(await linhas("ana")).toMatchObject([
      { chave: "CM:1", apagado: 0 },
      { chave: "casa-1", apagado: 1 },
      { chave: "casa-2", apagado: 0 },
      { chave: "outro-1", apagado: 0 },
      { chave: "trabalho-1", apagado: 0 },
    ]);
    expect(await linhas("rui")).toMatchObject([{ chave: "casa-do-rui", apagado: 0 }]);

    // Um Trabalho novo só mexe no Trabalho.
    await sincronizado(telemovelB, resposta.revisao, [favorito("trabalho-2", { tipo: "TRABALHO", nome: "Trabalho novo" })]);
    expect(await linhas("ana")).toMatchObject([
      { chave: "CM:1", apagado: 0 },
      { chave: "casa-1", apagado: 1 },
      { chave: "casa-2", apagado: 0 },
      { chave: "outro-1", apagado: 0 },
      { chave: "trabalho-1", apagado: 1 },
      { chave: "trabalho-2", apagado: 0 },
    ]);
  });

  it("casaApagadaNaoApagaAsOutras", async () => {
    await sincronizado(telemovelA, 0, [favorito("casa-1", { tipo: "CASA" })]);
    await sincronizado(telemovelB, 1, [favorito("casa-2", { tipo: "CASA", apagado: true })]);
    expect(await linhas("ana")).toMatchObject([
      { chave: "casa-1", apagado: 0 },
      { chave: "casa-2", apagado: 1 },
    ]);
  });

  it("apagadoChegaAoOutroTelemovel", async () => {
    const inicial = await sincronizado(telemovelA, 0, [favorito("u-1", { tipo: "OUTRO", nome: "Escola" }), favorito("CM:1")]);
    await sincronizado(telemovelB, 0);

    await sincronizado(telemovelA, inicial.revisao, [favorito("u-1", { tipo: "OUTRO", nome: "Escola", apagado: true })]);

    const noB = await sincronizado(telemovelB, inicial.revisao);
    expect(noB.alteracoes).toEqual([{ ...favorito("u-1", { tipo: "OUTRO", nome: "Escola", apagado: true }), revisao: 3 }]);
    expect(noB.revisao).toBe(3);
  });

  it("contasNaoSeVeemUmaAOutra", async () => {
    await sincronizado(telemovelA, 0, [favorito("CM:1"), favorito("u-1", { tipo: "OUTRO" })]);
    const doRui = await sincronizado(telemovelDoRui, 0, [favorito("CM:1", { nome: "Do Rui" })]);
    expect(doRui.alteracoes).toEqual([{ ...favorito("CM:1", { nome: "Do Rui" }), revisao: 1 }]);
    expect((await sincronizado(telemovelA, 0)).alteracoes.find((f) => f.chave === "CM:1")!.nome).toBe("Paragem CM:1");
  });

  it("semSessaoDa401", async () => {
    const corpo = { desde: 0, alteracoes: [favorito("CM:1")] };
    for (const opcoes of [{ corpo }, { corpo, token: "nao-existe" }]) {
      const resposta = await pedir("/api/sincronizar", opcoes);
      expect(resposta.status).toBe(401);
      expect(await resposta.json()).toEqual({ erro: "sem_sessao" });
    }
    expect(await linhas("ana")).toEqual([]);
  });

  it("demasiadasAlteracoesDa400", async () => {
    const muitas = Array.from({ length: 501 }, (_, i) => favorito(`CM:${i}`));
    const recusada = await sincronizar(telemovelA, 0, muitas);
    expect(recusada.status).toBe(400);
    expect(await recusada.json()).toEqual({ erro: "demasiadas_alteracoes" });
    expect(await linhas("ana")).toEqual([]);

    const limite = await sincronizado(telemovelA, 0, muitas.slice(0, 500));
    expect(limite.alteracoes).toHaveLength(500);
    expect(limite.revisao).toBe(500);
  });

  it("pedidoInvalidoDa400ENaoGravaNada", async () => {
    const boa = favorito("CM:1");
    const invalidos: [string, unknown, unknown][] = [
      ["desde em falta", undefined, [boa]],
      ["desde negativo", -1, [boa]],
      ["desde com casas decimais", 1.5, [boa]],
      ["desde em texto", "0", [boa]],
      ["alteracoes que não é lista", 0, { ...boa }],
      ["alteração que não é objeto", 0, [boa, "CM:2"]],
      ["chave vazia", 0, [boa, favorito("")]],
      ["tipo desconhecido", 0, [boa, favorito("CM:2", { tipo: "ESCOLA" })]],
      ["nome com 121 caracteres", 0, [boa, favorito("CM:2", { nome: "a".repeat(121) })]],
      ["nome que não é texto", 0, [boa, { ...favorito("CM:2"), nome: 7 }]],
      ["latitude acima de 90", 0, [boa, favorito("CM:2", { lat: 90.1 })]],
      ["latitude em texto", 0, [boa, { ...favorito("CM:2"), lat: "38.7" }]],
      ["longitude abaixo de -180", 0, [boa, favorito("CM:2", { lon: -180.1 })]],
      ["longitude nula", 0, [boa, { ...favorito("CM:2"), lon: null }]],
      ["ordem com casas decimais", 0, [boa, favorito("CM:2", { ordem: 0.5 })]],
      ["apagado em texto", 0, [boa, { ...favorito("CM:2"), apagado: "false" }]],
    ];
    for (const [descricao, desde, alteracoes] of invalidos) {
      const resposta = await sincronizar(telemovelA, desde, alteracoes);
      expect(resposta.status, descricao).toBe(400);
      expect(await resposta.json(), descricao).toEqual({ erro: "pedido_invalido" });
    }
    // JSON estragado e corpo em falta.
    expect((await pedir("/api/sincronizar", { token: telemovelA, corpo: "{" })).status).toBe(400);
    expect((await pedir("/api/sincronizar", { token: telemovelA })).status).toBe(400);
    // Tudo ou nada: a alteração boa que ia junto da má também não ficou.
    expect(await linhas("ana")).toEqual([]);
    expect(await revisaoDaConta("ana")).toBe(0);
  });

  it("favoritoDeLinhaSincroniza", async () => {
    const linha = favorito("LINHA|CM:1009|0|CM:060419", { tipo: "LINHA", nome: "1009 · Sentido Lisboa" });
    const outroSentido = favorito("LINHA|CM:1009|1|CM:060419", { tipo: "LINHA", nome: "1009 · Sentido Setúbal", ordem: 1 });
    const doA = await sincronizado(telemovelA, 0, [linha, outroSentido]);
    // Duas linhas favoritas do mesmo tipo convivem (só a Casa e o Trabalho são únicos).
    expect(doA.alteracoes.map((f) => [f.chave, f.tipo, f.apagado])).toEqual([["LINHA|CM:1009|0|CM:060419", "LINHA", false], ["LINHA|CM:1009|1|CM:060419", "LINHA", false]]);

    const doB = await sincronizado(telemovelB, 0);
    expect(doB.alteracoes).toEqual([{ ...linha, revisao: 1 }, { ...outroSentido, revisao: 2 }]);

    // Tirar a ★ chega ao outro telemóvel como as outras remoções.
    await sincronizado(telemovelB, 2, [{ ...linha, apagado: true }]);
    const depois = await sincronizado(telemovelA, 2);
    expect(depois.alteracoes.map((f) => [f.chave, f.apagado])).toEqual([["LINHA|CM:1009|0|CM:060419", true]]);
  });

  it("favoritoDeLinhaDaCpSincroniza", async () => {
    // Uma da CP sem nome curto: o nome longo e os ids com "ó" e parênteses passam dos 120 caracteres.
    const prefixo = "LINHA|TRANSITOUS:pt-Comboios-de-Portugal(CP):Linha de Cascais - Cais do Sodré (Lisboa) ó Cascais|0|TRANSITOUS:pt-Comboios-de-Portugal(CP)_94_";
    const chave = `${prefixo}${"6".repeat(180 - prefixo.length)}`;
    expect(chave).toHaveLength(180);

    const resposta = await sincronizado(telemovelA, 0, [favorito(chave, { tipo: "LINHA", nome: "Linha de Cascais" })]);
    expect(resposta.alteracoes.map((f) => f.chave)).toEqual([chave]);
    expect(await linhas("ana")).toEqual([{ chave, apagado: 0, revisao: 1 }]);
  });

  it("favoritoDeLinhaSemPrefixoOuComMaisDe250Da400", async () => {
    const boa = favorito("CM:1");
    const prefixo = "LINHA|TRANSITOUS:pt-Carris:728|0|";
    const invalidos: [string, Favorito][] = [
      ["sem o prefixo LINHA|", favorito("CM:1009|0|CM:060419", { tipo: "LINHA" })],
      ["prefixo em minúsculas", favorito("linha|CM:1009|0|CM:060419", { tipo: "LINHA" })],
      ["só o tipo, a chave de uma paragem", favorito("CM:060419", { tipo: "LINHA" })],
      // O nome curto, para o que falha ser só a chave (o `favorito()` faz o nome a partir dela).
      ["251 caracteres", favorito(`${prefixo}${"x".repeat(251 - prefixo.length)}`, { tipo: "LINHA", nome: "728" })],
      // Os outros tipos continuam nos 120.
      ["paragem com 121 caracteres", favorito(`CM:${"1".repeat(118)}`, { nome: "Paragem" })],
    ];
    for (const [descricao, mau] of invalidos) {
      const resposta = await sincronizar(telemovelA, 0, [boa, mau]);
      expect(resposta.status, descricao).toBe(400);
      expect(await resposta.json(), descricao).toEqual({ erro: "pedido_invalido" });
    }
    // Tudo ou nada: o bom que ia junto também não ficou.
    expect(await linhas("ana")).toEqual([]);

    // Nos 250 certos serve, e o que não é LINHA continua a servir com 120.
    const nos250 = favorito(`${prefixo}${"x".repeat(250 - prefixo.length)}`, { tipo: "LINHA", nome: "728" });
    const nos120 = favorito(`CM:${"1".repeat(117)}`, { nome: "Paragem" });
    expect(nos250.chave).toHaveLength(250);
    expect(nos120.chave).toHaveLength(120);
    expect((await sincronizado(telemovelA, 0, [nos250, nos120])).alteracoes).toHaveLength(2);
  });

  it("nomeComExatamente120CaracteresServe", async () => {
    const resposta = await sincronizado(telemovelA, 0, [favorito("CM:1", { nome: "ã".repeat(120) }), favorito("CM:2", { lat: -90, lon: 180 })]);
    expect(resposta.alteracoes).toHaveLength(2);
  });

  it("casasRepetidasNoPedidoLeemAContaUmaVez", async () => {
    await semear("ana", 500);
    let lidas = 0;
    // Conta as linhas lidas pelo batch da sincronização.
    const DB = {
      prepare: (sql: string) => env.DB.prepare(sql),
      batch: async (passos: D1PreparedStatement[]) => {
        const resultados = await env.DB.batch(passos);
        lidas += resultados.reduce((total, r) => total + r.meta.rows_read, 0);
        return resultados;
      },
    } as unknown as D1Database;
    const casas = Array.from({ length: 500 }, (_, i) => favorito(`casa-${i}`, { tipo: "CASA", nome: "Casa" }));

    const resposta = await pedir("/api/sincronizar", { token: telemovelA, corpo: { desde: 500, alteracoes: casas }, segredos: { DB } });
    expect(resposta.status).toBe(200);
    expect(lidas).toBeLessThan(20_000);
    // Fica só a última Casa do pedido, como antes.
    const vivas = await env.DB.prepare("SELECT chave FROM favoritos WHERE conta_id = 'ana' AND tipo = 'CASA' AND apagado = 0").all<{ chave: string }>();
    expect(vivas.results.map((linha) => linha.chave)).toEqual(["casa-499"]);
  });

  it("tetosDaContaRecusamAlteracoesMasNaoALeitura", async () => {
    // Reenviar as mesmas 500 não aumenta as linhas, mas gasta as 1000 alterações do dia.
    const quinhentas = Array.from({ length: 500 }, (_, i) => favorito(`CM:${i}`));
    await sincronizado(telemovelA, 0, quinhentas);
    await sincronizado(telemovelA, 500, quinhentas.map((f) => ({ ...f, nome: "Outro nome" })));
    expect(await linhas("ana")).toHaveLength(500);
    const semDia = await sincronizar(telemovelA, 1000, [favorito("CM:novo")]);
    expect(semDia.status).toBe(429);
    expect(await semDia.json()).toEqual({ erro: "muitos_pedidos" });
    expect(await revisaoDaConta("ana")).toBe(1000);

    // Com 1000 linhas (apagadas incluídas) já não entra mais nenhuma, e a recusa não gasta o dia.
    await semear("rui", 1000);
    const cheia = await sincronizar(telemovelDoRui, 1000, [favorito("CM:novo")]);
    expect(cheia.status).toBe(403);
    expect(await cheia.json()).toEqual({ erro: "demasiados_favoritos" });
    expect(await linhas("rui")).toHaveLength(1000);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM contadores WHERE nome = 'favoritos:rui'").first("n")).toBe(0);

    // Sem alterações, as duas contas continuam a receber o que mudou.
    expect((await sincronizado(telemovelB, 999)).alteracoes).toHaveLength(1);
    expect((await sincronizado(telemovelDoRui, 999)).alteracoes).toHaveLength(1);
  });

  it("decimaPrimeiraSincronizacaoNoMinutoDa429", async () => {
    const limite = limitador(10);
    for (let vez = 1; vez <= 10; vez++) {
      expect((await pedir("/api/sincronizar", { token: telemovelA, corpo: { desde: 0, alteracoes: [] }, segredos: { LIMITADOR: limite } })).status, `vez ${vez}`).toBe(200);
    }
    const decimaPrimeira = await pedir("/api/sincronizar", { token: telemovelA, corpo: { desde: 0, alteracoes: [] }, segredos: { LIMITADOR: limite } });
    expect(decimaPrimeira.status).toBe(429);
    expect(await decimaPrimeira.json()).toEqual({ erro: "devagar" });
    expect(limite.chaves).toEqual(Array(11).fill("sincronizar:ana"));
  });
});
