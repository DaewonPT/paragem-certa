# Publicar o servidor da Paragem Certa

Guia para o autor. Nada disto foi feito por nós: o servidor (`worker/`) só corre no PC até fazeres estes passos, e só os fazes depois do teu "publica". Os comandos correm numa janela de terminal dentro da pasta `worker/` (`npm ci` primeiro, uma vez).

Precisas de: a conta da Cloudflare (com a zona `pixellusitano.com`), uma conta na Resend, uma conta na Google Cloud, o repositório no GitHub e o Node 24.

**Regra de ouro:** nenhuma chave, token, nome ou id do cliente da Google vai para o repositório nem para uma conversa. Colas tu cada valor, no sítio indicado.

**Quando:** só com a 0.4.0, no fim da etapa 5. A política de privacidade já descreve a comunidade, as fotos e os dados ao vivo da Carris e do Metro, que só existem nessa altura. Antes de publicar, relê-a contra o que a app faz mesmo.

`AMBIENTE` e `TESTE_GOOGLE_JWKS` são só para os testes: nunca os ponhas como variáveis nem segredos do Worker publicado.

A ordem conta: o `wrangler.jsonc` exige seis segredos (`secrets.required`) e os dois buckets do R2, por isso o `wrangler deploy` falha enquanto algum faltar. Os passos 3 e 4 têm de vir antes do 5, e os passos 10 a 13 (R2, buckets e os segredos novos) antes da primeira publicação do passo 6. O `pc-admin`, a página de revisão, é outro Worker e só se publica no passo 14.

## 1. Entrar na Cloudflare

1. `npx wrangler login` abre o browser; autoriza.
   - Para guardar o acesso no cofre do Windows em vez de num ficheiro, usa `npx wrangler login --use-keyring`.
2. `npx wrangler whoami` mostra a conta e o **id da conta**. Vais precisar dele no passo 7.

## 2. Base de dados na UE

1. `npx wrangler d1 create paragem-certa --jurisdiction=eu`
   - A jurisdição só se escolhe agora, na criação. Se te enganares, tens de criar outra base.
   - Quando perguntar "Would you like Wrangler to add it on your behalf?", responde **não**.
2. Copia o `database_id` que o comando mostra para `worker/wrangler.jsonc` (troca o texto `ID-DA-BASE-DE-DADOS-CRIADA-PELO-AUTOR`). O id não é um segredo; faz commit dele.
3. `npx wrangler d1 info paragem-certa` mostra a base, para confirmares.

## 3. Resend (emails com o código de entrada)

1. Cria a conta em resend.com. O plano grátis dá 100 emails por dia; o `config.json` reserva 70 para códigos de entrada (`codigosPorDia`).
2. Em **Domains → Add Domain**, escreve `mail.pixellusitano.com` (um subdomínio, para não mexer nos registos do email Proton) e escolhe a região **Ireland (eu-west-1)**.
3. Acrescenta na Cloudflare (zona `pixellusitano.com` → DNS → Records) os registos que a Resend mostra, tal e qual. São todos do subdomínio `mail`; não alteres os registos da raiz.
4. Espera que o domínio passe a "Verified".
5. Em **API Keys → Create API Key**, cria uma chave só com permissão de envio e só para esse domínio. Guarda-a num sítio privado: é o valor de `RESEND_API_KEY`.

O remetente que o servidor usa é `Paragem Certa <codigo@mail.pixellusitano.com>` nos códigos de entrada e `Paragem Certa <avisos@mail.pixellusitano.com>` nos recibos, nas decisões e no resumo diário (o mesmo domínio verificado).

## 4. Google (Entrar com o Google)

Na consola da Google Cloud (console.cloud.google.com), num projeto teu, abre **Google Auth Platform**. Os nomes dos menus mudam de vez em quando; segue o que a consola mostrar.

