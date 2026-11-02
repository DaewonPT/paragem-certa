const CABECALHOS_JSON = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };

export function json(corpo: unknown, estado = 200): Response {
  return new Response(JSON.stringify(corpo), { status: estado, headers: CABECALHOS_JSON });
}

export function semConteudo(): Response {
  return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
}

/** Erro da API: `{"erro":"<codigo>"}`, com o código em minúsculas e sem acentos (a app compara-o). */
export function erro(codigo: string, estado: number): Response {
  return json({ erro: codigo }, estado);
}
