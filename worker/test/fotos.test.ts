import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { MAX_FOTO, temMetadados, URL_FOTOS } from "../src/fotos";
import { criarConta, LIGADO, pedir } from "./ajudas";

const servidor: Required<Pick<ExportedHandler<Env>, "fetch">> = worker;

const HORA = 60 * 60_000;
const DIA = 24 * HORA;
const INICIO = Date.UTC(2026, 10, 3, 8, 0);
const HOJE = "2026-11-03";
const PARAGEM = "CM:060419";

// Pedaços de um JPEG feito à mão: SOI, APP0 JFIF, o cabeçalho do SOS, os dados (com um FF00 e um RST0) e EOI.
const SOI = [0xff, 0xd8];
const EOI = [0xff, 0xd9];
const JFIF = [0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00];
const CABECALHO_SOS = [0xff, 0xda, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3f, 0x00];

function dados(marca = 0): number[] {
  return [0x12, 0x34, 0xff, 0x00, 0x56, 0xff, 0xd0, 0x78, marca];
}

/** Um segmento APP1 (Exif ou XMP) com o texto à frente. */
function app1(texto: string): number[] {
  const corpo = [...new TextEncoder().encode(texto), 1, 2, 3];
  return [0xff, 0xe1, (corpo.length + 2) >> 8, (corpo.length + 2) & 0xff, ...corpo];
}

function jpeg(...partes: number[][]): Uint8Array {
  return Uint8Array.from(partes.flat());
}

/** Um JPEG válido; `marca` muda um byte para cada foto ter o seu próprio SHA-256. */
function valido(marca = 0): Uint8Array {
  return jpeg(SOI, JFIF, CABECALHO_SOS, dados(marca), EOI);
}

/** Um JPEG válido com exatamente `tamanho` bytes. */
function comTamanho(tamanho: number): Uint8Array {
  const foto = new Uint8Array(tamanho);
  const cabeca = jpeg(SOI, JFIF, CABECALHO_SOS);
  foto.set(cabeca);
  foto.fill(0x11, cabeca.length, tamanho - 2);
  foto.set(EOI, tamanho - 2);
  return foto;
}

async function sha256(dadosDaFoto: Uint8Array): Promise<string> {
  const resumo = await crypto.subtle.digest("SHA-256", dadosDaFoto);
  return [...new Uint8Array(resumo)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

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

function enviar(token: string, foto: Uint8Array, paragem = PARAGEM, segredos: Partial<Env> = LIGADO): Promise<Response> {
  return pedir(`/api/fotos?paragem=${encodeURIComponent(paragem)}`, { token, corpo: foto, segredos, cabecalhos: { "content-type": "image/jpeg" } });
}

/** Como `enviar`, mas com o corpo como fluxo, para ver quanto dele o servidor lê. */
async function enviarFluxo(token: string, corpo: ReadableStream<Uint8Array>, cabecalhos: Record<string, string> = {}): Promise<Response> {
  const ctx = createExecutionContext();
  const resposta = await servidor.fetch(
    new Request(`https://exemplo.test/api/fotos?paragem=${PARAGEM}`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "image/jpeg", ...cabecalhos },
      body: corpo,
    }),
    { ...env, RESEND_API_KEY: undefined, ...LIGADO } as Env,
    ctx,
  );
  await waitOnExecutionContext(ctx);
  return resposta;
}

async function erroDe(resposta: Response): Promise<{ estado: number; erro: string }> {
  return { estado: resposta.status, erro: ((await resposta.json()) as { erro: string }).erro };
}

function lerComunidade(paragens: string[], segredos: Partial<Env> = LIGADO): Promise<Response> {
  return pedir(`/api/comunidade?paragens=${encodeURIComponent(paragens.join(","))}`, { metodo: "GET", segredos });
}

async function linhasEmFotos(): Promise<number> {
  return (await env.DB.prepare("SELECT COUNT(*) AS n FROM fotos").first<number>("n"))!;
}

