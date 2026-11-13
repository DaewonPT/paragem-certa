import { apagarContaPorId } from "../conta";
import { enviarNoTeto, enviarOuGuardar } from "../email";
import { decisaoDaDenuncia, explicacao, REGRAS } from "../explicacoes";
import { marcaDoEmail } from "../portao";
import type { Aviso } from "./paginas";

const MAX_MOTIVO = 500;
/** O email não é único (contas do Google diferentes podem ter tido o mesmo endereço), mas mais do que isto já não é uma pessoa. */
const MAX_CONTAS_DO_EMAIL = 10;
const MAX_SUBSTITUIDAS = 100;
const TIPOS_DE_CORRECAO: Record<string, string> = { posicao: "posição", nome: "nome", ja_nao_existe: "já não existe" };

/** Voltar à página com estes avisos; 400 se o formulário não serve; 404 se não existe ou já foi decidido (um segundo clique). */
export type Desfecho = Aviso[] | 400 | 404;

/** O porquê e a regra, que vão na explicação (DSA, art. 17) e na decisão, e a denúncia de onde veio, se veio. */
type Razao = { motivo: string; regra: string; denunciaId: string | null };

function razaoDe(campos: URLSearchParams, denunciaId: string | null = null): Razao | null {
  const motivo = campos.get("motivo")?.trim() ?? "";
  const regra = campos.get("regra") ?? "";
  // O comprimento em UTF-16 limita o trabalho antes de contar os caracteres de verdade (um emoji ocupa dois).
  if (motivo.length === 0 || motivo.length > 2 * MAX_MOTIVO || [...motivo].length > MAX_MOTIVO || !Object.hasOwn(REGRAS, regra)) return null;
  return { motivo, regra, denunciaId };
}

/** A marca do email de quem foi afetado; se a conta saiu a meio, a decisão fica na mesma, sem marca. */
async function marca(env: Env, email: string | null): Promise<string> {
  return email ? marcaDoEmail(email, env.CHAVE_MARCA_EMAIL) : "";
}

/**
 * A linha da decisão, no mesmo batch e logo a seguir à instrução que muda o conteúdo: só entra se essa instrução mudou
 * alguma linha, por isso um segundo clique não decide duas vezes. Guarda a marca do email, nunca o email.
 */
function decisao(env: Env, acao: string, conteudo: string, razao: Razao, emailHash: string, agora: number): D1PreparedStatement {
  return env.DB.prepare(
    `INSERT INTO decisoes (id, acao, conteudo, denuncia_id, email_hash, motivo, regra, criada_em)
     SELECT ?, ?, ?, ?, ?, ?, ?, ? WHERE changes() > 0`,
  ).bind(crypto.randomUUID(), acao, conteudo, razao.denunciaId, emailHash, razao.motivo, razao.regra, agora);
}

/** A explicação a quem foi afetado (DSA, art. 17); se não saiu, fica em Emails por enviar e o autor sabe. */
async function explicar(env: Env, email: string | null, oQue: string, duracao: string, razao: Razao, agora: number): Promise<Aviso[]> {
  if (!email) return [];
  const { assunto, texto } = explicacao({ oQue, duracao, porque: razao.motivo, regra: REGRAS[razao.regra], deDenuncia: razao.denunciaId !== null });
  return (await enviarOuGuardar(env, email, assunto, texto, agora)) === "enviado" ? [] : ["email_falhou"];
}

/** Apaga um relato, em qualquer estado; os votos saem com ele (ON DELETE CASCADE). */
async function apagarRelato(env: Env, id: string, razao: Razao, agora: number): Promise<Desfecho> {
  const relato = await env.DB.prepare(
    "SELECT r.tipo, r.paragem, r.linha, c.email FROM relatos r LEFT JOIN contas c ON c.id = r.conta_id WHERE r.id = ? LIMIT 1",
  )
    .bind(id)
    .first<{ tipo: string; paragem: string; linha: string; email: string | null }>();
  if (!relato) return 404;
  const [apagou] = await env.DB.batch([
    env.DB.prepare("DELETE FROM relatos WHERE id = ?").bind(id),
    decisao(env, "relato_apagado", `relato:${id}`, razao, await marca(env, relato.email), agora),
  ]);
  if (apagou.meta.changes === 0) return 404;
  const oQue = `Apagámos o teu relato "${relato.tipo.replaceAll("_", " ")}"${relato.linha ? ` da linha ${relato.linha}` : ""} na paragem ${relato.paragem}`;
  return explicar(env, relato.email, oQue, "de vez", razao, agora);
}

