package com.server.edge.gallery.runtime.onnx

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.server.edge.gallery.data.Model
import com.server.edge.gallery.runtime.CleanUpListener
import com.server.edge.gallery.runtime.LlmModelHelper
import com.server.edge.gallery.runtime.ResultListener
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ToolProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope

private const val TAG = "AGOnnxModelHelper"

data class OnnxModelInstance(
  val env: OrtEnvironment,
  val session: OrtSession,
  val adapter: OnnxInferenceAdapter,
  val probeResult: OnnxCompatibilityProbeResult,
  val embeddingOnly: Boolean = false,
)

object OnnxModelHelper : LlmModelHelper {
  private val adapters: List<OnnxInferenceAdapter> = listOf(OnnxStringIOAdapter(), OnnxTokenIdsAdapter())

  fun preflight(context: Context, model: Model): String? {
    val result = runPreflight(context, model)
    Log.d(
      TAG,
      "onnx_preflight modelName=${model.name} reasonCode=${result.reasonCode} adapterId=${result.selectedAdapterId} " +
        "loadable=${result.isLoadable} userMessage=${result.userMessage}",
    )
    return if (result.isLoadable) null else result.userMessage.ifBlank { result.reason }
  }

  fun classifyModelKind(context: Context, model: Model): OnnxModelKind {
    return runPreflight(context, model).modelKind
  }

  override fun initialize(
    context: Context,
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    coroutineScope: CoroutineScope?,
  ) {
    try {
      val metadataResult = runPreflight(context, model)
      if (!metadataResult.isLoadable) {
        Log.w(
          TAG,
          "onnx_init_result modelName=${model.name} reasonCode=${metadataResult.reasonCode} msg=${metadataResult.userMessage} details=${metadataResult.techDetails}",
        )
        onDone(metadataResult.userMessage.ifBlank { metadataResult.reason })
        return
      }
      val env = OrtEnvironment.getEnvironment()
      val modelPath = model.getPath(context = context)
      val session = env.createSession(modelPath, OrtSession.SessionOptions())
      val probe = OnnxCompatibilityProbe(adapters)
      val result = probe.probe(session)
      Log.d(
        TAG,
        "onnx_probe modelName=${model.name} reasonCode=${result.reasonCode} loadable=${result.isLoadable} " +
          "adapterId=${result.selectedAdapterId} inputs=${result.inputNames} outputs=${result.outputNames} " +
          "msg=${result.userMessage} details=${result.techDetails}",
      )
      if (!result.isLoadable) {
        session.close()
        onDone(result.userMessage.ifBlank { result.reason })
        return
      }
      val adapter = adapters.firstOrNull { it.id == result.selectedAdapterId }
      if (adapter == null) {
        session.close()
        onDone("Adapter ONNX tidak ditemukan untuk signature model ini. Coba model ONNX Android-ready lain.")
        return
      }
      val embeddingOnly = result.modelKind == OnnxModelKind.EMBEDDING
      if (!embeddingOnly && adapter.requiresTokenizer && !hasTokenizerHints(modelPath)) {
        session.close()
        onDone("Tokenizer belum didukung untuk paket ONNX ini. Sertakan tokenizer.json atau gunakan paket ONNX Android-ready.")
        return
      }
      Log.d(
        TAG,
        "onnx_adapter_select modelName=${model.name} adapterId=${adapter.id} embeddingOnly=$embeddingOnly",
      )
      model.instance =
        OnnxModelInstance(
          env = env,
          session = session,
          adapter = adapter,
          probeResult = result,
          embeddingOnly = embeddingOnly,
        )
      onDone("")
    } catch (e: Exception) {
      Log.e(TAG, "onnx_init_result modelName=${model.name} reasonCode=${OnnxReasonCode.ORT_SESSION_FAIL} msg=${e.message}", e)
      onDone("Gagal inisialisasi ONNX runtime: ${e.message ?: "unknown error"}")
    }
  }

  override fun resetConversation(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
  ) {
    // Stateless fallback for ONNX adapters in this phase.
  }

  override fun cleanUp(model: Model, onDone: () -> Unit) {
    val instance = model.instance as? OnnxModelInstance
    if (instance == null) {
      onDone()
      return
    }
    runCatching { instance.session.close() }
      .onFailure { Log.e(TAG, "Failed closing ONNX session for ${model.name}", it) }
    model.instance = null
    onDone()
  }

