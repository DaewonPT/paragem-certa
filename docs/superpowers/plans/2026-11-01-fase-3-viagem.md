# Fase 3 (parte 1) — versão 0.3.0 "Viagem ao vivo" — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** "Começar viagem" na própria app: a viagem segue etapa a etapa (a pé, à espera, a bordo, transbordos) e avisa "Prepara-te" e "Sai agora" com o ecrã desligado, num serviço próprio que sobrevive a falhas do resto da app.

**Architecture:** um motor puro no `:core` (`core/viagem/`) recebe posições GPS e o relógio e devolve o estado e os avisos; é testado no PC com viagens sintéticas e com as gravações reais do autor (fora do repositório). No `:app`, um serviço em primeiro plano do tipo localização, no processo `:trip`, alimenta o motor, toca o alarme e escreve `trip-state.json`; a app lê esse ficheiro e mostra o ecrã da viagem. Sem base de dados, definições nem tarefas no processo `:trip`.

**Tech Stack:** Kotlin 2.4, kotlinx.serialization, Compose M3, `LocationManagerCompat`, `NotificationCompat`, `AtomicFile`, Robolectric 4.17 (`@Config(sdk = [36])`), Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-27-paragem-certa-design.md` — secções 3 (Fase 3), 5.2 (Viagem ao vivo), 7 (inteira), 9, 11. Desenho e pesquisa que fundamentam este plano (veredicto do advisor, relatórios das APIs e do Android): `../pesquisa-fase-3.md` (fora do repositório).

## Decisões do autor (01/11/2026)

- **"Prepara-te"** dispara quando o veículo sai da **antepenúltima** paragem (duas antes do destino) ou a 2 min do destino, o que vier primeiro (`PREPARA_ANTES = 2`). Resolve a contradição entre a secção 3 ("chega antes da penúltima") e a 7.3 ("sai da penúltima").
- **Aceites na 0.3.0:** no Metro subterrâneo o aviso é estimado pelo horário e pode chegar uma estação mais cedo se a pessoa apanhar um comboio mais tarde (o texto do aviso diz o nome da estação); o telemóvel fica acordado (wake lock parcial) durante a viagem.
- **Fica para a 0.3.1:** "Avisa-me quando estiver a X min" (7.5), confirmação pela posição do autocarro (`vehicles`, 7.3), bússola "Encontrar a paragem". **Fica para a 0.4:** vista ao vivo com fotos de rua (sem cobertura do Panoramax em Lisboa; termos do Mapillary por esclarecer).
- **Gravações reais de GPS só no PC**, em `Paragem Certa/gravacoes/` (fora do repositório). No GitHub só entram viagens sintéticas geradas pelo código.
- **O `:trip` não faz pedidos ao Transitous nem a `vehicles`** na 0.3.0. Só consulta as chegadas da Carris Metropolitana na paragem de embarque enquanto se espera (para "O 736 chega em 4 min").

## Global Constraints

- PT-PT em todo o texto (tu); nomes no código em português, como o resto. Commits em inglês, sem atribuição a IA.
- minSdk 26, targetSdk 36, compileSdk 37. Sem Google Play Services (localização por `LocationManager`).
- Nunca pedir `ACCESS_BACKGROUND_LOCATION`, `USE_FULL_SCREEN_INTENT` nem `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (spec 7.2; políticas da Play).
- O serviço só arranca com a app visível, depois de tocar em "Começar viagem" (Android 14+). Sem reinício automático (`START_NOT_STICKY`).
- Pedidos com o User-Agent da app; pausas crescentes depois de falhas (`pausaDepoisDeFalha`).
- Textos fixos da spec: "Prepara-te", "Sai agora", "Silenciar", "Já saí", "Parar", "Posição estimada pelo horário", "Viagem interrompida — toca para retomar", "Faltam 3 paragens · sais em Oriente", "O 736 chega em 4 min".
- Capturas de referência só no CI (ubuntu-24.04); localmente gravam-se só para ver, sem commit.

## Review Focus

1. **O Android fecha o serviço a meio** (fabricante, falta de memória): ao voltar à app aparece "Viagem interrompida — toca para retomar"; ao retomar, nenhum aviso já dado toca outra vez (Task 3 teste 13, Task 7).
2. **A localização é desligada ou a permissão retirada a meio:** a viagem passa para "posição estimada pelo horário", aparece "Liga a localização" e os avisos continuam pelo horário (Task 3 teste 5, Task 5).
3. **A pessoa perde o autocarro e apanha o seguinte:** com GPS, fica "à espera" até o veículo arrancar com ela; nunca avisa "Sai agora" antes de sair da penúltima paragem (Task 3 testes 3 e 11).
4. **Permissão de notificações recusada:** os avisos tocam e vibram na mesma, sem erro (Task 5).
5. **Telemóvel em silêncio, volume do alarme a 0:** o som usa o canal de alarme; a primeira viagem avisa se o volume do alarme estiver a 0 (Task 7).

