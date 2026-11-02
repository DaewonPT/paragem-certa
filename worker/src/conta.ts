import { corpoJson } from "./codigos";
import { erro, semConteudo } from "./respostas";
import { contaDaSessao } from "./sessoes";

const MAX_VERSAO = 40;

/** DELETE /api/conta: apaga a conta, as sessões (de todos os telemóveis), os favoritos e os aceites dos termos. */
export async function apagarConta(pedido: Request, env: Env): Promise<Response> {
  const sessao = await contaDaSessao(pedido, env);
  if (!sessao) return erro("sem_sessao", 401);

  // Numa transação: ou desaparece tudo, ou nada.
  await env.DB.batch([
    env.DB.prepare("DELETE FROM favoritos WHERE conta_id = ?").bind(sessao.contaId),
    env.DB.prepare("DELETE FROM aceites_termos WHERE conta_id = ?").bind(sessao.contaId),
    env.DB.prepare("DELETE FROM sessoes WHERE conta_id = ?").bind(sessao.contaId),
    env.DB.prepare("DELETE FROM contas WHERE id = ?").bind(sessao.contaId),
  ]);
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
