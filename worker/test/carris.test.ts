import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { filtrar, lerPosicoes, type VeiculoCarris } from "../src/carris";
import { FonteFalhou } from "../src/fontes";
import { pedir } from "./ajudas";

// Um codificador de protobuf mínimo, só para escrever os feeds dos testes (sem dependências).
function varint(n: number): number[] {
  const bytes: number[] = [];
  while (n >= 128) {
    bytes.push((n % 128) | 128);
    n = Math.floor(n / 128);
  }
  bytes.push(n);
  return bytes;
}
const chave = (campo: number, tipo: number) => varint(campo * 8 + tipo);
const numero = (campo: number, n: number) => [...chave(campo, 0), ...varint(n)];
const bloco = (campo: number, bytes: number[]) => [...chave(campo, 2), ...varint(bytes.length), ...bytes];
const texto = (campo: number, valor: string) => bloco(campo, [...new TextEncoder().encode(valor)]);
function fixo(campo: number, tipo: 1 | 5, valor: number): number[] {
  const bytes = new Uint8Array(tipo === 1 ? 8 : 4);
  const vista = new DataView(bytes.buffer);
  if (tipo === 1) vista.setFloat64(0, valor, true);
  else vista.setFloat32(0, valor, true);
  return [...chave(campo, tipo), ...bytes];
}

interface Escrito { id: string; rota: string; viagem: string; sentido: number; lat: number; lon: number; seq: number; paragem?: string; ts: number }

/** Um `FeedEntity` com o `VehiclePosition` deste veículo; `extra` junta campos ao `VehiclePosition`, `extraPosicao` ao `Position`. */
function entidade(v: Escrito, extra: number[] = [], extraPosicao: number[] = []): number[] {
  const viagem = [...texto(1, v.viagem), ...texto(5, v.rota), ...numero(6, v.sentido)];
  const posicao = [...fixo(1, 5, v.lat), ...fixo(2, 5, v.lon), ...extraPosicao];
  const veiculo = [
    ...bloco(1, viagem),
    ...bloco(2, posicao),
    ...numero(3, v.seq),
    ...numero(5, v.ts),
    ...(v.paragem === undefined ? [] : texto(7, v.paragem)),
    ...bloco(8, texto(1, v.id)),
    ...extra,
  ];
  return bloco(2, [...texto(1, `e${v.id}`), ...bloco(4, veiculo)]);
}

function feed(ts: number, entidades: number[][], extraCabecalho: number[] = []): Uint8Array {
  const cabecalho = [...texto(1, "2.0"), ...numero(3, ts), ...extraCabecalho];
  return new Uint8Array([...bloco(1, cabecalho), ...entidades.flat()]);
}

const AGORA = Date.UTC(2026, 5, 6, 10, 0, 0);
const AGORA_S = AGORA / 1000;
const CONHECIDO: Escrito = { id: "5247", rota: "103_0", viagem: "4502_20260606_103_0_8", sentido: 1, lat: 38.7068, lon: -9.1754, seq: 19, paragem: "12105", ts: AGORA_S - 20 };
const LIGADO = { AMBIENTE: "teste", TESTE_INTERRUPTORES: JSON.stringify({ carrisAoVivo: true }) } as Partial<Env>;
const URL_CARRIS = "https://gateway.carris.pt/gateway/gtfs/api/v2.11/GTFS/realtime/vehiclepositions";

function comoVeiculo(v: Escrito): VeiculoCarris {
  return { id: v.id, rota: v.rota, viagem: v.viagem, sentido: v.sentido, lat: v.lat, lon: v.lon, seq: v.seq, paragem: v.paragem ?? null, ts: v.ts };
}

function esperarVeiculo(lido: VeiculoCarris, esperado: Escrito): void {
  const { lat, lon, ...resto } = lido;
  const { lat: latEsperada, lon: lonEsperada, ...restoEsperado } = comoVeiculo(esperado);
  expect(resto).toEqual(restoEsperado);
  expect(Math.abs(lat - latEsperada)).toBeLessThan(1e-4);
  expect(Math.abs(lon - lonEsperada)).toBeLessThan(1e-4);
}

const carris = () => pedir("/api/rt/carris", { metodo: "GET", segredos: LIGADO });

