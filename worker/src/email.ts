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

function carteiroResend(chave: string): Carteiro {
  return {
    async enviar(para, assunto, texto) {
      const resposta = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: { authorization: `Bearer ${chave}`, "content-type": "application/json" },
        body: JSON.stringify({ from: "Paragem Certa <codigo@mail.pixellusitano.com>", to: [para], subject: assunto, text: texto }),
      });
      // Só o estado vai no erro: o pedido e a resposta trazem o email e o código.
      if (!resposta.ok) throw new Error(`o Resend respondeu ${resposta.status}`);
    },
  };
}

export function carteiroDe(env: Env): Carteiro {
  return env.RESEND_API_KEY ? carteiroResend(env.RESEND_API_KEY) : carteiroDeRegisto;
}
