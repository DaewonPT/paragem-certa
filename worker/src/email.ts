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
 * Fica só em memória e nunca vai para a consola: o texto traz dados de quem o recebe.
 */
export const mensagensEnviadas: Mensagem[] = [];

const carteiroDeRegisto: Carteiro = {
  async enviar(para, assunto, texto) {
    mensagensEnviadas.push({ para, assunto, texto });
  },
};

export const REMETENTE_AVISOS = "Paragem Certa <avisos@mail.pixellusitano.com>";

function carteiroResend(chave: string): Carteiro {
  return {
    async enviar(para, assunto, texto) {
      const resposta = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: { authorization: `Bearer ${chave}`, "content-type": "application/json" },
        body: JSON.stringify({ from: REMETENTE_AVISOS, to: [para], subject: assunto, text: texto }),
      });
      // Só o estado vai no erro: o pedido e a resposta trazem o email e o texto.
      if (!resposta.ok) throw new Error(`o Resend respondeu ${resposta.status}`);
    },
  };
}

export function carteiroDe(env: Env): Carteiro {
  return env.RESEND_API_KEY ? carteiroResend(env.RESEND_API_KEY) : carteiroDeRegisto;
}

/**
 * Cada teto tem o seu contador em `contadores`: os avisos ao autor (denúncias de perigo) não ficam atrás dos recibos e das decisões.
 * Juntos são 35 por dia, no máximo 1085 por mês: longe dos 100 por dia e 3000 por mês do plano grátis do Resend.
 */
const TETOS = { emails: config.emails.outrosPorDia, autor: 5 };
type Teto = keyof typeof TETOS;

/**
 * Envia um email se couber no teto do dia: `emails.outrosPorDia` por omissão, ou o dos avisos ao autor.
 * `false` se o teto está cheio ou o Resend falhou. Também o "Enviar agora" do pc-admin passa por aqui.
 * Limite: um envio que falha gasta o seu lugar do dia; devolvê-lo se o Resend cair muitas vezes.
 */
export async function enviarNoTeto(env: Env, para: string, assunto: string, texto: string, agora = Date.now(), teto: Teto = "emails"): Promise<boolean> {
  const dia = new Date(agora).toISOString().slice(0, 10);
  // Contar e reservar o lugar num só passo, para envios em paralelo não passarem do teto.
  const lugar = await env.DB.prepare(
    "INSERT INTO contadores (dia, nome, quantos) VALUES (?1, ?3, 1) ON CONFLICT (dia, nome) DO UPDATE SET quantos = quantos + 1 WHERE quantos < ?2",
  )
    .bind(dia, TETOS[teto], teto)
    .run();
  if (lugar.meta.changes === 0) return false;
  try {
    await carteiroDe(env).enviar(para, assunto, texto);
    return true;
  } catch {
    // Sem registo: o erro só traz o estado do Resend.
    return false;
  }
}

/**
 * Envia um email (recibos, explicações, avisos ao autor) sem o perder: se não couber no teto do dia ou o Resend falhar,
 * fica em `emails_por_enviar`, que a página de revisão mostra com "Enviar agora".
 * O destinatário vem sempre de quem chama, nunca do texto de um pedido.
 */
export async function enviarOuGuardar(env: Env, para: string, assunto: string, texto: string, agora = Date.now(), teto: Teto = "emails"): Promise<"enviado" | "guardado"> {
  if (await enviarNoTeto(env, para, assunto, texto, agora, teto)) return "enviado";
  await env.DB.prepare("INSERT INTO emails_por_enviar (id, para, assunto, texto, criado_em) VALUES (?, ?, ?, ?, ?)")
    .bind(crypto.randomUUID(), para, assunto, texto, agora)
    .run();
  return "guardado";
}
