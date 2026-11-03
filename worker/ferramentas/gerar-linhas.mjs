// worker/ferramentas/gerar-linhas.mjs
// Gera o /dados/linhas.json (as linhas fora da Carris Metropolitana) a partir dos GTFS processados pelo Transitous,
// para os ids baterem com os do /stoptimes. Corre no GitHub Actions (dados.yml e servidor.yml), só com a biblioteca do Node.
// Uso: node ferramentas/gerar-linhas.mjs <pasta com uma subpasta por feed> <saída.json> [AAAA-MM-DD]
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";

const FEEDS = JSON.parse(fs.readFileSync(new URL("./feeds-linhas.json", import.meta.url), "utf8"));
const CARRIS = "Carris";
const CP = "Comboios-de-Portugal(CP)";
const DIAS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"];
const ordem = new Intl.Collator("pt", { numeric: true });
// Ordem total (o collator pode dar empates), para duas gerações iguais darem o mesmo ficheiro.
const comparar = (a, b) => ordem.compare(a, b) || (a < b ? -1 : a > b ? 1 : 0);
const arredondar = (x) => Math.round(x * 1e5) / 1e5;

/** Parte uma linha de CSV com campos entre aspas ("" dentro das aspas é uma aspa). */
function partir(linha) {
  const campos = [];
  let campo = "";
  let aspas = false;
  for (let i = 0; i < linha.length; i++) {
    const c = linha[i];
    if (aspas) {
      if (c !== '"') campo += c;
      else if (linha[i + 1] === '"') { campo += '"'; i++; }
      else aspas = false;
    } else if (c === '"') aspas = true;
    else if (c === ",") { campos.push(campo); campo = ""; }
    else campo += c;
  }
  campos.push(campo);
  return campos;
}

/** Os registos de um ficheiro do GTFS, linha a linha: o stop_times.txt da Carris passa dos 130 MB. */
async function* ler(pasta, nome, obrigatorio = true) {
  const ficheiro = path.join(pasta, nome);
  if (!fs.existsSync(ficheiro)) {
    if (obrigatorio) throw new Error(`falta o ${nome} em ${pasta}`);
    return;
  }
  let cabecalho;
  // shortcut: uma quebra de linha dentro de aspas parte o registo; nenhum dos feeds a tem, ler aos bocados se aparecer.
  for await (const linha of readline.createInterface({ input: fs.createReadStream(ficheiro, "utf8"), crlfDelay: Infinity })) {
    if (!linha.trim()) continue;
    const campos = (linha.includes('"') ? partir(linha) : linha.split(",")).map((c) => c.trim());
    if (!cabecalho) {
      cabecalho = campos;
      cabecalho[0] = cabecalho[0].replace(/^﻿/, "");
      continue;
    }
    const registo = {};
    cabecalho.forEach((c, i) => { registo[c] = campos[i] ?? ""; });
    yield registo;
  }
}

function contar(chaves) {
  const contagem = new Map();
  for (const k of chaves) contagem.set(k, (contagem.get(k) ?? 0) + 1);
  return contagem;
}

/** A chave com mais ocorrências; num empate, a primeira pela ordem de `comparar`. */
function maisFrequente(contagem) {
  let melhor;
  let n = -1;
  for (const [k, c] of contagem) if (c > n || (c === n && comparar(k, melhor) < 0)) { melhor = k; n = c; }
  return melhor;
}

function modoDe(tipo, nome, feed) {
  // O GTFS da Carris diz autocarro para tudo; os elétricos (e os ascensores) têm o nome acabado em "E".
  if (feed === CARRIS && /^\d+E$/.test(nome)) return "ELETRICO";
  const t = tipo === "" ? -1 : Number(tipo);
  if (t === 0) return "ELETRICO";
  if (t === 1) return "METRO";
  if (t === 2 || (t >= 100 && t <= 117)) return "COMBOIO";
  if (t === 3 || (t >= 700 && t <= 717)) return "AUTOCARRO";
  if (t === 4 || t === 1200) return "BARCO";
  return "OUTRO";
}

const cor = (c) => (/^[0-9A-Fa-f]{6}$/.test(c ?? "") ? "#" + c.toUpperCase() : null);
const naAreaMetropolitana = ([, lat, lon]) => lat >= 38.4 && lat <= 39.1 && lon >= -9.6 && lon <= -8.6;

