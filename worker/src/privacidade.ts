import modelo from "../modelos/privacidade.html";

const ESCAPES: Record<string, string> = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };

export function escaparHtml(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => ESCAPES[c]);
}

/** A política de privacidade com o nome do responsável pelos dados no sítio de `{{RESPONSAVEL}}`. */
export function paginaPrivacidade(responsavel: string): string {
  // Função em vez de cadeia: um nome com "$&" não pode ser lido como padrão de substituição.
  return modelo.replaceAll("{{RESPONSAVEL}}", () => escaparHtml(responsavel));
}
