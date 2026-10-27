# Recon: Moovit (Android, pacote com.tranzmate) para recriar como "Paragem Certa"

- Âmbito: a app de consumidor Moovit para Android (com apoio das fichas iOS e do site quando ajudam). Só fontes públicas.
- Para quem: Paragem Certa, app Android gratuita, sem anúncios nem subscrições, toda em português de Portugal, com visual próprio, para Portugal. Começa como app pessoal; mais tarde vai para a Google Play.
- Data da recolha: 2026-10-26.
- Método (recriação "clean-room"): páginas públicas lidas ao ritmo de uma pessoa (cerca de 90 páginas ao todo, sem varrimentos nem ciclos de pedidos). Não foi descarregado nenhum APK/XAPK, não foi lido código nem bundles JavaScript, não foram registados endpoints privados. A ficha da Google Play foi lida uma vez por pedido HTTP simples e depois no navegador. Os textos foram resumidos por palavras minhas; as etiquetas de interface do original aparecem em `código`.
- Convenções: "[confirmado]" = visto na fonte indicada; "por confirmar" = não confirmado numa fonte pública (ou só confirmado de forma indireta). R## = fonte da tabela 1; S## = ecrã (secção 4); F## = fluxo (secção 5).
- O que não correu bem na recolha: o leitor automático foi bloqueado (HTTP 403) pelo Centro de Ajuda da Moovit; as páginas foram então lidas no navegador integrado, como faria uma pessoa. O Reddit está bloqueado pelo navegador integrado (não consultado). O PDF do comunicado da Carris Metropolitana não abriu (HTTP 523, não repetido). As páginas de cidade do site da Moovit só mostram conteúdo depois de carregadas num navegador.

## 0. Resumo

Ciclo central (o que o utilizador usa todos os dias): abrir a app e ver, em 1 a 2 toques, quando chega o próximo autocarro, metro ou comboio à paragem mais próxima; ou planear A para B e ser avisado do momento de sair, com tempo real quando existe e horário oficial quando não existe.

Números do original (Play PT, 2026-10-25): nota 4,1 em 5 com 1,53 milhões de críticas; mais de 100 milhões de transferências; PEGI 3; "contém anúncios" e "compras na aplicação"; programador MOOVIT APP GLOBAL LTD (Israel; a Moovit assume-se como empresa Mobileye). Cobertura anunciada: mais de 3 500 cidades em 112 países, 45 idiomas (R01, R02, R06).

Números desta recolha: 131 funções em `features.csv`, 39 ecrãs, 23 fluxos, 10 queixas principais de utilizadores.

Conclusão em 5 pontos:
1. O núcleo gratuito da Moovit (planear, tempo real, linhas, paragens, favoritos, alertas, navegação com aviso para sair) é recriável; o que decide a qualidade são os dados (horários oficiais e tempo real por operador), não o ecrã.
2. A Moovit+ (subscrição) tira os anúncios e dá: Safe Ride, Compare Routes, Arrival Updates, Live Location, Live View e o Assistente de IA (secção 3).
3. As queixas dominantes são os anúncios (intersticiais, botões de fechar falsos, abrirem outras apps) e a fiabilidade do tempo real (secção 9).
4. Em Portugal a Moovit lista Lisboa, Porto/Braga/Vila Real, Coimbra, Funchal, Portimão (Algarve) e Açores; tempo real confirmado com a Carris Metropolitana e o Metro de Lisboa (secção 8).
5. Não é recriável: comunidade de editores (Mooviters), base de dados e modelo de previsões próprios, bilhetes/pagamentos e outras parcerias comerciais (secção 10).

## 1. Fontes consultadas (uma URL por linha)

Tipos de fiabilidade: A = oficial da Moovit; B = loja/plataforma pública; C = terceiros (imprensa, avaliações).

