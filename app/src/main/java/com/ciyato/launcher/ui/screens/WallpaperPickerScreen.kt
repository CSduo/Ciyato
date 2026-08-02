package com.ciyato.launcher.ui.screens

import android.app.WallpaperManager
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WallpaperPickerScreen - Suggestion #93
 * Browse gradient wallpapers or open system wallpaper picker.
 */

data class GradientWallpaper(
    val id: String,
    val name: String,
    val colors: List<Color>,
)

private val GRADIENT_PRESETS = listOf(
    GradientWallpaper("midnight", "Midnight", listOf(Color(0xFF050607), Color(0xFF14181D), Color(0xFF050607))),
    GradientWallpaper("graphite", "Graphite", listOf(Color(0xFF08090A), Color(0xFF282C31), Color(0xFF0D0F11))),
    GradientWallpaper("silver", "Silver Trace", listOf(Color(0xFF050607), Color(0xFF30353A), Color(0xFF9BA1A7))),
    GradientWallpaper("smoke", "Smoke", listOf(Color(0xFF101214), Color(0xFF33373A), Color(0xFF101214))),
    GradientWallpaper("slate", "Slate", listOf(Color(0xFF0B0D10), Color(0xFF20262C), Color(0xFF101216))),
    GradientWallpaper("mono", "Monochrome", listOf(Color(0xFF000000), Color(0xFF202020), Color(0xFF3B3B3B))),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ciyatoImageWallpaper by viewModel.ciyatoImageWallpaper.collectAsState()
    val wallpaperDim by viewModel.wallpaperDim.collectAsState()
    val wallpaperImageScale by viewModel.wallpaperImageScale.collectAsState()
    val wallpaperImageOffset by viewModel.wallpaperImageOffset.collectAsState()
    val wallpaperBlur by viewModel.wallpaperBlur.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var imageStatus by remember { mutableStateOf<String?>(null) }
    val personalImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                imageStatus = "Preparing image..."
                val localUri = importCiyatoImage(context, uri)
                imageStatus = if (localUri != null) {
                    viewModel.setCiyatoImageWallpaper(localUri.toString())
                    viewModel.setCiyatoVideoWallpaper("")
                    viewModel.setUseSystemWallpaper(false)
                    "Personal image is now the Ciyato background. Adjust its framing below."
                } else {
                    "Ciyato could not prepare that image."
                }
            }
        }
    }
    val personalVideoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                imageStatus = "Checking video..."
                val result = withContext(Dispatchers.IO) { validateCiyatoVideo(context, uri) }
                if (!result.isValid) {
                    imageStatus = result.message
                    return@launch
                }
                imageStatus = "Preparing Ciyato video background..."
                val localUri = importCiyatoVideo(context, uri)
                imageStatus = if (localUri != null) {
                    viewModel.setCiyatoVideoWallpaper(localUri.toString())
                    viewModel.setCiyatoImageWallpaper("")
                    viewModel.setUseSystemWallpaper(false)
                    "Ciyato-only video background applied. It pauses when Ciyato is hidden, the screen is off, or Battery Saver is on."
                } else {
                    "Ciyato could not prepare that video. Choose another clip."
                }
            }
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Wallpaper Studio", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = {
                    viewModel.setCiyatoImageWallpaper("")
                    viewModel.setCiyatoVideoWallpaper("")
                    viewModel.setUseSystemWallpaper(true)
                    imageStatus = "Ciyato now follows the current Android system wallpaper."
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Follow current system wallpaper", color = CiyatoGold)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable {
                    personalImagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Wallpaper, null, tint = CiyatoGold, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Choose personal image", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                        Text("Uses Android Photo Picker - no broad photo access", color = CiyatoMuted, fontSize = 12.sp)
                    }
                    Text("→", color = CiyatoSec, fontSize = 18.sp)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable {
                    personalVideoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Wallpaper, null, tint = CiyatoGold, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Choose short video", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                        Text("Ciyato-only background, silent, up to 15 seconds", color = CiyatoMuted, fontSize = 12.sp)
                    }
                    Text("->", color = CiyatoSec, fontSize = 18.sp)
                }
            }

            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open system wallpaper picker", color = CiyatoGold)
            }

            imageStatus?.let { status ->
                Text(
                    status,
                    color = CiyatoSec,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (ciyatoImageWallpaper.isNotBlank()) {
                ImageBackgroundControls(
                    uri = ciyatoImageWallpaper,
                    scale = wallpaperImageScale,
                    offset = wallpaperImageOffset,
                    dim = wallpaperDim,
                    blur = wallpaperBlur,
                    onScaleChanged = viewModel::setWallpaperImageScale,
                    onOffsetChanged = viewModel::setWallpaperImageOffset,
                    onDimChanged = viewModel::setWallpaperDim,
                    onBlurChanged = viewModel::setWallpaperBlur,
                    onRemove = {
                        viewModel.setCiyatoImageWallpaper("")
                        viewModel.setUseSystemWallpaper(true)
                        imageStatus = "Ciyato now follows the current Android system wallpaper."
                    },
                )
            }

            Text("Featured 4K Ciyato Wallpapers", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            val presetWallpapers = listOf(
                "Neon Fluid" to com.ciyato.launcher.R.drawable.wallpaper_neon_fluid,
                "Cosmic Nebula" to com.ciyato.launcher.R.drawable.wallpaper_cosmic_nebula,
                "Minimal Abstract" to com.ciyato.launcher.R.drawable.wallpaper_minimal_abstract,
                "Nature Aurora" to com.ciyato.launcher.R.drawable.wallpaper_nature_aurora,
                "Obsidian Gold" to com.ciyato.launcher.R.drawable.wallpaper_obsidian_gold,
                "Cyber Neon" to com.ciyato.launcher.R.drawable.wallpaper_cyber_neon,
                "Glass Prism" to com.ciyato.launcher.R.drawable.wallpaper_glass_geometry,
                "Deep Ocean" to com.ciyato.launcher.R.drawable.wallpaper_deep_ocean,
                "Cosmic Dust" to com.ciyato.launcher.R.drawable.wallpaper_cosmic_dust,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                presetWallpapers.chunked(3).forEach { rowWallpapers ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowWallpapers.forEach { (title, drawableRes) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        val resUri = "android.resource://${context.packageName}/$drawableRes"
                                        viewModel.setCiyatoImageWallpaper(resUri)
                                        viewModel.setCiyatoVideoWallpaper("")
                                        viewModel.setUseSystemWallpaper(false)
                                        imageStatus = "$title set as Ciyato wallpaper."
                                    },
                            ) {
                                AsyncImage(
                                    model = drawableRes,
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                ) {
                                    Text(title, color = CiyatoWhite, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        repeat(3 - rowWallpapers.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Minimal system wallpapers", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GRADIENT_PRESETS.chunked(3).forEach { rowWps ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowWps.forEach { wp ->
                            Box(modifier = Modifier.weight(1f)) {
                                GradientWallpaperCard(
                                    wp = wp,
                                    isSelected = selected == wp.id,
                                    onClick = {
                                        selected = wp.id
                                        applyGradientWallpaper(context, wp)
                                        viewModel.setCiyatoImageWallpaper("")
                                        viewModel.setCiyatoVideoWallpaper("")
                                        viewModel.setUseSystemWallpaper(true)
                                        imageStatus = "Applied as the Android system wallpaper. Ciyato follows it."
                                    }
                                )
                            }
                        }
                        repeat(3 - rowWps.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ImageBackgroundControls(
    uri: String,
    scale: Float,
    offset: Float,
    dim: Int,
    blur: Int,
    onScaleChanged: (Float) -> Unit,
    onOffsetChanged: (Float) -> Unit,
    onDimChanged: (Int) -> Unit,
    onBlurChanged: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CiyatoBg),
            ) {
                AsyncImage(
                    model = Uri.parse(uri),
                    contentDescription = "Selected Ciyato background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationY = size.height * offset * 0.18f
                        }
                        .blur(blur.dp),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim / 100f)))
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(132.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CiyatoBg.copy(alpha = 0.78f)),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CiyatoBg.copy(alpha = 0.78f)),
                            )
                        }
                    }
                }
            }
            Text("Ciyato image background", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
            WallpaperStudioSlider("Crop scale", scale, 1f..1.6f, "${(scale * 100).toInt()}%", onScaleChanged)
            WallpaperStudioSlider("Vertical position", offset, -1f..1f, "${(offset * 100).toInt()}%", onOffsetChanged)
            WallpaperStudioSlider("Dim", dim.toFloat(), 0f..80f, "$dim%") { onDimChanged(it.toInt()) }
            WallpaperStudioSlider("Blur", blur.toFloat(), 0f..20f, "$blur") { onBlurChanged(it.toInt()) }
            TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) {
                Text("Remove Ciyato image", color = CiyatoSec)
            }
        }
    }
}

