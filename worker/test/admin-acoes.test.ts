import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from "vitest";
import { AVISOS } from "../src/admin/paginas";
import { mensagensEnviadas } from "../src/email";
import { REGRAS } from "../src/explicacoes";
import { marcaDoEmail } from "../src/portao";
import { criarConta, LIGADO, pedir, sha256 } from "./ajudas";
import { pedirAdmin } from "./ajudas-admin";

const DIA = 24 * 60 * 60_000;
const INICIO = Date.UTC(2026, 10, 3, 8, 0);
const HOJE = "2026-11-03";
const PURGA: Partial<Env> = { CF_PURGE_TOKEN: "t", CF_ZONA: "z" };
const URL_PURGE = "https://api.cloudflare.com/client/v4/zones/z/purge_cache";
const RAZAO = { motivo: "Aparece a cara de uma pessoa", regra: "fotos_so_a_paragem" };

let fetchEspiado: MockInstance<typeof fetch>;
let purgeResponde: number;
let ana: string;
let rui: string;

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(INICIO);
  mensagensEnviadas.length = 0;
  purgeResponde = 200;
  // Nada de internet: só o purge da Cloudflare responde (com `purgeResponde`); o resto falha.
  fetchEspiado = vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
    if (String(url) === URL_PURGE) return new Response("{}", { status: purgeResponde });
    throw new Error("sem internet nos testes");
  });
  ana = await criarConta("ana", "ana@exemplo.pt");
  rui = await criarConta("rui", "rui@exemplo.pt");
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

/** Um JPEG mínimo e sem metadados; `marca` dá a cada foto o seu SHA-256. */
function jpeg(marca: number): Uint8Array {
  return Uint8Array.from([0xff, 0xd8, 0xff, 0xda, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3f, 0x00, 0x12, 0x34, marca, 0xff, 0xd9]);
}

function enviarFoto(token: string, paragem: string, marca: number): Promise<Response> {
  return pedir(`/api/fotos?paragem=${encodeURIComponent(paragem)}`, { token, corpo: jpeg(marca), segredos: LIGADO, cabecalhos: { "content-type": "image/jpeg" } });
}

/** Uma foto enviada pela app (fica pendente no bucket privado). */
async function fotoPendente(token = rui, paragem = "CM:1", marca = 1): Promise<{ id: string; chave: string }> {
  const resposta = await enviarFoto(token, paragem, marca);
  expect(resposta.status).toBe(201);
  const { id } = (await resposta.json()) as { id: string };
  return { id, chave: (await linhaFoto(id))!.chave! };
}

async function linhaFoto(id: string): Promise<{ estado: string; chave: string | null; decidida_em: number | null } | null> {
  return env.DB.prepare("SELECT estado, chave, decidida_em FROM fotos WHERE id = ?").bind(id).first();
}

