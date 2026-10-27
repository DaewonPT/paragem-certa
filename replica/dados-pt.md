# Paragem Certa — dados abertos e serviços gratuitos para transportes públicos em Portugal

- **Data da recolha:** 26/10/2026.
- **Método:** só páginas públicas e documentação. Fiz **um único pedido de teste por API** (lista no Anexo A). Não descarreguei nenhum GTFS: os tamanhos e as datas vêm das páginas do Mobility Database, do Transitland e do transport.data.gouv.fr.
- **Confiança:** **Alta** = confirmado na fonte oficial e/ou num pedido de teste. **Média** = fonte secundária (agregador) ou fontes oficiais que não batem certo. **Baixa** = indício indireto, desatualizado ou ausência de resultados. **"por confirmar"** = não consegui confirmar.
- **Licenças:** indico-as tal como as fontes as declaram. Isto não é um parecer jurídico.

---

## 0. Resumo executivo

1. **Horários (GTFS):** há GTFS público para quase toda a Área Metropolitana de Lisboa, para o Porto cidade (STCP e Metro do Porto), para a CP a nível nacional, para Braga, Coimbra, o Algarve e o Funchal, e para a FlixBus. **Não encontrei** GTFS público da **Unir** (AMP intermunicipal), da **Rede Expressos** nem dos **Açores**. Fontes: [NAP](https://nap-portugal.imt-ip.pt/nap/multimodalsupply), [Transitland](https://www.transit.land/places/Portugal), [pt.json do Transitous](https://github.com/public-transport/transitous/blob/main/feeds/pt.json).
2. **Tempo real aberto e sem chave**, confirmado com teste:
   - **Carris Metropolitana**: chegadas, veículos e alertas ([API](https://github.com/carrismetropolitana/api)).
   - **Carris**: GTFS-RT de posições ([NAP](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/10218)).
   - **STCP**: posições em FIWARE, CC0 ([Porto](https://dadosabertos.cm-porto.pt/dataset/urban-platform-bus-location)).

   O **Metro de Lisboa** exige registo e token ([API Store](https://api.metrolisboa.pt/store/)). A **CP** não tem API oficial.
3. **Planeamento:** o [Transitous](https://transitous.org/api/) cobre 23 fontes portuguesas mais a FlixBus. A API é gratuita, mas só para projetos **open-source e não comerciais**, com User-Agent e atribuição. Uso intenso, a partir de 720 pedidos `/plan` em 24 h, tem de ser combinado com a equipa antes.
4. **Mapas:** MapLibre Native Android (BSD-2) com [OpenFreeMap](https://openfreemap.org/), que é grátis, sem chave e permite uso comercial. O plano B é um ficheiro PMTiles próprio no Cloudflare R2, que cabe no escalão gratuito.
5. **Pesquisa:** o geocoder do Transitous autocompleta e devolve paragens. O [Photon](https://github.com/komoot/photon) serve de alternativa. O [Nominatim proíbe autocompletar](https://operations.osmfoundation.org/policies/nominatim/).
6. **Partilha (GBFS):** há feeds públicos da Bird (5 cidades), da Bora (Viseu Dão Lafões) e da TubaBike (Barcelos). A **GIRA não tem GBFS público**. A Bolt fornece dados só **a pedido e possivelmente com custos**.

---

## 1. Horários (GTFS) por operador

### 1.1 Catálogos e agregadores

| Catálogo | O que tem | URL | Notas | Confiança |
|---|---|---|---|---|
| **NAP Portugal (IMT)** | 84 fichas no módulo multimodal, que incluem também dados de carregamento elétrico, estacionamento e trânsito. Tem GTFS de cerca de 20 operadores ou redes. | https://nap-portugal.imt-ip.pt/nap/multimodalsupply | É uma página em JavaScript e não exige registo para consultar. Cada ficha declara a licença numa de seis categorias ("Sem licença – Sem contrato", "Licença e grátis", etc.), quase sempre sem o texto da licença. O enquadramento é o Regulamento Delegado (UE) 2017/1926, alterado pelo 2024/490 ([página inicial](https://nap-portugal.imt-ip.pt/nap/home)). | Alta |
| **dados.gov.pt** | A pesquisa "gtfs" devolve 10 conjuntos: Metro de Lisboa, Carris Metropolitana, TUB, SMTUC, Águeda, etc. | https://dados.gov.pt/pt/datasets/?q=gtfs | As licenças variam: o Metro de Lisboa tem CC BY 4.0, mas TUB, SMTUC e Carris Metropolitana aparecem com "Licença não especificada". | Alta |
| **Mobility Database** | Catálogo aberto com mais de 6000 feeds e cópias espelhadas dos GTFS. Mostra o tamanho e o intervalo de serviço de cada feed. | https://mobilitydatabase.org/about | É útil como espelho estável quando o URL do operador muda ou falha. | Alta |
| **Transitland** | 32 operadores em 12 regiões portuguesas, com metadados de licença por feed. | https://www.transit.land/places/Portugal | Alguns operadores aparecem sem feed visível (Rodoviária do Oeste, TUVR). | Alta |
| **Transitous** | 23 entradas portuguesas em `pt.json`, mais a FlixBus e a BlaBlaCar em `eu.json`. | https://github.com/public-transport/transitous/blob/main/feeds/pt.json · https://raw.githubusercontent.com/public-transport/transitous/main/feeds/eu.json | Detalhe na secção 3. | Alta |

### 1.2 Tabela por operador

Abreviaturas: **MDB** = Mobility Database. **NAP n.º** = ficha `https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/<n.º>`.

#### Área Metropolitana de Lisboa

| Operador | Dados | URL | Licença | Tempo real? | Notas | Confiança |
|---|---|---|---|---|---|---|
| **Carris Metropolitana** (TML) | GTFS de autocarros, 943 linhas | https://api.carrismetropolitana.pt/gtfs | **CC BY 4.0** segundo o [repositório oficial](https://github.com/carrismetropolitana/gtfs). O NAP n.º 159 declara "Sem licença – Sem contrato". | **Sim**, API aberta (§2) | Cobre 15 dos 18 municípios da AML ([API](https://github.com/carrismetropolitana/api)). O NAP declara atualização "Até semanal". O [MDB](https://mobilitydatabase.org/feeds/gtfs/mdb-2027) indica **74,62 MB comprimido / 930,24 MB descomprimido**, serviço de 01/01/2026 a 30/06/2027 e recolha a 25/10/2026. | Alta |
| **Carris** (Lisboa) | GTFS | https://gateway.carris.pt/gateway/gtfs/api/v2.11/GTFS ([MDB](https://mobilitydatabase.org/feeds/gtfs/mdb-2929)). O NAP n.º 10217 ainda indica `.../v2.8/GTFS`. | NAP: "Licença e grátis", sem texto da licença. O [Transitous](https://github.com/public-transport/transitous/blob/main/feeds/pt.json) indica CC0-1.0. | **Sim**, GTFS-RT de posições (§2) | O NAP declara "Até semanal". O MDB indica 45,71 MB comprimido / 151,69 MB descomprimido e serviço de 06/06/2026 a 31/12/2026. O Transitous aplica correções (gtfsclean) ao importar ([commits](https://github.com/public-transport/transitous/commits/main/feeds/pt.json)). | Alta (URL) / Média (licença) |
| **Metropolitano de Lisboa** | GTFS | https://www.metrolisboa.pt/google_transit/googleTransit.zip | [dados.gov.pt](https://dados.gov.pt/pt/datasets/gtfs-do-metropolitano-de-lisboa): **CC BY 4.0**. NAP n.º 186: "Sem licença – Sem contrato". Transitous: CC0-1.0. Recomendo tratar como CC BY 4.0. | Parcial: API com registo (§2) | O NAP declara "Até a cada 6 meses". O [Transitland](https://www.transit.land/feeds/f-eyckr-metrodelisboa) indica serviço até 31/12/2026. | Alta |
| **CP – Comboios de Portugal** (nacional) | GTFS ferroviário, 185 linhas | https://publico.cp.pt/gtfs/gtfs.zip | NAP n.º 176: "Sem licença – Sem contrato" | Não oficial (§2) | O NAP declara "Até semanal" e cobertura de todo o continente (lista de NUTS III). O [MDB](https://mobilitydatabase.org/feeds/gtfs/mdb-2057) indica 0,3 MB comprimido e serviço de 14/12/2025 a 12/12/2026. | Alta (URL) / Média (licença) |
| **Fertagus** | GTFS | https://www.fertagus.pt/GTFSTMLzip/Fertagus_GTFS.zip | NAP n.º 185: "Licença e grátis" | Não encontrado | O NAP declara "Até anualmente". O [Transitland](https://www.transit.land/feeds/f-eyce-fertagus) indica a versão 26.10.20, com serviço de 14/09/2026 a 14/03/2027. | Alta |
| **MTS – Metro Transportes do Sul** | GTFS, 3 linhas | https://mts.pt/imt/MTS-20240129.zip | NAP n.º 200: "Licença e grátis" | Não encontrado | O NAP declara "Menos frequente do que anual". O nome do ficheiro aponta para janeiro de 2024, mas o [MDB](https://mobilitydatabase.org/feeds/gtfs/mdb-2408) indica serviço até 2032. **Atualidade por confirmar.** | Média |
| **Transtejo/Soflusa** (TTSL, barcos) | GTFS | https://api.transtejo.pt/files/GTFS.zip | **por confirmar**: não encontrei licença. O MDB liga para uma página da Carris Metropolitana. | Não encontrado | O [MDB](https://mobilitydatabase.org/feeds/gtfs/mdb-2921) indica serviço de 06/05/2020 a 31/12/2028 e recolha a 17/08/2026. Não aparece no NAP. O Transitous adicionou-o em setembro de 2026. | Média |
| **MobiCascais** (Cascais Próxima) | GTFS | https://drive.google.com/uc?export=download&id=13ucYiAJRtu-gXsLa02qKJrGOgDjbnUWX | NAP n.º 10221: "Licença e grátis" | Não encontrado | Está alojado no Google Drive, o que o torna frágil. O [MDB](https://mobilitydatabase.org/feeds/gtfs/mdb-1272) indica serviço de 01/01/2025 a 31/12/2027. | Média |
| **TCB – Barreiro** | GTFS | https://backend.tcbarreiro.pt/download-gtfs | NAP n.º 187: "Licença e grátis" | Não encontrado | Publicado no NAP a 18/03/2026 ([lista](https://nap-portugal.imt-ip.pt/nap/multimodalsupply)). | Alta |

#### Área Metropolitana do Porto

| Operador | Dados | URL | Licença | Tempo real? | Notas | Confiança |
|---|---|---|---|---|---|---|
| **STCP** | GTFS | Conjunto: https://dadosabertos.cm-porto.pt/dataset/horarios-paragens-e-rotas-stcp. Versão mais recente ("GTFS STCP 01-09-2026"): https://dadosabertos.cm-porto.pt/dataset/71490e40-9e19-11f1-84ed-6abdb6d5cf34/resource/51340c18-0ef5-4895-b099-cf7247ea54f4/download/gtfs_feed.zip | **CC0** | **Sim**, posições em FIWARE (§2) | Atualização "Conforme necessário". **Cada versão é um recurso novo e não há URL fixo para a mais recente**, por isso é preciso usar a API CKAN do portal. O antigo `opendata.porto.digital` redireciona (301) para `dadosabertos.cm-porto.pt`. | Alta |
| **Metro do Porto** | GTFS | https://www.metrodoporto.pt/metrodoporto/uploads/document/file/794/google_transit.zip ([página](https://www.metrodoporto.pt/pages/337)) | NAP n.º 189: "Licença e grátis". Transitous: CC0-1.0. | Não encontrado | O NAP declara "Até a cada 6 meses". A página publica horários válidos a partir de 7/9/2026. | Alta |
| **Unir** (TMP, rede intermunicipal) | — | **não encontrado** | — | Só no Google Maps, desde junho de 2026 ([ECO](https://eco.sapo.pt/2026/06/11/transportes-da-regiao-do-porto-disponiveis-em-tempo-real-no-google-maps/)) | Não há GTFS público no NAP, no MDB nem no Transitland. | Baixa (ausência) |

#### Norte

| Operador | Dados | URL | Licença | Tempo real? | Notas | Confiança |
|---|---|---|---|---|---|---|
| **TUB – Braga** | GTFS | https://www.tub.pt/developer/gtfs/feed/tub.zip | [dados.gov.pt](https://dados.gov.pt/pt/datasets/gtfs-transportes-urbanos-de-braga): "Licença não especificada". NAP n.º 10204 (Pentágono Urbano): "Licença e grátis". | Não encontrado | O [Transitland](https://www.transit.land/feeds/f-transportes~urbanos~de~braga) recolheu-o a 21/10/2026, com serviço até 01/02/2027. | Média |
| **Guimarães (Guimabus), Barcelos, V. N. Famalicão, CIM Cávado, CIM Ave** (Associação de Municípios Pentágono Urbano) | GTFS (NAP n.º 10203, 10201, 10202, 10227, 10226) | Ligações para `https://map.mobility.ubiwhere.com/dataset/...`, por exemplo https://map.mobility.ubiwhere.com/dataset/operador-de-sptp-de-guimaraes | Só verifiquei Braga ("Licença e grátis"). As restantes estão **por confirmar**. | Não encontrado | Publicados no NAP a 05/03/2026 e a 27/05/2026. Não estão no Transitous. | Média |
| **CIM Tâmega e Sousa** (Lousada) | GTFS | https://transportes.cimtamegaesousa.pt/Servico/services/consultas.svc/Feed/OpenGTFS | NAP n.º 195: "Licença e grátis" | Não encontrado | Publicado no NAP a 21/04/2026. Não está no Transitous. | Alta |
| **AUTNA** (Galiza–Norte) | GTFS | https://drive.google.com/uc?export=download&id=1gah1x10RyFu7gJPweBcCXPd9vcFJFQ7c | **CC BY-ND 4.0**; o Transitland marca "derivados: Não" | — | Serviço de 28/05/2026 a 31/12/2026 ([Transitland](https://www.transit.land/feeds/f-ez-autnatransportes)). | Média |
| **TUVR – Vila Real** | O operador está no Transitland, mas sem feed visível | **por confirmar** | — | — | https://www.transit.land/explore/operators/o-ez66z-tuvr~urbanosdevilareal | Baixa |

#### Centro

| Operador | Dados | URL | Licença | Tempo real? | Notas | Confiança |
|---|---|---|---|---|---|---|
| **SMTUC** (Coimbra) | GTFS, 230 linhas | O URL muda a cada versão. Atual, segundo o [MDB mdb-3535](https://mobilitydatabase.org/feeds/gtfs/mdb-3535): https://dados.gov.pt/api/1/datasets/r/24045cfe-7132-430e-922f-3d3a7f0c921f | [dados.gov.pt](https://dados.gov.pt/pt/datasets/gtfs-estaticos-servicos-municipalizados-de-transportes-urbanos-de-coimbra): "Licença não especificada". NAP n.º 10215: "Licença e grátis". | Só na app oficial; não encontrei API ([aviso SMTUC](https://www.smtuc.pt/wp-content/uploads/2026/02/AVISO_programacao_2025.2026_2.pdf)) | O NAP declara "Até a cada 3 meses". Serviço de 18/09 a 12/11/2026. **Não está no Transitous.** | Média |
| **Busway Coimbra** (SIT Região de Coimbra) | GTFS | https://gtfs.itbusway.eu/GTFS_SIT_Metropolitano.zip | NAP n.º 10224: "Licença e grátis" | — | Publicado no NAP a 10/08/2026. Não está no Transitous. | Alta |
| **Busway CIRA** (Região de Aveiro) | GTFS | https://gtfs.itbusway.eu/GTFS_CIRA.zip | NAP n.º 10225: licença **por confirmar** | — | Publicado no NAP a 10/08/2026. | Média |
| **Águeda** (Auto Viação Aveirense) | GTFS | dados.gov.pt, sem recursos listados | CC BY 4.0 | — | A última atualização é de 13/11/2020, por isso deve estar desatualizado ([dados.gov.pt](https://dados.gov.pt/pt/datasets/rede-de-transportes-publicos-municipal)). | Baixa |
| **Rodoviária do Oeste** | O operador está no Transitland (Leiria, Santarém, Lisboa), mas sem feed visível | **por confirmar** | — | — | https://www.transit.land/explore/operators/o-e-rdoeste | Baixa |

#### Algarve

| Operador | Dados | URL | Licença | Tempo real? | Notas | Confiança |
|---|---|---|---|---|---|---|
| **Vamus** (interurbano) | GTFS | https://drive.google.com/uc?export=download&id=1CM8O4ndsfSJhka42SxFUZ9eB-wE10NqX | **CC BY-ND 4.0**; o Transitland marca "derivados: Não" | — | Serviço de 20/10/2026 a 31/12/2027 ([Transitland](https://www.transit.land/feeds/f-ey-vizur)). | Média |
| **Urbanos:** Próximo (Faro), A Onda (Lagos), Vai e Vem (Portimão), Giro (Albufeira), Sobe e Desce (Tavira), Circuito Olhão | GTFS no Google Drive | Próximo: https://drive.google.com/uc?export=download&id=1xNHjM7yl-SS1jCkGrvIBBlOhUkFfNBjY · A Onda: https://drive.google.com/uc?export=download&id=1aPfsxHqopxxcjV8HlRzImzxh_a6zRxGp | **CC BY-ND 4.0** no Próximo e na A Onda. As restantes estão **por confirmar**. | — | Fontes: [Próximo](https://www.transit.land/feeds/f-eyd-pxm), [A Onda](https://www.transit.land/feeds/f-ey9-aonda). Estão todas no Transitous. | Média |
| **Apanha-me** (Loulé) | GTFS | https://drive.google.com/uc?export=download&id=1w92h129CWNSoImBRZQOWT6KRPzSFwJ42 | NAP n.º 10216: "Licença e grátis" | — | Publicado no NAP a 27/01/2026. | Média |

#### Ilhas e longo curso

| Operador | Dados | URL | Licença | Tempo real? | Notas | Confiança |
|---|---|---|---|---|---|---|
| **Horários do Funchal** (Madeira) | GTFS urbano e interurbano | https://www.horariosdofunchal.pt/googletransit.zip | **CC BY 4.0** ([Transitland](https://www.transit.land/feeds/f-etgc-hf~urbano~hf~interurbano)) | — | Serviço de 09/09 a 30/11/2026, com versões curtas que exigem refrescamento frequente. Não encontrei os outros operadores da Madeira. | Média |
| **Açores** | — | **não encontrado** | — | — | Nada no NAP, no Transitland nem no MDB, e a pesquisa web também não deu resultados ([lista do Transitland](https://www.transit.land/places/Portugal)). | Baixa (ausência) |
| **FlixBus/FlixTrain** (rede europeia) | GTFS e GTFS-RT TripUpdates | https://gtfs.gis.flix.tech/gtfs_generic_eu.zip | **ODbL 1.0** mais "Conditions particulières" ([transport.data.gouv.fr](https://transport.data.gouv.fr/datasets/flixbus-horaires-theoriques-du-reseau-europeen-1)) | **Sim** (TripUpdates) | 30,15 MB, válido de 25/10/2026 a 25/04/2027 ([recurso](https://transport.data.gouv.fr/resources/11681?locale=en)). Está no Transitous (`eu.json`). Se inclui todas as paragens portuguesas está **por confirmar**. | Alta (feed) / Média (cobertura PT) |
| **BlaBlaCar Bus** | GTFS e SIRI-Lite | https://transport.data.gouv.fr/datasets/blablacar-bus-horaires-theoriques-et-temps-reel-du-reseau-europeen | ODbL | Sim (SIRI ET) | Está no Transitous (`eu.json`). Cobertura de Portugal **por confirmar**. | Baixa |
| **Rede Expressos** | — | **não encontrado** | — | — | Não há GTFS público em nenhum catálogo. | Baixa (ausência) |

### 1.3 Frequência de atualização declarada no NAP

O NAP declara um teto de frequência. Não é a frequência real: para essa, veja as datas no MDB e no Transitland acima.

| Ficha NAP | Operador | Licença declarada | Frequência declarada |
|---|---|---|---|
| [176](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/176) | CP | Sem licença – Sem contrato | Até semanal |
| [10217](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/10217) | Carris (GTFS) | Licença e grátis | Até semanal |
| [159](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/159) | Carris Metropolitana (GTFS) | Sem licença – Sem contrato | Até semanal |
| [186](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/186) | Metro de Lisboa | Sem licença – Sem contrato | Até a cada 6 meses |
| [189](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/189) | Metro do Porto | Licença e grátis | Até a cada 6 meses |
| [10215](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/10215) | SMTUC | Licença e grátis | Até a cada 3 meses |
| [185](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/185) | Fertagus | Licença e grátis | Até anualmente |
| [200](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/200) | MTS | Licença e grátis | Menos frequente do que anual |

---

## 2. Tempo real

| Operador | O que há | Endpoint(s) | Chave ou registo? | Termos e licença | Teste (26/10/2026) | Confiança |
|---|---|---|---|---|---|---|
| **Carris Metropolitana** | Chegadas por paragem (estimadas, programadas e observadas), veículos e alertas | Base `https://api.carrismetropolitana.pt/v2/`, com os caminhos `GET /arrivals/by_stop/:id`, `GET /arrivals/by_pattern/:id`, `GET /vehicles` e `/vehicles.pb` (GTFS-RT VehiclePositions), `GET /alerts` e `/alerts.pb` (GTFS-RT ServiceAlerts) e `POST /pips/estimates` ([README](https://github.com/carrismetropolitana/api)) | Não documentada: o README não menciona chaves | O site descreve-a como "API REST de livre acesso" ([dados abertos](https://carrismetropolitana.pt/open-data)). Não há limites nem termos publicados. O README não refere TripUpdates em GTFS-RT. Estado do serviço: https://status.carrismetropolitana.pt/status/cm-status | `GET /v2/alerts` devolveu **HTTP 200** em JSON (cerca de 84 KB), com `active_period`, `cause`, `effect`, `header_text`, `description_text`, `informed_entity` e `image` | Alta |
| **Carris** | Posições de veículos em GTFS-RT | https://gateway.carris.pt/gateway/gtfs/api/v2.8/GTFS/realtime/vehiclepositions | Não exigiu chave no teste | NAP n.º [10218](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/10218): "Licença e grátis", publicada a 12/01/2026, contacto nap.dados@carris.pt | **HTTP 200**, `application/octet-stream`, cerca de 40 KB | Alta (acesso) / Média (estabilidade): em 2022 a Carris **fechou a API pública** e passou a exigir credenciais ([Lisboa Para Pessoas](https://lisboaparapessoas.pt/en/?p=21696)). TripUpdates e alertas **por confirmar**. |
| **Metro de Lisboa** | Tempos de espera, estado das linhas, estações e intervalos | Base `https://api.metrolisboa.pt:8243/estadoServicoML/1.0.1`, com `/tempoEspera/Estacao/todos`, `/tempoEspera/Estacao/{estacao}`, `/tempoEspera/Linha/{linha}`, `/estadoLinha/todos`, `/estadoLinha/{linha}`, `/infoEstacao/todos`, `/infoDestinos/todos` e `/infoIntervalos/{linha}/{dia}` ([API Console](https://api.metrolisboa.pt/store/apis/info?name=EstadoServicoML&version=1.0.1&provider=admin)) | **Sim.** É preciso criar conta na API Store, subscrever a API e usar um token Bearer. | Não encontrei termos de uso. Os comentários públicos de 2023 e 2024 pedem a reposição dos tempos de espera e queixam-se de baixa disponibilidade, por isso o **funcionamento atual está por confirmar** ([mesma página](https://api.metrolisboa.pt/store/apis/info?name=EstadoServicoML&version=1.0.1&provider=admin)). | Não testado (exige chave) | Média |
| **CP** | Não há API oficial. O site e a app têm "Próximos comboios" desde outubro de 2024 ([ECO](https://eco.sapo.pt/descodificador/ja-pode-ver-se-o-comboio-esta-atrasado-sem-ir-a-estacao-veja-como/)). | Feed comunitário de GTFS-RT TripUpdates usado pelo Transitous: https://rt.gtfs.baguette.pirnet.si/gtfs-rt/cp/trip_updates.pb ([pt.json](https://github.com/public-transport/transitous/blob/main/feeds/pt.json); adicionado no commit "Add realtime feeds for Spain, Portugal and Romania", de setembro de 2025, [histórico](https://github.com/public-transport/transitous/commits/main/feeds/pt.json)) | — | **Origem e termos desconhecidos.** As bibliotecas não oficiais que usam endpoints da CP avisam para pedir autorização à CP ([build-cp-gtfs](https://npmjs.com/package/build-cp-gtfs)). | Não testado (não é uma API do operador) | Baixa |
| **STCP** (Porto Digital) | Posições de autocarros em FIWARE NGSI v2, modelo "Vehicle" | https://broker.fiware.urbanplatform.portodigital.pt/v2/entities?q=vehicleType==bus&limit=1000 | Não exigiu chave no teste | **CC0** ([conjunto](https://dadosabertos.cm-porto.pt/dataset/urban-platform-bus-location)) | Com `limit=1` devolveu **HTTP 200** em JSON: `id`, linha (`stcp:route:107`), `location`, `bearing`, `speed` e `observationDateTime` | Alta. As **previsões de chegada** só estão no Google Maps desde junho de 2026 ([Observador](https://observador.pt/2026/06/09/stcp-disponibiliza-horarios-dos-autocarros-em-tempo-real-no-google-maps-a-partir-desta-terca-feira/)) e não encontrei feed público. |
| **Metro do Porto** | Não encontrado | — | — | — | — | Baixa (ausência) |
| **Unir** (TMP) | Só no Google Maps | — | — | — | — | Média ([ECO](https://eco.sapo.pt/2026/06/11/transportes-da-regiao-do-porto-disponiveis-em-tempo-real-no-google-maps/)) |
| **FlixBus** | GTFS-RT TripUpdates | https://global.api.flixbus.com/gis/v1/gtfs-rt/flavour/generic/region/eu/agency/bus_train/trip-update | Não | ODbL ([transport.data.gouv.fr](https://transport.data.gouv.fr/datasets/flixbus-horaires-theoriques-du-reseau-europeen-1)) | Não testado (já vem pelo Transitous) | Alta |
| Fertagus, MTS, Transtejo, TUB, SMTUC, Algarve, Funchal | Não encontrado (o SMTUC só tem tempo real na app) | — | — | — | — | Baixa (ausência) |

---

## 3. Planeamento de viagens

### 3.1 Transitous: cobertura portuguesa

O Transitous é um serviço comunitário de cálculo de rotas, assente no motor **MOTIS** (licença MIT) ([transitous.org](https://transitous.org/), [MOTIS](https://github.com/motis-project/motis)).

**Fontes portuguesas** (`pt.json` a 26/10/2026, [ficheiro](https://raw.githubusercontent.com/public-transport/transitous/main/feeds/pt.json)), com a licença declarada no próprio ficheiro:

| Fonte | Licença declarada |
|---|---|
| Carris Metropolitana | CC-BY-4.0, recolha diária |
| Carris | CC0-1.0 |
| Metro de Lisboa | CC0-1.0 |
| Cascais Próxima | não declarada |
| CP, com tempo real comunitário | não declarada |
| Fertagus (escrito "Fergatus" no ficheiro) | não declarada |
| Barreiro | não declarada |
| Funchal | não declarada |
| Vamus | não declarada |
| TUB | não declarada |
| AUTNA | não declarada |
| STCP | CC0-1.0 |
| Tavira, Portimão, Faro, Albufeira, Olhão, Apanha-me, Lagos | não declarada |
| Metro do Porto | CC0-1.0 |
| Transtejo-Soflusa | não declarada |
| MTS | não declarada |

O ficheiro `eu.json` acrescenta a **FlixBus** (GTFS e GTFS-RT) e a **BlaBlaCar Bus** (GTFS e SIRI) ([eu.json](https://raw.githubusercontent.com/public-transport/transitous/main/feeds/eu.json)).

**Ficam de fora do Transitous**, segundo o `pt.json`: SMTUC, Busway SIT/CIRA, as redes do Pentágono Urbano, Lousada e Unir. A Rede Expressos e os Açores não têm dados.

**Tempo real de Portugal no Transitous:**
- Só há **TripUpdates comunitários da CP** e os **TripUpdates da FlixBus**.
- O Transitous consulta os feeds de tempo real uma vez por minuto e trata **TripUpdates e alertas, mas ainda não VehiclePositions** ([doc](https://transitous.org/doc/)).
- Por isso as posições da Carris, da Carris Metropolitana e da STCP não entram, e a app tem de as ir buscar diretamente.

**Teste:** `GET /api/v1/geocode?text=Cais do Sodré` devolveu **HTTP 200**. Os resultados incluíram paragens com os IDs `pt-Comboios-de-Portugal(CP)_94_69005`, `pt-Transtejo-Soflusa_5` e `pt-Carris-Metropolitana_360451`, e um local do OpenStreetMap.

### 3.2 Endpoints da API

A especificação OpenAPI do MOTIS está ligada a partir de https://transitous.org/api/ e a especificação em si está em https://raw.githubusercontent.com/motis-project/motis/v2.10.2/openapi.yaml. **Os prefixos de versão (v1, v5, v6) mudam entre versões do MOTIS**, por isso confirme-os na instância de produção.

| Endpoint | Para que serve |
|---|---|
| `GET /api/v6/plan` | Itinerários. Parâmetros principais: `fromPlace`/`toPlace` (coordenadas ou ID de paragem), `time`, `arriveBy`, `transitModes`, `maxTransfers`, `numItineraries` e `realtimeMode`. |
| `GET /api/v6/stoptimes` | Próximas partidas ou chegadas numa paragem. Aceita `stopId` ou `center` com `radius`, e ainda `n`, `window` e `withAlerts`. Devolve os campos `realTime` e `cancelled`. |
| `GET /api/v6/trip` | Uma viagem completa a partir do seu ID. |
| `GET /api/v1/geocode` | **Autocompletar** e geocodificar texto (`text`, `language`, `type`, `place`/`placeBias`, `numResults`). |
| `GET /api/v1/reverse-geocode` | Moradas, locais ou paragens mais próximos de umas coordenadas. |
| `GET /api/v6/map/stops`, `GET /api/v6/map/trips`, `GET /api/v1/map/initial`, `GET /api/v1/map/levels` | Dados para o mapa: paragens e viagens por zona e janela temporal. |
| `GET /api/v6/stop` | Linhas que servem uma paragem. |
| `GET /api/v1/rentals` | Mobilidade partilhada (GBFS). Não há GBFS português configurado. |
| `GET /api/v1/one-to-many`, `GET /api/v6/one-to-all` | Matrizes e isócronas. São pesados: ver a política na secção 3.3. |
| `GET/POST /api/v6/refresh-itinerary`, `/api/experimental/...` | Experimentais. |
| `GET /api/v1/health` | Mostra que feeds já completaram pelo menos um ciclo de atualização. |

### 3.3 Política de uso

Fonte: https://transitous.org/api/

- **Requisitos:**
  - O projeto tem de ser **open-source**. São permitidos testes leves de protótipo antes de publicar o código.
  - O projeto tem de ser **não comercial**. Segundo a política, isto deve-se às licenças das fontes e à prioridade dada aos recursos.
- **Identificação:** cada pedido tem de levar um **User-Agent** com o nome da app, a versão e um contacto (email ou site).
- **Atribuição:** é preciso pôr uma **ligação visível para https://transitous.org/sources/**, cumprir as condições de cada fonte e seguir as regras de atribuição do OpenStreetMap.
- **Carga:** a política considera carga elevada mais de **720 pedidos em 24 h** de `/plan`, `/one-to-many` ou `/one-to-all`, assim como muitos utilizadores ou pedidos automáticos. Nesses casos pede que se contacte a equipa no Matrix **antes**.
- **Disponibilidade:** o serviço é mantido por voluntários e funciona "best-effort". A equipa reserva-se o direito de cortar o acesso a qualquer consumidor a qualquer momento.
- **Recolha de dados:** não se deve fazer scraping da API. O conjunto de dados completo pode ser descarregado, e os feeds processados estão em https://api.transitous.org/gtfs/ ([doc](https://transitous.org/doc/)).
- **Montra:** a equipa diz que não adiciona apps "vibecoded" à montra. Isto só afeta a montra; as regras de uso são as que estão acima.

**Conclusão:** uma app gratuita, sem anúncios, com código publicado sob uma licença open-source cumpre os critérios de base. Mas uma app pública ultrapassa depressa os 720 pedidos `/plan` por dia, por isso é **obrigatório falar com a equipa antes de publicar na Google Play**. A resposta deles está **por confirmar**.

### 3.4 Alternativas e custos

| Opção | Custo | Viabilidade para Portugal | Notas | Confiança |
|---|---|---|---|---|
| **MOTIS próprio no Oracle Cloud Always Free** | 0 € | Provável. O MOTIS afirma ter "low memory usage" ([README](https://github.com/motis-project/motis)). **A memória real para Portugal está por confirmar.** | O Always Free inclui Ampere A1 com o **equivalente a 2 OCPU e 12 GB de RAM**, 200 GB de disco e 10 TB de tráfego de saída por mês. A Oracle pode recuperar instâncias ociosas, ou seja, as que durante 7 dias ficam abaixo de 20 % de CPU (95.º percentil), de rede e, no A1, de memória ([Oracle](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)). Como a API é igual à do Transitous, **na app só muda o URL base**. É preciso automatizar a atualização dos GTFS. | Média |
| **OpenTripPlanner 2 no mesmo servidor** | 0 € | Apertado. A documentação do OTP diz que a Finlândia precisa de pouco mais de 10 GB e a Alemanha de 95 GB ([OTP](https://docs.opentripplanner.org/en/latest/System-Requirements/)), por isso Portugal deve andar perto do limite de 12 GB (**estimativa por confirmar**). | Precisa de mais memória do que o MOTIS e tem uma API diferente. | Média |
| **VPS pago** | Desde 6,49 €/mês sem IVA (Hetzner, "Regular Performance") ([Hetzner](https://www.hetzner.com/cloud/)) | Sim | É a saída se o Oracle recuperar a instância ou se a memória não chegar. | Média |
| **Rotas no próprio telemóvel (RAPTOR)** | 0 €: os dados são preparados no GitHub Actions, que é **grátis em repositórios públicos** ([GitHub](https://docs.github.com/en/billing/concepts/product-billing/github-actions)), e distribuídos pelo R2 (10 GB grátis) | **Só é viável com pré-processamento.** O GTFS bruto do país é enorme: só a Carris Metropolitana tem 74,62 MB comprimido e **930 MB descomprimido**, e a Carris 45,71 MB e 151,69 MB ([MDB CM](https://mobilitydatabase.org/feeds/gtfs/mdb-2027), [MDB Carris](https://mobilitydatabase.org/feeds/gtfs/mdb-2929)). Seria preciso converter os GTFS num formato binário compacto por região (dezenas de MB, **por confirmar**) e calcular os transbordos a pé à parte. | O algoritmo RAPTOR está descrito em [Delling et al., ALENEX 2012](https://www.microsoft.com/en-us/research/publication/round-based-public-transit-routing/). Funciona sem servidor e offline, mas obriga a fazer muito código à mão: tempo real, ruas, pesquisa de moradas. | Média (estimativa) |

---

## 4. Mapas

| Fonte | Custo e limites | Pode ser usada numa app pública? | Atribuição obrigatória | Offline | Fontes | Confiança |
|---|---|---|---|---|---|---|
| **OpenFreeMap** | Grátis, sem chave, sem registo e **sem limite de visualizações** | **Sim, incluindo uso comercial.** Não tem SLA e pode ser **descontinuado sem aviso**. É financiado por doações. | "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" (o MapLibre mostra-a automaticamente) | Cada descarga pode ter até 3000 mosaicos ou 50 MB, com no máximo 4 pedidos em paralelo e um User-Agent próprio | https://openfreemap.org/ · https://openfreemap.org/tos/ | Alta |
| **Protomaps (PMTiles) no Cloudflare R2** | O R2 inclui grátis **10 GB-mês**, 1 milhão de operações de classe A e **10 milhões de classe B** (leituras) por mês, e **a saída de dados é grátis** ([preços](https://developers.cloudflare.com/r2/pricing/)). O endereço `r2.dev` tem limite de pedidos e serve só para desenvolvimento, por isso é preciso um **domínio próprio na mesma conta Cloudflare** ([buckets públicos](https://developers.cloudflare.com/r2/buckets/public-buckets/)). Um subdomínio de pixellusitano.com, que já está no Cloudflare, serve. O Worker é opcional: o plano grátis dá 100 000 pedidos por dia e o pago custa no mínimo 5 USD por mês ([Workers](https://developers.cloudflare.com/workers/platform/pricing/), [guia Protomaps](https://docs.protomaps.com/deploy/cloudflare)). | **Sim** | Os dados são ODbL: é obrigatório o "© OpenStreetMap" | O ficheiro `.pmtiles` pode ir no próprio telemóvel. O planeta ocupa cerca de 120 GB (z0–z15) e a região extrai-se com `pmtiles extract`. A documentação pede que não se use as builds diretamente: é preciso copiá-las para o próprio armazenamento ([downloads](https://docs.protomaps.com/basemaps/downloads)). O tamanho de Portugal está **por confirmar**. | https://docs.protomaps.com/basemaps/downloads | Alta |
| **MapTiler Cloud Free** | 0 €, com 5 mil sessões de mapa e 100 mil pedidos por mês. **Se exceder, o serviço pára até ao mês seguinte.** | Só para testes, protótipos e uso pessoal ou não comercial. **É arriscado para uma app pública.** | Logótipo MapTiler | — | https://www.maptiler.com/cloud/pricing/ | Alta |
| **tile.openstreetmap.org** | Grátis e sem SLA | Permitido com um User-Agent próprio, mas **podem bloquear sem aviso**. Só tem mosaicos raster. | "© OpenStreetMap" visível no mapa | **Proibido**, tal como a pré-descarga | https://operations.osmfoundation.org/policies/tiles/ | Alta |

**SDK:** **MapLibre Native Android**, com licença **BSD-2-Clause**. A dependência é `org.maplibre.gl:android-sdk` (o README mostra a versão 11.11.0) ([repositório](https://github.com/maplibre/maplibre-native)). Suporta **PMTiles desde a versão 11.7.0**, através de `pmtiles://https://...`, `pmtiles://file://...` e `pmtiles://asset://...`. **As fontes PMTiles não suportam "offline packs" nem cache** ([exemplo oficial](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/)).

---

## 5. Pesquisa de moradas e locais

| Serviço | Política | Autocompletar? | Notas | Fonte | Confiança |
|---|---|---|---|---|---|
| **Transitous** `/api/v1/geocode` | A mesma do Transitous (secção 3.3) | **Sim**: a especificação diz "Autocompletes and geocodes user-entered address text" | Devolve paragens dos GTFS e moradas e locais do OSM. O teste correu bem. | https://transitous.org/api/ · https://raw.githubusercontent.com/motis-project/motis/v2.10.2/openapi.yaml | Alta |
| **Photon** (servidor público photon.komoot.io) | Pode ser usado com um "número razoável" de pedidos. O uso intenso é **limitado ou banido** e não há garantia de disponibilidade. O software tem licença Apache-2.0. | Por confirmar se foi pensado para isso; o README fala de pesquisa multilingue | Aceita os parâmetros `countrycode=PT`, `bbox` e `lat`/`lon` ([API](https://github.com/komoot/photon/blob/master/docs/api-v1.md)). **No teste sem filtro, o 1.º resultado para "Rua Augusta Lisboa" foi no Brasil.** Alojar uma instância própria do planeta exige cerca de 95 GB de disco e 64 GB de RAM. | https://github.com/komoot/photon | Alta |
| **Nominatim** (OSMF) | **No máximo 1 pedido por segundo para toda a app.** Exige User-Agent e cache, e a app tem de conseguir desligar o serviço sem atualizar. | **Proibido**: "you must not implement such a service on the client side" | Serve só para uma pesquisa pontual ou uma geocodificação inversa ocasional. | https://operations.osmfoundation.org/policies/nominatim/ | Alta |
| **Geoapify Free** | 3000 créditos por dia e 5 pedidos por segundo. **Permite uso comercial** com atribuição. | Por confirmar | Exige uma chave, que fica embutida no APK e pode ser extraída. | https://www.geoapify.com/pricing/ | Média |
| **LocationIQ Free** | 5000 pedidos por dia, 2 por segundo. Permite uso comercial limitado, com uma ligação "Search by LocationIQ.com". | Provável (por confirmar) | Exige chave. | https://locationiq.com/pricing | Média |
| **Pesquisa offline** | A Base Nacional de Moradas (INE, INSPIRE) e a Toponímia (DGT) estão listadas no NAP. **Licença e utilidade por confirmar.** | — | O INE tem um serviço de descarga em https://inspire.ine.pt/AD/atom/downloadservice.xml | https://nap-portugal.imt-ip.pt/nap/multimodalsupply | Baixa |

---

## 6. Bicicletas e trotinetes partilhadas (GBFS)

| Sistema | Dados | URL | Licença | Teste | Fonte | Confiança |
|---|---|---|---|---|---|---|
| **Bird** (Lisboa, Porto, Braga, Cascais, Matosinhos) | GBFS 2.3 público | https://mds.bird.co/gbfs/v2/public/lisbon/gbfs.json (os outros usam `/porto/`, `/braga/`, `/cascais/` e `/matosinhos/`) | **por confirmar** (ver `system_information.json`) | Lisboa devolveu **HTTP 200**, com `free_bike_status`, `station_*`, `geofencing_zones` e `vehicle_types`, ttl de 60 s | [MobilityData systems.csv](https://github.com/MobilityData/gbfs/blob/master/systems.csv) | Alta (feed) / Média (se a operação continua ativa) |
| **Bora** (CIM Viseu Dão Lafões) | GBFS 3.0 | https://gbfs.primelayer.pt/gbfs-smartmobility/gbfs/v3/gbfs.json | por confirmar | **HTTP 200**, ttl de 300 s | idem | Alta |
| **TubaBike** (Barcelos, nextbike) | GBFS 2.x em português e inglês | https://gbfs.nextbike.net/maps/gbfs/v2/nextbike_bx/gbfs.json | por confirmar | **HTTP 200**, ttl de 60 s | idem | Alta |
| **GIRA** (Lisboa, EMEL) | Só a lista estática de estações, em XML | https://dados.emel.pt/dataset/cfb68ba1-d2c0-4923-a0cd-00504510acdb/resource/ee9d35d0-7990-4bd6-8811-5ea54c63d6a0/download/girastations.xml | CC BY 4.0 ([dados.gov.pt](https://dados.gov.pt/pt/datasets/estacoes-gira/)) | — | **Não encontrei GBFS nem tempo real público.** O portal da EMEL menciona "APIs abertas" atrás de uma sessão ([sobre](https://dados.emel.pt/about)). Por confirmar. | Média |
| **Bolt** (micromobilidade) | GBFS "sob pedido", com "eventual aplicação de custos" | NAP n.º [10219](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/10219) | — | — | NAP | Alta |
| **Lime e outros** | Não constam do catálogo da MobilityData | — | — | — | [systems.csv](https://github.com/MobilityData/gbfs/blob/master/systems.csv) | Média (ausência) |

O Transitous não tem GBFS português configurado ([pt.json](https://github.com/public-transport/transitous/blob/main/feeds/pt.json)).

---

## 7. Alertas de serviço e perturbações

| Operador | Fonte de alertas | Formato | Fonte | Confiança |
|---|---|---|---|---|
| Carris Metropolitana | `GET /v2/alerts` e `/v2/alerts.pb` | JSON e GTFS-RT ServiceAlerts, com causa, efeito, período, linhas afetadas e imagem | [README](https://github.com/carrismetropolitana/api) e teste | Alta |
| Metro de Lisboa | `/estadoLinha/todos` e `/estadoLinha/{linha}` | REST, com token | [API Store](https://api.metrolisboa.pt/store/apis/info?name=EstadoServicoML&version=1.0.1&provider=admin) | Média |
| Carris | Canal de alertas no WhatsApp, desde abril de 2026. Não encontrei feed estruturado. | Texto | [Sábado](https://www.sabado.pt/portugal/detalhe/carris-lanca-canal-de-whatsapp-para-divulgar-informacoes-e-alertas) | Média |
| CP | Alertas de atraso na app e no site desde outubro de 2024: mais de 5 minutos no longo e médio curso, mais de 3 minutos nos urbanos. Não há API. | App e site | [Time Out](https://www.timeout.pt/lisboa/pt/noticias/comboio-atrasado-cp-lanca-ferramenta-para-acompanhar-informacao-em-tempo-real-102924) | Média |
| FlixBus | GTFS-RT TripUpdates, com atrasos e cancelamentos | GTFS-RT | [transport.data.gouv.fr](https://transport.data.gouv.fr/datasets/flixbus-horaires-theoriques-du-reseau-europeen-1) | Alta |
| Porto (trânsito) | Condicionamentos de trânsito, programados e não programados, publicados pela Câmara do Porto. Ajudam a perceber desvios. | DATEX II, GeoJSON e CSV | [NAP](https://nap-portugal.imt-ip.pt/nap/multimodalsupply) | Média |
| Transitous | Passa os alertas dos feeds que os publicam (`alerts` em `Leg` e `Place`; parâmetro `withAlerts` em `stoptimes`). Hoje não tem alertas portugueses configurados. | API MOTIS | [openapi](https://raw.githubusercontent.com/motis-project/motis/master/openapi.yaml) · [pt.json](https://github.com/public-transport/transitous/blob/main/feeds/pt.json) | Média |
| STCP, Metro do Porto, Unir, Fertagus, MTS, Transtejo, TUB, SMTUC, Algarve, Funchal | Não encontrei feed estruturado. A app teria de ligar para o site ou as redes sociais do operador (**por confirmar**). | — | — | Baixa |

---

## 8. Recomendação

### 8.1 Combinação a custo zero que cobre mais do país

1. **Planeamento, horários e pesquisa: Transitous.**
   - Publicar o código da Paragem Certa num repositório público com licença open-source (por exemplo GPL-3.0 ou MIT).
   - Enviar `User-Agent: ParagemCerta/<versão> (<URL do projeto>)`.
   - Ter um ecrã "Fontes de dados" com a ligação para transitous.org/sources e todas as atribuições ([política](https://transitous.org/api/)).
2. **Tempo real direto, sem servidor próprio:**
   - Chegadas, veículos e alertas da Carris Metropolitana ([API](https://github.com/carrismetropolitana/api)).
   - Posições da Carris em GTFS-RT ([NAP](https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/10218)).
   - Posições da STCP em FIWARE ([CC0](https://dadosabertos.cm-porto.pt/dataset/urban-platform-bus-location)).
   - A FlixBus e a CP comunitária já vêm pelo Transitous.
   - A API do Metro de Lisboa fica **só para a fase pessoal**, porque exige um token ([API Store](https://api.metrolisboa.pt/store/)).
3. **Mapas:**
   - Já: MapLibre Native Android com estilos do OpenFreeMap ([OpenFreeMap](https://openfreemap.org/)).
   - Plano B e escala: um PMTiles de Portugal no Cloudflare R2, servido por um subdomínio de pixellusitano.com, a 0 € dentro do escalão gratuito ([R2](https://developers.cloudflare.com/r2/pricing/)). O mesmo ficheiro pode ser descarregado para o telemóvel e usado offline com `pmtiles://file://` ([MapLibre](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/)).
4. **Pesquisa:**
   - O geocoder do Transitous como fonte principal.
   - O Photon com `countrycode=PT` como alternativa ([API Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md)).
   - **Nunca o Nominatim para autocompletar** ([política](https://operations.osmfoundation.org/policies/nominatim/)).
5. **Partilha:** GBFS da Bird, da Bora e da TubaBike lidos diretamente; são JSON pequenos, com ttl de 60 a 300 s ([systems.csv](https://github.com/MobilityData/gbfs/blob/master/systems.csv)).
6. **Alertas:**
   - Os da Carris Metropolitana e o estado das linhas do Metro de Lisboa.
   - Para os outros operadores, ligações para os respetivos sites.
7. **Alargar a cobertura sem custos:** propor ao Transitous os feeds do NAP que lhe faltam: SMTUC, Busway SIT/CIRA, Lousada e as redes do Pentágono Urbano ([NAP](https://nap-portugal.imt-ip.pt/nap/multimodalsupply), [como adicionar](https://transitous.org/doc/)).
8. **Plano B, se o Transitous recusar o volume da app pública:**
   - Um MOTIS próprio no Oracle Always Free. Tem a mesma API, por isso **só muda o URL base** ([Oracle](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm), [MOTIS](https://github.com/motis-project/motis)).
   - Em alternativa ou em complemento, cálculo RAPTOR no telemóvel com pacotes por região, gerados no GitHub Actions e servidos pelo R2.

### 8.2 O que fica sem cobertura

**Horários:**
- Unir, a rede intermunicipal da AMP. Só existe no Google Maps ([ECO](https://eco.sapo.pt/2026/06/11/transportes-da-regiao-do-porto-disponiveis-em-tempo-real-no-google-maps/)).
- Rede Expressos.
- Açores.
- Madeira fora do Funchal.
- A maioria das redes intermunicipais do interior (Alentejo, Beiras, Trás-os-Montes, Médio Tejo, Oeste e Leiria). A Rodoviária do Oeste aparece no Transitland mas sem feed ([Transitland](https://www.transit.land/explore/operators/o-e-rdoeste)). Isto está **por confirmar caso a caso**.
- O SMTUC, a Busway e as redes do Pentágono Urbano existem no NAP mas **ainda não estão no Transitous**.

**Tempo real:**
- Não há tempo real para Metro do Porto, Unir, Fertagus, MTS, Transtejo, TUB, SMTUC e o Algarve.
- A CP só tem um feed comunitário.
- Os tempos de espera do Metro de Lisboa são incertos.
- As previsões de chegada da STCP só estão no Google Maps.

**Partilha:** GIRA, Lime e Bolt.

### 8.3 Riscos

- **Dependência de serviços comunitários:**
  - O **Transitous** é mantido por voluntários e funciona "best-effort". Pode cortar o acesso, exige que a app seja open-source e não comercial, e tem o limiar de 720 pedidos `/plan` em 24 h ([política](https://transitous.org/api/)).
  - O **OpenFreeMap** não tem SLA e pode ser descontinuado sem aviso ([ToS](https://openfreemap.org/tos/)).
  - O **Photon** pode limitar ou banir o uso intenso ([README](https://github.com/komoot/photon)).
- **Licenças:**
  - **CC BY-ND 4.0** (Vamus, urbanos do Algarve, AUTNA): a cláusula "sem derivados" choca com reprocessar ou combinar os dados, por isso convém pedir autorização ([Transitland](https://www.transit.land/feeds/f-ey-vizur)).
  - **ODbL** (OSM e FlixBus): obriga a atribuir e, se a app distribuir uma base de dados derivada (por exemplo pacotes offline), a partilhá-la nos mesmos termos ([FlixBus](https://transport.data.gouv.fr/datasets/flixbus-horaires-theoriques-du-reseau-europeen-1), [Protomaps](https://docs.protomaps.com/basemaps/downloads)).
  - **Sem licença explícita ou inconsistente** (CP e Metro de Lisboa no NAP, API da Carris Metropolitana, Transtejo, TUB e SMTUC no dados.gov.pt): convém pedir confirmação por email. Os contactos estão nas fichas do NAP, por exemplo institucional@cp.pt e nap.dados@carris.pt.
- **APIs que podem fechar ou mudar:**
  - A Carris já fechou a API pública em 2022 ([Lisboa Para Pessoas](https://lisboaparapessoas.pt/en/?p=21696)).
  - A API do Metro de Lisboa tem queixas de baixa disponibilidade.
  - **Um token ou chave embutido num APK público pode ser extraído.** Esconder um token exigiria um proxy (por exemplo um Cloudflare Worker grátis, com 100 000 pedidos por dia, [Workers](https://developers.cloudflare.com/workers/platform/pricing/)), que já é um servidor, ainda que gratuito.
- **Tempo real da CP não oficial:** o feed comunitário tem origem e termos desconhecidos ([pt.json](https://github.com/public-transport/transitous/blob/main/feeds/pt.json)).
- **Atualidade e fragilidade dos dados:**
  - O ficheiro da MTS é de 2024 ([MDB](https://mobilitydatabase.org/feeds/gtfs/mdb-2408)) e o de Águeda de 2020.
  - Os feeds alojados no Google Drive (Algarve, Cascais) podem falhar.
  - Na STCP e no SMTUC o URL muda a cada versão, por isso é melhor usar as APIs dos portais ou os espelhos do Mobility Database ([MDB](https://mobilitydatabase.org/about)).
  - O Funchal tem versões de curta validade ([Transitland](https://www.transit.land/feeds/f-etgc-hf~urbano~hf~interurbano)).
- **"Todo o Portugal" é inatingível só com dados abertos:** sem Unir, Rede Expressos e Açores, a app terá buracos visíveis. O NAP existe para estes dados serem publicados ([NAP](https://nap-portugal.imt-ip.pt/nap/home)), por isso pode valer a pena pedir aos operadores e autoridades que os publiquem lá.

### 8.4 Próximos passos

1. Criar o repositório público com licença open-source antes de usar o Transitous a sério.
2. Escrever à equipa do Transitous (Matrix) com uma estimativa de utilizadores e de pedidos `/plan` por dia.
3. Pedir clarificação de licença à CP, ao Metro de Lisboa, à TML (Carris Metropolitana), à Transtejo e aos publicadores do Algarve, por causa da cláusula ND.
4. Na fase pessoal, criar uma conta na API Store do Metro de Lisboa para testar os tempos de espera.

---

## Anexo A — Pedidos de teste feitos

Fiz um pedido por API, a 26/10/2026, sem ciclos.

| API | Pedido | Resultado |
|---|---|---|
| Transitous | `GET https://api.transitous.org/api/v1/geocode?text=Cais%20do%20Sodr%C3%A9&language=pt&numResults=5` | 200, JSON de 3,1 KB, com paragens da CP, da Transtejo-Soflusa e da Carris Metropolitana e 1 local do OSM |
| Carris Metropolitana | `GET https://api.carrismetropolitana.pt/v2/alerts` | 200, JSON de cerca de 84 KB (alertas no formato GTFS-RT, em JSON) |
| Carris | `GET https://gateway.carris.pt/gateway/gtfs/api/v2.8/GTFS/realtime/vehiclepositions` | 200, `application/octet-stream`, cerca de 40 KB, sem chave |
| Porto Digital (STCP) | `GET https://broker.fiware.urbanplatform.portodigital.pt/v2/entities?q=vehicleType==bus&limit=1` | 200, JSON NGSI v2 com uma entidade "Vehicle" |
| Photon | `GET https://photon.komoot.io/api/?q=Rua%20Augusta%20Lisboa&limit=2` | 200. O 1.º resultado foi no Brasil e o 2.º em Lisboa, por isso é preciso `countrycode=PT` |
| Bird (Lisboa) | `GET https://mds.bird.co/gbfs/v2/public/lisbon/gbfs.json` | 200, GBFS 2.3 |
| Bora (Viseu) | `GET https://gbfs.primelayer.pt/gbfs-smartmobility/gbfs/v3/gbfs.json` | 200, GBFS 3.0 |
| nextbike (Barcelos) | `GET https://gbfs.nextbike.net/maps/gbfs/v2/nextbike_bx/gbfs.json` | 200, GBFS 2.x |

**Não testei:**
- A API do Metro de Lisboa, porque exige chave.
- O Nominatim, o OpenFreeMap, o MapTiler e o tempo real da FlixBus. Para estes bastou a documentação.
- O feed comunitário da CP, porque não é uma API do operador.

## Anexo B — Fichas do NAP relevantes (IDs)

| Ficha | Conteúdo |
|---|---|
| 159 | GTFS da Carris Metropolitana |
| 175 | API da Carris Metropolitana (estimativas de chegada) |
| 176 | GTFS da CP |
| 185 | GTFS da Fertagus |
| 186 | GTFS do Metro de Lisboa |
| 187 | GTFS dos TCB (Barreiro) |
| 189 | GTFS do Metro do Porto |
| 195 | GTFS de Lousada (Tâmega e Sousa) |
| 200 | GTFS da MTS |
| 10201 | Barcelos |
| 10202 | V. N. Famalicão |
| 10203 | Guimarães |
| 10204 | Braga |
| 10215 | GTFS do SMTUC |
| 10216 | GTFS do Apanha-me (Loulé) |
| 10217 | GTFS da Carris |
| 10218 | GTFS-RT de posições da Carris |
| 10219 | GBFS da Bolt (a pedido) |
| 10221 | GTFS da MobiCascais |
| 10224 | Busway Coimbra (SIT) |
| 10225 | Busway CIRA |
| 10226 | CIM Ave |
| 10227 | CIM Cávado |

Todas no formato `https://nap-portugal.imt-ip.pt/nap/multimodalsupplydetail/<ID>`.
