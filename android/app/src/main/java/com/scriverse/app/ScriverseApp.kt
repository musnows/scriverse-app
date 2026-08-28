package com.scriverse.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scriverse.app.feature.ai.AiScreen
import com.scriverse.app.feature.knowledge.KnowledgeScreen
import com.scriverse.app.feature.settings.AccountScreen
import com.scriverse.app.feature.settings.SyncCenterScreen
import com.scriverse.app.feature.shelf.ShelfScreen
import com.scriverse.app.feature.workspaces.WorkspacesScreen
import com.scriverse.app.feature.workspaces.LoginScreen
import com.scriverse.app.feature.writing.WritingScreen

private data class MainDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val mainDestinations = listOf(
    MainDestination("shelf", "书架", Icons.Outlined.AutoStories),
    MainDestination("writing", "写作", Icons.Outlined.EditNote),
    MainDestination("knowledge", "设定", Icons.Outlined.Hub),
    MainDestination("ai", "AI", Icons.Outlined.Forum),
    MainDestination("account", "我的", Icons.Outlined.AccountCircle),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriverseApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 700.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    mainDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = current?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = { navController.openTopLevel(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    AppTopBar(state, navController)
                    AppNavHost(
                        navController = navController,
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            Scaffold(
                topBar = { AppTopBar(state, navController) },
                bottomBar = {
                    NavigationBar {
                        mainDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = current?.hierarchy?.any { it.route == destination.route } == true,
                                onClick = { navController.openTopLevel(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = { Text(destination.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                AppNavHost(
                    navController = navController,
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(state: AppUiState, navController: NavHostController) {
    TopAppBar(
        title = {
            TextButton(onClick = { navController.navigate("workspaces") }) {
                Icon(Icons.Outlined.Workspaces, contentDescription = null)
                Text(state.selectedProfile?.name ?: "选择工作区", modifier = Modifier.padding(start = 8.dp))
            }
        },
        actions = {
            IconButton(onClick = { navController.navigate("sync") }) {
                Icon(Icons.Outlined.Sync, contentDescription = "同步中心")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier,
) {
    NavHost(navController, startDestination = "shelf", modifier = modifier) {
        composable("shelf") {
            ShelfScreen(state = state, onSelectWork = viewModel::selectWork, onContinueWriting = {
                state.selectedChapterId?.let(viewModel::selectChapter)
                navController.navigate("writing")
            })
        }
        composable("writing") {
            WritingScreen(
                state = state,
                onSelectChapter = viewModel::selectChapter,
                onContentChange = viewModel::updateChapter,
            )
        }
        composable("knowledge") { KnowledgeScreen(state) }
        composable("ai") { AiScreen(state) }
        composable("account") { AccountScreen(state, onOpenWorkspaces = { navController.navigate("workspaces") }) }
        composable("workspaces") {
            WorkspacesScreen(
                state = state,
                onSelect = viewModel::selectProfile,
                onCreateLocal = viewModel::createLocalWorkspace,
                onAddRemote = viewModel::addRemoteWorkspace,
                onLogin = { navController.navigate("login/$it") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("login/{profileId}") { entry ->
            val profileId = entry.arguments?.getString("profileId").orEmpty()
            LoginScreen(
                profile = state.profiles.firstOrNull { it.id == profileId },
                state = state,
                onLogin = { username, password, captcha -> viewModel.login(profileId, username, password, captcha) },
                onBack = { navController.popBackStack() },
            )
        }
        composable("sync") { SyncCenterScreen(state, onBack = { navController.popBackStack() }) }
    }
}

private fun NavHostController.openTopLevel(route: String) {
    navigate(route) {
        popUpTo("shelf") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
