import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AVISOS } from "../src/admin/paginas";
import { mensagensEnviadas } from "../src/email";
import { REGRAS } from "../src/explicacoes";
import { marcaDoEmail } from "../src/portao";
import { criarConta, LIGADO, pedir, sha256 } from "./ajudas";
import { pedirAdmin } from "./ajudas-admin";

const DIA = 24 * 60 * 60_000;
const INICIO = Date.UTC(2026, 10, 3, 8, 0);
const HOJE = "2026-11-03";
const RAZAO = { motivo: "Aparece a cara de uma pessoa", regra: "fotos_so_a_paragem" };

let ana: string;
let rui: string;

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(INICIO);
  mensagensEnviadas.length = 0;
  // Nada de internet nos testes.
  vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("sem internet nos testes"));
  ana = await criarConta("ana", "ana@exemplo.pt");
  rui = await criarConta("rui", "rui@exemplo.pt");
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

/** Uma ação da página de revisão, da própria origem e com o token do autor. */
function acao(caminho: string, corpo: Record<string, string> = {}): Promise<Response> {
  return pedirAdmin(caminho, { metodo: "POST", corpo });
}

/** 303 de volta à página, com estes avisos (ou nenhum). */
function voltou(resposta: Response, ...avisos: string[]): void {
  expect(resposta.status).toBe(303);
  expect(resposta.headers.get("location")).toBe(avisos.length ? `/?${avisos.map((aviso) => `aviso=${aviso}`).join("&")}` : "/");
  expect(resposta.headers.get("content-security-policy")).toContain("default-src 'none'");
}

async function pagina(caminho = "/"): Promise<string> {
  const resposta = await pedirAdmin(caminho);
  expect(resposta.status).toBe(200);
  return resposta.text();
}

async function comunidade(...paragens: string[]): Promise<{ correcoes: unknown[] }> {
  const resposta = await pedir(`/api/comunidade?paragens=${encodeURIComponent(paragens.join(","))}`, { metodo: "GET", segredos: LIGADO });
  expect(resposta.status).toBe(200);
  return resposta.json();
}

async function decisoes(): Promise<Record<string, unknown>[]> {
  return (await env.DB.prepare("SELECT * FROM decisoes ORDER BY rowid LIMIT 100").all<Record<string, unknown>>()).results;
}

