# Paragem Certa — especificação do desenho

- **Data:** 27/10/2026
- **Autor:** Alize.On.Me (Pixel Lusitano)
- **Estado:** para revisão

Os termos técnicos estão explicados no glossário (secção 13).

## 1. Objetivo

A Paragem Certa é uma app Android de transportes públicos para Portugal. Recria de raiz as funções da Moovit (pacote `com.tranzmate`), incluindo as da subscrição Moovit+, com estas regras:

- totalmente gratuita, sem anúncios nem subscrições;
- visual próprio, mais apelativo e intuitivo;
- toda em português de Portugal.

O nome é a promessa: avisar a pessoa para sair na paragem certa.

**Recriação limpa.** Tudo foi estudado só a partir de fontes públicas, que estão em `replica/recon.md`. Não se usa código, imagens, textos nem APIs privadas da Moovit. O APK da Moovit não é aberto nem decompilado.

**Critérios de sucesso:**

1. O autor troca a Moovit pela Paragem Certa no dia a dia na Área Metropolitana de Lisboa.
2. Ao abrir a app, as chegadas da paragem favorita aparecem sem nenhum toque.
3. O aviso de saída funciona com o ecrã desligado. É validado numa viagem real de autocarro e noutra de Metro.
4. A app fica pronta para a Play Store: política de privacidade, apagar conta, ficha de segurança de dados e nada copiado da Moovit.

## 2. Decisões tomadas

| Tema | Decisão |
|---|---|
| Público | Primeiro para uso pessoal (APK instalado à mão), depois na Play Store |
| Zona inicial | Área Metropolitana de Lisboa. O objetivo é o país todo |
| Primeira função | Chegadas em tempo real |
| Contas | Opcionais, entrando com Google ou com email e código. A app funciona toda sem conta |
| Código | Público no GitHub, com licença **GPL-3.0**. O nome e o logótipo ficam reservados. As chaves nunca entram no repositório |
| Tecnologia | **App nativa:** Kotlin + Jetpack Compose. Ganhou uma comparação feita por 3 avaliadores (notas de 1 a 10): nativa 7,8, Capacitor 6,5, Flutter 5,5, React Native 5,3 |
| Visual | Ecrã de início "mapa primeiro". Estilo **Azulejo** no modo claro e **Azulejo noite** no modo escuro, conforme o modo do telemóvel. Minutos com cor conforme o atraso |
| Custo | Custo zero para manter a app a funcionar. A única exceção é a conta de programador da Google Play (taxa única de 25 $), na fase 6 |
| Fora | Anúncios, subscrição, assistente de IA, outros idiomas e funções que dependem de parcerias ou de dados da Moovit (secção 4) |

## 3. Âmbito por fases

Cada fase termina num APK que se usa no dia a dia, com nota de versão em português (`PATCHNOTES.md`, a mais recente primeiro).

### Fase 0 — Preparação

**Conteúdo:**

- ferramentas no PC, instaladas com autorização do autor (secção 11);
- repositório público e compilação automática no GitHub Actions;
- esqueleto da app com o estilo Azulejo;
- ecrã "Sobre e fontes de dados";
- instruções para o desenho do logótipo e do ícone.

**Feita quando:** o APK de esqueleto se instala no telemóvel do autor.

### Fase 1 — versão 0.1 "Chegadas"

**Conteúdo:**

- **Início:** mapa com paragens e veículos ao vivo. O painel tem as paragens favoritas e "perto de ti".
- **Ecrã da paragem:**
  - chegadas com as cores de atraso;
  - filtro por linha;
  - perturbações ativas, com ícone de gravidade (sempre com texto) junto a cada linha afetada, que ao tocar abre o detalhe;
  - ícone de cadeira de rodas na paragem e em cada chegada, quando se sabe.
- **Veículos:** tocar num veículo no mapa mostra a linha, o destino e "último sinal há X s". Se o sinal tiver mais de 2 min, o ícone fica esbatido.
- **Pesquisa:**
  - paragens;
  - linhas da Carris Metropolitana pelo número ou nome. As linhas dos outros operadores aparecem a partir das paragens.
  - Tocar numa linha encontrada mostra as paragens dela no mapa e as chegadas na paragem mais próxima. O ecrã completo da linha chega na fase 4.
- **Favoritos:** paragens favoritas guardadas no telemóvel.
- **Primeira abertura:** explica e pede a localização, só enquanto a app está a ser usada. Se for recusada, o mapa abre em Lisboa.
- **"Reportar erro" e "Enviar opinião":** abrem a app de email com contact@pixellusitano.com e com o assunto e o texto já preenchidos (versão da app, número da paragem ou da linha). Não há servidor.
- **Atualizador automático** da variante pessoal.
- **Fontes:**
  - Carris Metropolitana em tempo real;
  - Carris, Metro de Lisboa, CP, Fertagus, MTS e Transtejo pelo Transitous, com tempo real onde existir e horário no resto.

**Feita quando:**

- durante 7 dias seguidos, o autor não abre a Moovit para ver chegadas;
- ao abrir a app, as chegadas da paragem favorita aparecem sem toques;
- não fica nenhum erro bloqueante por resolver.

### Fase 2 — versão 0.2 "Rotas"

**Conteúdo:**

