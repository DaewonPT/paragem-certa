-- Só se entra com o Google: apaga as contas sem Google ligado (as criadas por código no email), com tudo o que é delas.
-- Mesma ordem e mesmas tabelas do apagarContaPorId (conta.ts), sem confiar nas cascatas, para apagar exatamente o
-- mesmo que o botão "Apagar conta". Denúncias, decisões e bloqueios ficam (só guardam a marca do email).
-- Os pedidos de código (com o email em claro) e a contagem dos envios também saem todos: o cron já não os limpa.
-- Limite: nos segundos entre esta migração e o deploy do Worker novo, o Worker antigo ainda pode criar uma conta por código,
-- e essa conta fica sem forma de entrar e só se apaga a pedido. Um código pedido nesse intervalo também deixa a linha dele
-- em codigos até as tabelas codigos e envios_email saírem.

DELETE FROM confirmacoes WHERE relato_id IN (SELECT id FROM relatos WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL));
DELETE FROM confirmacoes WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM relatos WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM correcoes WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM favoritos WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM aceites_termos WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM sessoes WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM contas WHERE google_sub IS NULL;
DELETE FROM codigos;
DELETE FROM envios_email;