/** A correção aprovada substitui as aprovadas antes dela, do mesmo tipo e na mesma paragem (índice correcoes_paragem). */
async function aprovarCorrecao(env: Env, id: string, agora: number): Promise<Desfecho> {
  const [aprovada] = await env.DB.batch([
    env.DB.prepare("UPDATE correcoes SET estado = 'aprovada', decidida_em = ?1 WHERE id = ?2 AND estado = 'pendente'").bind(agora, id),
    env.DB.prepare(
      `DELETE FROM correcoes WHERE id IN (
         SELECT o.id FROM correcoes n JOIN correcoes o ON o.paragem = n.paragem AND o.estado = 'aprovada' AND o.tipo = n.tipo AND o.id <> n.id
         WHERE n.id = ?1 AND n.estado = 'aprovada' LIMIT ?2)`,
    ).bind(id, MAX_SUBSTITUIDAS),
  ]);
  return aprovada.meta.changes === 0 ? 404 : [];
}

async function rejeitarCorrecao(env: Env, id: string, razao: Razao, agora: number): Promise<Desfecho> {
  const correcao = await env.DB.prepare(
    "SELECT k.paragem, k.tipo, c.email FROM correcoes k LEFT JOIN contas c ON c.id = k.conta_id WHERE k.id = ? AND k.estado = 'pendente' LIMIT 1",
  )
    .bind(id)
    .first<{ paragem: string; tipo: string; email: string | null }>();
  if (!correcao) return 404;
  const [apagou] = await env.DB.batch([
    env.DB.prepare("DELETE FROM correcoes WHERE id = ? AND estado = 'pendente'").bind(id),
    decisao(env, "correcao_rejeitada", `correcao:${id}`, razao, await marca(env, correcao.email), agora),
  ]);
  if (apagou.meta.changes === 0) return 404;
  const oQue = `Rejeitámos a tua correção (${TIPOS_DE_CORRECAO[correcao.tipo] ?? correcao.tipo}) da paragem ${correcao.paragem}`;
  return explicar(env, correcao.email, oQue, "de vez", razao, agora);
}

/**
 * Remover (o relato, com a explicação a dizer que veio de uma denúncia) ou manter; em ambos os casos quem
 * denunciou recebe a decisão (DSA, art. 16) e, fechada a denúncia, dela fica só a marca do email.
 * Se o conteúdo já saiu (por outra denúncia, ou a conta foi apagada), a denúncia decide-se na mesma.
 */
async function decidirDenuncia(env: Env, id: string, campos: URLSearchParams, agora: number): Promise<Desfecho> {
  const acao = campos.get("acao");
  const razao = razaoDe(campos, id);
  if ((acao !== "remover" && acao !== "manter") || !razao) return 400;
  // A conta lê-se antes de decidir: "Remover" apaga o relato e o conta_id vai com ele.
  const denuncia = await env.DB.prepare(
    `SELECT d.conteudo, d.nome, d.email, r.conta_id FROM denuncias d LEFT JOIN relatos r ON r.id = substr(d.conteudo, 8)
     WHERE d.id = ? AND d.decidida_em IS NULL LIMIT 1`,
  )
    .bind(id)
    .first<{ conteudo: string; nome: string | null; email: string | null; conta_id: string | null }>();
  if (!denuncia) return 404;

  const avisos: Aviso[] = [];
  if (campos.has("bloquear")) {
    // Limite: sem o relato já não se sabe a conta. Não se decide nada, para o autor não julgar que bloqueou.
    if (!denuncia.conta_id) return 404;
    campos.set("denuncia", id);
    // O 404 (conta já bloqueada ou já apagada) não impede a decisão.
    const feito = await bloquear(env, denuncia.conta_id, campos, agora);
    if (Array.isArray(feito)) avisos.push(...feito);
  }
  if (acao === "remover") {
    // "relato:<id>": o formato foi confirmado ao denunciar.
    const feito = await apagarRelato(env, denuncia.conteudo.split(":")[1], razao, agora);
    if (Array.isArray(feito)) avisos.push(...feito);
  }

  const fechada = await env.DB.prepare("UPDATE denuncias SET nome = NULL, email = NULL, decidida_em = ?1 WHERE id = ?2 AND decidida_em IS NULL")
    .bind(agora, id)
    .run();
  if (fechada.meta.changes === 0) return 404;
  if (denuncia.email) {
    const { assunto, texto } = decisaoDaDenuncia({ nome: denuncia.nome ?? "", conteudo: denuncia.conteudo, removido: acao === "remover", porque: razao.motivo });
    if ((await enviarOuGuardar(env, denuncia.email, assunto, texto, agora)) === "guardado") avisos.push("email_falhou");
  }
  return avisos;
}

/**
 * Pela marca do email normalizado: apanha as variantes do mesmo endereço e sobrevive a apagar a conta. Dura 12 meses (Cron).
 * Só depois de uma denúncia, como prometem os termos.
 */