async function ficheirosNoR2(): Promise<number> {
  return (await env.FOTOS_PENDENTES.list()).objects.length;
}

async function contadorDeFotos(): Promise<number> {
  return (await env.DB.prepare("SELECT quantos FROM contadores WHERE dia = ? AND nome = 'fotos'").bind(HOJE).first<number>("quantos")) ?? 0;
}

function inserirFoto(id: string, contaId: string, paragem: string, estado: string, chave: string | null, criadaEm = Date.now(), decididaEm: number | null = null): Promise<unknown> {
  return env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em, decidida_em) VALUES (?, ?, ?, ?, ?, ?, ?)")
    .bind(id, paragem, contaId, chave, estado, criadaEm, decididaEm)
    .run();
}

describe("envio de fotos", () => {
  it("fotoValidaFicaPendenteNoR2", async () => {
    const foto = valido();
    const resposta = await enviar(ana, foto);
    expect(resposta.status).toBe(201);
    const { id, estado } = (await resposta.json()) as { id: string; estado: string };
    expect(estado).toBe("pendente");

    const chave = `${await sha256(foto)}.jpg`;
    const guardada = await env.FOTOS_PENDENTES.get(chave);
    expect(guardada).not.toBeNull();
    expect(guardada!.httpMetadata?.contentType).toBe("image/jpeg");
    expect(new Uint8Array(await guardada!.arrayBuffer())).toEqual(foto);
    expect(await env.FOTOS.get(chave)).toBeNull();

    expect(await env.DB.prepare("SELECT id, paragem, conta_id, chave, estado FROM fotos").all()).toMatchObject({
      results: [{ id, paragem: PARAGEM, conta_id: "ana", chave, estado: "pendente" }],
    });
    expect(await contadorDeFotos()).toBe(1);
  });

  it("fotoGrandeDa413", async () => {
    expect(await erroDe(await enviar(ana, comTamanho(MAX_FOTO + 1)))).toEqual({ estado: 413, erro: "foto_grande" });
    expect(await linhasEmFotos()).toBe(0);
    expect(await ficheirosNoR2()).toBe(0);
    expect(await contadorDeFotos()).toBe(0);
  });

  it("fotoDe500KbExatosEntra", async () => {
    expect((await enviar(ana, comTamanho(MAX_FOTO))).status).toBe(201);
  });

  it("tamanhoDeclaradoGrandeDa413SemLerOCorpo", async () => {
    let lidos = 0;
    const corpo = new ReadableStream<Uint8Array>(
      {
        pull(controlo) {
          lidos++;
          controlo.enqueue(valido());
          controlo.close();
        },
      },
      { highWaterMark: 0 },
    );
    const resposta = await enviarFluxo(ana, corpo, { "content-length": String(MAX_FOTO + 1) });
    expect(await erroDe(resposta)).toEqual({ estado: 413, erro: "foto_grande" });
    expect(lidos).toBe(0);
  });

  it("corpoSemFimDa413SemLerTudo", async () => {
    // Sem Content-Length (ou com um que mente) o corpo pode não acabar: o servidor pára de ler logo a seguir ao limite.
    const pedaco = 64 * 1024;
    let lidos = 0;
    let cancelado = false;
    const infinito = new ReadableStream<Uint8Array>(
      {
        pull(controlo) {
          lidos += pedaco;
          controlo.enqueue(new Uint8Array(pedaco));
        },
        cancel() {
          cancelado = true;
        },
      },
      { highWaterMark: 0 },
    );
    expect(await erroDe(await enviarFluxo(ana, infinito))).toEqual({ estado: 413, erro: "foto_grande" });
    expect(lidos).toBeLessThanOrEqual(MAX_FOTO + 2 * pedaco);
    expect(cancelado).toBe(true);
    expect(await linhasEmFotos()).toBe(0);
  });

  it("fotoComExifDa400", async () => {
    const comExif = jpeg(SOI, JFIF, app1("Exif\0\0"), CABECALHO_SOS, dados(), EOI);
    expect(await erroDe(await enviar(ana, comExif))).toEqual({ estado: 400, erro: "foto_com_metadados" });
    expect(await linhasEmFotos()).toBe(0);
    expect(await ficheirosNoR2()).toBe(0);
  });

  it("fotoComXmpDa400", async () => {
    const comXmp = jpeg(SOI, JFIF, app1("http://ns.adobe.com/xap/1.0/\0"), CABECALHO_SOS, dados(), EOI);
    expect(await erroDe(await enviar(ana, comXmp))).toEqual({ estado: 400, erro: "foto_com_metadados" });
    expect(await linhasEmFotos()).toBe(0);
  });

  it("naoEJpegDa400", async () => {
    const png = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d, 0x49, 0x48, 0x44, 0x52]);
    const cortadoAntesDoSos = jpeg(SOI, JFIF);
    const comBytesDepoisDoEoi = jpeg(SOI, JFIF, CABECALHO_SOS, dados(), EOI, [0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70]);
    for (const [nome, corpo] of [["png", png], ["cortado", cortadoAntesDoSos], ["bytes depois do EOI", comBytesDepoisDoEoi], ["vazio", new Uint8Array()]] as const) {
      expect(await erroDe(await enviar(ana, corpo)), nome).toEqual({ estado: 400, erro: "pedido_invalido" });
    }
    expect(await linhasEmFotos()).toBe(0);
    expect(await ficheirosNoR2()).toBe(0);
  });

  it("paragemInvalidaOuEmFaltaDa400", async () => {
    for (const paragem of ["x", "CM:<script>", ""]) {
      expect(await erroDe(await enviar(ana, valido(), paragem)), paragem).toEqual({ estado: 400, erro: "pedido_invalido" });
    }
    expect(await erroDe(await pedir("/api/fotos", { token: ana, corpo: valido(), segredos: LIGADO }))).toEqual({ estado: 400, erro: "pedido_invalido" });
  });

  it("semSessaoDa401", async () => {
    expect(await erroDe(await pedir(`/api/fotos?paragem=${PARAGEM}`, { corpo: valido(), segredos: LIGADO }))).toEqual({ estado: 401, erro: "sem_sessao" });
  });

  it("segundaFotoNoMesmoDiaDa429", async () => {
    expect((await enviar(ana, valido(1), "CM:1")).status).toBe(201);
    expect(await erroDe(await enviar(ana, valido(2), "CM:2"))).toEqual({ estado: 429, erro: "muitos_pedidos" });
    expect(await linhasEmFotos()).toBe(1);
    expect(await ficheirosNoR2()).toBe(1);
    expect(await contadorDeFotos()).toBe(1);

    avancar(DIA);
    expect((await enviar(ana, valido(2), "CM:2")).status).toBe(201);
  });

  it("tetoGlobalDe100Da429", async () => {
    await env.DB.prepare("INSERT INTO contadores (dia, nome, quantos) VALUES (?, 'fotos', 100)").bind(HOJE).run();
    expect(await erroDe(await enviar(ana, valido()))).toEqual({ estado: 429, erro: "muitos_pedidos" });
    expect(await linhasEmFotos()).toBe(0);
    expect(await ficheirosNoR2()).toBe(0);
    expect(await contadorDeFotos()).toBe(100);
  });

  it("paragemComFotoDa409", async () => {
    expect((await enviar(ana, valido(1))).status).toBe(201);
    expect(await erroDe(await enviar(rui, valido(2)))).toEqual({ estado: 409, erro: "ja_tem_foto" });
    // A mesma conta a repetir (resposta perdida): também 409, e não gasta o teto global.
    expect(await erroDe(await enviar(ana, valido(1)))).toEqual({ estado: 409, erro: "ja_tem_foto" });
    expect(await linhasEmFotos()).toBe(1);
    expect(await ficheirosNoR2()).toBe(1);
    expect(await contadorDeFotos()).toBe(1);
  });

  it("mesmoFicheiroEmDuasParagensDa409", async () => {
    expect((await enviar(ana, valido(7), "CM:1")).status).toBe(201);
    expect(await erroDe(await enviar(rui, valido(7), "CM:2"))).toEqual({ estado: 409, erro: "ja_tem_foto" });
    expect(await linhasEmFotos()).toBe(1);
  });

  it("rejeitadaContaParaOLimiteMasLibertaAParagem", async () => {
    await inserirFoto("velha", "ana", "CM:1", "rejeitada", null, Date.now() - HORA);
    // A conta que foi rejeitada ainda gastou a foto do dia; a paragem, essa, aceita logo outra.
    expect(await erroDe(await enviar(ana, valido(1), "CM:2"))).toEqual({ estado: 429, erro: "muitos_pedidos" });
    expect((await enviar(rui, valido(2), "CM:1")).status).toBe(201);
  });

  it("duasDaMesmaContaEmParaleloSoUmaEntra", async () => {
    const respostas = await Promise.all([enviar(ana, valido(1), "CM:1"), enviar(ana, valido(2), "CM:2")]);
    expect(respostas.map((r) => r.status).sort()).toEqual([201, 429]);
    expect(await linhasEmFotos()).toBe(1);
    expect(await ficheirosNoR2()).toBe(1);
    expect(await contadorDeFotos()).toBe(1);
  });

  it("duasContasNaMesmaParagemEmParaleloSoUmaEntra", async () => {
    const respostas = await Promise.all([enviar(ana, valido(1)), enviar(rui, valido(2))]);
    expect(respostas.map((r) => r.status).sort()).toEqual([201, 409]);
    expect(await linhasEmFotos()).toBe(1);
    expect(await ficheirosNoR2()).toBe(1);
    expect(await contadorDeFotos()).toBe(1);
  });

  it("tetoGlobalEmParaleloNaoPassaDe100", async () => {
    await env.DB.prepare("INSERT INTO contadores (dia, nome, quantos) VALUES (?, 'fotos', 99)").bind(HOJE).run();
    const respostas = await Promise.all([enviar(ana, valido(1), "CM:1"), enviar(rui, valido(2), "CM:2"), enviar(eva, valido(3), "CM:3")]);
    expect(respostas.map((r) => r.status).sort()).toEqual([201, 429, 429]);
    expect(await contadorDeFotos()).toBe(100);
    expect(await linhasEmFotos()).toBe(1);
    expect(await ficheirosNoR2()).toBe(1);
  });

  it("r2EmBaixoDa502ESoltaALinhaEOLugarNoTeto", async () => {
    const avariado = { put: async () => Promise.reject(new Error("R2 em baixo")) } as unknown as R2Bucket;
    expect(await erroDe(await enviar(ana, valido(), PARAGEM, { ...LIGADO, FOTOS_PENDENTES: avariado }))).toEqual({ estado: 502, erro: "guardar_falhou" });
    expect(await linhasEmFotos()).toBe(0);
    expect(await contadorDeFotos()).toBe(0);
    // A conta não perdeu a foto do dia: tenta outra vez e entra.
    expect((await enviar(ana, valido())).status).toBe(201);
  });

  it("pendenteNaoApareceMasMarcaEmRevisao", async () => {
    const foto = valido();
    expect((await enviar(ana, foto)).status).toBe(201);
    const corpo = await (await lerComunidade([PARAGEM, "CM:outra"])).text();
    expect(JSON.parse(corpo)).toMatchObject({ fotos: [], emRevisao: [PARAGEM] });
    expect(corpo).not.toContain(await sha256(foto));
    expect(corpo).not.toContain("ana");
  });

  it("aprovadaApareceComUrl", async () => {
    await inserirFoto("f1", "ana", PARAGEM, "aprovada", "abc123.jpg");
    const resposta = await lerComunidade([PARAGEM]);
    expect(resposta.status).toBe(200);
    expect(await resposta.json()).toMatchObject({ fotos: [{ paragem: PARAGEM, id: "f1", url: `${URL_FOTOS}/abc123.jpg` }], emRevisao: [] });
    expect(URL_FOTOS).toBe("https://fotos.pixellusitano.com");
    expect(JSON.stringify(await (await lerComunidade([PARAGEM])).json())).not.toContain("ana");
  });

  it("aprovadaTrazADataDaDecisaoEmUtcEAPendenteContinuaSemFoto", async () => {
    // 23:30 UTC de 2 de julho já é dia 3 em Lisboa: a data é a UTC, e a da decisão (não a do envio).
    await inserirFoto("f1", "ana", PARAGEM, "aprovada", "abc123.jpg", Date.UTC(2026, 5, 30, 9, 0), Date.UTC(2026, 6, 2, 23, 30));
    await inserirFoto("f2", "rui", "CM:2", "pendente", "def456.jpg");
    const dados = await (await lerComunidade([PARAGEM, "CM:2"])).json();
    expect(dados).toMatchObject({ fotos: [{ paragem: PARAGEM, id: "f1", url: `${URL_FOTOS}/abc123.jpg`, data: "2026-07-02" }], emRevisao: ["CM:2"] });
    expect((dados as { fotos: unknown[] }).fotos).toHaveLength(1);
  });

  it("aprovadaSemDataDeDecisaoUsaADoEnvio", async () => {
    // Nunca devia acontecer (aprovar grava as duas), mas uma data de 1970 faria a foto perder sempre contra a de rua.
    await inserirFoto("f1", "ana", PARAGEM, "aprovada", "abc123.jpg", Date.UTC(2026, 5, 30, 9, 0));
    expect(await (await lerComunidade([PARAGEM])).json()).toMatchObject({ fotos: [{ id: "f1", data: "2026-06-30" }] });
  });

  it("rejeitadaNaoApareceNaLeitura", async () => {
    await inserirFoto("f1", "ana", PARAGEM, "rejeitada", null);
    expect(await (await lerComunidade([PARAGEM])).json()).toMatchObject({ fotos: [], emRevisao: [] });
  });

  it("fotosDesligadasDa403EFicamForaDaLeitura", async () => {
    const soComunidade = { ...LIGADO, TESTE_INTERRUPTORES: JSON.stringify({ comunidade: true }) };
    expect(await erroDe(await enviar(ana, valido(), PARAGEM, soComunidade))).toEqual({ estado: 403, erro: "desligado" });
    await inserirFoto("f1", "ana", PARAGEM, "aprovada", "abc123.jpg");
    await inserirFoto("f2", "rui", "CM:2", "pendente", "def456.jpg");
    expect(await (await lerComunidade([PARAGEM, "CM:2"], soComunidade)).json()).toMatchObject({ fotos: [], emRevisao: [] });
    expect(await linhasEmFotos()).toBe(2);
  });
});