- Pesquisa de moradas e locais.
- **Rotas de A para B:**
  - várias opções comparadas em cartões (secção 5.2), com etiquetas ("mais rápida", "menos a pé", "direta", "transbordo apertado");
  - perturbações nas rotas;
  - escolha da hora de partida ou de chegada.
- **Filtros:** menos a pé, menos transbordos, acessível e meios de transporte. As preferências ficam guardadas e passam a ser a escolha habitual.
- **Locais:** casa, trabalho e outros locais com nome. Adicionam-se pela pesquisa ou pelo mapa, e pode-se mudar o nome, reordenar e apagar.
- **Toque longo no mapa:** "Ir para aqui", "Partir daqui" e "Guardar local", também disponíveis pela pesquisa para o TalkBack.
- **Cartões no Início:** "Ir para casa" e "Ir para o trabalho" (próxima ligação e duração, só a mais de 500 m), e "Repetir a última rota".
- Viagens recentes.
- **Partilhar rota:** envia, pela partilha do Android, um texto com a origem, o destino, as horas e cada etapa.
- **Atalho "Abrir app de TVDE":** abre a app instalada ou a loja.
- **"Apagar dados deste telemóvel":** apaga favoritos, recentes e cache, com confirmação.

**Feita quando:**

- as 10 viagens de `docs/testes/viagens.md` (origem, destino e hora) se comparam, à mesma hora, com um planeador de referência;
- cada uma dá uma opção no máximo 10 min mais lenta e sem ligações impossíveis;
- 2 delas usam a hora de chegada e 2 usam o filtro "acessível" ou "menos transbordos".

### Fase 3 — versão 0.3 "Viagem ao vivo"

**Conteúdo:**

- Viagem ao vivo etapa a etapa, com aviso de saída (secção 7), incluindo etapas a pé e transbordos.
- **"Avisa-me quando estiver a X min":** pode seguir várias linhas ao mesmo tempo (secção 7.5).
- Partilhar a hora de chegada por mensagem.
- **Vista ao vivo:** fotos de rua da paragem a partir de fontes abertas (Mapillary e Panoramax, onde houver), e a bússola "Encontrar a paragem", com seta e distância.
- **Primeira viagem:** pede a permissão de notificações e mostra como tirar a app da poupança de bateria do fabricante.

**Feita quando:**

- Numa viagem real de autocarro e noutra de Metro, ambas com o ecrã desligado:
  - "Prepara-te" chega antes da penúltima paragem;
  - "Sai agora" chega antes de o veículo parar no destino.
- Uma das viagens tem um transbordo.
- Um aviso de chegada dispara com a app fechada.
- A vista ao vivo mostra uma foto numa paragem com cobertura.

### Fase 4 — versão 0.4 "Conta e extras"

**Conteúdo:**

- Entrar com Google ou com email, sincronizar favoritos e apagar conta (secção 8).
- **Separador Linhas:**
  - lista por meio de transporte (autocarro, metro, comboio, barco, elétrico) e, dentro de cada um, por operador, com pesquisa no topo;
  - "Mapa oficial da rede" de cada operador, que abre a página do operador no navegador.
- **Ecrã da linha:**
  - percurso no mapa;
  - lista de paragens com chegadas;
  - horário completo do dia e primeira e última partida;
  - estrela de favorito, com escolha do sentido e da paragem habitual (as próximas chegadas dela aparecem no Início).
- **Perturbações:**
  - lista por operador, com datas e filtro por meio de transporte;
  - notificações das perturbações novas nas linhas favoritas (secção 6.3).
- **No mapa:**
  - bicicletas e trotinetes (onde houver dados abertos) e posições dos autocarros da Carris;
  - botão de camadas para mostrar ou esconder veículos, bicicletas e trotinetes.
- **Widgets** das próximas chegadas (secção 6.3).
- **Metro de Lisboa em tempo real** pela API oficial, só se funcionar (secção 6.1).

**Feita quando:** uma conta é criada, sincronizada entre dois telemóveis e apagada, e um widget mostra chegadas atualizadas.

### Fase 5 — versão 0.5 "País todo"

**Conteúdo:**

- Porto e resto do país (secção 6.1).
- Rotas entre regiões (CP de longo curso e expressos, onde o Transitous os tiver).
- Mapa offline por região.
- Horários guardados no telemóvel para usar sem rede (secção 6.3).

**Feita quando:**

- 3 rotas no Porto e 3 fora das áreas metropolitanas, planeadas sem sair de casa, dão resultados;
- uma rota de Lisboa para o Porto dá resultados;
- uma paragem do Porto mostra os autocarros da STCP no mapa;
- em modo de avião, o mapa offline abre e uma paragem favorita mostra o horário guardado.

### Fase 6 — versão 1.0 "Play Store"

**Conteúdo:**

- política de privacidade e página web para apagar conta;
- ficha de segurança de dados e textos e capturas da loja;
- acordo com a equipa do Transitous e clarificação das licenças com os operadores (secção 12);
- conta de programador e variante "play";
- **teste fechado** com pelo menos 12 testadores durante 14 dias seguidos, obrigatório para contas pessoais novas antes de publicar;
- **declaração dos serviços em primeiro plano** na Play Console (tipos `location` e `specialUse`), com descrição e vídeo de cada um.

**Feita quando:** a app é publicada, depois de o autor dizer "publica".

### Funções do Moovit+ incluídas de graça

A Paragem Certa oferece de graça estas funções da subscrição Moovit+:

