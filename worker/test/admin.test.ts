import { env } from "cloudflare:workers";
import { generateKeyPair } from "jose";
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from "vitest";
import { criarConta, pedir } from "./ajudas";
import { AUTOR, EQUIPA, pedirAdmin, tokenAccess } from "./ajudas-admin";

const DIA = 24 * 60 * 60_000;
const HOSTIL = `<script>x</script>"><img src=x onerror=alert(1)>`;

let fetchEspiado: MockInstance<typeof fetch>;

beforeEach(() => {
  // Nada de internet nos testes: se o pc-admin fosse buscar as chaves do Access, falhava aqui (e fecha a porta).
  fetchEspiado = vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("sem internet nos testes"));
});

afterEach(() => {
  vi.restoreAllMocks();
});

/** O wrangler*.jsonc só com comentários em linhas inteiras, que é como estão escritos. */
function lerJsonc(texto: string): Record<string, any> {
  return JSON.parse(texto.replace(/^\s*\/\/.*$/gm, ""));
}

async function pagina(resposta: Response): Promise<string> {
  expect(resposta.status).toBe(200);
  expect(resposta.headers.get("content-type")).toContain("text/html");
  return resposta.text();
}

async function semAcesso(resposta: Response): Promise<void> {
  expect(resposta.status).toBe(403);
  expect(resposta.headers.get("content-type")).toContain("text/plain");
  const corpo = await resposta.text();
  expect(corpo).not.toContain("Fotos");
  expect(corpo).not.toContain("<");
}

async function conta(id: string, email: string): Promise<void> {
  await env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES (?, ?, ?)").bind(id, email, Date.now() - 10 * DIA).run();
}

describe("pc-admin: o Access", () => {
  it("semCabecalhoDa403", async () => {
    await semAcesso(await pedirAdmin("/", { jwt: null }));
    await semAcesso(await pedirAdmin("/foto/qualquer", { jwt: null }));
    await semAcesso(await pedirAdmin("/", { jwt: "" }));
    await semAcesso(await pedirAdmin("/", { jwt: "isto.nao.e-um-jwt" }));
  });

  it("cookieSemCabecalhoNaoServe", async () => {
    await semAcesso(await pedirAdmin("/", { jwt: null, cabecalhos: { cookie: `CF_Authorization=${await tokenAccess()}` } }));
  });

  it("audienciaErradaDa403", async () => {
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({ aud: ["outra-audiencia"] }) }));
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({ aud: undefined }) }));
  });

  it("emissorErradoDa403", async () => {
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({ iss: "https://outra-equipa.cloudflareaccess.com" }) }));
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({ iss: undefined }) }));
  });

  it("expiradoDa403", async () => {
    const agora = Math.floor(Date.now() / 1000);
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({ iat: agora - 7200, nbf: agora - 7200, exp: agora - 60 }) }));
    // Um token sem `exp` valeria para sempre.
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({ exp: undefined }) }));
  });

  it("assinadoComOutraChaveDa403", async () => {
    const deFora = await generateKeyPair("RS256");
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({}, deFora.privateKey) }));
  });

  it("emailQueNaoEODoAutorDa403", async () => {
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({ email: "outra@exemplo.pt" }) }));
    // Os tokens de serviço do Access não têm email.
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({ email: undefined }) }));
    await semAcesso(await pedirAdmin("/", { jwt: await tokenAccess({ email: `${AUTOR}.outro.pt` }) }));
    // O mesmo endereço noutra caixa é o autor.
    await pagina(await pedirAdmin("/", { jwt: await tokenAccess({ email: AUTOR.toUpperCase() }) }));
  });

  it("semConfiguracaoDoAccessFechaTudo", async () => {
    const jwt = await tokenAccess();
    await pagina(await pedirAdmin("/", { jwt }));
    for (const segredo of ["ACCESS_AUD", "ACCESS_EQUIPA", "EMAIL_AUTOR", "CHAVE_MARCA_EMAIL"] as const) {
      for (const valor of [undefined, "", "  "]) {
        await semAcesso(await pedirAdmin("/", { jwt, segredos: { [segredo]: valor } }));
      }
    }
  });

  it("chavesDeTesteForaDoAmbienteDeTesteSaoIgnoradas", async () => {
    const jwt = await tokenAccess();
    await semAcesso(await pedirAdmin("/", { jwt, segredos: { AMBIENTE: undefined } }));
    await semAcesso(await pedirAdmin("/", { jwt, segredos: { RESEND_API_KEY: "x" } }));
    // Sem as chaves de teste foi às do Access (e falhou, porque os testes não têm internet).
    expect(fetchEspiado.mock.calls.map(([url]) => String(url))).toContain(`${EQUIPA}/cdn-cgi/access/certs`);
  });

  it("postDeOutraOrigemDa403", async () => {
    await semAcesso(await pedirAdmin("/", { metodo: "POST", origem: "https://outro.test", corpo: { a: "1" } }));
    await semAcesso(await pedirAdmin("/", { metodo: "POST", origem: null, corpo: { a: "1" } }));
    await semAcesso(await pedirAdmin("/", { metodo: "POST", origem: "null", corpo: { a: "1" } }));
    // Da mesma origem passa a guarda (e esta rota não existe).
    expect((await pedirAdmin("/", { metodo: "POST", corpo: { a: "1" } })).status).toBe(404);
  });
});

