package com.server.edge.gallery.runtime.onnx

import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession

private const val TAG = "AGOnnxProbe"

enum class OnnxReasonCode {
  OK,
  DESKTOP_ARCH,
  INVALID_MODEL,
  ORT_SESSION_FAIL,
  NO_ADAPTER_MATCH,
  TOKENIZER_MISSING,
  FILE_UNREADABLE,
}

enum class OnnxModelKind {
  UNKNOWN,
  EMBEDDING,
  GENERATION,
}

data class OnnxCompatibilityProbeResult(
  val isLoadable: Boolean,
  val selectedAdapterId: String? = null,
  val reason: String = "",
  val reasonCode: OnnxReasonCode = OnnxReasonCode.OK,
  val userMessage: String = "",
  val techDetails: String = "",
  val inputNames: List<String> = emptyList(),
  val outputNames: List<String> = emptyList(),
  val modelKind: OnnxModelKind = OnnxModelKind.UNKNOWN,
)

class OnnxCompatibilityProbe(private val adapters: List<OnnxInferenceAdapter>) {
  fun probe(session: OrtSession): OnnxCompatibilityProbeResult {
    val inputNames = session.inputNames.toList().sorted()
    val outputNames = session.outputNames.toList().sorted()
    val modelKind = classifyKind(inputNames = inputNames, outputNames = outputNames)
    val adapter = adapters.firstOrNull { adapter ->
      runCatching { adapter.supports(session) }.getOrDefault(false)
    }

    return if (adapter != null) {
      OnnxCompatibilityProbeResult(
        isLoadable = true,
        selectedAdapterId = adapter.id,
        reason = "adapter matched",
        reasonCode = OnnxReasonCode.OK,
        userMessage = "ONNX model compatible.",
        techDetails = "Adapter ${adapter.id} matched for inputs=$inputNames outputs=$outputNames",
        inputNames = inputNames,
        outputNames = outputNames,
        modelKind = modelKind,
      )
    } else {
      val msg =
        "Model ONNX belum didukung adapter runtime saat ini. " +
          "Coba model ONNX Android ARM lain atau LiteRTLM."
      OnnxCompatibilityProbeResult(
        isLoadable = false,
        reason =
          "No compatible ONNX adapter found. Inputs=$inputNames Outputs=$outputNames. " +
            "Tambahkan adapter yang sesuai signature model.",
        reasonCode = OnnxReasonCode.NO_ADAPTER_MATCH,
        userMessage = msg,
        techDetails = "No adapter matched for inputs=$inputNames outputs=$outputNames",
        inputNames = inputNames,
        outputNames = outputNames,
        modelKind = modelKind,
      )
    }
  }

  private fun classifyKind(inputNames: List<String>, outputNames: List<String>): OnnxModelKind {
    val joinedOutputs = outputNames.joinToString(",").lowercase()
    val joinedInputs = inputNames.joinToString(",").lowercase()
    val embeddingHints =
      listOf("sentence_embedding", "embeddings", "last_hidden_state", "pooler_output")
    val generationHints = listOf("logits", "present", "past_key_values", "output_ids", "tokens")
    return when {
      embeddingHints.any { joinedOutputs.contains(it) } -> OnnxModelKind.EMBEDDING
      generationHints.any { joinedOutputs.contains(it) } -> OnnxModelKind.GENERATION
      joinedInputs.contains("input_ids") && joinedOutputs.contains("logits") ->
        OnnxModelKind.GENERATION
      else -> OnnxModelKind.UNKNOWN
    }
  }
}