- sem anúncios;
- comparar rotas (fase 2);
- avisos de chegada (fase 3);
- partilhar a hora de chegada por mensagem (fase 3);
- vista ao vivo (fase 3, com fontes abertas).

## 4. O que fica de fora, e porquê

| Função da Moovit | Motivo |
|---|---|
| Assistente de IA (MoovitAI) | O autor não quer: teria custo mensal |
| Comunidade Mooviter (relatos, lotação, editores) | Não existe comunidade. Só se usam fontes oficiais. "Reportar erro" envia um email ao autor |
| Comprar bilhetes, validar passes, carteira | Exigem acordos com os operadores e o cumprimento das regras sobre pagamentos |
| Pedir TVDE ou táxi dentro da app | Exige parcerias. Fica só o atalho "Abrir app de TVDE" |
| Parcerias de micromobilidade (mais de 300 empresas) | Só dados abertos (GBFS) e atalho para a app do operador |
| Previsões próprias da Moovit | São dados da Moovit. Usam-se as previsões dos operadores |
| Realidade aumentada do Way Finder | Só existia no iPhone. É substituída pela bússola "Encontrar a paragem" |
| Mapas oficiais em PDF dentro da app | Abre-se a página do operador no navegador |
| Escolher área ou cidade | O país é uma só área. O mapa abre na posição atual ou, sem ela, em Lisboa |
| Outros idiomas | Só português de Portugal |

## 5. Experiência e visual

### 5.1 Navegação

Há 4 separadores em baixo:

- **Início:** mapa e painel.
- **Rotas:** planear de A para B.
- **Linhas:** linhas, horários, percursos e perturbações.
- **Eu:** favoritos, conta, definições, sobre e fontes de dados.

Para voltar atrás usa-se o gesto do Android que mostra o ecrã anterior enquanto se arrasta o dedo (gesto "voltar" preditivo).

### 5.2 Ecrãs principais

O inventário completo do original está em `replica/recon.md` (ecrãs S01 a S39).

**Início (mapa primeiro):**

- O mapa ocupa o ecrã inteiro, com paragens e veículos ao vivo.
- A pesquisa "Para onde vais?" fica no topo.
- O painel deslizante tem as favoritas (com as próximas chegadas), "perto de ti" e, a partir da fase 2, os cartões de casa e trabalho.
- Ao abrir, o painel mostra a paragem favorita mais próxima da última posição conhecida. Sem posição ou sem permissão, mostra a primeira favorita pela ordem do utilizador.

**Paragem:**

- chegadas com cores de atraso e filtro por linha;
- perturbações ativas;
- estrela de favorito;
- "Avisa-me quando estiver a X min";
- vista ao vivo (fotos de rua e "Encontrar a paragem");
- "Reportar erro".

**Rotas:**

- Origem, destino, hora de partida ou de chegada, e filtros.
- Cada cartão de resultado mostra:
  - a duração e as horas de saída e de chegada;
  - o tempo a pé e os transbordos;
  - a primeira paragem e a próxima partida ao vivo da primeira linha;
  - o ícone de perturbação, se alguma linha da rota tiver um aviso ativo.
- O detalhe mostra as etapas da viagem (a pé, autocarro, metro…), o mapa e os botões "Começar viagem", "Partilhar rota" e "Abrir app de TVDE".

**Viagem ao vivo:**

- Um cartão por etapa, que se desliza para ver a anterior e a seguinte.
- Paragens que faltam, "estás aqui" e "sais aqui".
- Avisos "Prepara-te" e "Sai agora".
- "Posição estimada pelo horário" no subterrâneo.
- Botões "Silenciar", "Já saí" e "Parar".

**Linha:**

- percurso no mapa e sentido;
- lista de paragens com chegadas;
- horário do dia;
- estrela de favorito;
- perturbações.

**Eu:**

- **Favoritos:** paragens, linhas, locais, casa e trabalho.
- **Conta.**
- **Definições:**
  - modo claro ou escuro;
  - limites de atraso;
  - som e vibração do aviso;
  - preferências de rota;
  - notificações por tipo;
  - mapas offline.
- **Privacidade:** "Apagar dados deste telemóvel".
- **Sobre e fontes de dados.**
- **"Enviar opinião".**

Todos os ecrãs preveem cinco situações: vazio, a carregar, erro, sem rede e sem permissão de localização. Os textos para as situações mais comuns estão na secção 9.

### 5.3 Estilo Azulejo

**Modo claro:**

- fundo branco;
- azul-cobalto `#1F4FA3` como cor principal;
- amarelo de elétrico `#FFC629` como destaque;
- texto em azul-tinta `#132A4F`;
- friso de azulejo no painel e cantos redondos;
- letra Nunito, ou a do sistema se faltar.

**Modo escuro (Azulejo noite):**

- fundo `#0B1222` e superfícies `#121B30`;
- azul-claro `#7FB0FF` como cor principal;
- minutos com as cores de atraso para o escuro (secção 5.4).

**Escolha do modo:** segue o modo do telemóvel. Em "Eu → Definições" pode fixar-se o claro ou o escuro.

**Cores das linhas:** usam sempre a cor oficial de cada operador (campo `route_color` dos dados GTFS). Se faltar, usa-se uma cor neutra.

**Logótipo e ícone:** são novos e nunca derivam da Moovit.

