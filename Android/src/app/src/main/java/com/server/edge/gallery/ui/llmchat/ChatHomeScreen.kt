package com.server.edge.gallery.ui.llmchat

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.server.edge.gallery.R
import com.server.edge.gallery.data.BuiltInTaskId
import com.server.edge.gallery.data.ChatSession
import com.server.edge.gallery.data.ChatMode
import com.server.edge.gallery.data.ChatPreferencesRepository
import com.server.edge.gallery.data.ChatSessionRepository
import com.server.edge.gallery.data.EMPTY_MODEL
import com.server.edge.gallery.data.Model
import com.server.edge.gallery.data.ModelDownloadStatusType
import com.server.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.server.edge.gallery.ui.theme.emptyStateContent
import com.server.edge.gallery.ui.theme.emptyStateTitle
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "AGChatHomeScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHomeScreen(
  modelManagerViewModel: ModelManagerViewModel,
  onGoToModels: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LlmChatViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val repository = remember { ChatSessionRepository(context) }
  val preferencesRepository = remember { ChatPreferencesRepository(context) }

  var sessions by remember { mutableStateOf(repository.loadSessions()) }
  var activeSessionId by remember { mutableStateOf(repository.getActiveChatId()) }
  var activeChatMode by remember { mutableStateOf(repository.getActiveChatMode()) }
  var activeWebSearchEnabled by remember { mutableStateOf(repository.getActiveWebSearchEnabled()) }
  var allowNextSessionRestoreForModelSelection by remember { mutableStateOf(false) }

  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  val isSelectedModelInitialized =
    selectedModel.name != EMPTY_MODEL.name && modelManagerUiState.isModelInitialized(selectedModel)

  Log.d(TAG, "Compose: sessions=${sessions.size}, activeSessionId=$activeSessionId, selectedModel=${selectedModel.name}")

  LaunchedEffect(activeSessionId) {
    if (activeSessionId == null && viewModel.currentSessionId == null) {
      viewModel.currentSessionId = "draft-${System.currentTimeMillis()}"
      Log.d(TAG, "Allocated draft session key=${viewModel.currentSessionId}")
    }
  }

  // Refresh sessions whenever the drawer opens so the list is always up-to-date.
  LaunchedEffect(drawerState.currentValue) {
    if (drawerState.currentValue == DrawerValue.Open) {
      Log.d(TAG, "Drawer opened, refreshing sessions")
      sessions = repository.loadSessions()
    }
  }

  LaunchedEffect(selectedModel.name, isSelectedModelInitialized) {
    if (!isSelectedModelInitialized) return@LaunchedEffect
    if (allowNextSessionRestoreForModelSelection) {
      allowNextSessionRestoreForModelSelection = false
      return@LaunchedEffect
    }
    val oldModel = viewModel.lastInitializedModelName
    viewModel.lastInitializedModelName = selectedModel.name
    // Cross-model continuity: copy the currently active session messages to the new model buffer.
    if (!oldModel.isNullOrEmpty() && oldModel != selectedModel.name) {
      val oldMessages = viewModel.uiState.value.messagesByModel[oldModel] ?: emptyList()
      if (oldMessages.isNotEmpty()) {
        viewModel.setMessages(selectedModel, oldMessages)
      }
    }
  }

  LaunchedEffect(activeSessionId, selectedModel.name, modelManagerUiState.modelInitializationStatus) {
    Log.d(TAG, "LaunchedEffect activeSessionId=$activeSessionId")
    repository.setActiveChatId(activeSessionId)
    if (activeSessionId != null) {
      val session = sessions.find { it.id == activeSessionId }
      if (session != null) {
        val task = modelManagerViewModel.getTaskById(id = BuiltInTaskId.LLM_CHAT)
        val sessionModel = task?.models?.find { it.name == session.modelName }
        val hasLoadedSelectedModel =
          selectedModel.name != com.server.edge.gallery.data.EMPTY_MODEL.name &&
            modelManagerUiState.isModelInitialized(selectedModel)

        if (hasLoadedSelectedModel && session.modelName != selectedModel.name) {
          Log.d(
            TAG,
            "Keeping loaded selected model ${selectedModel.name}; not switching to session model ${session.modelName}",
          )
          viewModel.currentSessionId = null
          return@LaunchedEffect
        }

        val targetModel = selectedModel
        Log.d(TAG, "Loading session ${session.id}, model=${session.modelName}, sessionModel=${sessionModel?.name}, targetModel=${targetModel.name}, msgs=${session.messages.size}")
        viewModel.currentSessionId = session.id
        activeChatMode = session.toChatMode()
        repository.setActiveChatMode(activeChatMode)
        activeWebSearchEnabled = session.webSearchEnabled
        repository.setActiveWebSearchEnabled(activeWebSearchEnabled)
        val uiMessages = session.toUiMessages(showThinking = preferencesRepository.getShowThinking())
        Log.d(TAG, "Converted to ${uiMessages.size} UI messages")
        viewModel.setMessages(targetModel, uiMessages)
      } else {
        Log.w(TAG, "Session $activeSessionId not found in sessions list (size=${sessions.size})")
      }
    } else {
      Log.d(TAG, "Clearing chat because activeSessionId is null")
      viewModel.currentSessionId = null
      viewModel.clearAllMessages(selectedModel)
    }
  }

  LaunchedEffect(
    activeSessionId,
    modelManagerUiState.routerAllowedModelNames,
    modelManagerUiState.modelImportingUpdateTrigger,
  ) {
    val task = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT) ?: return@LaunchedEffect
    val allowed = modelManagerUiState.routerAllowedModelNames
    if (allowed.isEmpty()) return@LaunchedEffect
    val candidates =
      task.models
        .filter { model ->
          allowed.contains(model.name) &&
            InferenceRouter.isGenerativeCandidate(model) &&
            modelManagerUiState.modelDownloadStatus[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
        }
        .distinctBy { it.name }
    if (candidates.isEmpty()) return@LaunchedEffect
    Log.d(TAG, "router_preload_start candidates=${candidates.map { it.name }}")
    for (candidate in candidates) {
      Log.d(TAG, "router_preload_step model=${candidate.name}")
      val initialized = warmCheckModel(context = context, viewModel = modelManagerViewModel, task = task, model = candidate)
      Log.d(TAG, "router_preload_done model=${candidate.name} ready=$initialized")
      modelManagerViewModel.cleanupModel(
        context = context,
        task = task,
        model = candidate,
        reason = com.server.edge.gallery.ui.modelmanager.CleanupReason.INTERNAL_MEMORY_POLICY,
      )
      Log.d(TAG, "router_preload_unload_done model=${candidate.name}")
    }
  }

  fun refreshSessions() {
    val loaded = repository.loadSessions()
    Log.d(TAG, "refreshSessions: loaded ${loaded.size} sessions")
    sessions = loaded
  }

  fun saveCurrentSession(model: Model) {
    scope.launch {
      val sessionKey = viewModel.currentSessionId ?: model.name
      val messages =
        viewModel.uiState.value.messagesBySession[sessionKey]
          ?: viewModel.uiState.value.messagesByModel[model.name]
          ?: return@launch
      Log.d(TAG, "chat_history_store=session_buffer key=$sessionKey count=${messages.size}")
      val includeThinkingInHistory = preferencesRepository.getShowThinking()
      val dataMessages = messages.toDataMessages(includeThinking = includeThinkingInHistory)
      Log.d(TAG, "saveCurrentSession: model=${model.name}, uiMsgs=${messages.size}, dataMsgs=${dataMessages.size}")
      if (dataMessages.isEmpty()) return@launch

      val existingSessionId = viewModel.currentSessionId
      val existingSession = existingSessionId?.let { id -> sessions.find { it.id == id } }

      val sessionId =
        if (existingSession != null) {
          Log.d(TAG, "Reusing existing session $existingSessionId")
          existingSessionId
        } else {
          val newId = generateSessionId()
          Log.d(TAG, "Creating new session $newId (existingSession=${existingSession?.id}, modelMatch=${existingSession?.modelName == model.name})")
          newId
        }

      if (viewModel.currentSessionId != sessionId) {
        viewModel.currentSessionId = sessionId
        activeSessionId = sessionId
      }

      val session =
        ChatSession(
          id = sessionId,
          title = generateChatTitle(dataMessages),
          updatedAt = java.time.Instant.now().toString(),
          messages = dataMessages,
          modelName = model.name,
          chatMode = activeChatMode.name,
          modelSwitchHistory =
            ((existingSession?.modelSwitchHistory ?: emptyList()).let { history ->
              if (history.lastOrNull() == model.name) history else history + model.name
            }),
          webSearchEnabled = activeWebSearchEnabled,
        )
      repository.upsertSession(session)
      Log.d(TAG, "Upserted session $sessionId with ${dataMessages.size} messages")
      refreshSessions()
    }
  }

  val navigationIcon: @Composable (() -> Unit) = {
    IconButton(onClick = { scope.launch { drawerState.open() } }) {
      Icon(Icons.Rounded.Menu, contentDescription = "Menu")
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        ChatHistoryDrawer(
          sessions = sessions,
          activeChatId = activeSessionId,
          onNewChat = {
            Log.d(TAG, "onNewChat clicked")
            activeSessionId = null
            activeWebSearchEnabled = repository.getActiveWebSearchEnabled()
            viewModel.currentSessionId = null
            viewModel.clearAllMessages(selectedModel)
            scope.launch { drawerState.close() }
          },
          onOpenSession = { sessionId ->
            Log.d(TAG, "onOpenSession: $sessionId")
            allowNextSessionRestoreForModelSelection = true
            activeSessionId = sessionId
            scope.launch { drawerState.close() }
          },
          onDeleteSession = { sessionId ->
            Log.d(TAG, "onDeleteSession: $sessionId")
            repository.deleteSession(sessionId)
            if (activeSessionId == sessionId) {
              activeSessionId = null
              viewModel.currentSessionId = null
              viewModel.clearAllMessages(selectedModel)
            }
            refreshSessions()
          },
        )
      }
    },
  ) {
    LlmChatScreen(
      modelManagerViewModel = modelManagerViewModel,
      navigateUp = {},
      modifier = modifier,
      viewModel = viewModel,
      showImagePicker = true,
      showAudioPicker = true,
      chatMode = activeChatMode,
      onChatModeChanged = {
        activeChatMode = it
        repository.setActiveChatMode(it)
      },
      showThinking = preferencesRepository.getShowThinking(),
      onShowThinkingChanged = { preferencesRepository.setShowThinking(it) },
      webSearchEnabled = activeWebSearchEnabled,
      onWebSearchEnabledChanged = {
        activeWebSearchEnabled = it
        repository.setActiveWebSearchEnabled(it)
      },
      navigationIcon = navigationIcon,
      onMessagesUpdated = { model -> saveCurrentSession(model) },
      emptyStateComposable = { model ->
        if (model.name == EMPTY_MODEL.name) {
          NoModelEmptyState()
        } else {
          DefaultChatEmptyState()
        }
      },
    )
  }

}

private suspend fun warmCheckModel(
  context: android.content.Context,
  viewModel: ModelManagerViewModel,
  task: com.server.edge.gallery.data.Task,
  model: Model,
): Boolean {
  return suspendCancellableCoroutine { cont ->
    viewModel.initializeModel(
      context = context,
      task = task,
      model = model,
      onDone = { if (cont.isActive) cont.resume(true) },
      onError = { if (cont.isActive) cont.resume(false) },
    )
  }
}

@Composable
private fun NoModelEmptyState() {
  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier.align(Alignment.TopStart)
          .fillMaxWidth()
          .padding(start = 32.dp, end = 32.dp, top = 88.dp),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(R.string.aichat_no_model_title), style = emptyStateTitle)
      Text(
        stringResource(R.string.aichat_no_model_content),
        style = emptyStateContent,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
      )
    }
  }
}

@Composable
private fun DefaultChatEmptyState() {
  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier.align(Alignment.TopStart)
          .fillMaxWidth()
          .padding(start = 32.dp, end = 32.dp, top = 88.dp),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(R.string.aichat_emptystate_title), style = emptyStateTitle)
      Text(
        stringResource(R.string.aichat_emptystate_content),
        style = emptyStateContent,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
      )
    }
  }
}
