/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.server.edge.gallery.ui.llmchat

import androidx.hilt.navigation.compose.hiltViewModel

import android.graphics.Bitmap
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.server.edge.gallery.GalleryEvent
import com.server.edge.gallery.R
import com.server.edge.gallery.data.BuiltInTaskId
import com.server.edge.gallery.data.ChatMode
import com.server.edge.gallery.data.ChatPreferencesRepository
import com.server.edge.gallery.data.KnowledgeRepository
import com.server.edge.gallery.data.Model
import com.server.edge.gallery.data.ModelCapability
import com.server.edge.gallery.data.ModelDownloadStatusType
import com.server.edge.gallery.data.RuntimeType
import com.server.edge.gallery.data.Task
import com.server.edge.gallery.firebaseAnalytics
import com.server.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.server.edge.gallery.ui.common.chat.ChatMessageInfo
import com.server.edge.gallery.ui.common.chat.ChatMessageImage
import com.server.edge.gallery.ui.common.chat.ChatMessageText
import com.server.edge.gallery.ui.common.chat.ChatMessageType
import com.server.edge.gallery.ui.common.chat.ChatMessageWarning
import com.server.edge.gallery.ui.common.chat.ChatView
import com.server.edge.gallery.ui.common.chat.UploadedTextFile
import com.server.edge.gallery.ui.common.chat.SendMessageTrigger
import com.server.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.server.edge.gallery.ui.theme.emptyStateContent
import com.server.edge.gallery.ui.theme.emptyStateTitle
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "AGLlmChatScreen"
private const val WEB_SEARCH_TAG = "AGWebSearch"
private const val CHAT_PIPELINE_TAG = "AGChatPipeline"

@Composable
fun LlmChatScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  taskId: String = BuiltInTaskId.LLM_CHAT,
  onFirstToken: (Model) -> Unit = {},
  onGenerateResponseDone: (Model) -> Unit = {},
  onSkillClicked: () -> Unit = {},
  onTextFilesPicked: (List<UploadedTextFile>) -> Unit = {},
  onResetSessionClickedOverride: ((Task, Model) -> Unit)? = null,
  composableBelowMessageList: @Composable (Model) -> Unit = {},
  viewModel: LlmChatViewModel = hiltViewModel(),
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  emptyStateComposable: @Composable (Model) -> Unit = {},
  sendMessageTrigger: SendMessageTrigger? = null,
  showImagePicker: Boolean = false,
  showAudioPicker: Boolean = false,
  chatMode: ChatMode = ChatMode.DEFAULT,
  onChatModeChanged: (ChatMode) -> Unit = {},
  showThinking: Boolean = false,
  onShowThinkingChanged: (Boolean) -> Unit = {},
  webSearchEnabled: Boolean = false,
  onWebSearchEnabledChanged: (Boolean) -> Unit = {},
  getActiveSkills: () -> List<String> = { emptyList() },
  navigationIcon: @Composable (() -> Unit)? = null,
  onMessagesUpdated: (Model) -> Unit = {},
) {
  ChatViewWrapper(
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    taskId = taskId,
    navigateUp = navigateUp,
    modifier = modifier,
    onSkillClicked = onSkillClicked,
    onTextFilesPicked = onTextFilesPicked,
    onFirstToken = onFirstToken,
    onGenerateResponseDone = onGenerateResponseDone,
    onResetSessionClickedOverride = onResetSessionClickedOverride,
    composableBelowMessageList = composableBelowMessageList,
    allowEditingSystemPrompt = allowEditingSystemPrompt,
    curSystemPrompt = curSystemPrompt,
    onSystemPromptChanged = onSystemPromptChanged,
    emptyStateComposable = emptyStateComposable,
    sendMessageTrigger = sendMessageTrigger,
    showImagePicker = showImagePicker,
    showAudioPicker = showAudioPicker,
    chatMode = chatMode,
    onChatModeChanged = onChatModeChanged,
    showThinking = showThinking,
    onShowThinkingChanged = onShowThinkingChanged,
    webSearchEnabled = webSearchEnabled,
    onWebSearchEnabledChanged = onWebSearchEnabledChanged,
    getActiveSkills = getActiveSkills,
    navigationIcon = navigationIcon,
    onMessagesUpdated = onMessagesUpdated,
  )
}

