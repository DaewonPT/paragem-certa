import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { after, before, test } from "node:test";
import { codificarPolilinha, gerar, simplificar } from "./gerar-linhas.mjs";

const FIXTURAS = path.join(import.meta.dirname, "teste-gtfs");
const GERADOR = path.join(import.meta.dirname, "gerar-linhas.mjs");
const DIA = "2026-11-03";
const pastas = [];

/** Uma pasta temporária com uma subpasta por feed, com os nomes verdadeiros do Transitous: { feed: fixtura }. */
function montar(feeds) {
  const pasta = fs.mkdtempSync(path.join(os.tmpdir(), "linhas-"));
  pastas.push(pasta);
  for (const [feed, fixtura] of Object.entries(feeds)) fs.cpSync(path.join(FIXTURAS, fixtura), path.join(pasta, feed), { recursive: true });
  return pasta;
}

const TODOS = {
  Carris: "carris",
  "Metro-Lisboa": "metro",
  "Comboios-de-Portugal(CP)": "cp",
  "Metro-Transportes-do-Sul": "mts",
  "Cascais-Próxima": "semsentido",
};
let catalogo;
before(async () => { catalogo = await gerar(montar(TODOS), DIA); });
after(() => { for (const p of pastas) fs.rmSync(p, { recursive: true, force: true }); });

const linha = (operador, nome) => catalogo.linhas.find((l) => l.operador === operador && l.nome === nome);
const sentido = (l, n) => l.sentidos.find((s) => s.sentido === n);

/** O descodificador do Google (o mesmo do core/rotas/Rotas.kt), para confirmar o que o gerador escreve. */
function descodificar(texto) {
  const pontos = [];
  let i = 0, lat = 0, lon = 0;
  const proximo = () => {
    let resultado = 0, deslocamento = 0, b;
    do {
      b = texto.charCodeAt(i++) - 63;
      resultado |= (b & 0x1f) << deslocamento;
      deslocamento += 5;
    } while (b >= 0x20);
    return resultado & 1 ? ~(resultado >> 1) : resultado >> 1;
  };
  while (i < texto.length) {
    lat += proximo();
    lon += proximo();
    pontos.push([lat / 1e5, lon / 1e5]);
  }
  return pontos;
}

/** Distância em metros de um ponto à polilinha (aproximação plana, chega para uns quilómetros). */
function distancia(p, linhaDe) {
  const m = (q) => [q[1] * 111320 * Math.cos((p[0] * Math.PI) / 180), q[0] * 110540];
  const [px, py] = m(p);
  let menor = Infinity;
  for (let k = 0; k + 1 < linhaDe.length; k++) {
    const [ax, ay] = m(linhaDe[k]);
    const [bx, by] = m(linhaDe[k + 1]);
    const dx = bx - ax, dy = by - ay;
    const t = dx || dy ? Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy))) : 0;
    menor = Math.min(menor, Math.hypot(px - ax - t * dx, py - ay - t * dy));
  }
  return menor;
}

test("linhaComVariasRotasJunta", () => {
  const l728 = catalogo.linhas.filter((l) => l.operador === "pt-Carris" && l.nome === "728");
  assert.equal(l728.length, 1);
  assert.deepEqual(l728[0].rotas, ["pt-Carris_204_0", "pt-Carris_204_1"]);
  // O nome longo é o da rota com mais viagens.
  assert.equal(l728[0].nomeLongo, "Restelo - Portela");
  assert.deepEqual(l728[0].sentidos.map((s) => s.sentido), [0, 1]);
  assert.deepEqual(sentido(l728[0], 1).paragens, ["pt-Carris_3805", "pt-Carris_3804", "pt-Carris_3803"]);
});

test("sentidoUsaOPadraoMaisFrequente", () => {
  // 3 viagens 3803→3805 (uma com as linhas do stop_times fora de ordem) e 1 que segue até à 3806.
  assert.deepEqual(sentido(linha("pt-Carris", "728"), 0).paragens, ["pt-Carris_3803", "pt-Carris_3804", "pt-Carris_3805"]);
});

