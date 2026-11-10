package dev.fl3xy.wallhue

import android.app.WallpaperManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.material.color.utilities.MaterialDynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

private enum class ApplyState { Idle, Working, Done, Failed }
private enum class Shiz { Missing, NeedsPermission, Ready }

private fun shizukuState(): Shiz = when {
    !Shizuku.pingBinder() -> Shiz.Missing
    Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Shiz.NeedsPermission
    else -> Shiz.Ready
}

@Composable
private fun rememberShizukuState(): Pair<Shiz, () -> Unit> {
    var state by remember { mutableStateOf(shizukuState()) }
    DisposableEffect(Unit) {
        val refresh = { state = shizukuState() }
        val received = Shizuku.OnBinderReceivedListener { refresh() }
        val dead = Shizuku.OnBinderDeadListener { refresh() }
        val result = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(result)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(result)
        }
    }
    return state to {
        if (state == Shiz.NeedsPermission) Shizuku.requestPermission(1) else state = shizukuState()
    }
}

private fun schemeFor(seed: Int, dark: Boolean): ColorScheme {
    val s = ThemeEngine.scheme(seed, dark)
    fun c(pick: (MaterialDynamicColors) -> com.google.android.material.color.utilities.DynamicColor) =
        Color(ThemeEngine.argb(s, pick))
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c { it.primary() }, onPrimary = c { it.onPrimary() },
        primaryContainer = c { it.primaryContainer() }, onPrimaryContainer = c { it.onPrimaryContainer() },
        secondary = c { it.secondary() }, onSecondary = c { it.onSecondary() },
        secondaryContainer = c { it.secondaryContainer() }, onSecondaryContainer = c { it.onSecondaryContainer() },
        tertiary = c { it.tertiary() }, onTertiary = c { it.onTertiary() },
        tertiaryContainer = c { it.tertiaryContainer() }, onTertiaryContainer = c { it.onTertiaryContainer() },
        background = c { it.background() }, onBackground = c { it.onBackground() },
        surface = c { it.surface() }, onSurface = c { it.onSurface() },
        surfaceVariant = c { it.surfaceVariant() }, onSurfaceVariant = c { it.onSurfaceVariant() },
        surfaceContainerLow = c { it.surfaceContainerLow() }, surfaceContainer = c { it.surfaceContainer() },
        surfaceContainerHigh = c { it.surfaceContainerHigh() },
        surfaceContainerHighest = c { it.surfaceContainerHighest() },
        outline = c { it.outline() }, outlineVariant = c { it.outlineVariant() },
    )
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = spring<Color>(stiffness = 120f)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "c").value
    return copy(
        primary = a(primary), onPrimary = a(onPrimary), primaryContainer = a(primaryContainer),
        onPrimaryContainer = a(onPrimaryContainer), secondaryContainer = a(secondaryContainer),
        onSecondaryContainer = a(onSecondaryContainer), tertiaryContainer = a(tertiaryContainer),
        onTertiaryContainer = a(onTertiaryContainer), background = a(background), surface = a(surface),
        onSurface = a(onSurface), onSurfaceVariant = a(onSurfaceVariant),
        surfaceContainer = a(surfaceContainer), surfaceContainerHigh = a(surfaceContainerHigh),
        surfaceContainerHighest = a(surfaceContainerHighest), outlineVariant = a(outlineVariant),
    )
}