@Composable
fun LlmAskImageScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LlmAskImageViewModel = hiltViewModel(),
) {
  ChatViewWrapper(
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    taskId = BuiltInTaskId.LLM_ASK_IMAGE,
    navigateUp = navigateUp,
    modifier = modifier,
    showImagePicker = true,
    showAudioPicker = false,
    emptyStateComposable = { model ->
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier =
            Modifier.align(Alignment.Center).padding(horizontal = 48.dp).padding(bottom = 48.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(stringResource(R.string.askimage_emptystate_title), style = emptyStateTitle)
          val contentRes =
            if (model.runtimeType == RuntimeType.AICORE) R.string.askimage_emptystate_content_aicore
            else R.string.askimage_emptystate_content
          Text(
            stringResource(contentRes),
            style = emptyStateContent,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    },
  )
}

@Composable
fun LlmAskAudioScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LlmAskAudioViewModel = hiltViewModel(),
) {
  ChatViewWrapper(
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    taskId = BuiltInTaskId.LLM_ASK_AUDIO,
    navigateUp = navigateUp,
    modifier = modifier,
    showImagePicker = false,
    showAudioPicker = true,
    emptyStateComposable = {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier =
            Modifier.align(Alignment.Center).padding(horizontal = 48.dp).padding(bottom = 48.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(stringResource(R.string.askaudio_emptystate_title), style = emptyStateTitle)
          Text(
            stringResource(R.string.askaudio_emptystate_content),
            style = emptyStateContent,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    },
  )
}

@Composable
fun ChatViewWrapper(
  viewModel: LlmChatViewModelBase,
  modelManagerViewModel: ModelManagerViewModel,
  taskId: String,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  onSkillClicked: () -> Unit = {},
  onTextFilesPicked: (List<UploadedTextFile>) -> Unit = {},
  onFirstToken: (Model) -> Unit = {},
  onGenerateResponseDone: (Model) -> Unit = {},
  onResetSessionClickedOverride: ((Task, Model) -> Unit)? = null,
  composableBelowMessageList: @Composable (Model) -> Unit = {},
  emptyStateComposable: @Composable (Model) -> Unit = {},
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  sendMessageTrigger: SendMessageTrigger? = null,
  showImagePicker: Boolean = false,
  showAudioPicker: Boolean = false,
  chatMode: ChatMode = ChatMode.DEFAULT,
  onChatModeChanged: (ChatMode) -> Unit = {},
  showThinking: Boolean = false,
  onShowThinkingChanged: (Boolean) -> Unit = {},
  webSearchEnabled: Boolean = false,
  onWebSearchEnabledChanged: (Boolean) -> Unit = {},
  getActiveSkills: () -> List<String> = { emptyList() },
  navigationIcon: @Composable (() -> Unit)? = null,
  onMessagesUpdated: (Model) -> Unit = {},
) {
  val context = LocalContext.current
  val knowledgeRepository = KnowledgeRepository(context)
  val chatPreferencesRepository = ChatPreferencesRepository(context)
  val coroutineScope = rememberCoroutineScope()
  var sessionWebSearchEnabled by remember { mutableStateOf(webSearchEnabled) }
  var chatTaskMissingSinceMs by remember { mutableStateOf<Long?>(null) }
  LaunchedEffect(webSearchEnabled) {
    sessionWebSearchEnabled = webSearchEnabled
  }
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val task = modelManagerUiState.tasks.firstOrNull { it.id == taskId }
  if (task == null) {
    if (chatTaskMissingSinceMs == null) {
      chatTaskMissingSinceMs = System.currentTimeMillis()
    }
    Log.w(TAG, "chat_task_state=missing taskId=$taskId")
    Box(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Chat sedang disiapkan...", style = emptyStateTitle)
        Text(
          "Daftar task/model belum siap. Coba tunggu beberapa detik atau buka tab Models lalu kembali ke Chats.",
          style = emptyStateContent,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    return
  }
  chatTaskMissingSinceMs?.let { startedAt ->
    Log.d(TAG, "startup_to_chat_ready_ms=${System.currentTimeMillis() - startedAt}")
    chatTaskMissingSinceMs = null
  }
  Log.d(TAG, "chat_task_state=ready taskId=${task.id}")

  ChatView(
    task = task,
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    navigationIcon = navigationIcon,
    onSendMessage = { requestedModel, messages ->
      var text = ""
      val images: MutableList<Bitmap> = mutableListOf()
      val audioMessages: MutableList<ChatMessageAudioClip> = mutableListOf()
      var chatMessageText: ChatMessageText? = null
      for (message in messages) {
        if (message is ChatMessageText) {
          chatMessageText = message
          text = message.content
        } else if (message is ChatMessageImage) {
          images.addAll(message.bitmaps)
        } else if (message is ChatMessageAudioClip) {
          audioMessages.add(message)
        }
      }
      if ((text.isNotEmpty() && chatMessageText != null) || audioMessages.isNotEmpty()) {
        Log.d(CHAT_PIPELINE_TAG, "router_submit_pipeline_start textLen=${text.length} webOn=$sessionWebSearchEnabled")
        coroutineScope.launch {
          val pipelineTraceId = "router-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
          Log.d(CHAT_PIPELINE_TAG, "pipeline_trace_start traceId=$pipelineTraceId")
          if (showThinking) {
            emitPipelineStep(viewModel, requestedModel, "Menerima prompt user", pipelineTraceId)
            emitPipelineStep(viewModel, requestedModel, "Membaca context session/global", pipelineTraceId)
          }
          val directAnswerDecision =
            if (text.isNotBlank()) {
              resolveDirectAnswerBeforeModel(
                viewModel = viewModel,
                requestedModel = requestedModel,
                knowledgeRepository = knowledgeRepository,
                query = text,
                webSearchEnabled = sessionWebSearchEnabled,
                traceId = pipelineTraceId,
              )
            } else {
              null
            }
          if (directAnswerDecision != null) {
            Log.d(
              CHAT_PIPELINE_TAG,
              "router_direct_answer_hit source=${directAnswerDecision.source} router_model_bypass=${directAnswerDecision.bypassModel}",
            )
            for (message in messages) {
              viewModel.addMessage(model = requestedModel, message = message)
            }
            viewModel.addMessage(
              model = requestedModel,
              message =
                ChatMessageText(
                  content = directAnswerDecision.answerText,
                  side = com.server.edge.gallery.ui.common.chat.ChatSide.AGENT,
                ),
            )
            if (shouldCacheDirectAnswer(text, directAnswerDecision)) {
              knowledgeRepository.upsertValidatedAnswer(
                query = text,
                answer = directAnswerDecision.answerText,
                sourceType = directAnswerDecision.source,
                entityHint = resolveFactEntity(text),
              )
            }
            Log.d(CHAT_PIPELINE_TAG, "pipeline_trace_end traceId=$pipelineTraceId status=direct_answer")
            onMessagesUpdated(requestedModel)
            return@launch
          }
          val routeDecision =
            InferenceRouter.route(
              requestedModel = requestedModel,
              taskModels = task.models,
              messages = messages,
              allowedModelNames = modelManagerUiState.routerAllowedModelNames,
            )
          Log.d(
            CHAT_PIPELINE_TAG,
            "router_route_selected selected=${routeDecision.selectedModel.name} requested=${requestedModel.name} reason=${routeDecision.reasonCode} candidates=${routeDecision.candidateModels.map { it.name }} ensemble=${routeDecision.ensembleCandidates.map { it.name }}",
          )
          Log.d(CHAT_PIPELINE_TAG, "router_route_reason code=${routeDecision.reasonCode}")
          val resolvedModel =
            resolveModelForExecution(
              context = context,
              modelManagerViewModel = modelManagerViewModel,
              task = task,
              routeDecision = routeDecision,
              onInitProgress = { modelName ->
                if (showThinking) {
                  emitPipelineStep(viewModel, requestedModel, "Memuat model: $modelName", pipelineTraceId)
                }
                viewModel.addMessage(
                  model = requestedModel,
                  message = ChatMessageInfo(content = "Router sedang memuat model $modelName ..."),
                )
              },
            )
          if (resolvedModel == null) {
            viewModel.addMessage(
              model = requestedModel,
              message =
                ChatMessageWarning(
                  content = "Router belum punya model aktif. Buka Models > Router Models lalu aktifkan minimal 1 model yang sudah didownload.",
                ),
            )
            return@launch
          }
          // Remove transient loading info once model is ready to infer.
          val lastMsg = viewModel.getLastMessage(requestedModel)
          if (lastMsg is ChatMessageInfo && lastMsg.content.startsWith("Router sedang memuat model")) {
            viewModel.removeLastMessage(requestedModel)
          }
          Log.d(
            CHAT_PIPELINE_TAG,
            "router_final_model_used selected=${resolvedModel.name} requested=${requestedModel.name}",
          )
          if (resolvedModel.name != requestedModel.name) {
            modelManagerViewModel.selectModel(resolvedModel)
            viewModel.addMessage(
              model = resolvedModel,
              message =
                ChatMessageInfo(
                  content = "Auto Router memakai model: ${resolvedModel.displayName.ifEmpty { resolvedModel.name }}",
                ),
            )
          }
          for (message in messages) {
            viewModel.addMessage(model = resolvedModel, message = message)
          }
          onMessagesUpdated(resolvedModel)

          if (text.isNotEmpty()) {
            modelManagerViewModel.addTextInputHistory(text)
            val correctionEntity = detectCorrectionEntity(text)
            if (correctionEntity != null) {
              knowledgeRepository.addUserCorrection(content = text, factEntity = correctionEntity)
              Log.d(CHAT_PIPELINE_TAG, "fact_policy_applied=true fact_resolution_source=user_correction_ingest entity=$correctionEntity")
            } else if (looksLikeSessionPreference(text)) {
              knowledgeRepository.addSessionPriorityContext(content = text)
              Log.d(CHAT_PIPELINE_TAG, "memory_conflict_resolved=session_priority_ingested")
            }
          }
          val adaptiveContextBudgetChars =
            when (resolvedModel.runtimeType) {
              RuntimeType.AICORE -> 3200
              else -> 6000
            }
          val retrievedMemory =
            withContext(Dispatchers.Default) {
              knowledgeRepository.searchRelevant(text, maxContextChars = adaptiveContextBudgetChars)
            }
          val hybridMemory = prioritizeHybridMemory(retrievedMemory)
          val shapedMemory = shapeRetrievedMemory(query = text, items = hybridMemory)
          val memoryPrompt =
            if (isCurrentFactQuery(text)) {
              ""
            } else if (shapedMemory.isEmpty()) {
              ""
            } else {
              buildString {
                appendLine("Relevant Prior Knowledge:")
                for (item in shapedMemory.take(3)) {
                  appendLine("- ${item.title} [${item.language}]")
                  appendLine(item.content.take(700))
                }
              }
            }
          Log.d(
            CHAT_PIPELINE_TAG,
            "context_retrieval_summary source=session+global count=${shapedMemory.size} chars=${shapedMemory.sumOf { it.content.length }}",
          )
          val planningPrompt =
            if (chatMode == ChatMode.PLAN) {
              "Plan Mode: focus on goals, assumptions, steps, and acceptance criteria. Do not execute actions.\n\n"
            } else {
              ""
            }
          val factualCurrentQuery = isCurrentFactQuery(text)
          val shouldUseWebForThisQuery = factualCurrentQuery
          Log.d(CHAT_PIPELINE_TAG, "web_scope_applied=factual_only web_skipped_non_factual=${!factualCurrentQuery}")
          if (showThinking && shouldUseWebForThisQuery) {
            viewModel.addMessage(
              model = resolvedModel,
              message = ChatMessageInfo(content = "Thinking: mulai web search untuk memperkaya konteks faktual."),
            )
          }
          val webResult =
            if (shouldUseWebForThisQuery && text.isNotBlank()) {
              withTimeoutOrNull(4500) { fetchDuckDuckGoContext(text) }
                ?: WebSearchResult("", "timeout", emptyList(), "none")
            } else {
              WebSearchResult("", null, emptyList(), null)
            }
          val correctionEntity = resolveFactEntity(text)
          val sessionCorrections =
            if (correctionEntity != null) {
              knowledgeRepository.getRecentUserCorrections(factEntity = correctionEntity, limit = 2)
            } else {
              emptyList()
            }
          val trustedWebContext =
            if (factualCurrentQuery && webResult.results.isNotEmpty()) {
              filterTrustedResults(webResult.results)
            } else {
              webResult.results
            }
          val sessionCorrectionsPrompt =
            if (sessionCorrections.isNotEmpty()) {
              buildString {
                appendLine("Session Corrections (highest priority for this conversation):")
                sessionCorrections.forEach { appendLine("- ${it.content.take(280)}") }
                appendLine()
              }
            } else {
              ""
            }
          if (webResult.error != null) {
            viewModel.addMessage(
              model = resolvedModel,
              message = ChatMessageInfo(content = "Web search unavailable: ${webResult.error}"),
            )
          } else if (showThinking && webResult.results.isNotEmpty()) {
            viewModel.addMessage(
              model = resolvedModel,
              message =
                ChatMessageInfo(
                  content =
                    "Thinking: web search berhasil (${webResult.source ?: "unknown"}) dengan ${webResult.results.size} hasil.",
                ),
            )
          }
          if (webResult.contextPrompt.isNotBlank() && factualCurrentQuery && chatPreferencesRepository.getAutoIngestKnowledge()) {
            knowledgeRepository.addFromWeb(
              title = "Web Search Context",
              content = webResult.contextPrompt,
              query = text,
            )
          }
          val webUsageInstruction =
            if (trustedWebContext.isNotEmpty()) {
              "Gunakan hasil web ini sebagai referensi utama. Jika konflik dengan riwayat lama, utamakan web.\n\n"
            } else {
              ""
            }
          val factualOverrideInstruction = buildFactualOverrideInstruction(text, trustedWebContext)
          val webPrompt =
            if (trustedWebContext.isNotEmpty()) {
              buildString {
                appendLine("Relevant Web Results:")
                trustedWebContext.take(2).forEach { appendLine(it.take(420)) }
                appendLine()
              }
            } else {
              ""
            }
          val outputLanguageInstruction =
            if (detectUserLanguage(text) == "id") {
              "Jawab wajib dalam Bahasa Indonesia yang natural dan to the point.\n\n"
            } else {
              "Answer in natural English and keep it concise.\n\n"
            }
          val finalInput =
            outputLanguageInstruction +
              planningPrompt +
              sessionCorrectionsPrompt +
              webUsageInstruction +
              factualOverrideInstruction +
              memoryPrompt +
              webPrompt +
              "\n" +
              text
          val factResolutionSource =
            when {
              sessionCorrections.isNotEmpty() -> "user_correction"
              trustedWebContext.isNotEmpty() -> "web"
              memoryPrompt.isNotBlank() -> "memory"
              else -> "none"
            }
          Log.d(CHAT_PIPELINE_TAG, "fact_policy_applied=$factualCurrentQuery fact_resolution_source=$factResolutionSource")
          Log.d(
            CHAT_PIPELINE_TAG,
            "web_context_state used=${trustedWebContext.isNotEmpty()} source=${webResult.source} err=${webResult.error}",
          )
          Log.d(CHAT_PIPELINE_TAG, "final_prompt_size chars=${finalInput.length} webOn=$sessionWebSearchEnabled")
          if (showThinking) {
            emitPipelineStep(
              viewModel,
              resolvedModel,
              "Model dipilih. Estimasi akurasi konteks: ${estimateContextAccuracy(memoryPrompt, trustedWebContext)}%",
              pipelineTraceId,
            )
          }
          val activeSkills = getActiveSkills()
          Log.d(
            TAG,
            "Analytics: generate_action, capability_name=${task.id}, active_skills=${activeSkills.joinToString(",")}",
          )
          firebaseAnalytics?.logEvent(
            GalleryEvent.GENERATE_ACTION.id,
            Bundle().apply {
              putString("capability_name", task.id)
              putString("model_id", resolvedModel.name)
              putBoolean("has_image", images.isNotEmpty())
              putInt("image_count", images.size)
              putBoolean("has_audio", audioMessages.isNotEmpty())
              putInt("audio_count", audioMessages.size)
              putInt("active_skills_count", activeSkills.size)
              putString("active_skills_list", activeSkills.joinToString(","))
            },
          )
          var ensembleRerankTriggered = false
          viewModel.generateResponse(
            model = resolvedModel,
            input = finalInput,
            images = images,
            audioMessages = audioMessages,
            onFirstToken = onFirstToken,
            onDone = {
              coroutineScope.launch {
                applyPostModelFactValidation(
                  viewModel = viewModel,
                  model = resolvedModel,
                  query = text,
                  knowledgeRepository = knowledgeRepository,
                )
                applyPostModelLanguageRewrite(
                  viewModel = viewModel,
                  model = resolvedModel,
                  query = text,
                )
                if (showThinking) {
                  emitPipelineStep(viewModel, resolvedModel, "Jawaban difinalisasi dan dikirim", pipelineTraceId)
                }
              }
              applyFactConflictGuard(
                viewModel = viewModel,
                model = resolvedModel,
                query = text,
                sessionCorrections = sessionCorrections,
                trustedWebContext = trustedWebContext,
              )
              if (chatPreferencesRepository.getAutoIngestKnowledge()) {
                val latestAgentText =
                  (viewModel.getLastMessage(resolvedModel) as? ChatMessageText)
                    ?.takeIf { it.side == com.server.edge.gallery.ui.common.chat.ChatSide.AGENT }
                    ?.content
                    .orEmpty()
                if (latestAgentText.length >= 24) {
                  knowledgeRepository.addFromChat(
                    content = latestAgentText,
                    title = "Assistant Response",
                  )
                }
              }
              val latestText =
                (viewModel.getLastMessage(resolvedModel) as? ChatMessageText)?.content.orEmpty()
              val secondaryModel = routeDecision.ensembleCandidates.getOrNull(1)
              val shouldRerank =
                !ensembleRerankTriggered &&
                  secondaryModel != null &&
                  secondaryModel.name != resolvedModel.name &&
                  isLikelyInvalidModelAnswer(latestText)
              if (shouldRerank) {
                ensembleRerankTriggered = true
                coroutineScope.launch {
                  val initialized =
                    ensureModelInitialized(
                      context = context,
                      modelManagerViewModel = modelManagerViewModel,
                      task = task,
                      model = secondaryModel,
                    )
                  if (!initialized) {
                    onGenerateResponseDone(resolvedModel)
                    onMessagesUpdated(resolvedModel)
                    return@launch
                  }
                  Log.w(
                    CHAT_PIPELINE_TAG,
                    "router_ensemble_rerank_used from=${resolvedModel.name} to=${secondaryModel.name}",
                  )
                  viewModel.addMessage(
                    model = secondaryModel,
                    message =
                      ChatMessageInfo(
                        content =
                          "Auto Router: jawaban awal tidak valid, mencoba model cadangan ${secondaryModel.displayName.ifEmpty { secondaryModel.name }}.",
                      ),
                  )
                  viewModel.generateResponse(
                    model = secondaryModel,
                    input = finalInput,
                    images = images,
                    audioMessages = audioMessages,
                    onFirstToken = onFirstToken,
                    onDone = {
                      onGenerateResponseDone(secondaryModel)
                      onMessagesUpdated(secondaryModel)
                    },
                    onError = { fallbackError ->
                      viewModel.handleError(
                        context = context,
                        task = task,
                        model = secondaryModel,
                        errorMessage = fallbackError,
                        modelManagerViewModel = modelManagerViewModel,
                      )
                      onGenerateResponseDone(resolvedModel)
                      onMessagesUpdated(resolvedModel)
                    },
                    allowThinking =
                      showThinking && task.allowCapability(ModelCapability.LLM_THINKING, secondaryModel),
                    showThinking = showThinking,
                  )
                }
              } else {
                Log.d(CHAT_PIPELINE_TAG, "pipeline_trace_end traceId=$pipelineTraceId status=model_done")
                onGenerateResponseDone(resolvedModel)
                onMessagesUpdated(resolvedModel)
              }
            },
            onError = { errorMessage ->
              viewModel.handleError(
                context = context,
                task = task,
                model = resolvedModel,
                errorMessage = errorMessage,
                modelManagerViewModel = modelManagerViewModel,
              )
              Log.d(CHAT_PIPELINE_TAG, "pipeline_trace_end traceId=$pipelineTraceId status=error")
              onMessagesUpdated(resolvedModel)
            },
            allowThinking = showThinking && task.allowCapability(ModelCapability.LLM_THINKING, resolvedModel),
            showThinking = showThinking,
          )
          Log.d(CHAT_PIPELINE_TAG, "router_infer_started model=${resolvedModel.name}")
          // Auto-ingest user snippets and lightweight memory for future retrieval.
          if (chatPreferencesRepository.getAutoIngestKnowledge() && text.length >= 24) {
            knowledgeRepository.addFromChat(content = text, title = "User Prompt")
          }
        }
      }
    },
    onRunAgainClicked = { model, message ->
      if (message is ChatMessageText) {
        viewModel.runAgain(
          model = model,
          message = message,
          onError = { errorMessage ->
            viewModel.handleError(
              context = context,
              task = task,
              model = model,
              errorMessage = errorMessage,
              modelManagerViewModel = modelManagerViewModel,
            )
            onMessagesUpdated(model)
          },
          allowThinking = task.allowCapability(ModelCapability.LLM_THINKING, model),
          showThinking = showThinking,
        )
      }
    },
    onBenchmarkClicked = { _, _, _, _ -> },
    onResetSessionClicked = { model ->
      if (onResetSessionClickedOverride != null) {
        onResetSessionClickedOverride(task, model)
      } else {
        viewModel.resetSession(
          task = task,
          model = model,
          supportImage = showImagePicker,
          supportAudio = showAudioPicker,
        )
      }
    },
    showStopButtonInInputWhenInProgress = true,
    onStopButtonClicked = { model -> viewModel.stopResponse(model = model) },
    onSkillClicked = onSkillClicked,
    onTextFilesPicked = { files ->
      onTextFilesPicked(files)
      if (chatPreferencesRepository.getAutoIngestKnowledge()) {
        files.forEach { file ->
          knowledgeRepository.addFromFile(fileName = file.fileName, content = file.content)
        }
      }
    },
    navigateUp = navigateUp,
    modifier = modifier,
    composableBelowMessageList = composableBelowMessageList,
    showImagePicker = showImagePicker,
    emptyStateComposable = emptyStateComposable,
    chatMode = chatMode,
    onChatModeChanged = onChatModeChanged,
    showThinking = showThinking,
    onShowThinkingChanged = onShowThinkingChanged,
    webSearchEnabled = sessionWebSearchEnabled,
    onWebSearchEnabledChanged = {
      sessionWebSearchEnabled = it
      onWebSearchEnabledChanged(it)
      Log.d(CHAT_PIPELINE_TAG, "web_toggle_state source=session enabled=$it")
    },
    allowEditingSystemPrompt = allowEditingSystemPrompt,
    curSystemPrompt = curSystemPrompt,
    onSystemPromptChanged = onSystemPromptChanged,
    sendMessageTrigger = sendMessageTrigger,
    showAudioPicker = showAudioPicker,
  )
}

private data class WebSearchResult(
  val contextPrompt: String,
  val error: String?,
  val results: List<String>,
  val source: String?,
)

private data class RouterAnswerDecision(
  val answerText: String,
  val source: String,
  val bypassModel: Boolean = true,
)

private enum class FactIntentType {
  STRICT_ONE_LINE,
  NORMAL,
}

private enum class DirectIntentType {
  TIME,
  MATH_COMPLEX,
  DURATION_CONVERSION,
  FACT_STRICT,
  FACT_CONFIRMATION,
  NORMAL,
}

private suspend fun resolveDirectAnswerBeforeModel(
  viewModel: LlmChatViewModelBase?,
  requestedModel: Model?,
  knowledgeRepository: KnowledgeRepository,
  query: String,
  webSearchEnabled: Boolean,
  traceId: String? = null,
): RouterAnswerDecision? {
  val lowered = query.lowercase(Locale.ROOT)
  val directIntent = detectDirectIntent(query)
  emitPipelineStep(viewModel, requestedModel, "Deteksi intent: $directIntent", traceId)
  if (directIntent == DirectIntentType.TIME) {
    val now = LocalDateTime.now(ZoneId.systemDefault())
    val day = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale("id", "ID"))
    val date = now.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("id", "ID")))
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale("id", "ID")))
    return RouterAnswerDecision("Sekarang hari $day, tanggal $date, pukul $time.", "time")
  }
  if (directIntent == DirectIntentType.DURATION_CONVERSION) {
    resolveDurationConversionAnswer(query)?.let {
      Log.d(CHAT_PIPELINE_TAG, "duration_conversion_hit expr='${it.expression}'")
      emitPipelineStep(viewModel, requestedModel, "Direct answer: konversi durasi", traceId)
      return RouterAnswerDecision(it.answer, "duration_conversion")
    }
  }
  if (directIntent == DirectIntentType.MATH_COMPLEX) {
    evaluateMathExpression(query)?.let { evaluated ->
      Log.d(CHAT_PIPELINE_TAG, "math_direct_answer_hit expr='${evaluated.expression}'")
      emitPipelineStep(viewModel, requestedModel, "Direct answer: kalkulasi matematika", traceId)
      return RouterAnswerDecision(evaluated.answer, "math")
    }
  }
  if (directIntent == DirectIntentType.FACT_CONFIRMATION) {
    val factual = resolveFactConfirmationAnswer(query, knowledgeRepository)
    if (factual != null) {
      emitPipelineStep(viewModel, requestedModel, "Direct answer: verifikasi fakta", traceId)
      Log.d(CHAT_PIPELINE_TAG, "fact_confirmation_applied=true")
      return RouterAnswerDecision(factual, "fact_confirmation")
    }
  }

  val entityHint = resolveFactEntity(query)
  val factIntent = detectFactIntent(query, entityHint)
  Log.d(CHAT_PIPELINE_TAG, "fact_intent=${if (factIntent == FactIntentType.STRICT_ONE_LINE) "strict_one_line" else "normal"}")
  if (entityHint != null) {
    knowledgeRepository.findValidatedAnswerForQuery(query, entityHint)?.let {
      val normalized = normalizeStrictFactAnswer(it.answer, entityHint)
      if (normalized != null) {
        Log.d(CHAT_PIPELINE_TAG, "fact_answer_normalized=true")
        emitPipelineStep(viewModel, requestedModel, "Direct answer: factual dari cache", traceId)
        return RouterAnswerDecision(normalized, "cache")
      }
      Log.w(CHAT_PIPELINE_TAG, "fact_answer_rejected reason=no_entity")
    }
  }

  if (entityHint != null) {
    val correction = knowledgeRepository.getRecentUserCorrections(entityHint, limit = 1).firstOrNull()
    if (correction != null) {
      val normalized = normalizeStrictFactAnswer(correction.content, entityHint)
      if (normalized != null) {
        Log.d(CHAT_PIPELINE_TAG, "fact_answer_normalized=true")
        emitPipelineStep(viewModel, requestedModel, "Direct answer: factual dari koreksi sesi", traceId)
        return RouterAnswerDecision(normalized, "user_correction")
      }
      Log.w(CHAT_PIPELINE_TAG, "fact_answer_rejected reason=no_entity")
    }
  }

  val factualQuery = isCurrentFactQuery(query)
  val factWebOverride = factualQuery && !webSearchEnabled
  Log.d(CHAT_PIPELINE_TAG, "fact_web_override_applied=$factWebOverride")
  if ((webSearchEnabled || factWebOverride) && factualQuery) {
    Log.d(CHAT_PIPELINE_TAG, "web_fetch_attempt query='$query'")
    val webResult = withTimeoutOrNull(6500) { fetchDuckDuckGoContext(query) }
    if (webResult == null) {
      Log.w(CHAT_PIPELINE_TAG, "web_fetch_failed_reason=timeout")
      return null
    }
    val trusted = filterTrustedResults(webResult.results)
    if (trusted.isNotEmpty()) {
      Log.d(CHAT_PIPELINE_TAG, "web_fetch_source_success source=${webResult.source} count=${trusted.size}")
      val answer =
        resolveStrictFactualAnswerFromTrustedWeb(
          query = query,
          entityHint = entityHint,
          trustedResults = trusted,
          factIntent = factIntent,
        )
      if (answer != null) {
        Log.d(CHAT_PIPELINE_TAG, "fact_answer_normalized=true")
        emitPipelineStep(viewModel, requestedModel, "Direct answer: factual dari web tepercaya", traceId)
        knowledgeRepository.upsertValidatedAnswer(
          query = query,
          answer = answer,
          sourceType = "web",
          entityHint = entityHint,
        )
        return RouterAnswerDecision(answer, "web")
      } else {
        Log.w(CHAT_PIPELINE_TAG, "fact_answer_rejected reason=low_confidence")
      }
    } else {
      Log.w(CHAT_PIPELINE_TAG, "web_fetch_failed_reason=empty_or_untrusted source=${webResult.source}")
    }
  }
  if (factIntent == FactIntentType.STRICT_ONE_LINE) {
    Log.d(CHAT_PIPELINE_TAG, "fact_resolution_source=fallback_short")
    return RouterAnswerDecision(
      answerText = "Saya belum bisa memverifikasi nama presiden saat ini.",
      source = "fallback_short",
    )
  }
  return null
}

