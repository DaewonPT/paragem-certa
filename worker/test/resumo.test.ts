import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { mensagensEnviadas } from "../src/email";
import { resumoAoAutor } from "../src/resumo";
import { correrCron } from "./ajudas";

const AGORA = Date.UTC(2026, 10, 3, 3, 0);
const HOJE = "2026-11-03";
const AUTOR = "autor@exemplo.pt";
const ASSUNTO = "Paragem Certa: há coisas por rever";

function ambiente(segredos: Partial<Env> = {}): Env {
  return { ...env, RESEND_API_KEY: undefined, EMAIL_AUTOR: AUTOR, ...segredos } as Env;
}

beforeEach(async () => {
  mensagensEnviadas.length = 0;
  await env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES ('rui', 'rui@exemplo.pt', 1)").run();
});

describe("resumo diário ao autor", () => {
  it("resumoSoComAlgoPorRever", async () => {
    // Já decidido não conta: uma foto aprovada, uma rejeitada, uma correção aprovada e uma denúncia decidida.
    await env.DB.batch([
      env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em) VALUES ('f1', 'CM:1', 'rui', 'a.jpg', 'aprovada', 1), ('f2', 'CM:2', 'rui', NULL, 'rejeitada', 1)"),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, estado, criada_em, decidida_em) VALUES ('k1', 'CM:1', 'rui', 'ja_nao_existe', 'aprovada', 1, 1)"),
      env.DB.prepare("INSERT INTO denuncias (id, conteudo, motivo, email_hash, criada_em, decidida_em) VALUES ('d1', 'foto:f1', 'perigo', 'h', 1, 1)"),
    ]);
    await resumoAoAutor(ambiente(), AGORA);
    expect(mensagensEnviadas).toEqual([]);

    await env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em) VALUES ('f3', 'CM:3', 'rui', 'b.jpg', 'pendente', 1)").run();
    // Sem EMAIL_AUTOR não há a quem mandar.
    await resumoAoAutor(ambiente({ EMAIL_AUTOR: undefined }), AGORA);
    await resumoAoAutor(ambiente({ EMAIL_AUTOR: "  " }), AGORA);
    expect(mensagensEnviadas).toEqual([]);

    await resumoAoAutor(ambiente(), AGORA);
    expect(mensagensEnviadas).toHaveLength(1);
    expect(mensagensEnviadas[0]).toMatchObject({ para: AUTOR, assunto: ASSUNTO });
    expect(mensagensEnviadas[0].texto).toContain("1 foto por rever");
    expect(mensagensEnviadas[0].texto).not.toContain("correç");
    // Conta para o teto dos 30 do dia.
    expect(await env.DB.prepare("SELECT quantos FROM contadores WHERE dia = ? AND nome = 'emails'").bind(HOJE).first("quantos")).toBe(1);
  });

  it("resumoAvisaDenunciaDePerigo", async () => {
    await env.DB.batch([
      env.DB.prepare("INSERT INTO denuncias (id, conteudo, motivo, email_hash, criada_em) VALUES ('d1', 'relato:r1', 'perigo', 'h1', 1), ('d2', 'relato:r2', 'falso', 'h2', 1)"),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, criada_em) VALUES ('k1', 'CM:1', 'rui', 'ja_nao_existe', 1), ('k2', 'CM:2', 'rui', 'ja_nao_existe', 1)"),
      env.DB.prepare("INSERT INTO emails_por_enviar (id, para, assunto, texto, criado_em) VALUES ('e1', 'rui@exemplo.pt', 'A', 'B', 1)"),
    ]);

    await resumoAoAutor(ambiente(), AGORA);

    expect(mensagensEnviadas).toHaveLength(1);
    const { texto } = mensagensEnviadas[0];
    for (const parte of ["2 denúncias abertas", "1 de perigo", "2 correções por rever", "1 email por enviar"]) expect(texto, parte).toContain(parte);
    expect(texto).not.toContain("foto");
    // Nada de quem enviou nem de quem denunciou.
    expect(texto).not.toContain("rui");
  });

  it("cronMandaOResumo", async () => {
    // Criada a essa hora, para os prazos do mesmo Cron não a apagarem.
    await env.DB.prepare("INSERT INTO fotos (id, paragem, conta_id, chave, estado, criada_em) VALUES ('f1', 'CM:1', 'rui', 'a.jpg', 'pendente', ?)").bind(AGORA).run();

    await correrCron({ agora: AGORA, segredos: { EMAIL_AUTOR: AUTOR } });

    expect(mensagensEnviadas).toEqual([expect.objectContaining({ para: AUTOR, assunto: ASSUNTO })]);
    // A hora vem do controlador do Cron: o lugar gasto é o desse dia.
    expect(await env.DB.prepare("SELECT quantos FROM contadores WHERE dia = ? AND nome = 'emails'").bind(HOJE).first("quantos")).toBe(1);
  });
});
