import config from "../public/dados/config.json";
import { carteiroDe } from "./email";
import { erro, json, semConteudo } from "./respostas";
import { aleatorioBase64Url, apagarSessoesExpiradas, criarSessao, sha256Hex } from "./sessoes";

const MINUTO = 60_000;
const HORA = 60 * MINUTO;
/** Um código (e um nonce do Google) vale 10 minutos. */
export const VALIDADE = 10 * MINUTO;
const MAX_TENTATIVAS = 5;
const MAX_PEDIDOS_POR_HORA = 3;

// Uma só arroba e um ponto depois dela; sem espaços nem separadores, para o endereço ir sempre como um destinatário só.
const FORMATO_EMAIL = /^[^\s@<>,;"]+@[^\s@<>,;"]+\.[^\s@<>,;"]+$/;

function emailDe(valor: unknown): string | null {
  if (typeof valor !== "string") return null;
  const email = valor.trim().toLowerCase();
  return email.length <= 254 && FORMATO_EMAIL.test(email) ? email : null;
}

export async function corpoJson(pedido: Request): Promise<Record<string, unknown>> {
  try {
    const corpo: unknown = await pedido.json();
    return corpo !== null && typeof corpo === "object" ? (corpo as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

/** 6 dígitos sem enviesar: valores acima do maior múltiplo de 1 000 000 que cabe em 32 bits são deitados fora. */
function codigoAleatorio(): string {
  const valor = new Uint32Array(1);
  do crypto.getRandomValues(valor);
  while (valor[0] >= 4_294_000_000);
  return String(valor[0] % 1_000_000).padStart(6, "0");
}

/** POST /api/auth/email/pedir */
export async function pedirCodigo(pedido: Request, env: Env): Promise<Response> {
  const email = emailDe((await corpoJson(pedido)).email);
  if (!email) return erro("email_invalido", 400);

  const agora = Date.now();
  const codigo = codigoAleatorio();
  const sal = aleatorioBase64Url(16);
  const hash = await sha256Hex(sal + codigo);

  // Cada pedido é uma linha (é assim que se contam por hora); só a mais recente serve para confirmar.
  // Contar e inserir num só passo, para pedidos em paralelo não passarem do limite.
  const guardado = await env.DB.prepare(
    `INSERT INTO codigos (email, hash, sal, criado_em)
     SELECT ?1, ?2, ?3, ?4 WHERE (SELECT COUNT(*) FROM codigos WHERE email = ?1 AND criado_em > ?5) < ?6`,
  )
    .bind(email, hash, sal, agora, agora - HORA, MAX_PEDIDOS_POR_HORA)
    .run();
  if (guardado.meta.changes === 0) return erro("muitos_pedidos", 429);

  // O teto diário só conta o que passou o limite por email, para quem insiste num email não gastar o teto de todos.
  const dia = new Date(agora).toISOString().slice(0, 10);
  const contado = await env.DB.prepare(
    "INSERT INTO envios_email (dia, quantos) VALUES (?1, 1) ON CONFLICT (dia) DO UPDATE SET quantos = quantos + 1 WHERE quantos < ?2",
  )
    .bind(dia, config.emails.codigosPorDia)
    .run();
  if (contado.meta.changes === 0) {
    await env.DB.prepare("DELETE FROM codigos WHERE email = ? AND hash = ?").bind(email, hash).run();
    return erro("limite_do_dia", 429);
  }

  try {
    await carteiroDe(env).enviar(
      email,
      "O teu código da Paragem Certa",
      `O teu código é ${codigo}.\n\nVálido durante 10 minutos.\n\nSe não foste tu a pedi-lo, ignora este email.`,
    );
  } catch {
    // Sem registo: o erro não traz nada útil e o código nunca deve ir parar a um registo.
    return erro("envio_falhou", 502);
  }
  return semConteudo();
}

/**
 * A conta do email, criada na primeira entrada. Se houver mais do que uma, a mais antiga. Uma conta criada pelo Google só
 * serve se for do Gmail: noutros domínios o endereço pode ter mudado de dono (o espelho da regra em google.ts).
 */
async function contaDoEmail(env: Env, email: string): Promise<string> {
  const existente = await env.DB.prepare(
    `SELECT id FROM contas WHERE email = ?1
       AND (google_sub IS NULL OR ?1 LIKE '%@gmail.com' OR ?1 LIKE '%@googlemail.com')
     ORDER BY criada_em, id LIMIT 1`,
  ).bind(email).first<{ id: string }>();
  if (existente) return existente.id;
  const id = crypto.randomUUID();
  await env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES (?, ?, ?)").bind(id, email, Date.now()).run();
  return id;
}

/** POST /api/auth/email/confirmar */
export async function confirmarCodigo(pedido: Request, env: Env): Promise<Response> {
  const corpo = await corpoJson(pedido);
  const email = emailDe(corpo.email);
  if (!email) return erro("email_invalido", 400);
  const codigo = typeof corpo.codigo === "string" ? corpo.codigo.trim() : "";

  // Gasta a tentativa antes de comparar, num só passo: pedidos em paralelo não adivinham de graça.
  // Só conta o pedido mais recente deste email, ainda vivo e com tentativas por gastar.
  const atual = await env.DB.prepare(
    `UPDATE codigos SET tentativas = tentativas + 1
     WHERE rowid = (SELECT rowid FROM codigos WHERE email = ?1 ORDER BY criado_em DESC, rowid DESC LIMIT 1)
       AND tentativas < ?2 AND criado_em > ?3
     RETURNING rowid AS id, hash, sal, tentativas`,
  )
    .bind(email, MAX_TENTATIVAS, Date.now() - VALIDADE)
    .first<{ id: number; hash: string; sal: string; tentativas: number }>();
  if (!atual) return erro("codigo_expirado", 410);

  if ((await sha256Hex(atual.sal + codigo)) === atual.hash) {
    // Gasta o código; se dois pedidos certos chegarem juntos, só o primeiro o consegue.
    const gasto = await env.DB.prepare("UPDATE codigos SET hash = '', sal = '', tentativas = ?1 WHERE rowid = ?2 AND hash = ?3")
      .bind(MAX_TENTATIVAS, atual.id, atual.hash)
      .run();
    if (gasto.meta.changes === 0) return erro("codigo_expirado", 410);
    return json({ token: await criarSessao(env, await contaDoEmail(env, email)), email });
  }

  // Na última tentativa errada o código morre. A linha fica (conta nos pedidos por hora), mas já sem com que comparar.
  if (atual.tentativas >= MAX_TENTATIVAS) {
    await env.DB.prepare("UPDATE codigos SET hash = '', sal = '' WHERE rowid = ?").bind(atual.id).run();
  }
  return erro("codigo_errado", 401);
}

/** O Cron Trigger diário: apaga o que passou os prazos. */
export async function limparExpirados(env: Env, agora = Date.now()): Promise<void> {
  await env.DB.batch([
    env.DB.prepare("DELETE FROM codigos WHERE criado_em < ?").bind(agora - VALIDADE),
    env.DB.prepare("DELETE FROM nonces WHERE criado_em < ?").bind(agora - VALIDADE),
  ]);
  await apagarSessoesExpiradas(env, agora);
}
