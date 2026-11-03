import config from "../public/dados/config.json";

export interface Carteiro {
  enviar(para: string, assunto: string, texto: string): Promise<void>;
}

export interface Mensagem {
  para: string;
  assunto: string;
  texto: string;
}

/**
 * O que o carteiro de registo "enviou" (testes e `wrangler dev`, sem RESEND_API_KEY).
 * Fica só em memória e nunca vai para a consola: o texto traz o código de entrada.
 */
export const mensagensEnviadas: Mensagem[] = [];

const carteiroDeRegisto: Carteiro = {
  async enviar(para, assunto, texto) {
    mensagensEnviadas.push({ para, assunto, texto });
  },
};

export const REMETENTE_CODIGO = "Paragem Certa <codigo@mail.pixellusitano.com>";
export const REMETENTE_AVISOS = "Paragem Certa <avisos@mail.pixellusitano.com>";

function carteiroResend(chave: string, remetente: string): Carteiro {
  return {
    async enviar(para, assunto, texto) {
      const resposta = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: { authorization: `Bearer ${chave}`, "content-type": "application/json" },
        body: JSON.stringify({ from: remetente, to: [para], subject: assunto, text: texto }),
      });
      // Só o estado vai no erro: o pedido e a resposta trazem o email e o código.
      if (!resposta.ok) throw new Error(`o Resend respondeu ${resposta.status}`);
    },
  };
}

export function carteiroDe(env: Env, remetente = REMETENTE_CODIGO): Carteiro {
  return env.RESEND_API_KEY ? carteiroResend(env.RESEND_API_KEY, remetente) : carteiroDeRegisto;
}

/**
 * Envia um email que não é um código de entrada se couber no teto do dia (`emails.outrosPorDia`, à parte dos códigos).
 * `false` se o teto está cheio ou o Resend falhou. Também o "Enviar agora" do pc-admin passa por aqui.
 * shortcut: um envio que falha gasta o seu lugar do dia; devolvê-lo se o Resend cair muitas vezes.
 */
export async function enviarNoTeto(env: Env, para: string, assunto: string, texto: string, agora = Date.now()): Promise<boolean> {
  const dia = new Date(agora).toISOString().slice(0, 10);
  // Contar e reservar o lugar num só passo, para envios em paralelo não passarem do teto.
  const lugar = await env.DB.prepare(
    "INSERT INTO contadores (dia, nome, quantos) VALUES (?1, 'emails', 1) ON CONFLICT (dia, nome) DO UPDATE SET quantos = quantos + 1 WHERE quantos < ?2",
  )
    .bind(dia, config.emails.outrosPorDia)
    .run();
  if (lugar.meta.changes === 0) return false;
  try {
    await carteiroDe(env, REMETENTE_AVISOS).enviar(para, assunto, texto);
    return true;
  } catch {
    // Sem registo: o erro só traz o estado do Resend.
    return false;
  }
}

/**
 * Envia um email que não é um código de entrada (recibos, explicações, avisos ao autor) sem o perder: se não couber no
 * teto do dia ou o Resend falhar, fica em `emails_por_enviar`, que a página de revisão mostra com "Enviar agora".
 * O destinatário vem sempre de quem chama, nunca do texto de um pedido.
 */
export async function enviarOuGuardar(env: Env, para: string, assunto: string, texto: string, agora = Date.now()): Promise<"enviado" | "guardado"> {
  if (await enviarNoTeto(env, para, assunto, texto, agora)) return "enviado";
  await env.DB.prepare("INSERT INTO emails_por_enviar (id, para, assunto, texto, criado_em) VALUES (?, ?, ?, ?, ?)")
    .bind(crypto.randomUUID(), para, assunto, texto, agora)
    .run();
  return "guardado";
}
