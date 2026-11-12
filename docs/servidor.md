# Publicar o servidor da Paragem Certa

Guia para o autor. O servidor (`worker/`) só corre no PC até fazeres estes passos. Os comandos correm na «Linha de comandos» (cmd), dentro da pasta `worker/` (`npm ci` primeiro, uma vez), e não no PowerShell: o PowerShell do Windows vem com os scripts desligados, por isso o `npx` não corre lá, e o `curl` do PowerShell é outro comando (por isso os comandos com curl levam `curl.exe`).

Precisas de: a conta da Cloudflare (com a zona `pixellusitano.com`), uma conta na Resend, uma conta na Google Cloud, o repositório no GitHub e o Node 24.

**Regra de ouro:** nenhuma chave, token, nome ou id do cliente da Google vai para o repositório. Colas cada valor no sítio indicado.

**Modelo D:** o servidor nunca custa dinheiro. Nunca ligues o Workers Paid nem cries buckets no R2 (ver «Custos e limites»).

**Quando:** só com a 0.4.0 ou mais recente. A política de privacidade já descreve a comunidade, as correções e os dados ao vivo da Carris, que só existem nessa altura. Antes de publicar, relê-a contra o que a app faz mesmo.

`AMBIENTE`, `TESTE_GOOGLE_JWKS`, `TESTE_ACCESS_JWKS` e `TESTE_INTERRUPTORES` são só para os testes: nunca os ponhas como variáveis nem segredos de nenhum dos dois Workers publicados (`paragem-certa` e `pc-admin`).

A ordem conta: o `wrangler.jsonc` exige cinco segredos (`secrets.required`), e o `wrangler.admin.jsonc` do `pc-admin` outros cinco, por isso o `wrangler deploy` falha enquanto algum faltar. Os passos 3 e 4 têm de vir antes do 5, e os passos 10 e 13 (Zero Trust e os segredos `EMAIL_AUTOR` e `CHAVE_MARCA_EMAIL`) antes da primeira publicação do passo 6. O servidor não usa o R2: os passos 11 e 12 só apagam o que ficou da 0.4.0, depois de o Servidor publicar sem ele. O `pc-admin`, a página de revisão, é outro Worker e só se publica no passo 14.

## Publicar a 0.4.0, por esta ordem

1 a 3. Já estão feitos: o ramo `versao-0-4` está no GitHub e já entrou no `main`, as capturas estão em `app/src/test/screenshots` e o Android passou a verde no `main` (05/11/2026). Começa no ponto 4.
4. Este guia, por esta ordem: passos 1, 2, 3, 4 e 5; depois 10 e 13; só então o 6; depois 7, 14 e 15 (o 8 já está feito no servidor). O 11 e o 12 só apagam o R2 que ficou da 0.4.0. O 9 e o 16 ligam os interruptores e ficam para o ponto 6. O 17 e o 18 são para quando for preciso: lê-os já, mas não há nada para fazer. Relê a política de privacidade antes.
5. Envia para o GitHub o que mudaste no PC (pelo menos os `database_id` do passo 2): `git push origin main`. Depois **Servidor → Run workflow**, no `main`, que põe no ar o `linhas.json` e o `fotos-de-rua.json`. Quando ficar verde, cria a variável `SERVIDOR_PUBLICADO` = `sim` (passo 20). Confirma que `https://paragemcerta.pixellusitano.com/dados/linhas.json` responde.
6. Liga os interruptores em `worker/public/dados/config.json` (passos 9 e 16), ajusta os testes do servidor, faz commit e `git push origin main`. Esse push abre sozinho uma corrida do Servidor (só testes): espera que acabe e só então **Servidor → Run workflow**, no `main`. Confirma em `https://paragemcerta.pixellusitano.com/dados/config.json` e só depois liga os mesmos em `app/src/main/assets/config.json` (com os testes da app), faz commit e `git push origin main`. O `carrisAoVivo` fica `false` até haver o OK escrito da Carris (passo 19). Se deixares algum desligado, tira a linha dele do `## 0.4.0` do `PATCHNOTES.md`: esse texto é o do aviso de atualização.
7. Faz commit do `app/src/main/assets/config.json` e do `PATCHNOTES.md` no `main` e envia-o (`git push`). Espera que o workflow **Android** fique verde no `main`. Depois, no terminal: `git checkout main`, `git pull`, `git tag v0.4.0` e `git push origin v0.4.0`. O CI assina o APK e cria a Release com o texto do `## 0.4.0` do `PATCHNOTES.md`; os telemóveis com a 0.3.2 recebem o aviso de atualização.

Antes de passar de 720 `/plan` por dia, fala com a equipa do Transitous no Matrix.

## 1. Entrar na Cloudflare

1. `npx wrangler login` abre o browser; autoriza.
   - Opcional: para o acesso não ficar num ficheiro em texto simples, usa `npx wrangler login --use-keyring`. Fica num ficheiro cifrado, com a chave no Gestor de Credenciais do Windows. Da primeira vez, a wrangler descarrega com o npm um pacote pequeno (`@napi-rs/keyring`, uns 2 MB).
2. `npx wrangler whoami` mostra a conta e o **id da conta**. Vais precisar dele no passo 7.

## 2. Base de dados na UE

