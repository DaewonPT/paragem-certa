import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import config from "../public/dados/config.json";
import { mensagensEnviadas } from "../src/email";
import { contaDaSessao } from "../src/sessoes";
import { pedidoComToken, pedir, sha256 } from "./ajudas";

const MINUTO = 60_000;
const EMAIL = "ana@exemplo.pt";

beforeEach(() => {
  mensagensEnviadas.length = 0;
});

afterEach(() => {
  vi.restoreAllMocks();
});

function pedirCodigo(email: unknown = EMAIL): Promise<Response> {
  return pedir("/api/auth/email/pedir", { corpo: { email } });
}

function confirmar(codigo: unknown, email: unknown = EMAIL): Promise<Response> {
  return pedir("/api/auth/email/confirmar", { corpo: { email, codigo } });
}

/** O código que o carteiro de teste "enviou" por último. */
function ultimoCodigo(): string {
  return /\b(\d{6})\b/.exec(mensagensEnviadas.at(-1)!.texto)![1];
}

/** Um código de 6 dígitos garantidamente diferente de `certo`. */
function outroCodigo(certo: string): string {
  return String((Number(certo) + 1) % 1_000_000).padStart(6, "0");
}

async function tentativas(): Promise<number> {
  const linha = await env.DB.prepare("SELECT tentativas FROM codigos ORDER BY rowid DESC LIMIT 1").first<{ tentativas: number }>();
  return linha!.tentativas;
}

/** Põe os pedidos de código `ms` milissegundos mais para trás no tempo. */
async function envelhecerCodigos(ms: number): Promise<void> {
  await env.DB.prepare("UPDATE codigos SET criado_em = criado_em - ?").bind(ms).run();
}

