package com.zeroflux.cprcoach.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import java.util.concurrent.Executors

/** Back camera preview that also hands every frame to [onImage], which must close it. */
@Composable
fun PoseCameraView(onImage: (ImageProxy) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var cameraInfo: CameraInfo? = null
        var analysis: ImageAnalysis? = null
        val cameraObserver = Observer<CameraState> { state ->
            state.error?.let { error ->
                Log.e("CPR_CAM", "Camera unavailable: code=${error.code}", error.cause)
            }
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
                        val preview = Preview.Builder().setResolutionSelector(previewSelector).build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(analysisSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                        analysis = imageAnalysis
                        imageAnalysis.setAnalyzer(executor, onImage)
                        resolved.unbindAll()
                        val camera = resolved.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis,
                        )
                        cameraInfo = camera.cameraInfo
                        camera.cameraInfo.cameraState.observe(lifecycleOwner, cameraObserver)
                    } catch (failure: Exception) {
                        Log.e("CPR_CAM", "Could not bind back camera", failure)
                        analysis?.clearAnalyzer()
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        } catch (failure: Exception) {
            Log.e("CPR_CAM", "Could not initialize camera", failure)
        }
        onDispose {
            disposed = true
            try {
                cameraInfo?.cameraState?.removeObserver(cameraObserver)
                analysis?.clearAnalyzer()
                provider?.unbindAll()
            } catch (failure: Exception) {
                Log.e("CPR_CAM", "Could not release camera", failure)
            } finally {
                executor.shutdown()
            }
        }
    }
    AndroidView({ previewView }, modifier)
}