@Composable
private fun WallpaperStudioSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChanged: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CiyatoSec, fontSize = 13.sp, modifier = Modifier.width(116.dp))
        Slider(
            value = value,
            onValueChange = onValueChanged,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = CiyatoGold,
                activeTrackColor = CiyatoGold,
                inactiveTrackColor = CiyatoBgEl2,
            ),
        )
        Text(valueText, color = CiyatoMuted, fontSize = 11.sp, modifier = Modifier.width(38.dp))
    }
}

@Composable
private fun GradientWallpaperCard(
    wp: GradientWallpaper,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.56f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(wp.colors))
            .then(
                if (isSelected)
                    Modifier.border(2.dp, CiyatoGold, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, null, tint = CiyatoGold, modifier = Modifier.size(28.dp))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(6.dp),
        ) {
            Text(wp.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun applyGradientWallpaper(context: android.content.Context, wp: GradientWallpaper) {
    try {
        val wm = WallpaperManager.getInstance(context)
        val dm = context.resources.displayMetrics
        val bitmap = android.graphics.Bitmap.createBitmap(dm.widthPixels, dm.heightPixels,
            android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        val colorInts = wp.colors.map { c ->
            android.graphics.Color.argb(
                (c.alpha * 255).toInt(),
                (c.red * 255).toInt(),
                (c.green * 255).toInt(),
                (c.blue * 255).toInt(),
            )
        }.toIntArray()
        val positions = FloatArray(colorInts.size) { i -> i.toFloat() / (colorInts.size - 1) }
        val shader = android.graphics.LinearGradient(
            0f, 0f, 0f, dm.heightPixels.toFloat(),
            colorInts, positions, android.graphics.Shader.TileMode.CLAMP,
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, dm.widthPixels.toFloat(), dm.heightPixels.toFloat(), paint)
        wm.setBitmap(bitmap)
    } catch (_: Exception) {}
}

private data class VideoValidationResult(val isValid: Boolean, val message: String)

private fun validateCiyatoVideo(context: android.content.Context, uri: Uri): VideoValidationResult = runCatching {
    val retriever = MediaMetadataRetriever()
    val metadata = try {
        retriever.setDataSource(context, uri)
        VideoMetadata(
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty(),
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
        )
    } finally {
        retriever.release()
    }
    val sizeBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
        descriptor.length.takeIf { it >= 0L }
    }
    when {
        metadata.durationMs <= 0L -> VideoValidationResult(false, "Ciyato could not read that video.")
        metadata.durationMs > 60_000L -> VideoValidationResult(false, "Choose a clip that is 60 seconds or shorter.")
        metadata.width <= 0 || metadata.height <= 0 -> VideoValidationResult(false, "That video has an unsupported resolution.")
        metadata.width.toLong() * metadata.height > MAX_VIDEO_PIXELS -> VideoValidationResult(false, "Choose a video no larger than 4K.")
        metadata.rotation !in setOf(0, 90, 180, 270) -> VideoValidationResult(false, "That video has an unsupported orientation.")
        metadata.mimeType !in SUPPORTED_VIDEO_TYPES -> VideoValidationResult(false, "Choose an MP4, WebM, or 3GP video.")
        sizeBytes != null && sizeBytes > MAX_VIDEO_BYTES -> VideoValidationResult(false, "Choose a video smaller than 60 MB.")
        else -> VideoValidationResult(true, "Ciyato-only video background applied. It pauses in Battery Saver.")
    }
}.getOrElse { VideoValidationResult(false, "Ciyato could not open that video.") }

private suspend fun importCiyatoVideo(context: android.content.Context, source: Uri): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val extension = when (context.contentResolver.getType(source)) {
                "video/webm" -> "webm"
                "video/3gpp" -> "3gp"
                else -> "mp4"
            }
            val target = File(directory, "ciyato_video_wallpaper.$extension")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Selected video is no longer readable")
            Uri.fromFile(target)
        }.getOrNull()
    }

private suspend fun importCiyatoImage(context: android.content.Context, source: Uri): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val target = File(directory, "ciyato_image_wallpaper.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Selected image is no longer readable")
            Uri.fromFile(target)
        }.getOrNull()
    }

private data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val rotation: Int,
)

private val SUPPORTED_VIDEO_TYPES = setOf("video/mp4", "video/webm", "video/3gpp")
private const val MAX_VIDEO_PIXELS = 3_840L * 2_160L
private const val MAX_VIDEO_BYTES = 60L * 1024L * 1024L