1. `npx wrangler d1 create paragem-certa --jurisdiction=eu`
   - A jurisdição só se escolhe agora, na criação. Se te enganares, tens de criar outra base.
   - Quando perguntar "Would you like Wrangler to add it on your behalf?", responde **não**. Se disseres que sim por engano, a wrangler reescreve o `wrangler.jsonc` com uma segunda ligação à base: desfaz com `git checkout -- worker/wrangler.jsonc` (na pasta `codigo`) e copia só o id, como no ponto 2.
2. Copia o `database_id` que o comando mostra para `worker/wrangler.jsonc` **e** para `worker/wrangler.admin.jsonc` (troca o texto `ID-DA-BASE-DE-DADOS-CRIADA-PELO-AUTOR` nos dois). O id não é um segredo: faz commit dele no `main` e envia-o para o GitHub com `git push origin main`. O **Run workflow** usa o que está no GitHub, não o que tens no PC.
3. `npx wrangler d1 info paragem-certa` mostra a base, para confirmares.

## 3. Resend (emails de avisos e decisões)

1. Cria a conta em resend.com. O plano grátis dá 100 emails por dia e 3000 por mês. O servidor envia no máximo 30 por dia (`outrosPorDia` no `config.json`), como recibos, decisões e o resumo diário; os que não couberem ficam guardados para enviares mais tarde com "Enviar agora".
2. Em **Domains → Add Domain**, escreve `mail.pixellusitano.com` (um subdomínio, para não mexer nos registos do email Proton) e escolhe a região **Ireland (eu-west-1)**.
3. Acrescenta na Cloudflare (zona `pixellusitano.com` → DNS → Records) os registos que a Resend mostra, tal e qual. São todos do subdomínio `mail`; não alteres os registos da raiz.
4. Espera que o domínio passe a "Verified".
5. Em **API Keys → Create API Key**, cria uma chave só com permissão de envio e só para esse domínio. Guarda-a num sítio privado: é o valor de `RESEND_API_KEY`.

O remetente que o servidor usa é `Paragem Certa <avisos@mail.pixellusitano.com>` nos recibos, nas decisões e no resumo diário (do domínio que verificaste).

## 4. Google (Entrar com o Google)

Na consola da Google Cloud (console.cloud.google.com), num projeto teu, abre **Google Auth Platform**. Os nomes dos menus mudam de vez em quando; segue o que a consola mostrar.

Se o projeto ainda não tiver nada configurado, a consola mostra primeiro o botão **Get started**. Carrega nele e preenche: nome «Paragem Certa», email de apoio (ver o ponto 1 abaixo), Audience **External** e, em **Contact Information**, o teu email (é para lá que a Google manda os avisos do projeto). No fim aceita a política de dados da Google e carrega em **Create**. Depois completa o resto em **Branding**.

1. **Branding:** nome "Paragem Certa", domínio `pixellusitano.com`, ligação da política `https://paragemcerta.pixellusitano.com/privacidade` e dos termos `https://paragemcerta.pixellusitano.com/termos`. O **email de apoio** é uma lista: só aparece o email da conta Google com que entraste na consola (ou um Grupo Google teu), e quem entrar na app com o Google vai vê-lo. Para escolheres contact@pixellusitano.com, antes de fazeres este passo 4 cria uma conta Google com esse endereço: em accounts.google.com → Criar conta, escolhe a opção de usar o email que já tens (o código de confirmação chega ao Proton). Depois faz o passo 4 todo com essa conta. Se o projeto for de outra conta tua, dá à nova o papel Editor no IAM do projeto, sai e volta a entrar com ela. Não escolhas o teu Gmail pessoal, senão fica à vista de toda a gente.
2. **Audience:** "External". A app só pede o email e o perfil básico (Entrar com o Google), por isso qualquer conta Google consegue entrar já em "Testing", sem precisares de pôr utilizadores de teste. Quando tudo estiver pronto, carrega em **Publish app** para passar a "In production". Enquanto a marca não for verificada (ponto 6 abaixo), o Google mostra só o domínio `pixellusitano.com` em vez do nome «Paragem Certa».
3. **Clients → Create client → Web application**, com o nome "Paragem Certa (servidor)". Guarda o **Client ID**: é o valor de `GOOGLE_WEB_CLIENT_ID`, que o servidor usa para conferir os tokens e a app para pedir o login. Não precisas de origens nem de endereços de retorno. A consola mostra também um **Client secret**: não precisas dele, não o guardes nem o ponhas em lado nenhum.
4. **Clients → Create client → Android**, um por cada chave com que se assina a app, sempre com o pacote `pt.pixellusitano.paragemcerta.pessoal`:
   - **Lançamento:** a SHA-1 da chave de lançamento. Tira-a de um APK assinado com ela (o da última Release no GitHub serve, a chave é sempre a mesma). Descarrega o APK da última Release em github.com/DaewonPT/paragem-certa/releases (por exemplo `paragem-certa-v0.3.2.apk`, que vai para Transferências), abre a Linha de comandos nessa pasta e corre o comando com o nome verdadeiro do ficheiro:
     `C:\Android\sdk\build-tools\36.0.0\apksigner.bat verify --print-certs paragem-certa-v0.3.2.apk`
   - **Testes:** o mesmo comando num APK de debug feito no PC, para poderes experimentar o login em builds locais. Abre a Linha de comandos na pasta `codigo` (a de cima da `worker`). Se ainda não compilaste a app no PC, corre primeiro `gradlew.bat assemblePessoalDebug`. Depois corre:
     `C:\Android\sdk\build-tools\36.0.0\apksigner.bat verify --print-certs app\build\outputs\apk\pessoal\debug\app-pessoal-debug.apk`
   - Estes dois comandos não correm na pasta `worker/`. Em cada um, da linha "Signer #1 certificate SHA-1 digest:" copia só o código de 40 letras e números que vem a seguir, sem o texto antes. Se a consola o recusar, põe «:» entre cada par (AB:CD:EF:…).
   - A variante `play` (pacote `pt.pixellusitano.paragemcerta`, chave da Play) só entra quando a app for para a Play.
   - Nota: a Google apaga sozinha qualquer cliente OAuth que passe 6 meses sem uso e avisa-te por email 30 dias antes. O de testes é o que corre mais risco. Se receberes esse aviso, abre o cliente na consola e muda-lhe qualquer coisa (o nome, por exemplo), que isso já conta como uso. Se já tiver sido apagado, recupera-o na página das credenciais eliminadas (dá até 30 dias) ou cria-o outra vez com o mesmo pacote e a mesma SHA-1.
