plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core"))
    api(libs.jcefmaven)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlin.logging)
}
