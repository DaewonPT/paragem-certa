import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { interruptores, marcaDoEmail, normalizarEmail, portaoDeEscrita, quemEscreve } from "../src/portao";
import { criarConta, LIGADO, limitador, pedidoComToken, pedir, sha256 } from "./ajudas";

/** O ambiente de teste com as três funções ligadas; cada teste muda o que precisa. */
function ligado(extra: Partial<Env> = {}): Env {
  return { ...env, ...LIGADO, ...extra } as Env;
}

async function bloquear(emailHash: string): Promise<void> {
  await env.DB.prepare("INSERT INTO bloqueios (email_hash, motivo, criado_em) VALUES (?, 'teste', ?)").bind(emailHash, Date.now()).run();
}

/** O que o portão devolveu quando recusou: o estado e o código do erro. */
async function recusa(resultado: unknown): Promise<{ estado: number; erro: string }> {
  expect(resultado).toBeInstanceOf(Response);
  const resposta = resultado as Response;
  return { estado: resposta.status, erro: ((await resposta.json()) as { erro: string }).erro };
}

describe("portão das escritas da comunidade", () => {
  it("desligadoDa403", async () => {
    const token = await criarConta("ana", "ana@exemplo.pt");
    const resultado = await portaoDeEscrita(pedidoComToken(token), ligado({ TESTE_INTERRUPTORES: undefined }), "comunidade");
    expect(await recusa(resultado)).toEqual({ estado: 403, erro: "desligado" });
  });

  it("cadaFuncaoTemOSeuInterruptor", async () => {
    const token = await criarConta("ana", "ana@exemplo.pt");
    // Os seis explícitos: o teste não depende do que estiver ligado no config.json.
    const soAsFotos = ligado({
      TESTE_INTERRUPTORES: JSON.stringify({ conta: false, comunidade: false, fotos: true, correcoes: false, carrisAoVivo: false, metro: false }),
    });
    expect(await portaoDeEscrita(pedidoComToken(token), soAsFotos, "fotos")).toMatchObject({ contaId: "ana" });
    for (const funcao of ["comunidade", "correcoes"] as const) {
      expect(await recusa(await portaoDeEscrita(pedidoComToken(token), soAsFotos, funcao)), funcao).toEqual({ estado: 403, erro: "desligado" });
    }
    expect(interruptores(soAsFotos)).toEqual({ conta: false, comunidade: false, fotos: true, correcoes: false, carrisAoVivo: false, metro: false });
  });

  it("interruptoresDeTesteForaDoAmbienteDeTesteSaoIgnorados", async () => {
    const token = await criarConta("ana", "ana@exemplo.pt");
    const foraDoTeste: Partial<Env>[] = [{ AMBIENTE: undefined }, { AMBIENTE: "producao" }, { RESEND_API_KEY: "x" }];
    for (const extra of foraDoTeste) {
      const resultado = await portaoDeEscrita(pedidoComToken(token), ligado(extra), "comunidade");
      expect(await recusa(resultado), JSON.stringify(extra)).toEqual({ estado: 403, erro: "desligado" });
    }
  });

  it("semSessaoDa401", async () => {
    await criarConta("ana", "ana@exemplo.pt");
    for (const token of [undefined, "token-que-nao-existe"]) {
      const resultado = await portaoDeEscrita(pedidoComToken(token), ligado(), "comunidade");
      expect(await recusa(resultado), String(token)).toEqual({ estado: 401, erro: "sem_sessao" });
    }
  });

  it("decimaPrimeiraEscritaNoMinutoDa429", async () => {
    const token = await criarConta("ana", "ana@exemplo.pt");
    const limite = limitador(10);
    const segredos = ligado({ LIMITADOR: limite });
    for (let escrita = 1; escrita <= 10; escrita++) {
      expect(await portaoDeEscrita(pedidoComToken(token), segredos, "comunidade"), `escrita ${escrita}`).toMatchObject({ contaId: "ana" });
    }
    const decimaPrimeira = await portaoDeEscrita(pedidoComToken(token), segredos, "comunidade");
    expect(await recusa(decimaPrimeira)).toEqual({ estado: 429, erro: "devagar" });
    expect(limite.chaves).toEqual(Array(11).fill("ana"));
  });

  it("contaBloqueadaDa403", async () => {
    const token = await criarConta("ana", "ana@exemplo.pt");
    const tokenDoRui = await criarConta("rui", "rui@exemplo.pt");
    await bloquear(await sha256("ana@exemplo.pt"));

    const bloqueada = await portaoDeEscrita(pedidoComToken(token), ligado(), "comunidade");
    expect(await recusa(bloqueada)).toEqual({ estado: 403, erro: "conta_bloqueada" });
    // Só a Ana: o bloqueio é por pessoa, não por servidor.
    expect(await portaoDeEscrita(pedidoComToken(tokenDoRui), ligado(), "comunidade")).toMatchObject({ contaId: "rui" });

    // O bloqueio vem antes dos termos: quem está bloqueado e nem aceitou os termos vê o bloqueio.
    const tokenSemTermos = await criarConta("sem-termos", "ana@exemplo.pt", { termos: "0" });
    const semTermos = await portaoDeEscrita(pedidoComToken(tokenSemTermos), ligado(), "comunidade");
    expect(await recusa(semTermos)).toEqual({ estado: 403, erro: "conta_bloqueada" });
  });

  it("bloqueioSobreviveAApagarAConta", async () => {
    const token = await criarConta("ana", "ana@exemplo.pt");
    await bloquear(await sha256("ana@exemplo.pt"));

    expect((await pedir("/api/conta", { metodo: "DELETE", token })).status).toBe(204);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM contas WHERE id = 'ana'").first("n")).toBe(0);

    const novoToken = await criarConta("ana-outra-vez", "ana@exemplo.pt");
    const resultado = await portaoDeEscrita(pedidoComToken(novoToken), ligado(), "comunidade");
    expect(await recusa(resultado)).toEqual({ estado: 403, erro: "conta_bloqueada" });
  });

  it("bloqueioApanhaEnderecoComMais", async () => {
    await bloquear(await marcaDoEmail("ana@gmail.com"));
    await bloquear(await marcaDoEmail("rui+x@exemplo.pt"));

    // Maiúsculas, pontos, "+etiqueta" e googlemail.com: tudo a mesma caixa de correio.
    const variantes: [string, string][] = [
      ["a1", "A.Na+2@googlemail.com"],
      ["a2", "  ana@GMAIL.com "],
      ["a3", "an.a@gmail.com"],
      ["r1", "rui@exemplo.pt"],
      ["r2", "Rui+outra@exemplo.pt"],
    ];
    for (const [id, email] of variantes) {
      const token = await criarConta(id, email);
      const resultado = await portaoDeEscrita(pedidoComToken(token), ligado(), "comunidade");
      expect(await recusa(resultado), email).toEqual({ estado: 403, erro: "conta_bloqueada" });
    }

    // Fora do Gmail os pontos contam (a.na@ e ana@ podem ser pessoas diferentes), e outro endereço não é apanhado.
    for (const [id, email] of [["o1", "a.na@exemplo.pt"], ["o2", "ana2@gmail.com"], ["o3", "rui@gmail.com"]]) {
      const token = await criarConta(id, email);
      expect(await portaoDeEscrita(pedidoComToken(token), ligado(), "comunidade"), email).toMatchObject({ contaId: id });
    }
  });

  it("normalizarEmailSegueAsRegras", async () => {
    const casos: [string, string][] = [
      ["Ana@Exemplo.PT", "ana@exemplo.pt"],
      ["  ana@exemplo.pt\n", "ana@exemplo.pt"],
      ["ana+etiqueta+outra@exemplo.pt", "ana@exemplo.pt"],
      ["a.na@exemplo.pt", "a.na@exemplo.pt"],
      ["A.Na+2@gmail.com", "ana@gmail.com"],
      ["a.n.a@googlemail.com", "ana@gmail.com"],
      ["ana@gmail.com.pt", "ana@gmail.com.pt"],
      ["a.na@meugmail.com", "a.na@meugmail.com"],
    ];
    for (const [entrada, esperado] of casos) expect(normalizarEmail(entrada), entrada).toBe(esperado);
    expect(await marcaDoEmail("A.Na+2@googlemail.com")).toBe(await sha256("ana@gmail.com"));
  });

  it("termosAntigosNaoChegam", async () => {
    const token = await criarConta("ana", "ana@exemplo.pt", { termos: "0" });
    const recusada = await portaoDeEscrita(pedidoComToken(token), ligado(), "comunidade");
    expect(await recusa(recusada)).toEqual({ estado: 403, erro: "termos_por_aceitar" });

    expect((await pedir("/api/termos", { token, corpo: { versao: "1" } })).status).toBe(204);
    expect(await portaoDeEscrita(pedidoComToken(token), ligado(), "comunidade")).toMatchObject({ contaId: "ana" });
  });

  it("tudoCertoDevolveQuemEscreve", async () => {
    const token = await criarConta("ana", "ana@exemplo.pt");
    const resultado = await portaoDeEscrita(pedidoComToken(token), ligado(), "comunidade");
    expect(resultado).toEqual({ contaId: "ana", email: "ana@exemplo.pt", emailHash: await sha256("ana@exemplo.pt") });
  });

  it("quemEscreveNaoPedeInterruptorNemTermos", async () => {
    // As denúncias usam só `quemEscreve`: uma conta bloqueada, sem os termos novos, pode denunciar.
    const token = await criarConta("ana", "Ana+x@exemplo.pt", { termos: "0" });
    await bloquear(await sha256("ana@exemplo.pt"));
    const semInterruptores = ligado({ TESTE_INTERRUPTORES: undefined });
    expect(await quemEscreve(pedidoComToken(token), semInterruptores)).toEqual({
      contaId: "ana",
      email: "Ana+x@exemplo.pt",
      emailHash: await sha256("ana@exemplo.pt"),
    });
    expect(await recusa(await quemEscreve(pedidoComToken(), semInterruptores))).toEqual({ estado: 401, erro: "sem_sessao" });
  });
});
