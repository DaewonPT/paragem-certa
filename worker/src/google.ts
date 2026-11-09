import { createLocalJWKSet, createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";
import { corpoJson, VALIDADE } from "./codigos";
import { erro, json } from "./respostas";
import { aleatorioBase64Url, criarSessao } from "./sessoes";

declare global {
  interface Env {
    /** Só nos testes; nunca no wrangler.jsonc nem nos segredos. "teste" deixa usar TESTE_GOOGLE_JWKS. */
    AMBIENTE?: string;
    /** Só nos testes: as chaves públicas (JWKS em JSON) que substituem as da Google. */
    TESTE_GOOGLE_JWKS?: string;
  }
}

const EMISSORES = ["accounts.google.com", "https://accounts.google.com"];
/** Os tokens da Google têm 1 a 2 KB; acima disto nem se tenta ler. */
const TAMANHO_MAXIMO = 8192;

// Um por isolate: o jose guarda as chaves em cache e só as volta a pedir quando aparece um `kid` novo.
const chavesDaGoogle = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

/**
 * As chaves de teste só valem com AMBIENTE "teste" e sem RESEND_API_KEY. O servidor publicado tem sempre a chave
 * do Resend (o wrangler.jsonc exige-a), por isso um AMBIENTE "teste" posto lá por engano não chega para as ligar.
 */
function chavesPublicas(env: Env): JWTVerifyGetKey {
  if (env.AMBIENTE === "teste" && !env.RESEND_API_KEY && env.TESTE_GOOGLE_JWKS) {
    return createLocalJWKSet(JSON.parse(env.TESTE_GOOGLE_JWKS));
  }
  return chavesDaGoogle;
}

/**
 * Confirma um token da Google (assinatura RS256, `iss`, `aud`, `exp`, `email_verified`) e gasta o nonce que ele traz,
 * que tem de ter sido dado por este servidor há menos de 10 min. Lança um erro se alguma coisa falhar.
 */
export async function verificarTokenGoogle(idToken: string, env: Env): Promise<{ sub: string; email: string; hd?: string }> {
  const cliente = env.GOOGLE_WEB_CLIENT_ID?.trim();
  if (!cliente) throw new Error("falta o GOOGLE_WEB_CLIENT_ID");
  const { payload } = await jwtVerify(idToken, chavesPublicas(env), {
    algorithms: ["RS256"],
    issuer: EMISSORES,
    audience: cliente,
    requiredClaims: ["exp"],
  });
  const { sub, email, email_verified, nonce, hd } = payload;
  if (typeof sub !== "string" || !sub || typeof email !== "string" || !email || email_verified !== true || typeof nonce !== "string") {
    throw new Error("faltam campos ao token");
  }
  // Apagar o nonce é o que o faz servir uma vez só, mesmo com dois pedidos ao mesmo tempo.
  const gasto = await env.DB.prepare("DELETE FROM nonces WHERE valor = ? AND criado_em > ? RETURNING valor")
    .bind(nonce, Date.now() - VALIDADE)
    .first();
  if (!gasto) throw new Error("nonce desconhecido, expirado ou já usado");
  return { sub, email: email.trim().toLowerCase(), ...(typeof hd === "string" && hd ? { hd } : {}) };
}

/** Gmail ou Google Workspace (`hd`): é a Google que responde pelo endereço. */
function googleRespondePeloEmail({ email, hd }: { email: string; hd?: string }): boolean {
  return email.endsWith("@gmail.com") || email.endsWith("@googlemail.com") || hd !== undefined;
}

/** A conta desta pessoa do Google; na primeira vez junta-a à conta de código do mesmo email, se puder, ou cria uma. */
async function contaDoGoogle(env: Env, pessoa: { sub: string; email: string; hd?: string }): Promise<string> {
  const existente = await env.DB.prepare("SELECT id FROM contas WHERE google_sub = ?").bind(pessoa.sub).first<{ id: string }>();
  if (existente) return existente.id;

  const passos: D1PreparedStatement[] = [];
  if (googleRespondePeloEmail(pessoa)) {
    // Só uma conta ainda sem Google: uma ligada a outro `sub` é de outra pessoa (endereços do Workspace reaproveitados).
    passos.push(
      env.DB.prepare(
        `UPDATE OR IGNORE contas SET google_sub = ?1
         WHERE id = (SELECT id FROM contas WHERE email = ?2 AND google_sub IS NULL ORDER BY criada_em, id LIMIT 1)`,
      ).bind(pessoa.sub, pessoa.email),
    );
  }
  // Se já se juntou (ou outro pedido igual chegou primeiro), o google_sub UNIQUE faz este INSERT não fazer nada.
  passos.push(
    env.DB.prepare("INSERT INTO contas (id, email, google_sub, criada_em) VALUES (?, ?, ?, ?) ON CONFLICT (google_sub) DO NOTHING").bind(
      crypto.randomUUID(),
      pessoa.email,
      pessoa.sub,
      Date.now(),
    ),
    env.DB.prepare("SELECT id FROM contas WHERE google_sub = ?").bind(pessoa.sub),
  );
  const resultados = await env.DB.batch<{ id: string }>(passos);
  return resultados.at(-1)!.results[0].id;
}

/** POST /api/auth/google/nonce: o pedido único que a app entrega à Google e que volta dentro do token. */
export async function novoNonce(env: Env): Promise<Response> {
  const nonce = aleatorioBase64Url(32);
  await env.DB.prepare("INSERT INTO nonces (valor, criado_em) VALUES (?, ?)").bind(nonce, Date.now()).run();
  return json({ nonce });
}

/** POST /api/auth/google */
export async function entrarComGoogle(pedido: Request, env: Env): Promise<Response> {
  const { idToken } = await corpoJson(pedido);
  // Qualquer falha na verificação é um token recusado; sem registo, porque o token é da pessoa.
  const pessoa =
    typeof idToken === "string" && idToken.length <= TAMANHO_MAXIMO ? await verificarTokenGoogle(idToken, env).catch(() => null) : null;
  if (!pessoa) return erro("token_invalido", 401);
  return json({ token: await criarSessao(env, await contaDoGoogle(env, pessoa)), email: pessoa.email });
}
