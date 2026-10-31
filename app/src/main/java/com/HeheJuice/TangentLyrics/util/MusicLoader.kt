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
 
package com.HeheJuice.TangentLyrics.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.HeheJuice.TangentLyrics.R
import com.HeheJuice.TangentLyrics.data.PickedMusic
import java.io.ByteArrayOutputStream
import java.io.InputStream

object MusicLoader {

    private const val MAX_ART_SIZE = 1024
    // ★ 提升到 20MB，確保能覆蓋 M4A 的 moov box（就算在尾部也能讀到）
    private const val MAX_HEADER_SIZE = 20 * 1024 * 1024 

    fun load(context: Context, uri: Uri): PickedMusic {
        val retriever = MediaMetadataRetriever()
        val title: String
        val artist: String
        val durationMs: Long
        val art: Bitmap?

        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_title)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_artist)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            
            art = retriever.embeddedPicture?.let {
                try {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                        ?.let { bmp -> scaleDown(bmp, MAX_ART_SIZE) }
                } catch (_: OutOfMemoryError) { null }
                catch (_: Exception) { null }
            }
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }

        val rawLyrics = tryReadLyricsBytes(context, uri)
        val cleanLyrics = formatLrc(rawLyrics)

        return PickedMusic(
            uri = uri.toString(),
            albumArt = art,
            title = title,
            artist = artist,
            durationMs = durationMs,
            lyrics = cleanLyrics
        )
    }

    private fun formatLrc(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        
        val timeTagRegex = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?\\]")
        if (!raw.contains(timeTagRegex)) return raw.trim()

        val lines = raw.split("\n")
        val result = mutableListOf<String>()
        var lastTimeTag = ""

        for (line in lines) {
            if (line.isBlank()) continue
            
            val matches = timeTagRegex.findAll(line).toList()
            if (matches.size > 1 && matches.all { it.value == matches[0].value }) {
                val texts = line.split(timeTagRegex).filter { it.isNotBlank() }
                if (texts.size >= 2) {
                    result.add("${matches[0].value}${texts[0].trim()}\u0001${texts[1].trim()}")
                    lastTimeTag = matches[0].value
                    continue
                }
            }

            val primaryTimeTag = matches.firstOrNull()?.value ?: ""
            val content = line.replace(timeTagRegex, "").trimStart()

            if (content.isBlank()) continue

            if (primaryTimeTag == lastTimeTag && result.isNotEmpty()) {
                val lastLine = result.removeAt(result.size - 1)
                result.add("$lastLine\u0001$content")
            } else {
                result.add("$primaryTimeTag$content")
                lastTimeTag = primaryTimeTag
            }
        }
        
        return if (result.isNotEmpty()) result.joinToString("\n") else raw.trim()
    }

    private fun scaleDown(src: Bitmap, maxSize: Int): Bitmap {
        val w = src.width; val h = src.height
        if (w <= maxSize && h <= maxSize) return src
        val scale = maxSize.toFloat() / maxOf(w, h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(src, nw, nh, true).also { if (it !== src) src.recycle() }
        } catch (_: OutOfMemoryError) { src }
    }

    private fun tryReadLyricsBytes(context: Context, uri: Uri): String? {
        var stream: InputStream? = null
        try {
            stream = context.contentResolver.openInputStream(uri) ?: return null
            val buffer = ByteArrayOutputStream()
            val data = ByteArray(8192)
            var totalRead = 0
            var bytesRead: Int
            
            while (stream.read(data).also { bytesRead = it } != -1 && totalRead < MAX_HEADER_SIZE) {
                buffer.write(data, 0, bytesRead)
                totalRead += bytesRead
            }
            val bytes = buffer.toByteArray()

            // 1. MP3 (ID3v2)
            if (bytes.size > 10 && bytes[0] == 'I'.code.toByte() && 
                bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
                parseId3v2(bytes)?.let { return it }
            }

            // 2. M4A / MP4 (直接暴力搜索 ©lyr 標籤，無視複雜的 Box 嵌套)
            val asString = String(bytes, Charsets.ISO_8859_1)
            if (asString.contains("ftyp") || asString.contains("moov")) {
                parseM4aLyricsRobust(bytes)?.let { return it }
            }

            // 3. FLAC
            if (bytes.size > 4 && bytes[0] == 'f'.code.toByte() && 
                bytes[1] == 'L'.code.toByte() && bytes[2] == 'a'.code.toByte() && 
                bytes[3] == 'C'.code.toByte()) {
                parseFlacLyrics(bytes)?.let { return it }
            }

        } catch (_: Exception) {
        } finally {
            try { stream?.close() } catch (_: Exception) { }
        }
        return null
    }

    private fun parseId3v2(bytes: ByteArray): String? {
        try {
            val majorVersion = bytes[3].toInt() and 0xFF
            val flags = bytes[5].toInt() and 0xFF
            var tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
                          ((bytes[7].toInt() and 0x7F) shl 14) or
                          ((bytes[8].toInt() and 0x7F) shl 7) or
                           (bytes[9].toInt() and 0x7F)
            
            if (tagSize > bytes.size - 10) tagSize = bytes.size - 10
            var offset = 10

            if (flags and 0x40 != 0) {
                if (majorVersion == 4) {
                    val extSize = ((bytes[offset].toInt() and 0x7F) shl 21) or
                                  ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
                                  ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
                                   (bytes[offset + 3].toInt() and 0x7F)
                    offset += extSize
                } else if (majorVersion == 3) {
                    val extSize = ((bytes[offset].toInt() and 0xFF) shl 24) or
                                  ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                                  ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                                   (bytes[offset + 3].toInt() and 0xFF)
                    offset += 4 + extSize
                }
            }

            while (offset + 10 <= tagSize + 10) {
                val frameId = String(bytes, offset, 4, Charsets.ISO_8859_1)
                val frameSize = if (majorVersion == 4) {
                    ((bytes[offset + 4].toInt() and 0x7F) shl 21) or
                    ((bytes[offset + 5].toInt() and 0x7F) shl 14) or
                    ((bytes[offset + 6].toInt() and 0x7F) shl 7) or
                     (bytes[offset + 7].toInt() and 0x7F)
                } else {
                    ((bytes[offset + 4].toInt() and 0xFF) shl 24) or
                    ((bytes[offset + 5].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 6].toInt() and 0xFF) shl 8) or
                     (bytes[offset + 7].toInt() and 0xFF)
                }

                if (frameSize <= 0 || offset + 10 + frameSize > bytes.size) break
                val frameData = bytes.copyOfRange(offset + 10, offset + 10 + frameSize)

                if (frameId == "USLT" || frameId == "ULT") {
                    parseUslt(frameData)?.let { return it }
                } else if (frameId == "SYLT" || frameId == "SLT") {
                    parseSylt(frameData)?.let { return it }
                }
                offset += 10 + frameSize
            }
        } catch (_: Exception) { }
        return null
    }

    private fun parseUslt(data: ByteArray): String? {
        if (data.size < 4) return null
        val encoding = data[0].toInt() and 0xFF
        var pos = 4
        
        if (encoding == 0 || encoding == 3) {
            while (pos < data.size && data[pos] != 0.toByte()) pos++
            pos++
        } else {
            while (pos + 1 < data.size && !(data[pos] == 0.toByte() && data[pos + 1] == 0.toByte())) pos += 2
            pos += 2
        }
        if (pos >= data.size) return null

        val lyricsBytes = data.copyOfRange(pos, data.size)
        return when (encoding) {
            0 -> String(lyricsBytes, Charsets.ISO_8859_1)
            1 -> String(lyricsBytes, Charsets.UTF_16)
            2 -> String(lyricsBytes, Charsets.UTF_16BE)
            3 -> String(lyricsBytes, Charsets.UTF_8)
            else -> null
        }?.trim()?.replace("\u0000", "")?.takeIf { it.isNotBlank() }
    }

    private fun parseSylt(data: ByteArray): String? {
        if (data.size < 6) return null
        val encoding = data[0].toInt() and 0xFF
        var pos = 6
        
        if (encoding == 0 || encoding == 3) {
            while (pos < data.size && data[pos] != 0.toByte()) pos++
            pos++
        } else {
            while (pos + 1 < data.size && !(data[pos] == 0.toByte() && data[pos + 1] == 0.toByte())) pos += 2
            pos += 2
        }

        val sb = StringBuilder()
        while (pos + 4 < data.size) {
            pos += 4
            var end = pos
            if (encoding == 0 || encoding == 3) {
                while (end < data.size && data[end] != 0.toByte()) end++
                sb.append(String(data, pos, end - pos, if (encoding == 0) Charsets.ISO_8859_1 else Charsets.UTF_8))
                end++
            } else {
                while (end + 1 < data.size && !(data[end] == 0.toByte() && data[end + 1] == 0.toByte())) end += 2
                sb.append(String(data, pos, end - pos, if (encoding == 1) Charsets.UTF_16 else Charsets.UTF_16BE))
                end += 2
            }
            sb.append("\n")
            pos = end
        }
        return sb.toString().trim().takeIf { it.isNotBlank() }
    }

    // ★ 全新的極度強壯 M4A 解析：直接搜索 ©lyr，尋找後續的 data 盒子
    private fun parseM4aLyricsRobust(bytes: ByteArray): String? {
        // ©lyr 的十六進制是 A9 6C 79 72
        val target = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var idx = indexOf(bytes, target)
        
        while (idx != -1) {
            // 找到 ©lyr 後，往後搜索 "data" 標籤
            val limit = minOf(bytes.size - 4, idx + 1024) // 通常緊跟在後，最多找 1KB
            for (i in idx + 4..limit) {
                if (bytes[i] == 'd'.code.toByte() && 
                    bytes[i+1] == 'a'.code.toByte() && 
                    bytes[i+2] == 't'.code.toByte() && 
                    bytes[i+3] == 'a'.code.toByte()) {
                    
                    // 找到 'data'，結構通常是：[size(4)][data(4)][type(4)][locale(4)][payload...]
                    val textStart = i + 12
                    if (textStart >= bytes.size) break
                    
                    val boxSize = readInt32BE(bytes, i - 4)
                    // 確保 size 合理，並且內容在 byte 數組範圍內
                    if (boxSize > 16 && textStart + (boxSize - 16) <= bytes.size) {
                        val textLength = boxSize - 16
                        val text = String(bytes, textStart, textLength, Charsets.UTF_8)
                            .trim()
                            .replace("\u0000", "")
                        if (text.isNotBlank()) return text
                    }
                }
            }
            // 如果這個 ©lyr 沒找到有效歌詞，繼續找下一個
            idx = indexOf(bytes, target, idx + 1)
        }
        return null
    }

    private fun readInt32BE(data: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
               ((data[offset + 1].toInt() and 0xFF) shl 16) or
               ((data[offset + 2].toInt() and 0xFF) shl 8) or
               (data[offset + 3].toInt() and 0xFF)
    }

    private fun parseFlacLyrics(bytes: ByteArray): String? {
        var keyBytes = "LYRICS=".toByteArray(Charsets.US_ASCII)
        var idx = indexOf(bytes, keyBytes)
        if (idx == -1) {
            keyBytes = "UNSYNCEDLYRICS=".toByteArray(Charsets.US_ASCII)
            idx = indexOf(bytes, keyBytes)
            if (idx == -1) return null
        }
        
        if (idx < 4) return null
        val start = idx - 4
        val length = (bytes[start].toInt() and 0xFF) or
                     ((bytes[start + 1].toInt() and 0xFF) shl 8) or
                     ((bytes[start + 2].toInt() and 0xFF) shl 16) or
                     ((bytes[start + 3].toInt() and 0xFF) shl 24)
                     
        val end = start + 4 + length
        if (end > bytes.size || end <= idx) return null
        
        val valueStart = idx + keyBytes.size
        if (valueStart >= end) return null
        
        return String(bytes, valueStart, end - valueStart, Charsets.UTF_8)
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun indexOf(array: ByteArray, target: ByteArray, startIndex: Int = 0): Int {
        if (target.isEmpty() || array.size < target.size || startIndex < 0) return -1
        outer@ for (i in startIndex..array.size - target.size) {
            for (j in target.indices) {
                if (array[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }
}