import { carrisAoVivo } from "./carris";
import { lerAvisos, lerComunidade } from "./comunidade";
import { aceitarTermos, apagarConta } from "./conta";
import { criarCorrecao } from "./correcoes";
import { denunciar } from "./denuncias";
import { sincronizar } from "./favoritos";
import { entrarComGoogle, limparNoncesExpirados, novoNonce } from "./google";
import { FonteFalhou } from "./fontes";
import { aplicarPrazos } from "./prazos";
import { paginaPrivacidade } from "./privacidade";
import { criarRelato, votar } from "./relatos";
import { erro, json } from "./respostas";
import { resumoAoAutor } from "./resumo";
import { apagarSessoesExpiradas, sair } from "./sessoes";

export default {
  async fetch(pedido, env, ctx): Promise<Response> {
    try {
      const { pathname } = new URL(pedido.url);

      if (pathname === "/api/saude" && pedido.method === "GET") return json({ ok: true });

      // As apps até à 0.5.3 ainda pedem a entrada por código: só o 404 lhes mostra o texto certo, nunca o 429 do travão.
      if (pathname.startsWith("/api/auth/email/")) return erro("nao_existe", 404);

      // Cada entrada escreve na base (um nonce, uma sessão): sem isto, um só IP podia gastar a quota diária de escritas.
      if (pathname.startsWith("/api/auth/")) {
        const { success } = await env.LIMITE_AUTH.limit({ key: pedido.headers.get("cf-connecting-ip") ?? "sem-ip" });
        if (!success) return erro("espera_um_pouco", 429);
      }

      if (pedido.method === "POST") {
        if (pathname === "/api/auth/sair") return await sair(pedido, env);
        if (pathname === "/api/auth/google/nonce") return await novoNonce(env);
        if (pathname === "/api/auth/google") return await entrarComGoogle(pedido, env);
        if (pathname === "/api/sincronizar") return await sincronizar(pedido, env);
        if (pathname === "/api/termos") return await aceitarTermos(pedido, env);
        if (pathname === "/api/relatos") return await criarRelato(pedido, env);
        if (pathname === "/api/votos") return await votar(pedido, env);
        if (pathname === "/api/correcoes") return await criarCorrecao(pedido, env);
        if (pathname === "/api/denuncias") return await denunciar(pedido, env);
      }

      if (pedido.method === "GET") {
        if (pathname === "/api/comunidade") return await lerComunidade(pedido, env);
        if (pathname === "/api/avisos") return await lerAvisos(pedido, env);
        if (pathname === "/api/rt/carris") return await carrisAoVivo(env, ctx);
      }

      if (pathname === "/api/conta" && pedido.method === "DELETE") return await apagarConta(pedido, env);

      if (pathname === "/privacidade" && (pedido.method === "GET" || pedido.method === "HEAD")) {
        const responsavel = env.RESPONSAVEL?.trim();
        // Sem o nome do responsável a política estaria incompleta (RGPD, art. 13): é melhor não a mostrar.
        if (!responsavel) return erro("indisponivel", 503);
        return new Response(paginaPrivacidade(responsavel), {
          headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=3600" },
        });
      }

      return erro("nao_existe", 404);
    } catch (falha) {
      // Uma fonte externa em baixo nunca é uma pausa: a app volta ao horário de sempre.
      if (falha instanceof FonteFalhou) return erro("fonte_falhou", 502);
      // O D1 grátis passou o limite do dia: a app para de pedir até à meia-noite UTC. Os outros erros sobem como antes.
      if (String(falha).includes("exceeded D1's free tier daily")) return erro("em_pausa", 503);
      throw falha;
    }
  },

  // Cron Trigger diário (corre na Cloudflare, não no telemóvel): limpa os nonces e as sessões expirados, aplica os prazos e
  // manda o resumo ao autor. A hora vem do controlador, para os testes a poderem escolher.
  async scheduled(controlo, env, ctx): Promise<void> {
    const agora = controlo.scheduledTime;
    ctx.waitUntil(
      Promise.all([limparNoncesExpirados(env, agora), apagarSessoesExpiradas(env, agora), aplicarPrazos(env, agora), resumoAoAutor(env, agora)]),
    );
  },
} satisfies ExportedHandler<Env>;
