import { REGRAS } from "../explicacoes";
import { escaparHtml } from "../privacidade";

const LIMITE = 100;
const DIA = 24 * 60 * 60_000;

/** O que as ações dizem ao voltar (`/?aviso=<chave>`): só estas chaves se mostram, sempre com este texto. */
export const AVISOS = {
  email_falhou: "O email não saiu: ficou em Emails por enviar.",
  conta_apagada: "Conta apagada.",
  conta_nao_existe: "Não há nenhuma conta com esse email.",
} satisfies Record<string, string>;
export type Aviso = keyof typeof AVISOS;

/** HTML já montado: só o `html` o cria, por isso nenhum texto da base de dados chega aqui sem passar por escaparHtml. */
class Html {
  constructor(readonly texto: string) {}
}

/** Tudo o que entra em `${}` sai escapado, menos outro html`…` (ou uma lista deles). */
function html(partes: TemplateStringsArray, ...valores: unknown[]): Html {
  return new Html(partes.reduce((feito, parte, i) => feito + pedaco(valores[i - 1]) + parte));
}

function pedaco(valor: unknown): string {
  if (valor instanceof Html) return valor.texto;
  if (Array.isArray(valor)) return valor.map(pedaco).join("");
  return escaparHtml(String(valor ?? ""));
}

function hora(ms: number): string {
  return `${new Date(ms).toISOString().slice(0, 16).replace("T", " ")} UTC`;
}

/** Quem enviou: a conta e o email dela (a conta pode já ter sido apagada). */
function quem(contaId: string | null, email: string | null): Html {
  return contaId ? html`conta ${contaId} · ${email ?? "sem email"}` : html`conta já apagada`;
}

function secao<T>(titulo: string, linhas: T[], item: (linha: T) => Html): Html {
  return html`<h2>${titulo} (${linhas.length})</h2>${linhas.length ? html`<ul>${linhas.map(item)}</ul>` : html`<p>Nada.</p>`}`;
}

const OPCOES_DAS_REGRAS = html`<option value="">Regra…</option>${Object.entries(REGRAS).map(([id, texto]) => html`<option value="${id}">${texto}</option>`)}`;

/** Uma ação sem perguntas. */
function botao(acao: string, rotulo: string): Html {
  return html`<form method="post" action="${acao}"><button>${rotulo}</button></form>`;
}

/** Uma ação que restringe alguém: pede o porquê e a regra, que vão no email a essa pessoa. */
function comRazao(acao: string, rotulo: string, antes: Html | string = ""): Html {
  return html`<form method="post" action="${acao}">${antes}<input name="motivo" required maxlength="500" aria-label="Porquê (vai no email)" placeholder="Porquê (vai no email)">
<select name="regra" required aria-label="Regra em causa">${OPCOES_DAS_REGRAS}</select> <button>${rotulo}</button></form>`;
}

/** Só nas denúncias: os termos prometem que só se bloqueia uma conta depois de uma denúncia. */
function bloquearConta(contaId: string | null, denunciaId: string): Html | string {
  if (!contaId) return "";
  return comRazao(`/contas/${contaId}/bloquear`, "Bloquear conta", html`<input type="hidden" name="denuncia" value="${denunciaId}">`);
}

type Correcao = { id: string; paragem: string; criada_em: number; conta_id: string; email: string | null; tipo: string; lat: number | null; lon: number | null; nome: string | null };
type Denuncia = {
  id: string; conteudo: string; motivo: string; explicacao: string; nome: string | null; email: string | null; criada_em: number;
  conta_id: string | null; conta_email: string | null;
};
type Relato = { id: string; tipo: string; paragem: string; linha: string; criado_em: number; expira_em: number; conta_id: string; email: string | null };
type Bloqueio = { email_hash: string; motivo: string; criado_em: number };
type EmailPorEnviar = { id: string; para: string; assunto: string; texto: string; criado_em: number };

// A conta do conteúdo denunciado: "relato:<id>" (o id procura-se pela chave primária).
const SQL_DENUNCIAS = `
  SELECT d.id, d.conteudo, d.motivo, d.explicacao, d.nome, d.email, d.criada_em,
         r.conta_id, c.email AS conta_email
  FROM denuncias d
  LEFT JOIN relatos r ON substr(d.conteudo, 1, 7) = 'relato:' AND r.id = substr(d.conteudo, 8)
  LEFT JOIN contas c ON c.id = r.conta_id
  WHERE d.decidida_em IS NULL AND (d.motivo = 'perigo') = ?
  ORDER BY d.criada_em LIMIT ?`;

/**
 * A página de revisão: o que há por rever, o mais antigo primeiro, quem enviou cada coisa e os botões das ações.
 * `avisos` vem do endereço: só as chaves de AVISOS aparecem, com o texto fixo delas.
 */