5. Para builds no PC, cria (ou abre, se já existir) o ficheiro `local.properties` na pasta `codigo`, ao lado do `settings.gradle.kts` (não na `worker/`; não vai para o git), e escreve lá a linha `GOOGLE_WEB_CLIENT_ID=o-teu-client-id`. Depois compila a app outra vez. Sem ele, a Conta (no Menu e o ícone da conta no Início) fica escondida a quem não tiver sessão aberta.
6. **Verificar a marca (opcional, só depois do passo 6, com a `/privacidade` no ar e o Audience em "In production").** Sem isto, quem entra com o Google vê só `pixellusitano.com` e não «Paragem Certa». Em Branding, no campo da página inicial («Application home page»), põe uma página tua em pixellusitano.com que explique o que a app faz (não pode ser só uma página de entrada) e tenha uma ligação para `https://paragemcerta.pixellusitano.com/privacidade`. Na Google Search Console, verifica o domínio `pixellusitano.com` com a mesma conta Google que é dona (Owner) ou editora (Editor) do projeto. Depois carrega em **Verify Branding**. Quando passar, carrega em **Publish branding**, senão o nome não aparece.

## 5. Segredos do Worker

Ainda na pasta `worker/`, um comando por segredo; cada um pede o valor e tu colas (não aparece no ecrã). Cola com o botão direito do rato, nunca com Ctrl+V: nesta pergunta escondida o Ctrl+V guarda o carácter de controlo da tecla em vez do texto, e o segredo fica estragado sem nenhum aviso. Em alternativa, põe os valores num ficheiro `NOME=valor` em UTF-8, fora da pasta `codigo`, e envia-os de uma vez com `npx wrangler secret bulk caminho-do-ficheiro` (com `-c wrangler.admin.jsonc` para o `pc-admin`):

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

- O primeiro cria as tabelas na base da UE (pede confirmação; responde `y`). Não faz cópia à parte: se algo correr mal, a D1 guarda sozinha os últimos 7 dias (Time Travel, `npx wrangler d1 time-travel`).
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

   Os quatro da etapa da comunidade juntam-se no passo 15.
3. Para publicar: **Actions → Servidor → Run workflow**, no ramo `main`. Da primeira vez, só no ponto 5 de «Publicar a 0.4.0», depois do passo 15: antes disso faltam segredos e o `publicar` pára a meio. O trabalho `publicar` (no GitHub aparece como «Publish to Cloudflare») espera pelo trabalho dos testes e dos tipos («Tests and types»), aplica as migrações na base remota, faz o deploy do Worker principal e a seguir o do `pc-admin`, enviando outra vez os segredos de cada um. Só corre assim, à mão e no `main`: um commit ou um pedido de alterações nunca publica nada.
4. O segredo `GOOGLE_WEB_CLIENT_ID` também serve ao `android.yml`: a Release assinada leva o id no APK. Sem ele, a Conta (no Menu e o ícone da conta no Início) fica escondida a quem não tiver sessão aberta.

## 8. Limite contra abusos em `/api/auth/*` (já está no servidor)

Não há nada para fazer neste passo. Não se usa uma regra de limite de pedidos da Cloudflare porque o plano grátis só deixa uma por zona e a da zona `pixellusitano.com` é a do site (`/api/feedback`). Em vez dela, o Worker tem o limitador `LIMITE_AUTH` (5 pedidos para `/api/auth/` em 10 segundos por IP, responde `429`), mas é aproximado: a Cloudflare conta-o em cada instância e junta as contas em segundo plano, por isso rajadas passam quase todas (testado a 08/11/2026: 2 recusas em 60 pedidos a uns 3 por segundo). Não conta com ele como travão.

- Uma entrada normal faz 2 ou 3 pedidos. Sem limite, um só IP podia gastar sozinho as 100 000 escritas por dia da base grátis (cada pedido de `/api/auth/google/nonce` escreve uma linha) e parar as entradas e a sincronização de toda a gente até à meia-noite UTC. Quem partilhar o IP e passar o limite espera 10 segundos.
- Na prática, quem fizer isto gasta ao mesmo ritmo os 100 000 pedidos por dia dos Workers da conta (partilhados com o `pc-admin` e o site), como com as leituras, que não têm limite por IP. A defesa é a do passo 18: ver o IP e bloqueá-lo com uma Custom rule (o plano grátis tem 5).