/** Uma ação da página de revisão, da própria origem e com o token do autor. */
function acao(caminho: string, corpo: Record<string, string> = {}, segredos: Partial<Env> = PURGA): Promise<Response> {
  return pedirAdmin(caminho, { metodo: "POST", corpo, segredos });
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

async function comunidade(...paragens: string[]): Promise<{ fotos: unknown[]; emRevisao: string[]; correcoes: unknown[] }> {
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

function purges(): string[][] {
  return fetchEspiado.mock.calls.filter(([url]) => String(url) === URL_PURGE).map(([, opcoes]) => JSON.parse(String(opcoes?.body)).files);
}

async function chavesNoR2(bucket: R2Bucket): Promise<string[]> {
  return (await bucket.list()).objects.map((objeto) => objeto.key);
}

async function relatoDoRui(id = "relato-1", paragem = "CM:1"): Promise<void> {
  await env.DB.batch([
    env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES (?1, 'desvio', ?2, '736', 'rui', ?3, ?4)").bind(id, paragem, INICIO, INICIO + DIA),
    env.DB.prepare("INSERT INTO confirmacoes (relato_id, conta_id, voto, em) VALUES (?1, 'rui', 'ainda_esta', ?2), (?1, 'ana', 'ainda_esta', ?2)").bind(id, INICIO),
  ]);
}

/** Uma denúncia aberta da Ana, como a rota pública a guarda. */
async function denunciaDaAna(id: string, conteudo: string, motivo = "falso"): Promise<void> {
  await env.DB.prepare("INSERT INTO denuncias (id, conteudo, motivo, nome, email, email_hash, criada_em) VALUES (?, ?, ?, 'Ana', 'ana@exemplo.pt', ?, ?)")
    .bind(id, conteudo, motivo, await sha256("ana@exemplo.pt"), INICIO)
    .run();
}

describe("pc-admin: fotos", () => {
  it("aprovarCopiaParaOBucketPublico", async () => {
    const { id, chave } = await fotoPendente();

    voltou(await acao(`/fotos/${id}/aprovar`));

    const publica = await env.FOTOS.get(chave);
    expect(publica?.httpMetadata).toMatchObject({ contentType: "image/jpeg", cacheControl: "public, max-age=86400" });
    expect(new Uint8Array(await publica!.arrayBuffer())).toEqual(jpeg(1));
    expect(await env.FOTOS_PENDENTES.get(chave)).toBeNull();
    expect(await linhaFoto(id)).toEqual({ estado: "aprovada", chave, decidida_em: INICIO });
    expect(await comunidade("CM:1")).toMatchObject({ fotos: [{ paragem: "CM:1", id, url: `https://fotos.pixellusitano.com/${chave}` }], emRevisao: [] });
    // Aprovar não é uma restrição: nenhum email, nenhuma decisão. Um segundo clique já não encontra nada por rever.
    expect(mensagensEnviadas).toEqual([]);
    expect(await decisoes()).toEqual([]);
    expect((await acao(`/fotos/${id}/aprovar`)).status).toBe(404);
  });

  it("rejeitarApagaEExplica", async () => {
    const { id, chave } = await fotoPendente();

    voltou(await acao(`/fotos/${id}/rejeitar`, RAZAO));

    expect(await env.FOTOS_PENDENTES.head(chave)).toBeNull();
    expect(await env.FOTOS.head(chave)).toBeNull();
    expect(await linhaFoto(id)).toEqual({ estado: "rejeitada", chave: null, decidida_em: INICIO });
    const feitas = await decisoes();
    expect(feitas).toHaveLength(1);
    expect(feitas[0]).toMatchObject({
      acao: "foto_rejeitada", conteudo: `foto:${id}`, denuncia_id: null, email_hash: await sha256("rui@exemplo.pt"), motivo: RAZAO.motivo, regra: RAZAO.regra, criada_em: INICIO,
    });
    expect(JSON.stringify(feitas)).not.toContain("@");
    expect(mensagensEnviadas).toHaveLength(1);
    const [mensagem] = mensagensEnviadas;
    expect(mensagem.para).toBe("rui@exemplo.pt");
    expect(mensagem.assunto).toBe("Decisão sobre o teu conteúdo na Paragem Certa");
    for (const texto of ["O que fizemos", "foto", "Não houve nenhuma decisão automática", RAZAO.motivo, REGRAS.fotos_so_a_paragem, "sem denúncia", "ANACOM"]) {
      expect(mensagem.texto, texto).toContain(texto);
    }

    // Um segundo clique não decide nem explica outra vez.
    expect((await acao(`/fotos/${id}/rejeitar`, RAZAO)).status).toBe(404);
    expect(await decisoes()).toHaveLength(1);
    expect(mensagensEnviadas).toHaveLength(1);
  });

  it("rejeitadaAindaContaParaOLimite", async () => {
    const { id } = await fotoPendente(rui, "CM:1", 1);
    voltou(await acao(`/fotos/${id}/rejeitar`, RAZAO));

    const outra = await enviarFoto(rui, "CM:2", 2);
    expect(outra.status).toBe(429);
    expect(await outra.json()).toEqual({ erro: "muitos_pedidos" });
    // A paragem fica livre logo para outra conta.
    expect((await enviarFoto(ana, "CM:1", 3)).status).toBe(201);
  });

  it("apagarFotoAprovadaLimpaACache", async () => {
    const { id, chave } = await fotoPendente();
    voltou(await acao(`/fotos/${id}/aprovar`));
    expect(purges()).toEqual([]);

    voltou(await acao(`/fotos/${id}/apagar`, RAZAO));

    expect(purges()).toEqual([[`https://fotos.pixellusitano.com/${chave}`]]);
    const [, opcoes] = fetchEspiado.mock.calls.find(([url]) => String(url) === URL_PURGE)!;
    expect(new Headers(opcoes?.headers).get("authorization")).toBe("Bearer t");
    expect(await chavesNoR2(env.FOTOS)).toEqual([]);
    expect(await chavesNoR2(env.FOTOS_PENDENTES)).toEqual([]);
    expect(await comunidade("CM:1")).toMatchObject({ fotos: [], emRevisao: [] });
    expect(await linhaFoto(id)).toEqual({ estado: "rejeitada", chave: null, decidida_em: INICIO });
    expect(await decisoes()).toMatchObject([{ acao: "foto_apagada", conteudo: `foto:${id}`, email_hash: await sha256("rui@exemplo.pt") }]);
    expect(mensagensEnviadas).toHaveLength(1);
    expect(mensagensEnviadas[0]).toMatchObject({ para: "rui@exemplo.pt" });
    expect(mensagensEnviadas[0].texto).toContain("Apagámos");
  });

  it("cacheQueFalhaAvisaOAutor", async () => {
    const { id, chave } = await fotoPendente();
    voltou(await acao(`/fotos/${id}/aprovar`));
    purgeResponde = 500;

    voltou(await acao(`/fotos/${id}/apagar`, RAZAO), "cache_falhou");

    expect(await pagina("/?aviso=cache_falhou")).toContain("A cache não limpou: a foto desaparece em 24 h.");
    // A foto foi apagada na mesma, e a pessoa recebeu a explicação.
    expect(await env.FOTOS.head(chave)).toBeNull();
    expect((await linhaFoto(id))?.estado).toBe("rejeitada");
    expect(mensagensEnviadas).toHaveLength(1);
  });

  it("r2EmBaixoNaoMudaNada", async () => {
    const { id, chave } = await fotoPendente();
    const r2EmBaixo = {
      delete: async () => {
        throw new Error("R2 em baixo");
      },
    } as unknown as R2Bucket;

    voltou(await acao(`/fotos/${id}/rejeitar`, RAZAO, { FOTOS_PENDENTES: r2EmBaixo }), "apagar_falhou");

    expect(await linhaFoto(id)).toEqual({ estado: "pendente", chave, decidida_em: null });
    expect(await decisoes()).toEqual([]);
    expect(mensagensEnviadas).toEqual([]);
  });

  it("fluxoCompletoDeUmaFoto", async () => {
    // Enviada pela app → aparece na página de revisão → aprovada → na paragem → apagada → some.
    const { id } = await fotoPendente(rui, "CM:1", 1);
    expect(await comunidade("CM:1")).toMatchObject({ fotos: [], emRevisao: ["CM:1"] });
    const html = await pagina();
    expect(html).toContain(`src="/foto/${id}"`);
    expect(new Uint8Array(await (await pedirAdmin(`/foto/${id}`)).arrayBuffer())).toEqual(jpeg(1));

    voltou(await acao(`/fotos/${id}/aprovar`));
    expect((await comunidade("CM:1")).fotos).toHaveLength(1);
    voltou(await acao(`/fotos/${id}/apagar`, RAZAO));
    expect(await comunidade("CM:1")).toMatchObject({ fotos: [], emRevisao: [] });

    // Outra: rejeitada → chega o email com a explicação.
    mensagensEnviadas.length = 0;
    const outra = await fotoPendente(ana, "CM:2", 2);
    voltou(await acao(`/fotos/${outra.id}/rejeitar`, RAZAO));
    expect(mensagensEnviadas).toHaveLength(1);
    expect(mensagensEnviadas[0].para).toBe("ana@exemplo.pt");
    expect(mensagensEnviadas[0].texto).toContain(REGRAS.fotos_so_a_paragem);
    expect(await comunidade("CM:2")).toMatchObject({ fotos: [], emRevisao: [] });
  });
});

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
    const { id } = await fotoPendente();
    await relatoDoRui();
    await denunciaDaAna("denuncia-1", "relato:relato-1");
    const invalidos: [string, Record<string, string>][] = [
      [`/fotos/${id}/rejeitar`, { ...RAZAO, regra: "inventada" }],
      [`/fotos/${id}/rejeitar`, { ...RAZAO, regra: "toString" }],
      [`/fotos/${id}/rejeitar`, { motivo: RAZAO.motivo }],
      [`/fotos/${id}/rejeitar`, { ...RAZAO, motivo: "   " }],
      [`/fotos/${id}/rejeitar`, { ...RAZAO, motivo: "a".repeat(501) }],
      [`/fotos/${id}/apagar`, { ...RAZAO, regra: "inventada" }],
      ["/relatos/relato-1/apagar", { ...RAZAO, regra: "" }],
      ["/denuncias/denuncia-1/decidir", { ...RAZAO, acao: "apagar_tudo" }],
      ["/denuncias/denuncia-1/decidir", { acao: "remover", motivo: "x", regra: "inventada" }],
      ["/contas/rui/bloquear", { ...RAZAO, regra: "inventada" }],
      ["/contas/rui/bloquear", { ...RAZAO, denuncia: "nao-existe" }],
      ["/contas/apagar", { email: "isto não é um email" }],
    ];
    for (const [caminho, corpo] of invalidos) {
      const resposta = await acao(caminho, corpo);
      expect(resposta.status, `${caminho} ${JSON.stringify(corpo)}`).toBe(400);
      expect(resposta.headers.get("content-security-policy")).toContain("default-src 'none'");
    }
    // 500 caracteres ainda passam.
    voltou(await acao(`/fotos/${id}/rejeitar`, { ...RAZAO, motivo: "a".repeat(500) }));

    expect(await contar("relatos WHERE id = 'relato-1'")).toBe(1);
    expect(await contar("denuncias WHERE decidida_em IS NULL")).toBe(1);
    expect(await contar("bloqueios")).toBe(0);
    expect(await contar("contas WHERE id = 'rui'")).toBe(1);
    expect(await decisoes()).toHaveLength(1);
  });

  it("acoesDeOutraOrigemNaoFazemNada", async () => {
    const { id, chave } = await fotoPendente();
    await relatoDoRui();
    for (const [caminho, corpo] of [
      [`/fotos/${id}/aprovar`, {}],
      [`/fotos/${id}/rejeitar`, RAZAO],
      ["/relatos/relato-1/apagar", RAZAO],
      ["/contas/rui/bloquear", RAZAO],
      ["/contas/apagar", { email: "rui@exemplo.pt" }],
    ] as [string, Record<string, string>][]) {
      for (const origem of ["https://outro.test", null, "null"]) {
        expect((await pedirAdmin(caminho, { metodo: "POST", corpo, origem, segredos: PURGA })).status, `${caminho} ${origem}`).toBe(403);
      }
      // Nem sem o token do Access.
      expect((await pedirAdmin(caminho, { metodo: "POST", corpo, jwt: null, segredos: PURGA })).status, caminho).toBe(403);
    }
    expect(await linhaFoto(id)).toEqual({ estado: "pendente", chave, decidida_em: null });
    expect(await contar("relatos")).toBe(1);
    expect(await contar("bloqueios")).toBe(0);
    expect(await contar("contas")).toBe(2);
    expect(await decisoes()).toEqual([]);
    expect(mensagensEnviadas).toEqual([]);
  });

  it("paginaTemOsBotoes", async () => {
    const { id } = await fotoPendente();
    await relatoDoRui();
    await denunciaDaAna("denuncia-1", "relato:relato-1");
    await env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k1', 'CM:9', 'rui', 'nome', 'Largo Novo', ?)").bind(INICIO).run();
    await env.DB.prepare("INSERT INTO bloqueios (email_hash, motivo, criado_em) VALUES ('hash-1', 'Relatos falsos', ?)").bind(INICIO).run();
    await env.DB.prepare("INSERT INTO emails_por_enviar (id, para, assunto, texto, criado_em) VALUES ('email-1', 'ze@exemplo.pt', 'A', 'B', ?)").bind(INICIO).run();

    const html = await pagina();
    for (const acaoDoFormulario of [
      `/fotos/${id}/aprovar`, `/fotos/${id}/rejeitar`, "/contas/rui/bloquear",
      "/correcoes/k1/aprovar", "/correcoes/k1/rejeitar",
      "/denuncias/denuncia-1/decidir", "/relatos/relato-1/apagar",
      "/bloqueios/hash-1/desbloquear", "/emails/email-1/enviar", "/contas/apagar",
    ]) {
      expect(html, acaoDoFormulario).toContain(`<form method="post" action="${acaoDoFormulario}"`);
    }
    for (const regra of Object.keys(REGRAS)) expect(html, regra).toContain(`<option value="${regra}">`);
    expect(html).toContain('name="denuncia" value="denuncia-1"');
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

    // Uma foto aprovada denunciada sai do R2 e da cache.
    mensagensEnviadas.length = 0;
    const { id, chave } = await fotoPendente();
    voltou(await acao(`/fotos/${id}/aprovar`));
    await denunciaDaAna("denuncia-2", `foto:${id}`, "dados_pessoais");
    voltou(await acao("/denuncias/denuncia-2/decidir", { ...RAZAO, acao: "remover" }));
    expect(await chavesNoR2(env.FOTOS)).toEqual([]);
    expect(purges()).toEqual([[`https://fotos.pixellusitano.com/${chave}`]]);
    expect((await decisoes()).at(-1)).toMatchObject({ acao: "foto_apagada", conteudo: `foto:${id}`, denuncia_id: "denuncia-2" });
    expect(mensagensEnviadas.map((m) => m.para).sort()).toEqual(["ana@exemplo.pt", "rui@exemplo.pt"]);
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

  it("adminApagaContaPorEmailLimpaR2EACache", async () => {
    const pendente = await fotoPendente(rui, "CM:1", 1);
    await env.FOTOS.put("aprovada-do-rui.jpg", jpeg(9));
    await env.DB.batch([
      env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em) VALUES ('f-aprovada', 'CM:2', 'rui', 'aprovada-do-rui.jpg', 'aprovada', ?)").bind(INICIO - 2 * DIA),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('k1', 'CM:1', 'rui', 'nome', 'Outro', ?)").bind(INICIO),
    ]);
    await relatoDoRui();
    const daAna = await fotoPendente(ana, "CM:3", 3);

    // Do pedido feito em apagar-conta.html: o email como a pessoa o escreveu.
    voltou(await acao("/contas/apagar", { email: " RUI@Exemplo.pt " }), "conta_apagada");

    for (const tabela of ["relatos", "fotos", "correcoes", "sessoes", "aceites_termos"]) expect(await contar(`${tabela} WHERE conta_id = 'rui'`), tabela).toBe(0);
    expect(await contar("contas WHERE id = 'rui'")).toBe(0);
    expect(await contar("confirmacoes")).toBe(0);
    expect(await chavesNoR2(env.FOTOS)).toEqual([]);
    expect(await chavesNoR2(env.FOTOS_PENDENTES)).toEqual([daAna.chave]);
    expect(await chavesNoR2(env.FOTOS_PENDENTES)).not.toContain(pendente.chave);
    expect(purges()).toEqual([["https://fotos.pixellusitano.com/aprovada-do-rui.jpg"]]);
    // Sem decisão nem email: o autor responde ao pedido.
    expect(await decisoes()).toEqual([]);
    expect(mensagensEnviadas).toEqual([]);
    expect(await contar("contas WHERE id = 'ana'")).toBe(1);

    voltou(await acao("/contas/apagar", { email: "ninguem@exemplo.pt" }), "conta_nao_existe");
    voltou(await acao("/contas/apagar", { email: "rui@exemplo.pt" }), "conta_nao_existe");

    // O R2 em baixo: nada se apaga e o autor sabe.
    const r2EmBaixo = {
      delete: async () => {
        throw new Error("R2 em baixo");
      },
    } as unknown as R2Bucket;
    voltou(await acao("/contas/apagar", { email: "ana@exemplo.pt" }, { FOTOS: r2EmBaixo }), "apagar_falhou");
    expect(await contar("contas WHERE id = 'ana'")).toBe(1);
    expect(await contar("fotos WHERE conta_id = 'ana'")).toBe(1);
    expect(await pagina("/?aviso=apagar_falhou")).toContain(AVISOS.apagar_falhou);
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
    const { id } = await fotoPendente();
    await env.DB.prepare("INSERT INTO contadores (dia, nome, quantos) VALUES (?, 'emails', 30)").bind(HOJE).run();

    voltou(await acao(`/fotos/${id}/rejeitar`, RAZAO), "email_falhou");

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