| # | fonte | URL | o que dá | tipo |
| --- | --- | --- | --- | --- |
| R01 | Ficha Google Play (pt_PT) | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT | Descrição em português, novidades (IA da Moovit+), nota e contagens, "contém anúncios", intervalo de preços das compras (0,99 a 19,99 EUR), cidades/operadores/serviços de bicicletas listados, 3 críticas visíveis | B |
| R02 | Ficha Google Play (en) | https://play.google.com/store/apps/details?id=com.tranzmate&hl=en&gl=PT | Descrição inglesa mais recente (pagamentos digitais, Way Finder em RA, mapas offline em PDF, informação da comunidade); legendas das 6 capturas de ecrã | B |
| R03 | Play: segurança dos dados | https://play.google.com/store/apps/datasafety?id=com.tranzmate&hl=pt_PT | Tipos de dados recolhidos e partilhados (localização, dados pessoais, IDs, fotos, pagamentos, atividade) | B |
| R04 | Play: críticas ("Ver todas as críticas") | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT&gl=PT&showAllReviews=true | Cerca de 40 críticas públicas em português (muitas do Brasil) com respostas-tipo da Moovit | C |
| R05 | Ficha App Store Portugal | https://apps.apple.com/pt/app/moovit-transportes-p%C3%BAblicos/id498477945 | Preços das compras na app em EUR, versão 5.202.1, privacidade, 4 críticas portuguesas | B |
| R06 | moovit.com (início) | https://moovit.com/ | Posicionamento, menus (Moovit Ads, MaaS, comunidade, bilhetes), ligações para ajuda e termos | A |
| R07 | moovit.com: funcionalidades | https://moovit.com/features/ | Lista oficial de funções do consumidor (planeador, tempo real, favoritos, alertas, bicicletas, carteira, Way Finder, mapas PDF, comunidade, acessibilidade) | A |
| R08 | moovit.com: acessibilidade | https://moovit.com/features/accessibility/ | Funções de acessibilidade (leitores de ecrã, texto grande, cor, percursos acessíveis) | A |
| R09 | Moovit Ads | https://moovit.com/ads/ | Formatos publicitários e segmentação | A |
| R10 | Moovit Ads: baseados em localização | https://moovit.com/ads/location-based/ | Tamanhos de banner (320x50 e 300x250), intersticiais, anúncios no mapa, geovedação | A |
| R11 | Imprensa: lançamento da Moovit+ (funções) | https://moovit.com/press-releases/moovit-plus-features/ | Safe Ride, Compare Routes, Arrival Updates, sem anúncios; o que continua grátis (agosto de 2023) | A |
| R12 | Imprensa: Moovit+ e estratégia | https://moovit.com/press-releases/moovit-plus-launch-business-strategy/ | Modelo freemium, áreas de negócio (Ads, MaaS, APIs), Uber e Apple a usar as APIs | A |
| R13 | Imprensa: Carris Metropolitana (2022) | https://moovit.com/press-releases/carris-metropolitana-moovit-partnership/ | Parceria oficial com a TML: 260 linhas e 4 290 paragens no arranque, Live Directions, alertas | A |
| R14 | Imprensa: Metro de Lisboa em tempo real (2022-07-28) | https://moovit.com/?p=29496 | Chegadas em tempo real do Metropolitano de Lisboa na app | A |
| R15 | Blogue: guia de navegação | https://moovit.com/blog/navigating-with-moovit/ | Fluxo oficial: "Where to?", rotas, Live Directions, Get Off Alert, separadores, favoritos | A |
| R16 | Blogue: Moovit Assistant (2026-05-11) | https://moovit.com/blog/introducing-moovit-assistant/ | Trip Insights, filtros inteligentes, reencaminhamento, MoovitAI | A |
| R17 | Blogue: índice | https://moovit.com/blog/ | Lista de artigos recentes (para ver o que é novidade) | A |
| R18 | Mass Transit: Live Directions (2015) | https://masstransitmag.com/technology/press-release/12145871/moovit-moovit-adds-live-directions-and-get-off-notifications | Origem das indicações ao vivo e dos avisos de saída; funcionamento em segundo plano | C |
| R19 | Centro de Ajuda (EN): início | https://support.moovitapp.com/hc/en-us | Categorias: Discover Moovit, Moovit+, What's New, Payments, Contributing | A |
| R20 | CA: Getting Started with Moovit | https://support.moovitapp.com/hc/en-us/articles/211391869-Getting-Started-with-Moovit | Os 3 separadores (Direções, Paragens, Linhas) | A |
| R21 | CA: FAQ to Moovit | https://support.moovitapp.com/hc/en-us/articles/9024545968274-FAQ-to-Moovit | Perguntas frequentes: modos, tempo real, hora futura, tarifas, menos caminhada, erros de ligação | A |
| R22 | CA: Everything You Need to Know About Any Line | https://support.moovitapp.com/hc/en-us/articles/211393089-Everything-You-Need-to-Know-About-Any-Line | Separador Linhas e detalhe da linha | A |
| R23 | CA: Arrival Time Accuracy Guide | https://support.moovitapp.com/hc/en-us/articles/12476396200978-Arrival-Time-Accuracy-Guide | Cores de fiabilidade da hora de chegada | A |
| R24 | CA: Live Directions & Get Off Alerts | https://support.moovitapp.com/hc/en-us/articles/211392929-Live-Directions-Get-Off-Alerts | Navegação ao vivo, sino, cartões, localização sempre ligada | A |
| R25 | CA: Plan a Trip & Get Directions | https://support.moovitapp.com/hc/en-us/articles/212011605-Plan-a-Trip-Get-Directions | Planeador em 3 passos; Depart Now; Filter; Navigate | A |
| R26 | CA: Trip options based on preferred transit types | https://support.moovitapp.com/hc/en-us/articles/5880749202834-Trip-options-based-on-preferred-transit-types | Modos preferidos e menos preferidos | A |
| R27 | CA: Share directions | https://support.moovitapp.com/hc/en-us/articles/360011910560-Share-directions-to-any-place-with-friends | Partilhar o percurso | A |
| R28 | CA: electric scooters, bikes, mopeds | https://support.moovitapp.com/hc/en-us/articles/4404565195026-Try-electric-scooters-bikes-or-mopeds-as-a-part-of-your-journey | Mobilidade partilhada (300+ empresas) | A |
| R29 | CA: carbon footprint | https://support.moovitapp.com/hc/en-us/articles/33022874559506-How-does-Moovit-calculate-the-carbon-footprint-of-my-trip | CO2e junto aos percursos | A |
| R30 | CA: View the Live Location of your transit line | https://support.moovitapp.com/hc/en-us/articles/8978526768530-View-the-Live-Location-of-your-transit-line-on-the-map | Veículo em movimento no mapa | A |
| R31 | CA: On the Map: See all options around you | https://support.moovitapp.com/hc/en-us/articles/211393049-On-the-Map-See-all-options-around-you | Separador Paragens: mapa, NEARBY/FAVORITES, camadas | A |
| R32 | CA: Check a Line's Service Status | https://support.moovitapp.com/hc/en-us/articles/212012025-Check-a-Line-s-Service-Status | Alertas de serviço por cor, Agency Alerts | A |
| R33 | CA: WayFinder (iPhone) | https://support.moovitapp.com/hc/en-us/articles/11389054527122-WayFinder-Augmented-Reality-helper-to-find-your-stop-iPhone-only | Realidade aumentada para achar a paragem | A |
| R34 | CA: tempos de chegada acima de 60 min ou negativos | https://support.moovitapp.com/hc/en-us/articles/360010463960-Real-time-estimated-arrival-display-time-longer-than-60-minutes-or-in-negative-format | Erro de fuso horário (Android) | A |
| R35 | CA: Quick Directions to Favorite Locations | https://support.moovitapp.com/hc/en-us/articles/211393169-Quick-Directions-to-Favorite-Locations | Casa, Trabalho e outros locais | A |
| R36 | CA: Favorite Lines | https://support.moovitapp.com/hc/en-us/articles/213040725-Favorite-Lines-for-Updates-Arrival-Times | Linhas favoritas e alertas | A |
| R37 | CA: Favorite Stations | https://support.moovitapp.com/hc/en-us/articles/213040745-Easily-View-Your-Favorite-Stations | Paragens favoritas | A |
| R38 | CA: How to change metro (service area) | https://support.moovitapp.com/hc/en-us/articles/7185075096978-How-to-change-metro-service-area | Escolha da área de serviço | A |
| R39 | CA: Changing the Language | https://support.moovitapp.com/hc/en-us/articles/211393329-Changing-the-Language-in-Moovit | Idioma da app = idioma do telemóvel (iOS pode ter o seu) | A |
| R40 | CA: Map Settings | https://support.moovitapp.com/hc/en-us/articles/212012125-Map-Settings | Definições do mapa | A |
| R41 | CA: Personalize Notifications | https://support.moovitapp.com/hc/en-us/articles/4692566849170-Personalize-Notifications | Notificações em Definições | A |
| R42 | CA: Dark Mode | https://support.moovitapp.com/hc/en-us/articles/11373097613970-Dark-Mode | Modo escuro segue o sistema | A |
| R43 | CA: Smart Cards | https://support.moovitapp.com/hc/en-us/articles/6592985526034-Smart-Cards-personalized-Instant-travel-suggestions | Sugestões instantâneas no ecrã inicial | A |
| R44 | CA: Deleting my Accounts and Personal Information | https://support.moovitapp.com/hc/en-us/articles/6923596858770-Deleting-my-Accounts-and-Personal-Information | Pedido de apagar conta/dados (RGPD) | A |
| R45 | CA: Moovit+: New Pluses for a First Class Ride | https://support.moovitapp.com/hc/en-us/articles/10011559836818-Moovit-New-Pluses-for-a-First-Class-Ride | Lista oficial do que a Moovit+ inclui; como aderir | A |
| R46 | CA: Moovit+ Costs and Payment Methods | https://support.moovitapp.com/hc/en-us/articles/15105291310738-Moovit-Costs-and-Payment-Methods | Dois planos (mensal e anual, até 50% de desconto); gestão na loja | A |
| R47 | CA: Canceling or Restoring Your Moovit+ Subscription | https://support.moovitapp.com/hc/en-us/articles/14981025645330-Canceling-or-Restoring-Your-Moovit-Subscription | Cancelar e restaurar; apagar conta não cancela | A |
| R48 | CA: Moovit Assistant | https://support.moovitapp.com/hc/en-us/articles/35543186031634-Moovit-Assistant-Smarter-Trip-Planning-Than-Ever-Before | Trip Insights, filtros, reencaminhamento, MoovitAI (Moovit+, Android primeiro) | A |
| R49 | CA: Live View | https://support.moovitapp.com/hc/en-us/articles/13518129473938-Live-View-See-Your-Real-Life-Surroundings | Vista panorâmica da paragem (Moovit+) | A |
| R50 | CA: Safe Ride | https://support.moovitapp.com/hc/en-us/articles/10341420133650-Safe-Ride-Sharing-Live-Location-with-Contacts | Contactos de segurança, SMS e página de seguimento | A |
| R51 | CA: Live Location (secção Moovit+) | https://support.moovitapp.com/hc/en-us/articles/12708869559826-Live-Location-See-Your-Line-in-Real-Time-on-a-map | Live Location lançada na v5.104 (nov. 2022); listada na secção Moovit+ | A |
| R52 | CA: Compare Routes | https://support.moovitapp.com/hc/en-us/articles/12398708242578-Compare-Routes-For-Real-Time-Insights-on-Your-Route | Botão azul "Compare on Map" | A |
| R53 | CA: Arrival Updates | https://support.moovitapp.com/hc/en-us/articles/13517706542866-Arrival-Updates-Always-Stay-in-the-Loop | Avisos de chegada de uma linha (só uma de cada vez) | A |
| R54 | CA: v5.123 ecrã de Direções | https://support.moovitapp.com/hc/en-us/articles/12816367973266-Ver-5-123-Getting-around-just-got-easier-with-our-improved-Directions-screen | Itinerário em linha temporal | A |
| R55 | CA: v5.70 lotação e relatos | https://support.moovitapp.com/hc/en-us/articles/360022062080-Ver-5-70-Know-in-real-time-how-crowded-your-station-next-bus-are-and-help-others-know-by-self-reporting | Níveis de lotação e como reportar | A |
| R56 | CA: v5.63 lotação em tempo real | https://support.moovitapp.com/hc/en-us/articles/360022063460-Ver-5-63-Real-time-crowdedness-information | Lotação do veículo (dados da operadora) | A |
| R57 | CA: v5.45 pesquisa no mapa | https://support.moovitapp.com/hc/en-us/articles/360022062400-Ver-5-45-Easily-Search-the-Map-for-Anywhere-in-Your-City | Barra de pesquisa no mapa de Paragens | A |
| R58 | CA: v5.28 alertas e notificações | https://support.moovitapp.com/hc/en-us/articles/360022062260-Ver-5-28-Personalize-your-Service-Alerts-Get-Notifications | Notificações por linha | A |
| R59 | CA: v5.32 fotos das paragens | https://support.moovitapp.com/hc/en-us/articles/360022062580-Ver-5-32-Station-Pictures | Fotos das paragens feitas por utilizadores | A |
| R60 | CA: v5.35 calendário (iPhone) | https://support.moovitapp.com/hc/en-us/articles/360022062660-Ver-5-35-Sync-Up-Your-Calendar-iPhone-Only | Sincronização do calendário | A |
| R61 | CA: Registering and Creating an Account | https://support.moovitapp.com/hc/en-us/articles/360012534739-Registering-and-Creating-an-Account | Conta Moovit Pay (versão de Israel) | A |
| R62 | CA: bilhetes de longa distância (Q&A) | https://support.moovitapp.com/hc/en-us/articles/29235652291090-Common-Q-As | Pagamento e bilhete digital da Moovit Tickets | A |
| R63 | CA: comunidade Mooviter | https://support.moovitapp.com/hc/en-us/articles/33512223705362-Welcome-to-the-Mooviter-Community-Transforming-Mobility-Together | Editores voluntários, Embaixadores, código de conduta | A |
| R64 | CA: fontes e fiabilidade dos dados | https://support.moovitapp.com/hc/en-us/articles/23519029569682--Data-Sources-and-Reliability-in-the-Moovit-Editor | De onde vêm os dados (operadoras, utilizadores, terceiros) | A |
| R65 | CA em português: A Começar com a Moovit | https://support.moovitapp.com/hc/pt/articles/211391869-A-Come%C3%A7ar-com-a-Moovit | Nomes em português dos separadores (Direções, Paragens, Linhas) | A |
| R66 | Moovit Updates: How to Add Favorites | https://updates.moovit.com/how-to-add-favorites/ | Favoritos: Casa/Trabalho/Add Location, linhas e paragens | A |
| R67 | Moovit Updates: índice | https://updates.moovit.com/ | Avisos de serviço publicados por cidade (a Moovit escreve-os) | A |
| R68 | Moovit Updates: Carris Metropolitana em tempo real | https://updates.moovit.com/portugal-lisboa-cm-rt-1/ | Tempo real da Carris Metropolitana; nome PT "Vá no Verde" | A |
| R69 | Índice Moovit: Portugal | https://moovitapp.com/index/en/public_transit-Portugal | Regiões cobertas em Portugal | A |
| R70 | Índice Moovit: Lisboa | https://moovitapp.com/index/en/public_transit-Lisboa-2460 | Operadores por modo na região de Lisboa | A |
| R71 | Índice Moovit: Porto, Braga e Vila Real | https://moovitapp.com/index/en/public_transit-Porto-1904 | Operadores por modo | A |
| R72 | Índice Moovit: Coimbra | https://moovitapp.com/index/en/public_transit-Coimbra-1905 | Operadores por modo | A |
| R73 | Índice Moovit: Funchal | https://moovitapp.com/index/en/public_transit-Funchal-3481 | Operadores (HF, EACL, SIGA; lista só parcialmente lida) | A |
| R74 | Índice Moovit: Portimão (Algarve) | https://moovitapp.com/index/en/public_transit-Portimao-5576 | Operadores do Algarve | A |
| R75 | Índice Moovit: Açores | https://moovitapp.com/index/en/public_transit-A%C3%A7ores-6235 | Linhas PDL Mini BUS e AzoresBus (lista só parcialmente lida) | A |
| R76 | Moovit: página da Carris Metropolitana | https://moovitapp.com/index/pt/transportes_p%C3%BAblicos-lines-Lisboa-2460-3668250 | Exemplo de página de operador (344 rotas e 3 780 paragens numa sub-agência) | A |
| R77 | Moovit Tickets | https://moovitapp.com/tickets/en | Bilhetes de autocarro/comboio/ferry em 40+ países; Porto-Lisboa como exemplo | A |
| R78 | Time Out Lisboa (2024-01-09) | https://www.timeout.pt/lisboa/pt/noticias/olhe-para-o-google-maps-ja-la-andam-os-autocarros-da-carris-metropolitana-010924 | Tempo real da Carris Metropolitana (mais de 1 700 autocarros e cerca de 12 000 paragens) em várias apps | C |
| R79 | Trustpilot: moovit.com | https://ca.trustpilot.com/review/moovit.com | 1,3 em 5 (186 avaliações); temas de queixa em inglês | C |
| R80 | Portal da Queixa: Moovit | https://portaldaqueixa.com/brands/moovit | Uma queixa portuguesa (cobranças indevidas, abril de 2026) | C |
| R81 | Vídeo público no YouTube | https://www.youtube.com/watch?v=iIYwbvdWrvE | Tutorial de terceiros sobre horários; descrição genérica, pouco útil | C |

