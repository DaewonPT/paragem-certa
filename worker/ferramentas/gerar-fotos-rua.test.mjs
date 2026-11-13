import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { after, test } from "node:test";
import { escolherFotos, gerarFicheiro, normalizarFoto, obterFicheiro, procurarFotos, ZONA } from "./gerar-fotos-rua.mjs";

const GERADOR = path.join(import.meta.dirname, "gerar-fotos-rua.mjs");
const DIA = "2026-11-03";
const METROS_POR_GRAU = 111195;
const PARAGEM = { chave: "CM:060419", lat: 38.7, lon: -9.1 };
const pastas = [];
after(() => { for (const p of pastas) fs.rmSync(p, { recursive: true, force: true }); });

/** Uma foto (já normalizada) a `metros` da paragem, de onde o rumo até à paragem é `rumo`; olha nesse rumo, salvo `azimute`. */
function fotoA(metros, rumo, campos = {}, paragem = PARAGEM) {
  const r = (rumo * Math.PI) / 180;
  return {
    id: "a",
    lat: paragem.lat - (metros * Math.cos(r)) / METROS_POR_GRAU,
    lon: paragem.lon - (metros * Math.sin(r)) / (METROS_POR_GRAU * Math.cos((paragem.lat * Math.PI) / 180)),
    azimute: rumo,
    miniatura: "https://panoramax.example/a/thumb.jpg",
    autor: "Ana",
    licenca: "CC BY-SA 4.0",
    data: "2022-03-13",
    ...campos,
  };
}

/** Uma foto como a devolve o /api/search do Panoramax. */
function feature({ id = "a", lat = 38.7, lon = -9.1, propriedades = {}, ...resto } = {}) {
  return {
    type: "Feature",
    id,
    geometry: { type: "Point", coordinates: [lon, lat] },
    properties: {
      datetime: "2022-03-13T10:20:30+00:00",
      "view:azimuth": 90,
      license: "CC-BY-SA-4.0",
      "geovisio:status": "ready",
      ...propriedades,
    },
    providers: [{ name: "Ana", roles: ["producer"] }],
    assets: { thumb: { href: `https://panoramax.example/${id}/thumb.jpg` } },
    ...resto,
  };
}

const muitas = (n, prefixo = "f") => Array.from({ length: n }, (_, i) => feature({ id: `${prefixo}${i}` }));

// escolherFotos

test("escolheAMaisRecenteVirada", () => {
  const fotos = [fotoA(10, 0, { id: "velha", data: "2017-05-01" }), fotoA(20, 90, { id: "nova", data: "2026-01-02" }), fotoA(25, 180, { id: "meio", data: "2022-03-13" })];
  assert.equal(escolherFotos([PARAGEM], fotos, new Set())[PARAGEM.chave].id, "nova");
});

test("aMaisDe30mNaoConta", () => {
  assert.equal(escolherFotos([PARAGEM], [fotoA(29, 0)], new Set())[PARAGEM.chave].id, "a");
  assert.deepEqual(escolherFotos([PARAGEM], [fotoA(31, 0)], new Set()), {});
});

test("fotoNaCelulaDaGrelhaAoLadoConta", () => {
  // A paragem fica mesmo no canto de uma célula de 0,001°: a foto cai na célula ao lado, seja de que lado for.
  const paragem = { chave: "CM:1", lat: 38.7000001, lon: -9.1000001 };
  for (const rumo of [0, 45, 90, 135, 180, 225, 270, 315]) {
    assert.equal(escolherFotos([paragem], [fotoA(25, rumo, {}, paragem)], new Set())[paragem.chave]?.id, "a", `rumo ${rumo}`);
  }
});

test("direcaoFora45GrausNaoConta", () => {
  assert.equal(escolherFotos([PARAGEM], [fotoA(20, 90, { azimute: 130 })], new Set())[PARAGEM.chave].id, "a");
  assert.deepEqual(escolherFotos([PARAGEM], [fotoA(20, 90, { azimute: 140 })], new Set()), {});
  assert.deepEqual(escolherFotos([PARAGEM], [fotoA(20, 90, { azimute: 270 })], new Set()), {});
});

test("direcaoPassaPeloNorte", () => {
  assert.equal(escolherFotos([PARAGEM], [fotoA(20, 350, { azimute: 20 })], new Set())[PARAGEM.chave].id, "a");
  assert.deepEqual(escolherFotos([PARAGEM], [fotoA(20, 350, { azimute: 60 })], new Set()), {});
  // E ao contrário: a foto olha para antes do norte e o rumo vem depois dele.
  assert.equal(escolherFotos([PARAGEM], [fotoA(20, 20, { azimute: 350 })], new Set())[PARAGEM.chave].id, "a");
});

