package dev.snapseek.app

/**
 * What version this build is. Filled in at build time from the same property the installers are stamped with, so
 * the update check compares like with like instead of a number somebody forgot to bump.
 */
object BuildInfo {
    val VERSION: String = BuildInfo::class.java.`package`?.implementationVersion
        ?: System.getProperty("snapseek.version")
        ?: "2.0.0"
}