---

### Task 1: Modelo da viagem, parser e ids da Carris Metropolitana (core)

**Files:** Modify `core/.../core/modelo/Rotas.kt`, `core/.../core/fontes/transitous/Transitous.kt`, `core/.../core/fontes/cm/CmIds.kt`; Test `core/.../core/rotas/RotasTest.kt`, `core/.../core/fontes/cm/CmIdsTest.kt` (novo)

**Interfaces — Produces:**
- `object InstanteSerializer : KSerializer<Instant>` (texto ISO-8601), no ficheiro do modelo.
- `@Serializable` em `Lugar`, `Etapa` e `Itinerario` (os `Instant` com `InstanteSerializer`).
- `@Serializable data class ParagemEtapa(val id: String?, val nome: String, val lat: Double, val lon: Double, val hora: Instant)` — `hora = departure ?: arrival`.
- `Etapa` ganha `val idViagem: String? = null` (do `leg.tripId`) e `val paragens: List<ParagemEtapa> = emptyList()` = `from` + `intermediateStops` + `to` (só nas etapas em veículo). `paragensIntermedias` fica.
- `fun cmParagemDeTransitous(id: String): String?` — tira `pt-Carris-Metropolitana_`; se não tiver esse prefixo, `null`; o primeiro dígito `3` passa a `0` (320047 → 020047; 360155 → 060155; 160099 fica).
- `fun cmViagemDeTransitous(id: String): String?` — tira `^\d{8}_\d\d:\d\d_pt-Carris-Metropolitana_`; sem esse prefixo, `null`.
- O comentário "os ids não coincidem" em `Transitous.kt` passa a dizer a regra acima.

- [ ] **Step 1: testes** — `paragensDaEtapa`: na fixture `transitous-plan.json`, a etapa do 210 tem `idViagem` não nulo e `paragens.size == paragensIntermedias + 2`, a primeira com o `stopId` e a hora de partida da etapa; `itinerarioIdaEVolta`: `Json.decodeFromString<Itinerario>(Json.encodeToString(it)) == it`; `CmIdsTest` com os três ids acima, um id da Carris (cidade) → `null`, e `cmViagemDeTransitous("20261101_10:35_pt-Carris-Metropolitana_3708_0_1|1|0525") == "3708_0_1|1|0525"`.
- [ ] **Step 2:** `gradle :core:test --tests '*RotasTest*' --tests '*CmIdsTest*'` → FAIL.
- [ ] **Step 3:** `PontoRotaJson` passa a ler `stopId`, `arrival`, `departure`; `EtapaJson` lê `tripId`.
- [ ] **Step 4:** `gradle :core:test` → PASS. **Step 5:** commit `feat(core): trip stops, trip id and CM id conversion for live trips`.

### Task 2: Percurso — projetar a posição na linha (core)

**Files:** Create `core/.../core/viagem/Percurso.kt`; Test `core/.../core/viagem/PercursoTest.kt`

**Interfaces — Produces:**
```kotlin
class Percurso(desenho: List<Pair<Double, Double>>, paragens: List<ParagemEtapa>) {
    val noPercurso: DoubleArray        // metros ao longo da linha de cada paragem; cada uma projetada a partir da anterior (voltas seguras)
    fun projetar(lat: Double, lon: Double, de: Double, ate: Double): Projecao?   // só segmentos dentro de [de, ate]
    fun horaEm(m: Double): Instant     // linear entre as horas das paragens
    fun progressoEm(h: Instant): Double
}
data class Projecao(val ao: Double, val afastamento: Double)
```
Projeção plana local (x = Δlon·cos(lat0)·R). Sem desenho, usa as coordenadas das paragens.

- [ ] **Step 1: testes** — linha reta de 3 paragens: `noPercurso` crescente com as distâncias certas (±1 m); um ponto a 20 m ao lado projeta com `afastamento ≈ 20`; percurso em laço que passa a 80 m do destino antes de lá chegar: com a janela `[0, 500]` a projeção não salta para o fim; `horaEm`/`progressoEm` inversos dentro da etapa.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** PASS. **Step 5:** commit `feat(core): project positions onto a trip leg`.

