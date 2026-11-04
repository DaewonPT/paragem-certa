// worker/ferramentas/gerar-fotos-rua.mjs
// Gera o /dados/fotos-de-rua.json (paragem → foto de rua do Panoramax). Corre no GitHub Actions (dados.yml e
// servidor.yml), só com a biblioteca do Node. Nenhum dos ficheiros gerados está no repositório e um deploy apaga do ar
// o que não leva, por isso este corre em todos os deploys e é ele que decide se gera ou copia o que está no ar.
// Uso: node ferramentas/gerar-fotos-rua.mjs <sempre|mensal|manter> <linhas.json> <saída.json> [AAAA-MM-DD]
import fs from "node:fs";

const PANORAMAX = "https://api.panoramax.xyz/api/search";
const PARAGENS_CM = "https://api.carrismetropolitana.pt/v2/stops";
const NO_AR = "https://paragemcerta.pixellusitano.com/dados/fotos-de-rua.json";
const EXCLUIDAS = new URL("./fotos-excluidas.json", import.meta.url);
const USER_AGENT = "ParagemCerta/0.4.0 (contact@pixellusitano.com; https://pixellusitano.com)";
const MODOS = ["sempre", "mensal", "manter"];

/** A caixa da Área Metropolitana do gerar-linhas.mjs, pela ordem do `bbox`: lonMin, latMin, lonMax, latMax. */
export const ZONA = [-9.6, 38.4, -8.6, 39.1];
const LIMITE = 1000;
// Depois de 8 divisões, uma caixa tem uns 300 m de lado: mais de 1000 fotos aí é a mesma rua e chegam as que vieram.
const MAX_DIVISOES = 8;
// Em 02/11 havia 3 980. Muito menos do que isso é a API a mudar ou a falhar, e não pode apagar as fotos que estão no ar.
const MINIMO_FOTOS = 500;
const RAIO_METROS = 30;
const MAX_DESVIO_GRAUS = 45;
// A 38–39° de latitude, 0,001° são mais de 85 m: uma foto a menos de 30 m está sempre na mesma célula ou numa vizinha.
const CELULA_GRAUS = 0.001;
const LICENCAS = new Map([
  ["CC-BY-SA-4.0", "CC BY-SA 4.0"],
  ["etalab-2.0", "Licence Ouverte 2.0"],
  ["CC0-1.0", "CC0"],
  ["CC0", "CC0"],
]);

const radianos = (graus) => (graus * Math.PI) / 180;

/** A foto com os campos de que o gerador precisa; null se lhe faltar algum ou não se puder mostrar. */
export function normalizarFoto(feature) {
  const p = feature?.properties;
  const id = feature?.id;
  const lon = feature?.geometry?.coordinates?.[0];
  const lat = feature?.geometry?.coordinates?.[1];
  const azimute = p?.["view:azimuth"];
  const miniatura = feature?.assets?.thumb?.href;
  const autor = feature?.providers?.[0]?.name;
  const licenca = LICENCAS.get(p?.license);
  const data = String(p?.datetime).slice(0, 10);
  const valida = p?.["geovisio:status"] === "ready"
    && typeof id === "string" && id
    && [lat, lon, azimute].every(Number.isFinite)
    && typeof miniatura === "string" && miniatura.startsWith("https://")
    && typeof autor === "string" && autor.trim()
    && licenca
    && /^\d{4}-\d{2}-\d{2}$/.test(data);
  return valida ? { id, lat, lon, azimute, miniatura, autor: autor.trim(), licenca, data } : null;
}

/** As fotos da caixa, sem repetidos. O /api/search não pagina: uma caixa que venha cheia divide-se em quatro. */
export async function procurarFotos(caixa, buscar) {
  const fotos = new Map();
  async function procurar([lonMin, latMin, lonMax, latMax], divisoes) {
    const { features } = await buscar(`${PANORAMAX}?bbox=${lonMin},${latMin},${lonMax},${latMax}&limit=${LIMITE}`);
    if (!Array.isArray(features)) throw new Error("o Panoramax respondeu sem features");
    if (features.length >= LIMITE && divisoes < MAX_DIVISOES) {
      const lon = (lonMin + lonMax) / 2;
      const lat = (latMin + latMax) / 2;
      await procurar([lonMin, latMin, lon, lat], divisoes + 1);
      await procurar([lon, latMin, lonMax, lat], divisoes + 1);
      await procurar([lonMin, lat, lon, latMax], divisoes + 1);
      await procurar([lon, lat, lonMax, latMax], divisoes + 1);
      return;
    }
    for (const f of features) {
      const foto = normalizarFoto(f);
      if (foto) fotos.set(foto.id, foto);
    }
  }
  await procurar(caixa, 0);
  if (fotos.size < MINIMO_FOTOS) throw new Error(`só ${fotos.size} fotos que se possam mostrar: abaixo de ${MINIMO_FOTOS}, fica o ficheiro que está no ar`);
  return [...fotos.values()];
}