test("fotoExcluidaNaoConta", () => {
  const fotos = [fotoA(10, 0, { id: "ma", data: "2026-01-01" }), fotoA(10, 0, { id: "boa", data: "2020-01-01" })];
  assert.equal(escolherFotos([PARAGEM], fotos, new Set(["ma"]))[PARAGEM.chave].id, "boa");
  assert.deepEqual(escolherFotos([PARAGEM], fotos, new Set(["ma", "boa"])), {});
});

test("empateFicaEstavel", () => {
  const b = fotoA(10, 0, { id: "b" });
  const a = fotoA(20, 0, { id: "a" });
  assert.equal(escolherFotos([PARAGEM], [a, b], new Set())[PARAGEM.chave].id, "a");
  assert.equal(escolherFotos([PARAGEM], [b, a], new Set())[PARAGEM.chave].id, "a");
});

test("umaFotoServeDuasParagens", () => {
  const outra = { chave: "TRANSITOUS:pt-Carris_1", lat: PARAGEM.lat + 0.00005, lon: PARAGEM.lon };
  const r = escolherFotos([PARAGEM, outra], [fotoA(10, 0, { id: "mesma" })], new Set());
  assert.equal(r[PARAGEM.chave].id, "mesma");
  assert.equal(r[outra.chave].id, "mesma");
});

// normalizarFoto

test("normalizaOFormatoDoPanoramax", () => {
  assert.deepEqual(normalizarFoto(feature({ lat: 38.7, lon: -9.1 })), {
    id: "a",
    lat: 38.7,
    lon: -9.1,
    azimute: 90,
    miniatura: "https://panoramax.example/a/thumb.jpg",
    autor: "Ana",
    licenca: "CC BY-SA 4.0",
    data: "2022-03-13",
  });
});

test("semAzimuteFicaDeFora", () => {
  assert.equal(normalizarFoto(feature({ propriedades: { "view:azimuth": undefined } })), null);
  assert.equal(normalizarFoto(feature({ propriedades: { "view:azimuth": null } })), null);
  // 0° é o norte, não é falta de direção.
  assert.equal(normalizarFoto(feature({ propriedades: { "view:azimuth": 0 } })).azimute, 0);
});

test("licencaDesconhecidaFicaDeFora", () => {
  assert.equal(normalizarFoto(feature({ propriedades: { license: "proprietary" } })), null);
  assert.equal(normalizarFoto(feature({ propriedades: { license: undefined } })), null);
  // Uma licença com o nome de uma propriedade de qualquer objeto também não conta.
  assert.equal(normalizarFoto(feature({ propriedades: { license: "constructor" } })), null);
});

test("licencasConhecidasFicamEscritas", () => {
  const licenca = (license) => normalizarFoto(feature({ propriedades: { license } })).licenca;
  assert.equal(licenca("CC-BY-SA-4.0"), "CC BY-SA 4.0");
  assert.equal(licenca("etalab-2.0"), "Licence Ouverte 2.0");
  assert.equal(licenca("CC0-1.0"), "CC0");
  assert.equal(licenca("CC0"), "CC0");
});

test("semAutorFicaDeFora", () => {
  assert.equal(normalizarFoto(feature({ providers: [] })), null);
  assert.equal(normalizarFoto(feature({ providers: undefined })), null);
  assert.equal(normalizarFoto(feature({ providers: [{ name: "  " }] })), null);
  assert.equal(normalizarFoto(feature({ providers: [{}] })), null);
});

test("miniaturaSemHttpsFicaDeFora", () => {
  assert.equal(normalizarFoto(feature({ assets: { thumb: { href: "http://panoramax.example/a/thumb.jpg" } } })), null);
  assert.equal(normalizarFoto(feature({ assets: {} })), null);
  assert.equal(normalizarFoto(feature({ assets: undefined })), null);
});

test("naoProntaFicaDeFora", () => {
  assert.equal(normalizarFoto(feature({ propriedades: { "geovisio:status": "hidden" } })), null);
  assert.equal(normalizarFoto(feature({ propriedades: { "geovisio:status": undefined } })), null);
});