describe("pc-admin: a página", () => {
  it("paginaMostraOQueHaPorRever", async () => {
    const agora = Date.now();
    await criarConta("conta-ana", "ana@exemplo.pt"); // a única sessão usada nos últimos 30 dias
    await conta("conta-rui", "rui@exemplo.pt");
    await conta("conta-eva", "eva@exemplo.pt");
    await conta("conta-teo", "teo@exemplo.pt");
    await env.DB.batch([
      env.DB.prepare("INSERT INTO sessoes (hash, conta_id, criada_em, usada_em) VALUES ('velha', 'conta-rui', ?1, ?1)").bind(agora - 31 * DIA),
      env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES ('relato-rui-1', 'desvio', 'CM:111', '15E', 'conta-rui', ?1, ?2)").bind(agora, agora + DIA),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('correcao-1', 'CM:222', 'conta-eva', 'nome', 'Largo Novo', ?)").bind(agora),
      env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES ('relato-ativo-1', 'desvio', 'CM:333', '736', 'conta-teo', ?1, ?2)").bind(agora, agora + DIA),
      env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, conta_id, criado_em, expira_em) VALUES ('relato-expirado-1', 'elevador', 'CM:444', 'conta-teo', ?1, ?2)").bind(agora - 2 * DIA, agora - DIA),
      env.DB.prepare(
        "INSERT INTO denuncias (id, conteudo, motivo, explicacao, nome, email, email_hash, criada_em) VALUES ('denuncia-falso-1', 'relato:relato-ativo-1', 'falso', 'Não há desvio', 'Ana Denunciante', 'ana@exemplo.pt', 'h1', ?)",
      ).bind(agora - 60_000),
      env.DB.prepare(
        "INSERT INTO denuncias (id, conteudo, motivo, nome, email, email_hash, criada_em) VALUES ('denuncia-perigo-1', 'relato:relato-rui-1', 'perigo', 'Ana Denunciante', 'ana@exemplo.pt', 'h2', ?)",
      ).bind(agora),
      env.DB.prepare(
        "INSERT INTO denuncias (id, conteudo, motivo, nome, email, email_hash, criada_em, decidida_em) VALUES ('denuncia-decidida-1', 'relato:relato-ativo-1', 'ofensivo', NULL, NULL, 'h3', ?1, ?1)",
      ).bind(agora),
      env.DB.prepare("INSERT INTO bloqueios (email_hash, motivo, criado_em) VALUES ('hash-bloqueado-1', 'Relatos falsos', ?)").bind(agora),
      env.DB.prepare("INSERT INTO emails_por_enviar (id, para, assunto, texto, criado_em) VALUES ('email-1', 'ze@exemplo.pt', 'Assunto guardado', 'Texto guardado', ?)").bind(agora),
    ]);

    const html = await pagina(await pedirAdmin("/"));
    expect(html).toContain('lang="pt"');
    for (const texto of [
      "relato-rui-1", "conta-rui", "rui@exemplo.pt",
      "correcao-1", "Largo Novo", "conta-eva", "eva@exemplo.pt",
      "relato-ativo-1", "conta-teo", "teo@exemplo.pt",
      "Não há desvio", "Ana Denunciante", "ana@exemplo.pt",
      "hash-bloqueado-1", "Relatos falsos",
      "email-1", "ze@exemplo.pt", "Assunto guardado", "Texto guardado",
      "Ameaça à vida ou à segurança: comunica à PSP ou à PJ",
      "Contas com sessão usada nos últimos 30 dias: 1",
    ]) {
      expect(html, texto).toContain(texto);
    }
    // As de perigo primeiro, mesmo sendo mais recentes.
    expect(html.indexOf("denuncia-perigo-1")).toBeGreaterThan(-1);
    expect(html.indexOf("denuncia-perigo-1")).toBeLessThan(html.indexOf("denuncia-falso-1"));
    // O conteúdo denunciado resolve-se para a conta de quem o enviou: o relato do rui e o relato do teo.
    const item = (id: string) => html.slice(html.indexOf(id), html.indexOf("</li>", html.indexOf(id)));
    expect(item("denuncia-perigo-1")).toContain("conta-rui · rui@exemplo.pt");
    expect(item("denuncia-perigo-1")).toContain("PSP ou à PJ");
    expect(item("denuncia-falso-1")).toContain("conta-teo · teo@exemplo.pt");
    expect(item("denuncia-falso-1")).not.toContain("PSP");
    expect(html).not.toContain("relato-expirado-1");
    expect(html).not.toContain("denuncia-decidida-1");
  });

  it("paginaEscapaOTexto", async () => {
    const agora = Date.now();
    await conta("conta-rui", "rui@exemplo.pt");
    await env.DB.batch([
      env.DB.prepare("INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em) VALUES ('r1', 'desvio', ?1, ?1, 'conta-rui', ?2, ?3)").bind(HOSTIL, agora, agora + DIA),
      env.DB.prepare("INSERT INTO correcoes (id, paragem, conta_id, tipo, nome, criada_em) VALUES ('c1', 'CM:1', 'conta-rui', 'nome', ?, ?)").bind(HOSTIL, agora),
      env.DB.prepare("INSERT INTO denuncias (id, conteudo, motivo, explicacao, nome, email, email_hash, criada_em) VALUES ('d1', ?1, 'falso', ?1, ?1, 'ana@exemplo.pt', 'h', ?2)").bind(HOSTIL, agora),
      env.DB.prepare("INSERT INTO emails_por_enviar (id, para, assunto, texto, criado_em) VALUES ('e1', 'ze@exemplo.pt', ?1, ?1, ?2)").bind(HOSTIL, agora),
    ]);

    const html = await pagina(await pedirAdmin("/"));
    expect(html).toContain("&lt;script&gt;x&lt;/script&gt;&quot;&gt;&lt;img src=x onerror=alert(1)&gt;");
    expect(html).not.toContain("<script");
    expect(html).not.toContain("<img src=x");
  });

  it("respostasDoAdminTemCsp", async () => {
    const respostas = [
      await pedirAdmin("/"),
      await pedirAdmin("/", { jwt: null }),
      await pedirAdmin("/nao-existe"),
      await pedirAdmin("/foto/nao-existe"),
      await pedirAdmin("/", { metodo: "POST", origem: "https://outro.test" }),
    ];
    expect(respostas.map((resposta) => resposta.status)).toEqual([200, 403, 404, 404, 403]);
    for (const resposta of respostas) {
      const csp = resposta.headers.get("content-security-policy") ?? "";
      expect(csp).toContain("default-src 'none'");
      expect(csp).toContain("frame-ancestors 'none'");
      expect(csp).not.toContain("img-src");
      expect(csp).not.toContain("script-src");
      expect(resposta.headers.get("x-content-type-options")).toBe("nosniff");
      expect(resposta.headers.get("cache-control")).toBe("no-store");
    }
  });
});