async function contar(sql: string, ...valores: unknown[]): Promise<number> {
  return (await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${sql}`).bind(...valores).first<number>("n"))!;
}

async function relatoDoRui(id = "relato-1", paragem = "CM:1"): Promise<void> {
  await env.DB.batch([
    env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES (?1, 'desvio', ?2, '736', 'rui', ?3, ?4)").bind(id, paragem, INICIO, INICIO + DIA),
    env.DB.prepare("INSERT INTO confirmacoes (relato_id, conta_id, voto, em) VALUES (?1, 'rui', 'ainda_esta', ?2), (?1, 'ana', 'ainda_esta', ?2)").bind(id, INICIO),
  ]);
}

/** Uma correção do Rui por rever. */
async function correcaoDoRui(id = "k1", paragem = "CM:9"): Promise<void> {
  await env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES (?, ?, 'rui', 'nome', 'Largo Novo', ?)").bind(id, paragem, INICIO).run();
}

/** Uma denúncia aberta da Ana, como a rota pública a guarda. */
async function denunciaDaAna(id: string, conteudo: string, motivo = "falso"): Promise<void> {
  await env.DB.prepare("INSERT INTO denuncias (id, conteudo, motivo, nome, email, email_hash, criada_em) VALUES (?, ?, ?, 'Ana', 'ana@exemplo.pt', ?, ?)")
    .bind(id, conteudo, motivo, await sha256("ana@exemplo.pt"), INICIO)
    .run();
}

describe("pc-admin: avisos e formulários", () => {
  it("avisoDesconhecidoNaoAparece", async () => {
    for (const caminho of ["/?aviso=%3Cscript%3E", "/?aviso=toString", "/?aviso=__proto__", "/?aviso=constructor"]) {
      const html = await pagina(caminho);
      expect(html, caminho).not.toContain("<script");
      expect(html, caminho).not.toContain("&lt;script");
      expect(html, caminho).not.toContain('class="aviso"');
    }
    // Só as chaves da lista fixa, com o seu texto.
    const html = await pagina("/?aviso=conta_apagada&aviso=%3Cscript%3E");
    expect(html).toContain(`<p class="aviso">${AVISOS.conta_apagada}</p>`);
    expect(html.match(/class="aviso"/g)).toHaveLength(1);
  });

  it("regraDesconhecidaDa400", async () => {
    await correcaoDoRui();
    await relatoDoRui();
    await denunciaDaAna("denuncia-1", "relato:relato-1");
    const invalidos: [string, Record<string, string>][] = [
      ["/correcoes/k1/rejeitar", { ...RAZAO, regra: "inventada" }],
      ["/correcoes/k1/rejeitar", { ...RAZAO, regra: "toString" }],
      ["/correcoes/k1/rejeitar", { motivo: RAZAO.motivo }],
      ["/correcoes/k1/rejeitar", { ...RAZAO, motivo: "   " }],
      ["/correcoes/k1/rejeitar", { ...RAZAO, motivo: "a".repeat(501) }],
      ["/relatos/relato-1/apagar", { ...RAZAO, regra: "" }],
      ["/denuncias/denuncia-1/decidir", { ...RAZAO, acao: "apagar_tudo" }],
      ["/denuncias/denuncia-1/decidir", { acao: "remover", motivo: "x", regra: "inventada" }],
      ["/contas/rui/bloquear", { ...RAZAO, regra: "inventada" }],
      ["/contas/rui/bloquear", { ...RAZAO, denuncia: "nao-existe" }],
      // Os termos prometem que só se bloqueia depois de uma denúncia.
      ["/contas/rui/bloquear", RAZAO],
      ["/contas/apagar", { email: "isto não é um email" }],
    ];
    for (const [caminho, corpo] of invalidos) {
      const resposta = await acao(caminho, corpo);
      expect(resposta.status, `${caminho} ${JSON.stringify(corpo)}`).toBe(400);
      expect(resposta.headers.get("content-security-policy")).toContain("default-src 'none'");
    }
    // 500 caracteres ainda passam.
    voltou(await acao("/correcoes/k1/rejeitar", { ...RAZAO, motivo: "a".repeat(500) }));

    expect(await contar("relatos WHERE id = 'relato-1'")).toBe(1);
    expect(await contar("denuncias WHERE decidida_em IS NULL")).toBe(1);
    expect(await contar("bloqueios")).toBe(0);
    expect(await contar("contas WHERE id = 'rui'")).toBe(1);
    expect(await decisoes()).toHaveLength(1);
  });

  it("acoesDeOutraOrigemNaoFazemNada", async () => {
    await correcaoDoRui();
    await relatoDoRui();
    for (const [caminho, corpo] of [
      ["/correcoes/k1/aprovar", {}],
      ["/correcoes/k1/rejeitar", RAZAO],
      ["/relatos/relato-1/apagar", RAZAO],
      ["/contas/rui/bloquear", RAZAO],
      ["/contas/apagar", { email: "rui@exemplo.pt" }],
    ] as [string, Record<string, string>][]) {
      for (const origem of ["https://outro.test", null, "null"]) {
        expect((await pedirAdmin(caminho, { metodo: "POST", corpo, origem })).status, `${caminho} ${origem}`).toBe(403);
      }
      // Nem sem o token do Access.
      expect((await pedirAdmin(caminho, { metodo: "POST", corpo, jwt: null })).status, caminho).toBe(403);
    }
    expect(await contar("correcoes WHERE estado = 'pendente'")).toBe(1);
    expect(await contar("relatos")).toBe(1);
    expect(await contar("bloqueios")).toBe(0);
    expect(await contar("contas")).toBe(2);
    expect(await decisoes()).toEqual([]);
    expect(mensagensEnviadas).toEqual([]);
  });

  it("paginaTemOsBotoes", async () => {
    await correcaoDoRui();
    await relatoDoRui();
    await denunciaDaAna("denuncia-1", "relato:relato-1");
    await env.DB.prepare("INSERT INTO bloqueios (email_hash, motivo, criado_em) VALUES ('hash-1', 'Relatos falsos', ?)").bind(INICIO).run();
    await env.DB.prepare("INSERT INTO emails_por_enviar (id, para, assunto, texto, criado_em) VALUES ('email-1', 'ze@exemplo.pt', 'A', 'B', ?)").bind(INICIO).run();

    const html = await pagina();
    for (const acaoDoFormulario of [
      "/contas/rui/bloquear",
      "/correcoes/k1/aprovar", "/correcoes/k1/rejeitar",
      "/denuncias/denuncia-1/decidir", "/relatos/relato-1/apagar",
      "/bloqueios/hash-1/desbloquear", "/emails/email-1/enviar", "/contas/apagar",
    ]) {
      expect(html, acaoDoFormulario).toContain(`<form method="post" action="${acaoDoFormulario}"`);
    }
    for (const regra of Object.keys(REGRAS)) expect(html, regra).toContain(`<option value="${regra}">`);
    expect(html).toContain('name="denuncia" value="denuncia-1"');
    // Só na denúncia: a correção e o relato do rui não trazem "Bloquear conta".
    expect(html.split('action="/contas/rui/bloquear"').length - 1).toBe(1);
  });
});

describe("pc-admin: correções e relatos", () => {
  it("aprovarCorrecaoSubstituiAAntiga", async () => {
    await env.DB.batch([
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, estado, criada_em, decidida_em) VALUES ('k-velha', 'CM:1', 'ana', 'nome', 'Largo Velho', 'aprovada', ?1, ?1)").bind(INICIO - 2 * DIA),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, lat, lon, estado, criada_em, decidida_em) VALUES ('k-posicao', 'CM:1', 'ana', 'posicao', 38.7, -9.1, 'aprovada', ?1, ?1)").bind(INICIO - 2 * DIA),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, estado, criada_em, decidida_em) VALUES ('k-outra', 'CM:2', 'ana', 'nome', 'Outra Paragem', 'aprovada', ?1, ?1)").bind(INICIO - 2 * DIA),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k-nova', 'CM:1', 'rui', 'nome', 'Largo Novo', ?)").bind(INICIO),
    ]);

    voltou(await acao("/correcoes/k-nova/aprovar"));

    expect((await comunidade("CM:1", "CM:2")).correcoes).toEqual(
      expect.arrayContaining([
        { paragem: "CM:1", tipo: "nome", nome: "Largo Novo" },
        { paragem: "CM:1", tipo: "posicao", lat: 38.7, lon: -9.1 },
        { paragem: "CM:2", tipo: "nome", nome: "Outra Paragem" },
      ]),
    );
    expect(await contar("correcoes WHERE id = 'k-velha'")).toBe(0);
    expect(await env.DB.prepare("SELECT estado, decidida_em FROM correcoes WHERE id = 'k-nova'").first()).toEqual({ estado: "aprovada", decidida_em: INICIO });
    expect(await contar("correcoes")).toBe(3);
    expect(mensagensEnviadas).toEqual([]);
    expect((await acao("/correcoes/k-nova/aprovar")).status).toBe(404);
  });

  it("rejeitarCorrecaoExplica", async () => {
    await env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k1', 'CM:1', 'rui', 'nome', 'Nome Inventado', ?)").bind(INICIO).run();
    const razao = { motivo: "A paragem chama-se assim há anos", regra: "correcoes_verdadeiras" };

    voltou(await acao("/correcoes/k1/rejeitar", razao));

    expect(await contar("correcoes")).toBe(0);
    expect(await decisoes()).toMatchObject([{ acao: "correcao_rejeitada", conteudo: "correcao:k1", email_hash: await sha256("rui@exemplo.pt"), motivo: razao.motivo, regra: razao.regra }]);
    expect(mensagensEnviadas).toHaveLength(1);
    expect(mensagensEnviadas[0].para).toBe("rui@exemplo.pt");
    for (const texto of ["O que fizemos", "correção", REGRAS.correcoes_verdadeiras, razao.motivo]) expect(mensagensEnviadas[0].texto, texto).toContain(texto);
    expect((await acao("/correcoes/k1/rejeitar", razao)).status).toBe(404);
    expect(mensagensEnviadas).toHaveLength(1);
  });

  it("apagarRelatoExplicaAQuemRelatou", async () => {
    await relatoDoRui();
    const razao = { motivo: "Não há nenhum desvio nessa linha", regra: "so_o_que_viste" };

    voltou(await acao("/relatos/relato-1/apagar", razao));

    expect(await contar("relatos")).toBe(0);
    expect(await contar("confirmacoes")).toBe(0);
    expect(await decisoes()).toMatchObject([{ acao: "relato_apagado", conteudo: "relato:relato-1", denuncia_id: null, email_hash: await sha256("rui@exemplo.pt") }]);
    expect(mensagensEnviadas).toHaveLength(1);
    expect(mensagensEnviadas[0].para).toBe("rui@exemplo.pt");
    for (const texto of ["relato", "736", "Regra em causa", REGRAS.so_o_que_viste, razao.motivo, "sem denúncia"]) expect(mensagensEnviadas[0].texto, texto).toContain(texto);
    expect((await acao("/relatos/relato-1/apagar", razao)).status).toBe(404);
  });
});

describe("pc-admin: denúncias", () => {
  const razao = { motivo: "O relato era falso", regra: "falso_ou_varias_contas" };

  it("denunciaRemoverApagaEAvisaOsDois", async () => {
    await relatoDoRui();
    await denunciaDaAna("denuncia-1", "relato:relato-1");

    voltou(await acao("/denuncias/denuncia-1/decidir", { ...razao, acao: "remover" }));

    expect(await contar("relatos")).toBe(0);
    expect(await decisoes()).toMatchObject([{ acao: "relato_apagado", conteudo: "relato:relato-1", denuncia_id: "denuncia-1", email_hash: await sha256("rui@exemplo.pt") }]);
    expect(mensagensEnviadas).toHaveLength(2);
    const aQuemRelatou = mensagensEnviadas.find((m) => m.para === "rui@exemplo.pt")!;
    const aQuemDenunciou = mensagensEnviadas.find((m) => m.para === "ana@exemplo.pt")!;
    expect(aQuemRelatou.texto).toContain("veio de uma denúncia");
    expect(aQuemDenunciou.assunto).toBe("Decisão sobre a tua denúncia");
    for (const texto of ["Olá Ana", "relato:relato-1", "Removemos o conteúdo", razao.motivo, "decisão automática", "ANACOM"]) expect(aQuemDenunciou.texto, texto).toContain(texto);
    // Depois da decisão enviada fica só o hash de quem denunciou.
    expect(await env.DB.prepare("SELECT nome, email, email_hash, decidida_em FROM denuncias WHERE id = 'denuncia-1'").first()).toEqual({
      nome: null, email: null, email_hash: await sha256("ana@exemplo.pt"), decidida_em: INICIO,
    });
    expect((await acao("/denuncias/denuncia-1/decidir", { ...razao, acao: "remover" })).status).toBe(404);
    expect(mensagensEnviadas).toHaveLength(2);
  });

  it("denunciaDeConteudoQueJaSaiuDecideNaMesma", async () => {
    await relatoDoRui();
    await denunciaDaAna("denuncia-1", "relato:relato-1");
    await env.DB.prepare("INSERT INTO denuncias (id, conteudo, motivo, nome, email, email_hash, criada_em) VALUES ('denuncia-2', 'relato:relato-1', 'falso', 'Eva', 'eva@exemplo.pt', 'h-eva', ?)").bind(INICIO).run();

    voltou(await acao("/denuncias/denuncia-1/decidir", { ...razao, acao: "remover" }));
    mensagensEnviadas.length = 0;
    // A segunda denúncia do mesmo relato: já não há o que apagar, mas quem denunciou recebe a decisão.
    voltou(await acao("/denuncias/denuncia-2/decidir", { ...razao, acao: "remover" }));

    expect(mensagensEnviadas).toHaveLength(1);
    expect(mensagensEnviadas[0]).toMatchObject({ para: "eva@exemplo.pt", assunto: "Decisão sobre a tua denúncia" });
    expect(mensagensEnviadas[0].texto).toContain("Removemos o conteúdo");
    expect(await decisoes()).toHaveLength(1);
    expect(await contar("denuncias WHERE decidida_em IS NULL OR email IS NOT NULL OR nome IS NOT NULL")).toBe(0);
  });

  it("denunciaManterSoAvisaQuemDenunciou", async () => {
    await relatoDoRui();
    await denunciaDaAna("denuncia-1", "relato:relato-1");
    const manter = { motivo: "O desvio foi confirmado pela Carris", regra: "so_o_que_viste", acao: "manter" };

    voltou(await acao("/denuncias/denuncia-1/decidir", manter));

    expect(await contar("relatos")).toBe(1);
    expect(await decisoes()).toEqual([]);
    expect(mensagensEnviadas).toHaveLength(1);
    expect(mensagensEnviadas[0]).toMatchObject({ para: "ana@exemplo.pt", assunto: "Decisão sobre a tua denúncia" });
    expect(mensagensEnviadas[0].texto).toContain("Mantivemos o conteúdo");
    expect(mensagensEnviadas[0].texto).toContain(manter.motivo);
    expect(await env.DB.prepare("SELECT nome, email, decidida_em FROM denuncias WHERE id = 'denuncia-1'").first()).toEqual({ nome: null, email: null, decidida_em: INICIO });
  });
});

describe("pc-admin: contas", () => {
  it("bloquearImpedeRelatar", async () => {
    const teo = await criarConta("teo", "te.o+paragem@gmail.com");
    await denunciaDaAna("denuncia-1", "relato:relato-x");
    const relatar = () => pedir("/api/relatos", { token: teo, corpo: { tipo: "desvio", paragem: "CM:1", linha: "736" }, segredos: LIGADO });
    const razao = { motivo: "Relatos falsos repetidos", regra: "falso_ou_varias_contas", denuncia: "denuncia-1" };

    voltou(await acao("/contas/teo/bloquear", razao));

    const hash = await marcaDoEmail("te.o+paragem@gmail.com");
    expect(hash).toBe(await sha256("teo@gmail.com"));
    expect(await decisoes()).toMatchObject([{ acao: "conta_bloqueada", denuncia_id: "denuncia-1", email_hash: hash, motivo: razao.motivo, regra: razao.regra }]);
    expect(JSON.stringify(await decisoes())).not.toContain("@");
    expect(await env.DB.prepare("SELECT email_hash, motivo FROM bloqueios").all()).toMatchObject({ results: [{ email_hash: hash, motivo: razao.motivo }] });
    expect(mensagensEnviadas).toHaveLength(1);
    expect(mensagensEnviadas[0].para).toBe("te.o+paragem@gmail.com");
    for (const texto of ["Bloqueámos a tua conta", "durante 12 meses", "veio de uma denúncia", REGRAS.falso_ou_varias_contas]) expect(mensagensEnviadas[0].texto, texto).toContain(texto);
    const bloqueada = await relatar();
    expect(bloqueada.status).toBe(403);
    expect(await bloqueada.json()).toEqual({ erro: "conta_bloqueada" });
    // Bloquear outra vez não decide nem explica outra vez (o INSERT não mudou nada, por isso a decisão do mesmo batch não entra).
    expect((await acao("/contas/teo/bloquear", razao)).status).toBe(404);
    expect(await decisoes()).toHaveLength(1);
    expect(mensagensEnviadas).toHaveLength(1);

    voltou(await acao(`/bloqueios/${hash}/desbloquear`));

    expect(await contar("bloqueios")).toBe(0);
    expect((await decisoes()).at(-1)).toMatchObject({ acao: "conta_desbloqueada", email_hash: hash });
    expect(mensagensEnviadas).toHaveLength(1);
    expect((await relatar()).status).toBe(201);
    expect((await acao(`/bloqueios/${hash}/desbloquear`)).status).toBe(404);
    expect(await decisoes()).toHaveLength(2);
  });

  it("adminApagaContaPorEmail", async () => {
    await correcaoDoRui();
    await env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k-ana', 'CM:8', 'ana', 'nome', 'Outro', ?)").bind(INICIO).run();
    await relatoDoRui();

    // Do pedido feito em apagar-conta.html: o email como a pessoa o escreveu.
    voltou(await acao("/contas/apagar", { email: " RUI@Exemplo.pt " }), "conta_apagada");

    for (const tabela of ["relatos", "correcoes", "sessoes", "aceites_termos"]) expect(await contar(`${tabela} WHERE conta_id = 'rui'`), tabela).toBe(0);
    expect(await contar("contas WHERE id = 'rui'")).toBe(0);
    expect(await contar("confirmacoes")).toBe(0);
    // Sem decisão nem email: o autor responde ao pedido.
    expect(await decisoes()).toEqual([]);
    expect(mensagensEnviadas).toEqual([]);
    expect(await contar("contas WHERE id = 'ana'")).toBe(1);
    expect(await contar("correcoes WHERE conta_id = 'ana'")).toBe(1);

    voltou(await acao("/contas/apagar", { email: "ninguem@exemplo.pt" }), "conta_nao_existe");
    voltou(await acao("/contas/apagar", { email: "rui@exemplo.pt" }), "conta_nao_existe");
  });

  it("apagarContaPorEmailApagaTodasAsContasDesseEmail", async () => {
    // Uma conta do Google e uma de código podem ter o mesmo email.
    await criarConta("rui-google", "rui@exemplo.pt");

    voltou(await acao("/contas/apagar", { email: "rui@exemplo.pt" }), "conta_apagada");

    expect(await contar("contas WHERE email = 'rui@exemplo.pt'")).toBe(0);
    expect(await contar("contas")).toBe(1);
  });
});

describe("pc-admin: emails por enviar", () => {
  it("tetoCheioGuardaAExplicacao", async () => {
    await correcaoDoRui();
    await env.DB.prepare("INSERT INTO contadores (dia, nome, quantos) VALUES (?, 'emails', 30)").bind(HOJE).run();

    voltou(await acao("/correcoes/k1/rejeitar", RAZAO), "email_falhou");

    expect(mensagensEnviadas).toEqual([]);
    const guardado = await env.DB.prepare("SELECT id, para, assunto, texto FROM emails_por_enviar").first<{ id: string; para: string; assunto: string; texto: string }>();
    expect(guardado).toMatchObject({ para: "rui@exemplo.pt", assunto: "Decisão sobre o teu conteúdo na Paragem Certa" });
    expect(guardado!.texto).toContain(RAZAO.motivo);
    const html = await pagina("/?aviso=email_falhou");
    expect(html).toContain(AVISOS.email_falhou);
    expect(html).toContain(guardado!.id);
    expect(html).toContain("para rui@exemplo.pt");

    // Com o teto ainda cheio, "Enviar agora" não envia e o email fica.
    voltou(await acao(`/emails/${guardado!.id}/enviar`), "email_falhou");
    expect(mensagensEnviadas).toEqual([]);
    expect(await contar("emails_por_enviar")).toBe(1);

    // Com o teto livre: enviado e apagado da lista, a contar para o teto.
    await env.DB.prepare("UPDATE contadores SET quantos = 0 WHERE dia = ? AND nome = 'emails'").bind(HOJE).run();
    voltou(await acao(`/emails/${guardado!.id}/enviar`));
    expect(mensagensEnviadas).toEqual([{ para: guardado!.para, assunto: guardado!.assunto, texto: guardado!.texto }]);
    expect(await contar("emails_por_enviar")).toBe(0);
    expect(await env.DB.prepare("SELECT quantos FROM contadores WHERE dia = ? AND nome = 'emails'").bind(HOJE).first("quantos")).toBe(1);
    expect((await acao(`/emails/${guardado!.id}/enviar`)).status).toBe(404);
  });
});