test("semDataOuSemSitioFicaDeFora", () => {
  assert.equal(normalizarFoto(feature({ propriedades: { datetime: null } })), null);
  assert.equal(normalizarFoto(feature({ propriedades: { datetime: "ontem" } })), null);
  assert.equal(normalizarFoto(feature({ geometry: null })), null);
  assert.equal(normalizarFoto(feature({ id: "" })), null);
  assert.equal(normalizarFoto(null), null);
});

// procurarFotos

test("zonaCheiaDivideEmQuatro", async () => {
  const pedidos = [];
  const buscar = async (url) => {
    pedidos.push(url);
    if (pedidos.length === 1) return { features: muitas(1000, "grande") };
    // A foto em cima da fronteira vem nas caixas dos dois lados.
    return { features: [feature({ id: "fronteira" }), ...muitas(199, `${pedidos.length}-`)] };
  };
  const fotos = await procurarFotos(ZONA, buscar);
  const params = pedidos.map((u) => new URL(u).searchParams);
  assert.equal(params[0].get("bbox"), "-9.6,38.4,-8.6,39.1");
  assert.ok(params.every((p) => p.get("limit") === "1000"));
  assert.deepEqual(params.slice(1).map((p) => p.get("bbox")).sort(), [
    "-9.1,38.4,-8.6,38.75",
    "-9.1,38.75,-8.6,39.1",
    "-9.6,38.4,-9.1,38.75",
    "-9.6,38.75,-9.1,39.1",
  ]);
  assert.equal(fotos.length, 4 * 199 + 1);
  assert.equal(new Set(fotos.map((f) => f.id)).size, fotos.length);
  assert.ok(!fotos.some((f) => f.id.startsWith("grande")));
});

test("tudoNoMesmoSitioNaoDivideParaSempre", async () => {
  let pedidos = 0;
  const buscar = async (url) => {
    pedidos++;
    const [lonMin, latMin, lonMax, latMax] = new URL(url).searchParams.get("bbox").split(",").map(Number);
    const temOSitio = lonMin <= -9.2 && -9.2 <= lonMax && latMin <= 38.7 && 38.7 <= latMax;
    return { features: temOSitio ? muitas(1000) : [] };
  };
  const fotos = await procurarFotos(ZONA, buscar);
  assert.equal(fotos.length, 1000);
  assert.ok(pedidos < 100, `${pedidos} pedidos`);
});

test("paginaFalhadaFazFalharAGeracao", async () => {
  let pedidos = 0;
  const buscar = async () => {
    pedidos++;
    if (pedidos === 1) return { features: muitas(1000) };
    if (pedidos === 3) throw new Error("500");
    return { features: muitas(600) };
  };
  await assert.rejects(procurarFotos(ZONA, buscar), /500/);
});

test("semFeaturesFazFalhar", async () => {
  await assert.rejects(procurarFotos(ZONA, async () => ({ links: [] })));
  await assert.rejects(procurarFotos(ZONA, async () => ({ features: "nada" })));
  await assert.rejects(procurarFotos(ZONA, async () => null));
});

test("poucasFotosFazFalhar", async () => {
  await assert.rejects(procurarFotos(ZONA, async () => ({ features: muitas(499) })), /499/);
  assert.equal((await procurarFotos(ZONA, async () => ({ features: muitas(500) }))).length, 500);
  // Uma mudança da API que estrague todas as fotos não pode apagar as que estão no ar.
  const semLicenca = muitas(900).map((f) => ({ ...f, properties: { ...f.properties, license: "nova" } }));
  await assert.rejects(procurarFotos(ZONA, async () => ({ features: semLicenca })), /\b0\b/);
});

// gerarFicheiro

const LINHAS = { paragens: { "pt-Carris_2": ["Cais Sodré", 38.71, -9.12], "pt-Carris_1": ["Rossio", 38.7, -9.1] } };
const CM = [{ id: "060419", lat: 38.7, lon: -9.1 }, { id: "062000", lat: 38.5, lon: -9.0 }];

/** Um `buscar` falso com as paragens da CM e as `features` dadas. */
const buscarFalso = (features) => async (url) => (url.includes("carrismetropolitana") ? CM : { features });