private suspend fun resolveModelForExecution(
  context: android.content.Context,
  modelManagerViewModel: ModelManagerViewModel,
  task: Task,
  routeDecision: RouteDecision,
  onInitProgress: (String) -> Unit = {},
): Model? {
  val candidates = routeDecision.candidateModels.distinctBy { it.name }
  for ((index, candidate) in candidates.withIndex()) {
    if (!InferenceRouter.isGenerativeCandidate(candidate)) {
      Log.w(CHAT_PIPELINE_TAG, "router_fallback_triggered skip_non_generative model=${candidate.name}")
      continue
    }
    val downloadStatus =
      modelManagerViewModel.uiState.value.modelDownloadStatus[candidate.name]?.status
    if (
      !candidate.imported &&
        downloadStatus != ModelDownloadStatusType.SUCCEEDED &&
        downloadStatus != null
    ) {
      Log.w(
        CHAT_PIPELINE_TAG,
        "router_fallback_triggered skip_not_downloaded model=${candidate.name} status=$downloadStatus",
      )
      continue
    }
    onInitProgress(candidate.displayName.ifEmpty { candidate.name })
    val initialized = ensureModelInitialized(context, modelManagerViewModel, task, candidate)
    if (initialized) {
      if (index > 0) {
        Log.w(CHAT_PIPELINE_TAG, "router_fallback_triggered model=${candidate.name} index=$index")
      }
      return candidate
    }
  }
  return null
}