## 9. Ligar a conta na app

A entrada "Conta" do Menu só aparece com o interruptor ligado. Sem o `GOOGLE_WEB_CLIENT_ID` no APK (passos 4 e 7), a Conta do Menu e o ícone da conta no Início ficam escondidos a quem não tiver sessão aberta.

1. Em `worker/public/dados/config.json`, muda `"conta": false` para `"conta": true`. Antes do commit, ajusta também os testes do servidor: hoje contam com todos os interruptores desligados e falham se ligares algum. Neste caso são `worker/test/base.test.ts` e `worker/test/portao.test.ts`. Muda-os no mesmo commit e confirma com `npm test` na pasta `worker/`. Se os testes falharem, o trabalho `publicar` não corre e nada vai para o ar. Faz commit no `main` e `git push origin main`, espera que a corrida do Servidor aberta por esse push acabe e só então corre o **Servidor → Run workflow** do passo 7.
2. Confirma em `https://paragemcerta.pixellusitano.com/dados/config.json`. A 0.3.2 que está nos telemóveis não tem Conta: experimenta numa build 0.4.0 feita no PC. A app vai buscá-lo de cada vez que abre; abre-a uma vez com rede e aparece Menu → Conta. Para entrares com o Google nessa build, precisas do passo 4 (cliente Android de testes e `local.properties`). O APK de debug não se instala por cima da 0.3.2 (a chave é outra): desinstala-a primeiro ou usa outro telemóvel, e faz o mesmo no segundo telemóvel para veres os favoritos.
3. No dia de publicar a app, liga também `"conta"` em `app/src/main/assets/config.json`: é a cópia de reserva que a app usa na primeira abertura sem rede. Antes do commit, ajusta também, no mesmo commit, o teste da app que conta com essa cópia desligada (`ConfiguracoesTest.kt`, `semRedeUsaADaApp`). Faz commit e `git push origin main` e espera que o workflow **Android** fique verde no `main` antes de criares a tag: se falhar, a `v0.4.0` não gera o APK assinado nem a Release.

Experimenta de ponta a ponta antes de anunciar: entrar com o Google, ver os favoritos aparecerem num segundo telemóvel e apagar a conta.

## 10. Ativar o Zero Trust

A página de revisão usa o Zero Trust. Pede um cartão, mas o plano Free não cobra nada. Faz este passo antes da primeira publicação do passo 6.

1. Abre **Zero Trust** no painel da Cloudflare, escolhe o nome da equipa (passa a ser o endereço `https://<equipa>.cloudflareaccess.com`) e o plano **Free**. Também pede dados de pagamento. Guarda o nome da equipa: precisas dele no passo 14.
2. **Alerta de gasto em 1 $:** Manage Account → Billing → Billable Usage, cria um alerta de gasto de 1 $. O de omissão é de 10 $. O alerta só avisa por email, e só no dia seguinte; não corta nada. Com o modelo D nada deve cobrar: se o alerta chegar, alguma coisa paga foi ligada por engano.

## 11. Tirar o endereço `fotos.pixellusitano.com` (só uma vez)

O servidor já não usa o R2: as fotos que as pessoas enviavam saíram da app, e o interruptor `fotos` nunca foi ligado, por isso nunca se guardou nenhuma. Os passos 11, 12 e 13 apagam o que ficou da 0.4.0 no painel da Cloudflare. Faz-os só depois de o **Servidor → Run workflow** (passo 7) publicar os dois Workers sem R2 e de, no painel, nenhum dos dois mostrar binding de R2 (Workers & Pages → o Worker → Bindings).

A ordem conta, e não é a dos números dos passos:

1. No GitHub, apaga os segredos `CF_PURGE_TOKEN` e `CF_ZONA` (passo 15).
2. Tira a permissão R2 do `CLOUDFLARE_API_TOKEN` (My Profile → API Tokens → o token do passo 7 → Edit), se a tiver.
3. `wrangler secret delete` dos dois, nos dois Workers (passo 13).
4. Revoga o token da cache (passo 13).
5. Remove o domínio `fotos.pixellusitano.com` do `pc-fotos` (este passo).
6. Apaga os buckets `pc-fotos` e `pc-fotos-pendentes` (passo 12).
7. Tira as linhas `CF_*` dos teus ficheiros de segredos.
8. Mantém o alerta de 1 $ (passo 10).

Este passo é o ponto 5:

1. R2 Object Storage → `pc-fotos` → Settings → **Custom Domains** → no menu ao lado de `fotos.pixellusitano.com`, **Remove**.
2. Na zona `pixellusitano.com` → DNS → Records, confirma que já não há o registo `fotos`; se ficou, apaga-o.
3. `curl.exe -I https://fotos.pixellusitano.com/teste.jpg` já não pode dar `200`.

## 12. Apagar os buckets das fotos (só uma vez)

Depois do passo 11, na pasta `worker/`:

```
npx wrangler r2 bucket delete pc-fotos-pendentes --jurisdiction eu
npx wrangler r2 bucket delete pc-fotos --jurisdiction eu
```

Se a wrangler do PC usar o `CLOUDFLARE_API_TOKEN` (e não o `wrangler login`), depois do ponto 2 do passo 11 o token já não tem a permissão do R2 e o apagar pode ser recusado. Nesse caso, apaga os buckets vazios no painel: R2 Object Storage → o bucket → Settings → Delete bucket.