Pistas de dados para a fase seguinte (fora do âmbito da recolha sobre a Moovit; por confirmar): o NAP Portugal (IMT) apresenta-se como ponto nacional de acesso a dados de transportes (https://nap-portugal.imt-ip.pt/nap/home; foi apenas visto de relance, não explorado). A Carris Metropolitana terá dados de tempo real em formato aberto (frase só vista num resumo de pesquisa do comunicado de imprensa da operadora; o PDF não abriu).

## 2. Funções por área

Marcas: [grátis] = disponível sem subscrição; [Moovit+] = atrás da subscrição; [iOS] = só no iPhone; [PT?] = por confirmar se existe em Portugal; [confirmado] = visto na fonte; "por confirmar" = não confirmado.

### 2.1 Planeador de viagens

- Pesquisa de destino. No separador Direções (ecrã inicial) há uma barra de pesquisa (`Where do you want to go?`). A origem por omissão é a localização atual e pode ser editada no ecrã seguinte. Também se pode partir de um favorito ou de uma viagem recente. [grátis] [confirmado R25, R15]
- Lista de percursos. Para cada um mostra duração total, tempo a pé, número de transbordos, hora a que se deve sair e nome da primeira paragem. Onde há tempo real, mostra a chegada ao vivo da primeira linha (a verde). Se uma linha do percurso tem alerta de serviço, o número da linha leva o ícone do alerta. [grátis] [confirmado R25, R32]
- Quando partir. O botão `Depart Now` (canto superior esquerdo) abre a escolha de data e hora de partida (planear para mais tarde). [grátis] [confirmado R25, R21]
- Chegar a uma hora. Por confirmar: o Centro de Ajuda só documenta a hora de partida; a página de funcionalidades cita um crítico a dizer que a app indica quando sair para chegar a uma hora marcada (R07).
- Filtro por tipo de transporte. Botão `Filter` (canto superior direito) escolhe os modos a mostrar. [grátis] [confirmado R25]
- Modos preferidos e menos preferidos (`Smart Trips`). Escolhem-se no arranque (onboarding) e mudam-se no `Filter` ou em Mais, Definições, `Trip Plan`. Se não houver melhor, a app pode propor um modo menos preferido. [grátis] [confirmado R26]
- Menos caminhada. O FAQ refere uma preferência `least walking` num separador de preferências antes de planear. [grátis] [confirmado R21, texto de 2023]
- Menos transbordos / caminhar mais. Filtros inteligentes no topo dos resultados (`Fit`, `Fewest Transfers`, `Least Walking`) trazidos pelo Assistente. [Moovit+, Android primeiro] [confirmado R16, R48]. Se existem na versão gratuita: por confirmar.
- Percursos acessíveis (cadeira de rodas, carrinho de bebé). O planeador calcula-os e, em algumas cidades, marca estações e linhas acessíveis. Onde se liga a opção: por confirmar. [confirmado R08]
- Etiquetas `Trip Insights` (cor roxa) junto de cada percurso: personalizado (`Best For You`), `Weather Smart` (chuva: menos caminhada e transbordos cobertos), transbordo apertado, alta frequência, último, mais cedo, direto, menos caminhada, espera longa. [Moovit+] [confirmado R48, R16]
- Reencaminhar durante a viagem (alternativas mais rápidas se a linha se atrasa ou se perde uma ligação) e sugerir a melhor hora de saída. [Moovit+] [confirmado R48, R16]
- Comparar no mapa. Botão azul `Compare on Map`: todos os percursos sugeridos desenhados em cores no mapa (etapas a pé, transbordos, veículos) e cartões deslizáveis. [Moovit+] [confirmado R52]
- Mobilidade partilhada no percurso. Bicicletas, trotinetes e ciclomotores de mais de 300 empresas, como percurso completo ou parte de um combinado; mostra tempo a pé até ao veículo, custo estimado e autonomia. [grátis] [PT?] [confirmado R28]. Rotas de bicicleta próprias combinadas com transporte só existem em áreas suportadas. [confirmado R01]
- Custo e pegada de carbono. Mostra custo estimado onde há tarifas e CO2e junto aos percursos em regiões selecionadas. [PT?] [confirmado R21, R29, R54]
- Itinerário (desde a v5.123). Mapa no topo; por baixo, resumo (duração, hora de chegada, custo); etapas em linha temporal, cada uma com a sua cor, com tempo real, duração, custo, número de paragens e onde sair; foto e ID da paragem quando existem; botões para ver o veículo no mapa, avisos de chegada, partilhar e iniciar a navegação. [confirmado R54, R25, R59]
- Partilhar direções. Botão Partilhar no itinerário; qualquer app de mensagens; quem recebe vê o percurso sem ter a Moovit. [grátis] [confirmado R27]
- Limites assumidos pela Moovit. As durações são estimativas e não se ajustam ao trânsito nem durante a viagem; sem resultado quando a viagem está fora da área de cobertura. [confirmado R25, R21]
- Planeador entre regiões (v5.139, costa leste dos EUA). Fora de Portugal; se existe em Portugal: por confirmar. [R19]

### 2.2 Tempo real

- Chegadas por paragem e por linha. A verde = tempo real; a preto = horário previsto ou estimativa. [grátis] [confirmado R20, R21]
- Código de cores da fiabilidade. Verde: sinal GPS forte e frequente; amarelo: sinal menos frequente; vermelho: sinais raros; vermelho com ponto de exclamação: o veículo desviou-se do percurso; preto: sem informação ao vivo (hora do horário ou estimativa a partir dos atrasos habituais da linha). Acima de 1 hora mostra a hora exata. Tocar na hora abre o detalhe da precisão. [confirmado R23]
- Origem dos dados. GPS nos veículos (segundo a ficha da Play); sem GPS, horário oficial ou estimativa histórica; a Moovit refere também dados de utilizadores. [confirmado R01, R23, R64]
- Veículo no mapa (`Live Location`). Botão na barra de ações do itinerário, do detalhe da linha e do detalhe da paragem; mostra um autocarro (ou comboio, elétrico, ferry, teleférico) a mover-se, a hora do último sinal e os alertas da linha; só para linhas com GPS. Lançada na v5.104 (nov. 2022). Está listada nas funções da Moovit+ (R45, R51); o artigo geral não diz que seja paga (R30). Por confirmar o que é grátis. 
- Lotação do veículo. Três níveis (lugares disponíveis, só em pé, muito cheio), em cidades selecionadas e conforme os dados da operadora. [grátis] [PT?] [confirmado R56]
- Lotação da paragem. Quatro níveis (não cheia, um pouco cheia, cheia, muito cheia) com base em relatos dos últimos 10 minutos. Depende de utilizadores a reportar. [PT?] [confirmado R55]
- Erro de fuso horário (Android). Horas a mais de 60 minutos ou negativas indicam fuso errado no telemóvel. [confirmado R34]
- Em Portugal. A marca usa o nome `Vá no Verde` para os minutos a verde (R68). Tempo real confirmado: Carris Metropolitana (parceria oficial de 2022; alargado em 2024) e Metropolitano de Lisboa (2022-07-28). Outras operadoras: por confirmar. [R13, R14, R68, R78]

### 2.3 Navegação ao vivo e avisos para sair

- `Live Directions`. Depois de escolher o percurso, tocar em `Navigate` (ou `Start`) inicia a navegação passo a passo: caminhar até à paragem, que linha apanhar e quanto esperar, quantas paragens faltam, aviso para sair e caminhada final. Funciona em segundo plano. Exige localização sempre ligada. [grátis] [confirmado R24, R25, R15, R18]
- Cartões de instruções. Deslizam-se (o cartão, não o mapa) para o passo seguinte ou anterior. O sino no canto desliga as notificações da viagem. [confirmado R24]
- Aviso para sair (`Get Off Alert`). Notificação quando se aproxima da paragem e quando é para sair; chega com o telemóvel bloqueado. [grátis] [confirmado R15, R18, R24]
- Avisos de chegada (`Arrival Updates`). Pelo sino ou botão `Arrival Updates`, escolhe-se uma linha e recebem-se avisos 10 minutos antes de chegar à paragem de partida, 5 minutos antes, à chegada e quando há mudanças súbitas. Só uma linha de cada vez; fica um cartão no ecrã Direções. [Moovit+] [confirmado R53, R11]
- Partilha de percurso em curso (`Safe Ride`). Contactos de segurança recebem um SMS com ligação a uma página web (sem app) com o percurso, a localização em tempo real, o ETA, desvios e chegada. Os contactos têm de aceitar. [Moovit+] [confirmado R50]
- Reencaminhamento durante a viagem. [Moovit+] [confirmado R48]
- Way Finder. No iPhone: botão azul, sinais digitais sobre a câmara a apontar o caminho para a paragem; a metade inferior mostra o mapa; ao baixar o telemóvel só fica o mapa. [iOS] [confirmado R33]. Na Moovit+ (Live View): botão `Way Finder` junto da paragem mostra uma vista panorâmica do local. [Moovit+] [confirmado R49]

### 2.4 Linhas e paragens

- Separador Linhas. Barra de pesquisa por nome ou número; por baixo, separadores por tipo de transporte (autocarro, comboio, elétrico, etc.), com um separador de estrela para as favoritas; as linhas aparecem agrupadas por operadora; um ícone colorido assinala alertas; quando a operadora tem feed no Twitter, as mensagens aparecem no topo. [grátis] [confirmado R22, R32]
- Detalhe da linha. Lista de paragens com a hora de chegada à mais próxima e a hora estimada em cada paragem; `Full Schedule` para o horário do dia; `Change direction`; ícone do mapa com o percurso; estrela para favoritar; seta de direção (v5.35). [confirmado R22, R20]
- Horário completo por paragem. Todas as linhas que passam e as próximas chegadas; `VIEW ALL LINES FOR THIS STATION`. [confirmado R20, R37]
- Pesquisa de paragens. Pelo nome na barra de pesquisa do mapa de Paragens. [confirmado R21, R57]
- Foto e ID da paragem para a reconhecer. [confirmado R59, R25]

### 2.5 Paragens e estações perto de mim

- Separador Paragens (também chamado Stations). Mapa centrado no ponto azul; cada estação é um ícone; tocar mostra as linhas e as próximas partidas. [confirmado R31]
- Cartões `NEARBY` e `FAVORITES`: arrastar o cartão para cima esconde o mapa e mostra a lista de paragens. [confirmado R31]
- Barra de pesquisa no mapa (v5.45) para ver outra zona; toque longo num ponto abre ações (direções de/para, adicionar aos favoritos no Android). [confirmado R57, R31]
- Camadas (botão no canto superior esquerdo) para escolher que serviços partilhados aparecem. [confirmado R31]
- Botão para pedir o táxi livre mais próximo nas cidades com esse serviço. [PT?] [confirmado R20]
- Pergunta frequente: "onde está a paragem mais próxima?" responde-se com o ícone de estação em baixo e a lista de linhas. [confirmado R21]

### 2.6 Favoritos

- Casa e Trabalho, mais outros locais (`Add`). No ecrã Direções, cada favorito mostra a próxima linha, a hora de partida e a duração. Adiciona-se escrevendo a morada ou escolhendo no mapa, dá-se um nome e grava-se (`Save & Get Directions`); também pela estrela nos resultados de pesquisa. Editar ou remover pelos três pontos. [grátis] [confirmado R35, R66]
- Linhas favoritas. Estrela no detalhe da linha; pede a paragem habitual; mostra chegadas dessa paragem e avisa de alterações e perturbações. [grátis] [confirmado R36, R22]
- Paragens favoritas. Estrela na paragem (`Add to Favorites`); ficam no cartão `FAVORITES`. [grátis] [confirmado R37]
- Cartões inteligentes (`Smart Cards`) sob a barra de pesquisa: melhor caminho para casa (quando está longe), melhor caminho para o trabalho (dias úteis de manhã), viagens recentes (últimos 30 dias, mesmo dia da semana e hora aproximada), paragem favorita (quando está perto), estação mais próxima, último percurso. Ordenados por prioridade e disponibilidade dos dados. [grátis] [confirmado R43]
- Calendário (iPhone). Eventos com morada aparecem no ecrã inicial e levam às direções com um toque; os dados ficam só no telemóvel. [iOS] [confirmado R60]
- Os favoritos estão ligados à conta: apagar a conta apaga-os. [confirmado R44]

### 2.7 Alertas de serviço e perturbações

- Ícones por gravidade junto da linha: vermelho (grave, normalmente sem serviço), amarelo (moderado ou menor), azul (outros avisos da operadora). Numa versão antiga havia também verde para "normal". Tocar abre o detalhe. [confirmado R32, R58]
- Onde aparecem: lista de linhas, pré-visualização dos percursos, detalhe da linha e a lista `Agency Alerts` (menu), que agrupa tudo por operadora. [confirmado R32]
- Receber notificações: favoritar a linha ou ligar o interruptor `Notify me about future service alerts to my line`. [confirmado R32, R58]
- Captura da Play: ecrã de alertas com lista datada e filtros. [confirmado R02]
- Quem escreve os alertas: operadoras e voluntários (alertas "por pedido" e alterações de curto prazo geridas por Mooviters). O site `updates.moovit.com` publica avisos por cidade (há posts de Lisboa de 2021 e 2022; os mais recentes que vi são de outros países). [confirmado R63, R67]

### 2.8 Mapas oficiais e offline

- Vista de mapa com estações, rotas e linhas de metro/autocarro. [confirmado R01, R07]
- Mapas em PDF para uso sem rede ou no subsolo (afirmado na ficha da Play e na página de funcionalidades). Onde se descarregam na app: por confirmar; não há artigo no Centro de Ajuda. [confirmado R01, R02, R07]
- Definições do mapa: ligar ou desligar objetos do mapa (Mais, Definições, Mapa). [confirmado R40]
- Modo offline completo (horários em cache): por confirmar. Um comunicado de 2015 fala de viagens guardadas para uso offline; não há prova de que exista hoje. [R18 e resumo de pesquisa]

### 2.9 Bicicletas e trotinetes partilhadas

- Veículos no mapa de Paragens (carros, bicicletas, e-bikes, trotinetes): localização exata, bateria ou combustível, tempo a pé, custo estimado. Reservar leva à app do parceiro (ou à loja se não a tiver). [confirmado R31, R28]
- Estações de bicicletas com docas em tempo real, só em áreas metropolitanas suportadas. [confirmado R01]
- Em Portugal a ficha da Play lista eCooltra, Gira, VOI e Hive. A lista pode estar desatualizada: por confirmar. [R01]

### 2.10 TVDE e táxis

- Reservar transporte individual sob pedido a partir do ecrã Direções e botão no mapa para o táxi livre mais próximo, onde o serviço existe. [confirmado R20, R65]
- A Moovit integrou a Uber em 2016 em 22 países, incluindo Portugal (resumo de pesquisa de um artigo do Executive Digest; não aberto). O estado atual em Portugal e se há Bolt ou FREE NOW: por confirmar.

### 2.11 Bilhetes e pagamentos

- Carteira de mobilidade (conta Moovit Pay): pagar transporte público, boleia e viagens a pedido; ver bilhetes, histórico, dados de pagamento e faturas. Perfis com desconto verificados. Validação de passe digital (mostrar ao motorista ou ler um código). Só em cidades e operadoras selecionadas. [confirmado R07, R61]
- O Centro de Ajuda só documenta estes serviços para Israel (com a Pango), Roma, Turim e São Paulo. Em Portugal: por confirmar (não encontrei qualquer referência). [confirmado R19]
- Moovit Tickets: compra de bilhetes de autocarro, comboio e ferry de longa distância em mais de 40 países (Porto-Lisboa e Lisboa-Madrid aparecem como viagens sugeridas). O Centro de Ajuda nomeia um parceiro de reservas; pagamento por cartão, Google Pay e Apple Pay; o bilhete digital fica no separador Tickets. [confirmado R77, R62]

### 2.12 Relatórios da comunidade

- Relatos de lotação: ícone `Report` na barra de ações rápidas, `Report crowdedness status`, escolher o nível para a linha ou a estação; outros utilizadores confirmam (visto) ou atualizam (X). Os relatos valem cerca de 10 minutos. [confirmado R55]
- Relatos de alterações de linha e de dados errados (paragem mudou de sítio, horário errado); fotos de paragens; correção de nomes e localização das paragens. [confirmado R07, R59]
- Editor web dos Mooviters com níveis (nível 3 ou mais tem funções extra, como marcar estações acessíveis), Embaixadores e código de conduta; regras para fotos. [confirmado R63]
- Fontes de dados declaradas: operadoras (principal), utilizadores da Moovit e terceiros (plataformas de dados abertos). [confirmado R64]

### 2.13 Acessibilidade

- Compatibilidade com leitores de ecrã (VoiceOver e TalkBack) e botões maiores com a navegação em baixo (para motricidade reduzida). [confirmado R07, R08]
- Texto grande: suporta o tamanho dinâmico do iPhone; layout pensado para não partir. [iOS] [confirmado R08]
- Cor nunca é a única informação; contrastes verificados também em tons de cinza. [confirmado R08]
- Percursos acessíveis e marcação de estações e linhas acessíveis em algumas cidades. [confirmado R08]
- Web com nível WCAG 2.1 AA; parcerias com WeWALK e ONCE. [confirmado R08]
- Sem funções documentadas para surdez ou controlo por voz. [R08]

### 2.14 Notificações

- Da navegação (Live Directions e aviso de saída). [R24]
- De alterações e perturbações das linhas favoritas. [R36, R58]
- De chegada de uma linha (Moovit+). [R53]
- Notícias da app e alertas da área. Em Mais, Definições, Notificações escolhe-se o que se quer receber. [R41]
- SMS aos contactos de segurança. [R50]

### 2.15 Widgets

- Nenhuma fonte pública documenta um widget de ecrã inicial. Por confirmar. (Um resumo de pesquisa fala de queixas, em versões antigas, pela remoção do widget; não verificado.)

### 2.16 Conta e login

- A conta é opcional para o uso básico (planear, ver linhas, favoritos locais): por confirmar. Há conta para Safe Ride (a app pede para "configurar a conta Moovit"), pagamentos (Moovit Pay, em Israel) e para a comunidade (Editor). [R50, R61, R63]
- Métodos de início de sessão (Google, Apple, e-mail, telefone): por confirmar. A conta de pagamentos usa telefone com código. [R61]
- Pedir a eliminação da conta e dos dados: no Android, formulário de feedback com o tipo `Personal Data/GDPR`; no iPhone, Definições, Privacidade, `Delete Account`. [confirmado R44]
- Dados que a app declara recolher (Play): localização aproximada e exata, nome, e-mail, IDs, morada, telefone, fotos, informação de pagamento e histórico de compras, registos de falhas, interações, histórico de pesquisas, apps instaladas, ficheiros. Partilha com terceiros: IDs do dispositivo e informação de pagamento. Encriptado em trânsito; permite pedir eliminação. [confirmado R03]

### 2.17 Definições

- Menu `More` (canto superior do ecrã inicial; em versões antigas o menu lateral) com: Definições, Moovit+ / versão sem anúncios, Agency Alerts, Feedback. A lista completa: por confirmar. [R41, R21, R32, R44]
- Definições conhecidas: área ("metro"), `Trip Plan` (modos preferidos), Mapa (objetos), Notificações, Idioma preferido (iPhone), Privacidade (apagar conta, iPhone). [confirmado R26, R38, R39, R40, R41, R44]
- Mudar de área ("metro"): tocar no nome da área no canto superior esquerdo (ou Definições, Metro Area), pesquisar e escolher; os dados passam a ser os dessa cidade. [confirmado R38]
- Modo escuro segue o do sistema (Android desde a v5.107; iPhone desde a v5.38). [confirmado R42]
- Se o telemóvel tem a ligação boa mas a app diz que não: atualizar, reiniciar, limpar cache (conselho do FAQ). [R21]

### 2.18 Idiomas

- Mais de 40 idiomas na app (45 na ficha da Play). O idioma segue o do telemóvel; no iPhone pode ser diferente. Em alguns casos o mapa mostra nomes na língua local. O Centro de Ajuda existe em 14 idiomas, incluindo português e português do Brasil. [confirmado R39, R02, R19]

### 2.19 Outras funções

- Pegada de carbono e projeto "Low Carbon Commute" (créditos de carbono; programa comercial). [confirmado R29]
- MoovitAI: pergunta em linguagem natural (ícone de estrelas), por exemplo "qual a paragem mais próxima" ou "que linha chega mais depressa ao hospital". [Moovit+] [confirmado R48]
- Web app em moovitapp.com e páginas web por cidade, linha e paragem (também servem de SEO). [confirmado R06, R70]
- Feedback e pedido de apoio na app e por formulário. [R19, R44]
- FlexiRide: outra app da Moovit para transporte a pedido. [confirmado R01]
- Negócios B2B (não são funções do consumidor): Moovit Ads, MaaS com apps de marca, análise de mobilidade, APIs, TimePro (tempo real para autocarros), transporte a pedido. [confirmado R06, R12]
- Relatório global de transportes públicos e ferramentas de dados no site (não são funções da app). [R06]

## 3. Moovit+ (subscrição) e anúncios da versão gratuita

### 3.1 O que fica atrás da subscrição

| função | versão gratuita | Moovit+ | fonte | nota |
| --- | --- | --- | --- | --- |
| Sem anúncios | mostra anúncios | sim | R45, R11 | a opção "versão sem anúncios" existe na maioria das áreas (R21) |
| Safe Ride (partilhar a viagem com contactos de segurança) | não | sim | R50, R45 | precisa de conta Moovit; contactos aprovam por SMS |
| Compare Routes (comparar percursos no mapa) | não | sim | R52 | botão azul na lista de percursos |
| Arrival Updates (avisos de chegada de uma linha) | não | sim | R53, R11 | uma linha de cada vez |
| Live Location (veículo no mapa) | por confirmar | listada como Moovit+ | R45, R51 (lista como Moovit+); R30 (artigo geral sem restrição) | há críticas de que o tempo real e os veículos ao vivo desapareceram para alguns (R04); não é claro se é falha ou nova restrição |
| Live View (vista panorâmica da paragem) | não | sim | R49 | botão `Way Finder` junto da paragem |
| Assistente: etiquetas Trip Insights, filtros inteligentes, reencaminhamento | não | sim (Android primeiro, iPhone a seguir) | R48, R16, R01 | a ficha da Play destaca `Best For You`, `Smart Weather` e `Tight Transfer Alerts` |
| MoovitAI (perguntas em linguagem natural) | não | sim (iPhone primeiro, Android a seguir) | R48 | ícone de estrelas |
| "Descontos" | por confirmar | por confirmar | R04 | as respostas do programador na Play falam de "recursos e descontos"; sem detalhe |

Continua grátis (segundo a Moovit): planeador, informação em tempo real, alertas de serviço, sugestões personalizadas, indicações passo a passo e favoritos (R11, R12, R45).

### 3.2 Preço em Portugal (o que se encontrou)

- Moovit (R46): dois planos, mensal e anual (o anual chega a 50% de desconto), geridos pela loja de apps. Não publica o preço no Centro de Ajuda.
- Google Play Portugal (R01): os itens de compra na app vão de 0,99 EUR a 19,99 EUR (faturados pelo Google Play). Não indica que item é cada um.
- App Store Portugal (R05): dez itens sem nome, em EUR: mensais a 2,99 e a 0,99; de 6 meses a 13,49 e a 9,99; anuais a 5,99, 8,99, 11,99, 15,99, 17,99 (dois itens iguais a 17,99). Indica vários produtos, promoções ou escalões; não há uma tabela única.
- Leitura prática: mensal por volta de 3 EUR e anual entre 6 e 18 EUR (valores observados, não uma tabela oficial). O preço atual exato dentro da app: por confirmar.
- Teste gratuito: terceiros e críticas falam em 7 dias (R79, R04). Por confirmar.
- Cancelar: na loja (Google Play, Pagamentos e subscrições, Subscrições, Moovit, Cancelar). Eliminar a conta Moovit não cancela. Restaurar num telemóvel novo: ir a uma função Moovit+, abrir o ecrã de planos e tocar `Restore purchase`. Não se transfere entre Android e iPhone (R47).

### 3.3 Anúncios da versão gratuita

Tipos (página de vendas da Moovit Ads, R09, R10):
- Banners: formato fixo 320x50 e formato 300x250.
- Intersticiais: mensagens a ecrã inteiro que, segundo essa página, o utilizador não pode dispensar; com ligação para o site ou compra.
- Anúncios no mapa, para quem está perto de um local.
- Anúncios segmentados: por localização atual e pontos do percurso (origem, destino, transbordos), perfil (por exemplo turistas ou estudantes), modo de transporte preferido, padrões de viagem, hora do dia; com geovedação.

Onde aparecem: a Moovit não o diz. Segundo as críticas públicas (R04, R05, R79):
- Logo ao abrir a app.
- A cada poucos toques ao pesquisar ou abrir percursos (relatos de um anúncio a cada 3 ou 4 toques; de 3 ou 4 anúncios só para abrir um percurso e outros 2 para acompanhá-lo).
- Banner estático por cima da lista de percursos e de linhas, que tapa informação.
- Durante o acompanhamento da viagem.
- Anúncios que abrem outra app ou a loja sem toque do utilizador; botões de fechar falsos, minúsculos ou lentos; relatos de instalações automáticas de jogos.

Resposta-tipo do programador às queixas: os anúncios mantêm o serviço gratuito e a Moovit+ remove-os (R04).

Privacidade ligada à publicidade: a ficha da App Store diz que identificadores são usados para seguimento entre apps (R05); a Play diz que IDs do dispositivo e informação de pagamento são partilhados com terceiros (R03).

## 4. Inventário de ecrãs

Notas de leitura: a coluna "estados vistos" só lista estados que alguma fonte pública mostra ou descreve. Os restantes (vazio, a carregar, erro, sem rede, sem permissão de localização) ficam por confirmar. Recomendação para a Paragem Certa: definir estados próprios (vazio, a carregar, erro, sem rede, sem permissão de localização) em todos os ecrãs.

| ID | ecrã | como se chega lá | para que serve | componentes principais | estados vistos |
| --- | --- | --- | --- | --- | --- |
| S01 | Primeira abertura (onboarding) | Abrir a app depois de instalar | Detetar a área, pedir localização, escolher modos preferidos | Pedido de permissão do sistema; seleção de modos preferidos e menos preferidos (autocarro, comboio, metro, bicicleta ou trotinete partilhada, táxi) | Escolha de modos faz parte do arranque (R26). Sem localização: por confirmar (o FAQ sugere que usa a área configurada, R21) |
| S02 | Início, separador Direções | Abrir a app; separador inferior 1 | Planear viagens e aceder a favoritos e recentes | Barra de pesquisa; nome da área (canto superior esquerdo); menu Mais; linha de favoritos (Casa, Trabalho, `Add`) com próxima linha, hora e duração; recentes; Smart Cards (carrossel horizontal); botão de táxi onde existe; botão de calendário (iPhone); barra inferior com 3 separadores; anúncios | Sem favoritos: botões Casa, Trabalho e Add (R35). Smart Cards aparecem conforme a prioridade e os dados (R43). Anúncios (R04) |
| S03 | Pesquisa de local | Tocar na barra de pesquisa | Escrever o destino e escolher sugestão ou recente | Campo de texto; sugestões; recentes com estrela para guardar como favorito; opção de escolher no mapa | Estrela ao lado de cada recente (R35). Sem resultados: por confirmar |
| S04 | Escolher partida | Ecrã seguinte à pesquisa (se a partida não é o local atual) | Mudar a origem | Campos de origem e destino; troca | Origem por omissão = localização atual (R25). Sem localização: origem manual (por confirmar) |
| S05 | Percursos sugeridos | Depois de escolher o destino | Comparar opções e escolher | `Depart Now` (esquerda); `Filter` (direita); cartões de percurso (duração, tempo a pé, transbordos, hora de saída, 1.ª paragem, chegada ao vivo a verde, ícone de alerta, lotação, custo, CO2e); etiquetas Trip Insights e filtros inteligentes (Moovit+); botão azul `Compare on Map` (Moovit+); anúncios | Com e sem tempo real (verde ou preto). Com alerta de serviço. Sem resultados quando fora da cobertura (R21) |
| S06 | Filtro de transportes e preferências | `Filter` na lista, ou Mais, Definições, `Trip Plan` | Escolher modos e preferências de caminhada | Lista de modos com estado preferido/menos preferido; preferência `least walking` | Pode propor modo menos preferido se não houver melhor (R26) |
| S07 | Data e hora de partida | `Depart Now` | Planear para outra hora | Seletor de data e hora (`Set your departure time`) | Partir agora / hora futura (R21). "Chegar às": por confirmar |
| S08 | Itinerário (Direções do percurso) | Tocar num percurso | Ver o percurso completo | Mapa no topo; resumo (duração, chegada, custo); linha temporal com etapas coloridas; tempo real por etapa; foto e ID da paragem; botões Live Location, sino de chegada, Partilhar, `Navigate`/`Start` | Com e sem tempo real; com GPS ou só percurso no mapa (R51). Lotação por etapa (R56) |
| S09 | Comparar no mapa (Moovit+) | `Compare on Map` | Ver todos os percursos no mapa | Mapa com percursos em cores; cartões deslizáveis | Para subscritores; sem subscrição leva ao ecrã de planos (por confirmar) |
| S10 | Direções ao vivo (Live Directions) | `Navigate`/`Start` no itinerário | Guiar passo a passo até ao destino | Mapa que segue o utilizador; cartão de instruções (deslizar para o passo seguinte ou anterior); sino para desligar notificações; tempo de espera e paragens que faltam | Exige localização sempre ligada (R24). Funciona em segundo plano (R18, R24). Sem sinal GPS: por confirmar |
| S11 | Notificações da viagem | Sistema, durante o S10 | Avisar a aproximação e o momento de sair | Notificação do sistema, mesmo com o telemóvel bloqueado | Desligam-se com o sino ou nas Definições (R24, R41) |
| S12 | Separador Paragens (mapa) | Separador inferior 2 | Ver paragens e serviços partilhados à volta | Mapa com ponto azul; ícones de estações; cartões `NEARBY` e `FAVORITES` (arrastar para cima); barra de pesquisa; camadas (canto superior esquerdo); ícones de veículos partilhados; botão de táxi; botão azul de Way Finder (iPhone) | Ponto azul = localização atual (R31). Mostra a área definida se não houver localização (inferido; por confirmar) |
| S13 | Detalhe da paragem | Tocar numa estação | Ver linhas e próximas chegadas | Lista de linhas com próxima chegada (verde ou preto); nível de lotação (4 níveis); foto; estrela `Add to Favorites`; `VIEW ALL LINES FOR THIS STATION`; barra de ações (Live Location, Report); Way Finder | Tempo real vs horário (R20). Lotação só com relatos dos últimos 10 min (R55) |
| S14 | Foto da paragem | Ícone de foto na paragem | Reconhecer a paragem; tirar foto se não existir | Foto; câmara; diretrizes de fotos | Sem foto: convida a tirar uma (R59) |
| S15 | Way Finder / Live View | Botão azul (iPhone) ou botão Way Finder na paragem (Moovit+) | Achar a paragem | Câmara com sinais em RA (iPhone); vista panorâmica (Moovit+) | Metade do ecrã é mapa; ao baixar o telemóvel fica só o mapa (R33) |
| S16 | Separador Linhas | Separador inferior 3 | Procurar linhas | Barra de pesquisa; separadores por tipo de transporte; separador estrela (favoritas); linhas por operadora; ícones de alerta; mensagens do Twitter da operadora no topo | Ícone vermelho, amarelo ou azul nas linhas com alerta (R32). Sem favoritas: por confirmar |
| S17 | Detalhe da linha | Tocar numa linha | Ver paragens, horários e sentido | Lista de paragens com hora de chegada; `Full Schedule`; `Change direction`; ícone de mapa; estrela; seta de direção; alertas | Sentido errado corrige-se com `Change direction` (R21). Sem tempo real: horário |
| S18 | Horário completo | `Full Schedule` | Ver todas as horas do dia numa paragem | Lista de horas por paragem | Por confirmar |
| S19 | Mapa da linha / veículo ao vivo | Ícone de mapa ou `Live Location` | Ver o percurso e onde vai o veículo | Percurso e paragens no mapa; ícone do veículo a mover-se; hora do último sinal; alertas da linha | Sem GPS: só percurso no mapa (R45). Hora do último sinal visível (R30) |
| S20 | Detalhe do alerta de serviço | Tocar no ícone colorido | Ler o alerta e ligar notificações | Gravidade (cor); texto; interruptor `Notify me about future service alerts to my line` | Três cores de gravidade (R32) |
| S21 | Alertas por operadora (`Agency Alerts`) | Menu | Ver todas as alterações por operadora | Lista por operadora com datas e filtros | Captura da Play mostra lista datada com filtros (R02) |
| S22 | Favoritos (locais) | Linha de favoritos do S02 | Ir a um favorito com 1 toque | Casa, Trabalho, `Add`; menu de três pontos (`Edit Favorite`, `Remove Favorite`) | Vazio: botões para adicionar (R35) |
| S23 | Adicionar ou editar favorito | `Add`, Casa, Trabalho ou estrela | Guardar um local | Campo de morada ou ponto de interesse; escolher no mapa; nome; `Save & Get Directions` | Sair com o X sem viajar (R66) |
| S24 | Menu Mais | Canto superior do ecrã inicial | Aceder a definições e extras | Definições; Moovit+ / versão sem anúncios; Agency Alerts; Feedback; (conta) | Lista completa: por confirmar |
| S25 | Definições | Mais, Definições | Personalizar | Área (Metro Area); `Trip Plan`; Mapa; Notificações; Idioma preferido (iPhone); Privacidade (apagar conta, iPhone) | Lista completa: por confirmar |
| S26 | Definições: notificações | Definições, Notificações | Escolher que notificações receber | Lista de tipos de notificação | Remover as que não interessam (R41) |
| S27 | Definições: mapa | Definições, Mapa | Ligar ou desligar objetos do mapa | Interruptores | Por confirmar |
| S28 | Mudar de área (metro) | Nome da área ou Definições, Metro Area | Escolher a cidade | Pesquisa; lista de áreas | Atualiza os dados disponíveis (R38) |
| S29 | Planos da Moovit+ (ecrã de pagamento) | Mais, Moovit+; ou qualquer função Moovit+ | Subscrever | Planos mensal e anual; confirmação da compra na loja; `Restore purchase` | Telemóvel novo: restaurar (R47) |
| S30 | Safe Ride | `Activate Safe Ride` no ecrã Moovit+ | Definir contactos de segurança | Criar conta; adicionar contactos por nome e número ou pela agenda; lista `My Safety Contacts` (aprovado ou recusado; editar/remover) | Contacto pode recusar; pode sair (R50) |
| S31 | Assistente / MoovitAI | Ícone de estrelas | Perguntar em linguagem natural | Caixa de pergunta; respostas; etiquetas de percurso | Moovit+; chega por fases (R48) |
| S32 | Reportar | Ícone `Report` na barra de ações rápidas | Informar lotação (e outros problemas) | `Report crowdedness status`; níveis; confirmar (visto) ou atualizar (X) | Relato expira em cerca de 10 min (R55). Outros tipos de relato: por confirmar |
| S33 | Partilhar direções | `Share` no itinerário | Enviar o percurso a alguém | Folha de partilha do sistema (e-mail, WhatsApp, Messenger); página web para quem não tem a app | Funciona sem a app instalada no destinatário (R27) |
| S34 | Cartão de veículo partilhado | Tocar num ícone de veículo no mapa | Ver detalhes e desbloquear | Localização exata; bateria ou combustível; tempo a pé; custo; passa para a app do parceiro | Sem a app do parceiro: vai para a loja (R28) |
| S35 | Pedir táxi ou TVDE | Botão no mapa ou no ecrã Direções | Chamar o táxi livre mais próximo | Cartão do serviço | Só onde existe; em Portugal: por confirmar (R20) |
| S36 | Conta e carteira (Moovit Pay) | `Join` no ecrã Direções (Israel) | Pagar transporte público | Telefone com código; nome; e-mail; ID; cartão; perfis com desconto; histórico e faturas | Não aplicável em Portugal (por confirmar) (R61) |
| S37 | Moovit Tickets (longa distância) | Separador Tickets ou site | Comprar bilhetes de autocarro, comboio e ferry | Datas, origem e destino, horários e preços; pagamento por cartão, Google Pay, Apple Pay | Bilhete digital no separador Tickets (R62) |
| S38 | Anúncios (banner, intersticial, no mapa) | Em vários ecrãs (S02, S05, S08, S10) | Receitas da versão gratuita | Banners 320x50 e 300x250; intersticial; anúncio no mapa | Ver secção 3.3 |
| S39 | Pedidos de permissão do sistema | Primeiro uso de localização, notificações, câmara, contactos, calendário | Autorizar funções | Diálogos do sistema (localização "sempre" para a navegação ao vivo) | Live Directions exige localização sempre ligada (R24) |

## 5. Fluxos

Contagem de toques: estimada a partir das descrições do Centro de Ajuda (não medida numa instalação). Não conta a escrita de texto nem os diálogos do sistema. É o número a bater pela Paragem Certa.

```
F01 Planear uma viagem agora (ciclo central)
    S02 -> S03 -> S05 -> S08
    toques no caminho feliz: 3 (barra de pesquisa, sugestão, percurso) + escrever o destino
    casos-limite: sem permissão de localização (origem manual, +2 toques); destino fora da
    cobertura (sem resultados); sem tempo real (hora a preto); linha com alerta de serviço;
    anúncio pelo meio na versão gratuita

F02 Navegar ao vivo e ser avisado de quando sair
    S08 -> S10 -> S11
    toques: 1 a partir do itinerário (Navigate/Start); 4 desde o ecrã inicial
    casos-limite: localização "sempre" não autorizada; app em segundo plano ou ecrã bloqueado;
    desligar avisos pelo sino; desvio de rota; sem GPS; veículo atrasado ou cancelado

F03 Planear para outra hora
    S02 -> S03 -> S05 -> S07 -> S05 -> S08
    toques: cerca de 7 (barra, sugestão, Depart Now, data e hora, confirmar, percurso)
    casos-limite: horário só existe numa janela de dias; serviço noturno; "chegar às" por confirmar

F04 Filtrar por modos e preferências
    S05 -> S06 -> S05
    toques: pelo menos 3 (Filter, escolher modos, voltar)
    casos-limite: filtro sem resultados; a app pode propor um modo menos preferido se não houver melhor

F05 Ver as próximas chegadas na paragem mais próxima
    S02 -> S12 -> S13
    toques: 2 (separador Paragens, ícone da paragem); ou 1 + deslizar o cartão NEARBY
    casos-limite: sem localização (mapa na área definida); paragem sem tempo real (preto);
    nenhuma linha a passar; lotação sem relatos recentes

F06 Consultar uma linha e o horário completo
    S02 -> S16 -> S17 -> S18
    toques: 4 (separador Linhas, linha, paragem, Full Schedule) + pesquisa
    casos-limite: sentido errado (Change direction); linha com alerta; data sem horário

F07 Guardar Casa e Trabalho
    S02 -> S23 -> (S05)
    toques: 3 + escrever (Casa, escolher a morada, guardar)
    casos-limite: morada ambígua; editar ou remover (três pontos); sair sem viajar (X);
    depois surge o cartão "melhor caminho para casa" quando se está longe

F08 Favoritar uma linha e receber alertas
    S16 -> S17 -> escolha da paragem habitual
    toques: 4 (Linhas, linha, estrela, paragem)
    casos-limite: notificações desligadas; sentido errado; linha com várias paragens

F09 Favoritar uma paragem
    S12 -> S13
    toques: 3 (Paragens, ícone, Add to Favorites); fica no cartão FAVORITES
    casos-limite: paragem sem linhas; removida do mapa

F10 Ver o veículo a mover-se no mapa
    S08 | S13 | S17 -> S19
    toques: +1 (Live Location) a partir de qualquer um dos três ecrãs
    casos-limite: linha sem GPS (só o percurso); sinal antigo (hora do último sinal);
    possivelmente exige Moovit+ (por confirmar)

F11 Ver e subscrever alertas de serviço de uma linha
    S16 -> S17 -> S20
    toques: 3 a 4 (Linhas, linha com ícone, ícone, interruptor)
    casos-limite: alerta vermelho num percurso planeado; alertas azuis só informativos; lista por operadora (S21)

F12 Receber avisos de chegada de uma linha (Moovit+)
    S08 | S13 -> sino -> escolha da linha
    toques: 2 (sino ou Arrival Updates, escolher a linha)
    casos-limite: sem subscrição leva ao ecrã de planos (S29); só uma linha de cada vez;
    notificações desligadas

F13 Reportar lotação
    S13 -> S32
    toques: 3 (Report, Report crowdedness status, nível); confirmar relato alheio: 1; atualizar: 2
    casos-limite: relatos expiram em cerca de 10 min; sem dados da operadora; sem comunidade não há relatos

F14 Partilhar um percurso
    S08 -> S33
    toques: 2 (Partilhar, escolher a app) + escolher o destinatário
    casos-limite: destinatário sem Moovit (abre página web); percurso desatualizado

F15 Subscrever, cancelar e restaurar a Moovit+
    S24 -> S29 -> loja
    toques: 4 para subscrever (Mais, Moovit+, plano, confirmar na loja); cancelar é na loja, fora da app;
    restaurar: ecrã de planos e Restore purchase
    casos-limite: eliminar a conta Moovit não cancela; Android para iPhone não se transfere;
    cobrança após teste gratuito (queixas, secção 9)

F16 Configurar o Safe Ride
    S29 -> S30 -> contactos
    toques: 6 ou mais (Activate Safe Ride, criar conta, adicionar contactos...)
    casos-limite: contacto recusa o SMS; remover contacto; desvio de rota; chegada ao destino

F17 Mudar de área (cidade)
    S02 -> S28 -> S02
    toques: 3 + escrever (nome da área, pesquisar, escolher)
    casos-limite: área sem dados; se Portugal inteiro cabe numa só área: por confirmar

F18 Definir modos preferidos nas Definições
    S24 -> S25 -> S06
    toques: 4 ou mais (Mais, Definições, Trip Plan, escolher)
    casos-limite: nenhum modo preferido; todos "menos preferidos"

F19 Usar bicicleta ou trotinete partilhada
    S12 -> S34 -> app do parceiro
    toques: 3 até ao desbloqueio na app do parceiro
    casos-limite: app do parceiro não instalada (vai para a loja); sem veículos perto; pouca bateria

F20 Pedir um táxi ou TVDE
    S02 | S12 -> S35
    toques: 2 a 3; em Portugal: por confirmar
    casos-limite: sem serviço na cidade; sem parceiro

F21 Explorar o mapa e pedir direções para um ponto
    S12 -> (toque longo) -> S05
    toques: 3 (Paragens, toque longo, "direções para aqui"); no Android também guardar como favorito
    casos-limite: ponto sem transporte; pesquisa de outra zona pela barra do mapa

F22 Pedir a eliminação da conta e dos dados
    S24 -> feedback (Android) | S25 Privacidade (iPhone)
    toques: iPhone cerca de 7 (Mais, Definições, Privacidade, Delete Account, confirmar, e-mail, confirmar);
    Android: formulário de feedback com o tipo Personal Data/GDPR e resposta humana
    casos-limite: não cancela a subscrição; os favoritos perdem-se

F23 Primeira utilização
    S01 -> S39 -> S02
    toques: por confirmar (permissão de localização, escolha de modos)
    casos-limite: recusar localização; recusar notificações
```

## 6. Componentes repetidos

| componente | variantes | estados | usado em |
| --- | --- | --- | --- |
| Barra de pesquisa | início, mapa, linhas | vazia, com texto, com sugestões; sem resultados (por confirmar) | S02, S03, S12, S16 |
| Barra de separadores inferior | Direções, Paragens, Linhas | ativo, inativo | S02, S12, S16 |
| Cartão de percurso | com ou sem tempo real; com alerta; com etiquetas | normal, selecionado | S05 |
| Etiqueta (chip) | Trip Insights (roxa), alerta, modo | informativa | S05 |
| Hora de chegada | verde, amarelo, vermelho, vermelho com exclamação, preto; hora exata acima de 1 h | tocar abre o detalhe da precisão | S05, S08, S13, S17 |
| Ícone de alerta de serviço | vermelho, amarelo, azul (verde numa versão antiga) | com ou sem notificação ligada | S05, S16, S17, S20 |
| Linha temporal de etapas | a pé, transporte, bicicleta, trotinete, táxi | uma cor por etapa | S08 |
| Barra de ações rápidas | Live Location, sino, Partilhar, Report, favorito | ativo, inativo, só Moovit+ | S08, S13, S17 |
| Cartão de instruções deslizável | passo atual, anterior, seguinte | | S09, S10 |
| Folha inferior de cartões | NEARBY, FAVORITES | recolhida, expandida | S12 |
| Mapa | paragens, serviços partilhados, ponto azul, ícone de veículo | com ou sem localização | S08, S09, S12, S19 |
| Smart Card | casa, trabalho, recentes, paragem favorita, estação mais próxima, último percurso, avisos de chegada | por prioridade | S02 |
| Linha de favoritos | Casa, Trabalho, outros; menu de três pontos | vazio, preenchido | S02, S22 |
| Interruptor e sino | notificar alertas; notificações da viagem | ligado, desligado | S10, S20, S26, S27 |
| Seletores de data/hora e de área | | | S07, S28 |
| Indicador de lotação | veículo (3 níveis), paragem (4 níveis) | sem dados | S05, S08, S13 |
| Foto da paragem | | sem foto: convite a tirar | S08, S13, S14 |
| Ecrã de planos | mensal, anual | | S29 |
| Anúncio | banner 320x50, 300x250, intersticial, no mapa | | S02, S05, S08, S10 |
| Notificação do sistema | aviso de saída, alerta de linha, chegada | | S11 |
| Diálogo de permissão | localização, notificações, câmara, contactos, calendário | concedida, recusada | S39 |
| Ícones de modo | autocarro, comboio, metro, elétrico, ferry, funicular, teleférico, bicicleta, trotinete, táxi | | vários |

## 7. Modelo de dados inferido

Tudo abaixo é dedução a partir dos ecrãs e dos artigos de ajuda (a Moovit não publica o seu modelo). A fase de arquitetura deve transformá-lo em esquema real.

```
Operador         id, nome, modos, avisos/feeds
                 evidência: S16 (linhas agrupadas por operadora), S21; confiança: alta
Linha            id, operador, nome ou número, modo, cor, acessível
                 evidência: S16, S17; confiança: alta
Padrão/Sentido   linha, sequência de paragens, forma no mapa
                 evidência: S17 (Change direction), S19; confiança: alta
Paragem/Estação  id visível, nome, coordenadas, foto, acessível, grupo (várias plataformas)
                 evidência: S13, S14, R63 (estações-hub); confiança: alta
Horário          viagens, hora por paragem, calendário e feriados
                 evidência: S18, R63; confiança: alta
Posição veículo  viagem ou linha, lat/lon, instante do último sinal
                 evidência: S19, R30; confiança: alta
Previsão chegada paragem, linha, hora prevista, fonte (GPS | histórico | horário),
                 qualidade (verde | amarelo | vermelho | preto), desvio
                 evidência: R23; confiança: alta
Alerta serviço   operador, linhas afetadas, gravidade (3 níveis), texto, período, fonte
                 evidência: S20, S21; confiança: alta
Local            morada ou ponto de interesse, coordenadas
                 evidência: S03; confiança: alta
Favorito         tipo (local | linha + paragem | paragem), etiqueta (Casa | Trabalho | nome), ordem
                 evidência: S22, S12; confiança: alta
Viagem recente   origem, destino, instante (janela de 30 dias para sugestões)
                 evidência: S02, R43; confiança: média
Percurso         origem, destino, hora, etapas, resumo (duração, chegada, custo, CO2e,
                 tempo a pé, transbordos), etiquetas
                 evidência: S05, S08; confiança: alta
Etapa            modo (a pé | transporte | bicicleta | trotinete | táxi), linha, paragens,
                 horas, tempo real, custo
                 evidência: S08; confiança: alta
Preferências     modos preferidos e menos preferidos, menos caminhada, notificações,
                 objetos do mapa, idioma, tema
                 evidência: S06, S25 a S27; confiança: média
Veículo partilhado  tipo, posição, bateria ou combustível, custo, fornecedor, ligação à app
                 evidência: S34; confiança: média
Relato lotação   alvo (linha | paragem), nível, instante, validade ~10 min, confirmações
                 evidência: S32, R55; confiança: média
Foto de paragem  paragem, ficheiro, autor, moderação
                 evidência: S14, R59; confiança: palpite
Contacto de segurança  nome, telefone, estado (pendente | aprovado | recusado)
                 evidência: S30, R50; confiança: alta (Moovit+)
Partilha de viagem  percurso, localização, ETA, ligação pública, expiração
                 evidência: R50; confiança: média (Moovit+)
Subscrição       loja, plano, estado (gerida pela loja)
                 evidência: S29, R47; confiança: alta (a Paragem Certa não a terá)
Conta            telefone ou e-mail, favoritos
                 evidência: R44, R61; confiança: palpite (opcional)
```

Relações: Operador 1-n Linha; Linha 1-n Padrão; Padrão n-n Paragem (com ordem); Linha 1-n Viagem; Viagem 1-n Hora na paragem; Paragem 1-n Previsão; Alerta n-n Linha; Percurso 1-n Etapa; Etapa n-1 Linha (quando é transporte); Favorito n-1 (Local ou Linha ou Paragem); Relato n-1 (Linha ou Paragem).

## 8. Cobertura da Moovit em Portugal

Regiões e operadores listados no site da Moovit (2026-10-26; R69 a R75):

| região (índice Moovit) | tipos | operadores listados | tempo real |
| --- | --- | --- | --- |
| Lisboa (R70) | 7: autocarro, comboio, metro, elétrico, ferry, funicular, teleférico | Autocarro: Carris, Carris Metropolitana, Cascais Próxima, Transportes Colectivos do Barreiro, Barraqueiro Oeste, Boa Viagem, Scotturb, Ribatejana, Rodoviária do Oeste (Rápidas), Rodoviária do Tejo (e Rápidas), SCALABUS, CP (autocarro), Aeroporto de Lisboa, Yellow Bus Tour. Comboio: CP, Fertagus. Metro: Metropolitano de Lisboa, MTS. Elétrico: Carris, Eléctrico de Sintra, Yellow Bus Tour. Ferry: Transtejo Soflusa, Atlantic Ferries, Yellow Bus Tour. Funicular: Carris, Serviços Municipalizados da Nazaré. Teleférico: Telecabine Lisboa | Confirmado: Carris Metropolitana e Metropolitano de Lisboa (R13, R14, R68, R78). Carris (cidade): por confirmar |
| Porto, Braga e Vila Real (R71) | 6: autocarro, comboio, metro, ferry, funicular, teleférico | Autocarro: STCP, Metro do Porto (Metrobus e autocarro), UNIR Mobilidade da Área Metropolitana do Porto, Getbus (Aerobus), Mobiave, Valpibus, TUB (Braga), TuViana, Guimabus, Ave Mobilidade, Cávado Mobilidade, Transdev, António Atalaia, RodoAmarante. Comboio: CP. Metro: Metro do Porto. Ferry: Câmara Municipal de Gondomar, Aveirobus, TMP. Funicular: Confraria do Bom Jesus do Monte, Câmara Municipal de Viana do Castelo. Teleférico: Telef | Por confirmar (o STCP terá partilhado tempo real com o Google Maps em 2026, segundo um resumo de pesquisa; sem ligação provada à Moovit) |
| Coimbra (R72) | 3: autocarro, comboio, ferry | SMTUC, Metro Mondego, Mobiviseudãolafões, Município da Figueira da Foz (autocarro e ferry), SIT Metropolitano da Região de Coimbra, CP | Por confirmar |
| Funchal / Madeira (R73) | autocarro (lista só parcialmente lida) | HF, EACL, SIGA (Transportes Públicos da RAM) | Por confirmar |
| Portimão / Algarve (R74) | autocarro, comboio, ferry | Apanha-me!, A Onda, Vai e Vem, Próximo (Transportes Urbanos de Faro), Vamus Algarve, CUBO, GIRO, Sobe e Desce (Tavira), CP, Barcos da Carreira de Olhão | Por confirmar |
| Açores (R75) | autocarro (lista só parcialmente lida) | PDL Mini BUS (linhas A a D), AzoresBus (linhas de São Miguel, por exemplo 101 a 3xx) | Por confirmar |

Outras indicações:
- Ficha da Play (R01, texto possivelmente antigo): cidades Lisboa, Porto, Coimbra, Bragança, Funchal, Leiria e Portimão; operadores Metropolitano de Lisboa, Carris, Metro do Porto, SMTUC, HF (urbano e interurbano) e STUB (Bragança); bicicletas e trotinetes eCooltra, Gira, VOI e Hive. Bragança e Leiria já não aparecem como regiões no índice atual: por confirmar se foram integradas ou retiradas.
- Carris Metropolitana (R13, setembro de 2022): parceiro oficial de mobilidade da TML; no arranque eram 260 linhas e 4 290 paragens; o serviço servia 9 municípios da margem sul e estava previsto passar a 18 em janeiro de 2023. As páginas atuais da Moovit dividem a Carris Metropolitana em várias sub-agências (uma delas mostra 344 rotas e 3 780 paragens, R76); o total atual: por confirmar.
- Metro de Lisboa (R14, 2022-07-28): chegadas em tempo real na app, em parceria com o Metropolitano de Lisboa, que também usa as ferramentas da Moovit para avisar de alterações.
- Tempo real da Carris Metropolitana alargado em janeiro de 2024: mais de 1 700 autocarros e cerca de 12 000 paragens, visível em várias apps, incluindo a Moovit (R78).
- Moovit Tickets (R77) vende bilhetes de longa distância com Porto-Lisboa como exemplo; não é tarifário urbano.
- Compras de bilhetes urbanos e validação em Portugal: nenhuma referência encontrada (por confirmar).

## 9. As 10 queixas mais comuns dos utilizadores

Amostra: cerca de 40 críticas lidas na Google Play em português (R04; muitas são do Brasil, algumas de Portugal), 4 na App Store de Portugal (R05), uma página de Trustpilot com cerca de 20 críticas em inglês (R79) e uma queixa no Portal da Queixa (R80). Não é estatística: é uma leitura qualitativa. Críticas citadas por data; autores omitidos. Contagem na Play: cerca de 28 das 40 criticam os anúncios.

| # | queixa (por palavras minhas) | evidência | link | o que a Paragem Certa faz melhor |
| --- | --- | --- | --- | --- |
| 1 | Anúncios em excesso: a versão gratuita quase não se consegue usar; há anúncios a cada poucos toques ou segundos | Play PT: críticas de 09/09/2026, 14/09/2026 e 14/09/2026 (português de Portugal); Play: relatos de um anúncio a cada 3 ou 4 toques (09/2026); App Store PT, 03/11/2023: "demasiados anúncios"; Trustpilot: anúncios que demoram minutos a saltar (2025) | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT ; https://apps.apple.com/pt/app/moovit-transportes-p%C3%BAblicos/id498477945 ; https://ca.trustpilot.com/review/moovit.com | Zero anúncios, nunca interromper uma consulta |
| 2 | Anúncios enganadores: botão de fechar falso, minúsculo ou lento; abrem outra app ou a loja sem toque; instalações automáticas | Play (BR), 07/2026 a 09/2026, cerca de 10 críticas; Trustpilot, 12/2025: anúncios que não fecham e bloqueiam a app | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT | Nenhum redirecionamento, sem bibliotecas de terceiros de publicidade |
| 3 | Com pressa, o anúncio tapa a informação (perde-se o autocarro) | Play, 02/2025 (48 pessoas acharam útil), 08/2026 e 09/2026; App Store PT, 2023: a app encrava quando é preciso um horário; Trustpilot, 11/2025 | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT | Informação crítica em primeiro lugar; 1 a 2 toques até às próximas chegadas; arranque rápido |
| 4 | Horas e previsões incorretas: o tempo real salta, o autocarro "aparece do nada", a hora não bate; a Moovit responde que os horários vêm das operadoras | Play, 08/2026 e 09/2026 (vários); Trustpilot, 02/2026 e 08/2026 | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT ; https://ca.trustpilot.com/review/moovit.com | Mostrar a origem e a idade do dado (horário ou ao vivo, hora do último sinal) com cor e texto; nunca prometer um minuto exato |
| 5 | Tempo real e veículos no mapa a desaparecer ou a cobrir só parte das linhas, mesmo para quem paga | Play, 08/2026 (plano anual sem veículos ao vivo desde 31/07), 09/2026 (paga e não vê a linha ao vivo), 10/2025 (nem todos os autocarros têm localização) | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT | Dizer por linha se há tempo real e porquê (quando não há); não esconder atrás de paywall |
| 6 | Notificações pouco fiáveis: a app deixou de avisar quando o autocarro vem; avisos de chegada só para uma linha | Play, 02/2025 (48 pessoas acharam útil); R53 (uma linha de cada vez) | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT | Avisos para várias linhas; testar com poupança de bateria no Android; mostrar o estado das notificações |
| 7 | A versão gratuita "empurra" a subscrição: funções básicas desaparecem ou ficam atrás de pagamento | Play PT, 14/09/2026 (ou pagas ou não há nada); App Store PT, 03/11/2023 (parece feita para vender o premium); Play, 08/2026 (horas em tempo real deixaram de aparecer) | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT ; https://apps.apple.com/pt/app/moovit-transportes-p%C3%BAblicos/id498477945 | Tudo gratuito, sem paywall |
| 8 | Subscrição e cobranças confusas: teste gratuito que passa a anual, cancelar difícil, subscrição que não aparece na Play, cobranças no saldo do telemóvel | Trustpilot, 06/2026 (Reino Unido), 08/2026 (Índia), 09/2025; Play, 11/08/2026; Portal da Queixa, 12/04/2026 (queixa portuguesa) | https://portaldaqueixa.com/brands/moovit ; https://ca.trustpilot.com/review/moovit.com | Sem subscrições nem cobranças; se um dia houver donativos, totalmente transparentes |
| 9 | Dados errados ou desatualizados: linhas, paragens, ligações e horários; pedidos de atualizar a área metropolitana de Lisboa | App Store PT, 28/07/2022; Play, 08/2026 e 09/2026; Trustpilot, 02/2026 | https://apps.apple.com/pt/app/moovit-transportes-p%C3%BAblicos/id498477945 ; https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT | Importar feeds oficiais; mostrar a data da última atualização; "reportar erro" em 2 toques |
| 10 | Estabilidade e interface: fecha sozinha ao pesquisar, mensagem de problemas técnicos, símbolos pequenos, pouco contraste no modo escuro, ecrã sobrecarregado | Play, 08/2026 e 09/2026 (incluindo uma crítica de Portugal sobre símbolos pequenos); Trustpilot, 12/2025 (congela) | https://play.google.com/store/apps/details?id=com.tranzmate&hl=pt_PT ; https://ca.trustpilot.com/review/moovit.com | App leve; estados de erro com "tentar de novo"; contraste AA no modo escuro; ícones legíveis |

Outras queixas menos frequentes (também úteis): falta de filtro para excluir autocarros intermunicipais por causa do custo (Play, 07/2026); a lista de percursos não deixa ver todas as linhas possíveis num troço (Play, 07/2026); a IA da subscrição inventa voltas em percursos simples (Play PT, 09/09/2026); pesquisa de moradas que aponta para sítio errado (Trustpilot, 02/2026).

## 10. O que não dá para recriar (ou não deve)

- Dados próprios da Moovit: base global de linhas e horários curada ao longo de anos; modelo de previsão de chegadas que cruza GPS de veículos, histórico e dados de milhões de utilizadores (R01, R23, R64).
- Comunidade de utilizadores: Mooviters (editores voluntários, Embaixadores, alertas "por pedido", alterações de curto prazo), relatos de lotação e fotos de paragens (R55, R59, R63). Sem comunidade, esses dados não existem na Paragem Certa.
- Parcerias comerciais: tempo real por acordo com operadoras (Carris Metropolitana, Metro de Lisboa), Uber, mais de 300 empresas de micromobilidade, bilhetes e pagamentos (Pango em Israel, Roma, Turim, São Paulo), Moovit Tickets e o seu parceiro de reservas, WeWALK; a Apple e a Uber usam as APIs da Moovit (R12).
- Imagens de rua licenciadas (Live View) e a realidade aumentada do Way Finder (só iPhone).
- Rede e segmentação publicitárias (Moovit Ads): por desenho, a Paragem Certa não tem anúncios.
- Subscrição Moovit+: por desenho, a Paragem Certa não tem subscrições.
- Negócio B2B: MaaS, apps de marca, análise de mobilidade, APIs, TimePro, transporte a pedido, créditos de carbono.
- Marca e visual: nome, mascote, cor laranja, nomes de funções como `Vá no Verde`. A Paragem Certa tem visual e nomes próprios.

## 11. Matriz de funções

Ver `features.csv` (colunas `feature,area,priority,original,clone,notes`). Total: 131 funções em 22 áreas. Prioridades: must 45, should 31, could 28, skip 27. `clone` é `no` em todas as linhas (nada construído). A coluna `original` usa `yes` (documentado numa fonte), `partial` (só em algumas cidades, plataformas ou planos) e `por confirmar`. As linhas `skip` explicam o motivo nas notas: umas não são recriáveis (parcerias comerciais, dados próprios da Moovit, comunidade de utilizadores, imagens licenciadas); outras ficam de fora por desenho da Paragem Certa (anúncios, subscrição, contas, outros idiomas).

## 12. Dimensão

Ecrãs 39 (o MVP da Paragem Certa precisa de cerca de 15), fluxos 23, entidades 21. Partes difíceis, por ordem:
1. Dados de transportes de Portugal: reunir, normalizar e manter atualizados os horários oficiais e o tempo real de várias operadoras, sem a rede de voluntários nem as parcerias da Moovit.
2. Motor de planeamento multimodal com tempo real: a pé, transbordos, preferências, data e hora, rapidez no telemóvel ou no servidor, reencaminhamento.
3. Navegação ao vivo no Android: localização contínua em segundo plano, avisos de saída fiáveis, consumo de bateria e permissões (localização "sempre", notificações).
Outras: previsões de chegada com qualidade (GPS mais histórico), veículo no mapa, partilha de viagem com contactos (precisa de servidor e SMS).
Dimensão: MVP (só "must") para Portugal, sem conta nem comunidade, é L (cerca de um trimestre), dominado pelos dados; igualar a Moovit completa é XL (reescopar).

## 13. Por confirmar (lacunas desta recolha)

- Se "chegar às" existe na app (o Centro de Ajuda só documenta a hora de partida).
- Onde se liga a opção de percursos acessíveis.
- Se existe widget de ecrã inicial, e a lista completa do menu Mais e das Definições.
- Onde se descarregam os mapas em PDF e se há modo offline real.
- Que parceiros de TVDE existem hoje em Portugal (a Uber foi integrada em 2016).
- Quais as operadoras portuguesas com tempo real além da Carris Metropolitana e do Metro de Lisboa.
- Se a Live Location é grátis ou só Moovit+; duração do teste gratuito; preço atual exato em Portugal.
- Métodos de início de sessão e se a conta é obrigatória para o uso básico.
- Se Bragança e Leiria ainda estão cobertas.
- Todos os estados vazios, de carregamento, de erro, sem rede e sem permissão de localização.
- Texto exato dos ecrãs em português de Portugal da app (o Centro de Ajuda em português usa Direções, Paragens e Linhas).

Próximo passo: `/replica-architect` (esquema de dados, fontes de dados abertas e ordem de construção).