/** Quanto as paragens de uma viagem seguem pela ordem do padrão: +1 por cada par na mesma ordem, −1 ao contrário. */
function concordancia(paragens, padrao) {
  const posicao = new Map();
  padrao.forEach((id, i) => { if (!posicao.has(id)) posicao.set(id, i); });
  let nota = 0;
  let antes;
  for (const id of paragens) {
    const i = posicao.get(id);
    if (i === undefined) continue;
    if (antes !== undefined) nota += Math.sign(i - antes);
    antes = i;
  }
  return nota;
}

/**
 * Sentidos de uma linha sem direction_id: as duas últimas paragens mais frequentes passam a sentido 0 e 1; uma viagem
 * que acaba noutra paragem vai para o sentido cujo padrão percorre pela mesma ordem (e sai, se não houver nenhum).
 */
function sentidosPelaUltimaParagem(viagens) {
  const finais = [...contar(viagens.map((v) => v.paragens.at(-1)))]
    .sort((a, b) => b[1] - a[1] || comparar(a[0], b[0]))
    .slice(0, 2)
    .map(([id]) => id);
  const padroes = finais.map((f) => maisFrequente(contar(viagens.filter((v) => v.paragens.at(-1) === f).map((v) => v.chave))).split("\n"));
  const grupos = new Map(finais.map((_, i) => [i, []]));
  for (const v of viagens) {
    let i = finais.indexOf(v.paragens.at(-1));
    if (i < 0) {
      const notas = padroes.map((p) => concordancia(v.paragens, p));
      i = notas.indexOf(Math.max(...notas));
      if (notas[i] <= 0) continue;
    }
    grupos.get(i).push(v);
  }
  return grupos;
}

/**
 * Na CP só são linhas as "Linha …"; as outras são categorias de serviço (U, R, IR…). Cada rota de uma categoria com
 * todas as paragens numa só dessas linhas junta-se a ela (os U de Cais do Sodré a São Pedro do Estoril vão para a Linha
 * de Cascais); as outras saem. Tira as categorias de `porLinha` e devolve, por linha, as viagens que se lhe juntam.
 */
function juntarCategoriasDaCp(porLinha) {
  const eLinha = (nome) => nome.startsWith("Linha ");
  const paragensDe = [...porLinha].filter(([nome]) => eLinha(nome)).map(([nome, vs]) => [nome, new Set(vs.flatMap((v) => v.paragens))]);
  const juntas = new Map();
  for (const [nome, vs] of [...porLinha]) {
    if (eLinha(nome)) continue;
    porLinha.delete(nome);
    const porRota = new Map();
    for (const v of vs) {
      if (!porRota.has(v.rota)) porRota.set(v.rota, []);
      porRota.get(v.rota).push(v);
    }
    for (const daRota of porRota.values()) {
      const usadas = new Set(daRota.flatMap((v) => v.paragens));
      const onde = paragensDe.filter(([, daLinha]) => [...usadas].every((id) => daLinha.has(id)));
      if (onde.length !== 1) continue;
      const [linha] = onde[0];
      if (!juntas.has(linha)) juntas.set(linha, []);
      juntas.get(linha).push(...daRota);
    }
  }
  return juntas;
}

/** Douglas–Peucker com a tolerância em metros (projeção plana à volta do primeiro ponto; chega para uma linha urbana). */
export function simplificar(pontos, tolerancia) {
  if (pontos.length < 3) return pontos;
  const escala = Math.cos((pontos[0][0] * Math.PI) / 180);
  const xy = pontos.map(([lat, lon]) => [lon * 111320 * escala, lat * 110540]);
  const fica = new Uint8Array(pontos.length);
  fica[0] = fica[pontos.length - 1] = 1;
  const pilha = [[0, pontos.length - 1]];
  while (pilha.length) {
    const [a, b] = pilha.pop();
    let longe = 0;
    let k = -1;
    for (let i = a + 1; i < b; i++) {
      const d = aoSegmento(xy[i], xy[a], xy[b]);
      if (d > longe) { longe = d; k = i; }
    }
    if (longe > tolerancia) {
      fica[k] = 1;
      pilha.push([a, k], [k, b]);
    }
  }
  return pontos.filter((_, i) => fica[i]);
}

// Ao segmento e não à reta: numa circular o primeiro e o último ponto são o mesmo.
function aoSegmento([px, py], [ax, ay], [bx, by]) {
  const dx = bx - ax;
  const dy = by - ay;
  const l2 = dx * dx + dy * dy;
  const t = l2 ? Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2)) : 0;
  return Math.hypot(px - ax - t * dx, py - ay - t * dy);
}