- Um bucket só se apaga vazio. Se a wrangler disser que não está, abre-o no painel, apaga o que lá estiver (a `teste.jpg` de 08/11, se ficou) e repete.
- No fim, o painel do R2 não mostra nenhum bucket. Sem buckets, o R2 não cobra nada. Nunca voltes a criar um: o R2 não tem teto de gasto.

Daqui em diante, nunca publiques a partir de um ramo ou tag anteriores à 0.5.2, nem voltes um Worker a uma versão anterior: a configuração antiga volta a criar os buckets.

## 13. Os segredos `EMAIL_AUTOR` e `CHAVE_MARCA_EMAIL`

Para os avisos ao autor e para a marca dos emails, os dois Workers precisam de mais dois segredos:

1. **`EMAIL_AUTOR`:** o teu email. Recebe o aviso das denúncias de perigo e o resumo diário, e é o único que o `pc-admin` deixa entrar. Tem de ser o mesmo endereço que puseres na política do Access (passo 14).
2. **`CHAVE_MARCA_EMAIL`:** uma chave secreta com que os dois Workers fazem a marca (HMAC-SHA-256) dos emails das denúncias, decisões e bloqueios. Gera-a uma vez, com `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`, e guarda-a na `Chave Privada`. Vai para o GitHub (passo 15) e para os dois Workers com o mesmo valor. **Nunca se muda:** com outra chave, os bloqueios guardados deixam de reconhecer os emails. Sem ela, o servidor recusa em vez de usar um hash simples.

Um comando por segredo e por Worker (cada um pede o valor e tu colas com o botão direito, como no passo 5, ou usa o `secret bulk` do passo 5):

```
npx wrangler secret put EMAIL_AUTOR
npx wrangler secret put CHAVE_MARCA_EMAIL
npx wrangler secret put EMAIL_AUTOR -c wrangler.admin.jsonc
npx wrangler secret put CHAVE_MARCA_EMAIL -c wrangler.admin.jsonc
```

Os dois primeiros são do Worker principal (`paragem-certa`); os dois com `-c wrangler.admin.jsonc` são do `pc-admin`. O `pc-admin` ainda não existe: no primeiro deles a wrangler pergunta se o crias, e respondes **sim** (como no passo 5).

No fim do passo 14, cada Worker tem de ter estes cinco segredos (`npx wrangler secret list` e `npx wrangler secret list -c wrangler.admin.jsonc` mostram os nomes, nunca os valores):

- `paragem-certa`: `RESEND_API_KEY`, `RESPONSAVEL` e `GOOGLE_WEB_CLIENT_ID` (passo 5), `EMAIL_AUTOR` e `CHAVE_MARCA_EMAIL` (este passo);
- `pc-admin`: `RESEND_API_KEY`, `ACCESS_EQUIPA` e `ACCESS_AUD` (passo 14), `EMAIL_AUTOR` e `CHAVE_MARCA_EMAIL` (este passo).

**Se ainda tens os da 0.4.0** (`CF_PURGE_TOKEN` e `CF_ZONA`, que limpavam a cache das fotos): depois de o Servidor publicar sem eles, e pela ordem do passo 11, apaga-os com `npx wrangler secret delete CF_PURGE_TOKEN`, `npx wrangler secret delete CF_ZONA` e os mesmos com `-c wrangler.admin.jsonc`. Depois, em My Profile → API Tokens, revoga o token da limpeza da cache.

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
2. A primeira publicação, à mão: `npx wrangler deploy -c wrangler.admin.jsonc`. Se a conta ainda não tiver endereço `workers.dev`, a wrangler pergunta "Would you like to register a workers.dev subdomain now?": responde **sim**, escreve um nome neutro (só minúsculas, números e hífenes; fica à vista de todos no endereço, por isso nada do teu nome nem do email) e, em "Ok to proceed?", responde outra vez **sim**. Se disseres que não, a publicação para e tens de repetir o comando. No fim mostra o endereço `https://pc-admin.<subdomínio>.workers.dev`. As pré-visualizações já vêm desligadas na configuração.
3. **One-time PIN:** Zero Trust → Integrations → Identity providers → junta **One-time PIN** (o código chega por email).
4. **Proteger o Worker:** primeiro cria a política: Zero Trust → Access controls → Policies → Add a policy, nome `só o autor`, Action **Allow**, Include → **Emails** → o teu email (o `EMAIL_AUTOR`), e guarda. Depois vai a Workers & Pages → `pc-admin` → separador **Access** → **Protect this Worker behind Access** → **All traffic**. Em Authentication policy escolhe `só o autor` e carrega em **Apply Access**. Nunca escolhas "Email domain" (deixava entrar qualquer pessoa com email desse domínio) nem uma política só com "Login Methods" (deixava entrar qualquer pessoa que consiga um PIN).
   - Se o plano grátis não tiver o Access no Worker, cria no Zero Trust (Access → Applications) uma aplicação **Self-hosted** para o hostname `pc-admin.<subdomínio>.workers.dev`, com a mesma política.
