package com.server.edge.gallery.runtime.onnx

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.server.edge.gallery.data.Model

interface OnnxInferenceAdapter {
  val id: String
  val requiresTokenizer: Boolean
    get() = false

  fun supports(session: OrtSession): Boolean

  fun infer(
    env: OrtEnvironment,
    session: OrtSession,
    model: Model,
    prompt: String,
  ): String
}
