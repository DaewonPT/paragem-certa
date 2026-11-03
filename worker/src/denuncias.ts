import { corpoJson } from "./codigos";
import { enviarOuGuardar } from "./email";
import { textoRecibo } from "./explicacoes";
import { interruptores, quemEscreve } from "./portao";
import { erro, semConteudo } from "./respostas";

const DIA = 24 * 60 * 60_000;
const MAX_POR_DIA = 5;
const MAX_NOME = 80;
const MAX_EXPLICACAO = 500;

const MOTIVOS = new Set(["falso", "ofensivo", "dados_pessoais", "perigo", "outro"]);
/** "relato:<id>" ou "foto:<id>"; sem nada que estrague o email nem a página de revisão. */
const FORMATO_CONTEUDO = /^(relato|foto):[A-Za-z0-9-]{1,64}$/;

/** O comprimento em UTF-16 limita o trabalho antes de contar os caracteres de verdade (um emoji ocupa dois). */
function cabe(texto: string, max: number): boolean {
  return texto.length <= 2 * max && [...texto].length <= max;
}

/**
 * POST /api/denuncias { conteudo, motivo, nome, explicacao?, boaFe: true }. Pede conta, mas não os termos nem o
 * bloqueio: quem está bloqueado também pode denunciar. O recibo vai sempre para o email da conta, nunca para um
 * endereço vindo do pedido, e nunca se perde (`enviarOuGuardar`). Denunciar de novo o mesmo conteúdo dá 204 sem outro recibo.
 */
export async function denunciar(pedido: Request, env: Env): Promise<Response> {
  const ligado = interruptores(env);
  if (!ligado.comunidade && !ligado.fotos) return erro("desligado", 403);
  const quem = await quemEscreve(pedido, env);
  if (quem instanceof Response) return quem;

  const { conteudo, motivo, nome, explicacao, boaFe } = await corpoJson(pedido);
  const nomeLimpo = typeof nome === "string" ? nome.trim() : "";
  const nota = explicacao ?? "";
  if (
    typeof conteudo !== "string" || !FORMATO_CONTEUDO.test(conteudo) ||
    typeof motivo !== "string" || !MOTIVOS.has(motivo) ||
    nomeLimpo.length === 0 || !cabe(nomeLimpo, MAX_NOME) || /\p{Cc}/u.test(nomeLimpo) ||
    typeof nota !== "string" || !cabe(nota.trim(), MAX_EXPLICACAO) ||
    boaFe !== true
  ) {
    return erro("pedido_invalido", 400);
  }

  const [tipo, id] = conteudo.split(":");
  const existe = await env.DB.prepare(
    tipo === "relato" ? "SELECT 1 FROM relatos WHERE id = ? LIMIT 1" : "SELECT 1 FROM fotos WHERE id = ? AND estado = 'aprovada' LIMIT 1",
  )
    .bind(id)
    .first();
  if (!existe) return erro("nao_existe", 404);

  const agora = Date.now();
  // A contagem dentro do INSERT: pedidos em paralelo não passam das 5 por dia; o índice único (conteudo, email_hash) apanha a repetida.
  const inserida = await env.DB.prepare(
    `INSERT INTO denuncias (id, conteudo, motivo, explicacao, nome, email, email_hash, criada_em)
     SELECT ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8
     WHERE (SELECT COUNT(*) FROM (SELECT 1 FROM denuncias WHERE email_hash = ?7 AND criada_em > ?9 LIMIT ?10)) < ?10
     ON CONFLICT DO NOTHING`,
  )
    .bind(crypto.randomUUID(), conteudo, motivo, nota.trim(), nomeLimpo, quem.email, quem.emailHash, agora, agora - DIA, MAX_POR_DIA)
    .run();
  if (inserida.meta.changes === 0) {
    const jaDenunciou = await env.DB.prepare("SELECT 1 FROM denuncias WHERE conteudo = ? AND email_hash = ? LIMIT 1").bind(conteudo, quem.emailHash).first();
    return jaDenunciou ? semConteudo() : erro("muitos_pedidos", 429);
  }

  const recibo = textoRecibo(nomeLimpo, conteudo, motivo);
  await enviarOuGuardar(env, quem.email, recibo.assunto, recibo.texto);
  // Ameaça à vida ou à segurança (DSA, art. 18): o autor sabe logo, sem esperar pelo resumo diário.
  if (motivo === "perigo" && env.EMAIL_AUTOR) {
    await enviarOuGuardar(env, env.EMAIL_AUTOR, "Paragem Certa: denúncia de perigo", `Denúncia de perigo sobre ${conteudo}. Abre a página de revisão.`);
  }
  return semConteudo();
}