1. **Branding:** nome "Paragem Certa", email de apoio contact@pixellusitano.com, domínio `pixellusitano.com`, ligação da política `https://paragemcerta.pixellusitano.com/privacidade` e dos termos `https://paragemcerta.pixellusitano.com/termos`.
2. **Audience:** "External". Quando tudo estiver pronto, passa a "In production" (sem isso só os utilizadores de teste conseguem entrar). A app só pede o email e o perfil básico.
3. **Clients → Create client → Web application**, com o nome "Paragem Certa (servidor)". Guarda o **Client ID**: é o valor de `GOOGLE_WEB_CLIENT_ID`, que o servidor usa para conferir os tokens e a app para pedir o login. Não precisas de origens nem de endereços de retorno.
4. **Clients → Create client → Android**, um por cada chave com que se assina a app, sempre com o pacote `pt.pixellusitano.paragemcerta.pessoal`:
   - **Lançamento:** a SHA-1 da chave de lançamento. Tira-a de um APK assinado com ela (o da última Release no GitHub serve, a chave é sempre a mesma):
     `C:\Android\sdk\build-tools\36.0.0\apksigner.bat verify --print-certs paragem-certa-vX.Y.Z.apk`
     Copia a linha "Signer #1 certificate SHA-1 digest".
   - **Testes:** o mesmo comando num APK de debug feito no PC (`app\build\outputs\apk\pessoal\debug\app-pessoal-debug.apk`), para poderes experimentar o login em builds locais.
   - A variante `play` (pacote `pt.pixellusitano.paragemcerta`, chave da Play) só entra quando a app for para a Play.
5. Para builds no PC, acrescenta ao `local.properties` (que não vai para o git) a linha `GOOGLE_WEB_CLIENT_ID=o-teu-client-id`. Sem ele o botão do Google não aparece.

## 5. Segredos do Worker

Ainda na pasta `worker/`, um comando por segredo; cada um pede o valor e tu colas (não aparece no ecrã):

```
npx wrangler secret put RESEND_API_KEY
npx wrangler secret put RESPONSAVEL
npx wrangler secret put GOOGLE_WEB_CLIENT_ID
```

- `RESEND_API_KEY`: a chave do passo 3.
- `RESPONSAVEL`: o nome do responsável pelos dados, escrito por ti. Só aparece na política de privacidade e em mais lado nenhum; sem ele a página `/privacidade` responde 503 de propósito.
- `GOOGLE_WEB_CLIENT_ID`: o Client ID do passo 4.

O primeiro comando diz que o Worker `paragem-certa` ainda não existe e pergunta se o crias: responde **sim**. `npx wrangler secret list` mostra os nomes dos segredos (nunca os valores).

## 6. Primeira publicação, à mão

```
npx wrangler d1 migrations apply DB --remote
npx wrangler deploy --domain paragemcerta.pixellusitano.com
```

- O primeiro cria as tabelas na base da UE (pede confirmação e faz uma cópia antes).
- O segundo publica o Worker e os ficheiros de `public/`, liga o domínio próprio `paragemcerta.pixellusitano.com` (a Cloudflare cria o registo DNS e o certificado) e marca o Cron diário das 03:00 UTC (que apaga o que passou os prazos). O endereço `workers.dev` fica desligado.
- O domínio só se liga nesta primeira vez: as publicações seguintes (a mão ou pelo GitHub) não lhe mexem.

Confirma, quando o certificado estiver pronto (pode demorar uns minutos):

- `https://paragemcerta.pixellusitano.com/api/saude` responde `{"ok":true}`;
- `/privacidade` mostra o teu nome como responsável;
- `/termos`, `/apagar-conta` e `/dados/config.json` abrem.

## 7. Publicar pelo GitHub nas vezes seguintes

1. **Token da Cloudflare:** Dashboard → My Profile → API Tokens → Create Token → modelo **Edit Cloudflare Workers**; acrescenta a permissão **Account → D1 → Edit**; limita-o à tua conta e à zona `pixellusitano.com`.
2. No GitHub, no repositório: Settings → Secrets and variables → Actions → New repository secret, cinco segredos:
   - `CLOUDFLARE_API_TOKEN`: o token;
   - `CLOUDFLARE_ACCOUNT_ID`: o id do passo 1;
   - `RESEND_API_KEY`, `RESPONSAVEL` e `GOOGLE_WEB_CLIENT_ID`: os mesmos valores do passo 5.

   Os cinco da etapa da comunidade juntam-se no passo 15.
3. Para publicar: **Actions → Servidor → Run workflow**, no ramo `main`. O trabalho `publicar` espera pelos testes e pelos tipos, aplica as migrações na base remota, faz o deploy do Worker principal e a seguir o do `pc-admin`, enviando outra vez os segredos de cada um. Só corre assim, à mão e no `main`: um commit ou um pedido de alterações nunca publica nada.
4. O segredo `GOOGLE_WEB_CLIENT_ID` também serve ao `android.yml`: a Release assinada leva o id no APK. Sem ele, o botão do Google não aparece.

## 8. Regra grátis contra abusos em `/api/auth/*`

Na Cloudflare, na zona `pixellusitano.com`: Security → WAF → Rate limiting rules → Create rule. O plano grátis só deixa uma regra, por IP, com janelas de 10 s.

