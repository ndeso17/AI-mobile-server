package com.server.edge.gallery.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.util.UUID
import kotlin.math.min

enum class KnowledgeSourceType {
  CHAT,
  FILE,
  WEB,
}

data class KnowledgeItem(
  val id: String,
  val sourceType: KnowledgeSourceType,
  val title: String,
  val content: String,
  val language: String,
  val tags: List<String>,
  val createdAt: String,
  val priority: Int = 0,
  val scope: String = "global",
  val factEntity: String? = null,
)

data class ValidatedQaItem(
  val queryKey: String,
  val answer: String,
  val sourceType: String,
  val entityHint: String? = null,
  val createdAt: String,
)

class KnowledgeRepository(context: Context) {
  companion object {
    private const val TAG = "AGKnowledgeRepo"
    private const val PREFS_NAME = "knowledge_repo_prefs"
    private const val KEY_ITEMS = "knowledge_items"
    private const val KEY_VALIDATED_QA = "validated_qa_items"
    private const val MAX_ITEMS = 1000
    private const val DEFAULT_CONTEXT_BUDGET_CHARS = 32000
  }

  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val gson = Gson()
  init {
    migrateAndPersistIfNeeded()
  }

  fun all(): List<KnowledgeItem> {
    val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
    val parsed = parseAndSanitize(json)
    return parsed.items
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
        priority = 0,
        scope = "global",
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
        priority = 0,
        scope = "global",
      )
    )
  }

  fun addFromWeb(title: String, content: String, query: String) {
    if (content.isBlank()) return
    upsert(
      KnowledgeItem(
        id = UUID.randomUUID().toString(),
        sourceType = KnowledgeSourceType.WEB,
        title = title,
        content = content.trim(),
        language = "text",
        tags = extractKeywords("$query $content"),
        createdAt = Instant.now().toString(),
        priority = 1,
        scope = "global",
      )
    )
  }

  fun addUserCorrection(content: String, factEntity: String) {
    if (content.isBlank()) return
    upsert(
      KnowledgeItem(
        id = UUID.randomUUID().toString(),
        sourceType = KnowledgeSourceType.CHAT,
        title = "User Correction",
        content = content.trim(),
        language = "text",
        tags = extractKeywords("$factEntity $content") + listOf("user_correction", "fact_current"),
        createdAt = Instant.now().toString(),
        priority = 100,
        scope = "session",
        factEntity = factEntity,
      )
    )
  }

  fun addSessionPriorityContext(content: String, title: String = "Session Context") {
    if (content.isBlank()) return
    upsert(
      KnowledgeItem(
        id = UUID.randomUUID().toString(),
        sourceType = KnowledgeSourceType.CHAT,
        title = title,
        content = content.trim(),
        language = "text",
        tags = extractKeywords(content) + listOf("session_priority"),
        createdAt = Instant.now().toString(),
        priority = 80,
        scope = "session",
      )
    )
  }

  fun getRecentUserCorrections(factEntity: String, limit: Int = 2): List<KnowledgeItem> {
    return all()
      .filter { it.factEntity == factEntity && it.tags.contains("user_correction") }
      .sortedByDescending { it.createdAt }
      .take(limit)
  }

  fun searchRelevant(query: String, maxContextChars: Int = DEFAULT_CONTEXT_BUDGET_CHARS): List<KnowledgeItem> {
    if (query.isBlank()) return emptyList()
    val queryTokens = extractKeywords(query).toSet()
    val ranked =
      all()
        .map { item ->
          val overlap = item.tags.count { it in queryTokens }
          val bonus = if (item.content.contains(query, ignoreCase = true)) 2 else 0
          val sessionBonus = if (item.scope.equals("session", ignoreCase = true)) 5 else 0
          val priorityBonus = (item.priority / 20).coerceAtLeast(0)
          item to (overlap + bonus + sessionBonus + priorityBonus)
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }

    val result = mutableListOf<KnowledgeItem>()
    var used = 0
    for (item in ranked) {
      val remaining = maxContextChars - used
      if (remaining <= 0) break
      val safeItem = sanitizeItem(item) ?: run {
        Log.w(TAG, "knowledge_item_skipped reason=invalid_after_sanitize id=${item.id}")
        continue
      }
      val trimmed =
        if (safeItem.content.length > remaining) {
          safeItem.copy(content = safeItem.content.take(remaining))
        } else {
          safeItem
        }
      result.add(trimmed)
      used += min(safeItem.content.length, remaining)
    }
    return result
  }

  fun upsertValidatedAnswer(
    query: String,
    answer: String,
    sourceType: String,
    entityHint: String? = null,
  ) {
    if (query.isBlank() || answer.isBlank()) return
    val key = normalizeQueryKey(query)
    val current = getValidatedAnswers().toMutableList()
    val idx = current.indexOfFirst { it.queryKey == key || (entityHint != null && it.entityHint == entityHint) }
    val item =
      ValidatedQaItem(
        queryKey = key,
        answer = answer.trim(),
        sourceType = sourceType,
        entityHint = entityHint,
        createdAt = Instant.now().toString(),
      )
    if (idx >= 0) current[idx] = item else current.add(0, item)
    if (current.size > MAX_ITEMS) current.subList(MAX_ITEMS, current.size).clear()
    prefs.edit().putString(KEY_VALIDATED_QA, gson.toJson(current)).apply()
  }

  fun findValidatedAnswerForQuery(query: String, entityHint: String? = null): ValidatedQaItem? {
    if (query.isBlank()) return null
    val key = normalizeQueryKey(query)
    val queryTokens = extractKeywords(query).toSet()
    val items = getValidatedAnswers()
    items.firstOrNull { it.queryKey == key }?.let { return it }
    if (entityHint != null) {
      items.firstOrNull { it.entityHint == entityHint }?.let { return it }
    }
    return items.maxByOrNull { item ->
      val overlap = extractKeywords(item.queryKey).count { it in queryTokens }
      overlap
    }?.takeIf {
      extractKeywords(it.queryKey).count { token -> token in queryTokens } >= 2
    }
  }

  private data class ParseResult(
    val items: List<KnowledgeItem>,
    val migrated: Boolean,
    val sanitizedCount: Int,
  )

  private fun migrateAndPersistIfNeeded() {
    val json = prefs.getString(KEY_ITEMS, null) ?: return
    val parsed = parseAndSanitize(json)
    if (parsed.migrated) {
      Log.d(TAG, "knowledge_migration_applied count=${parsed.sanitizedCount}")
      persist(parsed.items)
    }
  }

  private fun parseAndSanitize(json: String): ParseResult {
    return runCatching {
      val root = JsonParser.parseString(json)
      if (!root.isJsonArray) {
        return@runCatching ParseResult(emptyList(), migrated = true, sanitizedCount = 0)
      }
      val array = root.asJsonArray
      val result = mutableListOf<KnowledgeItem>()
      var migrated = false
      var sanitizedCount = 0
      for (element in array) {
        val itemObj = element.takeIf { it.isJsonObject }?.asJsonObject ?: run {
          migrated = true
          continue
        }
        val item = jsonToKnowledgeItem(itemObj)
        if (item == null) {
          migrated = true
          Log.w(TAG, "knowledge_item_skipped reason=invalid_json_object")
          continue
        }
        val sanitized = sanitizeItem(item)
        if (sanitized == null) {
          migrated = true
          Log.w(TAG, "knowledge_item_skipped reason=sanitize_failed id=${item.id}")
          continue
        }
        if (sanitized != item) {
          sanitizedCount += 1
          migrated = true
          Log.d(TAG, "knowledge_item_sanitized id=${item.id}")
        }
        result.add(sanitized)
      }
      ParseResult(result, migrated = migrated, sanitizedCount = sanitizedCount)
    }.getOrElse {
      Log.w(TAG, "knowledge_parse_failed fallback_empty reason=${it.message}")
      ParseResult(emptyList(), migrated = true, sanitizedCount = 0)
    }
  }

  private fun jsonToKnowledgeItem(obj: JsonObject): KnowledgeItem? {
    val id = obj.readString("id").ifBlank { UUID.randomUUID().toString() }
    val sourceType =
      runCatching {
        val raw = obj.readString("sourceType")
        if (raw.isBlank()) KnowledgeSourceType.CHAT else KnowledgeSourceType.valueOf(raw)
      }.getOrDefault(KnowledgeSourceType.CHAT)
    val title = obj.readString("title")
    val content = obj.readString("content")
    if (content.isBlank()) return null
    val language = obj.readString("language")
    val tags = obj.readStringList("tags")
    val createdAt = obj.readString("createdAt")
    val priority = obj.readInt("priority") ?: 0
    val scope = obj.readString("scope")
    val factEntity = obj.readNullableString("factEntity")

    return KnowledgeItem(
      id = id,
      sourceType = sourceType,
      title = title,
      content = content,
      language = language,
      tags = tags,
      createdAt = createdAt,
      priority = priority,
      scope = scope,
      factEntity = factEntity,
    )
  }

  private fun sanitizeItem(item: KnowledgeItem): KnowledgeItem? {
    val content = item.content.trim()
    if (content.isBlank()) return null
    val title = item.title.ifBlank { "Untitled Knowledge" }
    val language = item.language.ifBlank { detectLanguage(content) }
    val createdAt = item.createdAt.ifBlank { Instant.now().toString() }
    val scope = item.scope.ifBlank { "global" }
    val tags =
      if (item.tags.isEmpty()) {
        extractKeywords("$title $content")
      } else {
        item.tags.filter { it.isNotBlank() }.distinct()
      }
    return item.copy(
      title = title,
      content = content,
      language = language,
      tags = tags,
      createdAt = createdAt,
      scope = scope,
    )
  }

  private fun JsonObject.readString(key: String): String {
    val element: JsonElement = get(key) ?: return ""
    if (element.isJsonNull) return ""
    return runCatching { element.asString }.getOrDefault("")
  }

  private fun JsonObject.readNullableString(key: String): String? {
    val element: JsonElement = get(key) ?: return null
    if (element.isJsonNull) return null
    return runCatching { element.asString }.getOrNull()
  }

  private fun JsonObject.readInt(key: String): Int? {
    val element: JsonElement = get(key) ?: return null
    if (element.isJsonNull) return null
    return runCatching { element.asInt }.getOrNull()
  }

  private fun JsonObject.readStringList(key: String): List<String> {
    val element: JsonElement = get(key) ?: return emptyList()
    if (!element.isJsonArray) return emptyList()
    return element.asJsonArray.mapNotNull { runCatching { it.asString }.getOrNull() }
  }

  private fun persist(items: List<KnowledgeItem>) {
    prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
  }

  private fun getValidatedAnswers(): List<ValidatedQaItem> {
    val json = prefs.getString(KEY_VALIDATED_QA, null) ?: return emptyList()
    return runCatching {
      val type = object : TypeToken<List<ValidatedQaItem>>() {}.type
      gson.fromJson<List<ValidatedQaItem>>(json, type).orEmpty()
    }.getOrDefault(emptyList())
  }

  private fun normalizeQueryKey(query: String): String {
    return query.lowercase().replace(Regex("\\s+"), " ").trim()
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