### Task 3: O motor da viagem (core)

**Files:** Create `core/.../core/viagem/Viagem.kt`; Test `core/.../core/viagem/ViagemTest.kt`, `core/src/test/kotlin/.../core/viagem/Tracos.kt` (gerador de viagens sintéticas e leitor de gravações)

**Interfaces — Produces:**
```kotlin
sealed interface Entrada { val hora: Instant }
  // Gps(lat, lon, precisaoM: Float, hora) · Relogio(hora) · Partida(etapa: Int, prevista: Instant, hora) · JaSai(hora) · Silenciar(hora)
enum class Fase { A_PE, ESPERA, A_BORDO, TERMINADA }
enum class Origem { GPS, HORARIO }
enum class Evento { PREPARA_TE, SAI_AGORA, ETAPA_SEGUINTE, CHEGOU }
@Serializable data class EstadoViagem(val etapa: Int, val fase: Fase, val progresso: Double, val atrasoS: Long,
    val velocidade: Double, val origem: Origem, val ultimoGps: Instant?, val avisados: Set<Evento>,
    val altaPrecisao: Boolean, val silenciado: Boolean, val terminada: Boolean, val atualizado: Instant)
@Serializable data class PlanoViagem(val itinerario: Itinerario, val som: Boolean, val vibrar: Boolean)
class Viagem(val plano: Itinerario) {
    fun inicio(agora: Instant): EstadoViagem
    fun passo(e: EstadoViagem, x: Entrada): Pair<EstadoViagem, List<Evento>>
    fun paragemPassada(e: EstadoViagem): Int
    fun faltam(e: EstadoViagem): Int
    fun eta(e: EstadoViagem): Instant
}
```
O estado de saída é exatamente o `trip-state.json`. `avisados` volta a vazio ao mudar de etapa. Constantes no topo do ficheiro.

**Regras** (`d` = paragem de saída, `A[k] = noPercurso[k]`, `P` = progresso):
- Aceitar uma posição só se `precisao ≤ 75 m`, mais nova que `ultimoGps`, `afastamento ≤ max(50, precisao)` e `ao ∈ [P − 50, P + 100 + VMAX·Δt]` (VMAX 25 m/s autocarro, 35 m/s comboio e metro). Então `P = max(P, ao)`; `velocidade` = média de ΔP/Δt; `atrasoS = hora − horaEm(P)`.
- "Saiu da paragem k" ⇔ `P > A[k] + 30`. `faltam = d − paragemPassada`.
- `Partida` (chegada ao vivo enquanto se espera) põe `atrasoS = prevista − hora[0]`.
- **Horário:** 60 s sem posição aceite em ESPERA ou A_BORDO → `origem = HORARIO`; em cada `Relogio`, `P = max(P, progressoEm(agora − atraso))`; `eta = hora[d] + atraso`. Uma posição aceite volta a `GPS`.
- **ESPERA → A_BORDO:** 2 posições aceites com `P > A[0] + 60` e `v > 3 m/s`; ou em HORARIO quando `agora ≥ hora[0] + atraso`.
- **PREPARA_TE:** `P > A[d − 2] + 30` (limitado a 0: numa etapa de 1 paragem dispara ao embarcar) ou `eta − agora ≤ 2 min`.
- **SAI_AGORA:** `P > A[d − 1] + 30` e `A[d] − P ≤ max(150, v × 15)`; ou em HORARIO quando `eta − agora ≤ 60 s`. Se os dois forem devidos no mesmo passo: PREPARA_TE e depois SAI_AGORA.
- **altaPrecisao:** `P > A[d − 2] + 30` ou `eta − agora ≤ 3 min` ou `d ≤ 2`; fica ligada até ao fim da etapa.
- **Fim de etapa em veículo:** `P ≥ A[d] − 30`, ou `JaSai`, ou em HORARIO `agora ≥ eta + 60 s` → SAI_AGORA se ainda não tiver tocado, depois ETAPA_SEGUINTE (CHEGOU na última; `terminada`).
- **Fim de etapa a pé:** posição a ≤ 50 m do destino da etapa; ou posição que projeta `> A[0] + 60` na etapa seguinte (já embarcou: salta a espera); ou sem GPS quando `agora ≥ etapa.chegada`.
- **Guarda:** `agora > plano.chegada + 30 min` → TERMINADA. `Silenciar` só muda `silenciado`.