- Nome: `auth`.
- Quando: **URI Path** começa por `/api/auth/` (em texto: `starts_with(http.request.uri.path, "/api/auth/")`). O plano grátis só aceita o caminho (sem o Hostname); o site principal não tem este caminho, por isso a regra só apanha o servidor.
- Contar por **IP**: 5 pedidos em 10 segundos. Ação **Block**, durante 10 segundos.
- Uma entrada normal faz 2 ou 3 pedidos. Com mais, um só IP podia gastar sozinho as 100 000 escritas por dia da base grátis (cada pedido de `/api/auth/google/nonce` escreve uma linha) e parar as entradas e a sincronização de toda a gente até à meia-noite UTC. Quem partilhar o IP e for bloqueado espera 10 segundos.

Os limites de email por endereço (3 por hora) e por dia (70) são contados no servidor e não dependem desta regra.

## 9. Ligar a conta na app

A entrada "Conta" do Menu só aparece com o interruptor ligado.

1. Em `worker/public/dados/config.json`, muda `"conta": false` para `"conta": true`, faz commit no `main` e corre o **Servidor → Run workflow** do passo 7.
2. Confirma em `https://paragemcerta.pixellusitano.com/dados/config.json`. A app vai buscá-lo de cada vez que abre; depois de a abrires (uma vez, com rede) aparece Menu → Conta.
3. No dia de publicar a app, liga também `"conta"` em `app/src/main/assets/config.json`: é a cópia de reserva que a app usa na primeira abertura sem rede.

Experimenta de ponta a ponta antes de anunciar: entrar com um código por email, entrar com o Google, ver os favoritos aparecerem num segundo telemóvel e apagar a conta.

## 10. Ativar o R2 e o Zero Trust

A comunidade usa o R2 (as fotos) e o Zero Trust (a página de revisão). Os dois pedem um cartão. Faz estes passos antes da primeira publicação do passo 6.

1. No painel da Cloudflare, abre **R2 Object Storage** e ativa o R2. O plano grátis chega (10 GB), mas o R2 **não tem teto de gasto**: se passares o limite, cobra.
2. Abre **Zero Trust**, escolhe o nome da equipa (passa a ser o endereço `https://<equipa>.cloudflareaccess.com`) e o plano **Free**. Também pede dados de pagamento. Guarda o nome da equipa: precisas dele no passo 14.
3. **Alerta de gasto em 1 $:** Manage Account → Billing → Billable Usage, cria um alerta de gasto de 1 $. O de omissão é de 10 $. O alerta só avisa por email, e só no dia seguinte; não corta nada. Os outros travões são do servidor: uma foto por conta e por dia, no máximo 100 por dia e 500 KB cada.

## 11. Buckets das fotos, na UE

Na pasta `worker/`:

```
npx wrangler r2 bucket create pc-fotos-pendentes --jurisdiction eu
npx wrangler r2 bucket create pc-fotos --jurisdiction eu
```

- `pc-fotos-pendentes` é privado: as fotos novas entram aqui e só os dois Workers as leem. O `pc-fotos` serve as fotos aprovadas (passo 12).
- A jurisdição só se escolhe agora, na criação, e não muda depois (como a da base, no passo 2). Se te enganares, cria outro bucket.
- Os nomes têm de ser estes, tal e qual: estão no `wrangler.jsonc` e no `wrangler.admin.jsonc`.

## 12. O endereço das fotos: `fotos.pixellusitano.com`

1. No painel: R2 Object Storage → `pc-fotos` → Settings → **Custom Domains** → Add → `fotos.pixellusitano.com`. A Cloudflare cria o registo DNS e o certificado.
2. Nos **dois** buckets, em Settings → **Public Development URL** (o endereço `r2.dev`), deixa desligado. O `pc-fotos-pendentes` não pode ter nenhum endereço público.
3. Confirma que o endereço próprio funciona com um bucket da UE (por verificar: é a única coisa desta etapa que ainda ninguém experimentou). Põe lá uma foto de teste, qualquer JPEG pequeno sem pessoas, e pede-a:

   ```
   npx wrangler r2 object put pc-fotos/teste.jpg --file teste.jpg --content-type image/jpeg --cache-control "public, max-age=86400" --jurisdiction eu --remote
   curl -I https://fotos.pixellusitano.com/teste.jpg
   ```

   A resposta tem de ser `200` e trazer `cache-control: public, max-age=86400`. Depois apaga a foto de teste: `npx wrangler r2 object delete pc-fotos/teste.jpg --jurisdiction eu --remote`.