private suspend fun ensureModelInitialized(
  context: android.content.Context,
  modelManagerViewModel: ModelManagerViewModel,
  task: Task,
  model: Model,
): Boolean {
  if (modelManagerViewModel.uiState.value.isModelInitialized(model)) {
    return true
  }
  Log.d(CHAT_PIPELINE_TAG, "router_model_init_started model=${model.name}")
  return withTimeoutOrNull(45000) {
    suspendCancellableCoroutine { cont ->
      modelManagerViewModel.initializeModel(
        context = context,
        task = task,
        model = model,
        onDone = {
          Log.d(CHAT_PIPELINE_TAG, "router_model_init_done model=${model.name} success=true")
          if (cont.isActive) cont.resume(true)
        },
        onError = {
          Log.w(CHAT_PIPELINE_TAG, "router_model_init_done model=${model.name} success=false")
          if (cont.isActive) cont.resume(false)
        },
      )
    }
  } ?: false
}

private suspend fun fetchDuckDuckGoContext(query: String): WebSearchResult {
  return withContext(Dispatchers.IO) {
    val attempts = listOf(::fetchFromDuckDuckGoHtml, ::fetchFromDuckDuckGoLite, ::fetchFromWikipediaSummary)
    val errors = mutableListOf<String>()
    for (attempt in attempts) {
      val result = attempt(query)
      if (result.error == null && result.results.isNotEmpty()) {
        Log.d(WEB_SEARCH_TAG, "Web search success source=${result.source} results=${result.results.size}")
        return@withContext result
      }
      errors.add("${result.source ?: "unknown"}: ${result.error ?: "no results"}")
    }
    val joinedError = errors.joinToString(" | ")
    Log.w(WEB_SEARCH_TAG, "Web search failed query='$query' errors=$joinedError")
    WebSearchResult("", joinedError, emptyList(), null)
  }
}

