import { createLocalJWKSet, createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";
import { corpoJson, erro, json } from "./respostas";
import { aleatorioBase64Url, criarSessao } from "./sessoes";

declare global {
  interface Env {
    /** Só nos testes; nunca no wrangler.jsonc nem nos segredos. "teste" deixa usar TESTE_GOOGLE_JWKS. */
    AMBIENTE?: string;
    /** Só nos testes: as chaves públicas (JWKS em JSON) que substituem as da Google. */
    TESTE_GOOGLE_JWKS?: string;
  }
}

/** Um nonce vale 10 minutos. */
export const VALIDADE = 10 * 60_000;
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

/** A conta desta pessoa do Google; na primeira vez junta-a a uma conta antiga sem Google do mesmo email, se puder, ou cria uma. */
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

/** Nonces por dia UTC, na app toda: cada um custa 3 escritas a criar e 3 a apagar. */
export const MAX_NONCES_POR_DIA = 1000;

/** POST /api/auth/google/nonce: o pedido único que a app entrega à Google e que volta dentro do token. */
export async function novoNonce(env: Env): Promise<Response> {
  const agora = Date.now();
  // Contar e reservar num só passo, como em enviarNoTeto: com o teto cheio não se escreve nada.
  // Limite: quem gastar o teto de propósito deixa as entradas novas em pausa até às 00:00 UTC (as sessões abertas continuam);
  // se acontecer, passar a nonces assinados (HMAC), que só escrevem depois de um token válido da Google.
  const contado = await env.DB.prepare(
    "INSERT INTO contadores (dia, nome, quantos) VALUES (?1, 'nonces', 1) ON CONFLICT (dia, nome) DO UPDATE SET quantos = quantos + 1 WHERE quantos < ?2",
  )
    .bind(new Date(agora).toISOString().slice(0, 10), MAX_NONCES_POR_DIA)
    .run();
  // em_pausa: as apps publicadas (desde a 0.4.0) mostram "em pausa até <hora>" sem abrir o Google e não param o resto.
  if (contado.meta.changes === 0) return erro("em_pausa", 503);
  const nonce = aleatorioBase64Url(32);
  await env.DB.prepare("INSERT INTO nonces (valor, criado_em) VALUES (?, ?)").bind(nonce, agora).run();
  return json({ nonce });
}

/** Para o Cron diário: os nonces que nunca chegaram a ser gastos. */
export async function limparNoncesExpirados(env: Env, agora: number): Promise<void> {
  await env.DB.prepare("DELETE FROM nonces WHERE criado_em < ?").bind(agora - VALIDADE).run();
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
