plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versao = "0.1.0"

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
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core)
}