5. **A AUD:** vai a Zero Trust → Access controls → Applications → a aplicação do `pc-admin` → Edit → **Additional settings** e copia a **Application Audience (AUD) Tag**. Depois substitui o `por-preencher`: `npx wrangler secret put ACCESS_AUD -c wrangler.admin.jsonc`. Se apagares e voltares a criar a aplicação, a AUD muda e tens de a pôr outra vez (aqui e no GitHub).
6. **Prova:** numa janela privada do browser, o endereço do `pc-admin` mostra a página de entrada do Cloudflare Access, nunca a página de revisão. Se aparecerem duas maneiras de entrar (Cloudflare e One-time PIN), usa a do **One-time PIN**: escreve o teu email, carrega em **Send login code**, mete o código que chega e aparece a página de revisão. E `curl.exe -i https://pc-admin.<subdomínio>.workers.dev/` nunca devolve a página de revisão (só um redirecionamento ou um 403).

Numa denúncia, se quiseres bloquear a conta, bloqueia antes de carregares em Decidir: depois de decidires, a denúncia sai da lista e o botão vai com ela.

## 15. Segredos novos no GitHub

Para o trabalho `publicar` (passo 7) enviar os segredos aos dois Workers, junta no GitHub (Settings → Secrets and variables → Actions → New repository secret) quatro segredos novos, com os mesmos valores dos passos 13 e 14: `EMAIL_AUTOR`, `CHAVE_MARCA_EMAIL`, `ACCESS_AUD` e `ACCESS_EQUIPA`. O `RESEND_API_KEY` já lá está desde o passo 7. Em `ACCESS_AUD` vai a AUD verdadeira (ponto 5 do passo 14), nunca o `por-preencher`. Sem o `CHAVE_MARCA_EMAIL`, a corrida do Servidor pára antes de publicar. Se lá estiverem o `CF_PURGE_TOKEN` e o `CF_ZONA` da 0.4.0, apaga-os (Remove), mas só depois de o Servidor publicar sem eles (ordem do passo 11).

## 16. Ligar a comunidade e as correções

Só no fim, depois do parecer do advogado sobre o DSA.

1. Em `worker/public/dados/config.json`, em `interruptores`, muda para `true` os que queres ligar: `"comunidade"` e `"correcoes"`. O `correcoes` precisa do `comunidade` também ligado: sem ele, a app não mostra o botão "Corrigir dados". Antes do commit, ajusta também os testes do servidor: hoje contam com todos os interruptores desligados e falham se ligares algum. São `worker/test/base.test.ts` e `worker/test/portao.test.ts`, mais `relatos.test.ts` e `denuncias.test.ts` (`comunidade`) e `correcoes.test.ts` (`correcoes`), todos em `worker/test/`. Muda-os no mesmo commit e confirma com `npm test` na pasta `worker/`. Se os testes falharem, o trabalho `publicar` não corre e nada vai para o ar. Faz commit no `main` e `git push origin main`, espera que a corrida do Servidor aberta por esse push acabe e só então corre o **Servidor → Run workflow** (passo 7). Confirma em `https://paragemcerta.pixellusitano.com/dados/config.json`.
2. No dia de publicar a app, liga os mesmos em `app/src/main/assets/config.json` (a cópia de reserva da primeira abertura sem rede). Primeiro o servidor e só depois a app: nunca um interruptor ligado na app e desligado no servidor. Antes do commit, ajusta também os testes da app que contam com a cópia desligada (`RelatarTest.kt`, `CamadaRelatosTest.kt` e `ComunidadeTest.kt`). Faz commit e `git push origin main` e espera que o workflow **Android** fique verde no `main` antes de criares a tag: se falhar, a tag não gera o APK assinado nem a Release.
3. As fotos de rua do Panoramax (passo 21) aparecem sempre: não dependem de nenhum interruptor.

## 17. Pedidos de "Apagar conta" por email

Quem não tem a app pede por email para contact@pixellusitano.com, como diz a página `apagar-conta.html`. **Ninguém te avisa destes pedidos:** só aparecem nessa caixa de correio (não entram no resumo diário nem na página de revisão), por isso vê-a pelo menos uma vez por semana. O RGPD dá **um mês** a contar do dia em que o pedido chega (art. 12.º, n.º 3): um pedido de 15/01 tem de estar tratado até 15/02.

1. **Confirma que o pedido é do dono da conta.** O remetente de um email pode ser falsificado, por isso não chega ver de onde veio. Escreve tu, de contact@pixellusitano.com, um email novo (não carregues em Responder) para o endereço da conta, a perguntar se foi essa pessoa que pediu para apagar a conta. Só avanças quando a confirmação chegar desse endereço.
2. Abre a página de revisão do `pc-admin` (passo 14), escreve esse email em **Apagar conta por email** e carrega em "Apagar conta". Apaga todas as contas desse email, com os relatos, as confirmações e as correções. A procura é pelo endereço exato: se disser que não há conta, pede à pessoa o endereço com que entrou (com o + ou os pontos, se os usou).
3. Responde ao pedido a dizer que a conta foi apagada. A página não envia nenhum email.
4. **Guarda a troca de emails** (o pedido, a tua pergunta, a confirmação e a resposta final): é a prova de que confirmaste quem pediu e de que cumpriste o prazo. Não a apagues.

Nunca apagues pelo `wrangler d1 execute … DELETE`: o `pc-admin` apaga numa só transação todas as contas desse email e o que é delas; à mão é fácil esquecer uma tabela ou uma conta.

## 18. Se alguém gastar a quota de propósito