/** A polilinha do Google com precisão 5 (a que o core/rotas/Rotas.kt descodifica). */
export function codificarPolilinha(pontos) {
  let texto = "";
  let lat0 = 0;
  let lon0 = 0;
  for (const [lat, lon] of pontos) {
    const lat1 = Math.round(lat * 1e5);
    const lon1 = Math.round(lon * 1e5);
    texto += numero(lat1 - lat0) + numero(lon1 - lon0);
    lat0 = lat1;
    lon0 = lon1;
  }
  return texto;
}

function numero(n) {
  let v = n < 0 ? ~(n << 1) : n << 1;
  let texto = "";
  while (v >= 0x20) {
    texto += String.fromCharCode((0x20 | (v & 0x1f)) + 63);
    v >>= 5;
  }
  return texto + String.fromCharCode(v + 63);
}

/** As linhas de um feed; junta a `todasAsParagens` as paragens que elas usam. Um feed sem serviço por vir dá []. */
async function lerFeed(pasta, feed, dia, todasAsParagens) {
  const prefixo = `pt-${feed}_`;
  const hoje = dia.replaceAll("-", "");
  // Só contam as viagens com serviço de hoje em diante: um feed caducado (hoje, o MTS) fica sem linhas.
  const ativos = new Set();
  for await (const c of ler(pasta, "calendar.txt", false)) if (c.end_date >= hoje && DIAS.some((d) => c[d] === "1")) ativos.add(c.service_id);
  for await (const c of ler(pasta, "calendar_dates.txt", false)) if (c.exception_type === "1" && c.date >= hoje) ativos.add(c.service_id);
  if (!ativos.size) return [];

  const rotas = new Map();
  for await (const r of ler(pasta, "routes.txt")) rotas.set(r.route_id, r);
  const viagens = new Map();
  for await (const t of ler(pasta, "trips.txt")) {
    if (ativos.has(t.service_id) && rotas.has(t.route_id)) {
      viagens.set(t.trip_id, { rota: t.route_id, sentido: t.direction_id ?? "", destino: t.trip_headsign ?? "", forma: t.shape_id ?? "", seq: [] });
    }
  }
  const paragens = new Map();
  for await (const p of ler(pasta, "stops.txt")) paragens.set(p.stop_id, [p.stop_name, arredondar(Number(p.stop_lat)), arredondar(Number(p.stop_lon))]);
  // Os ids das paragens repetem-se milhões de vezes no stop_times.txt: guarda-se uma só cópia de cada.
  const unico = new Map();
  for await (const st of ler(pasta, "stop_times.txt")) {
    const v = viagens.get(st.trip_id);
    if (v) v.seq.push(Number(st.stop_sequence), unico.get(st.stop_id) ?? (unico.set(st.stop_id, st.stop_id), st.stop_id));
  }

  const porLinha = new Map();
  for (const v of viagens.values()) {
    const pares = [];
    for (let i = 0; i < v.seq.length; i += 2) pares.push([v.seq[i], v.seq[i + 1]]);
    pares.sort((a, b) => a[0] - b[0]);
    v.seq = null;
    v.paragens = pares.map((p) => p[1]);
    const pontos = v.paragens.map((id) => paragens.get(id));
    if (pontos.length < 2 || pontos.includes(undefined)) continue;
    if (feed === CP && !pontos.every(naAreaMetropolitana)) continue;
    v.chave = v.paragens.join("\n");
    if (!v.destino) v.destino = pontos.at(-1)[0];
    const r = rotas.get(v.rota);
    const nome = r.route_short_name || r.route_long_name || r.route_id;
    if (!porLinha.has(nome)) porLinha.set(nome, []);
    porLinha.get(nome).push(v);
  }
  const juntas = feed === CP ? juntarCategoriasDaCp(porLinha) : new Map();

  const linhas = [];
  const formas = new Map();
  for (const [nome, vs] of porLinha) {
    const extras = juntas.get(nome) ?? [];
    const contaRotas = contar(vs.map((v) => v.rota));
    const principal = rotas.get(maisFrequente(contaRotas));
    const comDirectionId = vs.every((v) => v.sentido !== "");
    const grupos = comDirectionId
      ? [...contar(vs.map((v) => Number(v.sentido))).keys()].map((s) => [s, vs.filter((v) => Number(v.sentido) === s)])
      : [...sentidosPelaUltimaParagem(vs)];
    const sentidos = grupos
      .filter(([, doSentido]) => doSentido.length)
      .sort((a, b) => a[0] - b[0])
      .map(([sentido, doSentido]) => {
        const padrao = maisFrequente(contar(doSentido.map((v) => v.chave)));
        const formaId = doSentido.filter((v) => v.chave === padrao && v.forma).map((v) => v.forma).sort(comparar)[0];
        if (formaId) formas.set(formaId, []);
        return {
          sentido,
          destino: maisFrequente(contar(doSentido.map((v) => v.destino))),
          destinos: [...new Set(doSentido.map((v) => v.destino))].sort(comparar),
          paragens: padrao.split("\n"),
          formaId,
          // Sentido inventado: o Transitous manda "directionId": "0" nas viagens sem direction_id (a CP), por isso a app não o
          // compara com o número e vai pelo destino.
          ...(comDirectionId ? {} : { porDestino: true }),
        };
      });
    // As viagens juntadas só acrescentam destinos, no sentido cujo padrão percorrem pela mesma ordem (o padrão, a
    // forma e o destino continuam os da própria linha).
    for (const v of extras) {
      const notas = sentidos.map((s) => concordancia(v.paragens, s.paragens));
      const i = notas.indexOf(Math.max(...notas));
      if (notas[i] > 0 && !sentidos[i].destinos.includes(v.destino)) sentidos[i].destinos = [...sentidos[i].destinos, v.destino].sort(comparar);
    }
    linhas.push({
      operador: `pt-${feed}`,
      nome,
      nomeLongo: principal.route_long_name ?? "",
      modo: modoDe(principal.route_type ?? "", nome, feed),
      cor: cor(principal.route_color),
      corTexto: cor(principal.route_text_color),
      rotas: [...new Set([...vs, ...extras].map((v) => v.rota))].sort(comparar).map((id) => prefixo + id),
      sentidos,
    });
  }

  // Só as formas que os sentidos usam (o shapes.txt da Carris tem 6 MB).
  for await (const s of ler(pasta, "shapes.txt", false)) {
    formas.get(s.shape_id)?.push([Number(s.shape_pt_sequence), Number(s.shape_pt_lat), Number(s.shape_pt_lon)]);
  }
  for (const l of linhas) {
    for (const s of l.sentidos) {
      const forma = (formas.get(s.formaId) ?? []).sort((a, b) => a[0] - b[0]).map(([, lat, lon]) => [lat, lon]);
      // Sem forma no GTFS, a linha liga as paragens.
      const pontos = forma.length >= 2 ? forma : s.paragens.map((id) => paragens.get(id).slice(1));
      s.forma = codificarPolilinha(simplificar(pontos, 15));
      delete s.formaId;
      for (const id of s.paragens) todasAsParagens[prefixo + id] = paragens.get(id);
      s.paragens = s.paragens.map((id) => prefixo + id);
    }
  }
  return linhas;
}