describe("temMetadados", () => {
  const exif = app1("Exif\0\0");
  const iptc = [0xff, 0xed, 0x00, 0x04, 0x01, 0x02];

  it("jpegSemMetadadosDaFalse", () => {
    expect(temMetadados(valido())).toBe(false);
    // Progressivo: dois SOS com um DHT (C4) pelo meio.
    const dht = [0xff, 0xc4, 0x00, 0x04, 0x00, 0x00];
    expect(temMetadados(jpeg(SOI, JFIF, CABECALHO_SOS, dados(), dht, CABECALHO_SOS, dados(), EOI))).toBe(false);
    // Preenchimento FF antes do marcador final é permitido.
    expect(temMetadados(jpeg(SOI, JFIF, CABECALHO_SOS, dados(), [0xff, 0xff], EOI))).toBe(false);
  });

  it("exifXmpEIptcDaTrue", () => {
    expect(temMetadados(jpeg(SOI, JFIF, exif, CABECALHO_SOS, dados(), EOI))).toBe(true);
    expect(temMetadados(jpeg(SOI, app1("http://ns.adobe.com/xap/1.0/\0"), JFIF, CABECALHO_SOS, dados(), EOI))).toBe(true);
    expect(temMetadados(jpeg(SOI, JFIF, iptc, CABECALHO_SOS, dados(), EOI))).toBe(true);
  });

  it("metadadosDepoisDoPrimeiroSosTambemContam", () => {
    const dht = [0xff, 0xc4, 0x00, 0x04, 0x00, 0x00];
    expect(temMetadados(jpeg(SOI, JFIF, CABECALHO_SOS, dados(), exif, CABECALHO_SOS, dados(), EOI))).toBe(true);
    expect(temMetadados(jpeg(SOI, JFIF, CABECALHO_SOS, dados(), dht, iptc, CABECALHO_SOS, dados(), EOI))).toBe(true);
  });

  it("segundoJpegDepoisDoPrimeiroEoiDaNull", () => {
    // Um mapa de ganho Ultra HDR ou outra imagem colada no fim também acaba em FF D9.
    expect(temMetadados(jpeg([...valido()], [...valido()]))).toBeNull();
    expect(temMetadados(jpeg([...valido()], SOI, exif, CABECALHO_SOS, dados(), EOI))).toBeNull();
  });

  it("estruturasMalFormadasDaNullSemFicarPresas", () => {
    const casos: Record<string, number[]> = {
      vazio: [],
      soSoi: [...SOI],
      soiEoi: [...SOI, ...EOI],
      semFfDepoisDoSoi: [...SOI, 0x00, 0x00, 0x00, 0x00, ...EOI],
      semEoiNoFim: [...SOI, ...JFIF, ...CABECALHO_SOS, ...dados(), 0x00, 0x00],
      // Um tamanho menor que 2 faria o ciclo andar para trás ou ficar parado.
      tamanhoZero: [...SOI, 0xff, 0xe0, 0x00, 0x00, ...EOI],
      tamanhoUm: [...SOI, 0xff, 0xe0, 0x00, 0x01, ...EOI],
      tamanhoAPassarDoFim: [...SOI, 0xff, 0xe0, 0xff, 0xff, 0x00, 0x00, ...EOI],
      sosAPassarDoFim: [...SOI, ...JFIF, 0xff, 0xda, 0xff, 0xff, ...EOI],
      preenchimentoEntreSegmentos: [...SOI, 0xff, 0xff, 0xff, 0xe0, 0x00, 0x02, ...CABECALHO_SOS, ...dados(), ...EOI],
      rstForaDoSitio: [...SOI, 0xff, 0xd3, ...CABECALHO_SOS, ...dados(), ...EOI],
      soiAninhado: [...SOI, ...SOI, ...CABECALHO_SOS, ...dados(), ...EOI],
      eoiSemImagem: [...SOI, ...JFIF, ...EOI],
      cortadoNoMeioDoTamanho: [...SOI, 0xff, 0xe0, 0x00, ...EOI],
    };
    for (const [nome, bytes] of Object.entries(casos)) expect(temMetadados(Uint8Array.from(bytes)), nome).toBeNull();
  });

  it("entradasMaldosasAcabamSemAlocarNemFicarPresas", () => {
    // 500 KB de FF depois do SOS: cada FF é preenchimento, e o fim é um EOI válido.
    const soFf = jpeg(SOI, JFIF, CABECALHO_SOS, new Array<number>(MAX_FOTO).fill(0xff), [0xd9]);
    expect(temMetadados(soFf)).toBe(false);
    // Os mesmos 500 KB a abrir como se fossem segmentos.
    expect(temMetadados(jpeg(SOI, new Array<number>(MAX_FOTO).fill(0xff), EOI))).toBeNull();
    // Muitos segmentos mínimos de comentário (FF FE 00 02), um a seguir ao outro.
    const comentarios = Array.from({ length: MAX_FOTO / 8 }, () => [0xff, 0xfe, 0x00, 0x02]).flat();
    expect(temMetadados(jpeg(SOI, comentarios, CABECALHO_SOS, dados(), EOI))).toBe(false);
  });
});
