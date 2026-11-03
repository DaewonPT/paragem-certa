/** No plano grátis a Cloudflare aceita até 100 endereços por pedido de purge. */
const POR_PEDIDO = 100;

/**
 * Pede à Cloudflare para esquecer estes endereços (as fotos aprovadas apagadas), em grupos de 100. Sem CF_PURGE_TOKEN
 * (testes e `wrangler dev`) ou sem endereços não pede nada. `false` se algum grupo falhou: a cópia em cache acaba
 * sozinha em 24 h (`max-age=86400`), por isso quem chama não pára por isto.
 */
export async function limparCache(env: Env, urls: string[]): Promise<boolean> {
  if (!env.CF_PURGE_TOKEN || urls.length === 0) return true;
  let tudo = true;
  for (let desde = 0; desde < urls.length; desde += POR_PEDIDO) {
    try {
      const resposta = await fetch(`https://api.cloudflare.com/client/v4/zones/${encodeURIComponent(env.CF_ZONA)}/purge_cache`, {
        method: "POST",
        headers: { authorization: `Bearer ${env.CF_PURGE_TOKEN}`, "content-type": "application/json" },
        body: JSON.stringify({ files: urls.slice(desde, desde + POR_PEDIDO) }),
      });
      if (!resposta.ok) tudo = false;
    } catch {
      tudo = false;
    }
  }
  return tudo;
}