/** O catálogo de todos os feeds da pasta (uma subpasta por feed, com o nome do Transitous), sempre pela mesma ordem. */
export async function gerar(pasta, dia) {
  const feeds = fs.readdirSync(pasta, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name).sort(comparar);
  const operadores = [];
  const linhas = [];
  const paragens = {};
  for (const feed of feeds) {
    const doFeed = await lerFeed(path.join(pasta, feed), feed, dia, paragens);
    if (!doFeed.length) continue;
    operadores.push({ id: `pt-${feed}`, nome: FEEDS.find((f) => f.feed === feed)?.nome ?? feed });
    linhas.push(...doFeed);
  }
  if (!linhas.length) throw new Error("nenhuma linha: um catálogo vazio não se publica");
  linhas.sort((a, b) => comparar(a.operador, b.operador) || comparar(a.nome, b.nome));
  return {
    versao: 1,
    geradoEm: dia,
    operadores,
    paragens: Object.fromEntries(Object.entries(paragens).sort(([a], [b]) => comparar(a, b))),
    linhas,
  };
}

if (import.meta.main) {
  const hoje = new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Lisbon" }).format(new Date());
  const [pasta, saida, dia = hoje] = process.argv.slice(2);
  if (!pasta || !saida || !/^\d{4}-\d{2}-\d{2}$/.test(dia)) {
    console.error("uso: node ferramentas/gerar-linhas.mjs <pasta com uma subpasta por feed> <saída.json> [AAAA-MM-DD]");
    process.exit(2);
  }
  const catalogo = await gerar(pasta, dia);
  fs.writeFileSync(saida, JSON.stringify(catalogo));
  console.log(`${catalogo.linhas.length} linhas de ${catalogo.operadores.length} operadores em ${saida}`);
}