describe("entrar com código por email", () => {
  it("pedirEConfirmarDaSessao", async () => {
    const pedido = await pedirCodigo("  Ana@Exemplo.PT ");
    expect(pedido.status).toBe(204);
    expect(mensagensEnviadas).toHaveLength(1);
    const mensagem = mensagensEnviadas[0];
    expect(mensagem.para).toBe(EMAIL);
    expect(mensagem.assunto).toBe("O teu código da Paragem Certa");
    expect(mensagem.texto).toContain("Válido durante 10 minutos.");
    const codigo = ultimoCodigo();

    const confirmacao = await confirmar(` ${codigo} `, "ANA@exemplo.pt");
    expect(confirmacao.status).toBe(200);
    const { token, email } = (await confirmacao.json()) as { token: string; email: string };
    expect(email).toBe(EMAIL);
    expect(token).toMatch(/^[A-Za-z0-9_-]{43}$/);

    const conta = await contaDaSessao(pedidoComToken(token), env);
    const guardada = await env.DB.prepare("SELECT id, email, google_sub FROM contas").all<{ id: string; email: string; google_sub: string | null }>();
    expect(guardada.results).toHaveLength(1);
    expect(guardada.results[0]).toMatchObject({ email: EMAIL, google_sub: null });
    expect(conta).toEqual({ contaId: guardada.results[0].id });
  });

  it("entrarOutraVezEncontraAMesmaConta", async () => {
    await pedirCodigo();
    const primeira = (await (await confirmar(ultimoCodigo())).json()) as { token: string };
    await pedirCodigo();
    const segunda = (await (await confirmar(ultimoCodigo())).json()) as { token: string };
    expect(segunda.token).not.toBe(primeira.token);
    const { n } = (await env.DB.prepare("SELECT COUNT(*) AS n FROM contas").first<{ n: number }>())!;
    expect(n).toBe(1);
    expect(await contaDaSessao(pedidoComToken(primeira.token), env)).toEqual(await contaDaSessao(pedidoComToken(segunda.token), env));
  });

  it("codigoNaoEntraNumaContaGoogleDeOutroDominio", async () => {
    // O endereço pode ter mudado de dono desde que a conta do Google foi criada.
    await env.DB.prepare("INSERT INTO contas (id, email, google_sub, criada_em) VALUES ('g1', ?, 'sub-1', 1)").bind(EMAIL).run();
    await pedirCodigo();
    const { token } = (await (await confirmar(ultimoCodigo())).json()) as { token: string };
    expect(await contaDaSessao(pedidoComToken(token), env)).not.toEqual({ contaId: "g1" });
  });

  it("codigoEntraNaContaGoogleDoGmail", async () => {
    await env.DB.prepare("INSERT INTO contas (id, email, google_sub, criada_em) VALUES ('g1', 'ana@gmail.com', 'sub-1', 1)").run();
    await pedirCodigo("ana@gmail.com");
    const { token } = (await (await confirmar(ultimoCodigo(), "ana@gmail.com")).json()) as { token: string };
    expect(await contaDaSessao(pedidoComToken(token), env)).toEqual({ contaId: "g1" });
  });

  it("codigoSoServeUmaVez", async () => {
    await pedirCodigo();
    const codigo = ultimoCodigo();
    expect((await confirmar(codigo)).status).toBe(200);
    const outra = await confirmar(codigo);
    expect(outra.status).toBe(410);
    expect(await outra.json()).toEqual({ erro: "codigo_expirado" });
  });

  it("novoPedidoSubstituiOAnterior", async () => {
    await pedirCodigo();
    const primeiro = ultimoCodigo();
    await pedirCodigo();
    if (ultimoCodigo() === primeiro) await pedirCodigo(); // 1 em 1 000 000 sai igual
    const ultimo = ultimoCodigo();
    expect((await confirmar(primeiro)).status).toBe(401);
    expect((await confirmar(ultimo)).status).toBe(200);
  });

  it("codigoGuardaSoOHash", async () => {
    await pedirCodigo();
    const codigo = ultimoCodigo();
    const { results } = await env.DB.prepare("SELECT * FROM codigos").all<{ hash: string; sal: string }>();
    expect(results).toHaveLength(1);
    expect(results[0].sal.length).toBeGreaterThanOrEqual(16);
    expect(results[0].hash).toBe(await sha256(results[0].sal + codigo));
    expect(JSON.stringify(results)).not.toContain(codigo);
  });

  it("codigoErradoContaTentativas", async () => {
    await pedirCodigo();
    const certo = ultimoCodigo();
    for (const n of [1, 2]) {
      const resposta = await confirmar(outroCodigo(certo));
      expect(resposta.status).toBe(401);
      expect(await resposta.json()).toEqual({ erro: "codigo_errado" });
      expect(await tentativas()).toBe(n);
    }
    expect((await confirmar(certo)).status).toBe(200);
  });

  it("quintaTentativaCertaAindaServe", async () => {
    await pedirCodigo();
    const certo = ultimoCodigo();
    for (let n = 0; n < 4; n++) expect((await confirmar(outroCodigo(certo))).status).toBe(401);
    expect((await confirmar(certo)).status).toBe(200);
  });

  it("sextaTentativaJaNaoServe", async () => {
    await pedirCodigo();
    const certo = ultimoCodigo();
    for (let n = 0; n < 5; n++) expect((await confirmar(outroCodigo(certo))).status).toBe(401);
    // À 5.ª errada o código morre e deixa de haver com que comparar.
    const morto = await env.DB.prepare("SELECT hash, sal FROM codigos").first<{ hash: string; sal: string }>();
    expect(morto).toEqual({ hash: "", sal: "" });

    const sexta = await confirmar(certo);
    expect(sexta.status).toBe(410);
    expect(await sexta.json()).toEqual({ erro: "codigo_expirado" });
  });

  it("codigoMortoNaoRessuscitaUmAnterior", async () => {
    // O pedido mais recente é que vale: ao morrer, o anterior não volta a servir.
    await pedirCodigo();
    const primeiro = ultimoCodigo();
    await pedirCodigo();
    const segundo = ultimoCodigo();
    for (let n = 0; n < 5; n++) await confirmar(outroCodigo(segundo));
    expect((await confirmar(primeiro)).status).toBe(410);
    expect((await confirmar(segundo)).status).toBe(410);
  });

  it("codigoExpiradoAos10Min", async () => {
    await pedirCodigo();
    const codigo = ultimoCodigo();
    await envelhecerCodigos(10 * MINUTO + 1000);
    const resposta = await confirmar(codigo);
    expect(resposta.status).toBe(410);
    expect(await resposta.json()).toEqual({ erro: "codigo_expirado" });
    expect((await confirmar(outroCodigo(codigo))).status).toBe(410);
  });

  it("codigoAindaServeAos9Min", async () => {
    await pedirCodigo();
    const codigo = ultimoCodigo();
    await envelhecerCodigos(9 * MINUTO);
    expect((await confirmar(codigo)).status).toBe(200);
  });

  it("semPedidoDa410", async () => {
    const resposta = await confirmar("123456");
    expect(resposta.status).toBe(410);
    expect(await resposta.json()).toEqual({ erro: "codigo_expirado" });
  });

  it("quartoPedidoNaHoraDa429", async () => {
    for (let n = 0; n < 3; n++) expect((await pedirCodigo()).status).toBe(204);
    const quarto = await pedirCodigo();
    expect(quarto.status).toBe(429);
    expect(await quarto.json()).toEqual({ erro: "muitos_pedidos" });
    expect(mensagensEnviadas).toHaveLength(3);

    // É por email: outro endereço não é afetado.
    expect((await pedirCodigo("bia@exemplo.pt")).status).toBe(204);
    // E passada a hora volta a poder pedir.
    await envelhecerCodigos(61 * MINUTO);
    expect((await pedirCodigo()).status).toBe(204);
  });

  it("tetoDiarioDa429", async () => {
    const hoje = new Date().toISOString().slice(0, 10);
    const teto = config.emails.codigosPorDia;
    await env.DB.prepare("INSERT INTO envios_email (dia, quantos) VALUES (?, ?)").bind(hoje, teto - 1).run();

    expect((await pedirCodigo()).status).toBe(204);
    const seguinte = await pedirCodigo("bia@exemplo.pt");
    expect(seguinte.status).toBe(429);
    expect(await seguinte.json()).toEqual({ erro: "muitos_pedidos" });
    expect(mensagensEnviadas).toHaveLength(1);

    // O pedido recusado não deixa código nem gasta o teto; o contador fica no teto.
    const { n } = (await env.DB.prepare("SELECT COUNT(*) AS n FROM codigos WHERE email = 'bia@exemplo.pt'").first<{ n: number }>())!;
    expect(n).toBe(0);
    const dia = await env.DB.prepare("SELECT quantos FROM envios_email WHERE dia = ?").bind(hoje).first<{ quantos: number }>();
    expect(dia?.quantos).toBe(teto);
  });

  it("pedidoRecusadoPeloEmailNaoGastaOTetoDiario", async () => {
    for (let n = 0; n < 3; n++) await pedirCodigo();
    expect((await pedirCodigo()).status).toBe(429);
    const hoje = new Date().toISOString().slice(0, 10);
    const dia = await env.DB.prepare("SELECT quantos FROM envios_email WHERE dia = ?").bind(hoje).first<{ quantos: number }>();
    expect(dia?.quantos).toBe(3);
  });

  it("emailInvalidoDa400", async () => {
    for (const email of ["", "   ", "sem-arroba", "a@b", "a@b.", "@b.pt", "a b@c.pt", "a@b@c.pt", "a@b.pt,c@d.pt", "Ana <a@b.pt>", 42, null]) {
      const resposta = await pedirCodigo(email);
      expect(resposta.status, String(email)).toBe(400);
      expect(await resposta.json()).toEqual({ erro: "email_invalido" });
    }
    // Sem o campo (o `undefined` de pedirCodigo() cairia no valor por omissão) e com JSON estragado.
    expect((await pedir("/api/auth/email/pedir", { corpo: {} })).status).toBe(400);
    expect((await pedir("/api/auth/email/pedir", { corpo: "{isto não é json" })).status).toBe(400);
    expect(await confirmar("123456", "sem-arroba").then((r) => r.status)).toBe(400);
    expect(mensagensEnviadas).toHaveLength(0);
  });

  it("soAceitaPost", async () => {
    const resposta = await pedir("/api/auth/email/pedir", { metodo: "GET" });
    expect(resposta.status).toBe(404);
    expect(mensagensEnviadas).toHaveLength(0);
  });
});