async function bloquear(env: Env, contaId: string, campos: URLSearchParams, agora: number): Promise<Desfecho> {
  const denunciaId = campos.get("denuncia") || null;
  const razao = razaoDe(campos, denunciaId);
  if (!razao || !denunciaId) return 400;
  if (!(await env.DB.prepare("SELECT 1 FROM denuncias WHERE id = ? LIMIT 1").bind(denunciaId).first())) return 400;
  const conta = await env.DB.prepare("SELECT email FROM contas WHERE id = ? LIMIT 1").bind(contaId).first<{ email: string }>();
  if (!conta) return 404;
  const emailHash = await marcaDoEmail(conta.email, env.CHAVE_MARCA_EMAIL);
  const [novo] = await env.DB.batch([
    env.DB.prepare("INSERT INTO bloqueios (email_hash, motivo, criado_em) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").bind(emailHash, razao.motivo, agora),
    decisao(env, "conta_bloqueada", "", razao, emailHash, agora),
  ]);
  if (novo.meta.changes === 0) return 404;
  const oQue = "Bloqueámos a tua conta nas funções da comunidade (relatar, confirmar relatos e enviar correções)";
  return explicar(env, conta.email, oQue, "durante 12 meses", razao, agora);
}

/** Sem email: do bloqueio só há a marca. */
async function desbloquear(env: Env, emailHash: string, agora: number): Promise<Desfecho> {
  const [saiu] = await env.DB.batch([
    env.DB.prepare("DELETE FROM bloqueios WHERE email_hash = ?").bind(emailHash),
    decisao(env, "conta_desbloqueada", "", { motivo: "", regra: "", denunciaId: null }, emailHash, agora),
  ]);
  return saiu.meta.changes === 0 ? 404 : [];
}

/**
 * O pedido de "Apagar conta" por email (apagar-conta.html): todas as contas desse email, pela mesma função do
 * DELETE /api/conta. Sem decisão nem email: o autor responde ao pedido.
 */
async function apagarContasDoEmail(env: Env, campos: URLSearchParams): Promise<Desfecho> {
  const email = campos.get("email")?.trim().toLowerCase() ?? "";
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+$/.test(email)) return 400;
  const { results } = await env.DB.prepare("SELECT id FROM contas WHERE email = ? LIMIT ?").bind(email, MAX_CONTAS_DO_EMAIL).all<{ id: string }>();
  if (results.length === 0) return ["conta_nao_existe"];
  for (const { id } of results) await apagarContaPorId(env, id);
  return ["conta_apagada"];
}

/** "Enviar agora": passa pelo teto do dia, como os outros; se não sair, fica na lista. */
async function enviarAgora(env: Env, id: string, agora: number): Promise<Desfecho> {
  const email = await env.DB.prepare("SELECT para, assunto, texto FROM emails_por_enviar WHERE id = ? LIMIT 1")
    .bind(id)
    .first<{ para: string; assunto: string; texto: string }>();
  if (!email) return 404;
  if (!(await enviarNoTeto(env, email.para, email.assunto, email.texto, agora))) return ["email_falhou"];
  await env.DB.prepare("DELETE FROM emails_por_enviar WHERE id = ?").bind(id).run();
  return [];
}

function comRazao(campos: URLSearchParams, fazer: (razao: Razao) => Promise<Desfecho>): Promise<Desfecho> | 400 {
  const razao = razaoDe(campos);
  return razao ? fazer(razao) : 400;
}

/**
 * Os formulários da página de revisão (POST application/x-www-form-urlencoded); o index.ts já confirmou o Access e a
 * origem. `null` se o caminho não é uma ação.
 */
export async function fazerAcao(pedido: Request, env: Env, caminho: string): Promise<Desfecho | null> {
  const campos = new URLSearchParams(await pedido.text());
  const agora = Date.now();
  if (caminho === "/contas/apagar") return apagarContasDoEmail(env, campos);
  const [, onde, id, verbo] = /^\/([a-z]+)\/([^/]+)\/([a-z]+)$/.exec(caminho) ?? [];
  switch (`${onde}/${verbo}`) {
    case "correcoes/aprovar":
      return aprovarCorrecao(env, id, agora);
    case "correcoes/rejeitar":
      return comRazao(campos, (razao) => rejeitarCorrecao(env, id, razao, agora));
    case "relatos/apagar":
      return comRazao(campos, (razao) => apagarRelato(env, id, razao, agora));
    case "denuncias/decidir":
      return decidirDenuncia(env, id, campos, agora);
    case "contas/bloquear":
      return bloquear(env, id, campos, agora);
    case "bloqueios/desbloquear":
      return desbloquear(env, id, agora);
    case "emails/enviar":
      return enviarAgora(env, id, agora);
  }
  return null;
}
