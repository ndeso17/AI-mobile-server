package com.server.edge.gallery.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.util.UUID
import kotlin.math.min

enum class KnowledgeSourceType {
  CHAT,
  FILE,
}

data class KnowledgeItem(
  val id: String,
  val sourceType: KnowledgeSourceType,
  val title: String,
  val content: String,
  val language: String,
  val tags: List<String>,
  val createdAt: String,
)

class KnowledgeRepository(context: Context) {
  companion object {
    private const val PREFS_NAME = "knowledge_repo_prefs"
    private const val KEY_ITEMS = "knowledge_items"
    private const val MAX_ITEMS = 1000
    private const val DEFAULT_CONTEXT_BUDGET_CHARS = 32000
  }

  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val gson = Gson()
  private val typeToken = object : TypeToken<List<KnowledgeItem>>() {}.type

  fun all(): List<KnowledgeItem> {
    val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
    return runCatching { gson.fromJson<List<KnowledgeItem>>(json, typeToken) ?: emptyList() }
      .getOrDefault(emptyList())
  }

  fun upsert(item: KnowledgeItem) {
    val items = all().toMutableList()
    val idx = items.indexOfFirst { it.id == item.id }
    if (idx >= 0) {
      items[idx] = item
    } else {
      items.add(0, item)
    }
    if (items.size > MAX_ITEMS) {
      items.subList(MAX_ITEMS, items.size).clear()
    }
    persist(items)
  }

  fun addFromChat(content: String, title: String = "Chat Snippet") {
    if (content.isBlank()) return
    upsert(
      KnowledgeItem(
        id = UUID.randomUUID().toString(),
        sourceType = KnowledgeSourceType.CHAT,
        title = title,
        content = content.trim(),
        language = detectLanguage(content),
        tags = extractKeywords(content),
        createdAt = Instant.now().toString(),
      )
    )
  }

  fun addFromFile(fileName: String, content: String) {
    if (content.isBlank()) return
    upsert(
      KnowledgeItem(
        id = UUID.randomUUID().toString(),
        sourceType = KnowledgeSourceType.FILE,
        title = fileName,
        content = content.trim(),
        language = detectLanguage(content, fileName),
        tags = extractKeywords(content),
        createdAt = Instant.now().toString(),
      )
    )
  }

  fun searchRelevant(query: String, maxContextChars: Int = DEFAULT_CONTEXT_BUDGET_CHARS): List<KnowledgeItem> {
    if (query.isBlank()) return emptyList()
    val queryTokens = extractKeywords(query).toSet()
    val ranked =
      all()
        .map { item ->
          val overlap = item.tags.count { it in queryTokens }
          val bonus = if (item.content.contains(query, ignoreCase = true)) 2 else 0
          item to (overlap + bonus)
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }

    val result = mutableListOf<KnowledgeItem>()
    var used = 0
    for (item in ranked) {
      val remaining = maxContextChars - used
      if (remaining <= 0) break
      val trimmed = if (item.content.length > remaining) item.copy(content = item.content.take(remaining)) else item
      result.add(trimmed)
      used += min(item.content.length, remaining)
    }
    return result
  }

  private fun persist(items: List<KnowledgeItem>) {
    prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
  }

  private fun detectLanguage(content: String, fileName: String = ""): String {
    val lower = content.lowercase()
    val ext = fileName.substringAfterLast('.', "")
    return when {
      ext.isNotEmpty() -> ext
      lower.contains("fun ") && lower.contains("val ") -> "kotlin"
      lower.contains("def ") && lower.contains("import ") -> "python"
      lower.contains("<html") -> "html"
      lower.contains("{") && lower.contains("}") && lower.contains(":") -> "json"
      lower.contains("function ") || lower.contains("const ") -> "javascript"
      else -> "text"
    }
  }

  private fun extractKeywords(text: String): List<String> {
    return text
      .lowercase()
      .split(Regex("[^a-z0-9_]+"))
      .filter { it.length >= 3 }
      .distinct()
      .take(128)
  }
}

