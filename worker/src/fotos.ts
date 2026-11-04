import config from "../public/dados/config.json";
import { portaoDeEscrita } from "./portao";
import { FORMATO_PARAGEM } from "./relatos";
import { erro, json } from "./respostas";

const REGRAS = config.comunidade;
const DIA = 24 * 60 * 60_000;
const MAX_FOTOS_LIDAS = 100;

/** Onde o domínio das fotos aprovadas serve o bucket `pc-fotos`. */
export const URL_FOTOS = "https://fotos.pixellusitano.com";
/** 500 KB: a app envia 150–300 KB; o que passa disto não vem da app, e cada foto tem de ser revista e fica no R2. */
export const MAX_FOTO = 500 * 1024;

/** `data`: AAAA-MM-DD (UTC) em que o autor a aprovou; a app compara-a com a da foto de rua e mostra a mais recente. */
export type FotoPublica = { paragem: string; id: string; url: string; data: string };

/** Marcadores sem tamanho (ou que não cabem entre segmentos): ZERO, TEM, SOI, RST0–RST7 e o preenchimento FF. */
function semTamanho(marcador: number): boolean {
  return marcador === 0x00 || marcador === 0x01 || marcador === 0xff || marcador === 0xd8 || (marcador >= 0xd0 && marcador <= 0xd7);
}

/**
 * Se o JPEG traz metadados que não queremos guardar: `true` com um segmento APP1 (Exif e XMP) ou APP13 (IPTC),
 * `false` se não traz, `null` se não é um JPEG inteiro e sem nada colado (SOI … EOI, e o EOI é o último byte).
 * Entrada de quem não conhecemos: anda sempre para a frente (cada volta come pelo menos 2 bytes) e não aloca nada.
 * Passa também pelos dados e pelos segmentos a seguir ao primeiro SOS (um JPEG progressivo tem vários), e o primeiro
 * EOI tem de ser o último byte: assim uma segunda imagem colada no fim (o mapa de ganho do Ultra HDR, por exemplo)
 * não leva metadados escondidos.
 */
export function temMetadados(jpeg: Uint8Array): boolean | null {
  const n = jpeg.length;
  if (n < 6 || jpeg[0] !== 0xff || jpeg[1] !== 0xd8 || jpeg[2] !== 0xff || jpeg[n - 2] !== 0xff || jpeg[n - 1] !== 0xd9) return null;

  let pos = 2;
  let comImagem = false;
  while (pos < n - 1) {
    if (jpeg[pos] !== 0xff) return null;
    const marcador = jpeg[pos + 1];
    if (marcador === 0xe1 || marcador === 0xed) return true;
    if (marcador === 0xd9) return comImagem && pos === n - 2 ? false : null;
    if (semTamanho(marcador) || pos + 4 > n) return null;

    // O tamanho inclui os próprios 2 bytes; menos que isso, ou a passar do fim, é lixo.
    const tamanho = (jpeg[pos + 2] << 8) | jpeg[pos + 3];
    pos += 2 + tamanho;
    if (tamanho < 2 || pos > n) return null;

    if (marcador === 0xda) {
      comImagem = true;
      // Dados da imagem: andam até ao próximo marcador de verdade (FF00 é um FF dos dados, FFD0–FFD7 são RST, FF FF é preenchimento).
      for (; pos < n - 1; pos++) {
        if (jpeg[pos] !== 0xff || jpeg[pos + 1] === 0xff) continue;
        if (jpeg[pos + 1] !== 0x00 && !(jpeg[pos + 1] >= 0xd0 && jpeg[pos + 1] <= 0xd7)) break;
        pos++;
      }
    }
  }
  return null;
}

/** O corpo todo se não passar de `maximo` bytes; `null` se passar (a leitura pára logo ao passar, sem esperar pelo resto). */
async function lerAte(corpo: ReadableStream<Uint8Array> | null, maximo: number): Promise<Uint8Array | null> {
  if (!corpo) return new Uint8Array();
  const leitor = corpo.getReader();
  const pedacos: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await leitor.read();
    if (done) break;
    total += value.byteLength;
    if (total > maximo) {
      await leitor.cancel();
      return null;
    }
    pedacos.push(value);
  }
  const todo = new Uint8Array(total);
  let desde = 0;
  for (const pedaco of pedacos) {
    todo.set(pedaco, desde);
    desde += pedaco.byteLength;
  }
  return todo;
}

