package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FullVideoProject
import com.example.data.model.SceneEntity
import com.example.data.model.SubtitleSegmentEntity
import com.example.data.model.VideoProjectEntity
import com.example.ui.viewmodel.VideoViewModel
import kotlin.math.sin
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.data.api.PollinationsImageService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()
    val currentProject by viewModel.currentProject.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val generationStatus by viewModel.generationStatus.collectAsStateWithLifecycle()
    val isApiKeyMissing by viewModel.isApiKeyMissing.collectAsStateWithLifecycle()
    val generationError by viewModel.generationError.collectAsStateWithLifecycle()

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progressMs by viewModel.playbackProgressMs.collectAsStateWithLifecycle()
    val activeSceneIdx by viewModel.activeSceneIndex.collectAsStateWithLifecycle()
    val activeSubtitle by viewModel.activeSubtitleText.collectAsStateWithLifecycle()

    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val exportStep by viewModel.exportStep.collectAsStateWithLifecycle()

    // Editor tab states: 0 = Scenes, 1 = Audio Mixer, 2 = Font Customizer
    var selectedTabIndex by remember { mutableStateOf(0) }
    var promptInput by remember { mutableStateOf("") }
    var showSubtitleEditDialog by remember { mutableStateOf<SubtitleSegmentEntity?>(null) }
    var showProjectCreator by remember { mutableStateOf(false) }
    var isVideoFullscreen by remember { mutableStateOf(false) }

    // Luxury Dark Synth Palette
    val darkBackground = Color(0xFF0F0E17)
    val cardBackground = Color(0xFF1D1B26)
    val neonPlum = Color(0xFFA78BFA)
    val neonCyan = Color(0xFF22D3EE)
    val hotPink = Color(0xFFF43F5E)
    val goldAccent = Color(0xFFFBBF24)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = darkBackground,
        contentWindowInsets = if (isVideoFullscreen) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
        topBar = {
            if (currentProject == null || !isVideoFullscreen) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Studio logo",
                                tint = neonCyan
                            )
                            Text(
                                text = "VidAI Studio",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    },
                    actions = {
                        if (currentProject != null) {
                            IconButton(
                                onClick = { viewModel.closeProject() },
                                modifier = Modifier.testTag("back_to_projects_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Project",
                                    tint = Color.LightGray
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = darkBackground,
                        titleContentColor = Color.White
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .then(if (isVideoFullscreen) Modifier.fillMaxSize() else Modifier.padding(innerPadding))
                .fillMaxSize()
        ) {
            when {
                // EXPORT RENDER DIALOG (FULL SCREEN SIMULATION)
                isExporting -> {
                    ExportRenderOverlay(
                        step = exportStep,
                        progress = exportProgress,
                        darkBackground = darkBackground,
                        neonPlum = neonPlum,
                        neonCyan = neonCyan
                    )
                }

                // PRIMARY CINEMATIC EDITOR LAYOUT
                currentProject != null -> {
                    if (isVideoFullscreen) {
                        FullscreenVideoPlayer(
                            fullProj = currentProject!!,
                            activeSceneIndex = activeSceneIdx,
                            progressMs = progressMs,
                            activeSubtitle = activeSubtitle,
                            isPlaying = isPlaying,
                            onPlayPause = { if (isPlaying) viewModel.pause() else viewModel.play() },
                            onSeek = { viewModel.seekTo(it) },
                            onExitFullscreen = { isVideoFullscreen = false },
                            neonCyan = neonCyan,
                            hotPink = hotPink,
                            cardBackground = cardBackground
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            val fullProj = currentProject!!

                            // SECTION 1: STUDIO PREVIEW MONITOR (PROMPT RENDERING GRAPHIC SIMULATION)
                            VideoMonitorScreen(
                                project = fullProj.project,
                                scenes = fullProj.scenes,
                                activeSceneIndex = activeSceneIdx,
                                progressMs = progressMs,
                                activeSubtitle = activeSubtitle,
                                isPlaying = isPlaying,
                                onPlayPause = { if (isPlaying) viewModel.pause() else viewModel.play() },
                                onSeek = { viewModel.seekTo(it) },
                                onToggleFullscreen = { isVideoFullscreen = true },
                                neonCyan = neonCyan,
                                hotPink = hotPink,
                                cardBackground = cardBackground
                            )

                            // SECTION 2: PROJECT INFO HEADER
                            ProjectHeaderWidget(
                                project = fullProj.project,
                                onDelete = {
                                    viewModel.deleteProject(fullProj.project.id)
                                    Toast.makeText(context, "Proyecto eliminado", Toast.LENGTH_SHORT).show()
                                },
                                onExport = {
                                    viewModel.renderAndExportVideo {
                                        Toast.makeText(context, "¡Video guardado en galería con éxito!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                neonCyan = neonCyan,
                                goldAccent = goldAccent,
                                cardBg = cardBackground
                            )

                            // SECTION 3: TAB SELECTOR (SUBTITLES, AUDIO MIXER, SUBTITLE FONTS)
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = darkBackground,
                            contentColor = neonPlum,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    color = neonCyan
                                )
                            }
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Subtítulos", fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Default.Subtitles, contentDescription = null) }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text("Mezclador", fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                            )
                            Tab(
                                selected = selectedTabIndex == 2,
                                onClick = { selectedTabIndex = 2 },
                                text = { Text("Estilo", fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Default.Palette, contentDescription = null) }
                            )
                        }

                        // SECTION 4: EDITING PANELS based on active tab
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp)
                        ) {
                            when (selectedTabIndex) {
                                0 -> {
                                    TimelineSubtitlesPanel(
                                        scenes = fullProj.scenes,
                                        subtitles = fullProj.subtitles,
                                        currentProgressMs = progressMs,
                                        cardBackground = cardBackground,
                                        neonCyan = neonCyan,
                                        onEditSubtitle = { showSubtitleEditDialog = it },
                                        onSeekTo = { viewModel.seekTo(it) }
                                    )
                                }
                                1 -> {
                                    MusicMixerPanel(
                                        project = fullProj.project,
                                        onMusicVolumeChange = { viewModel.updateMusicVolume(it) },
                                        onVoiceVolumeChange = { viewModel.updateVoiceVolume(it) },
                                        onChangeTrack = { viewModel.changeBackgroundMusic(it) },
                                        isPlaying = isPlaying,
                                        neonCyan = neonCyan,
                                        neonPlum = neonPlum,
                                        cardBg = cardBackground
                                    )
                                }
                                2 -> {
                                    SubtitleStylePanel(
                                        project = fullProj.project,
                                        onUpdateStyles = { f, c, s, l ->
                                            viewModel.updateSubtitleStyles(f, c, s, l)
                                        },
                                        neonCyan = neonCyan,
                                        hotPink = hotPink,
                                        cardBg = cardBackground
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
                }

                // OFF-EDITOR LANDING SCREEN (VIDEOS WORKSPACE CATALOGUE)
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            LandingHeaderWidget(
                                isApiKeyMissing = isApiKeyMissing,
                                onAddProjectClick = { showProjectCreator = true },
                                neonCyan = neonCyan,
                                neonPlum = neonPlum,
                                goldAccent = goldAccent,
                                cardBg = cardBackground
                            )
                        }

                        // PROJECT STORAGE LIST HEADER
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tus Proyectos de Video",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${projects.size} Videos",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        if (projects.isEmpty()) {
                            // EMPTY ARCHIVE CORNER
                            item {
                                EmptyLandingWidget(
                                    onAddButton = { showProjectCreator = true },
                                    cardBg = cardBackground,
                                    neonCyan = neonCyan
                                )
                            }
                        } else {
                            items(projects) { project ->
                                ProjectArchiveRow(
                                    project = project,
                                    onClick = { viewModel.selectProject(project) },
                                    onDelete = { viewModel.deleteProject(project.id) },
                                    neonCyan = neonCyan,
                                    cardBg = cardBackground
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(60.dp))
                        }
                    }
                }
            }

            // FULLSCREEN GENERATION STATUS WINDOW
            if (isGenerating) {
                CreationServiceProgressOverlay(
                    status = generationStatus,
                    error = generationError,
                    onDismissError = { viewModel.isGenerating.value = false },
                    darkBg = darkBackground,
                    cardBg = cardBackground,
                    neonPlum = neonPlum,
                    neonCyan = neonCyan
                )
            }

            // CREATOR INPUT SHEET / DIALOG MODAL
            if (showProjectCreator) {
                AiProjectCreatorModal(
                    prompt = promptInput,
                    onPromptChange = { promptInput = it },
                    onDismiss = { showProjectCreator = false },
                    onGenerate = {
                        showProjectCreator = false
                        viewModel.generateAIProject(promptInput)
                        promptInput = "" // reset
                    },
                    neonPlum = neonPlum,
                    neonCyan = neonCyan,
                    cardBg = cardBackground,
                    darkBg = darkBackground
                )
            }

            // IN-PLACE SUBTITLE EDITOR DIALOG
            showSubtitleEditDialog?.let { subItem ->
                SubtitleEditTextDialog(
                    subtitle = subItem,
                    onDismiss = { showSubtitleEditDialog = null },
                    onConfirm = { text ->
                        viewModel.editSubtitleText(subItem.id, text)
                        showSubtitleEditDialog = null
                    },
                    cardBg = cardBackground,
                    darkBg = darkBackground,
                    neonCyan = neonCyan
                )
            }
        }
    }
}

// ======================= COMPONENT WIDGETS =======================

@Composable
fun VideoMonitorScreen(
    project: VideoProjectEntity,
    scenes: List<SceneEntity>,
    activeSceneIndex: Int,
    progressMs: Long,
    activeSubtitle: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    neonCyan: Color,
    hotPink: Color,
    cardBackground: Color
) {
    if (scenes.isEmpty()) return
    val activeScene = scenes.getOrNull(activeSceneIndex) ?: scenes.first()
    val totalMs = scenes.sumOf { it.durationMs }

    // Animations of camera zooms inside canvas to emulate modern transitions
    val infiniteTransition = rememberInfiniteTransition(label = "player_zoom")
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "zoom_panning"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // PREVIEW CANVAS SCREEN
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(2.dp, neonCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .testTag("preview_monitor_box"),
            contentAlignment = Alignment.Center
        ) {
            // Simulated Animated Camera Space (Drawing complex cinematic gradients)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val activeColorHex = when (activeScene.sequenceIndex % 4) {
                            0 -> Color(0xFF1E1B4B) // Dark indigo
                            1 -> Color(0xFF581C87) // Dark purple
                            2 -> Color(0xFF0F172A) // Slate
                            else -> Color(0xFF831843) // Plum
                        }
                        val gradient = Brush.radialGradient(
                            colors = listOf(activeColorHex, Color(0xFF090510)),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * scaleFactor
                        )
                        drawRect(brush = gradient)

                        // Draw abstract grid lines fading
                        val gridInterval = 40.dp.toPx()
                        for (x in 0..(size.width / gridInterval).toInt()) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(x * gridInterval, 0f),
                                end = Offset(x * gridInterval, size.height)
                            )
                        }
                    }
            )

            // Dynamic Scene Overlay: Brief Prompt Summary and details
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Info Tracker Bar inside player
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Escena ${activeScene.sequenceIndex + 1} de ${scenes.size}",
                        fontSize = 11.sp,
                        color = neonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeScene.transitionEffect,
                        fontSize = 10.sp,
                        color = hotPink,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }

                // ✅ FASE 3: Imagen real generada por Pollinations.ai
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val imageUrl = remember(activeScene.sequenceIndex, activeScene.aiImagePrompt) {
                        PollinationsImageService.getImageUrl(
                            prompt = activeScene.aiImagePrompt,
                            seed = activeScene.sequenceIndex
                        )
                    }
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = activeScene.visualCaption,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = neonCyan,
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = activeScene.visualCaption,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                        fontStyle = FontStyle.Italic,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF1E1B4B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeScene.visualCaption,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    )
                }

                // SUBTITLE DYNAMIC DISPLAY RENDERER (BASED ON STYLES)
                val subtitleFam = when (project.subtitleFamily) {
                    "IMPACT" -> FontFamily.SansSerif
                    "DISPLAY" -> FontFamily.Cursive
                    "MONO" -> FontFamily.Monospace
                    else -> FontFamily.Default
                }
                val subtitleColorValue = try {
                    Color(android.graphics.Color.parseColor(project.subtitleColor))
                } catch (e: Exception) {
                    Color.Yellow
                }
                val subtitleWeight = when (project.subtitleFamily) {
                    "IMPACT" -> FontWeight.Black
                    else -> FontWeight.Bold
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = when (project.subtitleLocation) {
                        "TOP" -> Alignment.TopCenter
                        "CENTER" -> Alignment.Center
                        else -> Alignment.BottomCenter
                    }
                ) {
                    if (activeSubtitle.isNotEmpty()) {
                        Text(
                            text = activeSubtitle,
                            fontSize = 24.sp,
                            fontFamily = subtitleFam,
                            fontWeight = subtitleWeight,
                            color = subtitleColorValue,
                            textAlign = TextAlign.Center,
                            style = LocalTextStyle.current.copy(
                                shadow = if (project.subtitleStyle == "OUTLINE") {
                                    Shadow(color = Color.Black, offset = Offset(2f, 2f), blurRadius = 3f)
                                } else null
                            ),
                            modifier = Modifier
                                .then(
                                    if (project.subtitleStyle == "BACKGROUND") {
                                        Modifier
                                            .background(
                                                Color.Black.copy(alpha = 0.75f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    } else Modifier
                                )
                                .animateContentSize()
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .testTag("player_fullscreen_enter_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Pantalla completa",
                    tint = neonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // MOUNT TRACK CONTROLS
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // PLAY BUTTON
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .background(neonCyan, RoundedCornerShape(50))
                    .testTag("player_play_pause_btn")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play button",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }

            // DURATIONS PROGRESS SLIDER
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = progressMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..totalMs.toFloat(),
                    modifier = Modifier.testTag("player_timeline_slider"),
                    colors = SliderDefaults.colors(
                        activeTrackColor = neonCyan,
                        inactiveTrackColor = Color.DarkGray,
                        thumbColor = neonCyan
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(progressMs),
                        fontSize = 12.sp,
                        color = neonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Total: ${formatTime(totalMs.toLong())}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectHeaderWidget(
    project: VideoProjectEntity,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    neonCyan: Color,
    goldAccent: Color,
    cardBg: Color
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = project.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Inspirado en: \"${project.prompt}\"",
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // DELETE DISCARD WIDGET
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F1F27)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("delete_project_studio_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eliminar", color = Color(0xFFF43F5E), fontSize = 13.sp)
                }

                // EXPORT RENDER TRIGGER WIDGET
                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(containerColor = neonCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_video_studio_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (project.isExported) Icons.Default.Check else Icons.Default.FileDownload,
                        contentDescription = "Export Video",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (project.isExported) "Volver a Renderizar" else "Renderizar MP4",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar Proyecto?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Esta acción es irreversible y borrará el guion de video, subtítulos y mezclas guardadas.", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Borrar Proyecto", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = cardBg
        )
    }
}

// ======================= PANEL TABS SUB-WIDGETS =======================

@Composable
fun TimelineSubtitlesPanel(
    scenes: List<SceneEntity>,
    subtitles: List<SubtitleSegmentEntity>,
    currentProgressMs: Long,
    cardBackground: Color,
    neonCyan: Color,
    onEditSubtitle: (SubtitleSegmentEntity) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Línea de Tiempo y Subtítulos",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${subtitles.size} Segmentos",
                fontSize = 12.sp,
                color = neonCyan
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // HORIZONTAL SECUENTIAL STORYBOARD SCENES
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(scenes) { scene ->
                val sceneStartMs = scenes.filter { it.sequenceIndex < scene.sequenceIndex }.sumOf { it.durationMs }
                val sceneEndMs = sceneStartMs + scene.durationMs
                val isFocused = currentProgressMs >= sceneStartMs && currentProgressMs < sceneEndMs

                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isFocused) Color(0xFF2E1065) else cardBackground)
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) neonCyan else Color.DarkGray,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSeekTo(sceneStartMs.toLong() + 500) }
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Escena ${scene.sequenceIndex + 1}",
                                fontSize = 11.sp,
                                color = if (isFocused) neonCyan else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${scene.durationMs / 1000}s",
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = scene.visualCaption,
                            fontSize = 10.sp,
                            color = Color.White,
                            maxLines = 2,
                            modifier = Modifier.weight(1f).padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SUBTITLES VERTICAL SEQUENTIAL CHRONOGRAMS LIST
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Editor Sincronizado de Redacción",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (subtitles.isEmpty()) {
                    Text("No hay subtítulos disponibles.", color = Color.White, fontSize = 12.sp)
                } else {
                    subtitles.forEach { subtitle ->
                        val isActive = currentProgressMs >= subtitle.startMs && currentProgressMs < subtitle.endMs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) Color(0xFF1E3A8A).copy(alpha = 0.5f) else Color.Transparent)
                                .clickable { onSeekTo(subtitle.startMs) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pin Marker Icons
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (isActive) neonCyan else Color.DarkGray,
                                    modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text(
                                        text = "[${formatTime(subtitle.startMs)} - ${formatTime(subtitle.endMs)}]",
                                        fontSize = 11.sp,
                                        color = if (isActive) neonCyan else Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = subtitle.text,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onEditSubtitle(subtitle) },
                                modifier = Modifier.size(36.dp).testTag("edit_sub_pencil_${subtitle.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit segment",
                                    tint = neonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Divider(color = Color.DarkGray.copy(alpha = 0.4f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun MusicMixerPanel(
    project: VideoProjectEntity,
    onMusicVolumeChange: (Float) -> Unit,
    onVoiceVolumeChange: (Float) -> Unit,
    onChangeTrack: (String) -> Unit,
    isPlaying: Boolean,
    neonCyan: Color,
    neonPlum: Color,
    cardBg: Color
) {
    val musicTracks = listOf(
        "Retro Beats" to "Sintetizador de onda ochentera ritmo rápido",
        "Lo-Fi Lounge" to "Música de ambiente jazz relajado nocturno",
        "Cinematic Orchestral" to "Cuerdas dramáticas para narración profunda",
        "Ambient Waves" to "Frecuencia de calma y bienestar espiritual",
        "Techno Pulse" to "Arpegio de velocidad y secuencias cyber"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Estación de Audio y Mezclas",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Pruebe y edite volúmenes de canales de forma independiente en vivo",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(14.dp))

        // MIXER CONSOLES
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // CHANNEL A: MUSIC SLIDER
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = neonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Instrumental de Fondo AI", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        Text("${(project.musicVolume * 100).toInt()}%", fontSize = 12.sp, color = neonCyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = project.musicVolume,
                        onValueChange = onMusicVolumeChange,
                        colors = SliderDefaults.colors(activeTrackColor = neonCyan, thumbColor = neonCyan),
                        modifier = Modifier.testTag("mixer_music_volume_slider")
                    )
                }

                // CHANNEL B: VOICE LEVEL SLIDER
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = neonPlum, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Locutor de Voz Narrativa AI", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        Text("${(project.voiceVolume * 100).toInt()}%", fontSize = 12.sp, color = neonPlum, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = project.voiceVolume,
                        onValueChange = onVoiceVolumeChange,
                        colors = SliderDefaults.colors(activeTrackColor = neonPlum, thumbColor = neonPlum),
                        modifier = Modifier.testTag("mixer_voice_volume_slider")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // MOOD GENERATOR TRACKS SELECTOR
        Text(
            text = "Elegir Pista de Banda Sonora",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(musicTracks) { (trackName, trackDesc) ->
                val isSelected = project.backgroundMusic == trackName
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF0F172A) else cardBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) neonCyan else Color.DarkGray,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onChangeTrack(trackName) }
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = trackName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) neonCyan else Color.White
                            )

                            if (isSelected && isPlaying) {
                                // Custom synthesized glowing audio bars bouncing
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.height(10.dp)
                                ) {
                                    val infiniteT = rememberInfiniteTransition(label = "audio_anim")
                                    val barH1 by infiniteT.animateFloat(
                                        initialValue = 2f, targetValue = 9f,
                                        animationSpec = infiniteRepeatable(tween(250), RepeatMode.Reverse), label = "h1"
                                    )
                                    val barH2 by infiniteT.animateFloat(
                                        initialValue = 8f, targetValue = 3f,
                                        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "h2"
                                    )
                                    Box(Modifier.width(2.dp).fillMaxHeight().drawBehind { drawRect(neonCyan) })
                                    Box(Modifier.width(2.dp).height(barH1.dp).drawBehind { drawRect(neonCyan) })
                                    Box(Modifier.width(2.dp).height(barH2.dp).drawBehind { drawRect(neonCyan) })
                                }
                            } else {
                                Icon(Icons.Default.AudioFile, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = trackDesc,
                            fontSize = 10.sp,
                            color = Color.LightGray,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleStylePanel(
    project: VideoProjectEntity,
    onUpdateStyles: (String, String, String, String) -> Unit,
    neonCyan: Color,
    hotPink: Color,
    cardBg: Color
) {
    val subtitleColors = listOf(
        "#FFEB3B" to "Cyber Yellow",
        "#E91E63" to "Neon Pink",
        "#00E676" to "Matrix Green",
        "#00E5FF" to "Ocean Blue",
        "#FFFFFF" to "Pure White"
    )

    val subtitleFonts = listOf(
        "IMPACT" to "Impact (Neo-Sans)",
        "DISPLAY" to "Cinematic Script",
        "MONO" to "Techno Code",
        "SANS" to "Minimalist Sans"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Estilos y Diseño de Subtítulos",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Personalice las fuentes tipográficas y colores de subtítulo generados por la IA",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. SELECT NEON COLOR ROW
                Column {
                    Text("Paleta de Color de Presaltón", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        subtitleColors.forEach { (colorHex, colorLabel) ->
                            val colorValue = Color(android.graphics.Color.parseColor(colorHex))
                            val isSelected = project.subtitleColor == colorHex

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdateStyles(project.subtitleFamily, colorHex, project.subtitleStyle, project.subtitleLocation) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(colorValue)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(50)
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(colorLabel.split(" ")[1], fontSize = 10.sp, color = Color.LightGray)
                            }
                        }
                    }
                }

                Divider(color = Color.DarkGray.copy(alpha = 0.5f))

                // 2. SELECT TYPOGRAPHIC GROUP
                Column {
                    Text("Tipografías Disponibles", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(subtitleFonts) { (fontKey, fontName) ->
                            val isSelected = project.subtitleFamily == fontKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) neonCyan else Color.DarkGray.copy(alpha = 0.4f))
                                    .clickable { onUpdateStyles(fontKey, project.subtitleColor, project.subtitleStyle, project.subtitleLocation) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = fontName,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Divider(color = Color.DarkGray.copy(alpha = 0.5f))

                // 3. SELECT STYLE BACKDROP AND POSITION STRIP
                Column {
                    Text("Ubicación y Sombreado", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // POSITION BOXES
                        Button(
                            onClick = {
                                val nextLoc = when (project.subtitleLocation) {
                                    "BOTTOM" -> "TOP"
                                    "TOP" -> "CENTER"
                                    else -> "BOTTOM"
                                }
                                onUpdateStyles(project.subtitleFamily, project.subtitleColor, project.subtitleStyle, nextLoc)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Posición: ${project.subtitleLocation}", fontSize = 11.sp, color = Color.White)
                        }

                        // STYLE BACKDROP SWITCH
                        Button(
                            onClick = {
                                val nextStyle = when (project.subtitleStyle) {
                                    "OUTLINE" -> "BACKGROUND"
                                    "BACKGROUND" -> "NONE"
                                    else -> "OUTLINE"
                                }
                                onUpdateStyles(project.subtitleFamily, project.subtitleColor, nextStyle, project.subtitleLocation)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Estilo: ${project.subtitleStyle}", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ======================= CATALOG ARCHIVE ROW =======================

@Composable
fun LandingHeaderWidget(
    isApiKeyMissing: Boolean,
    onAddProjectClick: () -> Unit,
    neonCyan: Color,
    neonPlum: Color,
    goldAccent: Color,
    cardBg: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "AI Core",
            tint = neonCyan,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Crear Videos con Inteligencia Artificial",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            text = "En VidAI Studio puedes redactar un tema y generar de forma instantánea videos largos, con guion, timmings, subtítulos sincronizados automáticos y banda sonora sintética.",
            fontSize = 12.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isApiKeyMissing) {
            // WARNING ACCESSIBILITY AREA ABOUT CREDENTIALS PANEL
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3F2B15)),
                border = BorderStroke(1.dp, goldAccent)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = goldAccent)
                    Column {
                        Text(
                            text = "Falta clave API de Gemini",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Por favor agregue GEMINI_API_KEY en el panel lateral de Secrets de AI Studio para permitir la generación inteligente de videos.",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        // TRIGGER CREATOR MODAL PANEL
        Button(
            onClick = onAddProjectClick,
            colors = ButtonDefaults.buttonColors(containerColor = neonCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("create_new_video_hero_btn"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Icon", tint = Color.Black)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Crear Nuevo Video Inteligente", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProjectArchiveRow(
    project: VideoProjectEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    neonCyan: Color,
    cardBg: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("project_card_item_${project.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.5.dp, Color.DarkGray)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circle visual logo representing cinematic projects
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF261F36)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (project.isExported) Icons.Default.CheckCircle else Icons.Default.MovieFilter,
                        contentDescription = null,
                        tint = if (project.isExported) Color.Green else neonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = project.title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "${project.durationSeconds} segs • Pista: ${project.backgroundMusic}",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("archive_delete_row_${project.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar",
                    tint = Color(0xFFF43F5E).copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun EmptyLandingWidget(
    onAddButton: () -> Unit,
    cardBg: Color,
    neonCyan: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Movie,
            contentDescription = "No projects",
            tint = Color.DarkGray,
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Ningún video creado todavía",
            fontSize = 14.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        TextButton(onClick = onAddButton) {
            Text("Escriba su primera idea ahora mismo", color = neonCyan)
        }
    }
}

// ======================= MODAL BOTTOM INTERACTION SHEETS =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProjectCreatorModal(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
    neonPlum: Color,
    neonCyan: Color,
    cardBg: Color,
    darkBg: Color
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        containerColor = darkBg,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Generar Video Inteligente",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Describe detalladamente el tema de tu video de larga duración (ej: 'Misterios sin resolver del triángulo de las bermudas', o 'Cozy lo-fi beats con escenario de lluvia cyberpunk'). Gemini redactará el storyboard.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("creator_prompt_input_field"),
                    maxLines = 5,
                    placeholder = { Text("Escribe una idea en español para el video...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = neonCyan,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = cardBg),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Regresar", color = Color.LightGray)
                    }

                    Button(
                        onClick = onGenerate,
                        enabled = prompt.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = neonCyan,
                            disabledContainerColor = Color.DarkGray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("creator_generate_commit_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Generar con IA", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

@Composable
fun SubtitleEditTextDialog(
    subtitle: SubtitleSegmentEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    cardBg: Color,
    darkBg: Color,
    neonCyan: Color
) {
    var textInput by remember { mutableStateOf(subtitle.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Subtítulo", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column {
                Text(
                    text = "Ajustar las palabras que se muestran entre ${formatTime(subtitle.startMs)} y ${formatTime(subtitle.endMs)}",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subtitle_dialog_editor_field"),
                    placeholder = { Text("Palabras del subtítulo") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = neonCyan,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(textInput) },
                colors = ButtonDefaults.buttonColors(containerColor = neonCyan),
                modifier = Modifier.testTag("subtitle_dialog_approve_btn")
            ) {
                Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        },
        containerColor = cardBg
    )
}

// ======================= STATUS PROGRESS OVERLAYS =======================

@Composable
fun CreationServiceProgressOverlay(
    status: String,
    error: String?,
    onDismissError: () -> Unit,
    darkBg: Color,
    cardBg: Color,
    neonPlum: Color,
    neonCyan: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg.copy(alpha = 0.92f))
            .clickable(enabled = false) {}, // absorb touches
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (error != null) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error Logo",
                    tint = Color(0xFFF43F5E),
                    modifier = Modifier.size(60.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Ocurrió un Contratiempo",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Button(
                    onClick = onDismissError,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F1F27)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("De acuerdo", color = Color(0xFFF43F5E))
                }
            } else {
                Text(
                    text = "Estableciendo parámetros AI...",
                    fontSize = 16.sp,
                    color = neonPlum,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                CircularProgressIndicator(
                    color = neonCyan,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = status,
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                // Subtitle AI algorithm hint text
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "El proceso suele tardar de 15 a 45 segundos según la complejidad de la historia.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ExportRenderOverlay(
    step: String,
    progress: Float,
    darkBackground: Color,
    neonPlum: Color,
    neonCyan: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Icon(
                imageVector = Icons.Default.VideoSettings,
                contentDescription = "Rendering engine",
                tint = neonPlum,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Renderizando Video Largo con IA",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Uniendo fotogramas conceptuales, combinando subtítulos automáticos y codificando la mezcla de audio...",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Dynamic progression bar
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("render_progress_bar"),
                color = neonCyan,
                trackColor = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step,
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = neonCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FullscreenVideoPlayer(
    fullProj: FullVideoProject,
    activeSceneIndex: Int,
    progressMs: Long,
    activeSubtitle: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onExitFullscreen: () -> Unit,
    neonCyan: Color,
    hotPink: Color,
    cardBackground: Color
) {
    val project = fullProj.project
    val scenes = fullProj.scenes
    if (scenes.isEmpty()) return
    val activeScene = scenes.getOrNull(activeSceneIndex) ?: scenes.first()
    val totalMs = scenes.sumOf { it.durationMs }

    val infiniteTransition = rememberInfiniteTransition(label = "player_zoom_fullscreen")
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 1.1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "zoom_panning"
    )

    var showControls by remember { mutableStateOf(true) }
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            kotlinx.coroutines.delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
            .testTag("fullscreen_player_container"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val activeColorHex = when (activeScene.sequenceIndex % 4) {
                        0 -> Color(0xFF131130)
                        1 -> Color(0xFF3B0764)
                        2 -> Color(0xFF020617)
                        else -> Color(0xFF500725)
                    }
                    val gradient = Brush.radialGradient(
                        colors = listOf(activeColorHex, Color(0xFF020105)),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width * scaleFactor
                    )
                    drawRect(brush = gradient)

                    val gridInterval = 60.dp.toPx()
                    for (x in 0..(size.width / gridInterval).toInt()) {
                        drawLine(
                            color = neonCyan.copy(alpha = 0.04f),
                            start = Offset(x * gridInterval, 0f),
                            end = Offset(x * gridInterval, size.height)
                        )
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = project.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Escena ${activeScene.sequenceIndex + 1} de ${scenes.size}: ${activeScene.visualCaption}",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = onExitFullscreen,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                            .testTag("player_fullscreen_exit_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Salir de pantalla completa",
                            tint = neonCyan
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showControls) {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(72.dp)
                            .background(neonCyan.copy(alpha = 0.85f), RoundedCornerShape(50))
                            .testTag("fullscreen_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Pantalla Completa Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            val subtitleFam = when (project.subtitleFamily) {
                "IMPACT" -> FontFamily.SansSerif
                "DISPLAY" -> FontFamily.Cursive
                "MONO" -> FontFamily.Monospace
                else -> FontFamily.Default
            }
            val subtitleColorValue = try {
                Color(android.graphics.Color.parseColor(project.subtitleColor))
            } catch (e: Exception) {
                Color.Yellow
            }
            val subtitleWeight = when (project.subtitleFamily) {
                "IMPACT" -> FontWeight.Black
                else -> FontWeight.Bold
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = when (project.subtitleLocation) {
                        "TOP" -> Alignment.TopCenter
                        "CENTER" -> Alignment.Center
                        else -> Alignment.BottomCenter
                    }
                ) {
                    if (activeSubtitle.isNotEmpty()) {
                        Text(
                            text = activeSubtitle,
                            fontSize = 32.sp,
                            fontFamily = subtitleFam,
                            fontWeight = subtitleWeight,
                            color = subtitleColorValue,
                            textAlign = TextAlign.Center,
                            style = LocalTextStyle.current.copy(
                                shadow = if (project.subtitleStyle == "OUTLINE") {
                                    Shadow(color = Color.Black, offset = Offset(3f, 3f), blurRadius = 4f)
                                } else null
                            ),
                            modifier = Modifier
                                .then(
                                    if (project.subtitleStyle == "BACKGROUND") {
                                        Modifier
                                            .background(
                                                Color.Black.copy(alpha = 0.85f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    } else Modifier
                                )
                                .animateContentSize()
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Slider(
                            value = progressMs.toFloat(),
                            onValueChange = { onSeek(it.toLong()) },
                            valueRange = 0f..totalMs.toFloat(),
                            modifier = Modifier.testTag("player_fullscreen_slider"),
                            colors = SliderDefaults.colors(
                                activeTrackColor = neonCyan,
                                inactiveTrackColor = Color.DarkGray,
                                thumbColor = neonCyan
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(progressMs),
                                fontSize = 13.sp,
                                color = neonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Text(
                                text = "Transición: ${activeScene.transitionEffect}",
                                fontSize = 11.sp,
                                color = hotPink,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Total: ${formatTime(totalMs.toLong())}",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

// ======================= HELPERS =======================

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliSegment = (ms % 1000) / 100
    return String.format("%02d:%02d.%d", minutes, seconds, milliSegment)
}