test("servicoAcabadoNaoConta", () => {
  // As 4 viagens 3803→3806 do serviço "Verao" (acabou em junho) seriam o padrão mais frequente.
  const s = sentido(linha("pt-Carris", "728"), 0);
  assert.notDeepEqual(s.paragens, ["pt-Carris_3803", "pt-Carris_3806"]);
  assert.deepEqual(s.destinos, ['Largo "do" Aeroporto', "Portela"]);
});

test("destinoDoHeadsignOuDaUltimaParagem", () => {
  // A Carris não tem trip_headsign: o destino é o nome da última paragem.
  assert.equal(sentido(linha("pt-Carris", "728"), 0).destino, "Portela");
  assert.equal(sentido(linha("pt-Carris", "728"), 1).destino, "Martim Moniz");
  // O headsign ganha ao nome da paragem ("Parede Terminal"); o mais frequente é o destino, os outros ficam nos destinos.
  const s0 = sentido(linha("pt-Cascais-Próxima", "1"), 0);
  assert.equal(s0.destino, "Parede");
  const am = sentido(linha("pt-Metro-Lisboa", "Am"), 0);
  assert.equal(am.destino, "Odivelas");
  assert.deepEqual(am.destinos, ["Campo Grande", "Odivelas"]);
});

test("semDirectionIdSeparaPeloDestino", () => {
  const l = linha("pt-Cascais-Próxima", "1");
  assert.deepEqual(l.sentidos.map((s) => [s.sentido, s.destino, s.destinos]), [
    [0, "Parede", ["Estoril", "Parede"]],
    [1, "Cascais", ["Cascais"]],
  ]);
  assert.deepEqual(sentido(l, 0).paragens, ["pt-Cascais-Próxima_20127", "pt-Cascais-Próxima_20128", "pt-Cascais-Próxima_20129"]);
  assert.deepEqual(sentido(l, 1).paragens, ["pt-Cascais-Próxima_20129", "pt-Cascais-Próxima_20128", "pt-Cascais-Próxima_20127"]);
  // Sem nome curto, o nome longo; uma circular (uma só última paragem) tem um sentido.
  const circular = linha("pt-Cascais-Próxima", "Circular de Alcabideche");
  assert.deepEqual(circular.sentidos.map((s) => [s.sentido, s.destino]), [[0, "Alcabideche"]]);
});

test("sentidoInventadoVemMarcadoPorDestino", () => {
  // O Transitous manda "directionId": "0" nas viagens sem direction_id (a CP, lido a 03/11/2026): a app não pode comparar
  // com esse número os sentidos que o gerador inventa, e vai pelo destino.
  const marcados = (l) => l.sentidos.map((s) => s.porDestino === true);
  assert.deepEqual(marcados(linha("pt-Cascais-Próxima", "1")), [true, true]);
  assert.deepEqual(marcados(linha("pt-Cascais-Próxima", "Circular de Alcabideche")), [true]);
  assert.deepEqual(marcados(linha("pt-Comboios-de-Portugal(CP)", "Linha de Sintra")), [true, true]);
  // Com direction_id no GTFS o sentido é o verdadeiro e não leva marca.
  for (const l of [linha("pt-Carris", "728"), linha("pt-Carris", "28E"), linha("pt-Metro-Lisboa", "Am")]) {
    assert.ok(l.sentidos.every((s) => !("porDestino" in s)), l.nome);
  }
});

test("eletricoPeloNomeAcabadoEmE", () => {
  assert.equal(linha("pt-Carris", "28E").modo, "ELETRICO");
  assert.equal(linha("pt-Carris", "728").modo, "AUTOCARRO");
  assert.equal(linha("pt-Cascais-Próxima", "1").modo, "AUTOCARRO");
});