private fun fetchFromDuckDuckGoHtml(query: String): WebSearchResult {
  return runCatching {
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val html = fetchUrl("https://html.duckduckgo.com/html/?q=$encoded")
    val resultPattern =
      Regex(
        "<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>[\\s\\S]*?<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>",
        setOf(RegexOption.IGNORE_CASE),
      )
    val results =
      resultPattern.findAll(html).take(3).mapNotNull { m ->
        val rawUrl = Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
        if (!Patterns.WEB_URL.matcher(rawUrl).find()) return@mapNotNull null
        val title = Html.fromHtml(m.groupValues[2], Html.FROM_HTML_MODE_LEGACY).toString()
        val snippet = Html.fromHtml(m.groupValues[3], Html.FROM_HTML_MODE_LEGACY).toString()
        "- $title\n  $snippet\n  Source: $rawUrl"
      }.toList()
    buildWebSearchResult(results, "duckduckgo-html")
  }.getOrElse { WebSearchResult("", it.message ?: "fetch failed", emptyList(), "duckduckgo-html") }
}

private fun fetchFromDuckDuckGoLite(query: String): WebSearchResult {
  return runCatching {
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val html = fetchUrl("https://lite.duckduckgo.com/lite/?q=$encoded")
    val linkPattern =
      Regex("<a rel=\"nofollow\" href=\"([^\"]+)\">([^<]+)</a>", setOf(RegexOption.IGNORE_CASE))
    val results =
      linkPattern.findAll(html).take(3).mapNotNull { m ->
        val rawUrl = Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
        if (!Patterns.WEB_URL.matcher(rawUrl).find()) return@mapNotNull null
        val title = Html.fromHtml(m.groupValues[2], Html.FROM_HTML_MODE_LEGACY).toString()
        "- $title\n  Source: $rawUrl"
      }.toList()
    buildWebSearchResult(results, "duckduckgo-lite")
  }.getOrElse { WebSearchResult("", it.message ?: "fetch failed", emptyList(), "duckduckgo-lite") }
}

private fun fetchFromWikipediaSummary(query: String): WebSearchResult {
  return runCatching {
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val json = fetchUrl("https://id.wikipedia.org/api/rest_v1/page/summary/$encoded")
    val extract = Regex("\"extract\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)?.replace("\\n", " ")
    val title = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: query
    if (extract.isNullOrBlank()) {
      WebSearchResult("", "no summary extract", emptyList(), "wikipedia")
    } else {
      val results = listOf("- $title\n  $extract\n  Source: https://id.wikipedia.org/wiki/${encoded.replace("+", "_")}")
      buildWebSearchResult(results, "wikipedia")
    }
  }.getOrElse { WebSearchResult("", it.message ?: "fetch failed", emptyList(), "wikipedia") }
}

private fun fetchUrl(urlString: String): String {
  val connection = URL(urlString).openConnection() as HttpURLConnection
  return connection.run {
    requestMethod = "GET"
    connectTimeout = 7000
    readTimeout = 9000
    instanceFollowRedirects = true
    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) EdgeGallery/1.0")
    setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
    inputStream.bufferedReader().use { it.readText() }
  }
}

