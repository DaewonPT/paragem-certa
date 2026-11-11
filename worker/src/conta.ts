import { corpoJson } from "./codigos";
import { erro, semConteudo } from "./respostas";
import { contaDaSessao } from "./sessoes";

const MAX_VERSAO = 40;

/**
 * Apaga a conta por inteiro, no D1: os relatos (com os votos dos outros neles), os votos da conta, as
 * correções, os favoritos, os aceites, as sessões e a conta. Denúncias, decisões e bloqueios ficam (só com o hash do
 * email). Usada pelo DELETE /api/conta e pelo pc-admin.
 */
export async function apagarContaPorId(env: Env, contaId: string): Promise<void> {
  // Numa transação: ou desaparece tudo, ou nada.
  await env.DB.batch([
    env.DB.prepare("DELETE FROM confirmacoes WHERE relato_id IN (SELECT id FROM relatos WHERE conta_id = ?)").bind(contaId),
    env.DB.prepare("DELETE FROM confirmacoes WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM relatos WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM correcoes WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM favoritos WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM aceites_termos WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM sessoes WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM contas WHERE id = ?").bind(contaId),
  ]);
}

/** DELETE /api/conta: apaga a conta da sessão por inteiro (ver `apagarContaPorId`). */
export async function apagarConta(pedido: Request, env: Env): Promise<Response> {
  const sessao = await contaDaSessao(pedido, env);
  if (!sessao) return erro("sem_sessao", 401);
  await apagarContaPorId(env, sessao.contaId);
  return semConteudo();
}

/** POST /api/termos: a pessoa aceitou esta versão dos termos. Aceitar outra vez a mesma versão não muda o momento do primeiro aceite. */
export async function aceitarTermos(pedido: Request, env: Env): Promise<Response> {
  const sessao = await contaDaSessao(pedido, env);
  if (!sessao) return erro("sem_sessao", 401);

  const { versao } = await corpoJson(pedido);
  const limpa = typeof versao === "string" ? versao.trim() : "";
  if (limpa.length === 0 || limpa.length > MAX_VERSAO) return erro("pedido_invalido", 400);

  await env.DB.prepare("INSERT INTO aceites_termos (conta_id, versao_termos, aceite_em) VALUES (?, ?, ?) ON CONFLICT (conta_id, versao_termos) DO NOTHING")
    .bind(sessao.contaId, limpa, Date.now())
    .run();
  return semConteudo();
}