  override fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit,
    images: List<Bitmap>,
    audioClips: List<ByteArray>,
    coroutineScope: CoroutineScope?,
    extraContext: Map<String, String>?,
  ) {
    val instance = model.instance as? OnnxModelInstance
    if (instance == null) {
      onError("ONNX model instance is not initialized.")
      return
    }
    if (instance.embeddingOnly) {
      onError(
        "Model ONNX ini bertipe embedding-only. Gunakan untuk retrieval/context, bukan jawaban chat langsung.",
      )
      return
    }
    try {
      val start = System.currentTimeMillis()
      val output = instance.adapter.infer(instance.env, instance.session, model, input)
      Log.d(
        TAG,
        "onnx_infer_result modelName=${model.name} adapterId=${instance.adapter.id} runtimeMs=${System.currentTimeMillis() - start}",
      )
      resultListener(output, true, null)
    } catch (e: Exception) {
      Log.e(TAG, "onnx_infer_result modelName=${model.name} reasonCode=${OnnxReasonCode.INVALID_MODEL} msg=${e.message}", e)
      onError("ONNX inference gagal: ${e.message ?: "unknown error"}")
    }
  }

  override fun stopResponse(model: Model) {
    // No streaming cancellation support in current ONNX adapter phase.
  }

  private fun runPreflight(context: Context, model: Model): OnnxCompatibilityProbeResult {
    val modelPath = model.getPath(context = context)
    val file = File(modelPath)
    if (!file.exists() || !file.canRead() || !model.name.lowercase().endsWith(".onnx")) {
      return OnnxCompatibilityProbeResult(
        isLoadable = false,
        reasonCode = OnnxReasonCode.FILE_UNREADABLE,
        reason = "File ONNX tidak bisa dibaca.",
        userMessage = "File ONNX tidak valid/terbaca. Pastikan file .onnx tersedia.",
        techDetails = "path=$modelPath exists=${file.exists()} canRead=${file.canRead()}",
      )
    }
    val lowerName = model.name.lowercase()
    if (lowerName.contains("avx2") || lowerName.contains("avx") || lowerName.contains("x86") || lowerName.contains("x64")) {
      return OnnxCompatibilityProbeResult(
        isLoadable = false,
        reasonCode = OnnxReasonCode.DESKTOP_ARCH,
        reason = "Desktop architecture marker detected.",
        userMessage = "Model ONNX ini build desktop (AVX2/x86), tidak kompatibel Android ARM.",
        techDetails = "modelName=${model.name}",
      )
    }

    return runCatching {
      val env = OrtEnvironment.getEnvironment()
      env.createSession(modelPath, OrtSession.SessionOptions()).use { session ->
        val probeResult = OnnxCompatibilityProbe(adapters).probe(session)
        val requiresTokenizer =
          probeResult.selectedAdapterId
            ?.let { adapterId -> adapters.firstOrNull { it.id == adapterId }?.requiresTokenizer }
            ?: false
        val embeddingOnly = probeResult.modelKind == OnnxModelKind.EMBEDDING
        if (!embeddingOnly && requiresTokenizer && !hasTokenizerHints(modelPath)) {
          OnnxCompatibilityProbeResult(
            isLoadable = false,
            reasonCode = OnnxReasonCode.TOKENIZER_MISSING,
            reason = "Tokenizer hints are missing.",
            userMessage =
              "Tokenizer belum didukung untuk paket ONNX ini. Sertakan tokenizer.json atau gunakan paket ONNX Android-ready.",
            techDetails = "adapter=${probeResult.selectedAdapterId} modelKind=${probeResult.modelKind}",
            inputNames = probeResult.inputNames,
            outputNames = probeResult.outputNames,
            modelKind = probeResult.modelKind,
          )
        } else {
          probeResult
        }
      }
    }.getOrElse { e ->
      OnnxCompatibilityProbeResult(
        isLoadable = false,
        reasonCode = OnnxReasonCode.ORT_SESSION_FAIL,
        reason = e.message ?: "ORT session create failed",
        userMessage = "Model ONNX tidak bisa di-load oleh runtime Android.",
        techDetails = "exception=${e::class.java.simpleName} msg=${e.message}",
      )
    }
  }

  private fun hasTokenizerHints(modelPath: String): Boolean {
    val modelFile = File(modelPath)
    val dir = modelFile.parentFile ?: return false
    val candidates =
      listOf(
        File(dir, "tokenizer.json"),
        File(dir, "${modelFile.nameWithoutExtension}.tokenizer.json"),
        File(dir, "vocab.json"),
      )
    return candidates.any { it.exists() && it.canRead() }
  }
}
