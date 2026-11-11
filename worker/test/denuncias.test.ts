import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { enviarOuGuardar, mensagensEnviadas, REMETENTE_AVISOS } from "../src/email";
import { decisaoDaDenuncia, explicacao, REGRAS } from "../src/explicacoes";
import { criarConta, limitador, LIGADO, pedir, sha256 } from "./ajudas";

const DIA = 24 * 60 * 60_000;
const INICIO = Date.UTC(2026, 10, 3, 8, 0);

let ana: string;

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(INICIO);
  mensagensEnviadas.length = 0;
  ana = await criarConta("ana", "ana@exemplo.pt");
  await criarConta("rui", "rui@exemplo.pt");
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

function hoje(): string {
  return new Date(Date.now()).toISOString().slice(0, 10);
}

function denunciar(token: string | undefined, corpo: unknown, segredos: Partial<Env> = LIGADO): Promise<Response> {
  return pedir("/api/denuncias", { token, corpo, segredos });
}

/** Um relato do rui (cada um numa paragem sua, por causa do índice dos relatos ativos). */
async function relato(id: string, estado = "ativo"): Promise<string> {
  await env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, conta_id, criado_em, expira_em, estado) VALUES (?1, 'vai_cheio', ?2, 'rui', ?3, ?4, ?5)")
    .bind(id, `CM:${id}`, Date.now(), Date.now() + 30 * 60_000, estado)
    .run();
  return `relato:${id}`;
}

function corpo(conteudo: string, extra: Record<string, unknown> = {}): Record<string, unknown> {
  return { conteudo, motivo: "falso", nome: "Ana", boaFe: true, ...extra };
}