describe("carteiro Resend", () => {
  it("resendUsaAChaveEOEndereco", async () => {
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response('{"id":"x"}', { status: 200 }));
    const resposta = await pedir("/api/auth/email/pedir", { corpo: { email: EMAIL }, segredos: { RESEND_API_KEY: "chave-de-teste" } });
    expect(resposta.status).toBe(204);
    expect(mensagensEnviadas).toHaveLength(0);

    expect(fetchFalso).toHaveBeenCalledTimes(1);
    const [url, init] = fetchFalso.mock.calls[0];
    expect(String(url)).toBe("https://api.resend.com/emails");
    expect(init?.method).toBe("POST");
    expect(new Headers(init?.headers).get("authorization")).toBe("Bearer chave-de-teste");
    const corpo = JSON.parse(String(init?.body));
    expect(corpo.from).toBe("Paragem Certa <codigo@mail.pixellusitano.com>");
    expect(corpo.to).toEqual([EMAIL]);
    expect(corpo.subject).toBe("O teu código da Paragem Certa");
    expect(corpo.text).toMatch(/\b\d{6}\b/);
    expect(corpo.text).toContain("Válido durante 10 minutos.");
  });

  it("resendFalhaDa502", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response('{"message":"erro"}', { status: 500 }));
    const resposta = await pedir("/api/auth/email/pedir", { corpo: { email: EMAIL }, segredos: { RESEND_API_KEY: "chave-de-teste" } });
    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ erro: "envio_falhou" });
  });
});
