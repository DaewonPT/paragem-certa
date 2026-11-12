/** Como o servidor se apresenta à Carris e ao Metro: quem somos e como nos contactam. */
export const UA = "ParagemCerta/0.4.0 (contact@pixellusitano.com; https://pixellusitano.com)";

/**
 * Uma fonte externa em baixo, lenta ou com um formato que não conhecemos. O index.ts responde 502 `fonte_falhou`
 * (nunca `em_pausa`: a app volta ao horário de sempre).
 */
export class FonteFalhou extends Error {}

/** O maior corpo que aceitamos de uma fonte (o feed da Carris anda pelas dezenas de KB). */
const MAXIMO = 2 * 1024 * 1024;

/**
 * Pede a uma fonte externa com o nosso User-Agent e no máximo 8 s; uma resposta que não seja 2xx, com mais de 2 MB ou
 * um erro de rede dão FonteFalhou. O corpo já vem lido (até aos 2 MB), por isso ler a resposta devolvida não falha.
 */
export async function pedirFonte(url: string, init?: RequestInit): Promise<Response> {
  const cabecalhos = new Headers(init?.headers);
  cabecalhos.set("user-agent", UA);
  let resposta: Response;
  try {
    resposta = await fetch(url, { ...init, headers: cabecalhos, signal: AbortSignal.timeout(8000) });
  } catch (falha) {
    throw new FonteFalhou(`a fonte não respondeu: ${falha}`);
  }
  const grandeDemais = Number(resposta.headers.get("content-length")) > MAXIMO;
  if (!resposta.ok || grandeDemais) {
    await resposta.body?.cancel();
    throw new FonteFalhou(grandeDemais ? "a fonte anuncia mais de 2 MB" : `a fonte respondeu ${resposta.status}`);
  }
  const partes: Uint8Array[] = [];
  let total = 0;
  try {
    const leitor = resposta.body?.getReader();
    for (let parte = await leitor?.read(); parte && !parte.done; parte = await leitor?.read()) {
      total += parte.value.byteLength;
      if (total > MAXIMO) {
        await leitor?.cancel();
        throw new FonteFalhou("a fonte mandou mais de 2 MB");
      }
      partes.push(parte.value);
    }
  } catch (falha) {
    throw falha instanceof FonteFalhou ? falha : new FonteFalhou(`a fonte parou a meio: ${falha}`);
  }
  const corpo = new Uint8Array(total);
  let pos = 0;
  for (const parte of partes) {
    corpo.set(parte, pos);
    pos += parte.byteLength;
  }
  // O corpo já vem descomprimido e com outro tamanho: estes dois cabeçalhos deixavam de bater certo.
  const cabecalhosLidos = new Headers(resposta.headers);
  cabecalhosLidos.delete("content-length");
  cabecalhosLidos.delete("content-encoding");
  return new Response(corpo, { status: resposta.status, headers: cabecalhosLidos });
}

/**
 * Devolve o que `obter()` deu há menos de `segundos`, guardado em `caches.open("fontes")`; senão chama-o e guarda o
 * resultado. A frescura decide-se pelo `x-obtido-em` (assim os testes com o Date falso funcionam); o `max-age` é só
 * para a cache aceitar a cópia. Uma FonteFalhou de `obter()` também fica guardada `segundos` (dá a mesma FonteFalhou sem
 * voltar à fonte); os outros erros sobem sem guardar nada. A cache em baixo nunca parte o pedido: sem ler, pede-se à
 * fonte; sem guardar, devolve-se o valor e o próximo pedido volta à fonte.
 */
export async function comCache<T>(ctx: ExecutionContext, chave: string, segundos: number, obter: () => Promise<T>): Promise<T> {
  // Só uma chave, no endereço do próprio Worker; nunca é servida.
  const endereco = `https://paragemcerta.pixellusitano.com/__fontes/${encodeURIComponent(chave)}`;
  let cache: Cache | undefined;
  let falhouHaPouco = false;
  try {
    cache = await caches.open("fontes");
    const guardada = await cache.match(endereco);
    if (guardada) {
      const idade = Date.now() - Number(guardada.headers.get("x-obtido-em"));
      const fresca = idade >= 0 && idade < segundos * 1000;
      if (fresca && !guardada.headers.has("x-falhou")) return (await guardada.json()) as T;
      falhouHaPouco = fresca;
      await guardada.body?.cancel();
    }
  } catch {
    // A cache não leu: segue para a fonte.
  }
  // Fora do try: lá dentro, o catch de "a cache não leu" engolia-a. Sem isto, com a fonte em baixo cada pedido ia lá e esperava até 8 s.
  if (falhouHaPouco) throw new FonteFalhou(`a fonte falhou há menos de ${segundos} s`);
  const guardar = (corpo: string, falhou: boolean): void => {
    if (!cache) return;
    const cabecalhos = new Headers({ "content-type": "application/json", "cache-control": `max-age=${segundos}`, "x-obtido-em": String(Date.now()) });
    if (falhou) cabecalhos.set("x-falhou", "1");
    try {
      ctx.waitUntil(cache.put(endereco, new Response(corpo, { headers: cabecalhos })).catch(() => undefined));
    } catch {
      // A cache não guardou: o próximo pedido volta à fonte.
    }
  };
  const valor = await obter().catch((falha: unknown) => {
    // Corpo vazio: um Worker antigo reposto lê-o como cache ilegível (o json() falha dentro do try dele) e vai à fonte.
    if (falha instanceof FonteFalhou) guardar("", true);
    throw falha;
  });
  guardar(JSON.stringify(valor), false);
  return valor;
}
