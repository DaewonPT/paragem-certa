/** Como o servidor se apresenta à Carris e ao Metro: quem somos e como nos contactam. */
export const UA = "ParagemCerta/0.4.0 (contact@pixellusitano.com; https://pixellusitano.com)";

/**
 * Uma fonte externa em baixo, lenta ou com um formato que não conhecemos. O index.ts responde 502 `fonte_falhou`
 * (nunca `em_pausa`: a app volta ao horário de sempre).
 */
export class FonteFalhou extends Error {}

/** Pede a uma fonte externa com o nosso User-Agent e no máximo 8 s; uma resposta que não seja 2xx ou um erro de rede dão FonteFalhou. */
export async function pedirFonte(url: string, init?: RequestInit): Promise<Response> {
  const cabecalhos = new Headers(init?.headers);
  cabecalhos.set("user-agent", UA);
  let resposta: Response;
  try {
    resposta = await fetch(url, { ...init, headers: cabecalhos, signal: AbortSignal.timeout(8000) });
  } catch (falha) {
    throw new FonteFalhou(`a fonte não respondeu: ${falha}`);
  }
  if (!resposta.ok) {
    await resposta.body?.cancel();
    throw new FonteFalhou(`a fonte respondeu ${resposta.status}`);
  }
  return resposta;
}

/**
 * Devolve o que `obter()` deu há menos de `segundos`, guardado em `caches.open("fontes")`; senão chama-o e guarda o
 * resultado. A frescura decide-se pelo `x-obtido-em` (assim os testes com o Date falso funcionam); o `max-age` é só
 * para a cache aceitar a cópia. Um erro de `obter()` não guarda nada e sobe.
 */
export async function comCache<T>(ctx: ExecutionContext, chave: string, segundos: number, obter: () => Promise<T>): Promise<T> {
  const cache = await caches.open("fontes");
  const endereco = `https://fontes.paragem-certa.invalid/${encodeURIComponent(chave)}`;
  const guardada = await cache.match(endereco);
  if (guardada) {
    const idade = Date.now() - Number(guardada.headers.get("x-obtido-em"));
    if (idade >= 0 && idade < segundos * 1000) return (await guardada.json()) as T;
    await guardada.body?.cancel();
  }
  const valor = await obter();
  const copia = new Response(JSON.stringify(valor), {
    headers: { "content-type": "application/json", "cache-control": `max-age=${segundos}`, "x-obtido-em": String(Date.now()) },
  });
  ctx.waitUntil(cache.put(endereco, copia));
  return valor;
}