test("gerarJuntaAsParagensDasDuasFontes", async () => {
  const perto = feature({ id: "perto", lat: 38.7 - 20 / METROS_POR_GRAU, propriedades: { "view:azimuth": 0 } });
  const ficheiro = await gerarFicheiro({ dia: DIA, linhas: LINHAS, excluidas: new Set(), buscar: buscarFalso([perto, ...muitas(500)]) });
  const foto = { id: "perto", miniatura: "https://panoramax.example/perto/thumb.jpg", autor: "Ana", licenca: "CC BY-SA 4.0", data: "2022-03-13" };
  assert.deepEqual(ficheiro, { versao: 1, geradoEm: DIA, fotos: { "CM:060419": foto, "TRANSITOUS:pt-Carris_1": foto } });
  // O FotosDeRuaTest.kt (leOFicheiroDoGerador) lê este mesmo ficheiro com o leitor da app.
  const gravado = path.join(import.meta.dirname, "gerado", "fotos-de-rua.json");
  if (process.env.GRAVAR) {
    fs.mkdirSync(path.dirname(gravado), { recursive: true });
    fs.writeFileSync(gravado, JSON.stringify(ficheiro, null, 1) + "\n");
  }
  assert.deepEqual(JSON.parse(fs.readFileSync(gravado, "utf8")), ficheiro, "o fotos-de-rua.json mudou: corre GRAVAR=1 npm run ferramentas, revê a diferença e corre o :core:test");
});

test("carrisMetropolitanaEmBaixoFazFalhar", async () => {
  const comCm = (cm) => async (url) => {
    if (!url.includes("carrismetropolitana")) return { features: muitas(500) };
    if (cm === null) throw new Error("503");
    return cm;
  };
  await assert.rejects(gerarFicheiro({ dia: DIA, linhas: LINHAS, excluidas: new Set(), buscar: comCm(null) }), /503/);
  await assert.rejects(gerarFicheiro({ dia: DIA, linhas: LINHAS, excluidas: new Set(), buscar: comCm({ erro: "x" }) }));
  await assert.rejects(gerarFicheiro({ dia: DIA, linhas: LINHAS, excluidas: new Set(), buscar: comCm([]) }));
});

test("geradoDuasVezesDaOMesmo", async () => {
  const perto = (id, data) => feature({ id, lat: 38.7 - 20 / METROS_POR_GRAU, propriedades: { "view:azimuth": 0, datetime: `${data}T08:00:00Z` } });
  const features = [perto("b", "2024-01-01"), perto("a", "2024-01-01"), perto("c", "2017-01-01"), ...muitas(500)];
  const umaVez = await gerarFicheiro({ dia: DIA, linhas: LINHAS, excluidas: new Set(), buscar: buscarFalso(features) });
  const outra = await gerarFicheiro({
    dia: DIA,
    linhas: { paragens: Object.fromEntries(Object.entries(LINHAS.paragens).reverse()) },
    excluidas: new Set(),
    buscar: buscarFalso([...features].reverse()),
  });
  assert.equal(JSON.stringify(umaVez), JSON.stringify(outra));
  assert.deepEqual(Object.keys(umaVez.fotos), ["CM:060419", "TRANSITOUS:pt-Carris_1"]);
  assert.equal(umaVez.fotos["CM:060419"].id, "a");
});

// obterFicheiro

const NO_AR = { versao: 1, geradoEm: "2026-10-05", fotos: { "CM:1": { id: "x" } } };
const NOVO = { versao: 1, geradoEm: DIA, fotos: { "CM:2": { id: "y" } } };
const gerarBem = async () => NOVO;
const gerarMal = async () => { throw new Error("Panoramax em baixo"); };
const proibido = async () => assert.fail("não devia gerar");

test("manterCopiaADoAr", async () => {
  assert.deepEqual(await obterFicheiro({ modo: "manter", dia: "2026-11-02", noAr: NO_AR, gerar: proibido }), NO_AR);
});

test("manterSemNadaNoArGera", async () => {
  assert.deepEqual(await obterFicheiro({ modo: "manter", dia: "2026-11-02", noAr: null, gerar: gerarBem }), NOVO);
});

test("mensalNoDia8CopiaADoAr", async () => {
  assert.deepEqual(await obterFicheiro({ modo: "mensal", dia: "2026-11-08", noAr: NO_AR, gerar: proibido }), NO_AR);
});

test("mensalNoDia1Gera", async () => {
  assert.deepEqual(await obterFicheiro({ modo: "mensal", dia: "2026-11-01", noAr: NO_AR, gerar: gerarBem }), NOVO);
  assert.deepEqual(await obterFicheiro({ modo: "mensal", dia: "2026-11-07", noAr: NO_AR, gerar: gerarBem }), NOVO);
});

