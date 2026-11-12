import { corpoJson } from "./codigos";
import { erro, json } from "./respostas";
import { contaDaSessao } from "./sessoes";

export type Favorito = {
  chave: string;
  tipo: "PARAGEM" | "CASA" | "TRABALHO" | "OUTRO" | "LINHA";
  nome: string;
  lat: number;
  lon: number;
  ordem: number;
  apagado: boolean;
};

const TIPOS = new Set(["PARAGEM", "CASA", "TRABALHO", "OUTRO", "LINHA"]);
const MAX_ALTERACOES = 500;
// Tetos por conta: uma só conta não pode gastar a quota grátis do D1 (5 M leituras, 100 000 escritas por dia) de todos.
// Limite: os apagados nunca saem da base (têm de chegar aos outros telemóveis), por isso uma conta com 1000 linhas já não
// consegue enviar alterações (ficam no telemóvel); limpar apagados antigos se alguém lá chegar.
const MAX_FAVORITOS = 1000; // linhas da conta, apagados incluídos
const MAX_ALTERACOES_POR_DIA = 1000; // duas sincronizações cheias; tem de ser >= MAX_ALTERACOES
const MAX_TEXTO = 120;
// "LINHA|<linha>|<sentido>|<paragem>": uma linha da CP sem nome curto já passa dos 120.
const MAX_CHAVE_LINHA = 250;

function favoritoValido(valor: unknown): valor is Favorito {
  if (valor === null || typeof valor !== "object") return false;
  const f = valor as Record<string, unknown>;
  // As comparações com `lat` e `lon` deixam de fora o NaN e o infinito.
  return (
    typeof f.tipo === "string" && TIPOS.has(f.tipo) &&
    typeof f.chave === "string" && f.chave.length > 0 && f.chave.length <= (f.tipo === "LINHA" ? MAX_CHAVE_LINHA : MAX_TEXTO) &&
    (f.tipo !== "LINHA" || f.chave.startsWith("LINHA|")) &&
    typeof f.nome === "string" && f.nome.length <= MAX_TEXTO &&
    typeof f.lat === "number" && f.lat >= -90 && f.lat <= 90 &&
    typeof f.lon === "number" && f.lon >= -180 && f.lon <= 180 &&
    Number.isSafeInteger(f.ordem) &&
    typeof f.apagado === "boolean"
  );
}

// A revisão de cada alteração é `contas.revisao` (que só sobe no fim do pedido) mais o número de ordem dela no pedido;
// ganha a alteração que chega por último, porque é a que fica na linha.
const GRAVAR = `
  INSERT INTO favoritos (conta_id, chave, tipo, nome, lat, lon, ordem, apagado, revisao)
  VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, (SELECT revisao FROM contas WHERE id = ?1) + ?9)
  ON CONFLICT (conta_id, chave) DO UPDATE SET
    tipo = excluded.tipo, nome = excluded.nome, lat = excluded.lat, lon = excluded.lon,
    ordem = excluded.ordem, apagado = excluded.apagado, revisao = excluded.revisao`;

// Só há uma Casa e um Trabalho por conta: os outros do mesmo tipo ficam apagados, com a revisão da nova (?4),
// para chegarem aos outros telemóveis. Revisões iguais no mesmo pedido não faltam a ninguém: entram todas juntas.
const SUBSTITUIR = `
  UPDATE favoritos SET apagado = 1, revisao = (SELECT revisao FROM contas WHERE id = ?1) + ?4
  WHERE conta_id = ?1 AND tipo = ?2 AND chave <> ?3 AND apagado = 0`;

type Linha = Omit<Favorito, "apagado"> & { apagado: number; revisao: number };

