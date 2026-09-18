plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
}

/**
 * The version every installer is stamped with. A release build passes the tag in (-PappVersion=2.1.0); a local
 * build gets the fallback, so nothing has to be edited to build one.
 */
val appVersion: String = (findProperty("appVersion") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "2.0.0"

allprojects {
    group = "dev.snapseek"
    version = appVersion
}
