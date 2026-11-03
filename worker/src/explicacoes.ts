/**
 * Os textos dos emails das denúncias e das decisões (DSA, arts. 16 e 17). Só em português; quem pedir em inglês
 * escreve para o contacto. O que muda de caso para caso (nome, motivo, regra) entra por parâmetro.
 */

/** As regras da comunidade que o autor pode invocar, com o texto igual ao dos termos (`public/termos.html`). */
export const REGRAS: Record<string, string> = {
  so_o_que_viste: "Relata só o que viste, junto da paragem ou a bordo da linha. Os relatos têm tipos fixos, sem texto livre.",
  falso_ou_varias_contas: "Não relates o que é falso nem uses várias contas para confirmar o teu próprio relato.",
  fotos_so_a_paragem: "As fotos mostram só a paragem: sem pessoas identificáveis, sem matrículas legíveis e sem conteúdo ofensivo.",
  licenca_das_fotos: 'Ao enviares uma foto, aceitas a licença CC BY-SA 4.0 e que seja creditada como "Passageiro da Paragem Certa".',
  correcoes_verdadeiras: "As correções descrevem só o que existe no local.",
  lei: "Não publiques nada que a lei proíba.",
};

const CONTACTO = "contact@pixellusitano.com";
const SEM_DECISAO_AUTOMATICA = "Não houve nenhuma decisão automática: uma pessoa reviu o caso.";
const CONTESTAR = `Se não concordares, responde a este email ou escreve para ${CONTACTO}. Também podes apresentar queixa à ANACOM, o coordenador dos serviços digitais em Portugal, ou recorrer aos tribunais.`;

/** O motivo e a regra podem já acabar em ponto: não se acrescenta outro. */
function frase(texto: string): string {
  return texto.endsWith(".") ? texto : `${texto}.`;
}

/** O recibo de uma denúncia, para o email da conta de quem denuncia. */
export function textoRecibo(nome: string, conteudo: string, motivo: string): { assunto: string; texto: string } {
  return {
    assunto: "Recebemos a tua denúncia",
    texto:
      `Olá ${nome},\n\n` +
      `Recebemos a tua denúncia sobre o conteúdo ${conteudo} (motivo: ${motivo.replaceAll("_", " ")}). Vamos analisá-la e enviar-te a decisão por email.\n\n` +
      `Se houver perigo imediato para alguém, liga 112.\n\n` +
      `Paragem Certa · ${CONTACTO}`,
  };
}

/** A explicação a quem teve um conteúdo rejeitado ou apagado, ou a conta bloqueada. */
export function explicacao(a: { oQue: string; duracao: string; porque: string; regra: string; deDenuncia: boolean }): { assunto: string; texto: string } {
  return {
    assunto: "Decisão sobre o teu conteúdo na Paragem Certa",
    texto: [
      `O que fizemos: ${[a.oQue, a.duracao, "em toda a app"].filter(Boolean).join(", ")}.`,
      `Porquê: ${frase(a.porque)}`,
      `Regra em causa: ${frase(a.regra)}`,
      a.deDenuncia ? "A decisão veio de uma denúncia de outra pessoa." : "A decisão foi do autor da Paragem Certa, sem denúncia.",
      SEM_DECISAO_AUTOMATICA,
      CONTESTAR,
    ].join("\n\n"),
  };
}

/** A decisão sobre uma denúncia, para o email da conta de quem denunciou. */
export function decisaoDaDenuncia(a: { nome: string; conteudo: string; removido: boolean; porque: string }): { assunto: string; texto: string } {
  return {
    assunto: "Decisão sobre a tua denúncia",
    texto: [
      `Olá ${a.nome},`,
      `Analisámos a tua denúncia sobre o conteúdo ${a.conteudo}.`,
      a.removido ? "Removemos o conteúdo." : "Mantivemos o conteúdo.",
      `Porquê: ${frase(a.porque)}`,
      SEM_DECISAO_AUTOMATICA,
      CONTESTAR,
    ].join("\n\n"),
  };
}