describe("pc-admin: separado do Worker público", () => {
  it("configDoAdminSemPreVisualizacoes", () => {
    const admin = lerJsonc(env.TEST_ADMIN_CONFIG);
    const publico = lerJsonc(env.TEST_CONFIG);
    expect(admin.name).toBe("pc-admin");
    expect(admin.name).not.toBe(publico.name);
    expect(admin.main).toBe("src/admin/index.ts");
    expect(admin.preview_urls).toBe(false);
    expect(admin.routes).toBeUndefined();
    expect(admin.route).toBeUndefined();
    // Sem Cron (só o Worker público corre os prazos), sem ficheiros estáticos e sem migrações.
    expect(admin.triggers).toBeUndefined();
    expect(admin.assets).toBeUndefined();
    expect(admin.d1_databases).toHaveLength(1);
    expect(admin.d1_databases[0].migrations_dir).toBeUndefined();
    const { migrations_dir, ...d1Publico } = publico.d1_databases[0];
    expect(migrations_dir).toBe("migrations");
    expect(admin.d1_databases[0]).toEqual(d1Publico);
    expect(admin.r2_buckets).toEqual(publico.r2_buckets);
    expect(admin.secrets.required).toEqual(expect.arrayContaining(["ACCESS_EQUIPA", "ACCESS_AUD", "EMAIL_AUTOR", "CHAVE_MARCA_EMAIL"]));
  });

  it("workerPublicoNaoServeOAdmin", async () => {
    const jwt = await tokenAccess();
    for (const caminho of ["/", "/foto/x", "/admin", "/api/admin"]) {
      const resposta = await pedir(caminho, { metodo: "GET", cabecalhos: { "cf-access-jwt-assertion": jwt } });
      expect(resposta.status, caminho).toBe(404);
      const corpo = await resposta.text();
      expect(corpo, caminho).not.toContain("<html");
      expect(corpo, caminho).not.toContain("Fotos");
    }
    expect(env.TEST_INDEX_SRC).not.toMatch(/admin/);
    const publico = lerJsonc(env.TEST_CONFIG);
    expect(publico.main).toBe("src/index.ts");
    expect(publico.assets.run_worker_first).toEqual(["/api/*", "/privacidade"]);
    expect(publico.workers_dev).toBe(false);
    // Sem endereços de pré-visualização no workers.dev: o servidor só existe no domínio próprio.
    expect(publico.preview_urls).toBe(false);
  });
});
