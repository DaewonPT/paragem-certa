import { confirmarCodigo, limparExpirados, pedirCodigo } from "./codigos";
import { entrarComGoogle, novoNonce } from "./google";
import { paginaPrivacidade } from "./privacidade";
import { erro, json } from "./respostas";
import { sair } from "./sessoes";

export default {
  async fetch(pedido, env): Promise<Response> {
    const { pathname } = new URL(pedido.url);

    if (pathname === "/api/saude" && pedido.method === "GET") return json({ ok: true });

    if (pedido.method === "POST") {
      if (pathname === "/api/auth/email/pedir") return pedirCodigo(pedido, env);
      if (pathname === "/api/auth/email/confirmar") return confirmarCodigo(pedido, env);
      if (pathname === "/api/auth/sair") return sair(pedido, env);
      if (pathname === "/api/auth/google/nonce") return novoNonce(env);
      if (pathname === "/api/auth/google") return entrarComGoogle(pedido, env);
    }

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

  // Cron Trigger diário (corre na Cloudflare, não no telemóvel).
  async scheduled(_controlo, env, ctx): Promise<void> {
    ctx.waitUntil(limparExpirados(env));
  },
} satisfies ExportedHandler<Env>;
