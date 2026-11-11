import { semConteudo } from "./respostas";

const HORA = 60 * 60_000;
const DIA = 24 * HORA;
/** Expira ao fim de 60 dias sem uso ou de 180 dias no total. */
const SEM_USO = 60 * DIA;
const TOTAL = 180 * DIA;

/** SHA-256 em hexadecimal. É o que se guarda dos tokens e dos códigos; nunca o valor em si. */
export async function sha256Hex(texto: string): Promise<string> {
  const resumo = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
  return [...new Uint8Array(resumo)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** `bytes` bytes de crypto.getRandomValues em base64url (sem `=`). */
export function aleatorioBase64Url(bytes: number): string {
  const valores = crypto.getRandomValues(new Uint8Array(bytes));
  return btoa(String.fromCharCode(...valores)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

/** Cria uma sessão e devolve o token (32 bytes aleatórios). Só o SHA-256 vai para a base de dados. */
export async function criarSessao(env: Env, contaId: string): Promise<string> {
  const token = aleatorioBase64Url(32);
  const agora = Date.now();
  await env.DB.prepare("INSERT INTO sessoes (hash, conta_id, criada_em, usada_em) VALUES (?, ?, ?, ?)")
    .bind(await sha256Hex(token), contaId, agora, agora)
    .run();
  return token;
}

function tokenDoPedido(pedido: Request): string | null {
  return /^Bearer (\S+)$/i.exec(pedido.headers.get("authorization") ?? "")?.[1] ?? null;
}

/** A conta dona da sessão do pedido (`Authorization: Bearer <token>`), ou null se não houver sessão válida. */
export async function contaDaSessao(pedido: Request, env: Env): Promise<{ contaId: string } | null> {
  const token = tokenDoPedido(pedido);
  if (!token) return null;
  const hash = await sha256Hex(token);
  const sessao = await env.DB.prepare("SELECT conta_id, criada_em, usada_em FROM sessoes WHERE hash = ?")
    .bind(hash)
    .first<{ conta_id: string; criada_em: number; usada_em: number }>();
  if (!sessao) return null;
  const agora = Date.now();
  if (agora - sessao.criada_em >= TOTAL || agora - sessao.usada_em >= SEM_USO) return null;
  // No máximo uma escrita por dia e por sessão: poupa as 100 000 escritas diárias do D1.
  if (agora - sessao.usada_em >= DIA) {
    await env.DB.prepare("UPDATE sessoes SET usada_em = ? WHERE hash = ?").bind(agora, hash).run();
  }
  return { contaId: sessao.conta_id };
}

/** POST /api/auth/sair: apaga a sessão deste token. Sem sessão (ou já apagada) também dá 204: o telemóvel sai na mesma. */
export async function sair(pedido: Request, env: Env): Promise<Response> {
  const token = tokenDoPedido(pedido);
  if (token) await env.DB.prepare("DELETE FROM sessoes WHERE hash = ?").bind(await sha256Hex(token)).run();
  return semConteudo();
}

export async function apagarSessoesExpiradas(env: Env, agora: number): Promise<void> {
  await env.DB.prepare("DELETE FROM sessoes WHERE criada_em < ? OR usada_em < ?").bind(agora - TOTAL, agora - SEM_USO).run();
}
