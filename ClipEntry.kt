package com.clipvault.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

// ─── DATA MODEL ───────────────────────────────────────────────────────────────

enum class ClipType { TEXT, URL, NUMBER, IMAGE, SCREENSHOT }

data class ClipEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: ClipType,
    val content: String,
    val preview: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun formattedTime(): String =
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))

    fun shortDate(): String {
        val diff = System.currentTimeMillis() - timestamp
        val mins = diff / 60_000
        val hrs  = diff / 3_600_000
        val days = diff / 86_400_000
        return when {
            mins < 1   -> "Just now"
            mins < 60  -> "${mins}m ago"
            hrs  < 24  -> "${hrs}h ago"
            days == 1L -> "Yesterday"
            else       -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

fun detectType(text: String): ClipType {
    val t = text.trim()
    if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("www.")) return ClipType.URL
    if (t.matches(Regex("^[\\d\\s\\-+().]+$")) && t.length < 30) return ClipType.NUMBER
    return ClipType.TEXT
}

// ─── STORAGE ──────────────────────────────────────────────────────────────────

object ClipStorage {
    private const val PREFS = "clipvault_prefs"
    private const val KEY   = "clip_history"
    private const val MAX   = 300
    private val gson = Gson()

    fun load(ctx: Context): MutableList<ClipEntry> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<ClipEntry>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) { mutableListOf() }
    }

    /** Returns true if the entry was actually inserted (not a duplicate). */
    fun addEntry(ctx: Context, entry: ClipEntry): Boolean {
        val list = load(ctx)
        // Duplicate check against last 10 items
        if (list.take(10).any { it.content == entry.content && it.type == entry.type }) return false
        list.add(0, entry)
        save(ctx, list)
        return true
    }

    fun delete(ctx: Context, id: String): MutableList<ClipEntry> {
        val list = load(ctx).filter { it.id != id }.toMutableList()
        save(ctx, list)
        return list
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun save(ctx: Context, list: MutableList<ClipEntry>) {
        val trimmed = if (list.size > MAX) list.take(MAX).toMutableList() else list
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, gson.toJson(trimmed)).apply()
    }
}
