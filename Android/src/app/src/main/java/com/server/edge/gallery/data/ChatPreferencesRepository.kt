package com.server.edge.gallery.data

import android.content.Context

class ChatPreferencesRepository(context: Context) {
  companion object {
    private const val PREFS_NAME = "chat_preferences_prefs"
    private const val KEY_SHOW_THINKING = "show_thinking"
    private const val KEY_AUTO_INGEST_KNOWLEDGE = "auto_ingest_knowledge"
  }

  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getShowThinking(): Boolean = prefs.getBoolean(KEY_SHOW_THINKING, false)

  fun setShowThinking(show: Boolean) {
    prefs.edit().putBoolean(KEY_SHOW_THINKING, show).apply()
  }

  fun getAutoIngestKnowledge(): Boolean = prefs.getBoolean(KEY_AUTO_INGEST_KNOWLEDGE, true)

  fun setAutoIngestKnowledge(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_INGEST_KNOWLEDGE, enabled).apply()
  }
}