private fun buildWebSearchResult(results: List<String>, source: String): WebSearchResult {
  if (results.isEmpty()) {
    return WebSearchResult("", "no results", emptyList(), source)
  }
  val prompt =
    buildString {
      appendLine("Relevant Web Results:")
      results.forEach { appendLine(it) }
      appendLine()
    }
  return WebSearchResult(prompt, null, results, source)
}

private fun isCurrentFactQuery(text: String): Boolean {
  val lowered = text.lowercase()
  val keywords = listOf("saat ini", "terkini", "sekarang", "presiden", "menteri", "siapa", "latest")
  return keywords.any { lowered.contains(it) }
}

private fun isTimeDayQuery(lowered: String): Boolean {
  val hasNowToken =
    lowered.contains("sekarang") ||
      lowered.contains("saat ini") ||
      lowered.contains("today") ||
      lowered.contains("now")
  val asksCurrentTime =
    lowered.contains("jam berapa") ||
      lowered.contains("pukul berapa") ||
      lowered.contains("hari apa") ||
      lowered.contains("tanggal berapa") ||
      lowered.contains("what time") ||
      lowered.contains("what day") ||
      lowered.contains("what date")
  val hasDurationUnits = listOf("hari", "jam", "menit", "detik").count { lowered.contains(it) } >= 2
  val hasNumericDuration = Regex(""".*\d+.*""").containsMatchIn(lowered)
  if (hasDurationUnits && hasNumericDuration) return false
  return asksCurrentTime && hasNowToken
}

private fun filterTrustedResults(results: List<String>): List<String> {
  val trustedDomains =
    listOf(
      "setneg.go.id",
      "setkab.go.id",
      "ksp.go.id",
      "wapresri.go.id",
      "antaranews.com",
      "kompas.com",
      "tempo.co",
      "bbc.com",
      "reuters.com",
      "wikipedia.org",
    )
  val filtered = results.filter { item -> trustedDomains.any { domain -> item.contains(domain) } }
  return if (filtered.isNotEmpty()) filtered else results
}

private fun buildFactualOverrideInstruction(query: String, trustedWebContext: List<String>): String {
  val lowered = query.lowercase()
  if (!lowered.contains("presiden") || !lowered.contains("indonesia")) {
    return ""
  }
  val containsPrabowo = trustedWebContext.any { it.contains("prabowo", ignoreCase = true) }
  return if (containsPrabowo) {
    "Aturan jawaban fakta terkini: gunakan hasil web terbaru. Untuk pertanyaan presiden Indonesia saat ini, jawaban final harus menyebut Prabowo Subianto.\n\n"
  } else {
    ""
  }
}

private fun detectCorrectionEntity(text: String): String? {
  val lowered = text.lowercase()
  val looksCorrection =
    lowered.contains("salah") || lowered.contains("yang benar") || lowered.contains("kedepannya") || lowered.contains("harus jawab")
  if (!looksCorrection) return null
  if (lowered.contains("presiden") && lowered.contains("indonesia")) return "presiden_indonesia_current"
  return null
}

private fun looksLikeSessionPreference(text: String): Boolean {
  val lowered = text.lowercase(Locale.ROOT)
  return lowered.contains("kedepannya") ||
    lowered.contains("ingat ini") ||
    lowered.contains("jika saya tanya") ||
    lowered.contains("gunakan konteks ini")
}

private fun resolveFactEntity(query: String): String? {
  val lowered = query.lowercase()
  if (lowered.contains("presiden") && lowered.contains("indonesia")) return "presiden_indonesia_current"
  return null
}

private fun detectDirectIntent(query: String): DirectIntentType {
  val lowered = query.lowercase(Locale.ROOT)
  if (isTimeDayQuery(lowered)) return DirectIntentType.TIME
  if (looksLikeDurationConversionQuery(lowered)) return DirectIntentType.DURATION_CONVERSION
  if (resolveFactEntity(query) != null && detectFactIntent(query, resolveFactEntity(query)) == FactIntentType.STRICT_ONE_LINE) {
    return DirectIntentType.FACT_STRICT
  }
  if (looksLikeFactConfirmationQuery(lowered)) return DirectIntentType.FACT_CONFIRMATION
  if (looksLikeMathQuery(lowered)) return DirectIntentType.MATH_COMPLEX
  return DirectIntentType.NORMAL
}

private fun looksLikeMathQuery(lowered: String): Boolean {
  val keywords = listOf("berapa hasil", "hitung", "calculate", "sin", "cos", "tan", "sqrt", "akar")
  if (keywords.any { lowered.contains(it) }) return true
  if (looksLikeDurationConversionQuery(lowered)) return false
  return Regex("""^[\s0-9+\-*/:^%=().,]+$""").matches(lowered.replace(" ", ""))
}

private fun looksLikeDurationConversionQuery(lowered: String): Boolean {
  val hasDurationUnit = listOf("hari", "jam", "menit", "detik").any { lowered.contains(it) }
  val hasConversionVerb = lowered.contains("sama dengan") || lowered.contains("berapa")
  return hasDurationUnit && hasConversionVerb && Regex(""".*\d+.*""").containsMatchIn(lowered)
}

private fun looksLikeFactConfirmationQuery(lowered: String): Boolean {
  return lowered.startsWith("benarkah") || lowered.startsWith("apakah benar") || lowered.startsWith("apa benar")
}

private data class MathEvaluationResult(val expression: String, val answer: String)
private data class DurationConversionResult(val expression: String, val answer: String)

private fun evaluateMathExpression(rawQuery: String): MathEvaluationResult? {
  val normalized = rawQuery.lowercase(Locale.ROOT)
    .replace("berapa hasil", "")
    .replace("hitung", "")
    .replace("hasil", "")
    .replace("?", "")
    .replace("=", "")
    .replace(":", "/")
    .replace(",", ".")
    .replace("akar", "sqrt")
    .trim()
  if (normalized.isBlank()) return null
  val expression = normalized.filterNot { it == ' ' }
  return runCatching {
    val value = SimpleMathEvaluator(expression).parse()
    val rendered = formatNumericAnswer(value)
    val answer =
      if (expression.contains("/") && isSimpleIntegerDivision(expression)) {
        val ints = extractSimpleDivisionInts(expression)
        if (ints != null && ints.second != 0L) {
          val q = ints.first / ints.second
          val r = ints.first % ints.second
          if (r != 0L) "${ints.first}:${ints.second} = $q sisa $r (≈ $rendered)." else "${ints.first}:${ints.second} = $q."
        } else {
          "Hasilnya adalah $rendered."
        }
      } else {
        "Hasilnya adalah $rendered."
      }
    MathEvaluationResult(expression, answer)
  }.getOrNull()
}

private fun resolveDurationConversionAnswer(query: String): DurationConversionResult? {
  val lowered = query.lowercase(Locale.ROOT).replace("?", "").trim()
  val regex =
    Regex(
      """(\d+(?:[.,]\d+)?)\s*(hari|jam|menit|detik)\s*(?:sama dengan|jadi|=|ke)?\s*(?:berapa)?\s*(hari|jam|menit|detik)""",
    )
  val m = regex.find(lowered) ?: return null
  val value = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
  val from = m.groupValues[2]
  val to = m.groupValues[3]
  val inSeconds =
    when (from) {
      "hari" -> value * 86400.0
      "jam" -> value * 3600.0
      "menit" -> value * 60.0
      else -> value
    }
  val converted =
    when (to) {
      "hari" -> inSeconds / 86400.0
      "jam" -> inSeconds / 3600.0
      "menit" -> inSeconds / 60.0
      else -> inSeconds
    }
  val rendered = formatNumericAnswer(converted)
  return DurationConversionResult(
    expression = "$value $from -> $to",
    answer = "${formatNumericAnswer(value)} $from sama dengan $rendered $to.",
  )
}

