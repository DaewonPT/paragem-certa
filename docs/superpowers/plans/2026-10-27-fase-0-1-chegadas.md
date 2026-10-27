# Paragem Certa — Fases 0 e 1 ("Chegadas") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar a versão 0.1 "Chegadas" da Paragem Certa, uma app Android que, ao abrir, mostra num mapa as paragens e os autocarros ao vivo da Área Metropolitana de Lisboa e as próximas chegadas da paragem favorita, com cores de atraso.

**Architecture:**
- Dois módulos Gradle:
  - `:core`: Kotlin/JVM puro, com os tipos, os adaptadores da Carris Metropolitana e do Transitous e as regras puras (atraso, proximidade, versões, intervalos).
  - `:app`: Android, com Compose, MapLibre, Room e WorkManager.
- O telemóvel fala diretamente com a API da Carris Metropolitana e com o Transitous. Não há servidor nesta fase.
- A variante `pessoal` atualiza-se sozinha a partir do GitHub Releases.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), MapLibre Android 13.x, Room, WorkManager, DataStore, OkHttp, kotlinx.serialization, kotlinx.coroutines, JUnit 4, Robolectric + Roborazzi, GitHub Actions. As versões exatas estão na Task 2.

**Spec:** `docs/superpowers/specs/2026-10-27-paragem-certa-design.md` (secções 3 "Fase 0" e "Fase 1", 5, 6, 9, 10 e 11).

## Global Constraints

**Identidade e textos:**
- Pacote base `pt.pixellusitano.paragemcerta`.
- Variantes: `pessoal` (`applicationIdSuffix ".pessoal"`) e `play`. Só a `pessoal` declara `REQUEST_INSTALL_PACKAGES`.
- minSdk 26; compileSdk 37; targetSdk 36 (ver "Versões fixadas").
- Todo o texto visível em português de Portugal, em `app/src/main/res/values/strings.xml`. Usar "paragem", "autocarro", "comboio", "elétrico", "ecrã" e "telemóvel"; nunca termos do Brasil.
- Datas e horas em `Locale("pt","PT")`, com `ZoneId.of("Europe/Lisbon")`.

**Pedidos à rede:**
- Cada pedido à rede leva o User-Agent `ParagemCerta/<versionName> (contact@pixellusitano.com)`.
- Carris Metropolitana: base `https://api.carrismetropolitana.pt/v2/`.
- Transitous: base `https://api.transitous.org`, com `/api/v6/stoptimes`, `/api/v6/map/stops` e `/api/v1/geocode`. Nunca descarregar em massa.
- Na pesquisa e no mapa, as paragens do Transitous com id começado por `pt-Carris-Metropolitana_` são escondidas. Os ids do Transitous para a Carris Metropolitana não coincidem com os da própria API (`360419` contra `060419`); a Carris Metropolitana vem só da API dela.
- **Atualização:**
  - chegadas a cada 20 s (Carris Metropolitana) e a cada 60 s (Transitous), só com o ecrã visível;
  - veículos a cada 15 s, só com o mapa visível;
  - paragens e linhas da Carris Metropolitana uma vez por dia, só com rede.

**Estilo Azulejo:**
- Claro: fundo `#FFFFFF`, principal `#1F4FA3`, destaque `#FFC629`, texto `#132A4F`.
- Escuro: fundo `#0B1222`, superfície `#121B30`, principal `#7FB0FF`.
- A letra é Nunito, ou a do sistema se faltar.

**Cores dos minutos** (claro / escuro):

| Estado | Regra | Claro | Escuro | Texto |
|---|---|---|---|---|
| A horas | atraso de −1 a +1 | `#15803D` | `#4ADE80` | "a horas" |
| Atrasado | +2 a +5 | `#B45309` | `#FBBF24` | "+N" |
| Muito atrasado | > +5 | `#C81E1E` | `#F87171` | "+N" |
| Adiantado | ≤ −2 | `#1F4FA3` | `#7FB0FF` | "−N adiantado" |
| Horário | sem tempo real | `#5B6472` | `#9AA4B2` | "🕒 horário" |

- O atraso é arredondado ao minuto, com o meio minuto para longe de zero.
- Os limites 2 e 5 são configuráveis.
- **Acessibilidade:**
  - a cor nunca aparece sozinha;
  - botões com pelo menos 48 dp;
  - a letra funciona a 200 %;
  - todas as ações do mapa têm equivalente em lista ou pesquisa.

**Commits:**
- Em inglês e sem atribuição a IA. Nunca acrescentar linhas `Co-Authored-By`.
- Nunca fazer commit de `*.jks`, `keystore.properties`, `local.properties` ou de qualquer conteúdo de `../Chave Privada/`.

**Downloads e publicação:**
- Nada se descarrega sem antes dizer ao autor o nome, a origem e o tamanho, e ele autorizar.
- Nada se publica (repositório público, tag, release) sem o autor dizer "publica".

## Review Focus

1. **Paragem com 0 chegadas, ou API a devolver `[]` de madrugada:** mostrar "Não há chegadas nas próximas 2 horas", e nunca um ecrã vazio nem um erro (Task 9).
2. **Chegada já passada (`observed_arrival_unix` preenchido, ou hora prevista no passado):** não pode aparecer como "0 min" nem negativa. É descartada (Tasks 6 e 7).
3. **Viagem cancelada no Transitous (`cancelled` ou `tripCancelled` verdadeiro):** não pode aparecer como normal. É descartada (Task 8).
4. **Sem rede ao abrir a app:** mostrar as últimas chegadas guardadas com "Sem ligação — atualizado há X min"; se não houver nada guardado, uma mensagem e não um ecrã branco (Tasks 9 e 11).
5. **Resposta 429 ou 5xx de uma fonte:** a frequência baixa com pausas crescentes até 5 min e aparece "tempo real indisponível". Nunca repetir em ciclo (Tasks 8 e 9).

---

## Fase 0 — Preparação

### Task 1: Ferramentas no PC

**Files:** nenhum no repositório. O guia fica em `docs/ferramentas.md`.

- [ ] **Step 1: Pedir autorização ao autor, com esta lista**

| O quê | Origem | Tamanho |
|---|---|---|
| JDK Temurin 21.0.12.1+1 (ZIP, Windows x64) | github.com/adoptium (sha256 `f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`) | 205 MB |
| Android SDK Command-line Tools 23.0 | dl.google.com (`commandlinetools-win-16111833_latest.zip`) | 155 MB |
| Gradle 9.8.2 (só uma vez, para criar o `gradlew`) | services.gradle.org | 152 MB |
| platform-tools, `platforms;android-37`, `build-tools;36.0.0` (pelo sdkmanager) | dl.google.com | cerca de 135 MB |
| Android 16 do Robolectric (automático no 1.º teste) | Maven Central | 213 MB |
| Bibliotecas do Gradle (automático) | Maven Central e Google | 1 a 2 GB |

Esperar pelo "sim".

- [ ] **Step 2: Instalar**