O plano grátis dá **100 000 pedidos por dia aos Workers da conta inteira**: este (`paragem-certa`), o `pc-admin` e o do site (`pixellusitano`) gastam da mesma quota. As leituras da comunidade não têm limite por IP (nas redes móveis muita gente partilha o mesmo IP), por isso alguém pode gastá-la de propósito. Quando acaba, deixa de responder tudo o que passa pelo Worker: aqui, qualquer `/api/*` (entrar, sincronizar, relatos, Carris ao vivo) e a `/privacidade`; e também o `pc-admin` e o `/api/feedback` do site. Os ficheiros estáticos (`/termos`, `/apagar-conta`, `/dados/…`) continuam a abrir. A quota volta às 00:00 UTC (meia-noite em Lisboa no inverno, 1h no verão). Não cobra nada; nunca passes para o Workers Paid para fugir ao limite. **Ninguém te avisa a tempo:** o primeiro sinal deve ser um email da Cloudflare sobre os limites da D1, ou alguém a dizer que não consegue entrar. Se desconfiares de pedidos a mais, as estatísticas do Worker não te ajudam: não mostram IPs. Vê de onde vêm na zona `pixellusitano.com`: **Security → Analytics**. Filtra pelo hostname `paragemcerta.pixellusitano.com` e procura o IP que se repete nas estatísticas de topo (Source IP) ou nos registos de amostra (**Sampled logs**). Para o bloquear: com o filtro **Source IP** igual a esse IP, carrega em **Create custom security rule**. Ou vai a **Security → Security rules → Create rule → Custom rules** (no painel antigo: **Security → WAF → Custom rules**), campo **IP Source Address**, **equals**, esse IP, ação **Block** e **Deploy**. Se for um endereço IPv6, bloqueia o /64 inteiro: operador **is in** e os quatro primeiros blocos com `::/64` (por exemplo `2001:db8:1:2::/64`), porque cada ligação tem um /64 e muda de endereço lá dentro. Tira a regra no dia seguinte: nas redes móveis o IP é partilhado por muita gente e um bloqueio comprido apanha inocentes. Os nomes dos menus mudam de vez em quando; segue o que a Cloudflare mostrar.

## 19. Carris ao vivo

O `/api/rt/carris` lê o feed oficial das posições da Carris (GTFS-RT), tira as posições com mais de 5 minutos, as com hora mais de 1 minuto no futuro e as que não têm nem paragem nem número de paragem, e guarda o resultado 30 segundos na cache da Cloudflare, que é de cada centro de dados. A Carris recebe normalmente um pedido a cada 30 segundos de cada centro de dados (a cache não é garantida: pode haver mais, por exemplo quando chegam vários pedidos ao mesmo tempo com a cache vazia; em Portugal, quase sempre só o de Lisboa), por muita gente que tenha a app aberta; ninguém lhe pede nada diretamente do telemóvel. A app mostra os autocarros no mapa e no ecrã de uma linha, e "ao vivo, a N paragens" nas chegadas. Não leva nenhum segredo: não há nada para pôr na Cloudflare nem no GitHub.

O interruptor `carrisAoVivo` fica `false` até a Carris responder **por escrito** ao email enviado a 10/11/2026 para nap.dados@carris.pt, com Cc para atendimento@carris.pt (o texto está em `rascunhos/email-carris.md`, fora do repositório). Com ele desligado, o `/api/rt/carris` dá 403 `desligado` e a app não mostra nem pede nada disto. Se a Carris pedir um crédito, junta-o ao ecrã Sobre antes de ligar.

Com o OK da Carris (e a 0.4.0 já publicada):

1. Em `worker/public/dados/config.json`, em `interruptores`, muda `"carrisAoVivo"` para `true`. Antes do commit, ajusta também os testes do servidor: hoje contam com todos os interruptores desligados (`worker/test/base.test.ts`, `worker/test/portao.test.ts` e `worker/test/carris.test.ts`). Muda-os no mesmo commit e confirma com `npm test` na pasta `worker/`. Faz commit no `main` e `git push origin main`, espera que a corrida do Servidor aberta por esse push acabe e só então corre o **Servidor → Run workflow** (passo 7).
2. Confirma: `https://paragemcerta.pixellusitano.com/dados/config.json` mostra `true` e `curl.exe https://paragemcerta.pixellusitano.com/api/rt/carris` devolve `{"atualizado":…,"veiculos":[…]}` com autocarros.
3. No dia de publicar a app, liga o mesmo em `app/src/main/assets/config.json`. Primeiro o servidor e só depois a app (como no passo 16). Se algum teste da app contar com ele desligado, ajusta-o no mesmo commit; faz commit e `git push origin main` e espera que o workflow **Android** fique verde no `main`.
4. Para desligar, `false` nos mesmos sítios e outra publicação; a app deixa de mostrar os autocarros na abertura seguinte, com rede.

**O Metro de Lisboa fica para uma versão seguinte.** Até 10/11/2026, o servidor da API do Metro, na porta 8243, só mandava o certificado do site, e a Cloudflare recusava essas ligações (erro 526). A 11/11/2026 o Metro passou a mandar a cadeia completa (verificado com `openssl s_client`). Os tempos de espera e o estado das linhas entram numa versão seguinte; até lá não há chave nem segredo do Metro para pôr, e o interruptor `metro` fica `false`. As linhas e o horário do Metro continuam a vir do catálogo e do Transitous.

## 20. Linhas dos outros operadores

