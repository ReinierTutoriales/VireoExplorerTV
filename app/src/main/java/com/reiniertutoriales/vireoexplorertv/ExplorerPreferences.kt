package com.reiniertutoriales.vireoexplorertv

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.explorerDataStore by preferencesDataStore(name = "explorer_settings")
internal data class ExplorerSettings(val grid: Boolean = true, val sort: Int = 0, val favorites: List<String> = emptyList(), val recent: List<String> = emptyList())

internal class ExplorerPreferences(context: Context) {
    private val store = context.applicationContext.explorerDataStore
    private val grid = booleanPreferencesKey("grid")
    private val sort = intPreferencesKey("sort")
    private val favorites = stringPreferencesKey("favorite_folders")
    private val recent = stringPreferencesKey("recent_folders")
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ExplorerSettings(it[grid] ?: true, (it[sort] ?: 0).coerceIn(0, 3), decode(it[favorites]), decode(it[recent])) }
    suspend fun toggleGrid() { store.edit { it[grid] = !(it[grid] ?: true) } }
    suspend fun setGrid(enabled: Boolean) { store.edit { it[grid] = enabled } }
    suspend fun setSort(index: Int) { store.edit { it[sort] = index.coerceIn(0, 3) } }
    suspend fun nextSort() { store.edit { it[sort] = ((it[sort] ?: 0).coerceIn(0, 3) + 1) % 4 } }
    suspend fun toggleFavorite(uri: String) {
        store.edit { prefs ->
            val values = decode(prefs[favorites])
            prefs[favorites] = JSONArray(if (uri in values) values - uri else (listOf(uri) + values).take(50)).toString()
        }
    }
    suspend fun recordFolder(uri: String) {
        store.edit { prefs -> prefs[recent] = JSONArray((listOf(uri) + decode(prefs[recent]).filterNot { it == uri }).take(20)).toString() }
    }
    suspend fun clearRecent() { store.edit { it.remove(recent) } }
    private fun decode(value: String?): List<String> = try {
        val array = JSONArray(value ?: "[]")
        (0 until minOf(array.length(), 50)).map { array.getString(it) }.distinct()
    } catch (_: org.json.JSONException) { emptyList() }
}
