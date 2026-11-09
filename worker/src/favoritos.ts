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

  const { desde, alteracoes } = await corpoJson(pedido);
  if (!Number.isSafeInteger(desde) || (desde as number) < 0 || !Array.isArray(alteracoes)) return erro("pedido_invalido", 400);
  if (alteracoes.length > MAX_ALTERACOES) return erro("demasiadas_alteracoes", 400);
  if (!alteracoes.every(favoritoValido)) return erro("pedido_invalido", 400);

  // Tudo num só `batch` (uma transação): ou fica tudo gravado, ou nada, e dois telemóveis nunca se misturam a meio.
  const passos: D1PreparedStatement[] = [];
  alteracoes.forEach((f, i) => {
    const revisao = i + 1;
    passos.push(env.DB.prepare(GRAVAR).bind(sessao.contaId, f.chave, f.tipo, f.nome, f.lat, f.lon, f.ordem, f.apagado ? 1 : 0, revisao));
    if ((f.tipo === "CASA" || f.tipo === "TRABALHO") && !f.apagado) {
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
