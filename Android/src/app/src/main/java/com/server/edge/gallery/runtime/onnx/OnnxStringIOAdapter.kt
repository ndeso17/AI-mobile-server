package com.server.edge.gallery.runtime.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.server.edge.gallery.data.Model

class OnnxStringIOAdapter : OnnxInferenceAdapter {
  override val id: String = "onnx_string_io"

  override fun supports(session: OrtSession): Boolean {
    if (session.inputNames.size != 1 || session.outputNames.isEmpty()) {
      return false
    }
    val inputName = session.inputNames.first()
    val inputInfo = session.inputInfo[inputName]?.info?.toString() ?: return false
    return inputInfo.contains("STRING", ignoreCase = true)
  }

  override fun infer(
    env: OrtEnvironment,
    session: OrtSession,
    model: Model,
    prompt: String,
  ): String {
    val inputName = session.inputNames.first()
    val outputName = session.outputNames.first()
    val inputTensor = OnnxTensor.createTensor(env, arrayOf(prompt), longArrayOf(1))
    val inputs = mapOf(inputName to inputTensor)

    session.run(inputs, setOf(outputName)).use { result ->
      val value = result[0]?.value
      return when (value) {
        is Array<*> -> value.firstOrNull()?.toString() ?: ""
        else -> value?.toString() ?: ""
      }
    }
  }
}