/** POST /api/sincronizar: grava as alterações do telemóvel e devolve tudo o que mudou na conta desde a revisão `desde`. */
export async function sincronizar(pedido: Request, env: Env): Promise<Response> {
  const sessao = await contaDaSessao(pedido, env);
  if (!sessao) return erro("sem_sessao", 401);
  // Antes de ler o corpo, para os pedidos mal feitos também contarem. É aproximado (docs/servidor.md): as escritas ficam presas pelos tetos abaixo.
  const { success } = await env.LIMITADOR.limit({ key: `sincronizar:${sessao.contaId}` });
  if (!success) return erro("devagar", 429);

  const { desde, alteracoes } = await corpoJson(pedido);
  if (!Number.isSafeInteger(desde) || (desde as number) < 0 || !Array.isArray(alteracoes)) return erro("pedido_invalido", 400);
  if (alteracoes.length > MAX_ALTERACOES) return erro("demasiadas_alteracoes", 400);
  if (!alteracoes.every(favoritoValido)) return erro("pedido_invalido", 400);

  // Só quem traz alterações gasta os tetos: a sincronização que só lê passa sempre.
  if (alteracoes.length > 0) {
    const total = await env.DB.prepare("SELECT COUNT(*) AS n FROM (SELECT 1 FROM favoritos WHERE conta_id = ?1 LIMIT ?2)")
      .bind(sessao.contaId, MAX_FAVORITOS)
      .first<{ n: number }>();
    if (total!.n >= MAX_FAVORITOS) return erro("demasiados_favoritos", 403);
    // Contar e reservar num só passo, como em enviarNoTeto; um contador "favoritos:<conta>" por dia, que o Cron apaga.
    // Limite: se o batch falhar depois, as alterações reservadas contam na mesma para o dia; devolvê-las se isto acontecer.
    const contado = await env.DB.prepare(
      "INSERT INTO contadores (dia, nome, quantos) VALUES (?1, ?2, ?3) ON CONFLICT (dia, nome) DO UPDATE SET quantos = quantos + ?3 WHERE quantos + ?3 <= ?4",
    )
      .bind(new Date().toISOString().slice(0, 10), `favoritos:${sessao.contaId}`, alteracoes.length, MAX_ALTERACOES_POR_DIA)
      .run();
    if (contado.meta.changes === 0) return erro("muitos_pedidos", 429);
  }

  // Tudo num só `batch` (uma transação): ou fica tudo gravado, ou nada, e dois telemóveis nunca se misturam a meio.
  const passos: D1PreparedStatement[] = [];
  // Só a última Casa (e o último Trabalho) por apagar do pedido troca as outras: cada troca lê os favoritos todos da conta.
  const ultima = new Map<string, number>();
  alteracoes.forEach((f, i) => {
    if (!f.apagado) ultima.set(f.tipo, i);
  });
  alteracoes.forEach((f, i) => {
    const revisao = i + 1;
    passos.push(env.DB.prepare(GRAVAR).bind(sessao.contaId, f.chave, f.tipo, f.nome, f.lat, f.lon, f.ordem, f.apagado ? 1 : 0, revisao));
    if ((f.tipo === "CASA" || f.tipo === "TRABALHO") && ultima.get(f.tipo) === i) {
      passos.push(env.DB.prepare(SUBSTITUIR).bind(sessao.contaId, f.tipo, f.chave, revisao));
    }
  });
  if (alteracoes.length > 0) passos.push(env.DB.prepare("UPDATE contas SET revisao = revisao + ? WHERE id = ?").bind(alteracoes.length, sessao.contaId));
  passos.push(
    env.DB.prepare("SELECT revisao FROM contas WHERE id = ?").bind(sessao.contaId),
    // Limite: sem LIMIT (cortar a lista perdia favoritos em silêncio), só o índice (conta_id, revisao); passar a paginar se uma conta puder ter milhares de favoritos.
    env.DB.prepare("SELECT chave, tipo, nome, lat, lon, ordem, apagado, revisao FROM favoritos WHERE conta_id = ? AND revisao > ? ORDER BY revisao, chave").bind(
      sessao.contaId,
      desde,
    ),
  );
  const resultados = await env.DB.batch(passos);

  const conta = resultados.at(-2)!.results[0] as { revisao: number } | undefined;
  // A conta foi apagada enquanto o pedido corria (por outro telemóvel): a sessão já não existe.
  if (!conta) return erro("sem_sessao", 401);
  const linhas = resultados.at(-1)!.results as Linha[];
  return json({ revisao: conta.revisao, alteracoes: linhas.map((linha) => ({ ...linha, apagado: linha.apagado === 1 })) });
}