export async function paginaDeRevisao(env: Env, avisos: string[] = [], agora = Date.now()): Promise<string> {
  const [correcoes, perigo, outras, relatos, bloqueios, emails, ativas] = await env.DB.batch([
    env.DB.prepare(
      `SELECT k.id, k.paragem, k.tipo, k.lat, k.lon, k.nome, k.criada_em, k.conta_id, c.email FROM correcoes k LEFT JOIN contas c ON c.id = k.conta_id
       WHERE k.estado = 'pendente' ORDER BY k.criada_em LIMIT ?`,
    ).bind(LIMITE),
    // As de perigo à parte, para nunca ficarem para lá das primeiras 100.
    env.DB.prepare(SQL_DENUNCIAS).bind(1, LIMITE),
    env.DB.prepare(SQL_DENUNCIAS).bind(0, LIMITE),
    // Pelo índice da expiração, para não ler mais de 100; mostram-se depois do mais recente para o mais antigo.
    env.DB.prepare(
      `SELECT r.id, r.tipo, r.paragem, r.linha, r.criado_em, r.expira_em, r.conta_id, c.email FROM relatos r LEFT JOIN contas c ON c.id = r.conta_id
       WHERE r.estado = 'ativo' AND r.expira_em > ? ORDER BY r.expira_em DESC LIMIT ?`,
    ).bind(agora, LIMITE),
    env.DB.prepare("SELECT email_hash, motivo, criado_em FROM bloqueios ORDER BY criado_em DESC LIMIT ?").bind(LIMITE),
    env.DB.prepare("SELECT id, para, assunto, texto, criado_em FROM emails_por_enviar ORDER BY criado_em LIMIT ?").bind(LIMITE),
    // Para a ANACOM (DSA, art. 24(3)).
    env.DB.prepare("SELECT COUNT(DISTINCT conta_id) AS n FROM sessoes WHERE usada_em > ?").bind(agora - 30 * DIA),
  ]);
  const ativos = (relatos.results as Relato[]).sort((a, b) => b.criado_em - a.criado_em);
  const denuncias = [...(perigo.results as Denuncia[]), ...(outras.results as Denuncia[])];
  const conhecidos = [...new Set(avisos)].filter((aviso): aviso is Aviso => Object.hasOwn(AVISOS, aviso));

  return html`<!doctype html>
<html lang="pt">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Paragem Certa · revisão</title>
<style>
body { font-family: system-ui, sans-serif; max-width: 60rem; margin: 1rem auto; padding: 0 1rem; }
li { margin: 0.75rem 0; }
pre { white-space: pre-wrap; }
.perigo { color: #b00020; }
.aviso { padding: 0.5rem; border: 2px solid #b00020; }
form { display: inline-block; margin: 0.25rem 0.5rem 0.25rem 0; }
</style>
</head>
<body>
<h1>Paragem Certa · revisão</h1>
${conhecidos.map((aviso) => html`<p class="aviso">${AVISOS[aviso]}</p>`)}
<p>Contas com sessão usada nos últimos 30 dias: ${(ativas.results[0] as { n: number }).n}</p>
${secao("Correções por rever", correcoes.results as Correcao[], (k) => html`<li>${k.id} · ${k.paragem} · ${hora(k.criada_em)} · ${quem(k.conta_id, k.email)}<br>
${k.tipo === "posicao" ? html`Posição: ${k.lat}, ${k.lon}` : k.tipo === "nome" ? html`Nome: ${k.nome}` : html`Já não existe`}<br>
${botao(`/correcoes/${k.id}/aprovar`, "Aprovar")} ${comRazao(`/correcoes/${k.id}/rejeitar`, "Rejeitar")}</li>`)}
${secao("Denúncias abertas", denuncias, (d) => html`<li${d.motivo === "perigo" ? html` class="perigo"` : ""}>${d.id} · motivo ${d.motivo} · ${hora(d.criada_em)}<br>
Conteúdo ${d.conteudo}, de ${quem(d.conta_id, d.conta_email)}<br>
Denunciado por ${d.nome} (${d.email})${d.explicacao ? html`<br>Explicação: ${d.explicacao}` : ""}${d.motivo === "perigo" ? html`<br><strong>Ameaça à vida ou à segurança: comunica à PSP ou à PJ</strong>` : ""}<br>
${comRazao(`/denuncias/${d.id}/decidir`, "Decidir", html`<select name="acao" aria-label="Decisão"><option value="remover">Remover o conteúdo</option><option value="manter">Manter o conteúdo</option></select> ${d.conta_id ? html`<label><input type="checkbox" name="bloquear"> e bloquear a conta</label> ` : ""}`)} ${bloquearConta(d.conta_id, d.id)}</li>`)}
${secao("Relatos ativos", ativos, (r) => html`<li>${r.id} · ${r.tipo}${r.linha ? html` · linha ${r.linha}` : ""} · ${r.paragem} · ${hora(r.criado_em)} até ${hora(r.expira_em)} · ${quem(r.conta_id, r.email)}<br>
${comRazao(`/relatos/${r.id}/apagar`, "Apagar")}</li>`)}
${secao("Contas bloqueadas", bloqueios.results as Bloqueio[], (b) => html`<li>${b.email_hash} · ${hora(b.criado_em)} · ${b.motivo} ${botao(`/bloqueios/${b.email_hash}/desbloquear`, "Desbloquear")}</li>`)}
${secao("Emails por enviar", emails.results as EmailPorEnviar[], (e) => html`<li>${e.id} · para ${e.para} · ${hora(e.criado_em)} · ${e.assunto}<pre>${e.texto}</pre>${botao(`/emails/${e.id}/enviar`, "Enviar agora")}</li>`)}
<h2>Apagar conta por email</h2>
<p>Para os pedidos de apagar-conta.html: apaga todas as contas desse email, com os relatos, as confirmações e as correções.</p>
<form method="post" action="/contas/apagar"><input type="email" name="email" required aria-label="Email da conta" placeholder="Email da conta"> <button>Apagar conta</button></form>
</body>
</html>
`.texto;
}
