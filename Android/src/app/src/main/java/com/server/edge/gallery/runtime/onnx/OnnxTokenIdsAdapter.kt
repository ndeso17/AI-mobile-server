package com.server.edge.gallery.runtime.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.server.edge.gallery.data.Model
import java.nio.LongBuffer
import kotlin.math.abs

class OnnxTokenIdsAdapter : OnnxInferenceAdapter {
  override val id: String = "onnx_token_ids"
  override val requiresTokenizer: Boolean = true

  override fun supports(session: OrtSession): Boolean {
    val inputNames = session.inputNames.map { it.lowercase() }.toSet()
    val hasInputIds = inputNames.contains("input_ids")
    val hasOutput = session.outputNames.isNotEmpty()
    return hasInputIds && hasOutput
  }

  override fun infer(
    env: OrtEnvironment,
    session: OrtSession,
    model: Model,
    prompt: String,
  ): String {
    val tokenIds = promptToPseudoTokenIds(prompt)
    val seqLen = tokenIds.size.toLong()
    val inputIdsTensor =
      OnnxTensor.createTensor(
        env,
        LongBuffer.wrap(tokenIds),
        longArrayOf(1L, seqLen),
      )
    val inputs = mutableMapOf<String, OnnxTensor>()
    inputs["input_ids"] = inputIdsTensor
    if (session.inputNames.any { it.equals("attention_mask", ignoreCase = true) }) {
      val attentionMask = LongArray(tokenIds.size) { 1L }
      inputs["attention_mask"] =
        OnnxTensor.createTensor(
          env,
          LongBuffer.wrap(attentionMask),
          longArrayOf(1L, seqLen),
        )
    }

    session.run(inputs, session.outputNames).use { result ->
      val firstOutput = result.firstOrNull()?.value ?: return "ONNX inference done."
      return when (firstOutput) {
        is Array<*> -> firstOutput.firstOrNull()?.toString() ?: "ONNX inference done."
        else -> firstOutput.toString()
      }
    }
  }

  private fun promptToPseudoTokenIds(prompt: String): LongArray {
    val normalized = prompt.ifBlank { " " }.take(512)
    return normalized.map { c ->
      (abs(c.code) % 32000).toLong().coerceAtLeast(1L)
    }.toLongArray()
  }
}

