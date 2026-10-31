/*
 * Tangent Lyrics - Turn your local music files into shareable lyric cards.
 * Copyright (C) 2026 HeheJuice
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
 
package com.HeheJuice.TangentLyrics.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

data class PickedMusic(
    val uri: String,
    val albumArt: Bitmap?,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val lyrics: String?
)

object MusicSession {
    private const val PREFS_NAME = "music_cache"
    private const val KEY_URI = "uri"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_DURATION = "duration"
    private const val KEY_LYRICS = "lyrics"
    private const val ART_FILE_NAME = "cached_album_art.png"

    @Volatile
    var current: PickedMusic? = null
        private set

    /**
     * 設置當前音樂並立即存入本地緩存。
     */
    fun set(context: Context, music: PickedMusic) {
        current = music
        saveToCache(context, music)
    }

    /**
     * 從本地緩存加載上次選擇的音樂。
     */
    fun loadFromCache(context: Context) {
        if (current != null) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uri = prefs.getString(KEY_URI, null) ?: return
        val title = prefs.getString(KEY_TITLE, null) ?: return
        val artist = prefs.getString(KEY_ARTIST, null) ?: return
        val duration = prefs.getLong(KEY_DURATION, 0L)
        val lyrics = prefs.getString(KEY_LYRICS, null)

        val artFile = File(context.filesDir, ART_FILE_NAME)
        val art = if (artFile.exists()) BitmapFactory.decodeFile(artFile.absolutePath) else null

        current = PickedMusic(uri, art, title, artist, duration, lyrics)
    }

    /**
     * 清除內存和本地緩存。
     */
    fun clear(context: Context) {
        current = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        File(context.filesDir, ART_FILE_NAME).delete()
    }

    private fun saveToCache(context: Context, music: PickedMusic) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_URI, music.uri)
            putString(KEY_TITLE, music.title)
            putString(KEY_ARTIST, music.artist)
            putLong(KEY_DURATION, music.durationMs)
            putString(KEY_LYRICS, music.lyrics)
            apply()
        }

        val artFile = File(context.filesDir, ART_FILE_NAME)
        if (music.albumArt != null) {
            try {
                FileOutputStream(artFile).use { out ->
                    music.albumArt.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (_: Exception) { }
        } else {
            if (artFile.exists()) artFile.delete()
        }
    }
}