async function linhas(tabela: "denuncias" | "emails_por_enviar"): Promise<number> {
  return (await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${tabela}`).first<number>("n"))!;
}

async function erroDe(resposta: Response): Promise<{ estado: number; erro: string }> {
  return { estado: resposta.status, erro: ((await resposta.json()) as { erro: string }).erro };
}

describe("denúncias e recibo", () => {
  it("denunciaGuardaEMandaRecibo", async () => {
    const conteudo = await relato("r1");
    expect((await denunciar(ana, corpo(conteudo, { motivo: "ofensivo", explicacao: "  Está lá escrito um insulto.  " }))).status).toBe(204);

    expect(await env.DB.prepare("SELECT conteudo, motivo, explicacao, nome, email, email_hash, criada_em, decidida_em FROM denuncias").all()).toMatchObject({
      results: [
        {
          conteudo,
          motivo: "ofensivo",
          explicacao: "Está lá escrito um insulto.",
          nome: "Ana",
          email: "ana@exemplo.pt",
          email_hash: await sha256("ana@exemplo.pt"),
          criada_em: INICIO,
          decidida_em: null,
        },
      ],
    });
    expect(mensagensEnviadas).toHaveLength(1);
    expect(mensagensEnviadas.at(-1)).toMatchObject({ para: "ana@exemplo.pt", assunto: "Recebemos a tua denúncia" });
    expect(mensagensEnviadas.at(-1)!.texto).toContain("112");
    expect(mensagensEnviadas.at(-1)!.texto).toContain(conteudo);
  });

  it("reciboVaiSoParaOEmailDaConta", async () => {
    // Nenhum campo do pedido escolhe o destinatário: o recibo é sempre para o email da conta que denuncia.
    const conteudo = await relato("r1");
    const resposta = await denunciar(ana, corpo(conteudo, { email: "vitima@exemplo.pt", para: "vitima@exemplo.pt", to: ["vitima@exemplo.pt"], cc: "vitima@exemplo.pt", bcc: "vitima@exemplo.pt" }));
    expect(resposta.status).toBe(204);
    expect(mensagensEnviadas.map((m) => m.para)).toEqual(["ana@exemplo.pt"]);
    expect(mensagensEnviadas[0].assunto).toBe("Recebemos a tua denúncia");
    expect((await env.DB.prepare("SELECT email FROM denuncias").first<string>("email"))!).toBe("ana@exemplo.pt");
  });

  it("mesmaDenunciaNaoMandaOutroRecibo", async () => {
    const conteudo = await relato("r1");
    expect((await denunciar(ana, corpo(conteudo))).status).toBe(204);
    expect((await denunciar(ana, corpo(conteudo, { motivo: "outro" }))).status).toBe(204);
    expect(await linhas("denuncias")).toBe(1);
    expect(mensagensEnviadas).toHaveLength(1);
  });

  it("semBoaFeOuSemNomeDa400", async () => {
    const conteudo = await relato("r1");
    const estragados: Record<string, unknown>[] = [
      corpo(conteudo, { boaFe: false }),
      corpo(conteudo, { boaFe: "true" }),
      corpo(conteudo, { boaFe: undefined }),
      corpo(conteudo, { nome: "" }),
      corpo(conteudo, { nome: "   " }),
      corpo(conteudo, { nome: "a".repeat(81) }),
      corpo(conteudo, { nome: 7 }),
      corpo(conteudo, { nome: "Ana\nBcc: vitima@exemplo.pt" }),
      corpo(conteudo, { motivo: "x" }),
      corpo(conteudo, { motivo: undefined }),
      corpo(conteudo, { explicacao: "a".repeat(501) }),
      corpo(conteudo, { explicacao: 7 }),
      corpo("outra:1"),
      corpo("foto:f1"),
      corpo("relato:<script>"),
      corpo("relato:"),
      corpo(`relato:${"a".repeat(65)}`),
      corpo(7 as unknown as string),
    ];
    for (const estragado of estragados) {
      expect(await erroDe(await denunciar(ana, estragado)), JSON.stringify(estragado)).toEqual({ estado: 400, erro: "pedido_invalido" });
    }
    expect((await pedir("/api/denuncias", { token: ana, corpo: "{isto não é json", segredos: LIGADO })).status).toBe(400);
    expect(await linhas("denuncias")).toBe(0);
    expect(mensagensEnviadas).toHaveLength(0);
    // No limite: nome com 80 caracteres e explicação com 500 passam.
    expect((await denunciar(ana, corpo(conteudo, { nome: "a".repeat(80), explicacao: "b".repeat(500) }))).status).toBe(204);
  });

  it("conteudoQueNaoExisteDa404", async () => {
    expect(await erroDe(await denunciar(ana, corpo("relato:nao-existe")))).toEqual({ estado: 404, erro: "nao_existe" });
    expect(await linhas("denuncias")).toBe(0);
    expect(mensagensEnviadas).toHaveLength(0);
  });

  it("relatoEmQualquerEstadoPodeSerDenunciado", async () => {
    expect((await denunciar(ana, corpo(await relato("r1", "terminado")))).status).toBe(204);
    expect((await denunciar(ana, corpo(await relato("r2")))).status).toBe(204);
    expect(await linhas("denuncias")).toBe(2);
  });

  it("contaBloqueadaPodeDenunciar", async () => {
    const conteudo = await relato("r1");
    await env.DB.prepare("INSERT INTO bloqueios (email_hash, motivo, criado_em) VALUES (?, 'teste', ?)").bind(await sha256("ana@exemplo.pt"), Date.now()).run();
    // Nem os termos são pedidos: quem denuncia pode ainda não os ter aceitado.
    const teo = await criarConta("teo", "teo@exemplo.pt", { termos: "0" });
    expect((await denunciar(ana, corpo(conteudo))).status).toBe(204);
    expect((await denunciar(teo, corpo(conteudo))).status).toBe(204);
    expect(await linhas("denuncias")).toBe(2);
    expect(mensagensEnviadas.map((m) => m.para)).toEqual(["ana@exemplo.pt", "teo@exemplo.pt"]);
  });

  it("denunciarSoComAComunidadeLigada", async () => {
    const conteudo = await relato("r1");
    expect(await erroDe(await denunciar(ana, corpo(conteudo), {}))).toEqual({ estado: 403, erro: "desligado" });
    const so = (funcoes: object): Partial<Env> => ({ ...LIGADO, TESTE_INTERRUPTORES: JSON.stringify({ comunidade: false, correcoes: false, ...funcoes }) });
    expect((await denunciar(ana, corpo(conteudo), so({}))).status).toBe(403);
    expect((await denunciar(ana, corpo(conteudo), so({ correcoes: true }))).status).toBe(403);
    expect((await denunciar(ana, corpo(conteudo), so({ comunidade: true }))).status).toBe(204);
    expect(await linhas("denuncias")).toBe(1);
  });

  it("semSessaoDa401EDevagarDa429", async () => {
    const conteudo = await relato("r1");
    expect(await erroDe(await denunciar(undefined, corpo(conteudo)))).toEqual({ estado: 401, erro: "sem_sessao" });
    expect(await erroDe(await denunciar(ana, corpo(conteudo), { ...LIGADO, LIMITADOR: limitador(0) }))).toEqual({ estado: 429, erro: "devagar" });
    expect(await linhas("denuncias")).toBe(0);
  });

  it("sextaDenunciaNoDiaDa429", async () => {
    const conteudos: string[] = [];
    for (let i = 1; i <= 6; i++) conteudos.push(await relato(`r${i}`));
    for (const conteudo of conteudos.slice(0, 5)) expect((await denunciar(ana, corpo(conteudo))).status).toBe(204);
    expect(mensagensEnviadas).toHaveLength(5);

    expect(await erroDe(await denunciar(ana, corpo(conteudos[5])))).toEqual({ estado: 429, erro: "muitos_pedidos" });
    expect(mensagensEnviadas).toHaveLength(5);
    expect(await linhas("denuncias")).toBe(5);
    // Repetir uma já feita continua a dar 204 (e nenhum recibo novo), mesmo com o limite cheio.
    expect((await denunciar(ana, corpo(conteudos[0]))).status).toBe(204);
    expect(mensagensEnviadas).toHaveLength(5);

    vi.setSystemTime(Date.now() + DIA + 1);
    expect((await denunciar(ana, corpo(conteudos[5]))).status).toBe(204);
    expect(mensagensEnviadas).toHaveLength(6);
  });

  it("outraContaNaoGastaOLimiteDaAna", async () => {
    const rui = await criarConta("rui2", "rui2@exemplo.pt");
    const conteudos: string[] = [];
    for (let i = 1; i <= 6; i++) conteudos.push(await relato(`r${i}`));
    for (const conteudo of conteudos.slice(0, 5)) expect((await denunciar(ana, corpo(conteudo))).status).toBe(204);
    expect((await denunciar(rui, corpo(conteudos[5]))).status).toBe(204);
  });

  it("denunciaDePerigoAvisaOAutorLogo", async () => {
    const segredos = { ...LIGADO, EMAIL_AUTOR: "autor@exemplo.pt" };
    expect((await denunciar(ana, corpo(await relato("r1"), { motivo: "perigo" }), segredos)).status).toBe(204);
    expect(mensagensEnviadas.map((m) => m.para)).toEqual(["ana@exemplo.pt", "autor@exemplo.pt"]);
    expect(mensagensEnviadas[1].assunto).toBe("Paragem Certa: denúncia de perigo");
    expect(mensagensEnviadas[1].texto).toContain("perigo");
    // As duas contam para os 30 do dia.
    expect(await env.DB.prepare("SELECT quantos FROM contadores WHERE dia = ? AND nome = 'emails'").bind(hoje()).first<number>("quantos")).toBe(2);

    mensagensEnviadas.length = 0;
    expect((await denunciar(ana, corpo(await relato("r2"), { motivo: "falso" }), segredos)).status).toBe(204);
    expect(mensagensEnviadas.map((m) => m.para)).toEqual(["ana@exemplo.pt"]);

    // Sem EMAIL_AUTOR, só o recibo.
    mensagensEnviadas.length = 0;
    expect((await denunciar(ana, corpo(await relato("r3"), { motivo: "perigo" }))).status).toBe(204);
    expect(mensagensEnviadas.map((m) => m.para)).toEqual(["ana@exemplo.pt"]);
  });
});

describe("teto de emails e Resend em baixo", () => {
  it("tetoDeEmailsGuardaParaDepois", async () => {
    await env.DB.prepare("INSERT INTO contadores (dia, nome, quantos) VALUES (?, 'emails', 30)").bind(hoje()).run();
    const conteudo = await relato("r1");
    expect((await denunciar(ana, corpo(conteudo))).status).toBe(204);

    expect(mensagensEnviadas).toHaveLength(0);
    expect(await env.DB.prepare("SELECT para, assunto, texto, criado_em FROM emails_por_enviar").all()).toMatchObject({
      results: [{ para: "ana@exemplo.pt", assunto: "Recebemos a tua denúncia", texto: expect.stringContaining(conteudo), criado_em: INICIO }],
    });
    // O teto não sobe acima de 30 e a denúncia ficou guardada.
    expect(await env.DB.prepare("SELECT quantos FROM contadores WHERE dia = ? AND nome = 'emails'").bind(hoje()).first<number>("quantos")).toBe(30);
    expect(await linhas("denuncias")).toBe(1);
  });

  it("oTetoRenovaNoDiaSeguinte", async () => {
    await env.DB.prepare("INSERT INTO contadores (dia, nome, quantos) VALUES (?, 'emails', 30)").bind(hoje()).run();
    const semResend = { ...env, RESEND_API_KEY: "" } as Env;
    expect(await enviarOuGuardar(semResend, "ana@exemplo.pt", "A", "a")).toBe("guardado");
    vi.setSystemTime(Date.now() + DIA);
    expect(await enviarOuGuardar(semResend, "ana@exemplo.pt", "B", "b")).toBe("enviado");
    expect(mensagensEnviadas.map((m) => m.assunto)).toEqual(["B"]);
  });

  it("resendEmBaixoGuardaParaDepois", async () => {
    // Direto, e não pela rota: com a chave do Resend os interruptores de teste deixam de valer.
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response('{"message":"erro"}', { status: 500 }));
    const com = { ...env, RESEND_API_KEY: "chave-de-teste" } as Env;
    expect(await enviarOuGuardar(com, "ana@exemplo.pt", "Recebemos a tua denúncia", "Olá Ana")).toBe("guardado");

    expect(fetchFalso).toHaveBeenCalledTimes(1);
    expect(await env.DB.prepare("SELECT para, assunto, texto FROM emails_por_enviar").all()).toMatchObject({
      results: [{ para: "ana@exemplo.pt", assunto: "Recebemos a tua denúncia", texto: "Olá Ana" }],
    });
  });

  it("resendFeitoEnviaComORemetenteDosAvisos", async () => {
    const fetchFalso = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response('{"id":"x"}', { status: 200 }));
    const com = { ...env, RESEND_API_KEY: "chave-de-teste" } as Env;
    expect(await enviarOuGuardar(com, "ana@exemplo.pt", "Assunto", "Texto")).toBe("enviado");

    const corpoEnviado = JSON.parse(String(fetchFalso.mock.calls[0][1]?.body));
    expect(corpoEnviado).toMatchObject({ from: REMETENTE_AVISOS, to: ["ana@exemplo.pt"], subject: "Assunto", text: "Texto" });
    expect(REMETENTE_AVISOS).toBe("Paragem Certa <avisos@mail.pixellusitano.com>");
    expect(await linhas("emails_por_enviar")).toBe(0);
  });

  it("codigosNaoGastamOTetoDosOutros", async () => {
    await env.DB.prepare("INSERT INTO envios_email (dia, quantos) VALUES (?, 70)").bind(hoje()).run();
    expect((await denunciar(ana, corpo(await relato("r1")))).status).toBe(204);
    expect(mensagensEnviadas).toHaveLength(1);
    expect(await linhas("emails_por_enviar")).toBe(0);
  });
});

describe("textos das explicações", () => {
  it("explicacaoTemTudoOQueADsaPede", () => {
    const { assunto, texto } = explicacao({ oQue: "Apagámos o teu relato", duracao: "para sempre", porque: "Estava errado", regra: REGRAS.so_o_que_viste, deDenuncia: true });
    expect(assunto).toBe("Decisão sobre o teu conteúdo na Paragem Certa");
    for (const parte of ["O que fizemos", "Porquê", "Regra em causa", "denúncia", "decisão automática", "contact@pixellusitano.com", "ANACOM", "tribunais"]) {
      expect(texto, parte).toContain(parte);
    }
    expect(texto).toContain("Apagámos o teu relato, para sempre, em toda a app.");
    expect(texto).toContain("A decisão veio de uma denúncia de outra pessoa.");
    expect(texto).toContain(REGRAS.so_o_que_viste);
    // As regras acabam em ponto: não pode sair "..".
    expect(texto).not.toContain("..");
  });

  it("explicacaoSemDenunciaDizQueFoiDoAutor", () => {
    const { texto } = explicacao({ oQue: "Rejeitámos a tua correção", duracao: "para sempre", porque: "Descrevia o que não existe.", regra: REGRAS.lei, deDenuncia: false });
    expect(texto).toContain("A decisão foi do autor da Paragem Certa, sem denúncia.");
    expect(texto).not.toContain("de outra pessoa");
    expect(texto).not.toContain("..");
  });

  it("regrasTemTodosOsIdsDoPlano", () => {
    expect(Object.keys(REGRAS).sort()).toEqual(["correcoes_verdadeiras", "falso_ou_varias_contas", "lei", "so_o_que_viste"]);
    for (const texto of Object.values(REGRAS)) expect(texto.length).toBeGreaterThan(10);
  });

  it("decisaoDaDenunciaDizOQueFoiFeito", () => {
    const removida = decisaoDaDenuncia({ nome: "Ana", conteudo: "relato:r1", removido: true, porque: "Era falso" });
    expect(removida.assunto).toBe("Decisão sobre a tua denúncia");
    expect(removida.texto).toContain("Olá Ana,");
    expect(removida.texto).toContain("relato:r1");
    expect(removida.texto).toContain("Removemos o conteúdo.");
    expect(removida.texto).toContain("Era falso");
    const mantida = decisaoDaDenuncia({ nome: "Ana", conteudo: "relato:r1", removido: false, porque: "Estava certo" });
    expect(mantida.texto).toContain("Mantivemos o conteúdo.");
    expect(mantida.texto).not.toContain("Removemos");
    for (const { texto } of [removida, mantida]) {
      for (const parte of ["decisão automática", "contact@pixellusitano.com", "ANACOM", "tribunais"]) expect(texto, parte).toContain(parte);
    }
  });
});
