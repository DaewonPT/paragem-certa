// app/build.gradle.kts (AGP 9.4.x, Kotlin DSL). Only the signing parts are shown.
// The import must be at the very top of the file, before plugins { }.
import java.util.Properties

// Release key: on CI from env vars (GitHub secrets); locally from ../Chave Privada/keystore.properties,
// a sibling of the repo folder. It is never committed.
val localKeyProps = rootProject.file("../Chave Privada/keystore.properties")
val localKey = Properties().apply { if (localKeyProps.isFile) localKeyProps.inputStream().use(::load) }
fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: localKey.getProperty(prop)

android {
    // namespace, compileSdk = 36, defaultConfig, flavorDimensions + productFlavors { pessoal / play } ...

    signingConfigs {
        create("release") {
            val path = System.getenv("ANDROID_KEYSTORE_FILE")
                ?: localKey.getProperty("storeFile")?.let { localKeyProps.resolveSibling(it).path }
            if (path != null) {
                storeFile = file(path)
                storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
                keyPassword = storePassword // PKCS12: store and key share one password
            }
        }
    }

    buildTypes {
        release {
            // No key available (a fork, a PR, a fresh PC): the build still works, producing app-*-release-unsigned.apk.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
}

/* ../Chave Privada/keystore.properties (outside the repo):
storeFile=paragem-certa-release.jks
storePassword=...
keyAlias=paragem-certa
*/

=====
A. CRIAR A CHAVE (uma só vez, no PowerShell, depois de instalar o JDK 21)
   $dir = "$env:USERPROFILE\Desktop\PROJETOS\Paragem Certa\Chave Privada"
   New-Item -ItemType Directory -Force $dir | Out-Null
   keytool -genkeypair -v -storetype PKCS12 -keystore "$dir\paragem-certa-release.jks" -alias paragem-certa -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Pixel Lusitano, O=Pixel Lusitano, C=PT"
   - O keytool pede a palavra-passe duas vezes, e o que escreves não aparece no ecrã. Usa uma frase longa (20 ou mais caracteres) e guarda-a num gestor de palavras-passe.
   - Faz isto no PowerShell e não no Git Bash, onde o keytool pode mostrar a palavra-passe ou não conseguir ler o teclado.
   - O texto "CN=Pixel Lusitano..." fica visível dentro de cada APK, por isso nunca ponhas aí o nome real.
   - 10000 dias são cerca de 27 anos. A Play exige que a chave seja válida para além de 22/10/2033.

B. FICHEIRO keystore.properties (na MESMA pasta "Chave Privada", ao lado da pasta codigo e fora do repositório)
   Abre o Bloco de Notas e escreve:
     storeFile=paragem-certa-release.jks
     storePassword=A_TUA_PALAVRA_PASSE
     keyAlias=paragem-certa
   Usa Ficheiro > Guardar como, escolhe "Todos os ficheiros" e dá o nome keystore.properties (sem .txt) dentro de "Chave Privada".
   Usa só o nome do ficheiro, sem caminho, porque as barras "\" num .properties teriam de ser escritas a dobrar.

C. IMPRESSÕES DIGITAIS (servem para a consola da Google)
   keytool -list -v -keystore "$dir\paragem-certa-release.jks" -alias paragem-certa
   Copia a linha "SHA1:" para a consola da Google (pt.pixellusitano.paragemcerta.pessoal). Guarda também a "SHA256:", porque é o mesmo valor que o GitHub Actions mostra no passo "apksigner verify".

D. CÓPIA DE SEGURANÇA (obrigatória)
   Copia a pasta "Chave Privada" para uma pen USB e para uma nuvem cifrada (por exemplo o Proton Drive).
   Se perderes o .jks ou a palavra-passe, nenhum telemóvel consegue atualizar a variante pessoal: é preciso desinstalar a app e perdem-se os dados locais.

E. COPIAR A CHAVE EM BASE64 (PowerShell)
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$dir\paragem-certa-release.jks")) | Set-Clipboard
   O texto fica na área de transferência. Não uses "certutil -encode", porque acrescenta linhas BEGIN/END que estragam a descodificação.

F. CRIAR OS SEGREDOS NO GITHUB.COM (clique a clique)
   1. Abre https://github.com e entra na tua conta.
   2. Abre o repositório da Paragem Certa.
   3. Na barra de separadores por baixo do nome do repositório (Code, Issues, Pull requests, Actions...), clica em "Settings" (ícone de roda dentada). Se não o vires, clica em "..." no fim dessa barra e depois em "Settings".
   4. Na coluna da esquerda, na secção "Security", clica em "Secrets and variables" e depois em "Actions".
   5. No separador "Secrets", clica no botão verde "New repository secret".
   6. Em "Name" escreve ANDROID_KEYSTORE_BASE64. Em "Secret" cola com Ctrl+V o texto do passo E. Clica em "Add secret".
   7. Clica outra vez em "New repository secret". Em "Name" escreve ANDROID_KEYSTORE_PASSWORD e em "Secret" a palavra-passe da chave. Clica em "Add secret".
   8. Mais uma vez: em "Name" escreve ANDROID_KEY_ALIAS e em "Secret" escreve paragem-certa. Clica em "Add secret".
   9. Confirma que aparecem os 3 nomes em "Repository secrets". O GitHub nunca volta a mostrar os valores. Para mudar um, clica no lápis ("Update").
   10. Limpa a área de transferência: carrega em Win+V e clica em "Limpar tudo".
   11. Em Settings > Actions > General > "Workflow permissions", deixa "Read repository contents and packages permissions". O workflow só pede escrita no trabalho que publica a versão.
   Só são precisos 3 segredos, porque numa chave PKCS12 a palavra-passe da chave é igual à do ficheiro.

   Alternativa sem cliques, se o gh já tiver sessão iniciada. No PowerShell, dentro da pasta codigo:
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$dir\paragem-certa-release.jks")) | gh secret set ANDROID_KEYSTORE_BASE64
   gh secret set ANDROID_KEYSTORE_PASSWORD   (pede o valor)
   gh secret set ANDROID_KEY_ALIAS --body paragem-certa

G. PUBLICAR UMA VERSÃO (só depois de o autor dizer "publica")
   Sobe o versionCode, põe o versionName igual à etiqueta, faz commit e depois:
   git tag v0.1.0
   git push origin v0.1.0
   No separador "Actions" aparece a execução. No fim, o separador "Releases" mostra paragem-certa-v0.1.0.apk.

H. COMPILAR ASSINADO NO PC (opcional)
   ./gradlew :app:assemblePessoalRelease
   O APK fica em app/build/outputs/apk/pessoal/release/app-pessoal-release.apk e é assinado com o keystore.properties do passo B.