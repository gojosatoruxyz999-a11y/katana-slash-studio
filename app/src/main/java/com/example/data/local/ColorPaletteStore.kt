package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.roninPaletteDataStore: DataStore<Preferences> by preferencesDataStore(name = "ronin_palettes")

data class NeonPalettePreset(
    val id: String,
    val name: String,
    val colors: List<Long>,
    val isBuiltIn: Boolean = false
)

object DefaultNeonPalettes {
    const val PRESET_FAVORITES = "favorites"
    const val PRESET_RONIN_CYBER = "ronin_cyber"
    const val PRESET_BLOOD_SLASH = "blood_slash"
    const val PRESET_TOKYO_ARCADE = "tokyo_arcade"

    val defaultFavoriteColors: List<Long> = listOf(
        0xFFF8FAFC, // Pure White
        0xFFC084FC, // Neon Purple Bright
        0xFFA855F7, // Neon Purple
        0xFF22D3EE, // Cyber Cyan
        0xFFF43F5E, // Crimson Rose
        0xFFF59E0B, // Solar Amber
        0xFF10B981, // Plasma Emerald
        0xFF0A0A12  // Void Ink
    )

    val builtInPresets: List<NeonPalettePreset> = listOf(
        NeonPalettePreset(
            id = PRESET_RONIN_CYBER,
            name = "Ronin Cyber",
            colors = listOf(
                0xFFC084FC,
                0xFFA855F7,
                0xFF7C3AED,
                0xFF22D3EE,
                0xFF38BDF8,
                0xFFF8FAFC,
                0xFF0A0A12
            ),
            isBuiltIn = true
        ),
        NeonPalettePreset(
            id = PRESET_BLOOD_SLASH,
            name = "Blood Slash",
            colors = listOf(
                0xFFF43F5E,
                0xFFFB7185,
                0xFFE11D48,
                0xFFC084FC,
                0xFFF59E0B,
                0xFFF8FAFC,
                0xFF0A0A12
            ),
            isBuiltIn = true
        ),
        NeonPalettePreset(
            id = PRESET_TOKYO_ARCADE,
            name = "Tokyo Arcade",
            colors = listOf(
                0xFFEC4899,
                0xFFD946EF,
                0xFF22D3EE,
                0xFF34D399,
                0xFFFACC15,
                0xFF818CF8,
                0xFFF8FAFC
            ),
            isBuiltIn = true
        )
    )
}

class ColorPaletteStore(private val context: Context) {

    private val dataStore = context.applicationContext.roninPaletteDataStore

    private object Keys {
        val FAVORITE_COLORS = stringPreferencesKey("favorite_neon_colors")
        val ACTIVE_PRESET_ID = stringPreferencesKey("active_palette_preset_id")
        val CUSTOM_PRESETS = stringPreferencesKey("custom_palette_presets")
    }

    val favoriteColors: Flow<List<Long>> = dataStore.data.map { prefs ->
        decodeColorList(prefs[Keys.FAVORITE_COLORS]) ?: DefaultNeonPalettes.defaultFavoriteColors
    }

    val activePresetId: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PRESET_ID]?.takeIf { it.isNotBlank() } ?: DefaultNeonPalettes.PRESET_FAVORITES
    }

    val customPresets: Flow<List<NeonPalettePreset>> = dataStore.data.map { prefs ->
        decodeCustomPresets(prefs[Keys.CUSTOM_PRESETS])
    }

    suspend fun saveFavoriteColor(colorArgb: Long) {
        val normalized = normalizeOpaqueArgb(colorArgb)
        dataStore.edit { prefs ->
            val current = decodeColorList(prefs[Keys.FAVORITE_COLORS]) ?: DefaultNeonPalettes.defaultFavoriteColors
            if (current.any { normalizeOpaqueArgb(it) == normalized }) return@edit
            val updated = (listOf(normalized) + current).take(24)
            prefs[Keys.FAVORITE_COLORS] = encodeColorList(updated)
        }
    }

    suspend fun removeFavoriteColor(colorArgb: Long) {
        val normalized = normalizeOpaqueArgb(colorArgb)
        dataStore.edit { prefs ->
            val current = decodeColorList(prefs[Keys.FAVORITE_COLORS]) ?: DefaultNeonPalettes.defaultFavoriteColors
            val updated = current.filterNot { normalizeOpaqueArgb(it) == normalized }
            if (updated.isNotEmpty()) {
                prefs[Keys.FAVORITE_COLORS] = encodeColorList(updated)
            }
        }
    }

    suspend fun selectPreset(presetId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PRESET_ID] = presetId
        }
    }

    suspend fun saveCustomPreset(name: String, colors: List<Long>): NeonPalettePreset {
        val cleanColors = colors.map(::normalizeOpaqueArgb).distinct().ifEmpty { DefaultNeonPalettes.defaultFavoriteColors }
        val newPreset = NeonPalettePreset(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name.trim().ifBlank { "Neon Mix" }.take(24),
            colors = cleanColors,
            isBuiltIn = false
        )
        dataStore.edit { prefs ->
            val current = decodeCustomPresets(prefs[Keys.CUSTOM_PRESETS])
            val updated = (listOf(newPreset) + current).take(8)
            prefs[Keys.CUSTOM_PRESETS] = encodeCustomPresets(updated)
            prefs[Keys.ACTIVE_PRESET_ID] = newPreset.id
        }
        return newPreset
    }

    suspend fun deleteCustomPreset(presetId: String) {
        dataStore.edit { prefs ->
            val current = decodeCustomPresets(prefs[Keys.CUSTOM_PRESETS])
            val updated = current.filterNot { it.id == presetId }
            prefs[Keys.CUSTOM_PRESETS] = encodeCustomPresets(updated)
            if (prefs[Keys.ACTIVE_PRESET_ID] == presetId) {
                prefs[Keys.ACTIVE_PRESET_ID] = DefaultNeonPalettes.PRESET_FAVORITES
            }
        }
    }

    suspend fun resetDefaultFavorites() {
        dataStore.edit { prefs ->
            prefs[Keys.FAVORITE_COLORS] = encodeColorList(DefaultNeonPalettes.defaultFavoriteColors)
            prefs[Keys.ACTIVE_PRESET_ID] = DefaultNeonPalettes.PRESET_FAVORITES
        }
    }

    private fun normalizeOpaqueArgb(argb: Long): Long {
        return 0xFF000000L or (argb and 0x00FFFFFFL)
    }

    private fun encodeColorList(colors: List<Long>): String {
        return JSONArray().apply {
            colors.forEach { put(it) }
        }.toString()
    }

    private fun decodeColorList(json: String?): List<Long>? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    add(normalizeOpaqueArgb(arr.getLong(i)))
                }
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun encodeCustomPresets(presets: List<NeonPalettePreset>): String {
        return JSONArray().apply {
            presets.forEach { preset ->
                put(JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("colors", JSONArray().apply { preset.colors.forEach { put(it) } })
                })
            }
        }.toString()
    }

    private fun decodeCustomPresets(json: String?): List<NeonPalettePreset> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { continue }
                    val name = obj.optString("name", "Neon Mix").ifBlank { "Neon Mix" }
                    val colorsArr = obj.optJSONArray("colors") ?: continue
                    val colors = buildList {
                        for (c in 0 until colorsArr.length()) {
                            add(normalizeOpaqueArgb(colorsArr.getLong(c)))
                        }
                    }
                    if (colors.isNotEmpty()) {
                        add(NeonPalettePreset(id = id, name = name, colors = colors, isBuiltIn = false))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