**Viagens de teste:** `Tracos.sintetico(percurso, vMs = 7.0, paragemS = 20, ruidoM = 0.0, saltos = 0.0, buraco: IntRange? = null, semente = 1)` gera posições a cada 2 s; `Tracos.correr(viagem, inicio, posicoes, extras)` junta um `Relogio` de 5 s. As gravações reais (`s,lat,lon,acc` por linha, `#` comentários, com o `-plano.json` ao lado) ficam em `Paragem Certa/gravacoes/`; o teste `gravacoesReais` corre-as se a pasta existir (`Assume.assumeTrue`) e é ignorado no CI.

- [ ] **Step 1: testes** (`ViagemTest`), com o esperado tirado da própria viagem ("antes da penúltima" = primeira posição a 30 m da paragem d−1; "antes de parar" = primeira posição a 20 m de d):
  1. autocarro limpo: PREPARA_TE antes da penúltima, SAI_AGORA a ≤ 150 m e antes de parar, cada um uma vez;
  2. ruído σ 25 m e 5 % de saltos de 300 m: a contagem nunca anda para trás; SAI_AGORA nunca antes de sair da penúltima;
  3. penúltima a 100 m do destino: SAI_AGORA só depois de sair dela;
  4. posições com 150 m de precisão ignoradas;
  5. Metro sem posições: HORARIO aos 60 s, SAI_AGORA a eta − 60 s;
  6. túnel e depois GPS: volta a GPS, nada toca duas vezes;
  7. `Partida` 4 min atrasada desloca a eta e a estimativa;
  8. etapa de uma paragem;
  9. laço que passa a 80 m do destino mais cedo: não avisa nessa passagem;
  10. a pé → autocarro → a pé → metro → a pé: ETAPA_SEGUINTE em cada mudança e CHEGOU no fim;
  11. andar a pé ao longo da linha não conta como embarcar;
  12. `JaSai` a meio da etapa e na última;
  13. estado em JSON e de volta, continuar: nenhum aviso se repete;
  14. posição fora da linha ignorada;
  15. a passagem para altaPrecisao;
  16. guarda a `chegada + 30 min`;
  17. `gravacoesReais` (só no PC).
- [ ] **Step 2:** FAIL. **Step 3:** implementar `Viagem.kt` e `Tracos.kt`. **Step 4:** `gradle :core:test` → PASS. **Step 5:** commit `feat(core): live trip engine with get-off alerts`.

### Task 4: Ficheiros, notificações e alarme (app)

**Files:** Create `app/.../viagem/FicheirosViagem.kt`, `app/.../viagem/Notificacoes.kt`, `app/.../viagem/Alarme.kt`, `strings.xml`; Test `app/src/test/.../viagem/FicheirosViagemTest.kt`, `NotificacoesTest.kt` (Robolectric, `@Config(sdk = [36])`)

**Interfaces — Produces:**
- `object FicheirosViagem { fun gravarPlano(c: Context, p: PlanoViagem); fun lerPlano(c): PlanoViagem?; fun gravarEstado(c, e: EstadoViagem); fun lerEstado(c): EstadoViagem?; fun apagar(c); fun emCurso(c): Boolean; const val ACAO_ESTADO = "pt.pixellusitano.paragemcerta.VIAGEM" }` — `filesDir/viagem/trip-plan.json` e `trip-state.json` com `AtomicFile`; `emCurso` = há um processo da app cujo nome acaba em `:trip` (`getRunningAppProcesses`); um estado com mais de `chegada + 30 min` apaga-se ao ler.
- `object Notificacoes { fun criarCanais(c); fun progresso(c, plano, estado, viagem): Notification; fun aviso(c, evento, plano, estado, viagem): Notification }` — canal `viagem` (LOW, fixa, `setOnlyAlertOnce`, `FOREGROUND_SERVICE_IMMEDIATE`, botões "Silenciar", "Já saí", "Parar" como `PendingIntent.getService`) e canal `avisos` (HIGH, sem som próprio, sem vibração do canal, `CATEGORY_ALARM`).
- `object Alarme { fun tocar(c, evento: Evento, som: Boolean, vibrar: Boolean) }` — `RingtoneManager` `TYPE_ALARM` (reserva `TYPE_NOTIFICATION`) com `USAGE_ALARM`, 3 s no Prepara-te e 6 s no Sai agora; vibração com `VibrationAttributes.USAGE_ALARM` (33+) ou `AudioAttributes` de alarme (26–32).

