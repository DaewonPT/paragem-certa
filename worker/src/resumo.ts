import { enviarOuGuardar } from "./email";

/** Conta-se até aqui; acima disto o resumo diz "mais de 100" (nunca se leem mais linhas). */
const MAX_CONTADO = 100;

/** Quantas linhas há, sem ler mais de MAX_CONTADO + 1. */
function contar(env: Env, de: string): D1PreparedStatement {
  return env.DB.prepare(`SELECT COUNT(*) AS n FROM (SELECT 1 FROM ${de} LIMIT ?)`).bind(MAX_CONTADO + 1);
}

function quantos(n: number, singular: string, plural: string): string {
  return n > MAX_CONTADO ? `mais de ${MAX_CONTADO} ${plural}` : `${n} ${n === 1 ? singular : plural}`;
}

/**
 * O email diário ao autor, pelo Cron: quantas fotos, correções e denúncias há por rever e quantos emails
 * ficaram por enviar. Nada se nada houver ou sem EMAIL_AUTOR. Só números: nada de quem enviou ou denunciou.
 * Conta para o teto dos 30 emails do dia (se não couber, fica em Emails por enviar, como os outros).
 */
export async function resumoAoAutor(env: Env, agora = Date.now()): Promise<void> {
  const autor = env.EMAIL_AUTOR?.trim();
  if (!autor) return;
  // Todas pelos índices: fotos_estado, correcoes_estado, denuncias_abertas e a chave dos emails por enviar.
  const contagens = await env.DB.batch([
    contar(env, "fotos WHERE estado = 'pendente'"),
    contar(env, "correcoes WHERE estado = 'pendente'"),
    contar(env, "denuncias WHERE decidida_em IS NULL"),
    contar(env, "denuncias WHERE decidida_em IS NULL AND motivo = 'perigo'"),
    contar(env, "emails_por_enviar"),
  ]);
  const [fotos, correcoes, denuncias, perigo, emails] = contagens.map((resultado) => (resultado.results[0] as { n: number }).n);

  const linhas = [
    fotos && quantos(fotos, "foto por rever", "fotos por rever"),
    correcoes && quantos(correcoes, "correção por rever", "correções por rever"),
    denuncias && `${quantos(denuncias, "denúncia aberta", "denúncias abertas")}${perigo ? ` (${quantos(perigo, "de perigo", "de perigo")})` : ""}`,
    emails && quantos(emails, "email por enviar", "emails por enviar"),
  ].filter(Boolean);
  if (linhas.length === 0) return;

  const texto = `Há coisas por rever na página de revisão:\n\n${linhas.map((linha) => `- ${linha}`).join("\n")}` +
    (perigo ? "\n\nHá denúncias de perigo: vê-as primeiro. Ameaça à vida ou à segurança: comunica à PSP ou à PJ." : "");
  await enviarOuGuardar(env, autor, "Paragem Certa: há coisas por rever", texto, agora);
}
