package com.mutkuensert.seslendirmen.data.preferences

import android.content.Context
import com.mutkuensert.seslendirmen.domain.model.LastReadPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastReadPositionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(fileName: String): LastReadPosition? {
        if (preferences.getString(KEY_FILE_NAME, null) != fileName) return null
        if (!preferences.contains(KEY_CHUNK_ID) || !preferences.contains(KEY_CHUNK_COUNT)) {
            return null
        }
        return LastReadPosition(
            fileName = fileName,
            chunkId = preferences.getLong(KEY_CHUNK_ID, 0L),
            chunkCount = preferences.getInt(KEY_CHUNK_COUNT, 0),
        )
    }

    fun save(position: LastReadPosition) {
        preferences.edit()
            .putString(KEY_FILE_NAME, position.fileName)
            .putLong(KEY_CHUNK_ID, position.chunkId)
            .putInt(KEY_CHUNK_COUNT, position.chunkCount)
            .apply()
    }

    fun clear(fileName: String) {
        if (preferences.getString(KEY_FILE_NAME, null) != fileName) return
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "reader_position"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_CHUNK_ID = "chunk_id"
        const val KEY_CHUNK_COUNT = "chunk_count"
    }
}
