import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val versao = "0.5.5"

// Chave de lançamento: no CI vem dos segredos do GitHub; no PC vem de ../Chave Privada/keystore.properties (fora do repositório).
val propsChave = rootProject.file("../Chave Privada/keystore.properties")
val chaveLocal = Properties().apply { if (propsChave.isFile) propsChave.inputStream().use(::load) }
fun valorChave(env: String, prop: String): String? = System.getenv(env) ?: chaveLocal.getProperty(prop)

// O id do cliente Web da Google não está no repositório: no CI vem dos segredos do GitHub, no PC de local.properties.
// Sem ele a Conta (no Menu e o ícone no Início) fica escondida a quem não tiver sessão aberta.
val propsLocais = Properties().apply { rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load) }
val clienteWebGoogle = (System.getenv("GOOGLE_WEB_CLIENT_ID") ?: propsLocais.getProperty("GOOGLE_WEB_CLIENT_ID") ?: "").trim()

android {
    namespace = "pt.pixellusitano.paragemcerta"
    compileSdk = 37

    defaultConfig {
        applicationId = "pt.pixellusitano.paragemcerta"
        minSdk = 26
        targetSdk = 36
        versionName = versao
        // Mesma regra que versionCodeFromTag no :core: major*10000 + minor*100 + patch.
        versionCode = versao.split('.').map(String::toInt).let { (a, b, c) -> a * 10000 + b * 100 + c }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$clienteWebGoogle\"")
        // Só telemóveis ARM: as bibliotecas do mapa para x86 (emuladores, PCs) duplicavam o tamanho do APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("release") {
            val caminho = System.getenv("ANDROID_KEYSTORE_FILE")
                ?: chaveLocal.getProperty("storeFile")?.let { propsChave.resolveSibling(it).path }
            if (caminho != null) {
                storeFile = file(caminho)
                storePassword = valorChave("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = valorChave("ANDROID_KEY_ALIAS", "keyAlias")
                keyPassword = storePassword // PKCS12: uma só palavra-passe
            }
        }
    }

    buildTypes {
        release {
            // Sem chave (fork, PR, PC novo) compila na mesma, mas sai app-*-release-unsigned.apk.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    flavorDimensions += "distribuicao"
    productFlavors {
        create("pessoal") {
            dimension = "distribuicao"
            applicationIdSuffix = ".pessoal"
        }
        create("play") {
            dimension = "distribuicao"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                // O Robolectric mexe em java.io.FileDescriptor por reflexão; no JDK 21 isso tem de ser aberto.
                it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
                // O ContaTest lê worker/src/favoritos.ts: sem isto, o Gradle dava os testes da app como feitos quando só esse ficheiro muda.
                it.inputs.file("../worker/src/favoritos.ts")
            }
        }
    }

    // Os testes da app usam as mesmas respostas gravadas que os do :core.
    sourceSets.named("test") { resources.directories.add("../core/src/test/resources") }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.maplibre)
    implementation(libs.androidx.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.navigation.compose)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

room {
    schemaDirectory("$projectDir/schemas")
}