### 5.4 Cores dos minutos

**Como se conta o atraso:** atraso = (hora prevista − hora programada), arredondado ao minuto, com o meio minuto arredondado para longe de zero.

| Estado | Regra | Claro | Escuro | Texto por baixo |
|---|---|---|---|---|
| A horas | atraso de −1 a +1 | `#15803D` | `#4ADE80` | "a horas" |
| Atrasado | atraso de +2 a +5 | `#B45309` | `#FBBF24` | "+3" |
| Muito atrasado | atraso acima de +5 | `#C81E1E` | `#F87171` | "+9" |
| Adiantado | atraso de −2 ou menos | `#1F4FA3` | `#7FB0FF` | "−2 adiantado" |
| Sem tempo real | só há horário | `#5B6472` | `#9AA4B2` | "🕒 horário" e a hora marcada (por exemplo, 14:32) em vez de minutos |

- Os limites de 2 e 5 min podem ser mudados nas definições.
- Quando faltam mais de 60 min, mostra-se a hora (por exemplo, 15:40) em vez dos minutos, com a mesma cor.
- A cor nunca aparece sozinha: há sempre texto e uma descrição para o leitor de ecrã do Android (TalkBack).

### 5.5 Acessibilidade e texto

- Funciona com a letra do sistema a 200 %.
- Os botões têm pelo menos 48 dp.
- Cada ação no mapa tem equivalente em lista ou pesquisa, para o TalkBack.
- Quando as chegadas mudam, o TalkBack não as lê em voz alta a cada atualização, para não interromper a pessoa a toda a hora.
- **Textos:** todos em português de Portugal, no ficheiro `strings.xml`. Usa-se "paragem", "autocarro", "comboio", "elétrico", "ecrã" e "telemóvel".
- **Datas e horas:** formato português, no fuso de Lisboa (`Europe/Lisbon`).

## 6. Dados

### 6.1 Fontes

A lista completa, com licenças e testes, está em `replica/dados-pt.md`.

**Carris Metropolitana** (`https://api.carrismetropolitana.pt/v2/`; aberta e sem chave):

| O quê | Endereço | Notas |
|---|---|---|
| Chegadas | `arrivals/by_stop/{id}` | Hora prevista e programada, por isso dá o atraso |
| Veículos | `vehicles` | Mapa ao vivo e confirmação da posição no aviso de saída |
| Perturbações | `alerts` | — |
| Paragens e linhas | `stops`, `lines` | Guardadas no telemóvel e atualizadas uma vez por dia |
| Percursos | `patterns/{id}`, `shapes/{id}` | Pedidos só ao abrir uma linha ou ao começar uma viagem, e guardados. O GTFS não é usado no telemóvel, porque descomprimido tem 930 MB |

**Transitous** (API MOTIS 2, base `https://api.transitous.org`):

- Dá horários e tempo real dos outros operadores, rotas e pesquisa de moradas.
- **Endereços:** `/api/v6/stoptimes`, `/api/v6/plan`, `/api/v6/trip`, `/api/v6/stop`, `/api/v6/map/stops`, `/api/v1/geocode` e `/api/v1/reverse-geocode`. Ficam definidos num só sítio do código.
- **Regras de uso a cumprir:**
  - identificar a app em cada pedido (User-Agent `ParagemCerta/<versão> (contact@pixellusitano.com)`);
  - pôr uma ligação para transitous.org/sources;
  - nunca descarregar dados em massa;
  - falar com a equipa antes da fase 6.

**Metro de Lisboa em tempo real** (fase 4):

- Usa a API oficial `api.metrolisboa.pt`, que exige uma chave secreta, por isso passa sempre pelo servidor.
- A API responde na porta 8243. Antes da fase 4, publica-se um Worker de teste para confirmar que a Cloudflare consegue chamar essa porta.
- Se não conseguir, a função fica de fora e o Metro mostra o horário pelo Transitous.
- Se funcionar, o servidor guarda cada resposta durante 30 s, para vários telemóveis fazerem um só pedido ao Metro.

**Carris (cidade):**

- Horário pelo Transitous.
- A posição dos autocarros no mapa (dados abertos GTFS-RT) chega na fase 4. O Transitous ainda não mostra posições de veículos.

**Porto** (fase 5):

- STCP e Metro do Porto em horário, pelo Transitous.
- Posições dos autocarros da STCP no mapa, pelos dados abertos do Porto Digital (licença CC0).
- Não há previsões públicas de chegada.

**Mapa:**

- **Mapa base:** OpenFreeMap (grátis e sem chave), com a atribuição do OpenStreetMap visível.
- **Mapa offline** (fase 5):
  - ficheiro PMTiles de Portugal, gerado no GitHub Actions com o Planetiler (perfil OpenMapTiles, o mesmo formato do OpenFreeMap);
  - publicado no Cloudflare R2 num subdomínio próprio (por exemplo `mapas.pixellusitano.com`);
  - a descarga inclui o estilo, as letras e os ícones do mapa.

**Bicicletas e trotinetes:** dados abertos GBFS onde existam (Bird, Bora, TubaBike…). A GIRA não tem dados abertos.

**Fotos de rua:**

- **Mapillary** (API v4): usa uma chave de cliente que não é secreta. É injetada no APK a partir das definições locais ou dos segredos do GitHub, nunca do repositório.
- **Panoramax:** não precisa de chave.
- A licença e o autor de cada foto vêm com a própria foto e são mostrados com ela.

