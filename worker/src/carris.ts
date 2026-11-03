import { comCache, FonteFalhou, pedirFonte } from "./fontes";
import { interruptores } from "./portao";
import { erro, json } from "./respostas";

/** O feed GTFS-RT oficial das posições da Carris (só aqui, no servidor; a app pede-o ao /api/rt/carris). */
const URL_CARRIS = "https://gateway.carris.pt/gateway/gtfs/api/v2.11/GTFS/realtime/vehiclepositions";

/** Um autocarro ou elétrico da Carris, como a app o recebe. `ts` e o `atualizado` da resposta são em segundos. */
export interface VeiculoCarris { id: string; rota: string; viagem: string; sentido: number; lat: number; lon: number; seq: number; paragem: string | null; ts: number }

type Valor = number | Uint8Array;

/**
 * Os campos de uma mensagem protobuf, pela ordem: os varint, fixed64 (double) e fixed32 (float) como número, os
 * length-delimited em bruto. Quem lê fica só com os campos que conhece; os outros saltam-se. Bytes cortados ou um tipo
 * de fio que o GTFS-RT não usa dão FonteFalhou.
 */
function* campos(bytes: Uint8Array): Generator<[number, Valor]> {
  const vista = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let pos = 0;
  const avancar = (n: number): number => {
    if (n > bytes.length - pos) throw new FonteFalhou("protobuf cortado");
    pos += n;
    return pos - n;
  };
  const varint = (): number => {
    // Até 10 bytes (64 bits); acima de 2^53 perde precisão, mas nenhum campo que lemos chega lá.
    let valor = 0;
    for (let escala = 1, i = 0; i < 10; i++, escala *= 128) {
      const byte = bytes[avancar(1)];
      valor += (byte & 0x7f) * escala;
      if (byte < 0x80) return valor;
    }
    throw new FonteFalhou("varint com mais de 10 bytes");
  };
  while (pos < bytes.length) {
    const chave = varint();
    const campo = Math.floor(chave / 8);
    const tipo = chave % 8;
    if (tipo === 0) yield [campo, varint()];
    else if (tipo === 1) yield [campo, vista.getFloat64(avancar(8), true)];
    else if (tipo === 2) {
      const tamanho = varint();
      const inicio = avancar(tamanho);
      yield [campo, bytes.subarray(inicio, inicio + tamanho)];
    } else if (tipo === 5) yield [campo, vista.getFloat32(avancar(4), true)];
    else throw new FonteFalhou(`tipo de fio ${tipo}`);
  }
}

const TEXTO = new TextDecoder();
const texto = (valor: Valor): string => (valor instanceof Uint8Array ? TEXTO.decode(valor) : "");
const numero = (valor: Valor): number => (typeof valor === "number" ? valor : 0);
const mensagem = (valor: Valor): Uint8Array => (valor instanceof Uint8Array ? valor : new Uint8Array());

/** Um `VehiclePosition`; sem posição não serve para o mapa (seria um autocarro no meio do mar, em 0,0). */
function lerVeiculo(bytes: Uint8Array, idDaEntidade: string): VeiculoCarris | null {
  const v: VeiculoCarris = { id: idDaEntidade, rota: "", viagem: "", sentido: 0, lat: 0, lon: 0, seq: 0, paragem: null, ts: 0 };
  let temPosicao = false;
  for (const [campo, valor] of campos(bytes)) {
    if (campo === 1) {
      for (const [c, x] of campos(mensagem(valor))) {
        if (c === 1) v.viagem = texto(x);
        else if (c === 5) v.rota = texto(x);
        else if (c === 6) v.sentido = numero(x);
      }
    } else if (campo === 2) {
      for (const [c, x] of campos(mensagem(valor))) {
        if (c === 1) v.lat = numero(x);
        else if (c === 2) v.lon = numero(x);
      }
      temPosicao = true;
    } else if (campo === 3) v.seq = numero(valor);
    else if (campo === 5) v.ts = numero(valor);
    else if (campo === 7) v.paragem = texto(valor) || null;
    else if (campo === 8) {
      for (const [c, x] of campos(mensagem(valor))) if (c === 1 && texto(x)) v.id = texto(x);
    }
  }
  return temPosicao ? v : null;
}

/**
 * Lê um `FeedMessage` do GTFS-RT: o `timestamp` do cabeçalho e os veículos (`entity.vehicle`). As entidades sem
 * veículo ou sem posição ficam de fora. Bytes inválidos dão FonteFalhou.
 */
export function lerPosicoes(bytes: Uint8Array): { ts: number; veiculos: VeiculoCarris[] } {
  let ts = 0;
  const veiculos: VeiculoCarris[] = [];
  for (const [campo, valor] of campos(bytes)) {
    if (campo === 1) {
      for (const [c, x] of campos(mensagem(valor))) if (c === 3) ts = numero(x);
    } else if (campo === 2) {
      let id = "";
      let veiculo: Uint8Array | null = null;
      for (const [c, x] of campos(mensagem(valor))) {
        if (c === 1) id = texto(x);
        else if (c === 4) veiculo = mensagem(x);
      }
      const lido = veiculo && lerVeiculo(veiculo, id);
      if (lido) veiculos.push(lido);
    }
  }
  return { ts, veiculos };
}

/** Tira os fantasmas: sinal com mais de 5 min, a `0` sem paragem e os com hora mais de 60 s no futuro. */
export function filtrar(v: VeiculoCarris[], agoraS: number): VeiculoCarris[] {
  return v.filter((x) => agoraS - x.ts <= 300 && x.ts - agoraS <= 60 && !(x.seq === 0 && x.paragem === null));
}

/** GET /api/rt/carris: as posições da Carris, pedidas no máximo uma vez a cada 30 s; o filtro corre a cada pedido. */
export async function carrisAoVivo(env: Env, ctx: ExecutionContext): Promise<Response> {
  if (!interruptores(env).carrisAoVivo) return erro("desligado", 403);
  const feed = await comCache(ctx, "carris-posicoes", 30, async () => {
    const lido = lerPosicoes(new Uint8Array(await (await pedirFonte(URL_CARRIS)).arrayBuffer()));
    // Sem hora no cabeçalho, conta a hora a que o recebemos.
    return { ts: lido.ts || Math.floor(Date.now() / 1000), veiculos: lido.veiculos };
  });
  return json({ atualizado: feed.ts, veiculos: filtrar(feed.veiculos, Math.floor(Date.now() / 1000)) });
}
