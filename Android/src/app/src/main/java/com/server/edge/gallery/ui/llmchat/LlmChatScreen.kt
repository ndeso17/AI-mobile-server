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
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.server.edge.gallery.data.RuntimeType
import com.server.edge.gallery.data.Task
import com.server.edge.gallery.firebaseAnalytics
import com.server.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.server.edge.gallery.ui.common.chat.ChatMessageImage
import com.server.edge.gallery.ui.common.chat.ChatMessageText
import com.server.edge.gallery.ui.common.chat.ChatView
import com.server.edge.gallery.ui.common.chat.UploadedTextFile
import com.server.edge.gallery.ui.common.chat.SendMessageTrigger
import com.server.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.server.edge.gallery.ui.theme.emptyStateContent
import com.server.edge.gallery.ui.theme.emptyStateTitle

private const val TAG = "AGLlmChatScreen"

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
  getActiveSkills: () -> List<String> = { emptyList() },
  navigationIcon: @Composable (() -> Unit)? = null,
  onMessagesUpdated: (Model) -> Unit = {},
) {
  val context = LocalContext.current
  val knowledgeRepository = KnowledgeRepository(context)
  val chatPreferencesRepository = ChatPreferencesRepository(context)
  val task = modelManagerViewModel.getTaskById(id = taskId) ?: return

  ChatView(
    task = task,
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    navigationIcon = navigationIcon,
    onSendMessage = { model, messages ->
      if (!modelManagerViewModel.uiState.value.isModelInitialized(model)) {
        return@ChatView
      }
      for (message in messages) {
        viewModel.addMessage(model = model, message = message)
      }
      onMessagesUpdated(model)

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
        if (text.isNotEmpty()) {
          modelManagerViewModel.addTextInputHistory(text)
        }
        val adaptiveContextBudgetChars =
          when (model.runtimeType) {
            RuntimeType.AICORE -> 12000
            else -> 32000
          }
        val retrievedMemory =
          knowledgeRepository.searchRelevant(text, maxContextChars = adaptiveContextBudgetChars)
        val memoryPrompt =
          if (retrievedMemory.isEmpty()) {
            ""
          } else {
            buildString {
              appendLine("Relevant Prior Knowledge:")
              for (item in retrievedMemory) {
                appendLine("- ${item.title} [${item.language}]")
                appendLine(item.content)
              }
            }
          }
        val planningPrompt =
          if (chatMode == ChatMode.PLAN) {
            "Plan Mode: focus on goals, assumptions, steps, and acceptance criteria. Do not execute actions.\n\n"
          } else {
            ""
          }
        val finalInput = planningPrompt + memoryPrompt + "\n" + text
        viewModel.generateResponse(
          model = model,
          input = finalInput,
          images = images,
          audioMessages = audioMessages,
          onFirstToken = onFirstToken,
          onDone = {
            if (chatPreferencesRepository.getAutoIngestKnowledge()) {
              val latestAgentText =
                (viewModel.getLastMessage(model) as? ChatMessageText)
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
            onGenerateResponseDone(model)
            onMessagesUpdated(model)
          },
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
          allowThinking = showThinking && task.allowCapability(ModelCapability.LLM_THINKING, model),
          showThinking = showThinking,
        )
        // Auto-ingest user snippets and lightweight memory for future retrieval.
        if (chatPreferencesRepository.getAutoIngestKnowledge() && text.length >= 24) {
          knowledgeRepository.addFromChat(content = text, title = "User Prompt")
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
            putString("model_id", model.name)
            putBoolean("has_image", images.isNotEmpty())
            putInt("image_count", images.size)
            putBoolean("has_audio", audioMessages.isNotEmpty())
            putInt("audio_count", audioMessages.size)
            putInt("active_skills_count", activeSkills.size)
            putString("active_skills_list", activeSkills.joinToString(","))
          },
        )
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
    allowEditingSystemPrompt = allowEditingSystemPrompt,
    curSystemPrompt = curSystemPrompt,
    onSystemPromptChanged = onSystemPromptChanged,
    sendMessageTrigger = sendMessageTrigger,
    showAudioPicker = showAudioPicker,
  )
}
