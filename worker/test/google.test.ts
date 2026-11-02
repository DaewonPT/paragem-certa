import { env } from "cloudflare:workers";
import { exportJWK, generateKeyPair, importJWK, SignJWT, UnsecuredJWT, type CryptoKey, type JWK } from "jose";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { contaDaSessao } from "../src/sessoes";
import { pedidoComToken, pedir } from "./ajudas";

const MINUTO = 60_000;
const CLIENTE = "cliente-de-teste.apps.googleusercontent.com";
const SUB = "110000000000000000001";
const CHAVES_DA_GOOGLE = "https://www.googleapis.com/oauth2/v3/certs";

/** A chave com que os testes assinam, como a Google assina os seus. */
let chave: CryptoKey;
/** A mesma chave RSA, mas para RS512: o JWKS de teste aceitá-la-ia se o servidor não exigisse RS256. */
let chaveRS512: CryptoKey;
/** Uma chave que não está no JWKS de teste (mas com o mesmo `kid`). */
let chaveDeFora: CryptoKey;
let jwksDeTeste: string;
let jwkDeFora: JWK;

/** Só o necessário para verificar; sem `alg`, de propósito, para só o `algorithms: ["RS256"]` do servidor recusar o RS512. */
async function jwkPublica(publica: CryptoKey): Promise<JWK> {
  const { kty, n, e } = await exportJWK(publica);
  return { kty, n, e, kid: "teste", use: "sig" };
}

beforeAll(async () => {
  const par = await generateKeyPair("RS256", { extractable: true });
  const deFora = await generateKeyPair("RS256");
  chave = par.privateKey;
  const { kty, n, e, d, p, q, dp, dq, qi } = await exportJWK(par.privateKey);
  chaveRS512 = (await importJWK({ kty, n, e, d, p, q, dp, dq, qi }, "RS512")) as CryptoKey;
  chaveDeFora = deFora.privateKey;
  jwksDeTeste = JSON.stringify({ keys: [await jwkPublica(par.publicKey)] });
  jwkDeFora = await jwkPublica(deFora.publicKey);
});

beforeEach(() => {
  // Nada de internet nos testes: se o servidor fosse buscar as chaves da Google, falhava aqui.
  vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("sem internet nos testes"));
});

afterEach(() => {
  vi.restoreAllMocks();
});

function segredosDeTeste(): Partial<Env> {
  return { AMBIENTE: "teste", TESTE_GOOGLE_JWKS: jwksDeTeste, GOOGLE_WEB_CLIENT_ID: CLIENTE };
}

async function novoNonce(): Promise<string> {
  const resposta = await pedir("/api/auth/google/nonce");
  expect(resposta.status).toBe(200);
  return ((await resposta.json()) as { nonce: string }).nonce;
}

/** Um token como os da Google, com um nonce novo do servidor; em `campos`, `undefined` tira o campo. */
async function tokenGoogle(campos: Record<string, unknown> = {}, assinatura: { chave?: CryptoKey | Uint8Array; alg?: string } = {}): Promise<string> {
  const agora = Math.floor(Date.now() / 1000);
  const todos: Record<string, unknown> = {
    iss: "https://accounts.google.com",
    aud: CLIENTE,
    sub: SUB,
    email: "ana@gmail.com",
    email_verified: true,
    nonce: await novoNonce(),
    iat: agora,
    exp: agora + 3600,
    ...campos,
  };
  const corpo = Object.fromEntries(Object.entries(todos).filter(([, valor]) => valor !== undefined));
  return new SignJWT(corpo).setProtectedHeader({ alg: assinatura.alg ?? "RS256", kid: "teste", typ: "JWT" }).sign(assinatura.chave ?? chave);
}

function entrar(idToken: unknown, segredos: Partial<Env> = segredosDeTeste()): Promise<Response> {
  return pedir("/api/auth/google", { corpo: { idToken }, segredos });
}

/** Entra e confirma que a sessão devolvida serve; devolve também a conta dessa sessão. */
async function entrou(idToken: string): Promise<{ token: string; email: string; contaId: string }> {
  const resposta = await entrar(idToken);
  expect(resposta.status).toBe(200);
  const corpo = (await resposta.json()) as { token: string; email: string };
  expect(corpo.token).toMatch(/^[A-Za-z0-9_-]{43}$/);
  const conta = await contaDaSessao(pedidoComToken(corpo.token), env);
  expect(conta).not.toBeNull();
  return { ...corpo, contaId: conta!.contaId };
}

async function estado(): Promise<{ sessoes: number; contas: number; ligadas: number }> {
  return (await env.DB.prepare(
    `SELECT (SELECT COUNT(*) FROM sessoes) AS sessoes, (SELECT COUNT(*) FROM contas) AS contas,
            (SELECT COUNT(*) FROM contas WHERE google_sub IS NOT NULL) AS ligadas`,
  ).first<{ sessoes: number; contas: number; ligadas: number }>())!;
}

