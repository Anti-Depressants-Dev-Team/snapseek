import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":browser"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlin.logging)
    implementation(libs.sqlite.jdbc)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "dev.snapseek.app.MainKt"

        jvmArgs += listOf(
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "SnapSeek"
            packageVersion = project.version.toString()
            description = "Browse Pinterest, Pixiv and more, and save images in the format you want."
            vendor = "Yabosen"
            licenseFile.set(rootProject.file("LICENSE"))

            // Modules jlink can't infer from the classpath.
            modules("java.net.http", "java.sql", "java.naming", "java.management", "jdk.unsupported", "jdk.crypto.ec")

            windows {
                iconFile.set(rootProject.file("branding/snapseek.ico"))
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Keep this stable forever so newer MSIs upgrade in place instead of installing side by side.
                upgradeUuid = "8d2b8b3e-8a6d-4f2a-9a7d-1c5f9a2e6b10"
            }
            macOS {
                bundleID = "com.yabosen.snapseek"
            }
            linux {
                iconFile.set(rootProject.file("branding/snapseek.png"))
            }
        }

        buildTypes.release.proguard {
            // Chromium's Java bindings are loaded reflectively from native code; shrinking them breaks startup.
            isEnabled.set(false)
        }
    }
}
