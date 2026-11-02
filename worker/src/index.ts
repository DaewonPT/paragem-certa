import { paginaPrivacidade } from "./privacidade";
import { erro, json } from "./respostas";

export default {
  async fetch(pedido, env): Promise<Response> {
    const { pathname } = new URL(pedido.url);

    if (pathname === "/api/saude" && pedido.method === "GET") return json({ ok: true });

    if (pathname === "/privacidade" && (pedido.method === "GET" || pedido.method === "HEAD")) {
      const responsavel = env.RESPONSAVEL?.trim();
      // Sem o nome do responsável a política estaria incompleta (RGPD, art. 13): é melhor não a mostrar.
      if (!responsavel) return erro("indisponivel", 503);
      return new Response(paginaPrivacidade(responsavel), {
        headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=3600" },
      });
    }

    return erro("nao_existe", 404);
  },
} satisfies ExportedHandler<Env>;
