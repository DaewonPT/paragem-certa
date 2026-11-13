import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

// Os testes leem o que os geradores do worker escrevem, os dois config.json e o worker/src/relatos.ts: sem isto, o Gradle dava
// o :core:test como feito quando só esses ficheiros mudam.
tasks.test {
    inputs.dir("../worker/ferramentas/gerado")
    inputs.files("../worker/public/dados/config.json", "../app/src/main/assets/config.json", "../worker/src/relatos.ts")
}