**Ligações diretas:** o telemóvel liga-se diretamente a estas fontes. O servidor só entra onde há chaves secretas (Metro de Lisboa) ou contas.

### 6.2 Modelo interno

Fica no módulo `core`, escrito em Kotlin puro.

**Tipos de dados:**

- `Stop`: id, nome, posição, operador, linhas, acessível (sim, não ou desconhecido).
- `Line`: id, nome curto, nome longo, cor, operador, meio de transporte.
- `Pattern`: sentido, paragens por ordem, desenho do percurso.
- `Departure`: linha, destino, hora programada, hora prevista (pode não existir), fonte (`AO_VIVO` ou `HORARIO`), id da viagem, acessível.
- `Vehicle`: id, linha, posição, rumo, hora do último sinal.
- `Alert`:
  - linhas e paragens afetadas, período e texto;
  - gravidade (`GRAVE`, `AVISO` ou `INFO`), vinda do campo `severity_level`;
  - se faltar a gravidade, deduz-se do efeito: "sem serviço" é grave; desvio, serviço reduzido e atrasos grandes são aviso; o resto é informação.
- `Favorite`: tipo, referência, nome, ordem, revisão, apagado.

O estado de atraso da secção 5.4 é calculado a partir da `Departure` por uma função isolada e testada.

**Adaptadores:** cada fonte tem um adaptador que converte a resposta para estes tipos. O resto da app recebe tudo no mesmo formato e não precisa de saber de que operador veio cada dado.

**Regra de origem:**

- Tudo o que é da Carris Metropolitana é servido pelo adaptador da Carris Metropolitana.
- O Transitous também tem a Carris Metropolitana, com ids prefixados (por exemplo `pt-Carris-Metropolitana_360451`). Esses ids são convertidos para o id próprio da Carris Metropolitana. A conversão é confirmada com 3 paragens reais.
- As paragens da Carris Metropolitana devolvidas pelo Transitous são trocadas pelas da base local, para não aparecerem a dobrar.
- Nas rotas, o id da viagem é convertido da mesma forma, para a posição do autocarro poder ser confirmada.
- Se a API da Carris Metropolitana falhar, o horário vem do Transitous.
- Há um teste no `core` para esta conversão.

### 6.3 Atualização e cache

**Chegadas:**

- Atualizam-se enquanto o ecrã está visível: a cada 20 s nas fontes da Carris Metropolitana e a cada 60 s nas do Transitous, que só lê o tempo real uma vez por minuto.
- Param quando a app vai para segundo plano.

**Veículos:** atualizam-se a cada 15 s quando o mapa está visível, e o movimento entre leituras é suavizado.

**Base local (Room):**

- Guarda paragens, linhas, percursos, favoritos e as últimas chegadas vistas.
- As paragens e as linhas (não os percursos) atualizam-se uma vez por dia, em segundo plano e só com rede.

**Sem rede:**

- Fases 1 a 4: mostra as últimas chegadas vistas, com a faixa "Sem ligação — atualizado há X min".
- A partir da fase 5: mostra também o horário guardado no telemóvel. São as próximas 24 h das paragens favoritas e das 20 últimas vistas, renovadas uma vez por dia. Não se descarrega o GTFS da região.
- Nunca fica um ecrã vazio sem explicação.

**Perturbações** (fase 4):

- Uma tarefa periódica de 30 min, só com rede, lê os avisos das linhas favoritas.
- Notifica só os novos, guardando os ids já vistos.
- Não usa servidor.

**Widgets** (fase 4):

- Mostram horas (por exemplo, "14:32 · 728") e "atualizado às HH:MM", nunca "daqui a X min", porque o Android não deixa atualizar widgets a toda a hora.
- **Atualizam-se:**
  - quando a app abre ou fecha;
  - com o botão "Atualizar";
  - por uma tarefa de 15 em 15 min, só com rede.
- Ficam a cinzento quando os dados têm mais de 15 min.

## 7. Viagem ao vivo e aviso de saída

Esta é a parte mais importante.

### 7.1 Onde corre

**Serviço:**

- Corre num serviço próprio que o Android mantém ativo com uma notificação fixa (serviço em primeiro plano do tipo localização).
- Fica num processo separado do resto da app (`android:process=":trip"`). Assim, se o mapa falhar, o aviso de saída continua a funcionar.

**Isolamento do processo `:trip`:**

- Não abre a base de dados, as definições nem as tarefas periódicas. O arranque da app não inicializa nada nesse processo.
- **Plano da viagem:** fica num ficheiro `trip-plan.json`, cujo nome segue no pedido de arranque. Tem as paragens, o desenho, os horários e o id da viagem.
- **Estado:** o serviço escreve-o em `trip-state.json`. A app lê esse ficheiro e recebe atualizações por mensagem interna dirigida só a ela.
- **Viagem interrompida:** a app mostra "Viagem interrompida — toca para retomar" quando `trip-state.json` existe sem o serviço estar a correr.

### 7.2 Arranque

- Só arranca com a app visível, depois de o utilizador tocar em "Começar viagem" (regra do Android 14 e seguintes).
- **Localização:**
  - pede-se a localização **precisa**; se só houver a aproximada, explica-se porquê e pede-se a precisa;
  - não se pede acesso à localização com a app fechada (a opção "Permitir sempre");
  - se a localização do telemóvel estiver desligada, avisa-se antes de começar.
