package dev.snapseek.core.settings

import dev.snapseek.core.model.Settings
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Single source of truth for [Settings]. Reads once at construction, writes atomically on every change
 * (temp file, then rename) so a crash mid-write never leaves a half-written settings.json behind.
 */
class SettingsStore(
    private val file: Path,
    private val json: Json = defaultJson,
) {
    private val log = KotlinLogging.logger {}
    private val _settings = MutableStateFlow(load())

    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        save(next)
    }

    private fun load(): Settings {
        if (!file.exists()) return Settings()
        return try {
            json.decodeFromString<Settings>(file.readText())
        } catch (e: Exception) {
            log.warn(e) { "settings.json could not be read; keeping a copy as settings.json.bad and starting fresh" }
            runCatching { file.moveTo(file.resolveSibling("settings.json.bad"), overwrite = true) }
            Settings()
        }
    }

    private fun save(settings: Settings) {
        file.parent?.createDirectories()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(json.encodeToString(settings))
        tmp.moveTo(file, overwrite = true)
    }

    companion object {
        val defaultJson: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