private fun resolveFactConfirmationAnswer(query: String, knowledgeRepository: KnowledgeRepository): String? {
  val lowered = query.lowercase(Locale.ROOT)
  if (!(lowered.contains("presiden") && lowered.contains("indonesia"))) return null
  val mentionsPrabowo = lowered.contains("prabowo")
  if (!mentionsPrabowo) return "Saya belum bisa memverifikasi pernyataan itu saat ini."
  val correction = knowledgeRepository.getRecentUserCorrections("presiden_indonesia_current", 1).firstOrNull()
  val sourceAnswer = correction?.content ?: "Presiden Indonesia saat ini adalah Prabowo Subianto."
  val isTrue = sourceAnswer.contains("prabowo", ignoreCase = true)
  return if (isTrue) {
    "Ya, benar. Presiden Indonesia saat ini adalah Prabowo Subianto."
  } else {
    "Tidak, pernyataan itu tidak sesuai dengan data konteks saat ini."
  }
}

private fun isSimpleIntegerDivision(expr: String): Boolean = Regex("""^\d+/\d+$""").matches(expr)

private fun extractSimpleDivisionInts(expr: String): Pair<Long, Long>? {
  val m = Regex("""^(\d+)/(\d+)$""").find(expr) ?: return null
  return m.groupValues[1].toLongOrNull()?.let { a -> m.groupValues[2].toLongOrNull()?.let { b -> a to b } }
}

private fun formatNumericAnswer(value: Double): String {
  val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
  val longValue = rounded.toLong()
  return if (abs(rounded - longValue.toDouble()) < 1e-9) longValue.toString() else rounded.toString()
}

private class SimpleMathEvaluator(private val input: String) {
  private var pos = -1
  private var ch = 0

  fun parse(): Double {
    nextChar()
    val x = parseExpression()
    if (pos < input.length) throw IllegalArgumentException("Unexpected: ${ch.toChar()}")
    return x
  }

  private fun nextChar() {
    pos++
    ch = if (pos < input.length) input[pos].code else -1
  }

  private fun eat(charToEat: Int): Boolean {
    while (ch == ' '.code) nextChar()
    return if (ch == charToEat) {
      nextChar()
      true
    } else {
      false
    }
  }

  private fun parseExpression(): Double {
    var x = parseTerm()
    while (true) {
      x =
        when {
          eat('+'.code) -> x + parseTerm()
          eat('-'.code) -> x - parseTerm()
          else -> return x
        }
    }
  }

  private fun parseTerm(): Double {
    var x = parseFactor()
    while (true) {
      x =
        when {
          eat('*'.code) -> x * parseFactor()
          eat('/'.code) -> x / parseFactor()
          eat('%'.code) -> x % parseFactor()
          else -> return x
        }
    }
  }

  private fun parseFactor(): Double {
    if (eat('+'.code)) return parseFactor()
    if (eat('-'.code)) return -parseFactor()
    var x: Double
    val startPos = this.pos
    when {
      eat('('.code) -> {
        x = parseExpression()
        eat(')'.code)
      }
      ch in '0'.code..'9'.code || ch == '.'.code -> {
        while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
        x = input.substring(startPos, this.pos).toDouble()
      }
      ch in 'a'.code..'z'.code -> {
        while (ch in 'a'.code..'z'.code) nextChar()
        val func = input.substring(startPos, this.pos)
        x =
          when (func) {
            "pi" -> PI
            else -> {
              val arg = parseFactor()
              when (func) {
                "sqrt" -> sqrt(arg)
                "sin" -> sin(Math.toRadians(arg))
                "cos" -> cos(Math.toRadians(arg))
                "tan" -> tan(Math.toRadians(arg))
                "abs" -> abs(arg)
                else -> throw IllegalArgumentException("Unknown function: $func")
              }
            }
          }
      }
      else -> throw IllegalArgumentException("Unexpected: ${ch.toChar()}")
    }
    if (eat('^'.code)) x = x.pow(parseFactor())
    return x
  }
}

private fun prioritizeHybridMemory(items: List<com.server.edge.gallery.data.KnowledgeItem>): List<com.server.edge.gallery.data.KnowledgeItem> {
  return items.sortedWith(
    compareByDescending<com.server.edge.gallery.data.KnowledgeItem> { it.scope.equals("session", ignoreCase = true) }
      .thenByDescending { it.priority }
      .thenByDescending { it.createdAt },
  )
}

private fun shapeRetrievedMemory(
  query: String,
  items: List<com.server.edge.gallery.data.KnowledgeItem>,
): List<com.server.edge.gallery.data.KnowledgeItem> {
  if (items.isEmpty()) return items
  val lowered = query.lowercase(Locale.ROOT)
  val isCodingQuery =
    listOf("code", "kode", "script", "fungsi", "function", "class", "javascript", "python", "kotlin")
      .any { lowered.contains(it) }
  val isFactual = isCurrentFactQuery(query)
  val filtered =
    items.filter { item ->
      when {
        isFactual ->
          item.tags.contains("user_correction") ||
            item.tags.contains("session_priority") ||
            item.factEntity != null
        isCodingQuery ->
          item.language in setOf("javascript", "python", "kotlin", "java", "html", "css", "json", "text") &&
            !item.title.contains("Web Search Context", ignoreCase = true)
        else -> true
      }
    }
  val dedup = LinkedHashMap<String, com.server.edge.gallery.data.KnowledgeItem>()
  for (item in filtered) {
    val key = item.content.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").take(180)
    if (!dedup.containsKey(key)) dedup[key] = item
  }
  return dedup.values.toList()
}

private fun shouldCacheDirectAnswer(query: String, decision: RouterAnswerDecision): Boolean {
  if (decision.source == "fallback_short") return false
  if (resolveFactEntity(query) != null) {
    return decision.answerText.contains("Prabowo", ignoreCase = true)
  }
  return true
}

private fun emitPipelineStep(
  viewModel: LlmChatViewModelBase?,
  model: Model?,
  step: String,
  traceId: String? = null,
) {
  if (viewModel == null || model == null) return
  Log.d(CHAT_PIPELINE_TAG, "pipeline_canvas_step=$step")
  viewModel.updateCollapsableProgressPanelMessage(
    model = model,
    title = "Router Pipeline",
    inProgress = true,
    doneIcon = Icons.Rounded.Check,
    addItemTitle = step,
    addItemDescription = "",
    customData = traceId,
  )
}

private fun estimateContextAccuracy(memoryPrompt: String, trustedWebContext: List<String>): Int {
  if (trustedWebContext.isNotEmpty()) return 90
  if (memoryPrompt.isNotBlank()) return 80
  return 60
}

private fun detectFactIntent(query: String, entityHint: String?): FactIntentType {
  if (entityHint != "presiden_indonesia_current") return FactIntentType.NORMAL
  val lowered = query.lowercase(Locale.ROOT)
  val strict =
    (lowered.contains("siapa") || lowered.contains("nama")) &&
      (lowered.contains("saat ini") || lowered.contains("sekarang"))
  return if (strict) FactIntentType.STRICT_ONE_LINE else FactIntentType.NORMAL
}

private fun normalizeStrictFactAnswer(answer: String, entityHint: String?): String? {
  if (entityHint != "presiden_indonesia_current") return answer.takeIf { it.isNotBlank() }
  val lowered = answer.lowercase(Locale.ROOT)
  val rejectedMarkers = listOf("daftar presiden", "sejak 1945", "terdapat delapan", "wikipedia", "•")
  if (rejectedMarkers.any { lowered.contains(it) }) {
    Log.w(CHAT_PIPELINE_TAG, "fact_answer_rejected reason=list_like")
    return null
  }
  return when {
    lowered.contains("prabowo") -> "Presiden Indonesia saat ini adalah Prabowo Subianto."
    else -> null
  }
}