**Textos:** progresso "Faltam N paragens · sais em X" (1: "Falta 1 paragem · sais em X"); a pé "A pé até X · N m"; à espera "O N chega em M min" (sem tempo real: "O N parte às HH:mm"); em horário, acrescenta " · posição estimada pelo horário". Avisos: "Prepara-te — sais daqui a 2 paragens, em X" e "Sai agora — X"; num transbordo acrescenta "e apanha o N"; em horário acrescenta " (posição estimada, confirma a paragem)".

- [ ] **Step 1: testes** — plano e estado gravados e lidos iguais; ficheiro velho apagado; os dois canais com a importância certa; a notificação de progresso com o texto "Faltam 3 paragens · sais em Oriente" e os 3 botões; o aviso em horário com o sufixo.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** `gradle :app:testPessoalDebugUnitTest` → PASS. **Step 5:** commit `feat(trip): trip files, notifications and alarm`.

### Task 5: O serviço da viagem no processo `:trip`

**Files:** Create `app/.../viagem/ServicoViagem.kt`; Modify `AndroidManifest.xml`, `ParagemCertaApp.kt`, `MainActivity.kt`; Test `app/src/test/.../viagem/ServicoViagemTest.kt` (Robolectric `buildService`, `@Config(sdk = [36])`)

**Comportamento:**
- Manifesto: `<service android:name=".viagem.ServicoViagem" android:process=":trip" android:exported="false" android:foregroundServiceType="location" />`; permissões `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `VIBRATE`, `WAKE_LOCK`.
- `AtualizacaoDiaria.agendar` passa de `ParagemCertaApp.onCreate` para `MainActivity.onCreate` (o `Application` corre também no `:trip`, que não pode tocar no WorkManager nem na base de dados).
- Ações: `COMECAR` e `RETOMAR` (lêem `trip-plan.json` e, no retomar, `trip-state.json`), `SILENCIAR`, `JA_SAI`, `PARAR`. `startForeground(…, FOREGROUND_SERVICE_TYPE_LOCATION)` dentro de `try/catch`: se o Android recusar, `stopSelf` e o estado fica para "Viagem interrompida". `START_NOT_STICKY`.
- Wake lock parcial do início ao CHEGOU/PARAR, com limite `chegada − agora + 30 min` (`shortcut: wake lock durante a viagem toda, passar a alarmes exatos se a bateria se queixar`).
- Localização: `LocationManagerCompat.requestLocationUpdates` no GPS, modo poupar (10 s) e modo preciso (1 s) conforme `altaPrecisao`; `onProviderDisabled` → notificação "Liga a localização para a Paragem Certa saber onde estás." Sem FINE, a viagem corre pelo horário.
- `Relogio` a cada 5 s. Enquanto a fase é ESPERA numa etapa da Carris Metropolitana: chegadas da paragem de embarque a cada 30 s (`CarrisMetropolitana(OkHttpHttp(userAgent(...)))` criado no serviço; casar `linhaNome` e hora programada a ±90 s) → `Entrada.Partida`.
- Depois de cada passo que muda o estado (ou a cada 5 s no máximo): `gravarEstado`, notificação de progresso, e `sendBroadcast(Intent(ACAO_ESTADO).setPackage(packageName))`. Em cada evento: notificação de aviso e, se não estiver silenciado, `Alarme.tocar` — sempre antes de `stopForeground`/`stopSelf`.
- Só em `BuildConfig.DEBUG`: cada posição bruta acrescenta-se a `files/viagem/gps.csv` (para o autor copiar para `gravacoes/`).

- [ ] **Step 1: testes** — `COMECAR` com um plano gravado põe o serviço em primeiro plano com a notificação de progresso e escreve `trip-state.json`; `SILENCIAR` grava `silenciado = true`; `PARAR` apaga os ficheiros e para; sem permissão de notificações, um evento chama o alarme sem erro (alarme injetável no teste).
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** testes + `gradle :app:lintPessoalDebug` → PASS. **Step 5:** commit `feat(trip): foreground location service in the :trip process`.

### Task 6: O ecrã da viagem

**Files:** Create `app/.../ui/viagem/ViagemEcra.kt`, `ViagemViewModel.kt`; Modify `ui/Navegacao.kt`, `strings.xml`; Test `ViagemViewModelTest.kt`, `ViagemScreenshotTest.kt`

**Comportamento (spec 5.2):** um cartão por etapa, que se desliza para ver a anterior e a seguinte (`HorizontalPager`); na etapa em veículo, a lista das paragens com "estás aqui" e "sais aqui", e "Faltam N paragens"; à espera, "O 736 chega em 4 min"; a pé, a distância e o tempo; selo "Posição estimada pelo horário" quando `origem == HORARIO`; botões "Silenciar", "Já saí", "Parar" (mesmas ações do serviço); "Partilhar chegada" com a hora prevista de chegada (`textoPartilha` com a eta). O `ViagemViewModel` lê o estado do ficheiro ao abrir, em cada `ACAO_ESTADO` (`ContextCompat.registerReceiver`, `RECEIVER_NOT_EXPORTED`) e ao voltar à app. Rota `@Serializable data object Viagem`. O ecrã não tem mapa.

- [ ] **Step 1: testes** — o VM transforma estado + plano no que o ecrã mostra (etapa atual, faltam, estás aqui, selo de horário); capturas `viagem_claro`, `viagem_escuro`, `viagem_horario`, `viagem_letra200`.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** PASS; gravar as capturas localmente e olhar. **Step 5:** commit `feat(trip): live trip screen`.

### Task 7: "Começar viagem", primeira viagem e viagem interrompida

**Files:** Modify `ui/rotas/DetalheRotaEcra.kt` (`aoComecar`), `ui/rotas/RotasEcra.kt`, `ui/inicio/InicioEcra.kt`, `ui/eu/EuEcra.kt`, `dados/Preferencias.kt`, `strings.xml`; Create `ui/viagem/PrimeiraViagem.kt`; Test `PreferenciasTest.kt`, `PrimeiraViagemTest.kt`, capturas `detalhe_comecar`, `inicio_interrompida`

**Comportamento:**
- O detalhe da rota ganha o botão principal "Começar viagem" (antes de "Partilhar rota").
- Ao tocar (spec 7.2), por ordem: localização precisa (só aproximada → explicar e pedir uma vez; recusada → começar pelo horário); localização do telemóvel desligada → aviso com atalho para as definições e "Começar à mesma"; notificações (Android 13+; recusadas → "Os avisos Prepara-te e Sai agora vão só tocar e vibrar, sem aparecer no ecrã" e continua); na primeira viagem: tirar a app da poupança de bateria (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) e a ligação `https://dontkillmyapp.com/<fabricante>`; volume do alarme a 0 → aviso. Depois: gravar o plano (com `som`/`vibrar` das definições), apagar estado antigo, `startForegroundService(COMECAR)`, abrir o ecrã da viagem.
- Início: "Viagem interrompida — toca para retomar" com "Descartar", quando há estado sem o serviço a correr (`FicheirosViagem`); "retomar" só a partir da app visível.
- Preferências: `somAviso` e `vibracaoAviso` (por omissão ligados) e `primeiraViagemFeita`; em Eu → Definições, dois interruptores "Som do aviso" e "Vibração do aviso".

