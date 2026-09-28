package com.mutkuensert.narrator.feature.reader.data.preferences

import android.content.Context
import android.util.Base64
import com.mutkuensert.narrator.feature.reader.domain.model.LastReadPosition
import com.mutkuensert.narrator.feature.reader.domain.repository.LastReadPositionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastReadPositionStore @Inject constructor(
    @ApplicationContext context: Context,
) : LastReadPositionRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _positions = MutableStateFlow(loadPositions())
    override val positions: StateFlow<List<LastReadPosition>> = _positions.asStateFlow()

    override fun read(fileName: String): LastReadPosition? {
        val chunkIdKey = positionKey(fileName, CHUNK_ID_SUFFIX)
        val chunkCountKey = positionKey(fileName, CHUNK_COUNT_SUFFIX)
        if (!preferences.contains(chunkIdKey) || !preferences.contains(chunkCountKey)) {
            return null
        }
        return LastReadPosition(
            fileName = fileName,
            chunkId = preferences.getLong(chunkIdKey, 0L),
            chunkCount = preferences.getInt(chunkCountKey, 0),
        )
    }

    @Synchronized
    override fun save(position: LastReadPosition) {
        val fileNames = storedFileNames().apply { add(position.fileName) }
        val wasSaved = preferences.edit()
            .putStringSet(KEY_FILE_NAMES, fileNames)
            .putLong(positionKey(position.fileName, CHUNK_ID_SUFFIX), position.chunkId)
            .putInt(positionKey(position.fileName, CHUNK_COUNT_SUFFIX), position.chunkCount)
            .commit()
        if (wasSaved) publishPositions()
    }

    @Synchronized
    override fun clear(fileName: String) {
        val fileNames = storedFileNames().apply { remove(fileName) }
        val wasCleared = preferences.edit()
            .putStringSet(KEY_FILE_NAMES, fileNames)
            .remove(positionKey(fileName, CHUNK_ID_SUFFIX))
            .remove(positionKey(fileName, CHUNK_COUNT_SUFFIX))
            .commit()
        if (wasCleared) publishPositions()
    }

    private fun publishPositions() {
        _positions.value = loadPositions()
    }

    private fun loadPositions(): List<LastReadPosition> =
        storedFileNames()
            .mapNotNull(::read)
            .sortedBy { it.fileName.lowercase() }

    private fun storedFileNames(): MutableSet<String> =
        preferences.getStringSet(KEY_FILE_NAMES, emptySet()).orEmpty().toMutableSet()

    private companion object {
        const val PREFERENCES_NAME = "reader_position"
        const val KEY_FILE_NAMES = "file_names"
        const val POSITION_KEY_PREFIX = "position."
        const val CHUNK_ID_SUFFIX = ".chunk_id"
        const val CHUNK_COUNT_SUFFIX = ".chunk_count"

        fun positionKey(fileName: String, suffix: String): String {
            val encodedFileName = Base64.encodeToString(
                fileName.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE,
            )
            return "$POSITION_KEY_PREFIX$encodedFileName$suffix"
        }
    }
}
