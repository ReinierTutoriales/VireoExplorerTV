package com.reiniertutoriales.vireoexplorertv

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.explorerDataStore by preferencesDataStore(name = "explorer_settings")
internal data class ExplorerSettings(val grid: Boolean = false, val sort: Int = 0)

internal class ExplorerPreferences(context: Context) {
    private val store = context.applicationContext.explorerDataStore
    private val grid = booleanPreferencesKey("grid")
    private val sort = intPreferencesKey("sort")
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ExplorerSettings(it[grid] ?: false, (it[sort] ?: 0).coerceIn(0, 3)) }
    suspend fun toggleGrid() { store.edit { it[grid] = !(it[grid] ?: false) } }
    suspend fun nextSort() { store.edit { it[sort] = ((it[sort] ?: 0).coerceIn(0, 3) + 1) % 4 } }
}