- [ ] **Step 1: testes** — preferências ida e volta; a ordem dos passos da primeira viagem (função pura sobre o que falta: permissões, localização, bateria, volume) e que a segunda viagem não repete a explicação da bateria; capturas.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** PASS. **Step 5:** commit `feat(trip): start a trip, first-trip setup and resume after interruption`.

### Task 8: Verificação do manifesto no CI, guia da viagem real e versão 0.3.0

**Files:** Modify `.github/workflows/android.yml`, `app/build.gradle.kts`, `PATCHNOTES.md`; Create `docs/testes/viagem-real.md`

- [ ] CI: depois do build, um passo verifica o manifesto fundido da variante pessoal: o serviço tem `foregroundServiceType="location"` e `process=":trip"`; existem `FOREGROUND_SERVICE_LOCATION` e `POST_NOTIFICATIONS`; não existem `ACCESS_BACKGROUND_LOCATION`, `USE_FULL_SCREEN_INTENT` nem `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (spec 11).
- [ ] `docs/testes/viagem-real.md`: as duas viagens da validação (autocarro e Metro, ecrã desligado, uma com transbordo), o que conta como passar ("Prepara-te" antes da penúltima, "Sai agora" antes de parar), e como copiar `files/viagem/gps.csv` para `Paragem Certa/gravacoes/` (fora do repositório).
- [ ] `versao = "0.3.0"`, secção curta `## 0.3.0` no `PATCHNOTES.md`, testes + lint + `assemblePessoalRelease` verdes, commit `release: 0.3.0`.
- [ ] Revisão final do ramo inteiro e correções; publicar só depois do "publica". A fase 3 só fica fechada depois das viagens reais do autor (spec 11) e da 0.3.1.