/** Recusado: 401 token_invalido, e nem sessão, nem conta, nem ligação a uma conta do Google. */
async function esperarRecusa(idToken: unknown, segredos: Partial<Env> = segredosDeTeste()): Promise<void> {
  const antes = await estado();
  const resposta = await entrar(idToken, segredos);
  expect(resposta.status).toBe(401);
  expect(await resposta.json()).toEqual({ erro: "token_invalido" });
  expect(await estado()).toEqual(antes);
}

async function contaDeEmail(id: string, email: string, googleSub: string | null = null): Promise<void> {
  await env.DB.prepare("INSERT INTO contas (id, email, google_sub, criada_em) VALUES (?, ?, ?, 1)").bind(id, email, googleSub).run();
}

async function googleSubDe(id: string): Promise<string | null> {
  return (await env.DB.prepare("SELECT google_sub FROM contas WHERE id = ?").bind(id).first<{ google_sub: string | null }>())!.google_sub;
}

describe("entrar com o Google", () => {
  it("nonceTem32BytesEFicaGuardado", async () => {
    const nonce = await novoNonce();
    expect(nonce).toMatch(/^[A-Za-z0-9_-]{43}$/); // 32 bytes em base64url
    expect(await novoNonce()).not.toBe(nonce);
    const guardado = await env.DB.prepare("SELECT criado_em FROM nonces WHERE valor = ?").bind(nonce).first<{ criado_em: number }>();
    expect(guardado!.criado_em).toBeGreaterThan(Date.now() - MINUTO);
  });

  it("tokenValidoDaSessao", async () => {
    const { email, contaId } = await entrou(await tokenGoogle());
    expect(email).toBe("ana@gmail.com");
    const { results } = await env.DB.prepare("SELECT id, email, google_sub FROM contas").all();
    expect(results).toEqual([{ id: contaId, email: "ana@gmail.com", google_sub: SUB }]);
    // O nonce foi gasto.
    const { n } = (await env.DB.prepare("SELECT COUNT(*) AS n FROM nonces").first<{ n: number }>())!;
    expect(n).toBe(0);

    // Voltar a entrar (mesmo com outro email na Google) dá a mesma conta: a pessoa é o `sub`.
    const outraVez = await entrou(await tokenGoogle({ email: "ana.nova@gmail.com" }));
    expect(outraVez.contaId).toBe(contaId);
    expect(outraVez.email).toBe("ana.nova@gmail.com");
    expect((await estado()).contas).toBe(1);
  });

  it("aceitaOsDoisEmissoresDaGoogle", async () => {
    await entrou(await tokenGoogle({ iss: "accounts.google.com" }));
    await entrou(await tokenGoogle({ iss: "https://accounts.google.com" }));
  });

  it("audienciaErradaRecusa", async () => {
    await esperarRecusa(await tokenGoogle({ aud: "outro-cliente.apps.googleusercontent.com" }));
    await esperarRecusa(await tokenGoogle({ aud: ["outro-cliente.apps.googleusercontent.com"] }));
    await esperarRecusa(await tokenGoogle({ aud: undefined }));
  });

  it("semIdDoClienteConfiguradoRecusa", async () => {
    await esperarRecusa(await tokenGoogle({ aud: "" }), { ...segredosDeTeste(), GOOGLE_WEB_CLIENT_ID: "" });
    await esperarRecusa(await tokenGoogle(), { ...segredosDeTeste(), GOOGLE_WEB_CLIENT_ID: undefined });
  });

  it("emissorErradoRecusa", async () => {
    await esperarRecusa(await tokenGoogle({ iss: "https://accounts.google.com.exemplo.test" }));
    await esperarRecusa(await tokenGoogle({ iss: "http://accounts.google.com" }));
    await esperarRecusa(await tokenGoogle({ iss: undefined }));
  });

  it("expiradoRecusa", async () => {
    await esperarRecusa(await tokenGoogle({ exp: Math.floor(Date.now() / 1000) - 60 }));
    await esperarRecusa(await tokenGoogle({ exp: undefined }));
  });

  it("assinaturaErradaRecusa", async () => {
    await esperarRecusa(await tokenGoogle({}, { chave: chaveDeFora }));
    // Mudar o conteúdo de um token verdadeiro estraga a assinatura.
    const [cabecalho, , assinatura] = (await tokenGoogle()).split(".");
    const alterado = btoa(JSON.stringify({ iss: "https://accounts.google.com", aud: CLIENTE, sub: "outra-pessoa", email: "rui@gmail.com", email_verified: true, nonce: await novoNonce(), exp: Math.floor(Date.now() / 1000) + 3600 }))
      .replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
    await esperarRecusa(`${cabecalho}.${alterado}.${assinatura}`);
  });

  it("algoritmoErradoRecusa", async () => {
    await esperarRecusa(await tokenGoogle({}, { chave: chaveRS512, alg: "RS512" }));
    const segredo = crypto.getRandomValues(new Uint8Array(32));
    await esperarRecusa(await tokenGoogle({}, { chave: segredo, alg: "HS256" }));
    const semAssinatura = new UnsecuredJWT({ iss: "https://accounts.google.com", aud: CLIENTE, sub: SUB, email: "ana@gmail.com", email_verified: true, nonce: await novoNonce() })
      .setExpirationTime("1h")
      .encode();
    await esperarRecusa(semAssinatura);
  });

  it("tokenEstragadoRecusa", async () => {
    for (const idToken of [undefined, null, 123, "", "abc", "a.b.c", "a".repeat(10_000)]) await esperarRecusa(idToken);
    const resposta = await pedir("/api/auth/google", { corpo: "{nao é json", segredos: segredosDeTeste() });
    expect(resposta.status).toBe(401);
    expect(await resposta.json()).toEqual({ erro: "token_invalido" });
  });

  it("nonceDesconhecidoRecusa", async () => {
    await esperarRecusa(await tokenGoogle({ nonce: "nonce-inventado" }));
    await esperarRecusa(await tokenGoogle({ nonce: undefined }));
  });

  it("nonceComMaisDe10MinRecusa", async () => {
    const token = await tokenGoogle();
    await env.DB.prepare("UPDATE nonces SET criado_em = criado_em - ?").bind(10 * MINUTO + 1000).run();
    await esperarRecusa(token);
  });

  it("nonceSoServeUmaVez", async () => {
    const token = await tokenGoogle();
    await entrou(token);
    await esperarRecusa(token);
  });

  it("emailNaoVerificadoRecusa", async () => {
    await esperarRecusa(await tokenGoogle({ email_verified: false }));
    await esperarRecusa(await tokenGoogle({ email_verified: "true" }));
    await esperarRecusa(await tokenGoogle({ email_verified: undefined }));
  });

  it("semSubOuSemEmailRecusa", async () => {
    await esperarRecusa(await tokenGoogle({ sub: undefined }));
    await esperarRecusa(await tokenGoogle({ sub: "" }));
    await esperarRecusa(await tokenGoogle({ email: undefined }));
  });

  it("gmailJuntaComAContaDoEmail", async () => {
    await contaDeEmail("conta-gmail", "ana@gmail.com");
    await contaDeEmail("conta-googlemail", "rui@googlemail.com");
    await contaDeEmail("conta-workspace", "eva@empresa.pt");

    expect((await entrou(await tokenGoogle({ email: "Ana@Gmail.com" }))).contaId).toBe("conta-gmail");
    expect((await entrou(await tokenGoogle({ sub: "sub-rui", email: "rui@googlemail.com" }))).contaId).toBe("conta-googlemail");
    // Google Workspace: o token traz `hd`, e é a Google que responde pelo endereço.
    expect((await entrou(await tokenGoogle({ sub: "sub-eva", email: "eva@empresa.pt", hd: "empresa.pt" }))).contaId).toBe("conta-workspace");

    expect(await googleSubDe("conta-gmail")).toBe(SUB);
    expect(await googleSubDe("conta-googlemail")).toBe("sub-rui");
    expect(await googleSubDe("conta-workspace")).toBe("sub-eva");
    expect((await estado()).contas).toBe(3);
  });

  it("outroDominioSemHdNaoJunta", async () => {
    await contaDeEmail("conta-email", "rui@empresa.pt");
    const { contaId } = await entrou(await tokenGoogle({ email: "rui@empresa.pt" }));
    expect(contaId).not.toBe("conta-email");
    expect(await googleSubDe("conta-email")).toBeNull();
    expect(await googleSubDe(contaId)).toBe(SUB);
    expect((await estado()).contas).toBe(2);
  });

  it("contaJaLigadaAOutroGoogleNaoJunta", async () => {
    await contaDeEmail("conta-de-outro", "ana@gmail.com", "sub-de-outra-pessoa");
    const { contaId } = await entrou(await tokenGoogle());
    expect(contaId).not.toBe("conta-de-outro");
    expect(await googleSubDe("conta-de-outro")).toBe("sub-de-outra-pessoa");
  });

  it("chavesDeTesteForaDoAmbienteDeTesteSaoIgnoradas", async () => {
    // Fora dos testes, as chaves vêm sempre da Google (aqui, uma Google de mentira que não conhece a chave de teste).
    const pedidos: string[] = [];
    vi.mocked(fetch).mockImplementation(async (url) => {
      pedidos.push(String(url));
      return Response.json({ keys: [jwkDeFora] });
    });

    const semAmbiente = { ...segredosDeTeste(), AMBIENTE: undefined };
    const producao = { ...segredosDeTeste(), AMBIENTE: "producao" };
    const quaseTeste = { ...segredosDeTeste(), AMBIENTE: "Teste" };
    // Com a chave do Resend é um servidor a sério (o wrangler.jsonc exige-a ao publicar), mesmo com AMBIENTE "teste".
    const comResend = { ...segredosDeTeste(), RESEND_API_KEY: "re_chave_de_mentira" };
    for (const segredos of [semAmbiente, producao, quaseTeste, comResend]) await esperarRecusa(await tokenGoogle(), segredos);

    expect(pedidos.length).toBeGreaterThan(0);
    expect(new Set(pedidos)).toEqual(new Set([CHAVES_DA_GOOGLE]));
  });
});