test("metroComCaisEModoMetro", () => {
  const am = linha("pt-Metro-Lisboa", "Am");
  const vd = linha("pt-Metro-Lisboa", "Vd");
  assert.equal(am.modo, "METRO");
  assert.equal(vd.modo, "METRO");
  assert.equal(am.cor, "#FFCC00");
  assert.equal(am.corTexto, null);
  assert.deepEqual(am.rotas, ["pt-Metro-Lisboa_B"]);
  assert.deepEqual(sentido(am, 0).paragens, ["pt-Metro-Lisboa_RA1", "pt-Metro-Lisboa_CG1", "pt-Metro-Lisboa_OD1"]);
  assert.deepEqual(sentido(vd, 0).paragens, ["pt-Metro-Lisboa_CS1", "pt-Metro-Lisboa_CG3", "pt-Metro-Lisboa_TE1"]);
  assert.deepEqual(catalogo.paragens["pt-Metro-Lisboa_CG3"], ["Campo Grande", 38.75988, -9.15794]);
  assert.equal(catalogo.paragens["pt-Metro-Lisboa_CG"], undefined);
});

test("cpSoAsDaAreaMetropolitana", () => {
  const sintra = linha("pt-Comboios-de-Portugal(CP)", "Linha de Sintra");
  assert.ok(sintra);
  assert.equal(sintra.modo, "COMBOIO");
  assert.equal(sintra.nomeLongo, "");
  assert.deepEqual(sintra.sentidos.map((s) => [s.sentido, s.destino]), [[0, "Sintra"], [1, "Lisboa Rossio"]]);
  assert.equal(linha("pt-Comboios-de-Portugal(CP)", "IC"), undefined);
  assert.equal(catalogo.paragens["pt-Comboios-de-Portugal(CP)_94_80004"], undefined);
});

test("cpCategoriasJuntamNaLinha", () => {
  const cp = "pt-Comboios-de-Portugal(CP)";
  // Da CP só ficam as linhas "Linha …"; as categorias (U, R, IC…) não são linhas.
  assert.deepEqual(catalogo.linhas.filter((l) => l.operador === cp).map((l) => l.nome), ["Linha de Cascais", "Linha de Sintra"]);
  const cascais = linha(cp, "Linha de Cascais");
  // Os U (Cais do Sodré – São Pedro do Estoril) só param na Linha de Cascais: juntam-se a ela.
  assert.deepEqual(cascais.rotas, [
    `${cp}_26-94_69005-94_69211`,
    `${cp}_26-94_69211-94_69005`,
    `${cp}_27-94_69005-94_69179`,
    `${cp}_27-94_69179-94_69005`,
  ]);
  // Os destinos dos U entram no sentido que percorrem pela mesma ordem; as paragens e o destino continuam os da linha,
  // mesmo com mais viagens U do que da própria linha.
  assert.deepEqual(cascais.sentidos.map((s) => [s.sentido, s.destino, s.destinos, s.paragens]), [
    [0, "Cascais", ["Cascais", "São Pedro do Estoril"], [`${cp}_94_69005`, `${cp}_94_69112`, `${cp}_94_69179`, `${cp}_94_69211`]],
    [1, "Cais do Sodré", ["Cais do Sodré", "Oeiras"], [`${cp}_94_69211`, `${cp}_94_69179`, `${cp}_94_69112`, `${cp}_94_69005`]],
  ]);
  assert.equal(cascais.nomeLongo, "");
  assert.equal(cascais.cor, "#0099FF");
  // O R de Queluz-Belas a Oeiras toca em duas linhas: sai.
  const todasAsRotas = catalogo.linhas.flatMap((l) => l.rotas);
  assert.equal(todasAsRotas.includes(`${cp}_28-94_31112-94_69112`), false);
  assert.deepEqual(linha(cp, "Linha de Sintra").rotas, [`${cp}_25-94_31039-94_61101`, `${cp}_25-94_61101-94_31039`]);
});