- **Notificações (Android 13 e seguintes):** pede-se a permissão. Se for recusada, explica-se que "Prepara-te" e "Sai agora" só vibram e tocam, sem aparecer no ecrã, e deixa-se começar à mesma.

### 7.3 Etapas e cálculo

**Etapas:** a viagem segue a rota escolhida, etapa a etapa.

- **A pé:** distância e tempo até à paragem.
- **Espera:** "O 736 chega em 4 min", com a hora ao vivo.
- **Em transporte:** o cálculo abaixo.
- **Transbordo:** "Sai agora" na paragem de transbordo e passagem automática à etapa seguinte.

**Cálculo** (no `core`, testado no PC com gravações de GPS de viagens reais):

- Projeta a posição GPS no percurso da linha e conta as paragens que faltam.
- Na Carris Metropolitana, usa também a posição do próprio autocarro (`vehicles`) para confirmar onde está.
- **Sem GPS:** no subterrâneo, ou sem GPS durante mais de 60 s, estima pelo horário e pelo tempo decorrido, e mostra "posição estimada pelo horário".
- **"Prepara-te":** dispara quando o veículo sai da penúltima paragem, ou a 2 min do destino, o que vier primeiro.
- **"Sai agora":** dispara ao aproximar-se da paragem de destino: quando o veículo está a menos de cerca de 150 m dela, ou antes, se os dados do operador já indicarem que está a chegar.

### 7.4 Avisos

**Notificações:**

- **Progresso:** notificação fixa e silenciosa ("Faltam 3 paragens · sais em Oriente"), com os botões "Silenciar", "Já saí" e "Parar".
- **"Prepara-te" e "Sai agora":** notificação urgente, numa categoria própria de notificações que se pode configurar à parte. Essa categoria não tem som próprio, para que as definições de som e vibração possam mudar a qualquer momento.

**Som e vibração:**

- É o próprio serviço que toca o som e faz vibrar o telemóvel, com as definições de **alarme** do Android.
- Por isso funcionam com o telemóvel em silêncio e mesmo que a notificação seja afastada, sem pedir acesso a "Não incomodar".

**"Silenciar":** o progresso continua, mas nessa viagem não toca nem vibra.

**Recuperação:** se o Android fechar o serviço à força, a app não o tenta reabrir automaticamente, porque o Android 14 e seguintes não o permitem. Ao voltar à app, aparece "Viagem interrompida — toca para retomar".

**Bateria:**

- A localização em alta precisão liga-se quando o veículo sai da antepenúltima paragem, ou nos últimos 3 minutos antes do destino, o que vier primeiro.
- No resto da viagem, o GPS é consultado menos vezes, para poupar bateria.

### 7.5 "Avisa-me quando estiver a X min"

- Corre num serviço em primeiro plano do tipo `specialUse` ("aviso de chegada pedido pelo utilizador"), com a notificação fixa "A vigiar 2 linhas em Cais do Sodré".
- Só arranca a partir do ecrã visível.
- Consulta as chegadas a cada 30 s e avisa na categoria urgente.
- **Para sozinho:** quando todos os avisos disparam, quando o utilizador cancela, ou ao fim de 90 min.

## 8. Contas e servidor

**Servidor:**

- É um Cloudflare Worker com base de dados D1 guardada na União Europeia, no plano grátis.
- Fica em `paragemcerta.pixellusitano.com`: a API em `/api/…` e as páginas públicas (privacidade e apagar conta) no mesmo endereço.
- Usa-se só um nível antes de `pixellusitano.com`, porque o certificado de segurança gratuito da Cloudflare não cobre endereços como `a.b.pixellusitano.com`.

**Entrar com Google:**

- **No telemóvel:** usa-se o Credential Manager do Android ("Sign in with Google"), com `androidx.credentials` e `credentials-play-services-auth` 1.6.x e a biblioteca `googleid`. O botão aparece em português ("Iniciar sessão com o Google").
- **Pedido único (nonce):** o servidor gera um pedido único, que a app envia à Google.
- **No servidor:** o Worker confirma que o comprovativo de entrada (token) foi mesmo emitido pela Google, se destina a esta app e ainda está válido. Verifica o `nonce`, a assinatura e os campos `aud` (o id do cliente Web), `iss`, `exp` e `email_verified`.
- **Juntar contas:** se a pessoa já tiver conta com email e depois entrar com o Google com o mesmo endereço, as duas contas só se juntam automaticamente quando o endereço é Gmail ou Google Workspace. Nesses casos é a Google que garante que o endereço pertence mesmo a essa pessoa.

**Entrar com email:**

- Recebe-se por email um código de 6 dígitos, válido durante 10 min, com um máximo de 5 tentativas. Só se guarda o hash do código.
- **Envio:** os emails são enviados pelo **Resend**, a partir de um subdomínio próprio (por exemplo `mail.pixellusitano.com`), sem mexer nos registos DNS do email Proton. Se o Resend falhar, a app sugere entrar com o Google.
- **Proteção contra abusos:**
  - o limite por IP é aplicado antes de chegar à base de dados, pela regra grátis de limitação de pedidos da Cloudflare em `/api/auth/*`;
  - a base de dados só conta tentativas por email depois disso;
  - há um teto global de 100 envios por dia.