- **Pastas:**
  - JDK em `C:\Java\jdk-21`;
  - SDK em `C:\Android\sdk`, com as ferramentas em `cmdline-tools\latest`;
  - Gradle em `C:\Gradle\gradle-9.8.2`.
- **Variáveis do utilizador** (PowerShell `[Environment]::SetEnvironmentVariable(…,'User')`, nunca `setx PATH`): `JAVA_HOME` e `ANDROID_HOME`. No `PATH`, juntar `%JAVA_HOME%\bin`, `%ANDROID_HOME%\platform-tools` e `%ANDROID_HOME%\cmdline-tools\latest\bin`.
- **Pacotes, no Git Bash:** `sdkmanager.bat "platform-tools" "platforms;android-37" "build-tools;36.0.0"`. Antes, `yes | sdkmanager.bat --licenses`. Se `platforms;android-37` não existir com esse nome, usar o que `sdkmanager.bat --list | grep android-37` mostrar.
- As variáveis só valem em programas abertos depois, por isso exportá-las também na sessão atual.

- [ ] **Step 3: Verificar**

Run: `java -version` → Expected: `openjdk version "21.`
Run: `sdkmanager.bat --list_installed` → Expected: `platforms;android-37`, `build-tools;36.0.0` e `platform-tools`.

- [ ] **Step 4: Escrever `docs/ferramentas.md`**

Escrever em PT-PT o que foi instalado, onde ficou e como repetir. Fazer commit: `docs: local toolchain guide`.

