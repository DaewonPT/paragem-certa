// Todos os plugins declarados aqui com apply false: assim o Kotlin 2.4.21 substitui o 2.2.10 que o AGP 9 traz.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