O catálogo das linhas de Carris, Metro, CP, Fertagus, Transtejo, MobiCascais e TCB Barreiro é o ficheiro `/dados/linhas.json`, gerado pelo GitHub Actions a partir dos GTFS que o Transitous já processou. Nunca está no repositório (`.gitignore`). As linhas da Carris Metropolitana não vêm daqui: a app lê-as à API da própria Carris Metropolitana. Sem catálogo no ar, a app mostra só as da Carris Metropolitana e continua a funcionar.

Quem o gera:

- **O trabalho `publicar`** (passo 7) gera-o antes do deploy. Se o Transitous falhar, fica a cópia que já está no ar; só pára se não houver nenhuma (a primeira publicação com o Transitous em baixo: corre outra vez mais tarde).
- **O workflow `Dados`** (`dados.yml`) corre à segunda-feira às 04:17 UTC e trata dos dois ficheiros gerados: este e o das fotos de rua (passo 21). Faz o checkout só do **último commit que o Servidor publicou** (o da última corrida à mão que acabou bem), gera o catálogo, gera as fotos ou copia as que estão no ar, compara os dois com o que está no ar (sem o campo `geradoEm`) e **só faz o deploy se algum mudou**. Assim nunca publica código que ainda não publicaste. Se a última corrida do Servidor falhou ou ainda está a correr, pára com um erro: corre o Servidor outra vez e depois este.

Ao publicar a 0.4.0 (ponto 5 de "Publicar a 0.4.0"):

1. Só depois de o **Servidor → Run workflow** do ponto 5 acabar bem (verde): no GitHub, no repositório, Settings → Secrets and variables → Actions → separador **Variables** → New repository variable, nome `SERVIDOR_PUBLICADO`, valor `sim`. É uma variável, não um segredo. Sem ela o `Dados` não faz nada (por isso antes da 0.4.0 não corre nada). Se a criares antes, a corrida de segunda-feira pode apanhar o Servidor a meio e falhar.
2. A primeira publicação à mão do passo 6 não leva o `linhas.json` (não está no repositório). Se a fizeste, corre logo a seguir o **Servidor → Run workflow** (passo 7), que o gera e o põe no ar.
3. Para atualizar já, sem esperar pela segunda-feira: **Actions → Dados → Run workflow**, no `main`.

**Os 60 dias:** num repositório público, o GitHub desliga sozinho os workflows agendados ao fim de 60 dias sem atividade (commits, por exemplo). Quando isso acontece, o catálogo fica velho, mas a app continua a funcionar com ele. Para voltar a ligar: **Actions → Dados** e carrega em **Enable workflow** no aviso que lá aparece.

## 21. Fotos de rua

As fotos de rua do ecrã da paragem e da bússola vêm do Panoramax (fotos abertas, sem chave nem conta). O ficheiro `/dados/fotos-de-rua.json` diz que foto mostrar em cada paragem: a mais recente a menos de 30 m e virada para ela. Quem o gera é o `worker/ferramentas/gerar-fotos-rua.mjs`, no GitHub Actions. Nunca está no repositório (`.gitignore`) e, como um deploy apaga do ar o que não leva, vai em todos os deploys. Sem ficheiro no ar, a paragem e a bússola ficam sem foto e a app continua a funcionar.

Quando se gera:

- **O workflow `Dados`** gera-o na primeira segunda-feira do mês (dias 1 a 7). Nas outras segundas-feiras, copia o que está no ar.
- **O trabalho `publicar`** (passo 7) copia o que está no ar e só gera se não houver nada.
- **Se gerar falhar** (Panoramax em baixo, uma resposta que não se percebe, menos de 500 fotos, a API da Carris Metropolitana em baixo), fica o que está no ar, com um aviso na corrida. Sem nada no ar, fica um ficheiro vazio, e a segunda-feira seguinte tenta outra vez. Nunca pára uma publicação.

**Para gerar já:** **Actions → Dados → Run workflow**, no `main`. À mão, gera sempre.

**Para esconder uma foto má:** o id é o que vem depois de `pic=` na página da foto (a ligação do crédito na app). Junta-o à lista de `worker/ferramentas/fotos-excluidas.json`, por exemplo `["94c6834a-70ea-4038-9517-5a4a59570efb"]`, faz commit no `main` e envia-o para o GitHub (`git push origin main`). O `Dados` só usa código já publicado: corre primeiro o **Servidor → Run workflow**, no `main`, espera que acabe com o visto verde e só depois corre o **Dados → Run workflow**, no `main`. Se correres o `Dados` enquanto o Servidor ainda está a correr, ele pára com um erro: espera e corre-o outra vez.

## Custos e limites

**Modelo D: o servidor nunca custa dinheiro.**

- **Nunca ligues o Workers Paid**, nem para fugir a um limite. No plano grátis, os Workers e a D1 param no limite em vez de cobrar: 100 000 pedidos por dia para a conta inteira (este Worker, o `pc-admin` e o do site), até às 00:00 UTC. O passo 18 diz o que fazer.
- **O R2 não se usa.** Nunca cries buckets: o R2 não tem teto de gasto. Os da 0.4.0 apagam-se nos passos 11 e 12.
- A Resend fica no plano grátis (100 emails por dia, 3000 por mês): no limite pára, não cobra (passo 3).
- O Zero Trust (a página de revisão) pede cartão, mas o plano Free não cobra. O alerta de 1 $ do passo 10 é a rede de segurança.