- **Âmbito:** a conta só serve para guardar e sincronizar favoritos. Nunca se lhe acrescentam funções sensíveis (pagamentos, dados pessoais).

**Sessão:**

- Depois de entrar, o telemóvel recebe um código de sessão aleatório (token), e o servidor só guarda o hash dele.
- No telemóvel, o token fica no armazenamento privado da app e é excluído das cópias de segurança. Não tem cifra própria, porque pode ser anulado no servidor a qualquer momento.
- Expira ao fim de 60 dias sem uso ou de 180 dias no total.

**Sincronização:**

- O servidor dá a cada alteração um número de revisão crescente, e a app pede as alterações desde a última revisão que conhece.
- Se o mesmo favorito for mudado em dois telemóveis, fica a alteração que chegou por último ao servidor.
- Um favorito apagado fica marcado como apagado, para desaparecer também nos outros telemóveis.

**Chaves de assinatura e Google:**

- A variante pessoal é sempre assinada com a mesma chave de lançamento. O ficheiro em `Chave Privada/` é o mesmo que está nos segredos do GitHub.
- **Na consola da Google registam-se:**
  - `pt.pixellusitano.paragemcerta.pessoal` com a impressão digital (SHA-1) da chave de lançamento e da chave de testes;
  - na fase 6, `pt.pixellusitano.paragemcerta` com a impressão digital da chave da Play.

**Apagar conta:**

- **Na app:** botão "Apagar conta" em "Eu → Conta".
- **Na web:** página pública com o nome "Paragem Certa" e "Pixel Lusitano", e o email contact@pixellusitano.com como alternativa.
- **A política de privacidade diz quanto tempo ficam:**
  - as cópias de segurança da base de dados: 7 dias;
  - os registos de envio de email: 30 dias.

**Privacidade:**

- Sem anúncios, sem rastreio e sem estatísticas.
- **Posição:** a posição exata do GPS não é guardada nem enviada para o servidor da Paragem Certa.
- **O que vai para serviços de terceiros:**
  - Transitous: as coordenadas de origem e destino das rotas e das pesquisas perto de ti;
  - OpenFreeMap, Mapillary e Panoramax: a zona do mapa visível.
- Tudo isto consta da política de privacidade e da ficha de segurança de dados.
- A política identifica o responsável pelos dados. Como manter o nome real fora do site é uma questão em aberto (secção 12).

## 9. Erros e situações

| Situação | O que a app faz |
|---|---|
| Sem rede | Faixa "Sem ligação — atualizado há X min" e, a partir da fase 5, horário guardado no telemóvel |
| Fonte indisponível | Passa para o horário (cinzento) e indica "tempo real indisponível" |
| Sem permissão de localização | "Perto de ti" passa a "Procura uma paragem". O mapa abre em Lisboa |
| Paragem sem chegadas | Mostra "Não há chegadas nas próximas 2 horas" e a hora da primeira chegada do dia seguinte |
| Rota impossível | Explica a causa provável (hora, distância) e sugere mudar a hora |
| Demasiados pedidos à fonte | Espera e tenta de novo, com pausas cada vez maiores. Nunca fica a repetir sem parar |

## 10. Estrutura técnica

**Pasta do projeto** (`Desktop/PROJETOS/Paragem Certa/`):

- `codigo/`: o repositório;
- `Chave Privada/`: fora do repositório;
- `Ficheiro APK/` e `Projeto/`, cada uma com "Versão Atual" e "Versões Anteriores", como no SCAM Operator.

**Partes do código:**

- `core`:
  - Kotlin puro, sem nada do Android;
  - tipos de dados e adaptadores das fontes (OkHttp e kotlinx.serialization);
  - cálculo dos atrasos e cálculo da viagem.
- `app`:
  - ecrãs em Compose Material 3, com o estilo próprio;
  - mapa MapLibre Android 13.x, com a versão que escolhe sozinha entre Vulkan e OpenGL;
  - base de dados Room e tarefas em segundo plano (WorkManager);
  - serviços da viagem e dos avisos de chegada;
  - widgets (Glance) e entrada com Google (Credential Manager).
- **Ligações:** sem bibliotecas de injeção de dependências; as peças ligam-se à mão, com construtores simples.
- **Só Android, por agora.** O `core` fica separado, para um dia poder servir também o iPhone.

**Variantes:**

- **`pessoal`** (`pt.pixellusitano.paragemcerta.pessoal`): a app consulta a última versão no GitHub, compara o número da versão e entrega o APK ao instalador do Android. Quem verifica a assinatura é o próprio Android, que recusa um APK assinado com outra chave.
- **`play`** (`pt.pixellusitano.paragemcerta`): não pode instalar outras apps e atualiza-se pela Play Store.
- Cada variante tem um identificador próprio, para as duas poderem estar instaladas no mesmo telemóvel.

**Servidor:**

- fica em `worker/`, escrito em TypeScript, com wrangler e testes em vitest;
- a chave do Resend e a do Metro ficam guardadas como segredos do Worker.

**Mínimos:**

- Android 8.0 ou superior (minSdk 26);
- compileSdk e targetSdk 36 (Android 16) nas duas variantes, como a Play exige desde 31/08/2026.

**Caminho com espaços:** se o Gradle se queixar dos espaços no caminho, cria-se um atalho de pasta sem espaços (junção), `C:\dev\paragem-certa`, que aponta para a pasta real.

