# Publicar o servidor da Paragem Certa

Guia para o autor. Nada disto foi feito por nós: o servidor (`worker/`) só corre no PC até fazeres estes passos, e só os fazes depois do teu "publica". Os comandos correm numa janela de terminal dentro da pasta `worker/` (`npm ci` primeiro, uma vez).

Precisas de: a conta da Cloudflare (com a zona `pixellusitano.com`), uma conta na Resend, uma conta na Google Cloud, o repositório no GitHub e o Node 24.

**Regra de ouro:** nenhuma chave, token, nome ou id do cliente da Google vai para o repositório nem para uma conversa. Colas tu cada valor, no sítio indicado.

A ordem conta: o `wrangler.jsonc` exige os três segredos (`secrets.required`), por isso o `wrangler deploy` falha enquanto algum faltar. Os passos 3 e 4 têm de vir antes do 5.

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

O remetente que o servidor usa é `Paragem Certa <codigo@mail.pixellusitano.com>`.

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
3. Para publicar: **Actions → Servidor → Run workflow**, no ramo `main`. O trabalho `publicar` espera pelos testes e pelos tipos, aplica as migrações na base remota e faz o deploy, enviando outra vez os três segredos.
4. O segredo `GOOGLE_WEB_CLIENT_ID` também serve ao `android.yml`: a Release assinada leva o id no APK. Sem ele, o botão do Google não aparece.

## 8. Regra grátis contra abusos em `/api/auth/*`

Na Cloudflare, na zona `pixellusitano.com`: Security → WAF → Rate limiting rules → Create rule. O plano grátis só deixa uma regra, por IP, com janelas de 10 s.

- Nome: `auth`.
- Quando: **Hostname** igual a `paragemcerta.pixellusitano.com` **e** **URI Path** começa por `/api/auth/` (em texto: `(http.host eq "paragemcerta.pixellusitano.com" and starts_with(http.request.uri.path, "/api/auth/"))`).
- Contar por **IP**: 20 pedidos em 10 segundos. Ação **Block**, durante 10 segundos.
- O valor é folgado de propósito: nas redes móveis muita gente partilha o mesmo IP. Se vires abusos, baixa-o.

Os limites de email por endereço (3 por hora) e por dia (70) são contados no servidor e não dependem desta regra.

## 9. Ligar a conta na app

A entrada "Conta" do Menu só aparece com o interruptor ligado.

1. Em `worker/public/dados/config.json`, muda `"conta": false` para `"conta": true`, faz commit no `main` e corre o **Servidor → Run workflow** do passo 7.
2. Confirma em `https://paragemcerta.pixellusitano.com/dados/config.json`. A app vai buscá-lo de cada vez que abre; depois de a abrires (uma vez, com rede) aparece Menu → Conta.
3. No dia de publicar a app, liga também `"conta"` em `app/src/main/assets/config.json`: é a cópia de reserva que a app usa na primeira abertura sem rede.

Experimenta de ponta a ponta antes de anunciar: entrar com um código por email, entrar com o Google, ver os favoritos aparecerem num segundo telemóvel e apagar a conta.

## Custos e limites

Tudo no plano grátis da Cloudflare e da Resend: o Worker e a D1 param no limite (100 000 pedidos por dia para a conta inteira, até à meia-noite UTC) em vez de cobrar. Este servidor não usa o R2 nem o Zero Trust (fotos e página de revisão); esses, que pedem cartão, ficam para o guia da etapa 3.
