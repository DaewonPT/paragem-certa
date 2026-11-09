-- Comunidade (0.4): relatos e confirmações, fotos, correções, denúncias com as decisões, bloqueios,
-- emails por enviar e contadores diários. Só acrescenta; não mexe no que a 0001 criou.
-- Tempos em milissegundos desde 1970 (Date.now()); `dia` é a data em UTC ("2026-11-03"); os ids são crypto.randomUUID().
-- Chave da paragem = "CM:060419" ou "TRANSITOUS:<id>"; chave da linha = o nome visível ("736"), em maiúsculas.
-- Nada aqui guarda posição GPS nem IP. As consultas das tarefas seguintes usam sempre estes índices e um LIMIT.

-- estado: ativo | terminado. `linha` é '' (não NULL) nos tipos da paragem, para o índice único funcionar.
CREATE TABLE relatos (
  id TEXT PRIMARY KEY,
  tipo TEXT NOT NULL,
  paragem TEXT NOT NULL,
  linha TEXT NOT NULL DEFAULT '',
  conta_id TEXT NOT NULL REFERENCES contas (id) ON DELETE CASCADE,
  criado_em INTEGER NOT NULL,
  expira_em INTEGER NOT NULL,
  estado TEXT NOT NULL DEFAULT 'ativo'
);
-- Só um relato ativo igual de cada vez; os pedidos repetidos ou em paralelo caem aqui (ON CONFLICT DO NOTHING).
CREATE UNIQUE INDEX relatos_um_ativo ON relatos (paragem, tipo, linha) WHERE estado = 'ativo';
CREATE INDEX relatos_paragem ON relatos (paragem, expira_em);
CREATE INDEX relatos_linha ON relatos (linha, expira_em);
CREATE INDEX relatos_conta ON relatos (conta_id, criado_em);
CREATE INDEX relatos_expira ON relatos (expira_em);

-- voto: ainda_esta | ja_nao_esta. Uma linha por conta e relato (pode mudar de ideias).
CREATE TABLE confirmacoes (
  relato_id TEXT NOT NULL REFERENCES relatos (id) ON DELETE CASCADE,
  conta_id TEXT NOT NULL REFERENCES contas (id) ON DELETE CASCADE,
  voto TEXT NOT NULL,
  em INTEGER NOT NULL,
  PRIMARY KEY (relato_id, conta_id)
);
CREATE INDEX confirmacoes_conta ON confirmacoes (conta_id);

-- estado: pendente | aprovada | rejeitada. A rejeitada fica sem `chave` (o ficheiro sai do R2 na decisão) e a linha sai
-- 1 dia depois: até lá conta para o limite de 1 foto por conta e dia. `chave` é "<sha256 do JPEG>.jpg".
CREATE TABLE fotos (
  id TEXT PRIMARY KEY,
  paragem TEXT NOT NULL,
  conta_id TEXT NOT NULL REFERENCES contas (id) ON DELETE CASCADE,
  chave TEXT UNIQUE,
  estado TEXT NOT NULL DEFAULT 'pendente',
  criada_em INTEGER NOT NULL,
  decidida_em INTEGER
);
-- Uma foto por paragem de cada vez (pendente ou aprovada); a rejeitada deixa a paragem livre.
CREATE UNIQUE INDEX fotos_uma_por_paragem ON fotos (paragem) WHERE estado <> 'rejeitada';
CREATE INDEX fotos_conta ON fotos (conta_id, criada_em);
CREATE INDEX fotos_estado ON fotos (estado, criada_em);

-- tipo: posicao | nome | ja_nao_existe; estado: pendente | aprovada | rejeitada.
CREATE TABLE correcoes (
  id TEXT PRIMARY KEY,
  paragem TEXT NOT NULL,
  conta_id TEXT NOT NULL REFERENCES contas (id) ON DELETE CASCADE,
  tipo TEXT NOT NULL,
  lat REAL,
  lon REAL,
  nome TEXT,
  estado TEXT NOT NULL DEFAULT 'pendente',
  criada_em INTEGER NOT NULL,
  decidida_em INTEGER
);
CREATE INDEX correcoes_paragem ON correcoes (paragem, estado, decidida_em);
CREATE INDEX correcoes_conta ON correcoes (conta_id, criada_em);
CREATE INDEX correcoes_estado ON correcoes (estado, criada_em);

-- Denúncias, decisões e bloqueios não têm chave para as contas (apagar a conta não os leva) e guardam-se 12 meses.
-- `conteudo` é "relato:<id>" ou "foto:<id>". `email_hash` é o SHA-256 do email normalizado. O nome e o email de quem
-- denuncia ficam só até a decisão lhe ser enviada; depois fica o hash.
CREATE TABLE denuncias (
  id TEXT PRIMARY KEY,
  conteudo TEXT NOT NULL,
  motivo TEXT NOT NULL,
  explicacao TEXT NOT NULL DEFAULT '',
  nome TEXT,
  email TEXT,
  email_hash TEXT NOT NULL,
  criada_em INTEGER NOT NULL,
  decidida_em INTEGER
);
CREATE UNIQUE INDEX denuncias_uma_por_pessoa ON denuncias (conteudo, email_hash);
CREATE INDEX denuncias_pessoa ON denuncias (email_hash, criada_em);
CREATE INDEX denuncias_abertas ON denuncias (decidida_em, criada_em);
CREATE INDEX denuncias_criada ON denuncias (criada_em);

-- Uma linha por decisão do autor (a explicação da DSA, art. 17). `denuncia_id` é nulo se não veio de uma denúncia.
CREATE TABLE decisoes (
  id TEXT PRIMARY KEY,
  acao TEXT NOT NULL,
  conteudo TEXT NOT NULL DEFAULT '',
  denuncia_id TEXT,
  email_hash TEXT NOT NULL,
  motivo TEXT NOT NULL,
  regra TEXT NOT NULL,
  criada_em INTEGER NOT NULL
);
CREATE INDEX decisoes_criada ON decisoes (criada_em);

CREATE TABLE bloqueios (
  email_hash TEXT PRIMARY KEY,
  motivo TEXT NOT NULL,
  criado_em INTEGER NOT NULL
);
CREATE INDEX bloqueios_criado ON bloqueios (criado_em);

-- Emails que não saíram (teto diário cheio ou Resend em baixo): a página de revisão mostra-os com "Enviar agora".
CREATE TABLE emails_por_enviar (
  id TEXT PRIMARY KEY,
  para TEXT NOT NULL,
  assunto TEXT NOT NULL,
  texto TEXT NOT NULL,
  criado_em INTEGER NOT NULL
);
CREATE INDEX emails_por_enviar_criado ON emails_por_enviar (criado_em);

-- Tetos globais por dia UTC: nome 'fotos' ou 'emails'.
CREATE TABLE contadores (
  dia TEXT NOT NULL,
  nome TEXT NOT NULL,
  quantos INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (dia, nome)
);