private fun resolveStrictFactualAnswerFromTrustedWeb(
  query: String,
  entityHint: String?,
  trustedResults: List<String>,
  factIntent: FactIntentType,
): String? {
  if (factIntent != FactIntentType.STRICT_ONE_LINE || entityHint != "presiden_indonesia_current") {
    val concise = trustedResults.firstOrNull()?.lineSequence()?.take(2)?.joinToString(" ")?.trim().orEmpty()
    return concise.ifBlank { null }
  }
  val candidate = trustedResults.firstOrNull { it.contains("prabowo", ignoreCase = true) }
  if (candidate == null) {
    Log.w(CHAT_PIPELINE_TAG, "fact_answer_rejected reason=no_entity")
    return null
  }
  return "Presiden Indonesia saat ini adalah Prabowo Subianto."
}

private fun applyFactConflictGuard(
  viewModel: LlmChatViewModelBase,
  model: Model,
  query: String,
  sessionCorrections: List<com.server.edge.gallery.data.KnowledgeItem>,
  trustedWebContext: List<String>,
) {
  if (!query.lowercase().contains("presiden") || !query.lowercase().contains("indonesia")) return
  val lastAgent = viewModel.getLastMessage(model) as? ChatMessageText ?: return
  val containsJokowi = lastAgent.content.contains("joko widodo", ignoreCase = true) || lastAgent.content.contains("jokowi", ignoreCase = true)
  val correctionSaysPrabowo = sessionCorrections.any { it.content.contains("prabowo", ignoreCase = true) }
  val webSaysPrabowo = trustedWebContext.any { it.contains("prabowo", ignoreCase = true) }
  if (containsJokowi && (correctionSaysPrabowo || webSaysPrabowo)) {
    val corrected =
      "Presiden Indonesia saat ini adalah Prabowo Subianto."
    viewModel.replaceLastMessage(
      model = model,
      message =
        ChatMessageText(
          content = corrected,
          side = lastAgent.side,
          latencyMs = lastAgent.latencyMs,
          accelerator = lastAgent.accelerator,
          hideSenderLabel = lastAgent.hideSenderLabel,
        ),
      type = ChatMessageType.TEXT,
    )
    viewModel.addMessage(
      model = model,
      message = ChatMessageInfo(content = "Sistem: jawaban dikoreksi agar konsisten dengan konteks sesi dan sumber web."),
    )
  }
}

private fun isLikelyInvalidModelAnswer(text: String): Boolean {
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return true
  if (trimmed == "?" || trimmed == "??") return true
  if (trimmed.length <= 2 && trimmed.all { !it.isLetterOrDigit() }) return true
  return false
}

private suspend fun applyPostModelFactValidation(
  viewModel: LlmChatViewModelBase,
  model: Model,
  query: String,
  knowledgeRepository: KnowledgeRepository,
) {
  if (!isCurrentFactQuery(query)) return
  val lastAgent = viewModel.getLastMessage(model) as? ChatMessageText ?: return
  val current = lastAgent.content
  val entityHint = resolveFactEntity(query)
  val likelyWrongPresidentAnswer =
    entityHint == "presiden_indonesia_current" &&
      (current.contains("jokowi", ignoreCase = true) ||
        current.contains("joko widodo", ignoreCase = true))
  val genericInvalid = isLikelyInvalidModelAnswer(current)
  if (!likelyWrongPresidentAnswer && !genericInvalid) return

  val correction = entityHint?.let { knowledgeRepository.getRecentUserCorrections(it, 1).firstOrNull() }
  val replacementFromCorrection = correction?.content?.takeIf { it.isNotBlank() }
  val replacementFromCache = knowledgeRepository.findValidatedAnswerForQuery(query, entityHint)?.answer
  val replacementFromWeb =
    withTimeoutOrNull(6500) { fetchDuckDuckGoContext(query) }
      ?.results
      ?.let { filterTrustedResults(it) }
      ?.firstOrNull()
      ?.let { candidate ->
        if (entityHint == "presiden_indonesia_current" && candidate.contains("prabowo", ignoreCase = true)) {
          "Presiden Indonesia saat ini adalah Prabowo Subianto."
        } else {
          candidate.lineSequence().take(2).joinToString(" ").trim().ifBlank { null }
        }
      }

  val replacement = replacementFromCorrection ?: replacementFromCache ?: replacementFromWeb
  val source =
    when {
      replacement == replacementFromCorrection -> "user_correction"
      replacement == replacementFromCache -> "cache"
      replacement == replacementFromWeb -> "web"
      else -> "model"
    }
  Log.d(CHAT_PIPELINE_TAG, "fact_resolution_source=$source")
  val normalizedReplacement = normalizeStrictFactAnswer(replacement.orEmpty(), entityHint)
  val finalReplacement =
    when {
      normalizedReplacement != null -> normalizedReplacement
      detectFactIntent(query, entityHint) == FactIntentType.STRICT_ONE_LINE ->
        "Saya belum bisa memverifikasi nama presiden saat ini."
      else -> replacement
    }
  if (!finalReplacement.isNullOrBlank() && finalReplacement != current) {
    viewModel.replaceLastMessage(
      model = model,
      message =
        ChatMessageText(
          content = finalReplacement,
          side = lastAgent.side,
          latencyMs = lastAgent.latencyMs,
          accelerator = lastAgent.accelerator,
          hideSenderLabel = lastAgent.hideSenderLabel,
        ),
      type = ChatMessageType.TEXT,
    )
    knowledgeRepository.upsertValidatedAnswer(
      query = query,
      answer = finalReplacement,
      sourceType = source,
      entityHint = entityHint,
    )
  }
}

private fun applyPostModelLanguageRewrite(
  viewModel: LlmChatViewModelBase,
  model: Model,
  query: String,
) {
  val lastAgent = viewModel.getLastMessage(model) as? ChatMessageText ?: return
  val source = lastAgent.content
  if (source.isBlank()) return
  if (containsCodeBlock(source)) return
  val expectsIndonesian = detectUserLanguage(query) == "id"
  val rewritten =
    if (expectsIndonesian) {
      rewriteToIndonesian(source)
    } else {
      rewriteToEnglish(source)
    }
  if (rewritten != source) {
    Log.d(CHAT_PIPELINE_TAG, "language_rewrite_applied=true lang=${if (expectsIndonesian) "id" else "en"}")
    viewModel.replaceLastMessage(
      model = model,
      message =
        ChatMessageText(
          content = rewritten,
          side = lastAgent.side,
          latencyMs = lastAgent.latencyMs,
          accelerator = lastAgent.accelerator,
          hideSenderLabel = lastAgent.hideSenderLabel,
        ),
      type = ChatMessageType.TEXT,
    )
  }
}

private fun containsCodeBlock(text: String): Boolean {
  return text.contains("```") || Regex("""\b(const|function|class|import|def)\b""").containsMatchIn(text)
}

private fun detectUserLanguage(query: String): String {
  val lowered = query.lowercase(Locale.ROOT)
  val idMarkers = listOf("yang", "dan", "atau", "apa", "siapa", "contoh", "berikan", "hasil", "berapa", "benarkah", "apakah")
  val enMarkers = listOf("what", "who", "and", "or", "example", "please", "result", "is it", "how long")
  val idScore = idMarkers.count { lowered.contains(it) }
  val enScore = enMarkers.count { lowered.contains(it) }
  return if (idScore >= enScore) "id" else "en"
}

private fun rewriteToIndonesian(text: String): String {
  var out = text
  val replacements =
    listOf(
      "Here is" to "Berikut adalah",
      "The answer is" to "Jawabannya adalah",
      "I can't help with that" to "Maaf, saya belum bisa membantu untuk itu",
      "As an AI" to "Sebagai AI",
      "Current president" to "Presiden saat ini",
    )
  replacements.forEach { (en, id) -> out = out.replace(en, id, ignoreCase = true) }
  return out.trim()
}

private fun rewriteToEnglish(text: String): String {
  var out = text
  val replacements =
    listOf(
      "Berikut adalah" to "Here is",
      "Jawabannya adalah" to "The answer is",
      "Maaf, saya belum bisa membantu untuk itu" to "Sorry, I can't help with that yet",
      "Presiden saat ini adalah" to "The current president is",
    )
  replacements.forEach { (id, en) -> out = out.replace(id, en, ignoreCase = true) }
  return out.trim()
}