### Task 2: Esqueleto Gradle com `:core` e `:app`

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `.gitattributes`
- Create: `core/build.gradle.kts`, `core/src/main/kotlin/pt/pixellusitano/paragemcerta/core/Versao.kt`, `core/src/test/kotlin/pt/pixellusitano/paragemcerta/core/VersaoTest.kt`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/pt/pixellusitano/paragemcerta/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/pessoal/AndroidManifest.xml`
- Create: `LICENSE` (texto GPL-3.0), `README.md` (PT-PT, curto), `PATCHNOTES.md`

**Interfaces:**
- Produces:
  - `fun versionCodeFromTag(tag: String): Int?`. Exemplos: `"v0.1.0"` dá 100; `"v1.2.3"` dá 10203; uma tag inválida dá `null`.
  - `fun isNewer(currentVersionCode: Int, tag: String): Boolean`.
  - O `versionCode` da app vem da mesma regra: `major*10000 + minor*100 + patch`.

**Versões a fixar em `gradle/libs.versions.toml`:** (preenchidas a partir do estudo de 27/10/2026, ver tabela no fim deste plano)

- [ ] **Step 1: Write the failing test** em `VersaoTest.kt`

```kotlin
@Test fun versionCodeFromTag_parsesSemver() {
    assertEquals(100, versionCodeFromTag("v0.1.0"))
    assertEquals(10203, versionCodeFromTag("v1.2.3"))
    assertNull(versionCodeFromTag("latest"))
}
@Test fun isNewer_comparesCodes() {
    assertTrue(isNewer(100, "v0.1.1"))
    assertFalse(isNewer(101, "v0.1.1"))
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew :core:test` → Expected: FAIL (unresolved `versionCodeFromTag`).

- [ ] **Step 3: Implement**

- `Versao.kt`: implementar as duas funções.
- `app`:
  - Compose com `MainActivity`, que mostra só o nome da app no estilo Azulejo (ainda sem mapa);
  - `productFlavors` `pessoal` e `play` na dimensão `distribuicao`;
  - `versionName "0.1.0"` e `versionCode 100`.
- `.gitattributes`: `* text=auto eol=lf`, `*.bat eol=crlf` e `gradlew` com bit executável (`git update-index --chmod=+x gradlew`).

- [ ] **Step 4: Verificar**

Run: `./gradlew :core:test assemblePessoalDebug` → Expected: `BUILD SUCCESSFUL`, com `app/build/outputs/apk/pessoal/debug/app-pessoal-debug.apk` gerado.

- [ ] **Step 5: Commit**

`git add -A && git commit -m "build: Gradle skeleton with core and app modules"`

### Task 3: Tema Azulejo, textos e capturas de ecrã automáticas

**Files:**
- Create: `app/src/main/kotlin/pt/pixellusitano/paragemcerta/ui/tema/Cores.kt`, `Tema.kt`, `Letra.kt`
- Create: `core/src/main/kotlin/pt/pixellusitano/paragemcerta/core/atraso/EstadoAtraso.kt`, só com `enum class EstadoAtraso { A_HORAS, ATRASADO, MUITO_ATRASADO, ADIANTADO, HORARIO }`. As regras chegam na Task 6.
- Create: `app/src/main/res/font/nunito.ttf` (licença OFL, com `OFL.txt`)
- Create: ícone provisório adaptativo `app/src/main/res/mipmap-anydpi/ic_launcher.xml`: quadrado azul-cobalto com uma paragem estilizada branca e uma risca amarela.
- Create: `docs/logotipo.md`, com as instruções para o logótipo e o ícone definitivos:
  - estilo Azulejo, azul e amarelo, ligado à Pixel Lusitano;
  - legível a 48 dp;
  - nada parecido com a Moovit (laranja e o seu símbolo).
- Create: `app/src/test/kotlin/pt/pixellusitano/paragemcerta/ui/tema/TemaScreenshotTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun TemaParagemCerta(modo: ModoTema = ModoTema.SISTEMA, content: @Composable () -> Unit)`, com `enum class ModoTema { SISTEMA, CLARO, ESCURO }`;
  - `object CoresAtraso { fun cor(estado: EstadoAtraso, escuro: Boolean): Color }`;
  - `@Composable fun FrisoAzulejo(modifier: Modifier)`, a faixa às riscas de 6 dp nas cores do tema.

- [ ] **Step 1: Write the failing test**

Um teste Roborazzi com 3 capturas de uma amostra (título, linha de chegada, friso): `amostra_claro`, `amostra_escuro` e `amostra_letra200` (`fontScale = 2f`).

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew :app:testPessoalDebugUnitTest` → Expected: FAIL (unresolved `TemaParagemCerta`).

- [ ] **Step 3: Implementar**

Implementar o tema com as cores das Global Constraints e os 5 estados de atraso.

- [ ] **Step 4: Ver as capturas no PC**

Run: `./gradlew :app:recordRoborazziPessoalDebug` → Expected: PASS e 3 PNG em `app/src/test/screenshots/`.

Abrir os PNG e confirmar visualmente o azul, o amarelo e o friso. Depois **apagar** essa pasta antes do commit. As capturas de referência são gravadas no CI (Task 4, Step 6), porque as do Windows têm píxeis diferentes.

- [ ] **Step 5: Commit**

`feat(ui): Azulejo theme light/dark with delay colours`

### Task 4: Repositório público, assinatura e CI

**Files:**
- Create: `.github/workflows/android.yml` (tabela da Task 2, secção "CI")
- Modify: `app/build.gradle.kts` (bloco `signingConfigs`)
- Fora do repositório: `../Chave Privada/paragemcerta-release.jks` e `../Chave Privada/keystore.properties`

**Passos:**

- [ ] **Step 1: Pedir ao autor**

Pedir ao autor o "publica" para criar o repositório **público** `DaewonPT/paragem-certa`.

- [ ] **Step 2: Criar a chave de lançamento**

- Criar a chave com `keytool` em `../Chave Privada/`.
- A senha é gerada aleatoriamente e guardada só no `keystore.properties` dessa pasta.
- Confirmar que `git status` não mostra nada de `Chave Privada`.

- [ ] **Step 3: Configurar a assinatura**

- `signingConfigs.release` segue `anexos/assinatura.md`:
  - no CI lê `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD` e `ANDROID_KEY_ALIAS`;
  - localmente lê `../Chave Privada/keystore.properties`.
- Se nenhum existir, a variante `release` fica sem assinatura, mas a compilação não falha.

- [ ] **Step 4: Criar o repositório e o workflow do CI**

- Copiar `anexos/android.yml` para `.github/workflows/android.yml`.
- Criar o repositório com `gh repo create DaewonPT/paragem-certa --public --source . --push`.
- Pôr os 3 segredos com `gh secret set`, sem os mostrar no terminal (comandos em `anexos/assinatura.md`).

- [ ] **Step 5: Verificar**

Run: `gh run watch` (no último push) → Expected: verde. O aviso "Sem capturas de referência" é normal nesta altura.

- [ ] **Step 6: Gravar as capturas de referência no CI**

- Run: `gh workflow run android.yml -f record_screenshots=true`.
- Quando acabar: `gh run download <id> -n roborazzi-goldens -D app/src/test/screenshots`.
- Fazer commit das capturas. O push seguinte tem de ficar verde, já com `verifyRoborazziPessoalDebug`.
- Repetir este passo sempre que um ecrã muda de propósito.

- [ ] **Step 7: Commit**

`ci: build, test and signed release on tags`

### Task 5: Ecrã "Sobre e fontes de dados"

**Files:**
- Create: `app/src/main/kotlin/pt/pixellusitano/paragemcerta/ui/sobre/SobreEcra.kt`
- Test: `app/src/test/kotlin/pt/pixellusitano/paragemcerta/ui/sobre/SobreScreenshotTest.kt`

**Conteúdo exato:**
- Nome e versão.
- "Feito pela Pixel Lusitano (Alize.On.Me)".
- Licença GPL-3.0, com ligação para o repositório.
- **Fontes de dados**, cada uma com ligação:
  - "Carris Metropolitana — dados abertos" (https://carrismetropolitana.pt/open-data);
  - "Transitous — horários e rotas" (https://transitous.org/sources);
  - "OpenFreeMap" (https://openfreemap.org);
  - "© OpenStreetMap contributors" (https://www.openstreetmap.org/copyright).
- Email contact@pixellusitano.com.

- [ ] **Step 1: Write the failing test**

Captura `sobre_claro`, e um teste de semântica que encontra o texto "Transitous — horários e rotas" e confirma que tem ação de clique.

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

As ligações abrem com `Intent.ACTION_VIEW`.

- [ ] **Step 4: Verify and record**

Run: `./gradlew :app:recordRoborazziPessoalDebug :app:testPessoalDebugUnitTest` → Expected: PASS.

- [ ] **Step 5: Commit**

`feat(ui): about and data sources screen`

**Fim da fase 0:** instalar `app-pessoal-debug.apk` no telemóvel do autor (por cabo ou pela Release) e o autor confirma que abre.

---

## Fase 1 — versão 0.1 "Chegadas"

### Task 6: Tipos do `core` e cálculo do atraso

**Files:**
- Create: `core/src/main/kotlin/pt/pixellusitano/paragemcerta/core/modelo/Modelo.kt`, `.../core/atraso/Atraso.kt`
- Test: `core/src/test/kotlin/pt/pixellusitano/paragemcerta/core/atraso/AtrasoTest.kt`

**Interfaces:**
- Produces (`modelo`):

```kotlin
enum class Fonte { CM, TRANSITOUS }
enum class Modo { AUTOCARRO, ELETRICO, METRO, COMBOIO, BARCO, OUTRO }
data class RefParagem(val fonte: Fonte, val id: String) { val chave get() = "${fonte.name}:$id" }
data class Paragem(val ref: RefParagem, val nome: String, val lat: Double, val lon: Double, val acessivel: Boolean?)
data class Linha(val fonte: Fonte, val id: String, val nomeCurto: String, val nomeLongo: String, val cor: String?, val corTexto: String?, val modo: Modo)
data class Chegada(val fonte: Fonte, val linhaId: String, val linhaNome: String, val destino: String,
                   val programada: Instant, val prevista: Instant?, val idViagem: String?, val idVeiculo: String?,
                   val modo: Modo, val cor: String?, val acessivel: Boolean?)
data class Veiculo(val id: String, val linhaId: String, val lat: Double, val lon: Double, val rumo: Int?, val ultimoSinal: Instant)
enum class Gravidade { GRAVE, AVISO, INFO }
data class Alerta(val id: String, val linhaIds: Set<String>, val paragemIds: Set<String>, val inicio: Instant?, val fim: Instant?,
                  val titulo: String, val texto: String, val gravidade: Gravidade, val imagemUrl: String?)
```

- Produces (`atraso`; o `EstadoAtraso` já existe desde a Task 3):
  - `data class Limites(val atrasado: Int = 2, val muitoAtrasado: Int = 5)`
  - `fun minutosDeAtraso(programada: Instant, prevista: Instant): Int`: arredonda ao minuto, com o meio minuto para longe de zero.
  - `fun estado(c: Chegada, l: Limites = Limites()): EstadoAtraso`, com d = `minutosDeAtraso`:
    - HORARIO se não há `prevista`;
    - ADIANTADO se `d ≤ −2`;
    - A_HORAS se `−1 ≤ d < l.atrasado`;
    - ATRASADO se `l.atrasado ≤ d ≤ l.muitoAtrasado`;
    - MUITO_ATRASADO se `d > l.muitoAtrasado`.
  - `fun rotuloTempo(c: Chegada, agora: Instant, zona: ZoneId): String`, que devolve:
    - `"agora"` se faltar menos de 1 min;
    - `"N min"` até 60 min, com N = minutos inteiros que faltam, arredondados para baixo;
    - `"HH:mm"` acima de 60 min;
    - `"HH:mm"` sempre que não há tempo real.
  - `fun rotuloAtraso(c: Chegada, l: Limites = Limites()): String`: `"a horas"`, `"+3"`, `"+9"`, `"−2 adiantado"` (com o sinal de menos U+2212) ou `"🕒 horário"`.
  - `fun aindaPorChegar(c: Chegada, agora: Instant): Boolean`: falso se a hora em uso (prevista ou programada) já passou há mais de 30 s.

- [ ] **Step 1: Write the failing test** (valores exatos da especificação)

```kotlin
private val t0 = Instant.parse("2026-10-27T14:00:00Z")
private fun c(prev: Long?) = Chegada(Fonte.CM, "3708", "3708", "Cais do Sodré", t0, prev?.let { t0.plusSeconds(it) }, null, null, Modo.AUTOCARRO, null, null)
@Test fun arredondaMeioMinutoParaLongeDeZero() { assertEquals(2, minutosDeAtraso(t0, t0.plusSeconds(90))); assertEquals(-2, minutosDeAtraso(t0, t0.minusSeconds(90))); assertEquals(1, minutosDeAtraso(t0, t0.plusSeconds(89))) }
@Test fun estados() {
    assertEquals(EstadoAtraso.A_HORAS, estado(c(60)))
    assertEquals(EstadoAtraso.A_HORAS, estado(c(-60)))
    assertEquals(EstadoAtraso.ATRASADO, estado(c(120)))
    assertEquals(EstadoAtraso.ATRASADO, estado(c(300)))
    assertEquals(EstadoAtraso.MUITO_ATRASADO, estado(c(360)))
    assertEquals(EstadoAtraso.ADIANTADO, estado(c(-120)))
    assertEquals(EstadoAtraso.HORARIO, estado(c(null)))
}
@Test fun limitesConfiguraveis() { assertEquals(EstadoAtraso.A_HORAS, estado(c(120), Limites(atrasado = 3, muitoAtrasado = 8))) }
@Test fun rotulos() {
    val lx = ZoneId.of("Europe/Lisbon")
    assertEquals("agora", rotuloTempo(c(0), t0.plusSeconds(-20), lx))
    assertEquals("3 min", rotuloTempo(c(180), t0, lx))
    assertEquals("2 min", rotuloTempo(c(150), t0, lx))                        // minutos arredondados para baixo
    assertEquals("14:00", rotuloTempo(c(0), t0.minusSeconds(70 * 60), lx))    // faltam 70 min → hora
    assertEquals("14:00", rotuloTempo(c(null), t0.minusSeconds(120), lx))     // sem tempo real → hora marcada
    assertEquals("+3", rotuloAtraso(c(180))); assertEquals("−2 adiantado", rotuloAtraso(c(-120))); assertEquals("🕒 horário", rotuloAtraso(c(null)))
}
@Test fun passadasSaoDescartadas() { assertFalse(aindaPorChegar(c(0), t0.plusSeconds(31))); assertTrue(aindaPorChegar(c(0), t0.plusSeconds(29))) }
```

(A 27/10/2026 Lisboa já está na hora de inverno, igual a UTC, por isso `14:00Z` mostra-se como `14:00`.)

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew :core:test --tests '*AtrasoTest'` → Expected: FAIL.

- [ ] **Step 3: Implementar `Modelo.kt` e `Atraso.kt`**

- [ ] **Step 4: Run it and see it pass**

Run: `./gradlew :core:test` → Expected: PASS.

- [ ] **Step 5: Commit**

`feat(core): model and delay rules`

### Task 7: Adaptador da Carris Metropolitana

**Files:**
- Create: `core/src/main/kotlin/pt/pixellusitano/paragemcerta/core/fontes/cm/CmIds.kt`, `CmJson.kt`, `CarrisMetropolitana.kt`
- Create: `core/src/main/kotlin/pt/pixellusitano/paragemcerta/core/rede/Rede.kt`
- Test: `core/src/test/kotlin/pt/pixellusitano/paragemcerta/core/fontes/cm/CarrisMetropolitanaTest.kt`
- Fixtures (já existem, respostas reais de 27/10/2026, cortadas): `core/src/test/resources/fixtures/cm-arrivals-060419.json` (o 1.º item tem a hora prevista 240 s depois da programada), `cm-stops.json`, `cm-lines.json`, `cm-vehicles.json` e `cm-alerts.json`

**Interfaces:**
- Consumes: os tipos da Task 6.
- Produces:
  - `fun cmIdLimpo(bruto: String): String` tira todos os prefixos `[XXXX]` do início. Exemplos:
    - `"[YA15B]3708"` → `"3708"`;
    - `"[PL3CL][YA15B]3708_0_1"` → `"3708_0_1"`.
  - `fun cmLinhaDaRota(routeId: String): String`: `"[LA77N]1625_0"` → `"1625"`.
  - `fun gravidadeDe(effect: String?, severityLevel: String?): Gravidade`:
    - `severity_level`: `SEVERE` → GRAVE, `WARNING` → AVISO, `INFO` → INFO;
    - se faltar, pelo `effect`: `NO_SERVICE` → GRAVE; `DETOUR`, `REDUCED_SERVICE` e `SIGNIFICANT_DELAYS` → AVISO; o resto → INFO.
  - `object CmParser`, que só faz parsing a partir de `String`:
    - `fun chegadas(json: String, agora: Instant): List<Chegada>`:
      - ignora itens com `observed_arrival_unix` preenchido e os que `aindaPorChegar` rejeita;
      - ordena por hora em uso;
      - `linhaId` e `idVeiculo` vêm limpos.
    - `fun paragens(json: String): List<Paragem>`;
    - `fun linhas(json: String): List<Linha>` (todas com modo AUTOCARRO);
    - `fun veiculos(json: String): List<Veiculo>`;
    - `fun alertas(json: String, agora: Instant): List<Alerta>`: só os ativos em `agora`; usa o texto `pt`; `linhaIds` vem de `informed_entity.route_id` passado por `cmLinhaDaRota`.
  - `interface Http { suspend fun get(url: String): Resposta }` e `data class Resposta(val codigo: Int, val corpo: String)` (em `Rede.kt`), com uma implementação OkHttp que põe o User-Agent das Global Constraints.
  - `class CarrisMetropolitana(http: Http, base: String = "https://api.carrismetropolitana.pt/v2/")`, com `suspend fun chegadas(paragemId: String, agora: Instant): Resultado<List<Chegada>>` e o mesmo para `paragens()`, `linhas()`, `veiculos()` e `alertas(agora)`.
  - `sealed interface Resultado<out T> { data class Ok<T>(val valor: T) : Resultado<T>; data class Falha(val codigo: Int?, val limite: Boolean) : Resultado<Nothing> }`, em que `limite = true` para 429 e 503.

- [ ] **Step 1: Write the failing tests**

- `cmIdLimpo` e `cmLinhaDaRota` com os 3 exemplos acima.
- `chegadas(fixture, agora = 2026-10-27T05:00:00Z)`:
  - devolve 3 itens;
  - o 1.º tem `linhaId == "3708"`, `prevista - programada == 240 s` e `estado(...) == ATRASADO`;
  - o 2.º tem `destino == "Costa da Caparica (Terminal)"` e `prevista == null`.
- Com `agora` 10 min depois da última chegada, devolve `[]`.
- `paragens` encontra `060419`, com o nome "Cais do Sodré (Terminal)" e `acessivel == false`.
- `alertas(fixture, agora = 2026-10-27T12:00:00Z)` tem o alerta da 1625 com `gravidade == AVISO` (`DETOUR`) e `linhaIds == setOf("1625")`.
- `gravidadeDe("NO_SERVICE", null) == GRAVE` e `gravidadeDe("DETOUR", "INFO") == INFO`.
- `CarrisMetropolitana` com um `Http` falso a devolver 429 → `Falha(429, limite = true)`.

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

Usar kotlinx.serialization com `ignoreUnknownKeys = true` e campos anuláveis.

- [ ] **Step 4: Run it and see it pass**

Run: `./gradlew :core:test` → Expected: PASS.

- [ ] **Step 5: Commit**

`feat(core): Carris Metropolitana adapter`

### Task 8: Adaptador do Transitous, proximidade e pausas

**Files:**
- Create: `core/src/main/kotlin/pt/pixellusitano/paragemcerta/core/fontes/transitous/Transitous.kt`, `TransitousJson.kt`
- Create: `core/src/main/kotlin/pt/pixellusitano/paragemcerta/core/regras/Proximidade.kt`, `Pausas.kt`
- Test: `TransitousTest.kt`, `ProximidadeTest.kt`, `PausasTest.kt`
- Fixtures (já existem): `transitous-stoptimes.json`, `transitous-map-stops.json`

**Interfaces:**
- Produces:
  - `object TransitousParser`:
    - `fun chegadas(json: String, paragemId: String, agora: Instant): List<Chegada>`:
      - só itens com `place.stopId == paragemId`;
      - descarta `cancelled` ou `tripCancelled`;
      - `programada` = `place.scheduledDeparture ?: place.scheduledArrival`;
      - `prevista` = `if (realTime) place.departure ?: place.arrival else null`;
      - `linhaNome` = `routeShortName`, ou `displayName` se faltar;
      - `modo` pelo campo `mode`: `BUS` → AUTOCARRO, `TRAM`/`CABLE_CAR`/`FUNICULAR` → ELETRICO, `SUBWAY`/`METRO` → METRO, `RAIL`/`SUBURBAN`/`REGIONAL_RAIL`/`HIGHSPEED_RAIL`/`LONG_DISTANCE`/`NIGHT_RAIL`/`REGIONAL_FAST_RAIL` → COMBOIO, `FERRY` → BARCO, o resto → OUTRO;
      - `acessivel` = `wheelchairAccessible == "ACCESSIBLE"`, `null` se for `UNKNOWN`.
    - `fun paragensNoMapa(json: String): List<Paragem>`: exclui ids começados por `pt-Carris-Metropolitana_`.
    - `fun pesquisa(json: String): List<Paragem>`: resposta do geocode com `type=STOP`; também exclui a Carris Metropolitana.
  - `class Transitous(http: Http, base: String = "https://api.transitous.org")`, com:
    - `suspend fun chegadas(paragemId: String, agora: Instant): Resultado<List<Chegada>>` → `GET /api/v6/stoptimes?stopId=<url-encoded>&n=30`;
    - `suspend fun paragensNaZona(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): Resultado<List<Paragem>>` → `GET /api/v6/map/stops?min=<lat>,<lon>&max=<lat>,<lon>`;
    - `suspend fun pesquisar(texto: String): Resultado<List<Paragem>>` → `GET /api/v1/geocode?text=<enc>&type=STOP`.
  - `fun distanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double` (haversine, raio 6 371 000 m).
  - `fun maisProximas(paragens: List<Paragem>, lat: Double, lon: Double, limite: Int = 5, raioMetros: Double = 800.0): List<Paragem>`.
  - `data class Favorita(val ref: RefParagem, val nome: String, val ordem: Int, val lat: Double, val lon: Double)` e `fun favoritaPorOmissao(favoritas: List<Favorita>, ultimaPosicao: Pair<Double, Double>?): Favorita?`: a mais próxima da última posição; sem posição, a de menor `ordem`; sem favoritas, `null`.
  - `fun intervaloNormal(fonte: Fonte): Duration`: CM 20 s, TRANSITOUS 60 s.
  - `fun pausaDepoisDeFalha(fonte: Fonte, tentativa: Int): Duration` = `min(intervaloNormal × 2^tentativa, 5 min)`.

- [ ] **Step 1: Write the failing tests**

- **Chegadas do Transitous:** `chegadas(fixture, "pt-Carris_3803", agora = 2026-10-27T01:50:00Z)` devolve uma chegada com:
  - `linhaNome == "201"`;
  - `programada == Instant.parse("2026-10-27T01:54:00Z")`;
  - `prevista == null` (`realTime` falso);
  - `modo == AUTOCARRO`.
- **Paragem sem itens:** com o id do comboio, `pt-Comboios-de-Portugal(CP)_94_69005`, a mesma fixture devolve `[]`.
- **Cancelamentos:** uma cópia da fixture com `"cancelled": true` no item → `[]`.
- **Mapa:** `paragensNoMapa(fixture)` tem 3 paragens e nenhuma com id `pt-Carris-Metropolitana_*`.
- **Proximidade:**
  - `distanciaMetros` entre `060419` (38.705839, −9.143122) e `062000` (38.70533, −9.14394) dá entre 80 e 100 m;
  - `maisProximas` com raio de 50 m a partir de `060419` devolve só `060419`.
- **Favorita por omissão:** perto de `062000` devolve a favorita `062000`; com `null`, devolve a de `ordem` 0.
- **Pausas:** `pausaDepoisDeFalha(CM, 0) == 20 s`, `(CM, 3) == 160 s` e `(TRANSITOUS, 4) == 5 min`.

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Run it and see it pass**

Run: `./gradlew :core:test` → Expected: PASS.

- [ ] **Step 5: Commit**

`feat(core): Transitous adapter, proximity and backoff rules`

### Task 9: Dados no telemóvel: Room, repositório e atualização diária

**Files:**
- Create: `app/src/main/kotlin/pt/pixellusitano/paragemcerta/dados/BaseDados.kt` (Room: `ParagemEntidade`, `LinhaEntidade`, `FavoritaEntidade`, `ChegadasGuardadasEntidade`, DAOs)
- Create: `.../dados/Repositorio.kt`, `.../dados/AtualizacaoDiaria.kt` (CoroutineWorker), `.../dados/Ligacoes.kt` (cria as peças à mão)
- Test: `app/src/test/kotlin/pt/pixellusitano/paragemcerta/dados/RepositorioTest.kt` (Robolectric, Room em memória, `Http` falso)

**Interfaces:**
- Consumes: `CarrisMetropolitana`, `Transitous`, `CmParser`, `TransitousParser`, `favoritaPorOmissao`, `pausaDepoisDeFalha` e `estado`.
- Produces:
  - `data class EstadoChegadas(val chegadas: List<Chegada>, val atualizadoEm: Instant?, val semLigacao: Boolean, val tempoRealIndisponivel: Boolean)`
  - `class Repositorio`, com:
    - `fun chegadas(ref: RefParagem): Flow<EstadoChegadas>`:
      - consulta ao ritmo de `intervaloNormal(ref.fonte)` enquanto houver alguém a recolher o fluxo;
      - em falha, espera `pausaDepoisDeFalha`;
      - guarda a última resposta boa em `ChegadasGuardadasEntidade` e emite-a com `semLigacao = true` quando a rede falha.
    - `fun veiculos(): Flow<List<Veiculo>>` (CM, a cada 15 s);
    - `suspend fun alertasDaParagem(ref: RefParagem): List<Alerta>`;
    - `fun favoritas(): Flow<List<Favorita>>`;
    - `suspend fun alternarFavorita(p: Paragem)`;
    - `suspend fun paragensNaZona(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Paragem>`: CM da Room mais Transitous pela rede;
    - `suspend fun pesquisar(texto: String): List<Paragem>`: CM da Room (`LIKE`, ignorando acentos através de uma coluna `nomeNormalizado`) mais o Transitous;
    - `suspend fun linhaCm(id: String): Linha?`.
  - `AtualizacaoDiaria`: descarrega `stops` e `lines` da CM e substitui-os na Room numa transação. Agenda-se com `PeriodicWorkRequest` de 24 h, com `NetworkType.CONNECTED`.

- [ ] **Step 1: Write the failing tests**

- **Cache sem rede:** com um `Http` falso que responde a fixture e depois lança `IOException`, a segunda emissão de `chegadas(CM:060419)` tem as mesmas chegadas, `semLigacao == true` e `atualizadoEm` igual ao da primeira.
- **Limite de pedidos:** com 429, emite `tempoRealIndisponivel == true` e o pedido seguinte só acontece depois de `pausaDepoisDeFalha(CM, 0)` (relógio de teste do coroutines).
- **Paragem sem chegadas:** `[]` da API dá `chegadas.isEmpty()` e `semLigacao == false`.
- **Pesquisa:** `pesquisar("sodre")` encontra "Cais do Sodré (Terminal)", sem acento e sem diferença de maiúsculas.

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Run it and see it pass**

Run: `./gradlew :app:testPessoalDebugUnitTest --tests '*RepositorioTest'` → Expected: PASS.

- [ ] **Step 5: Commit**

`feat(data): Room cache, repository and daily refresh`

### Task 10: Mapa no Início

**Files:**
- Create: `app/src/main/kotlin/pt/pixellusitano/paragemcerta/ui/mapa/Mapa.kt`, `CamadasMapa.kt`
- Create: `app/src/main/kotlin/pt/pixellusitano/paragemcerta/ui/inicio/InicioEcra.kt`, `InicioViewModel.kt`, `Localizacao.kt`

**Interfaces:**
- Consumes: os métodos `Repositorio.paragensNaZona`, `veiculos()` e `linhaCm`.
- Produces:
  - `@Composable fun Mapa(paragens: List<Paragem>, veiculos: List<Veiculo>, coresLinhas: Map<String, String>, escuro: Boolean, aoMoverCamara: (minLat: Double, minLon: Double, maxLat: Double, maxLon: Double, zoom: Double) -> Unit, aoTocarParagem: (Paragem) -> Unit, aoTocarVeiculo: (Veiculo) -> Unit)`, um `AndroidView` com `MapView`. As camadas são `GeoJsonSource` + `SymbolLayer`/`CircleLayer`; nunca vistas do Android por marcador.
  - Estilo do mapa:
    - claro: `https://tiles.openfreemap.org/styles/positron`;
    - escuro: `https://tiles.openfreemap.org/styles/dark` se `curl -I` der 200 durante esta task, senão o `positron`;
    - registar no próprio código qual ficou.

**Regras:**
- **Paragens:** só se pedem a partir do zoom 15, e 500 ms depois de a câmara parar.
- **Veículos:** a cada 15 s, só com o ecrã visível (`repeatOnLifecycle(STARTED)`). Os de sinal com mais de 2 min ficam com opacidade 0,4.
- **Tocar num veículo:** mostra a linha, o destino e `"último sinal há N s"`.
- **Primeira abertura:**
  - explica num ecrã próprio porque é que a app pede a localização;
  - pede `ACCESS_FINE_LOCATION` e `ACCESS_COARSE_LOCATION`, só enquanto a app está a ser usada;
  - se a localização for recusada, a câmara vai para Lisboa (38.7223, −9.1393, zoom 13).
- A atribuição do OpenStreetMap fica sempre visível.

- [ ] **Step 1: Write the failing test**

Um teste do `InicioViewModel` com um repositório falso. Uma chamada a `aoMoverCamara` com zoom 14 não pede paragens; com zoom 15,5, pede uma vez, mesmo com 3 movimentos em 300 ms.

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Run it and see it pass**

- [ ] **Step 5: Verificar no emulador ou no telemóvel**

`./gradlew installPessoalDebug` e capturar o ecrã com `adb exec-out screencap -p > inicio.png`. Expected: mapa de Lisboa com paragens a azul e veículos com o número da linha.

- [ ] **Step 6: Commit**

`feat(map): home map with stops and live vehicles`

### Task 11: Painel do Início (favoritas e "perto de ti")

**Files:**
- Create: `app/src/main/kotlin/pt/pixellusitano/paragemcerta/ui/inicio/Painel.kt`, `.../ui/comum/LinhaChegada.kt`, `.../ui/comum/FaixaEstado.kt`
- Test: `app/src/test/kotlin/pt/pixellusitano/paragemcerta/ui/inicio/PainelScreenshotTest.kt`

**Interfaces:**
- Consumes: `favoritaPorOmissao`, `maisProximas`, `Repositorio.chegadas`, `rotuloTempo`, `rotuloAtraso` e `CoresAtraso`.
- Produces:
  - `@Composable fun LinhaChegada(c: Chegada, linha: Linha?, agora: Instant)`: o número da linha na cor oficial (ou neutra), o destino, `rotuloTempo` e `rotuloAtraso` na cor do estado, e o ícone de cadeira de rodas quando `acessivel == true`. A `contentDescription` junta tudo, por exemplo "Linha 728 para Restelo, daqui a 7 minutos, atrasado 3 minutos".
  - `@Composable fun FaixaEstado(e: EstadoChegadas, agora: Instant)`:
    - "Sem ligação — atualizado há N min" quando `semLigacao`;
    - "Tempo real indisponível" quando `tempoRealIndisponivel`;
    - nada nos outros casos.

**O painel:**
- `BottomSheetScaffold` com friso de azulejo.
- 1.º bloco: a favorita por omissão, com as 3 próximas chegadas.
- Depois as outras favoritas (2 chegadas cada).
- Por fim "Perto de ti": até 5 paragens a 800 m, com a primeira chegada de cada.
- **Sem favoritas:** "Toca na ★ de uma paragem para a veres aqui".
- **Sem localização:** "Perto de ti" passa a "Procura uma paragem", que abre a pesquisa.
- **Paragem sem chegadas:** "Não há chegadas nas próximas 2 horas".

- [ ] **Step 1: Write the failing test**

Capturas Roborazzi:
- `painel_claro` e `painel_escuro`, com uma favorita e 3 chegadas, uma de cada estado: A_HORAS, ATRASADO e HORARIO;
- `painel_sem_ligacao`;
- `painel_vazio`;
- `painel_letra200`.

Mais um teste de semântica que confirma que a linha atrasada tem na descrição "atrasado 3 minutos".

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Record, verify and look at the PNGs**

- [ ] **Step 5: Commit**

`feat(home): favourites and nearby panel`

### Task 12: Ecrã da paragem

**Files:**
- Create: `app/src/main/kotlin/pt/pixellusitano/paragemcerta/ui/paragem/ParagemEcra.kt`, `ParagemViewModel.kt`
- Test: `ParagemScreenshotTest.kt`, `ParagemViewModelTest.kt`

**Interfaces:**
- Consumes: `Repositorio.chegadas`, `alertasDaParagem`, `alternarFavorita`, `LinhaChegada` e `FaixaEstado`.
- Produces:
  - navegação `paragem/{chave}`, com a chave `CM:060419` ou `TRANSITOUS:<id>`;
  - `fun intentReportarErro(versao: String, chave: String, nome: String): Intent` (`ACTION_SENDTO` para `mailto:contact@pixellusitano.com`):
    - assunto `"Paragem Certa — erro na paragem <nome>"`;
    - corpo com a versão, a chave e a linha "Descreve o erro:".

**Conteúdo do ecrã:**
- Cabeçalho azul com o nome, o código, ★ e o friso.
- Filtros por linha (chips: "Todas" e uma por linha presente).
- Lista de `LinhaChegada`.
- Alertas ativos com o ícone de gravidade e o texto "Grave", "Aviso" ou "Informação". Tocar abre o texto completo e a imagem, se houver.
- Ícone de cadeira de rodas no cabeçalho quando `acessivel == true`.
- Botão "Reportar erro".

- [ ] **Step 1: Write the failing tests**

- **ViewModel:** escolher o chip "3708" deixa só as chegadas dessa linha; "Todas" repõe tudo.
- **`intentReportarErro`:** o URI é `mailto:contact@pixellusitano.com` e o assunto contém "Cais do Sodré (Terminal)".
- **Capturas:** `paragem_claro` com um alerta AVISO, `paragem_escuro`, `paragem_sem_chegadas` e `paragem_letra200`.

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Run it and see it pass**

- [ ] **Step 5: Commit**

`feat(stop): stop screen with delay colours, filters and alerts`

### Task 13: Pesquisa de paragens e linhas

**Files:**
- Create: `app/src/main/kotlin/pt/pixellusitano/paragemcerta/ui/pesquisa/PesquisaEcra.kt`, `PesquisaViewModel.kt`
- Test: `PesquisaViewModelTest.kt`

**Interfaces:**
- Consumes: `Repositorio.pesquisar`, `linhaCm` e `maisProximas`.
- Produces: `sealed interface ResultadoPesquisa { data class DeParagem(val p: Paragem) : ResultadoPesquisa; data class DeLinha(val l: Linha) : ResultadoPesquisa }`.

**Regras:**
- **Quando pesquisar:** a partir de 2 caracteres, 300 ms depois de parar de escrever.
- **Ordem dos resultados:**
  1. linhas CM cujo `nomeCurto` começa pelo texto;
  2. paragens CM;
  3. paragens Transitous.
- **Tocar numa paragem:** abre o ecrã da paragem.
- **Tocar numa linha CM:**
  - o mapa mostra as paragens dessa linha, via `patterns/{id}` da CM, guardado em cache só nesta sessão;
  - mostra a 1.ª chegada da linha na paragem dela mais próxima.
- O ecrã completo da linha fica para a fase 4.
- **Barra "Para onde vais?":** nesta versão abre esta pesquisa. O planeamento chega na fase 2.

- [ ] **Step 1: Write the failing test**

- Com o texto "37", aparece primeiro `DeLinha(3708)` e depois as paragens.
- Com "s", não há nenhum pedido.
- Com "sod" seguido de "sodr" em 100 ms, há um só pedido.

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Run it and see it pass**

- [ ] **Step 5: Commit**

`feat(search): stops and lines search`

### Task 14: Navegação e separador "Eu"

**Files:**
- Create: `app/src/main/kotlin/pt/pixellusitano/paragemcerta/ui/Navegacao.kt`
- Create: `.../ui/eu/EuEcra.kt`, `.../ui/eu/Definicoes.kt`
- Create: `.../dados/Preferencias.kt` (DataStore)
- Test: `EuScreenshotTest.kt`, `PreferenciasTest.kt`

**Interfaces:**
- Consumes: `TemaParagemCerta(modo)`, `Limites`, `Repositorio.favoritas` e `SobreEcra`.
- Produces:
  - `class Preferencias(context)`, com:
    - `val modo: Flow<ModoTema>` (por omissão SISTEMA);
    - `val limites: Flow<Limites>` (por omissão 2 e 5);
    - `suspend fun definirModo(m: ModoTema)`;
    - `suspend fun definirLimites(l: Limites)`, que rejeita `atrasado < 1` e `muitoAtrasado <= atrasado`.
  - Rotas `@Serializable`: `Inicio`, `Paragem(chave: String)`, `Pesquisa`, `Eu`, `Sobre`.

**O que a navegação tem:**
- **Barra inferior:** na versão 0.1 só tem **Início** e **Eu**. "Rotas" (fase 2) e "Linhas" (fase 4) entram quando existirem; não se mostram separadores vazios.
- **Eu:**
  - favoritas, com reordenação e remoção;
  - Definições: modo claro, escuro ou sistema; limites de atraso;
  - "Sobre e fontes de dados";
  - "Enviar opinião", com email para contact@pixellusitano.com, a versão no corpo e o assunto "Paragem Certa — opinião".
- **Ligação dos limites:** os limites guardados chegam a `estado()` e `rotuloAtraso()` em toda a app.

- [ ] **Step 1: Write the failing tests**

- **Preferências:** `definirLimites(Limites(3, 8))` seguido de `limites.first()` dá `Limites(3, 8)`. `Limites(5, 5)` lança `IllegalArgumentException`.
- **Capturas:** `eu_claro`, `eu_escuro` e `definicoes_claro`.

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Run it and see it pass**

- [ ] **Step 5: Commit**

`feat(ui): bottom navigation, Eu tab and settings`

### Task 15: Atualizador da variante pessoal

**Files:**
- Create: `app/src/pessoal/kotlin/pt/pixellusitano/paragemcerta/atualizar/Atualizador.kt`
- Create: `app/src/play/kotlin/pt/pixellusitano/paragemcerta/atualizar/Atualizador.kt` (vazio: não faz nada)
- Modify: `app/src/pessoal/AndroidManifest.xml` (`REQUEST_INSTALL_PACKAGES`)
- Test: `app/src/testPessoal/kotlin/.../atualizar/AtualizadorTest.kt`

**Interfaces:**
- Consumes: `isNewer` (Task 2) e `Http`.
- Produces: `class Atualizador`, com:
  - `suspend fun verificar(versionCodeAtual: Int): NovaVersao?`: `GET https://api.github.com/repos/DaewonPT/paragem-certa/releases/latest`, usando `tag_name` e o primeiro asset `.apk`.
  - `data class NovaVersao(val tag: String, val urlApk: String, val notas: String)`.
  - `suspend fun instalar(n: NovaVersao)`: descarrega para `cacheDir` e instala com `PackageInstaller` numa sessão.

**Quando corre e o que mostra:**
- Verifica ao abrir a app, no máximo uma vez por dia.
- Se houver versão nova, mostra um cartão: "Há uma versão nova (0.1.1)", com as notas e os botões "Atualizar" e "Agora não".

- [ ] **Step 1: Write the failing test**

- Com o JSON de uma release falsa (`tag_name` "v0.1.1", um asset `paragem-certa-v0.1.1.apk`, nome dado pelo CI) e `versionCodeAtual` 100, devolve `NovaVersao`.
- Com 101, devolve `null`.
- Com 404 (ainda não há releases), devolve `null` sem lançar erro.

- [ ] **Step 2: Run it and see it fail**

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Run it and see it pass**

- [ ] **Step 5: Commit**

`feat(update): self-updater for the personal flavour`

### Task 16: Versão 0.1.0

**Files:**
- Modify: `PATCHNOTES.md`
- Modify: `app/build.gradle.kts` (confirmar `versionName "0.1.0"` e `versionCode 100`)

- [ ] **Step 1: Correr tudo**

Run: `./gradlew :core:test :app:testPessoalDebugUnitTest :app:verifyRoborazziPessoalDebug lintPessoalDebug assemblePessoalRelease` → Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Escrever a nota de versão**

Escrever em PT-PT a entrada "0.1.0 — Chegadas", no topo do `PATCHNOTES.md`. Fazer commit: `release: 0.1.0`.

- [ ] **Step 3: Pedir o "publica"**

Mostrar ao autor a nota e as capturas, e pedir o "publica". Só depois: `git tag v0.1.0 && git push origin main v0.1.0`. Esperar que o CI anexe o APK à release.

- [ ] **Step 4: Instalar e avaliar**

O autor instala a partir da release e usa a app durante 7 dias. A fase 1 só fecha quando:
- o autor não abriu a Moovit para ver chegadas;
- as chegadas da favorita aparecem sem toques;
- não há nenhum erro bloqueante por resolver.

---

## Versões fixadas (estudo de 27/10/2026, verificado por um segundo agente)

| Peça | Coordenada | Versão |
|---|---|---|
| Gradle (wrapper) | `gradle-9.8.2-bin.zip`, sha256 `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607` | 9.8.2 (plano B: 9.7.0) |
| Android Gradle Plugin | `com.android.application` | 9.4.1 |
| Kotlin | `org.jetbrains.kotlin.jvm` (em `:core`); em `:app` usa-se o Kotlin incluído no AGP 9, **sem** aplicar `org.jetbrains.kotlin.android` | 2.4.21 |
| Compose compiler | `org.jetbrains.kotlin.plugin.compose` | 2.4.21 |
| Serialization (plugin) | `org.jetbrains.kotlin.plugin.serialization` | 2.4.21 |
| KSP | `com.google.devtools.ksp` | 2.3.12 |
| Compose BOM | `androidx.compose:compose-bom` (Material 3 1.4.0) | 2026.09.00 |
| Activity Compose | `androidx.activity:activity-compose` | 1.13.0 |
| Lifecycle (runtime e viewmodel compose) | `androidx.lifecycle:lifecycle-*-compose` | 2.11.0 |
| Navigation Compose | `androidx.navigation:navigation-compose` (rotas `@Serializable`) | 2.10.2 |
| MapLibre | `org.maplibre.gl:android-sdk-vulkan-opengl` (Maven Central; escolhe Vulkan ou OpenGL sozinho) | 13.7.1 |
| Room | `androidx.room:room-runtime`, `room-ktx` + `ksp(room-compiler)`, plugin `androidx.room` | 2.8.5 |
| WorkManager | `androidx.work:work-runtime` | 2.12.0 |
| DataStore | `androidx.datastore:datastore-preferences` | 1.3.0 |
| AndroidX Core | `androidx.core:core` | 1.19.1 |
| OkHttp | `com.squareup.okhttp3:okhttp` | 5.5.1 |
| kotlinx.serialization JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core`, `-android`, `-test` | 1.11.0 |
| Roborazzi | plugin `io.github.takahirom.roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule` | 1.77.0 |
| Robolectric | `org.robolectric:robolectric` | 4.17 |
| JUnit 4 | `junit:junit` | 4.13.2 |
| Turbine | `app.cash.turbine:turbine` | 1.2.1 |
| JDK | Temurin 21 (Windows: 21.0.12.1+1; CI: `setup-java` 21) | 21 |
| Letra | Nunito (google/fonts, `ofl/nunito`, `Nunito[wght].ttf` → `res/font/nunito.ttf`, licença OFL) | — |

**Regras que estas versões impõem:**

- **compileSdk 37** e **targetSdk 36**. As versões estáveis do Compose 1.12, do Lifecycle 2.11, do Navigation 2.10 e do Core 1.19 exigem compilar com o 37. O targetSdk 36 é o que a Play exige.
- **Plugins:** declaram-se todos no `build.gradle.kts` da raiz com `apply false`, para o Kotlin 2.4.21 substituir o 2.2.10 que o AGP 9 traz. Usa-se `kotlin { compilerOptions { } }` (já não existe `android.kotlinOptions`) e KSP, nunca kapt.
- **AGP 9:**
  - `flavorDimensions += "distribuicao"` e `dimension = "distribuicao"`;
  - `buildFeatures { buildConfig = true }`, que o atualizador usa;
  - `androidComponents { onVariants { } }` em vez de `applicationVariants`.
- **Bytecode 17** nos dois módulos. Não declarar `jvmToolchain`.
- **Memória do Gradle:** `org.gradle.jvmargs=-Xmx4g` em `gradle.properties`.
- **Capturas Roborazzi:**
  - as de referência gravam-se e verificam-se **só no CI** (`ubuntu-24.04`), porque as gravadas no Windows dão píxeis diferentes;
  - no PC usa-se `compareRoborazziPessoalDebug` só para ver;
  - pasta: `roborazzi { outputDir.set(file("src/test/screenshots")) }`;
  - testes com `@GraphicsMode(GraphicsMode.Mode.NATIVE)` e `isIncludeAndroidResources = true`.
- **CI pronto em `docs/superpowers/plans/anexos/android.yml`:**
  - copia-se tal como está;
  - segredos `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD` e `ANDROID_KEY_ALIAS` (chave PKCS12, uma só palavra-passe);
  - o APK da release chama-se `paragem-certa-<tag>.apk`.
- **Assinatura:** passo a passo em `anexos/assinatura.md`. Em Windows, o `keytool` corre-se em PowerShell, e o base64 sem quebras de linha (`[Convert]::ToBase64String`).
- **`.gitattributes`:**
  - `* text=auto`, `gradlew text eol=lf`, `*.bat text eol=crlf`, `*.jar binary` e `*.png binary`;
  - `git update-index --chmod=+x gradlew`;
  - o `gradle/wrapper/gradle-wrapper.jar` entra no repositório.