## 11. Testes e entrega

**Ferramentas no PC.** Cada uma só é instalada depois de dizer ao autor o nome, a origem e o tamanho, e de ele autorizar.

- **Nível 1** (fase 0, cerca de 0,5 GB, mais 1 a 2 GB de cache):
  - JDK 21 Temurin;
  - ferramentas de linha de comandos do Android SDK, platform-tools, plataforma 36 e build-tools.
- **Nível 2** (fase 3, cerca de 2,7 GB): emulador. Exige que o autor ative a "Plataforma do Hipervisor do Windows" e reinicie o PC.
- **Telemóvel do autor:** ligado por "Depuração sem fios", para ler os registos de erros e tirar capturas de ecrã reais.

**Testes automáticos:**

- **`core` (JUnit):**
  - estados de atraso e regra de origem;
  - adaptadores, com respostas gravadas;
  - cálculo da viagem, com gravações de GPS, incluindo Metro subterrâneo e GPS impreciso.
- **`app`:**
  - capturas automáticas dos ecrãs (Roborazzi, com o mapa substituído por uma imagem), nos modos claro e escuro e com letra a 200 %;
  - verificações de acessibilidade do Compose.
- **`worker`:** vitest, com uma base de dados D1 local.

**Compilação automática (CI, no GitHub Actions, repositório público):**

- Compila e testa.
- Verifica o manifesto da app: tipos dos serviços e permissões.
- Assina com a chave guardada nos segredos do GitHub.
- Publica o APK no GitHub Releases quando se cria uma etiqueta de versão (tag no git). Isso só acontece depois de o autor dizer "publica".

**Validação real:** o autor faz as viagens da fase 3 antes de ela ser dada como fechada.

**Regras:**

- os commits são em inglês, sem atribuição a IA;
- cada versão tem uma nota de versão.

## 12. Riscos e questões em aberto

1. **Transitous:**
   - É um serviço feito por voluntários, sem garantias.
   - Quem fizer mais de cerca de 720 pedidos de rotas por dia tem de combinar primeiro com a equipa, por isso é preciso falar com eles antes da fase 6.
   - **Plano B:** instalar o mesmo programa que o Transitous usa (MOTIS) num servidor gratuito da Oracle (Always Free). A app só teria de mudar o endereço.
2. **Licenças dos dados:**
   - os dados do Algarve e da AUTNA têm a licença CC BY-ND, que não permite alterá-los;
   - os dados da CP, do Metro de Lisboa (publicados no Ponto de Acesso Nacional) e da Transtejo não dizem que licença têm;
   - é preciso pedir clarificação aos operadores antes da fase 6.
3. **OpenFreeMap:** não garante que está sempre disponível. O plano B é o mapa próprio no R2 (fase 5).
4. **Limites gratuitos, partilhados com o site da Pixel Lusitano:**
   - Resend: 100 emails por dia;
   - Workers: 100 mil pedidos por dia;
   - D1: 100 mil linhas escritas e 5 milhões lidas por dia. Desde 01/09/2026, ao passar o limite, a base de dados deixa de responder até à meia-noite (UTC);
   - proteção: limites por IP e por email desde o primeiro dia (secção 8).
5. **Fiabilidade do aviso:**
   - Alguns fabricantes (Samsung, Xiaomi) fecham à força apps que estão a correr em segundo plano.
   - Para reduzir o risco, na primeira viagem a app explica como tirá-la da poupança de bateria, e o aviso é validado em viagens reais.
6. **Verificação de programadores Android:**
   - A partir de 2027, só se poderão instalar apps fora da Play Store se o programador estiver verificado pela Google.
   - É preciso criar antes disso a conta gratuita de "distribuição limitada".
7. **Proteção de dados (RGPD):**
   - A política de privacidade tem de identificar o responsável pelos dados.
   - Falta decidir como cumprir sem expor o nome real, por exemplo com uma empresa ou morada profissional.
   - Decide-se na fase 6.
8. **Tempo real fora de Lisboa:** quase não existe em dados abertos. No resto do país, a app mostra o horário (a cinzento) até haver dados.

## 13. Glossário

- **API:** endereço onde a app pede dados a outro serviço.
- **APK:** ficheiro de instalação de uma app Android.
- **Cache:** cópia de dados guardada no telemóvel para usar sem rede ou mais depressa.
- **CI:** compilação e testes automáticos no GitHub Actions.
- **dp:** medida de tamanho no ecrã (48 dp é cerca de 9 mm).
- **GBFS:** formato aberto dos dados de bicicletas e trotinetes partilhadas.
- **GTFS e GTFS-RT:** formato aberto dos horários (GTFS) e das posições e atrasos em tempo real (GTFS-RT).
- **Hash:** versão baralhada de um dado, que não permite recuperar o original.
- **Processo:** espaço de memória onde corre uma parte da app. Se um processo falhar, os outros continuam.
- **Serviço em primeiro plano:** tarefa que o Android mantém ativa com o ecrã desligado, sempre com uma notificação visível.
- **Token:** código que prova que a pessoa já entrou na conta.
- **Transitous:** serviço gratuito, feito por voluntários, que junta horários de transportes de toda a Europa e calcula rotas.
- **Worker e D1:** pequeno servidor e base de dados da Cloudflare, no plano grátis.
