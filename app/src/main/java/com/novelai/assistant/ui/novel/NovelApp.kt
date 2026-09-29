package com.novelai.assistant.ui.novel

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelai.assistant.ui.assistant.AssistantScreen
import com.novelai.assistant.ui.bookshelf.BookshelfScreen
import com.novelai.assistant.ui.components.glassEffect
import com.novelai.assistant.ui.components.glassSource
import com.novelai.assistant.ui.components.rememberGlassState
import com.novelai.assistant.ui.continuation.ContinuationScreen
import com.novelai.assistant.ui.discover.DiscoverScreen
import com.novelai.assistant.ui.reader.ReaderScreen
import com.novelai.assistant.ui.settings.ProviderEditScreen
import com.novelai.assistant.ui.settings.SettingsScreen
import com.novelai.assistant.ui.tree.BranchTreeScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val BOOKSHELF = "bookshelf"
    const val DISCOVER = "discover"
    const val ASSISTANT = "assistant?bookId={bookId}&quote={quote}"
    const val SETTINGS = "settings"
    const val READER = "reader/{bookId}?chapter={chapter}"
    const val GENERATE = "generate/{bookId}/{chapterId}?mode={mode}"
    const val TREE = "tree/{bookId}"
    const val PROVIDER_EDIT = "provider_edit/{providerId}"

    fun assistant(bookId: String?, quote: String?): String {
        val q = android.net.Uri.encode(quote ?: "")
        return "assistant?bookId=${bookId ?: ""}&quote=$q"
    }
    fun reader(bookId: String) = "reader/$bookId"
    fun readerAt(bookId: String, chapterIndex: Int) = "reader/$bookId?chapter=$chapterIndex"
    fun generate(bookId: String, chapterId: String, mode: String) =
        "generate/$bookId/$chapterId?mode=$mode"
    fun tree(bookId: String) = "tree/$bookId"
    fun providerEdit(providerId: String) = "provider_edit/$providerId"
}

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    BOOKSHELF("bookshelf", "书架", Icons.AutoMirrored.Rounded.LibraryBooks),
    DISCOVER("discover", "发现", Icons.Rounded.Explore),
    ASSISTANT("assistant", "AI助手", Icons.Rounded.AutoAwesome),
    SETTINGS("settings", "设置", Icons.Rounded.Settings)
}

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

// assistant 带查询参数，destination.route 是完整模式，需前缀匹配
private fun isTopLevelRoute(route: String?): Boolean =
    route != null && (route in topLevelRoutes || route.startsWith("assistant?"))

@Composable
fun NovelApp() {
    val appViewModel: AppViewModel = hiltViewModel()
    val onboardingDone by appViewModel.onboardingDone.collectAsStateWithLifecycle()

    // 引导标记加载中：显示底色占位，避免闪烁
    if (onboardingDone == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val navController = rememberNavController()
    val hazeState = rememberGlassState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = isTopLevelRoute(currentRoute)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                GlassBottomBar(
                    hazeState = hazeState,
                    currentRoute = currentRoute,
                    onSelect = { dest -> navigateTopLevel(navController, dest) }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingDone == true) Routes.BOOKSHELF else Routes.ONBOARDING,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp, bottom = padding.calculateBottomPadding())
                .glassSource(hazeState),
            enterTransition = {
                slideInHorizontally(tween(340)) { it / 3 } + fadeIn(tween(300))
            },
            exitTransition = {
                slideOutHorizontally(tween(340)) { -it / 5 } + fadeOut(tween(280))
            },
            popEnterTransition = {
                slideInHorizontally(tween(340)) { -it / 5 } + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(tween(340)) { it / 3 } + fadeOut(tween(280))
            }
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(Routes.BOOKSHELF) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.BOOKSHELF) {
                BookshelfScreen(
                    onOpenBook = { navController.navigate(Routes.reader(it)) },
                    onOpenTree = { navController.navigate(Routes.tree(it)) }
                )
            }
            composable(Routes.DISCOVER) {
                DiscoverScreen()
            }
            composable(
                Routes.ASSISTANT,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("quote") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                AssistantScreen(
                    bookIdArg = entry.arguments?.getString("bookId").orEmpty(),
                    quoteArg = entry.arguments?.getString("quote").orEmpty()
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onEditProvider = { navController.navigate(Routes.providerEdit(it)) }
                )
            }
            composable(
                Routes.READER,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("chapter") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { entry ->
                ReaderScreen(
                    bookId = entry.arguments?.getString("bookId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onGenerate = { bookId, chapterId, mode ->
                        navController.navigate(Routes.generate(bookId, chapterId, mode))
                    },
                    onOpenTree = { navController.navigate(Routes.tree(it)) },
                    onAsk = { bookId, quote -> navController.navigate(Routes.assistant(bookId, quote)) }
                )
            }
            composable(
                Routes.GENERATE,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "CONTINUATION" }
                )
            ) { entry ->
                ContinuationScreen(
                    bookId = entry.arguments?.getString("bookId").orEmpty(),
                    parentChapterId = entry.arguments?.getString("chapterId").orEmpty(),
                    mode = entry.arguments?.getString("mode") ?: "CONTINUATION",
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                Routes.TREE,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { entry ->
                BranchTreeScreen(
                    bookId = entry.arguments?.getString("bookId").orEmpty(),
                    onOpenChapter = { chapterIndex ->
                        val bid = entry.arguments?.getString("bookId").orEmpty()
                        navController.navigate(Routes.readerAt(bid, chapterIndex))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Routes.PROVIDER_EDIT,
                arguments = listOf(navArgument("providerId") { type = NavType.StringType })
            ) { entry ->
                ProviderEditScreen(
                    providerId = entry.arguments?.getString("providerId") ?: "new",
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun navigateTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun GlassBottomBar(
    hazeState: dev.chrisbanes.haze.HazeState,
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .glassEffect(
                state = hazeState,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(bottom = navBarPadding.calculateBottomPadding() * 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentRoute?.startsWith(dest.route) == true
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(dest.route)
                    }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = dest.icon,
                    contentDescription = dest.label,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = dest.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}