@Composable
private fun App() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var seeds by remember { mutableStateOf(listOf(0xFF4F90ED.toInt())) }
    var selected by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(2) } // 0 home, 1 lock, 2 both
    var applyState by remember { mutableStateOf(ApplyState.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    val (shiz, onShizuku) = rememberShizukuState()

    val seed = seeds.getOrElse(selected) { seeds.first() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
            val found = withContext(Dispatchers.Default) { ThemeEngine.seeds(bmp) }
            bitmap = bmp; seeds = found.ifEmpty { seeds }; selected = 0; applyState = ApplyState.Idle
        }
    }

    MaterialExpressiveTheme(colorScheme = schemeFor(seed, dark).animated(), motionScheme = MotionScheme.expressive()) {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                LargeTopAppBar(
                    title = { Text("Wallhue", fontWeight = FontWeight.SemiBold) },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
        ) { pad ->
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ShizukuCard(shiz, onShizuku)
                Preview(bitmap) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

                AnimatedVisibility(bitmap != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Section("Seed color") {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                seeds.forEachIndexed { i, c -> SeedDot(Color(c), i == selected) { selected = i; applyState = ApplyState.Idle } }
                            }
                        }
                        Section("Palette") { PaletteStrip(seed, dark) }
                        Section("Apply wallpaper to") {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf("Home", "Lock", "Both").forEachIndexed { i, label ->
                                    SegmentedButton(
                                        selected = target == i, onClick = { target = i },
                                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
                }

                ApplyButton(
                    state = applyState,
                    enabled = bitmap != null && shiz == Shiz.Ready && applyState != ApplyState.Working,
                ) {
                    val bmp = bitmap ?: return@ApplyButton
                    applyState = ApplyState.Working; error = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching {
                                val flags = when (target) {
                                    0 -> WallpaperManager.FLAG_SYSTEM
                                    1 -> WallpaperManager.FLAG_LOCK
                                    else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                                }
                                ThemeEngine.setWallpaper(context, bmp, flags)
                                ThemeEngine.applyColors(seed)
                            }
                        }
                        applyState = if (r.isSuccess) ApplyState.Done else ApplyState.Failed
                        error = r.exceptionOrNull()?.let { (it.cause ?: it).message ?: it.javaClass.simpleName }
                    }
                }

                AnimatedVisibility(error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                TextButton(
                    onClick = {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { ThemeEngine.resetColors() } }
                            error = r.exceptionOrNull()?.let { (it.cause ?: it).message }
                            applyState = ApplyState.Idle
                        }
                    },
                    enabled = shiz == Shiz.Ready,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(8.dp)); Text("Reset app colors")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    LaunchedEffect(applyState) {
        if (applyState == ApplyState.Done || applyState == ApplyState.Failed) { delay(2600); applyState = ApplyState.Idle }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ShizukuCard(state: Shiz, onClick: () -> Unit) {
    val ready = state == Shiz.Ready
    val container by animateColorAsState(
        if (ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer, label = "sc",
    )
    Card(
        onClick = onClick, shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(ready, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "si") {
                Icon(if (it) Icons.Rounded.Link else Icons.Rounded.LinkOff, null)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    when (state) {
                        Shiz.Ready -> "Shizuku connected"
                        Shiz.NeedsPermission -> "Allow Shizuku access"
                        Shiz.Missing -> "Shizuku not running"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when (state) {
                        Shiz.Ready -> "Only needed while applying. Nothing runs in the background."
                        Shiz.NeedsPermission -> "Tap to grant permission"
                        Shiz.Missing -> "Start it in the Shizuku app, then tap here"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Preview(bitmap: Bitmap?, onPick: () -> Unit) {
    Card(
        onClick = onPick,
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).aspectRatio(0.75f),
    ) {
        AnimatedContent(
            bitmap,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith (fadeOut() + scaleOut(targetScale = 1.04f)) },
            label = "preview",
        ) { bmp ->
            if (bmp == null) {
                Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(40.dp), MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.height(16.dp))
                    Text("Pick a wallpaper", style = MaterialTheme.typography.titleLarge)
                    Text("Google apps will follow its colors", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box {
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    FilledTonalButton(onClick = onPick, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Change")
                    }
                }
            }
        }
    }
}

@Composable
private fun SeedDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val size by animateDpAsState(if (selected) 56.dp else 44.dp, spring(dampingRatio = 0.45f, stiffness = 400f), label = "s")
    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, label = "r")
    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape).border(3.dp, ring, CircleShape).padding(4.dp)
                .clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(Icons.Rounded.Check, null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White)
            }
        }
    }
}

private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun PaletteStrip(seed: Int, dark: Boolean) {
    val s = remember(seed, dark) { ThemeEngine.scheme(seed, dark) }
    val tones = listOf(90, 80, 70, 60, 50, 40, 30, 20)
    Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(s.primaryPalette, s.secondaryPalette, s.tertiaryPalette, s.neutralPalette).forEach { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tones.forEach { t ->
                    val c by animateColorAsState(Color(p.tone(t)), spring(stiffness = 200f), label = "t")
                    Box(Modifier.weight(1f).height(28.dp).background(c))
                }
            }
        }
    }
}

@Composable
private fun ApplyButton(state: ApplyState, enabled: Boolean, onClick: () -> Unit) {
    val width by animateDpAsState(
        if (state == ApplyState.Working) 96.dp else 400.dp, spring(dampingRatio = 0.6f, stiffness = 300f), label = "w",
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick, enabled = enabled || state == ApplyState.Working,
            modifier = Modifier.height(64.dp).width(width),
            colors = if (state == ApplyState.Failed) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ) else ButtonDefaults.buttonColors(),
        ) {
            AnimatedContent(
                state,
                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "apply",
            ) { s ->
                when (s) {
                    ApplyState.Working -> LoadingIndicator(Modifier.size(36.dp), color = MaterialTheme.colorScheme.onPrimary)
                    ApplyState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Applied")
                    }
                    ApplyState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null); Spacer(Modifier.width(8.dp)); Text("Failed")
                    }
                    ApplyState.Idle -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp))
                        Text("Apply wallpaper & colors", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