test("mensalSemNadaNoArGera", async () => {
  assert.deepEqual(await obterFicheiro({ modo: "mensal", dia: "2026-11-20", noAr: null, gerar: gerarBem }), NOVO);
});

test("sempreGera", async () => {
  assert.deepEqual(await obterFicheiro({ modo: "sempre", dia: "2026-11-20", noAr: NO_AR, gerar: gerarBem }), NOVO);
});

test("geracaoFalhadaFicaComADoAr", async () => {
  assert.deepEqual(await obterFicheiro({ modo: "sempre", dia: DIA, noAr: NO_AR, gerar: gerarMal }), NO_AR);
  assert.deepEqual(await obterFicheiro({ modo: "mensal", dia: "2026-11-02", noAr: NO_AR, gerar: gerarMal }), NO_AR);
});

test("semNadaNoArFicaVazioEValido", async () => {
  const vazio = { versao: 1, geradoEm: DIA, fotos: {} };
  assert.deepEqual(await obterFicheiro({ modo: "manter", dia: DIA, noAr: null, gerar: gerarMal }), vazio);
  assert.deepEqual(await obterFicheiro({ modo: "sempre", dia: DIA, noAr: null, gerar: gerarMal }), vazio);
});

test("noArEstragadoOuVazioContaComoNada", async () => {
  for (const noAr of [{}, [], "x", { versao: 2, fotos: { "CM:1": {} } }, { versao: 1 }, { versao: 1, fotos: "x" }, { versao: 1, geradoEm: "2026-10-05", fotos: {} }]) {
    assert.deepEqual(await obterFicheiro({ modo: "manter", dia: DIA, noAr, gerar: gerarBem }), NOVO);
    assert.deepEqual(await obterFicheiro({ modo: "manter", dia: DIA, noAr, gerar: gerarMal }), { versao: 1, geradoEm: DIA, fotos: {} });
  }
});

// A linha de comandos, com um fetch falso: nunca sai para a rede.

const comFetch = (codigo) => `data:text/javascript,${encodeURIComponent(`globalThis.fetch = ${codigo};`)}`;
const FETCH_404 = comFetch("async () => new Response('', { status: 404 })");

function correrCom(fetchFalso, ...argumentos) {
  return spawnSync(process.execPath, [`--import=${fetchFalso}`, GERADOR, ...argumentos], { encoding: "utf8" });
}

const correr = (...argumentos) => correrCom(FETCH_404, ...argumentos);

function pastaComLinhas() {
  const pasta = fs.mkdtempSync(path.join(os.tmpdir(), "fotos-"));
  pastas.push(pasta);
  const linhas = path.join(pasta, "linhas.json");
  fs.writeFileSync(linhas, JSON.stringify(LINHAS));
  return { pasta, linhas };
}

test("cliComTudoEmBaixoEscreveVazioESaiBem", () => {
  const { pasta, linhas } = pastaComLinhas();
  const saida = path.join(pasta, "fotos.json");
  const r = correr("sempre", linhas, saida, DIA);
  assert.equal(r.status, 0, r.stderr);
  assert.deepEqual(JSON.parse(fs.readFileSync(saida, "utf8")), { versao: 1, geradoEm: DIA, fotos: {} });
});

test("cliSemLerONoArNemGerarNaoEscreveVazio", () => {
  for (const fetchFalso of ["async () => new Response('', { status: 503 })", "async () => { throw new TypeError('fetch failed'); }"]) {
    const { pasta, linhas } = pastaComLinhas();
    const saida = path.join(pasta, "fotos.json");
    const r = correrCom(comFetch(fetchFalso), "manter", linhas, saida, DIA);
    assert.equal(r.status, 1, r.stderr);
    assert.match(r.stderr, /::error::/);
    assert.equal(fs.existsSync(saida), false);
  }
});

test("cliSemConseguirEscreverSaiComErro", () => {
  const { pasta, linhas } = pastaComLinhas();
  assert.notEqual(correr("sempre", linhas, path.join(pasta, "nao", "existe.json"), DIA).status, 0);
});

test("cliComArgumentosErradosSaiComErro", () => {
  const { pasta, linhas } = pastaComLinhas();
  const saida = path.join(pasta, "fotos.json");
  assert.notEqual(correr("xpto", linhas, saida, DIA).status, 0);
  assert.notEqual(correr("sempre", linhas).status, 0);
  assert.notEqual(correr("sempre", linhas, saida, "ontem").status, 0);
  assert.equal(fs.existsSync(saida), false);
});