4. Se não funcionar, não ligues o interruptor `fotos` no passo 16 (a comunidade e as correções não dependem das fotos) até perceberes porquê.

## 13. Token de limpeza da cache e segredos novos

Quando rejeitas ou apagas uma foto aprovada, o servidor pede à Cloudflare para a esquecer da cache (senão a cópia podia ficar visível até 24 horas). Para isso, e para os avisos ao autor, os dois Workers precisam de três segredos novos:

1. **`CF_PURGE_TOKEN`:** My Profile → API Tokens → Create Token → Custom token, com uma só permissão, **Zone → Cache Purge → Purge**, e só para a zona `pixellusitano.com` (Zone Resources → Include → Specific zone). Não uses o token do passo 7. Guarda-o num sítio privado.
2. **`CF_ZONA`:** o id da zona `pixellusitano.com`, no painel da zona (Overview, na coluna da direita, "Zone ID").
3. **`EMAIL_AUTOR`:** o teu email. Recebe o aviso das denúncias de perigo e o resumo diário, e é o único que o `pc-admin` deixa entrar. Tem de ser o mesmo endereço que puseres na política do Access (passo 14).

Um comando por segredo e por Worker (cada um pede o valor e tu colas):

```
npx wrangler secret put CF_PURGE_TOKEN
npx wrangler secret put CF_ZONA
npx wrangler secret put EMAIL_AUTOR
npx wrangler secret put CF_PURGE_TOKEN -c wrangler.admin.jsonc
npx wrangler secret put CF_ZONA -c wrangler.admin.jsonc
npx wrangler secret put EMAIL_AUTOR -c wrangler.admin.jsonc
```

Os três primeiros são do Worker principal (`paragem-certa`); os três com `-c wrangler.admin.jsonc` são do `pc-admin`. O `pc-admin` ainda não existe: no primeiro deles a wrangler pergunta se o crias, e respondes **sim** (como no passo 5).

No fim do passo 14, cada Worker tem de ter estes seis segredos (`npx wrangler secret list` e `npx wrangler secret list -c wrangler.admin.jsonc` mostram os nomes, nunca os valores):

- `paragem-certa`: `RESEND_API_KEY`, `RESPONSAVEL`, `GOOGLE_WEB_CLIENT_ID` (passo 5), `CF_PURGE_TOKEN`, `CF_ZONA` e `EMAIL_AUTOR` (este passo);
- `pc-admin`: `RESEND_API_KEY`, `ACCESS_EQUIPA` e `ACCESS_AUD` (passo 14), `EMAIL_AUTOR`, `CF_PURGE_TOKEN` e `CF_ZONA` (este passo).

## 14. A página de revisão (`pc-admin`) e o Cloudflare Access

A página de revisão é um Worker à parte, o `pc-admin` (`worker/wrangler.admin.jsonc`), no endereço `workers.dev`. O Access fica à frente dele e só deixa entrar o teu email; o Worker volta a conferir o cabeçalho que o Access envia (`Cf-Access-Jwt-Assertion`) e, sem ele ou com outro email, não mostra nada. O Access nunca fica à frente do Worker principal.

1. Os segredos que faltam:

   ```
   npx wrangler secret put RESEND_API_KEY -c wrangler.admin.jsonc
   npx wrangler secret put ACCESS_EQUIPA -c wrangler.admin.jsonc
   npx wrangler secret put ACCESS_AUD -c wrangler.admin.jsonc
   ```

   - `RESEND_API_KEY`: a mesma chave do passo 3.
   - `ACCESS_EQUIPA`: o endereço da tua equipa do Zero Trust, `https://<equipa>.cloudflareaccess.com`, com o nome do passo 10.
   - `ACCESS_AUD`: ainda não existe (só nasce no ponto 4). Escreve `por-preencher`: com este valor o `pc-admin` recusa toda a gente.
2. A primeira publicação, à mão: `npx wrangler deploy -c wrangler.admin.jsonc`. Mostra o endereço `https://pc-admin.<subdomínio>.workers.dev`. As pré-visualizações já vêm desligadas na configuração.
3. **One-time PIN:** Zero Trust → Integrations → Identity providers → junta **One-time PIN** (o código chega por email).
4. **Proteger o Worker:** Workers & Pages → `pc-admin` → Access → Protect this Worker behind Access → **All traffic**. Na política, **Include → Emails → o teu email** (o `EMAIL_AUTOR`). Nunca uma política só com "Login Methods": deixava entrar qualquer pessoa que consiga um PIN.
   - Se o plano grátis não tiver o Access no Worker, cria no Zero Trust (Access → Applications) uma aplicação **Self-hosted** para o hostname `pc-admin.<subdomínio>.workers.dev`, com a mesma política.
