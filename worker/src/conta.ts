import { limparCache } from "./cache";
import { corpoJson } from "./codigos";
import { URL_FOTOS } from "./fotos";
import { erro, semConteudo } from "./respostas";
import { contaDaSessao } from "./sessoes";

const MAX_VERSAO = 40;

/** Uma conta tem no máximo 1 foto por dia; as rejeitadas já não têm ficheiro. */
const MAX_FOTOS_DA_CONTA = 1000;

/**
 * Apaga a conta por inteiro: os ficheiros das fotos nos dois buckets, a cache das aprovadas e, por fim, no D1, os
 * relatos (com os votos dos outros neles), os votos da conta, as fotos, as correções, os favoritos, os aceites, as
 * sessões e a conta. Denúncias, decisões e bloqueios ficam (só com o hash do email). `false` se o R2 falhou: aí o D1
 * fica igual e pode repetir-se. Usada pelo DELETE /api/conta e pelo pc-admin.
 */
export async function apagarContaPorId(env: Env, contaId: string): Promise<boolean> {
  const { results } = await env.DB.prepare("SELECT chave, estado FROM fotos WHERE conta_id = ? AND chave IS NOT NULL LIMIT ?")
    .bind(contaId, MAX_FOTOS_DA_CONTA)
    .all<{ chave: string; estado: string }>();
  if (results.length > 0) {
    const chaves = results.map((foto) => foto.chave);
    try {
      // Nos dois: uma aprovação a meio pode ter deixado a mesma chave em ambos.
      await Promise.all([env.FOTOS_PENDENTES.delete(chaves), env.FOTOS.delete(chaves)]);
    } catch {
      return false;
    }
    // Uma falha não trava: a cópia em cache acaba sozinha em 24 h.
    await limparCache(env, results.filter((foto) => foto.estado === "aprovada").map((foto) => `${URL_FOTOS}/${foto.chave}`));
  }

  // Numa transação: ou desaparece tudo, ou nada.
  await env.DB.batch([
    env.DB.prepare("DELETE FROM confirmacoes WHERE relato_id IN (SELECT id FROM relatos WHERE conta_id = ?)").bind(contaId),
    env.DB.prepare("DELETE FROM confirmacoes WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM relatos WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM fotos WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM correcoes WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM favoritos WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM aceites_termos WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM sessoes WHERE conta_id = ?").bind(contaId),
    env.DB.prepare("DELETE FROM contas WHERE id = ?").bind(contaId),
  ]);
  return true;
}

/** DELETE /api/conta: apaga a conta da sessão por inteiro (ver `apagarContaPorId`). */
export async function apagarConta(pedido: Request, env: Env): Promise<Response> {
  const sessao = await contaDaSessao(pedido, env);
  if (!sessao) return erro("sem_sessao", 401);
  if (!(await apagarContaPorId(env, sessao.contaId))) return erro("apagar_falhou", 502);
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