function distanciaMetros(a, b) {
  const dLat = radianos(b.lat - a.lat);
  const dLon = radianos(b.lon - a.lon);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(radianos(a.lat)) * Math.cos(radianos(b.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * 6371000 * Math.asin(Math.sqrt(h));
}

/** O rumo de `a` para `b`, em graus a partir do norte. */
function rumo(a, b) {
  const dLon = radianos(b.lon - a.lon);
  const y = Math.sin(dLon) * Math.cos(radianos(b.lat));
  const x = Math.cos(radianos(a.lat)) * Math.sin(radianos(b.lat)) - Math.sin(radianos(a.lat)) * Math.cos(radianos(b.lat)) * Math.cos(dLon);
  return (Math.atan2(y, x) * 180) / Math.PI;
}

/** A menos de 30 m e a olhar para a paragem (±45°, também à volta do norte). */
function serve(foto, paragem) {
  if (distanciaMetros(foto, paragem) >= RAIO_METROS) return false;
  const desvio = Math.abs(((foto.azimute - rumo(foto, paragem)) % 360 + 540) % 360 - 180);
  return desvio <= MAX_DESVIO_GRAUS;
}

/** Para cada paragem com foto, a mais recente que serve (num empate, a de menor id), já como vai para o ficheiro. */
export function escolherFotos(paragens, fotos, excluidas) {
  const celula = (graus) => Math.floor(graus / CELULA_GRAUS);
  const grelha = new Map();
  for (const f of fotos) {
    if (excluidas.has(f.id)) continue;
    const k = `${celula(f.lat)},${celula(f.lon)}`;
    if (grelha.has(k)) grelha.get(k).push(f);
    else grelha.set(k, [f]);
  }
  const escolhidas = {};
  for (const p of [...paragens].sort((a, b) => (a.chave < b.chave ? -1 : a.chave > b.chave ? 1 : 0))) {
    let melhor = null;
    for (let dLat = -1; dLat <= 1; dLat++) {
      for (let dLon = -1; dLon <= 1; dLon++) {
        for (const f of grelha.get(`${celula(p.lat) + dLat},${celula(p.lon) + dLon}`) ?? []) {
          if (!serve(f, p)) continue;
          if (!melhor || f.data > melhor.data || (f.data === melhor.data && f.id < melhor.id)) melhor = f;
        }
      }
    }
    if (melhor) {
      const { id, miniatura, autor, licenca, data } = melhor;
      escolhidas[p.chave] = { id, miniatura, autor, licenca, data };
    }
  }
  return escolhidas;
}

/** O ficheiro novo. Lança se alguma fonte falhar: um ficheiro com só parte das paragens não substitui o que está no ar. */
export async function gerarFicheiro({ dia, linhas, excluidas, buscar }) {
  const cm = await buscar(PARAGENS_CM);
  if (!Array.isArray(cm) || !cm.length) throw new Error("a Carris Metropolitana não devolveu paragens");
  const fotos = await procurarFotos(ZONA, buscar);
  const paragens = [
    ...Object.entries(linhas.paragens).map(([id, [, lat, lon]]) => ({ chave: `TRANSITOUS:${id}`, lat, lon })),
    ...cm.map((s) => ({ chave: `CM:${s.id}`, lat: s.lat, lon: s.lon })),
  ];
  return { versao: 1, geradoEm: dia, fotos: escolherFotos(paragens, fotos, excluidas) };
}

/** Decide entre gerar e copiar o que está no ar. Nunca lança: uma falha daqui não pode parar uma publicação. */
export async function obterFicheiro({ modo, dia, noAr, gerar }) {
  // Vazio no ar é o que fica de uma geração falhada sem nada no ar: conta como nada, para voltar a tentar.
  const temFotos = noAr?.versao === 1 && typeof noAr.fotos === "object" && Object.keys(noAr.fotos ?? {}).length > 0;
  if (temFotos && (modo === "manter" || (modo === "mensal" && Number(dia.slice(8)) > 7))) return noAr;
  try {
    return await gerar();
  } catch (e) {
    console.warn(`::warning::Fotos de rua não geradas, fica o que estava no ar: ${e.message}`);
    return temFotos ? noAr : { versao: 1, geradoEm: dia, fotos: {} };
  }
}

if (import.meta.main) {
  const hoje = new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Lisbon" }).format(new Date());
  const [modo, entrada, saida, dia = hoje] = process.argv.slice(2);
  if (!MODOS.includes(modo) || !entrada || !saida || !/^\d{4}-\d{2}-\d{2}$/.test(dia)) {
    console.error("uso: node ferramentas/gerar-fotos-rua.mjs <sempre|mensal|manter> <linhas.json> <saída.json> [AAAA-MM-DD]");
    process.exit(2);
  }
  // Um prazo para tudo: um pedido preso ou uma API que ignore a caixa não pode gastar o tempo da publicação.
  const prazo = AbortSignal.timeout(5 * 60_000);
  const buscar = async (url) => {
    await new Promise((resolver) => setTimeout(resolver, 200));
    const resposta = await fetch(url, { headers: { "User-Agent": USER_AGENT }, signal: AbortSignal.any([prazo, AbortSignal.timeout(60_000)]) });
    if (!resposta.ok) throw new Error(`${url} respondeu ${resposta.status}`);
    return resposta.json();
  };
  const noAr = await buscar(NO_AR).catch(() => null);
  const ficheiro = await obterFicheiro({
    modo,
    dia,
    noAr,
    gerar: () => gerarFicheiro({
      dia,
      linhas: JSON.parse(fs.readFileSync(entrada, "utf8")),
      excluidas: new Set(JSON.parse(fs.readFileSync(EXCLUIDAS, "utf8"))),
      buscar,
    }),
  });
  fs.writeFileSync(saida, JSON.stringify(ficheiro));
  console.log(`${Object.keys(ficheiro.fotos).length} paragens com foto em ${saida} (de ${ficheiro.geradoEm})`);
}
