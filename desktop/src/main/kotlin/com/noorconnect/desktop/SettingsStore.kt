package com.noorconnect.desktop

import com.noorconnect.domain.model.ModerationSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SettingsStore(
    filePath: String = Paths.get(System.getProperty("user.home"), ".noorconnect", "settings.json").toString(),
) {
    private val path: Path = Paths.get(filePath)

    fun load(): ModerationSettings {
        if (!Files.exists(path)) return ModerationSettings()
        return runCatching {
            Json.decodeFromString<ModerationSettings>(Files.readString(path))
        }.getOrDefault(ModerationSettings())
    }

    fun save(settings: ModerationSettings) {
        val parent = path.parent ?: return
        Files.createDirectories(parent)
        Files.writeString(path, Json.encodeToString(settings))
    }
}
