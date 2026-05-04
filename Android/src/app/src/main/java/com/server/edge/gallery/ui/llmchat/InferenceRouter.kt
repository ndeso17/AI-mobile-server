package com.server.edge.gallery.ui.llmchat

import com.server.edge.gallery.data.Model
import com.server.edge.gallery.data.RuntimeType
import com.server.edge.gallery.ui.common.chat.ChatMessage
import com.server.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.server.edge.gallery.ui.common.chat.ChatMessageImage

enum class ModelCapabilityType {
  GENERATIVE_CHAT,
  EMBEDDING,
  MULTIMODAL_IMAGE,
  MULTIMODAL_AUDIO,
}

data class RouteDecision(
  val selectedModel: Model,
  val candidateModels: List<Model>,
  val reasonCode: String,
  val usedFallback: Boolean = false,
  val ensembleCandidates: List<Model> = emptyList(),
)

object InferenceRouter {
  fun route(
    requestedModel: Model,
    taskModels: List<Model>,
    messages: List<ChatMessage>,
    allowedModelNames: Set<String> = emptySet(),
  ): RouteDecision {
    val hasImage = messages.any { it is ChatMessageImage }
    val hasAudio = messages.any { it is ChatMessageAudioClip }
    val constrainedModels = taskModels.filter { allowedModelNames.contains(it.name) }
    val capabilities = constrainedModels.associateWith { inferCapabilities(it) }

    val generativeCandidates =
      constrainedModels.filter { capabilities[it]?.contains(ModelCapabilityType.GENERATIVE_CHAT) == true }

    val imageCandidates =
      generativeCandidates.filter {
        capabilities[it]?.contains(ModelCapabilityType.MULTIMODAL_IMAGE) == true
      }
    val audioCandidates =
      generativeCandidates.filter {
        capabilities[it]?.contains(ModelCapabilityType.MULTIMODAL_AUDIO) == true
      }

    val preferred =
      when {
        hasImage && imageCandidates.isNotEmpty() -> imageCandidates
        hasAudio && audioCandidates.isNotEmpty() -> audioCandidates
        else -> generativeCandidates
      }

    val candidates =
      buildList {
        if (preferred.any { it.name == requestedModel.name }) {
          add(requestedModel)
        }
        preferred.filter { it.name != requestedModel.name }.forEach { add(it) }
        if (isGenerativeCandidate(requestedModel) && none { it.name == requestedModel.name }) {
          add(requestedModel)
        }
      }

    val selected = candidates.firstOrNull() ?: requestedModel
    val ensembleCandidates = candidates.take(2)
    val reason =
      when {
        hasImage -> "IMAGE_MESSAGE_ROUTE"
        hasAudio -> "AUDIO_MESSAGE_ROUTE"
        !isGenerativeCandidate(requestedModel) -> "REQUESTED_NOT_GENERATIVE"
        selected.name != requestedModel.name -> "BETTER_GENERATIVE_CANDIDATE"
        else -> "REQUESTED_MODEL_OK"
      }

    return RouteDecision(
      selectedModel = selected,
      candidateModels = candidates,
      reasonCode = reason,
      usedFallback = selected.name != requestedModel.name,
      ensembleCandidates = ensembleCandidates,
    )
  }

  fun inferCapabilities(model: Model): Set<ModelCapabilityType> {
    val caps = mutableSetOf<ModelCapabilityType>()
    val nameLower = model.name.lowercase()
    val displayLower = model.displayName.lowercase()

    val looksEmbedding =
      nameLower.contains("embedding") ||
        displayLower.contains("embedding") ||
        nameLower.contains("bge") ||
        nameLower.contains("e5")
    val outputTensorEmbeddingHint =
      nameLower.contains("last_hidden_state") ||
        nameLower.contains("sentence_embedding") ||
        nameLower.contains("pooler_output") ||
        nameLower.contains("embeddings")

    if (looksEmbedding || outputTensorEmbeddingHint) {
      caps.add(ModelCapabilityType.EMBEDDING)
    } else if (model.isLlm) {
      caps.add(ModelCapabilityType.GENERATIVE_CHAT)
    }

    if (model.llmSupportImage) caps.add(ModelCapabilityType.MULTIMODAL_IMAGE)
    if (model.llmSupportAudio) caps.add(ModelCapabilityType.MULTIMODAL_AUDIO)

    if (model.runtimeType == RuntimeType.ONNX && looksEmbedding) {
      caps.remove(ModelCapabilityType.GENERATIVE_CHAT)
      caps.add(ModelCapabilityType.EMBEDDING)
    }

    return caps
  }

  fun isGenerativeCandidate(model: Model): Boolean {
    return inferCapabilities(model).contains(ModelCapabilityType.GENERATIVE_CHAT)
  }
}
