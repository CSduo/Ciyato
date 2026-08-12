package com.ciyato.launcher.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.components.CiyatoButton
import com.ciyato.launcher.ui.components.CiyatoStepIndicator
import com.ciyato.launcher.ui.components.directionResetPointerInput
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoBgEl2
import com.ciyato.launcher.ui.theme.CiyatoBorder
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoGoldSoft
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoStrongBorder
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite

private data class OnboardingPanel(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private enum class OnboardingVisual {
    HOME,
    ORGANIZE,
    DRAWER,
    GESTURES,
    PERMISSIONS,
    HANDOFF,
}

private data class OnboardingPage(
    val icon: ImageVector,
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val bullets: List<String>,
    val panels: List<OnboardingPanel>,
    /** If true, this page shows the mini phone home preview. Only page 0 should. */
    val showHomePreview: Boolean = false,
    val visual: OnboardingVisual = OnboardingVisual.HOME,
)

// ─── Page definitions ────────────────────────────────────────────────────────
// Page 3 (Screenshot 01): Product purpose — the ONLY slide with a home preview.
// Page 4 (Screenshot 02): Honest first-run state — no fake weather/agenda.
// Page 5 (Screenshot 03): "From chaos to clarity" transformation explanation.
// Page 6 (Screenshot 04): Smart App Library — swipe-up drawer, real categories.
// Page 7 (Screenshot 05): Real gestures — swipe up, long-press, tap category.
// Page 8 (Screenshot 06): Permission gates — SAF, Photo Picker, location.
// Page 9 (Screenshot 07): Final handoff — set as home, configure, or skip.

// Three pages, ~30 seconds total. Depth lives in the app, not the intro.
private val detailedPages = listOf(
    // ── 1. What it is ──
    OnboardingPage(
        icon = Icons.Default.AutoAwesome,
        eyebrow = "AI Phone Organizer",
        title = "From chaos to clarity.",
        subtitle = "Your phone, beautifully organized.",
        body = "A premium home screen that sorts your apps, files, and photos into clean, smart categories — privately, on your device.",
        bullets = emptyList(),
        panels = emptyList(),
        showHomePreview = true,
        visual = OnboardingVisual.HOME,
    ),

    // ── 2. How you use it ──
    OnboardingPage(
        icon = Icons.Default.Home,
        eyebrow = "Three gestures",
        title = "Simple by design.",
        subtitle = "Everything else is just a tap away.",
        body = "",
        bullets = listOf(
            "Swipe up — open your organized App Library.",
            "Long-press — lift and move any icon, card, or group.",
            "Tap a group — see its apps; tap a mini-icon to launch instantly.",
        ),
        panels = emptyList(),
        visual = OnboardingVisual.GESTURES,
    ),

    // ── 3. Start ──
    OnboardingPage(
        icon = Icons.Default.Lock,
        eyebrow = "Private by design",
        title = "You stay in control.",
        subtitle = "Everything stays on this phone.",
        body = "No accounts, no uploads, no tracking. Permissions are asked only when a feature needs them — and every choice is reversible in Settings.",
        bullets = emptyList(),
        panels = emptyList(),
        visual = OnboardingVisual.HANDOFF,
    ),
)

// First-run uses the full V2 onboarding sequence. Each page gives enough context
// for an informed choice without requesting unrelated permissions up front.
private val pages = detailedPages

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onDone()
    }

    val directionalConnection = com.ciyato.launcher.ui.components.rememberDirectionalNestedScrollConnection()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF050607),
                        CiyatoBg,
                        Color(0xFF111416),
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandHeader()
            Spacer(Modifier.height(10.dp))

            // Swipe left/right between the three intro pages (or use the buttons).
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .directionResetPointerInput(directionalConnection)
                    .nestedScroll(directionalConnection),
                pageSpacing = 16.dp,
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex], pageIndex = pageIndex)
            }

            Spacer(Modifier.height(8.dp))
            CiyatoStepIndicator(
                totalSteps = pages.size,
                currentStep = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(10.dp))

            if (pagerState.currentPage < pages.lastIndex) {
                CiyatoButton(
                    text = "Continue",
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Final slide: primary action is Set as Home
                CiyatoButton(
                    text = "Set Ciyato as Home App",
                    onClick = { requestDefaultLauncher(context, roleRequestLauncher::launch, onDone) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val canGoBack = pagerState.currentPage > 0
                TextButton(
                    onClick = { if (canGoBack) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    enabled = canGoBack,
                ) {
                    Text("Back", color = if (canGoBack) CiyatoSec else CiyatoMuted, fontSize = 13.sp)
                }
                TextButton(onClick = onDone) {
                    Text("Skip for now", color = CiyatoMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─── Brand Header ─────────────────────────────────────────────────────────────
// Clean wordmark only: "Ciyato" + subtitle. No Private beta badge, no C* icon.
@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text("Ciyato", color = CiyatoWhite, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("AI Phone Organizer for Android", color = CiyatoSec, fontSize = 11.sp)
            }
        }
        Text("Setup guide", color = CiyatoSec, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Page content ─────────────────────────────────────────────────────────────
// Each page shows: HeroPanel + (MiniPhonePreview OR ProcessExplanation) + GuidancePanels
@Composable
private fun OnboardingPageContent(page: OnboardingPage, pageIndex: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroPanel(page = page, pageIndex = pageIndex)

        // Only the first slide (product introduction) shows the home preview.
        // All other slides show focused, unique educational content instead.
        if (page.showHomePreview) {
            MiniPhonePreview()
        } else {
            OnboardingVisualCard(visual = page.visual)
        }

        page.panels.forEach { panel ->
            GuidancePanel(panel = panel)
        }

        // Breathing room so the content clears cleanly
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun OnboardingVisualCard(visual: OnboardingVisual) {
    when (visual) {
        OnboardingVisual.HOME -> Unit
        OnboardingVisual.ORGANIZE -> OrganizationVisual()
        OnboardingVisual.DRAWER -> AppLibraryVisual()
        OnboardingVisual.GESTURES -> GestureVisual()
        OnboardingVisual.PERMISSIONS -> PermissionVisual()
        OnboardingVisual.HANDOFF -> SetupHandoffVisual()
    }
}

@Composable
private fun OnboardingVisualFrame(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoStrongBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = CiyatoWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = CiyatoMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        content()
    }
}

@Composable
private fun OrganizationVisual() {
    OnboardingVisualFrame(
        title = "From installed apps to your layout",
        subtitle = "Ciyato suggests a starting point. You decide what stays.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisualStep(Icons.Default.Apps, "Installed", "Real apps", Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
            VisualStep(Icons.Default.AutoAwesome, "Suggested", "Categories", Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
            VisualStep(Icons.Default.Edit, "Yours", "Edit anytime", Modifier.weight(1f))
        }
    }
}

@Composable
private fun VisualStep(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CiyatoBgEl2)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
        Column {
            Text(title, color = CiyatoWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(detail, color = CiyatoMuted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AppLibraryVisual() {
    OnboardingVisualFrame(
        title = "Your neutral app drawer",
        subtitle = "It opens from Home and stays focused on the apps you actually have.",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(CiyatoBg)
                .border(1.dp, CiyatoBorder, RoundedCornerShape(17.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CiyatoGold.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Swipe up", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Apps opens with real categories, search, and long-press actions.", color = CiyatoSec, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Text("Apps", color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GestureVisual() {
    OnboardingVisualFrame(
        title = "The launcher stays out of your way",
        subtitle = "The same three actions work from your everyday Home screen.",
    ) {
        GestureRow(Icons.Default.KeyboardArrowUp, "Swipe up", "Open Apps")
        GestureRow(Icons.Default.Apps, "Tap a category", "Open its contents")
        GestureRow(Icons.Default.Edit, "Long-press", "Enter edit mode")
    }
}

@Composable
private fun GestureRow(icon: ImageVector, gesture: String, outcome: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(19.dp))
        Text(gesture, color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(outcome, color = CiyatoSec, fontSize = 12.sp)
    }
}

@Composable
private fun PermissionVisual() {
    OnboardingVisualFrame(
        title = "Empty until you choose access",
        subtitle = "Every feature begins with a clear action instead of invented data.",
    ) {
        PermissionRow(Icons.Default.FolderOpen, "Files", "Choose a folder")
        PermissionRow(Icons.Default.AutoAwesome, "Photos", "Select media")
        PermissionRow(Icons.Default.Home, "Weather", "Allow approximate location")
        PermissionRow(Icons.Default.CalendarToday, "Agenda", "Connect calendar")
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, feature: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
        Text(feature, color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(action, color = CiyatoSec, fontSize = 12.sp)
    }
}

@Composable
private fun SetupHandoffVisual() {
    OnboardingVisualFrame(
        title = "What happens next",
        subtitle = "You can start with the launcher now and set up optional features later.",
    ) {
        SetupStep("1", "Set Ciyato as Home", "Android will ask you to confirm your preferred Home app.")
        SetupStep("2", "Explore a clean first run", "Weather, files, photos, and agenda stay in honest empty states until you choose access.")
        SetupStep("3", "Personalize when ready", "Long-press Home to edit, or open Ciyato Settings for launcher controls.")
    }
}

@Composable
private fun SetupStep(number: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(CiyatoGold.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = CiyatoGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = CiyatoSec, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

// ─── Hero Panel ───────────────────────────────────────────────────────────────
@Composable
private fun HeroPanel(page: OnboardingPage, pageIndex: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        CiyatoBgEl2.copy(alpha = 0.98f),
                        CiyatoBgEl.copy(alpha = 0.95f),
                    )
                )
            )
            .border(1.dp, CiyatoBorder, RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(CiyatoGold.copy(alpha = 0.10f))
                    .border(1.dp, CiyatoGold.copy(alpha = 0.18f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(page.icon, contentDescription = null, tint = CiyatoWhite, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(page.eyebrow, color = CiyatoSec, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("${pageIndex + 1} of ${pages.size}", color = CiyatoMuted, fontSize = 11.sp)
            }
        }

        Text(
            page.title,
            color = CiyatoWhite,
            fontSize = 29.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(page.subtitle, color = CiyatoGoldSoft, fontSize = 15.sp, lineHeight = 21.sp)
        if (page.body.isNotBlank()) {
            Text(page.body, color = CiyatoSec, fontSize = 13.sp, lineHeight = 20.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            page.bullets.forEach { bullet ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Text(bullet, color = CiyatoSec, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

// ─── Mini Phone Preview ───────────────────────────────────────────────────────
// Shown ONLY on the first onboarding slide. Uses honest first-run states:
// Weather says "Allow location or set city", Today says "Connect calendar or add item".
// No fake temperature, no fake meetings, no fake data of any kind.
@Composable
private fun MiniPhonePreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CiyatoBg)
            .border(1.dp, CiyatoStrongBorder, RoundedCornerShape(26.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status bar
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("9:30", color = CiyatoSec, fontSize = 11.sp)
            Text("100%", color = CiyatoSec, fontSize = 11.sp)
        }
        // Header — clean, no magic wand or settings icons
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("Ciyato Home", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("A clearer home screen", color = CiyatoMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Search bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(CiyatoBgEl2)
                .border(1.dp, CiyatoBorder, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Search apps...", color = CiyatoMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Search, contentDescription = null, tint = CiyatoSec, modifier = Modifier.size(18.dp))
            }
        }

        // Honest empty-state widgets: Weather and Today
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PreviewWidget("Weather", "Allow location or set city", Modifier.weight(1f))
            PreviewWidget("Today", "Connect calendar or add item", Modifier.weight(1f))
        }

        // Category preview
        Text("Smart categories", color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(3) { col ->
                        PreviewCategory(index = row * 3 + col, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        PreviewDock()
    }
}

@Composable
private fun PreviewWidget(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF26313A), CiyatoBgEl)))
            .border(1.dp, CiyatoBorder, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = CiyatoWhite, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Text(body, color = CiyatoSec, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun PreviewCategory(index: Int, modifier: Modifier = Modifier) {
    val labels = listOf("Work", "Social", "Finance", "Creative", "Utilities", "Daily")
    Column(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CiyatoBgEl2.copy(alpha = 0.9f))
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(labels[index], color = CiyatoWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("Organized apps", color = CiyatoMuted, fontSize = 9.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { dot ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (dot == 0) CiyatoGold.copy(0.95f) else CiyatoSec.copy(0.35f))
                )
            }
        }
    }
}

@Composable
private fun PreviewDock() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CiyatoBgEl2)
            .border(1.dp, CiyatoBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (index == 0) CiyatoGold else CiyatoWhite.copy(alpha = 0.82f))
            )
        }
    }
}

// ─── Guidance Panel ───────────────────────────────────────────────────────────
@Composable
private fun GuidancePanel(panel: OnboardingPanel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CiyatoBgEl.copy(alpha = 0.92f))
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CiyatoGold.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(panel.icon, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(panel.title, color = CiyatoWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(panel.body, color = CiyatoSec, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

/** Request HOME role via RoleManager (API 29+) or open Default Apps settings. */
fun requestDefaultLauncher(
    context: Context,
    launchIntent: (Intent) -> Unit,
    fallback: () -> Unit,
) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                launchIntent(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        fallback()
    } catch (e: Exception) {
        fallback()
    }
}