5. **A AUD:** copia a etiqueta de audiência da aplicação (Application Audience (AUD) Tag, na página da aplicação) e substitui o `por-preencher`: `npx wrangler secret put ACCESS_AUD -c wrangler.admin.jsonc`.
6. **Prova:** numa janela privada do browser, o endereço do `pc-admin` mostra a página do PIN da Cloudflare, nunca a página de revisão. Escreve o teu email, mete o código que chega e aparece a página. E `curl -i https://pc-admin.<subdomínio>.workers.dev/` nunca devolve a página de revisão (só um redirecionamento ou um 403).

## 15. Segredos novos no GitHub

Para o trabalho `publicar` (passo 7) enviar os segredos aos dois Workers, junta no GitHub (Settings → Secrets and variables → Actions → New repository secret) cinco segredos novos, com os mesmos valores dos passos 13 e 14: `CF_PURGE_TOKEN`, `CF_ZONA`, `EMAIL_AUTOR`, `ACCESS_AUD` e `ACCESS_EQUIPA`. O `RESEND_API_KEY` já lá está desde o passo 7. Em `ACCESS_AUD` vai a AUD verdadeira (ponto 5 do passo 14), nunca o `por-preencher`.

## 16. Ligar a comunidade, as fotos e as correções

Só no fim, depois do parecer do advogado sobre o DSA e as fotos.

1. Em `worker/public/dados/config.json`, em `interruptores`, muda para `true` os que queres ligar: `"comunidade"`, `"fotos"` e `"correcoes"`. Faz commit no `main` e corre o **Servidor → Run workflow** (passo 7). Confirma em `https://paragemcerta.pixellusitano.com/dados/config.json`.
2. No dia de publicar a app, liga os mesmos em `app/src/main/assets/config.json` (a cópia de reserva da primeira abertura sem rede). Primeiro o servidor e só depois a app: nunca um interruptor ligado na app e desligado no servidor.
3. Se o parecer o pedir, deixa `"fotos"` em `false`: as outras duas funcionam sem fotos.
4. **Desligar `fotos` só as esconde na app.** As fotos aprovadas continuam na internet em `fotos.pixellusitano.com`. Para as tirar de lá: tira o domínio `fotos.pixellusitano.com` do `pc-fotos` (R2 → `pc-fotos` → Settings → Custom Domains) ou esvazia o bucket, e limpa a cache (zona `pixellusitano.com` → Caching → Configuration → Purge Cache → Custom Purge, pelo hostname `fotos.pixellusitano.com`).

## 17. Pedidos de "Apagar conta" por email

Quem não tem a app pede por email, como diz a página `apagar-conta.html`, e a conta tem de ser apagada no máximo em 30 dias.

1. Confirma que o email do pedido veio do endereço da conta.
2. Abre a página de revisão do `pc-admin` (passo 14), escreve esse email em **Apagar conta por email** e carrega em "Apagar conta". Apaga todas as contas desse email, com as fotos (também as aprovadas, no R2 e na cache), os relatos, as confirmações e as correções.
3. Responde ao pedido a dizer que a conta foi apagada. A página não envia nenhum email.

Nunca apagues pelo `wrangler d1 execute … DELETE`: deixava as fotos aprovadas no ar.

## 18. Se alguém gastar a quota de propósito

As leituras da comunidade não têm limite por IP (nas redes móveis muita gente partilha o mesmo IP), por isso alguém pode gastar de propósito os 100 000 pedidos por dia e parar tudo no servidor até à meia-noite UTC. Se vires muitos pedidos de um só IP nas estatísticas do Worker, na zona `pixellusitano.com`: Security → WAF → Custom rules → cria uma regra que bloqueia esse IP e tira-a no dia seguinte (nas redes móveis o IP é partilhado por muita gente, por isso um bloqueio comprido apanha inocentes). Os nomes dos menus mudam de vez em quando; segue o que a Cloudflare mostrar.

## Custos e limites

Tudo no plano grátis da Cloudflare e da Resend: o Worker e a D1 param no limite (100 000 pedidos por dia para a conta inteira, até à meia-noite UTC) em vez de cobrar. O R2 (as fotos) e o Zero Trust (a página de revisão) pedem cartão: o R2 não tem teto de gasto, por isso o alerta de 1 $ do passo 10 não se salta. Os passos 10 a 18 tratam deles.
