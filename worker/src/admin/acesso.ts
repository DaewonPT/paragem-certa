import { createLocalJWKSet, createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";

declare global {
  interface Env {
    /** O endereço da equipa do Zero Trust, https://<equipa>.cloudflareaccess.com (o `iss` dos tokens do Access). */
    ACCESS_EQUIPA?: string;
    /** A etiqueta de audiência (AUD) da aplicação do Access que protege o pc-admin. */
    ACCESS_AUD?: string;
    /** Só nos testes: as chaves públicas (JWKS em JSON) que substituem as do Access. */
    TESTE_ACCESS_JWKS?: string;
  }
}

/** Os tokens do Access têm 1 a 2 KB; acima disto nem se tenta ler. */
const TAMANHO_MAXIMO = 8192;

// Um por isolate e por equipa: o jose guarda as chaves em cache e só as volta a pedir quando aparece um `kid` novo.
let chavesDoAccess: { equipa: string; chaves: JWTVerifyGetKey } | undefined;

/**
 * As chaves de teste só valem com AMBIENTE "teste" e sem RESEND_API_KEY: o pc-admin publicado tem sempre a chave do
 * Resend (o wrangler.admin.jsonc exige-a), por isso um AMBIENTE "teste" posto lá por engano não chega para as ligar.
 */
function chavesPublicas(env: Env, equipa: string): JWTVerifyGetKey {
  if (env.AMBIENTE === "teste" && !env.RESEND_API_KEY && env.TESTE_ACCESS_JWKS) {
    return createLocalJWKSet(JSON.parse(env.TESTE_ACCESS_JWKS));
  }
  if (chavesDoAccess?.equipa !== equipa) {
    chavesDoAccess = { equipa, chaves: createRemoteJWKSet(new URL(`${equipa}/cdn-cgi/access/certs`)) };
  }
  return chavesDoAccess.chaves;
}

/**
 * Quem o Access deixou passar, se for o autor: só o cabeçalho Cf-Access-Jwt-Assertion (nunca o cookie
 * CF_Authorization, que um pedido feito noutro sítio pode trazer), assinado RS256 pelas chaves da equipa, com o `iss`
 * da equipa, a audiência do pc-admin, dentro da validade e com o email do autor. Fecha tudo (`null`) se faltar
 * ACCESS_EQUIPA, ACCESS_AUD ou EMAIL_AUTOR, ou se alguma coisa falhar.
 */
export async function verificarAcesso(pedido: Request, env: Env): Promise<{ email: string } | null> {
  const equipa = env.ACCESS_EQUIPA?.trim().replace(/\/+$/, "");
  const audiencia = env.ACCESS_AUD?.trim();
  const autor = env.EMAIL_AUTOR?.trim().toLowerCase();
  const token = pedido.headers.get("cf-access-jwt-assertion");
  if (!equipa?.startsWith("https://") || !audiencia || !autor || !token || token.length > TAMANHO_MAXIMO) return null;
  try {
    const { payload } = await jwtVerify(token, chavesPublicas(env, equipa), {
      algorithms: ["RS256"],
      issuer: equipa,
      audience: audiencia,
      requiredClaims: ["exp"],
    });
    const email = typeof payload.email === "string" ? payload.email.trim().toLowerCase() : "";
    return email === autor ? { email } : null;
  } catch {
    return null;
  }
}
