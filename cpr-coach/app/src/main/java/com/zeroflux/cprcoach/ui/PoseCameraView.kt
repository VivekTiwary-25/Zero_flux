package com.zeroflux.cprcoach.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import java.util.concurrent.Executors

/** Back camera preview that also hands every frame to [onImage], which must close it. */
@Composable
@OptIn(ExperimentalCamera2Interop::class)
fun PoseCameraView(onImage: (ImageProxy) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val currentOnImage = rememberUpdatedState(onImage)

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var cameraInfo: CameraInfo? = null
        var analysis: ImageAnalysis? = null
        var cameraObserver: Observer<CameraState>? = null
        var useCases: Array<UseCase> = emptyArray()
        var bindingGeneration = 0

        fun releaseUseCases() {
            // Invalidate any queued recovery before detaching the old session.
            bindingGeneration++
            cameraObserver?.let { cameraInfo?.cameraState?.removeObserver(it) }
            cameraObserver = null
            cameraInfo = null
            analysis?.clearAnalyzer()
            analysis = null
            val oldUseCases = useCases
            useCases = emptyArray()
            if (oldUseCases.isNotEmpty()) provider?.unbind(*oldUseCases)
        }
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.w("CPR_CAM", "Camera permission denied")
            } else {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    if (disposed) return@addListener
                    try {
                        val resolved = future.get()
                        provider = resolved
                        val previewSelector = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                            )
                            .build()
                        // The pose model scales its input down to 256 px, so larger frames only add heat.
                        val analysisSelector = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            )
                            .build()
                        fun bindCamera(requestFrameRate: Boolean) {
                            if (disposed) return
                            try {
                                releaseUseCases()
                                val generation = bindingGeneration
                                val previewBuilder = Preview.Builder().setResolutionSelector(previewSelector)
                                val analysisBuilder = ImageAnalysis.Builder()
                                    .setResolutionSelector(analysisSelector)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                if (requestFrameRate) {
                                    val targetFrameRate = Range(24, 30)
                                    previewBuilder.setTargetFrameRate(targetFrameRate)
                                    // CameraX 1.6.2 has no ImageAnalysis.Builder.setTargetFrameRate.
                                    Camera2Interop.Extender(analysisBuilder).setCaptureRequestOption(
                                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFrameRate,
                                    )
                                    Log.i("CPR_CAM", "Requesting preview and analysis frame rate $targetFrameRate")
                                }
                                val preview = previewBuilder.build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val imageAnalysis = analysisBuilder.build()
                                analysis = imageAnalysis
                                useCases = arrayOf(preview, imageAnalysis)
                                // Ownership is unchanged: onImage must close each delivered frame.
                                imageAnalysis.setAnalyzer(executor) { image -> currentOnImage.value(image) }
                                val camera = resolved.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis,
                                )
                                cameraInfo = camera.cameraInfo
                                var fallbackQueued = false
                                val observer = Observer<CameraState> { state ->
                                    if (disposed || generation != bindingGeneration) return@Observer
                                    state.error?.let { error ->
                                        Log.e("CPR_CAM", "Camera unavailable: code=${error.code}", error.cause)
                                        if (requestFrameRate && !fallbackQueued &&
                                            error.code == CameraState.ERROR_STREAM_CONFIG
                                        ) {
                                            fallbackQueued = true
                                            Log.w("CPR_CAM", "Requested stream configuration refused; using default frame rate")
                                            // observe() may deliver immediately: defer rebinding to avoid reentrancy.
                                            mainExecutor.execute {
                                                if (!disposed && generation == bindingGeneration) bindCamera(false)
                                            }
                                        }
                                    }
                                }
                                cameraObserver = observer
                                camera.cameraInfo.cameraState.observe(lifecycleOwner, observer)
                            } catch (failure: Exception) {
                                if (requestFrameRate && !disposed) {
                                    Log.w("CPR_CAM", "Frame rate request failed; rebuilding with defaults", failure)
                                    // Fresh builders omit both FPS settings; release clears the old analyzer
                                    // and unbinds both old use cases before this single fallback bind.
                                    bindCamera(false)
                                } else {
                                    Log.e("CPR_CAM", "Could not bind back camera with default frame rate", failure)
                                    releaseUseCases()
                                }
                            }
                        }
                        bindCamera(true)
                    } catch (failure: Exception) {
                        Log.e("CPR_CAM", "Could not bind back camera", failure)
                        releaseUseCases()
                    }
                }, mainExecutor)
            }
        } catch (failure: Exception) {
            Log.e("CPR_CAM", "Could not initialize camera", failure)
        }
        onDispose {
            disposed = true
            try {
                releaseUseCases()
            } catch (failure: Exception) {
                Log.e("CPR_CAM", "Could not release camera", failure)
            } finally {
                executor.shutdown()
            }
        }
    }
    AndroidView({ previewView }, modifier)
}
