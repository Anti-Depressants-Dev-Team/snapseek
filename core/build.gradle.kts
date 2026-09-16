plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    // kotlin-logging talks to slf4j but leaves bringing it to whoever uses us, and these classes log from their
    // constructors, so the API has to be here or an app crashes the moment it builds one.
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
}
