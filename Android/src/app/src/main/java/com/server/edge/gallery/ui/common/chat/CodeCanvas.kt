package com.server.edge.gallery.ui.common.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

sealed class TextSegment {
  data class Markdown(val text: String) : TextSegment()
  data class Code(val language: String, val code: String) : TextSegment()
}

private val fencedCodeRegex = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")

fun parseTextSegments(input: String): List<TextSegment> {
  val segments = mutableListOf<TextSegment>()
  var lastIndex = 0
  for (match in fencedCodeRegex.findAll(input)) {
    val start = match.range.first
    if (start > lastIndex) {
      val before = input.substring(lastIndex, start).trim()
      if (before.isNotEmpty()) segments.add(TextSegment.Markdown(before))
    }
    val lang = match.groupValues[1].ifBlank { "code" }
    val code = match.groupValues[2].trimEnd()
    if (code.isNotEmpty()) segments.add(TextSegment.Code(lang, code))
    lastIndex = match.range.last + 1
  }
  if (lastIndex < input.length) {
    val tail = input.substring(lastIndex).trim()
    if (tail.isNotEmpty()) segments.add(TextSegment.Markdown(tail))
  }
  if (segments.isEmpty() && looksLikeCode(input)) {
    return listOf(TextSegment.Code(detectLanguage(input), input.trim()))
  }
  return if (segments.isEmpty()) listOf(TextSegment.Markdown(input)) else segments
}

private fun looksLikeCode(text: String): Boolean {
  val t = text.trim()
  if (t.startsWith("{") && t.endsWith("}")) return true
  val codeSignals =
    listOf("class ", "fun ", "def ", "import ", "const ", "let ", "var ", "<html", "SELECT ", "CREATE TABLE")
  return codeSignals.any { t.contains(it, ignoreCase = true) } && t.lines().size > 2
}

private fun detectLanguage(text: String): String {
  val t = text.lowercase()
  return when {
    t.startsWith("{") && t.endsWith("}") -> "json"
    t.contains("<html") -> "html"
    t.contains("fun ") && t.contains("val ") -> "kotlin"
    t.contains("def ") -> "python"
    t.contains("const ") || t.contains("function ") -> "javascript"
    t.contains("body {") || t.contains("font-family") -> "css"
    else -> "code"
  }
}

@Composable
fun CodeCanvasCard(language: String, code: String, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
        .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(language, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      TextButton(onClick = { copyToClipboard(context, code) }) { Text("Copy") }
    }
    Text(
      text = code,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private fun copyToClipboard(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("code", text))
}