test("feedCaducadoFicaDeFora", async () => {
  assert.equal(catalogo.operadores.find((o) => o.id === "pt-Metro-Transportes-do-Sul"), undefined);
  assert.equal(catalogo.linhas.some((l) => l.operador === "pt-Metro-Transportes-do-Sul"), false);
  // Um catálogo vazio nunca se publica.
  await assert.rejects(gerar(montar({ "Metro-Transportes-do-Sul": "mts" }), DIA), /nenhuma linha/);
});

test("camposComAspasEVirgulas", () => {
  assert.deepEqual(catalogo.paragens["pt-Carris_3804"], ["Rua da Palma, 12", 38.718, -9.136]);
  assert.deepEqual(catalogo.paragens["pt-Cascais-Próxima_20128"], ["Estoril, Escola", 38.706, -9.397]);
  assert.ok(sentido(linha("pt-Carris", "728"), 0).destinos.includes('Largo "do" Aeroporto'));
});

test("formaSimplificadaEDescodificavel", () => {
  // Um arco de ~2 km com 400 pontos e ruído de ±3 m: a forma fica com muito menos pontos e a menos de 20 m.
  const arco = Array.from({ length: 400 }, (_, i) => {
    const a = (i / 399) * Math.PI;
    const ruido = ((i * 7919) % 11) / 10 - 0.5;
    return [38.72 + 0.009 * Math.sin(a) + ruido * 5e-5, -9.14 + 0.012 * Math.cos(a)];
  });
  const forma = descodificar(codificarPolilinha(simplificar(arco, 15)));
  assert.ok(forma.length < 60, `${forma.length} pontos`);
  for (const p of arco) assert.ok(distancia(p, forma) < 20, `ponto a ${distancia(p, forma)} m`);
  // Na 728, a forma vem do shapes.txt (fora de ordem no ficheiro); os pontos em linha reta saem.
  const shape = [[38.7166, -9.1357], [38.71695, -9.13578], [38.7173, -9.13585], [38.71765, -9.13592], [38.718, -9.136], [38.744, -9.12], [38.77, -9.105]];
  const f728 = descodificar(sentido(linha("pt-Carris", "728"), 0).forma);
  assert.ok(f728.length < shape.length);
  for (const p of shape) assert.ok(distancia(p, f728) < 20);
  // Sem shape, a forma liga as paragens.
  assert.deepEqual(descodificar(sentido(linha("pt-Carris", "728"), 1).forma), [[38.77, -9.105], [38.718, -9.136], [38.7166, -9.1357]]);
});

test("idsComAcentoEParenteses", () => {
  const l = linha("pt-Cascais-Próxima", "1");
  assert.deepEqual(l.rotas, ["pt-Cascais-Próxima_M01_A"]);
  assert.equal(l.cor, "#AD0101");
  assert.equal(l.corTexto, "#FFFFFF");
  assert.ok(catalogo.paragens["pt-Cascais-Próxima_20127"]);
  assert.ok(catalogo.paragens["pt-Comboios-de-Portugal(CP)_94_31039"]);
  // Os nomes dos operadores vêm do feeds-linhas.json.
  assert.deepEqual(catalogo.operadores, [
    { id: "pt-Carris", nome: "Carris" },
    { id: "pt-Cascais-Próxima", nome: "MobiCascais" },
    { id: "pt-Comboios-de-Portugal(CP)", nome: "CP" },
    { id: "pt-Metro-Lisboa", nome: "Metro de Lisboa" },
  ]);
});

test("duasGeracoesIguaisDaoOMesmoFicheiro", () => {
  const pasta = montar(TODOS);
  const saidas = ["a.json", "b.json"].map((nome) => {
    const saida = path.join(pasta, nome);
    const r = spawnSync(process.execPath, [GERADOR, pasta, saida, DIA], { encoding: "utf8" });
    assert.equal(r.status, 0, r.stderr);
    return fs.readFileSync(saida);
  });
  assert.ok(saidas[0].equals(saidas[1]));
  const lido = JSON.parse(saidas[0].toString("utf8"));
  assert.equal(lido.versao, 1);
  assert.equal(lido.geradoEm, DIA);
  assert.deepEqual(lido, catalogo);
});
