-- Só se entra com o Google: apaga as contas sem Google ligado (as criadas por código no email), com tudo o que é delas.
-- Mesma ordem e mesmas tabelas do apagarContaPorId (conta.ts), sem confiar nas cascatas, para apagar exatamente o
-- mesmo que o botão "Apagar conta". Denúncias, decisões e bloqueios ficam (só guardam a marca do email).
-- Limite: nos segundos entre esta migração e o deploy do Worker novo, o Worker antigo ainda pode criar uma conta por código; essa conta fica sem forma de entrar e só se apaga a pedido.

DELETE FROM confirmacoes WHERE relato_id IN (SELECT id FROM relatos WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL));
DELETE FROM confirmacoes WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM relatos WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM correcoes WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM favoritos WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM aceites_termos WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM sessoes WHERE conta_id IN (SELECT id FROM contas WHERE google_sub IS NULL);
DELETE FROM contas WHERE google_sub IS NULL;
