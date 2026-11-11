import { fazerAcao } from "./acoes";
import { verificarAcesso } from "./acesso";
import { paginaDeRevisao, type Aviso } from "./paginas";

// Em todas as respostas, a 403 incluída: nada de scripts, nada de molduras noutros sítios, nada guardado em cache.
const CABECALHOS = {
  "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'",
  "x-content-type-options": "nosniff",
  "cache-control": "no-store",
};
const TEXTO = "text/plain; charset=utf-8";

function resposta(corpo: BodyInit | null, estado: number, tipo: string): Response {
  return new Response(corpo, { status: estado, headers: { ...CABECALHOS, "content-type": tipo } });
}

/** Depois de uma ação, de volta à página (303: o recarregar não repete o POST), com os avisos como chaves fixas. */
function voltar(avisos: Aviso[]): Response {
  const busca = [...new Set(avisos)].map((aviso) => `aviso=${aviso}`).join("&");
  return new Response(null, { status: 303, headers: { ...CABECALHOS, location: busca ? `/?${busca}` : "/" } });
}

async function responder(pedido: Request, env: Env): Promise<Response> {
  // Antes de tudo: sem o token do Access do autor não se vê nem se faz nada.
  if (!(await verificarAcesso(pedido, env))) return resposta("Sem acesso", 403, TEXTO);
  const url = new URL(pedido.url);
  // O Access deixa passar qualquer pedido do browser do autor, também um formulário de outro sítio: as escritas só
  // da própria página.
  if (pedido.method !== "GET" && pedido.headers.get("origin") !== url.origin) return resposta("Sem acesso", 403, TEXTO);

  if (pedido.method === "GET") {
    if (url.pathname === "/") return resposta(await paginaDeRevisao(env, url.searchParams.getAll("aviso")), 200, "text/html; charset=utf-8");
  }

  if (pedido.method === "POST") {
    const desfecho = await fazerAcao(pedido, env, url.pathname);
    if (desfecho === 400) return resposta("Formulário inválido: falta o porquê (até 500 caracteres), a regra, a decisão ou um email válido.", 400, TEXTO);
    if (desfecho === 404) return resposta("Não existe ou já foi decidido", 404, TEXTO);
    if (desfecho) return voltar(desfecho);
  }
  return resposta("Não existe", 404, TEXTO);
}

export default {
  async fetch(pedido, env): Promise<Response> {
    try {
      return await responder(pedido, env);
    } catch (falha) {
      // Também um erro sai com os cabeçalhos de cima.
      console.error(falha);
      return resposta("Erro no servidor", 500, TEXTO);
    }
  },
} satisfies ExportedHandler<Env>;