async function sha256(dados: Uint8Array): Promise<string> {
  const resumo = await crypto.subtle.digest("SHA-256", dados);
  return [...new Uint8Array(resumo)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/**
 * POST /api/fotos?paragem=K, com o JPEG em bruto. A foto fica pendente no bucket privado até o autor a rever.
 * Uma por conta em 24 h (as rejeitadas contam), uma por paragem de cada vez e no máximo 100 por dia em todo o servidor:
 * cada limite é uma instrução só, para pedidos em paralelo não o passarem.
 */
export async function enviarFoto(pedido: Request, env: Env): Promise<Response> {
  const quem = await portaoDeEscrita(pedido, env, "fotos");
  if (quem instanceof Response) return quem;

  const paragem = new URL(pedido.url).searchParams.get("paragem");
  if (!paragem || !FORMATO_PARAGEM.test(paragem)) return erro("pedido_invalido", 400);

  // O tamanho declarado recusa sem ler nada; como pode faltar ou mentir, a leitura também pára no limite.
  if (Number(pedido.headers.get("content-length")) > MAX_FOTO) return erro("foto_grande", 413);
  const foto = await lerAte(pedido.body, MAX_FOTO);
  if (!foto) return erro("foto_grande", 413);
  const metadados = temMetadados(foto);
  if (metadados === null) return erro("pedido_invalido", 400);
  if (metadados) return erro("foto_com_metadados", 400);

  const agora = Date.now();
  const id = crypto.randomUUID();
  const chave = `${await sha256(foto)}.jpg`;
  const inserida = await env.DB.prepare(
    `INSERT INTO fotos (id, paragem, conta_id, chave, criada_em)
     SELECT ?1, ?2, ?3, ?4, ?5
     WHERE (SELECT COUNT(*) FROM (SELECT 1 FROM fotos WHERE conta_id = ?3 AND criada_em > ?6 LIMIT ?7)) < ?7
     ON CONFLICT DO NOTHING`,
  )
    .bind(id, paragem, quem.contaId, chave, agora, agora - DIA, REGRAS.porContaPorDia.fotos)
    .run();
  if (inserida.meta.changes === 0) {
    // Ou a paragem já tem foto (ou esta foto já está noutra paragem), ou a conta já gastou a do dia.
    const ocupada = await env.DB.prepare("SELECT 1 FROM fotos WHERE (paragem = ?1 AND estado <> 'rejeitada') OR chave = ?2 LIMIT 1").bind(paragem, chave).first();
    return ocupada ? erro("ja_tem_foto", 409) : erro("muitos_pedidos", 429);
  }

  const dia = new Date(agora).toISOString().slice(0, 10);
  const noTeto = await env.DB.prepare(
    `INSERT INTO contadores (dia, nome, quantos) VALUES (?1, 'fotos', 1)
     ON CONFLICT (dia, nome) DO UPDATE SET quantos = quantos + 1 WHERE quantos < ?2`,
  )
    .bind(dia, REGRAS.fotosPorDia)
    .run();
  if (noTeto.meta.changes === 0) {
    await env.DB.prepare("DELETE FROM fotos WHERE id = ?").bind(id).run();
    return erro("muitos_pedidos", 429);
  }

  try {
    await env.FOTOS_PENDENTES.put(chave, foto, { httpMetadata: { contentType: "image/jpeg" } });
  } catch {
    // Sem ficheiro não há foto: a linha sai e a conta fica com o lugar no teto. Nunca `em_pausa`, que pararia a app até à meia-noite.
    await env.DB.batch([
      env.DB.prepare("DELETE FROM fotos WHERE id = ?").bind(id),
      env.DB.prepare("UPDATE contadores SET quantos = quantos - 1 WHERE dia = ? AND nome = 'fotos' AND quantos > 0").bind(dia),
    ]);
    return erro("guardar_falhou", 502);
  }
  return json({ id, estado: "pendente" }, 201);
}

/**
 * As fotos aprovadas das `paragens` (com o endereço público) e as paragens que têm uma por rever. Só há uma foto que
 * não seja rejeitada por paragem, por isso são no máximo 100 linhas. Nada da conta nem a chave do pendente sai.
 */
export async function fotosDaComunidade(env: Env, paragens: string[]): Promise<{ fotos: FotoPublica[]; emRevisao: string[] }> {
  const { results } = await env.DB.prepare(
    // A aprovada tem sempre `decidida_em`; o `criada_em` só evita uma data de 1970 se alguma vez faltar.
    "SELECT id, paragem, chave, estado, COALESCE(decidida_em, criada_em) AS quando FROM fotos WHERE paragem IN (SELECT value FROM json_each(?1)) AND estado <> 'rejeitada' LIMIT ?2",
  )
    .bind(JSON.stringify(paragens), MAX_FOTOS_LIDAS)
    .all<{ id: string; paragem: string; chave: string | null; estado: string; quando: number }>();
  return {
    fotos: results
      .filter((f) => f.estado === "aprovada" && f.chave)
      .map((f) => ({ paragem: f.paragem, id: f.id, url: `${URL_FOTOS}/${f.chave}`, data: new Date(f.quando).toISOString().slice(0, 10) })),
    emRevisao: results.filter((f) => f.estado === "pendente").map((f) => f.paragem),
  };
}