describe("Carris ao vivo", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(AGORA);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("lePosicoesDeUmFeedConhecido", async () => {
    const lido = lerPosicoes(feed(AGORA_S - 5, [entidade(CONHECIDO)]));
    expect(lido.ts).toBe(AGORA_S - 5);
    expect(lido.veiculos).toHaveLength(1);
    esperarVeiculo(lido.veiculos[0], CONHECIDO);

    // O mesmo pela rota, com a resposta que a app lê.
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(feed(AGORA_S - 5, [entidade(CONHECIDO)])));
    const resposta = await carris();
    expect(resposta.status).toBe(200);
    expect(resposta.headers.get("cache-control")).toBe("no-store");
    const corpo = (await resposta.json()) as { atualizado: number; veiculos: VeiculoCarris[] };
    expect(corpo.atualizado).toBe(AGORA_S - 5);
    expect(corpo.veiculos).toHaveLength(1);
    esperarVeiculo(corpo.veiculos[0], CONHECIDO);
  });

  it("tiraPosicoesVelhasSemParagemEDoFuturo", async () => {
    const velho = { ...CONHECIDO, id: "1", ts: AGORA_S - 301 };
    const semParagem = { ...CONHECIDO, id: "2", seq: 0, paragem: undefined };
    const doFuturo = { ...CONHECIDO, id: "3", ts: AGORA_S + 61 };
    const quaseVelho = { ...CONHECIDO, id: "4", ts: AGORA_S - 299 };
    const comZeroEParagem = { ...CONHECIDO, id: "5", seq: 0 };
    const todos = [velho, semParagem, doFuturo, quaseVelho, comZeroEParagem].map(comoVeiculo);
    expect(filtrar(todos, AGORA_S).map((v) => v.id)).toEqual(["4", "5"]);

    // A rota aplica o mesmo filtro.
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(feed(AGORA_S, [velho, semParagem, doFuturo, quaseVelho].map((v) => entidade(v)))));
    const corpo = (await (await carris()).json()) as { veiculos: VeiculoCarris[] };
    expect(corpo.veiculos.map((v) => v.id)).toEqual(["4"]);
  });

  it("campoDesconhecidoNaoParteALeitura", () => {
    const desconhecidos = [...numero(99, 2 ** 40), ...fixo(99, 1, 1.5), ...texto(99, "qualquer coisa"), ...fixo(99, 5, 2.5)];
    const odometro = fixo(4, 1, 123456.75); // Position.odometer é um double (fixed64)
    const entidadeSemVeiculo = bloco(2, [...texto(1, "alerta"), ...desconhecidos]);
    const bytes = feed(AGORA_S, [entidadeSemVeiculo, entidade(CONHECIDO, desconhecidos, odometro)], desconhecidos);
    const lido = lerPosicoes(new Uint8Array([...desconhecidos, ...bytes]));
    expect(lido.ts).toBe(AGORA_S);
    expect(lido.veiculos).toHaveLength(1);
    esperarVeiculo(lido.veiculos[0], CONHECIDO);
  });

  it("protobufCortadoDa502", async () => {
    const inteiro = feed(AGORA_S, [entidade(CONHECIDO)]);
    const cortado = inteiro.subarray(0, inteiro.length - 3);
    expect(() => lerPosicoes(cortado)).toThrow(FonteFalhou);
    // Um varint que acaba a meio também.
    expect(() => lerPosicoes(new Uint8Array([0x10, 0x80]))).toThrow(FonteFalhou);

    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(cortado));
    const resposta = await carris();
    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ erro: "fonte_falhou" });
  });

  it("carrisEmBaixoGuardaAFalha30Segundos", async () => {
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response("erro", { status: 500 }));
    const resposta = await carris();
    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ erro: "fonte_falhou" });

    // Dentro dos 30 s, o mesmo 502 sem voltar à Carris.
    vi.setSystemTime(AGORA + 29_000);
    const guardada = await carris();
    expect(guardada.status).toBe(502);
    expect(await guardada.json()).toEqual({ erro: "fonte_falhou" });
    expect(fetchFalso).toHaveBeenCalledTimes(1);

    fetchFalso.mockImplementation(async () => new Response(feed(AGORA_S, [entidade(CONHECIDO)])));
    vi.setSystemTime(AGORA + 31_000);
    expect((await carris()).status).toBe(200);
    expect(fetchFalso).toHaveBeenCalledTimes(2);
  });

  it("carrisLentaDa502", async () => {
    // O relógio do AbortSignal.timeout não é o Date falso: o teste confirma os 8 s e dá logo um sinal já esgotado.
    const prazo = vi.spyOn(AbortSignal, "timeout").mockImplementation(() => AbortSignal.abort(new DOMException("demorou", "TimeoutError")));
    vi.spyOn(globalThis, "fetch").mockImplementation(
      (_url, init) =>
        new Promise((_, rejeitar) => {
          const sinal = init?.signal;
          if (!sinal) return; // sem prazo, este pedido nunca acabava
          if (sinal.aborted) rejeitar(sinal.reason);
          sinal.addEventListener("abort", () => rejeitar(sinal.reason));
        }),
    );
    const resposta = await carris();
    expect(prazo).toHaveBeenCalledWith(8000);
    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ erro: "fonte_falhou" });
  });

  it("guarda30Segundos", async () => {
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response(feed(AGORA_S, [entidade(CONHECIDO)])));
    expect((await carris()).status).toBe(200);
    vi.setSystemTime(AGORA + 29_000);
    expect((await carris()).status).toBe(200);
    expect(fetchFalso).toHaveBeenCalledTimes(1);
    vi.setSystemTime(AGORA + 31_000);
    expect((await carris()).status).toBe(200);
    expect(fetchFalso).toHaveBeenCalledTimes(2);
  });

  it("posicaoSemCoordenadasSai", () => {
    // Um VehiclePosition com o bloco position mas sem latitude ou sem longitude seria um autocarro em 0,0.
    const comPosicao = (id: string, posicao: number[]) =>
      bloco(2, [...texto(1, `e${id}`), ...bloco(4, [...bloco(2, posicao), ...numero(3, 19), ...numero(5, AGORA_S), ...texto(7, "12105"), ...bloco(8, texto(1, id))])]);
    const bytes = feed(AGORA_S, [comPosicao("1", fixo(1, 5, 38.7)), comPosicao("2", fixo(2, 5, -9.1)), comPosicao("3", []), entidade(CONHECIDO)]);
    expect(lerPosicoes(bytes).veiculos.map((v) => v.id)).toEqual(["5247"]);
  });

  it("respostaGrandeDemaisDa502", async () => {
    const LIMITE = 2 * 1024 * 1024;
    const fetchFalso = vi.spyOn(globalThis, "fetch");
    // Diz logo no Content-Length que passa dos 2 MB.
    fetchFalso.mockResolvedValueOnce(new Response(feed(AGORA_S, [entidade(CONHECIDO)]), { headers: { "content-length": String(LIMITE + 1) } }));
    let resposta = await carris();
    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ erro: "fonte_falhou" });

    // Não diz o tamanho e manda mais do que 2 MB (zeros, que o leitor aceitaria).
    const aos64KB = (total: number) => {
      let enviados = 0;
      return new ReadableStream<Uint8Array>({
        pull(controlo) {
          if (enviados >= total) return controlo.close();
          controlo.enqueue(new Uint8Array(Math.min(64 * 1024, total - enviados)));
          enviados += 64 * 1024;
        },
      });
    };
    // Cada falha fica guardada 30 s: depois disso volta-se à fonte.
    vi.setSystemTime(AGORA + 31_000);
    fetchFalso.mockResolvedValueOnce(new Response(aos64KB(LIMITE + 1)));
    resposta = await carris();
    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ erro: "fonte_falhou" });

    // Exatamente 2 MB ainda passa.
    vi.setSystemTime(AGORA + 62_000);
    fetchFalso.mockResolvedValueOnce(new Response(aos64KB(LIMITE)));
    expect((await carris()).status).toBe(200);
    expect(fetchFalso).toHaveBeenCalledTimes(3);
  });

  it("cacheQueFalhaPedeAFonte", async () => {
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response(feed(AGORA_S, [entidade(CONHECIDO)])));
    const match = vi.fn((_chave: RequestInfo | URL) => Promise.reject(new Error("a cache não lê")));
    const put = vi.fn((_chave: RequestInfo | URL, _resposta: Response) => Promise.reject(new Error("a cache não guarda")));
    // Primeiro o caches.open falha; depois abre uma cache que não lê nem guarda.
    vi.spyOn(caches, "open")
      .mockRejectedValueOnce(new Error("sem cache"))
      .mockResolvedValue({ match, put } as unknown as Cache);
    for (let i = 0; i < 3; i++) {
      const resposta = await carris();
      expect(resposta.status).toBe(200);
      const corpo = (await resposta.json()) as { veiculos: VeiculoCarris[] };
      esperarVeiculo(corpo.veiculos[0], CONHECIDO);
    }
    expect(fetchFalso).toHaveBeenCalledTimes(3);
    expect(put).toHaveBeenCalledTimes(2);
    // A chave fica no endereço do próprio Worker (nunca é servida).
    expect(String(match.mock.calls[0][0])).toBe("https://paragemcerta.pixellusitano.com/__fontes/carris-posicoes");
    expect(String(put.mock.calls[0][0])).toBe("https://paragemcerta.pixellusitano.com/__fontes/carris-posicoes");
  });

  it("carrisDesligadoDa403", async () => {
    const fetchFalso = vi.spyOn(globalThis, "fetch");
    for (const segredos of [{}, { AMBIENTE: "teste", TESTE_INTERRUPTORES: JSON.stringify({ comunidade: true }) }] as Partial<Env>[]) {
      const resposta = await pedir("/api/rt/carris", { metodo: "GET", segredos });
      expect(resposta.status).toBe(403);
      expect(await resposta.json()).toEqual({ erro: "desligado" });
    }
    expect(fetchFalso).not.toHaveBeenCalled();
  });

  it("pedeComOUserAgent", async () => {
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response(feed(AGORA_S, [])));
    expect((await carris()).status).toBe(200);
    expect(fetchFalso).toHaveBeenCalledTimes(1);
    const [url, init] = fetchFalso.mock.calls[0];
    expect(String(url)).toBe(URL_CARRIS);
    expect(new Headers(init?.headers).get("user-agent")).toBe("ParagemCerta/0.4.0 (contact@pixellusitano.com; https://pixellusitano.com)");
  });
});
