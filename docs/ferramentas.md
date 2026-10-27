# Ferramentas no PC

Instaladas a 27/10/2026, com autorização do autor. Nenhuma precisou de administrador.

| Ferramenta | Pasta | Origem | Verificação |
|---|---|---|---|
| Java (Temurin 21.0.12.1+1) | `C:\Java\jdk-21` | github.com/adoptium | sha256 `f9d6e191…8b4e` |
| Android SDK — ferramentas de linha de comandos 23.0 | `C:\Android\sdk\cmdline-tools\latest` | dl.google.com | sha1 `57d04f2d…4e9d` |
| Android SDK — `platform-tools` 37.0.1, `platforms/android-37.0`, `build-tools/36.0.0` | `C:\Android\sdk` | sdkmanager | `sdkmanager.bat --list_installed` |
| Gradle 9.8.2 (só para criar o `gradlew`) | `C:\Gradle\gradle-9.8.2` | services.gradle.org | sha256 `21bd5da2…9607` |

**Variáveis do utilizador:**

- `JAVA_HOME=C:\Java\jdk-21` e `ANDROID_HOME=C:\Android\sdk`.
- No `Path`: `C:\Java\jdk-21\bin`, `C:\Android\sdk\platform-tools` e `C:\Android\sdk\cmdline-tools\latest\bin`.

Só valem em programas abertos depois da instalação.

**Para repetir noutro PC:**

1. Descarregar os três ZIP e confirmar as somas.
2. Descompactar nestas pastas. A pasta `cmdline-tools` de dentro do ZIP passa a chamar-se `latest`.
3. Definir as variáveis.
4. Correr `sdkmanager.bat "platform-tools" "platforms/android-37.0" "build-tools/36.0.0"`.

As versões novas do sdkmanager usam `/` em vez de `;` nos nomes dos pacotes.
