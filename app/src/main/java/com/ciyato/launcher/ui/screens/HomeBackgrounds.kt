package com.ciyato.launcher.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.launcher.*
import com.ciyato.launcher.ui.theme.*
import coil.compose.AsyncImage
import java.util.*

/**
 * Wallpaper layers behind Home — a looping Ciyato-only video, or a still image.
 */

@Composable
internal fun CiyatoVideoBackground(uri: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }
    var deviceInteractive by remember(powerManager) { mutableStateOf(powerManager?.isInteractive ?: true) }
    val canPlay = powerManager?.isPowerSaveMode != true && deviceInteractive
    val latestCanPlay by rememberUpdatedState(canPlay)

    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: android.content.Context?, intent: android.content.Intent?) {
                deviceInteractive = powerManager?.isInteractive ?: true
            }
        }
        val filter = IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // TextureView (not VideoView/SurfaceView): SurfaceView punches a hole in
    // the Compose layer stack and frequently shows black instead of the clip.
    var mediaPlayer by remember(uri) { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(canPlay, mediaPlayer) {
        val player = mediaPlayer ?: return@LaunchedEffect
        runCatching { if (canPlay) player.start() else player.pause() }
    }

    AndroidView(
        factory = { viewContext ->
            android.view.TextureView(viewContext).also { texture ->
                texture.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    // A MediaPlayer that hits MEDIA_ERROR_* moves into a permanently
                    // broken Error state — start()/pause() become silent no-ops from
                    // then on. Without a rebuild-and-retry here, one transient decode
                    // hiccup (common under memory pressure) leaves the background
                    // blank forever, which is exactly what "the video disappears
                    // after a while" describes. Bounded so a genuinely corrupt file
                    // gives up instead of retry-looping forever.
                    var retriesLeft = 2

                    fun startPlayback(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        val player = android.media.MediaPlayer()
                        runCatching {
                            player.setSurface(android.view.Surface(surfaceTexture))
                            player.setDataSource(viewContext, Uri.parse(uri))
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            player.setOnPreparedListener { prepared ->
                                // Center-crop the video into the screen.
                                val videoWidth = prepared.videoWidth.toFloat()
                                val videoHeight = prepared.videoHeight.toFloat()
                                if (videoWidth > 0f && videoHeight > 0f) {
                                    val scale = maxOf(width / videoWidth, height / videoHeight)
                                    val matrix = android.graphics.Matrix()
                                    matrix.setScale(
                                        videoWidth * scale / width,
                                        videoHeight * scale / height,
                                        width / 2f,
                                        height / 2f,
                                    )
                                    texture.setTransform(matrix)
                                }
                                if (latestCanPlay) prepared.start()
                            }
                            player.setOnErrorListener { broken, _, _ ->
                                runCatching { broken.release() }
                                if (mediaPlayer === broken) mediaPlayer = null
                                if (retriesLeft > 0 && texture.isAvailable) {
                                    retriesLeft--
                                    startPlayback(surfaceTexture, width, height)
                                }
                                true
                            }
                            player.prepareAsync()
                            mediaPlayer = player
                        }.onFailure { player.release() }
                    }

                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        retriesLeft = 2
                        startPlayback(surfaceTexture, width, height)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(surfaceTexture: android.graphics.SurfaceTexture): Boolean {
                        mediaPlayer?.let { runCatching { it.stop(); it.release() } }
                        mediaPlayer = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: android.graphics.SurfaceTexture) = Unit
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    DisposableEffect(lifecycleOwner, uri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (latestCanPlay) runCatching { mediaPlayer?.start() }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> runCatching { mediaPlayer?.pause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mediaPlayer?.let { runCatching { it.stop(); it.release() } }
            mediaPlayer = null
        }
    }
}

@Composable
internal fun CiyatoImageBackground(
    uri: String,
    scale: Float,
    verticalOffset: Float,
    blurRadius: Int,
) {
    AsyncImage(
        model = Uri.parse(uri),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = size.height * verticalOffset * 0.18f
            }
            .blur(blurRadius.dp),
    )
}
