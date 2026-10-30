package com.zeroflux.cprcoach.lab

import android.Manifest
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors

/** M0 test screen. It gets replaced by the real Home screen in M4. */
class LabActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        permissions.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var page by remember { mutableStateOf("menu") }
                Box(Modifier.fillMaxSize().background(Color(0xFF0B1220))) {
                    when (page) {
                        "audio" -> AudioTest { page = "menu" }
                        "pose" -> PoseTest { page = "menu" }
                        else -> Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = { page = "audio" }) { Text("Call audio test") }
                            Button(onClick = { page = "pose" }) { Text("Pose test") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioTest(onBack: () -> Unit) {
    val context = LocalContext.current
    val micDb by LabAudioService.micDb.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Metronome + mic during a call. Use a teammate's number. Never 112.", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { LabAudioService.start(context, AudioAttributes.USAGE_MEDIA) }) {
                Text("Start (media)")
            }
            Button(onClick = { LabAudioService.start(context, AudioAttributes.USAGE_VOICE_COMMUNICATION) }) {
                Text("Start (voice)")
            }
            Button(onClick = { LabAudioService.stop(context) }) { Text("Stop") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Opens the dialer empty on purpose, so nothing can ever ring 112 from this screen.
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))) }) {
                Text("Open dialer")
            }
            Button(onClick = { LabAudioService.stop(context); onBack() }) { Text("Back") }
        }
        Text(
            "Mic level: " + (micDb?.let { "%.1f dB".format(it) } ?: "off"),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

private const val TRACE_POINTS = 240

@Composable
private fun PoseTest(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var frame by remember { mutableStateOf<LabFrame?>(null) }
    var fps by remember { mutableStateOf(0.0) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var lastSecond by remember { mutableLongStateOf(0L) }
    var framesThisSecond by remember { mutableLongStateOf(0L) }
    val signal = remember { mutableStateListOf<Float>() }
    val tracker = remember {
        LabPoseTracker(context, File(context.getExternalFilesDir(null), "traces")) { result ->
            ContextCompat.getMainExecutor(context).execute {
                frame = result
                val second = System.currentTimeMillis() / 1000
                if (second != lastSecond) {
                    fps = framesThisSecond.toDouble()
                    framesThisSecond = 0
                    lastSecond = second
                }
                framesThisSecond++
                result?.let {
                    signal.add(it.wristSignal.toFloat())
                    if (signal.size > TRACE_POINTS) signal.removeAt(0)
                }
            }
        }
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val preview = Preview.Builder().setResolutionSelector(selector).build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(analysisExecutor) { tracker.submit(it) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (providerFuture.isDone) providerFuture.get().unbindAll()
            analysisExecutor.shutdown()
            tracker.close()
        }
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) { frame?.let { drawSkeleton(it) } }
        }
        Column(
            Modifier.weight(0.6f).fillMaxHeight().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val current = frame
            Text(
                if (current == null) {
                    "No person found  |  $fps fps"
                } else {
                    "Elbow %.0f°  |  vis %.2f  |  %.0f fps\nLatency %d ms  |  %s  |  %s arm".format(
                        current.elbowDegrees, current.minVisibility, fps, current.inferenceMs,
                        current.delegate, if (current.rightSide) "right" else "left",
                    )
                },
                color = Color.White,
            )
            Canvas(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF16223A))) {
                drawSignal(signal)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (recordingFile == null) {
                        recordingFile = tracker.startRecording()
                    } else {
                        tracker.stopRecording()
                        recordingFile = null
                    }
                }) { Text(if (recordingFile == null) "Record" else "Stop rec") }
                Button(onClick = onBack) { Text("Back") }
            }
            recordingFile?.let { Text("Saving ${it.name}", color = Color(0xFF5EEAD4)) }
        }
    }
}

private fun DrawScope.drawSkeleton(frame: LabFrame) {
    // The preview is letterboxed to 4:3 inside this box, so the overlay uses the same rectangle.
    val imageAspect = 4f / 3f
    val boxAspect = size.width / size.height
    val drawWidth = if (boxAspect > imageAspect) size.height * imageAspect else size.width
    val drawHeight = if (boxAspect > imageAspect) size.height else size.width / imageAspect
    val left = (size.width - drawWidth) / 2
    val top = (size.height - drawHeight) / 2
    fun place(p: LabFrame.Point) = Offset(left + p.x * drawWidth, top + p.y * drawHeight)
    val joints = listOf(frame.hip, frame.shoulder, frame.elbow, frame.wrist)
    val color = if (frame.minVisibility > 0.5f) Color(0xFF5EEAD4) else Color(0xFFFBBF24)
    joints.zipWithNext { a, b -> drawLine(color, place(a), place(b), strokeWidth = 6f) }
    joints.forEach { drawCircle(color, radius = 10f, center = place(it)) }
}

private fun DrawScope.drawSignal(signal: List<Float>) {
    if (signal.size < 2) return
    val low = signal.min()
    val range = (signal.max() - low).coerceAtLeast(0.01f)
    val step = size.width / (TRACE_POINTS - 1)
    for (i in 1 until signal.size) {
        val y0 = size.height * (1 - (signal[i - 1] - low) / range)
        val y1 = size.height * (1 - (signal[i] - low) / range)
        drawLine(Color(0xFF5EEAD4), Offset((i - 1) * step, y0), Offset(i * step, y1), strokeWidth = 3f)
    }
}
