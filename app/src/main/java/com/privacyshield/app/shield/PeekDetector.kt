package com.privacyshield.app.shield

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

/**
 * Counts faces in front of the screen with the front camera, about 3 times a second, using
 * ML Kit's on-device model. Frames are analysed in memory and dropped; nothing is saved or sent.
 */
class PeekDetector(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val onFaces: (Int) -> Unit,
    private val onProblem: (String?) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            // Faces smaller than this share of the frame are too far away to read the screen.
            .setMinFaceSize(0.08f)
            .build(),
    )
    private var provider: ProcessCameraProvider? = null
    private var wanted = false
    private var lastFrame = 0L
    @Volatile private var busy = false

    fun start() {
        if (wanted) return
        wanted = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!wanted) return@addListener
            try {
                val p = future.get().also { provider = it }
                if (!p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    onProblem("This phone has no front camera"); return@addListener
                }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                        ).build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, ::analyze)
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                onProblem(null)
            } catch (e: Exception) {
                onProblem("Camera unavailable: ${e.message ?: e.javaClass.simpleName}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        wanted = false
        runCatching { provider?.unbindAll() }
    }

    fun close() {
        stop()
        detector.close()
        executor.shutdown()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: androidx.camera.core.ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = proxy.image
        if (busy || media == null || now - lastFrame < FRAME_GAP_MS) { proxy.close(); return }
        lastFrame = now
        busy = true
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces -> if (wanted) onFaces(faces.size) }
            .addOnCompleteListener { busy = false; proxy.close() }
    }

    private companion object {
        const val FRAME_GAP_MS = 300L
    }
}
