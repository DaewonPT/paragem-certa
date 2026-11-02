-- Esquema inicial: conta, sessões, códigos por email, nonces do Google, favoritos e limites de email.
-- Tempos em milissegundos desde 1970 (Date.now()); `dia` é a data em UTC ("2026-11-02").
-- As consultas das tarefas seguintes usam sempre estes índices e um LIMIT.

CREATE TABLE contas (
  id TEXT PRIMARY KEY,                 -- crypto.randomUUID()
  email TEXT NOT NULL,                 -- em minúsculas; NÃO é único: uma conta do Google e uma de código podem ter o mesmo email
  google_sub TEXT UNIQUE,              -- o `sub` do token da Google, nulo nas contas só de email
  criada_em INTEGER NOT NULL,
  revisao INTEGER NOT NULL DEFAULT 0   -- última revisão dada a um favorito desta conta
);
CREATE INDEX contas_email ON contas (email);

CREATE TABLE sessoes (
  hash TEXT PRIMARY KEY,               -- SHA-256 (hexadecimal) do token; o token nunca se guarda
  conta_id TEXT NOT NULL REFERENCES contas (id) ON DELETE CASCADE,
  criada_em INTEGER NOT NULL,
  usada_em INTEGER NOT NULL
);
CREATE INDEX sessoes_conta ON sessoes (conta_id);
CREATE INDEX sessoes_criada ON sessoes (criada_em);
CREATE INDEX sessoes_usada ON sessoes (usada_em);

-- Cada pedido de código acrescenta uma linha (é assim que se contam os pedidos por hora);
-- só a mais recente de cada email serve para confirmar. Guarda-se SHA-256(sal + código), nunca o código.
CREATE TABLE codigos (
  email TEXT NOT NULL,
  hash TEXT NOT NULL,
  sal TEXT NOT NULL,
  criado_em INTEGER NOT NULL,
  tentativas INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX codigos_email ON codigos (email, criado_em);
CREATE INDEX codigos_criado ON codigos (criado_em);

CREATE TABLE nonces (
  valor TEXT PRIMARY KEY,
  criado_em INTEGER NOT NULL
);
CREATE INDEX nonces_criado ON nonces (criado_em);

CREATE TABLE favoritos (
  conta_id TEXT NOT NULL REFERENCES contas (id) ON DELETE CASCADE,
  chave TEXT NOT NULL,                 -- "CM:060419" nas paragens, o uuid nos sítios
  tipo TEXT NOT NULL,                  -- PARAGEM, CASA, TRABALHO ou OUTRO
  nome TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  ordem INTEGER NOT NULL,
  apagado INTEGER NOT NULL DEFAULT 0,  -- 1 = apagado (fica marcado para desaparecer nos outros telemóveis)
  revisao INTEGER NOT NULL,
  PRIMARY KEY (conta_id, chave)
);
CREATE INDEX favoritos_revisao ON favoritos (conta_id, revisao);

CREATE TABLE aceites_termos (
  conta_id TEXT NOT NULL REFERENCES contas (id) ON DELETE CASCADE,
  versao_termos TEXT NOT NULL,
  aceite_em INTEGER NOT NULL,
  PRIMARY KEY (conta_id, versao_termos)
);

-- Códigos de entrada enviados por dia, para o teto global (`emails.codigosPorDia` do config.json).
CREATE TABLE envios_email (
  dia TEXT PRIMARY KEY,
  quantos INTEGER NOT NULL DEFAULT 0
);
