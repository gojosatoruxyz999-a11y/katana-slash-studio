package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.ProjectType
import com.example.ui.animation.AnimationStudioScreen
import com.example.ui.animation.AnimationViewModel
import com.example.ui.components.KatanaIntroOverlay
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.drawing.DrawingStudioScreen
import com.example.ui.drawing.DrawingViewModel
import com.example.ui.export.ExportScreen
import com.example.ui.export.ExportViewModel
import com.example.ui.navigation.RoninRoutes
import com.example.ui.projects.ProjectManagerScreen
import com.example.ui.projects.ProjectsViewModel
import com.example.ui.sound.SoundStudioScreen
import com.example.ui.sound.SoundViewModel
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoninStudioTheme
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.video.VideoEditorScreen
import com.example.ui.video.VideoEditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        val container = RoninAppContainer(applicationContext)
        setContent {
            RoninStudioTheme {
                RoninStudioRoot(container = container)
            }
        }
    }
}

@Composable
private fun RoninStudioRoot(container: RoninAppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var showIntroOverlay by rememberSaveable { mutableStateOf(true) }
    var hasOpenedInitialCanvas by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasOpenedInitialCanvas) {
            hasOpenedInitialCanvas = true
            navController.navigate(RoninRoutes.animationRoute("starter-katana-slash")) {
                launchSingleTop = true
            }
        }
    }

    val showHubNav = currentRoute == RoninRoutes.ROUTE_DASHBOARD ||
        currentRoute == RoninRoutes.ROUTE_PROJECTS ||
        currentRoute?.startsWith(RoninRoutes.ROUTE_EXPORT) == true

    val hubDestinations = remember {
        listOf(
            Triple(RoninRoutes.ROUTE_DASHBOARD, "Studio", Pair(Icons.Rounded.Home, Icons.Outlined.Home)),
            Triple(RoninRoutes.ROUTE_PROJECTS, "Projects", Pair(Icons.Rounded.FolderOpen, Icons.Outlined.FolderOpen)),
            Triple(RoninRoutes.ROUTE_EXPORT, "Export", Pair(Icons.Rounded.Outbox, Icons.Outlined.Outbox))
        )
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp && showHubNav

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showHubNav && !useNavRail) {
                    NavigationBar(
                        containerColor = StudioSurface,
                        contentColor = TextPrimary
                    ) {
                        hubDestinations.forEach { (route, label, icons) ->
                            val selected = currentRoute == route ||
                                (route == RoninRoutes.ROUTE_EXPORT && currentRoute?.startsWith(RoninRoutes.ROUTE_EXPORT) == true)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonPurpleBright,
                                    selectedTextColor = NeonPurpleBright,
                                    indicatorColor = NeonPurple.copy(alpha = 0.22f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${label.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = StudioSurface,
                        contentColor = TextPrimary,
                        modifier = Modifier.statusBarsPadding()
                    ) {
                        hubDestinations.forEach { (route, label, icons) ->
                            val selected = currentRoute == route ||
                                (route == RoninRoutes.ROUTE_EXPORT && currentRoute?.startsWith(RoninRoutes.ROUTE_EXPORT) == true)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NeonPurpleBright,
                                    selectedTextColor = NeonPurpleBright,
                                    indicatorColor = StudioSurfaceElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = RoninRoutes.ROUTE_DASHBOARD,
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(240)
                        ) + fadeIn(animationSpec = tween(220))
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(180))
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(200))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(240)
                        ) + fadeOut(animationSpec = tween(180))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    composable(RoninRoutes.ROUTE_DASHBOARD) {
                        val vm: DashboardViewModel = viewModel(
                            factory = DashboardViewModel.Factory(container.repository)
                        )
                        DashboardScreen(
                            viewModel = vm,
                            onOpenAnimation = { id -> navController.navigate(RoninRoutes.animationRoute(id)) },
                            onOpenDrawing = { id -> navController.navigate(RoninRoutes.drawingRoute(id)) },
                            onOpenVideo = { id -> navController.navigate(RoninRoutes.videoRoute(id)) },
                            onOpenSound = { id -> navController.navigate(RoninRoutes.soundRoute(id)) },
                            onOpenProjects = { navigateTopLevel(RoninRoutes.ROUTE_PROJECTS) },
                            onOpenExport = { id -> navController.navigate(RoninRoutes.exportRoute(id)) },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }

                    composable(
                        route = "${RoninRoutes.ROUTE_ANIMATION}?${RoninRoutes.ARG_PROJECT_ID}={${RoninRoutes.ARG_PROJECT_ID}}",
                        arguments = listOf(navArgument(RoninRoutes.ARG_PROJECT_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { entry ->
                        val projectId = entry.arguments?.getString(RoninRoutes.ARG_PROJECT_ID)
                        val vm: AnimationViewModel = viewModel(
                            factory = AnimationViewModel.Factory(container.repository, projectId)
                        )
                        AnimationStudioScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onExportProject = { id -> navController.navigate(RoninRoutes.exportRoute(id)) }
                        )
                    }

                    composable(
                        route = "${RoninRoutes.ROUTE_DRAWING}?${RoninRoutes.ARG_PROJECT_ID}={${RoninRoutes.ARG_PROJECT_ID}}",
                        arguments = listOf(navArgument(RoninRoutes.ARG_PROJECT_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { entry ->
                        val projectId = entry.arguments?.getString(RoninRoutes.ARG_PROJECT_ID)
                        val vm: DrawingViewModel = viewModel(
                            factory = DrawingViewModel.Factory(container.repository, projectId)
                        )
                        DrawingStudioScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "${RoninRoutes.ROUTE_VIDEO}?${RoninRoutes.ARG_PROJECT_ID}={${RoninRoutes.ARG_PROJECT_ID}}",
                        arguments = listOf(navArgument(RoninRoutes.ARG_PROJECT_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { entry ->
                        val projectId = entry.arguments?.getString(RoninRoutes.ARG_PROJECT_ID)
                        val ctx = LocalContext.current
                        val vm: VideoEditorViewModel = viewModel(
                            factory = VideoEditorViewModel.Factory(ctx, container.repository, projectId)
                        )
                        VideoEditorScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "${RoninRoutes.ROUTE_SOUND}?${RoninRoutes.ARG_PROJECT_ID}={${RoninRoutes.ARG_PROJECT_ID}}",
                        arguments = listOf(navArgument(RoninRoutes.ARG_PROJECT_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { entry ->
                        val projectId = entry.arguments?.getString(RoninRoutes.ARG_PROJECT_ID)
                        val vm: SoundViewModel = viewModel(
                            factory = SoundViewModel.Factory(container.repository, container.soundEngine, projectId)
                        )
                        SoundStudioScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(RoninRoutes.ROUTE_PROJECTS) {
                        val vm: ProjectsViewModel = viewModel(
                            factory = ProjectsViewModel.Factory(
                                repository = container.repository,
                                cloudBackupRepository = container.cloudBackupRepository
                            )
                        )
                        ProjectManagerScreen(
                            viewModel = vm,
                            onBack = { navigateTopLevel(RoninRoutes.ROUTE_DASHBOARD) },
                            onOpenProject = { project ->
                                val route = when (project.type) {
                                    ProjectType.ANIMATION -> RoninRoutes.animationRoute(project.id)
                                    ProjectType.DRAWING -> RoninRoutes.drawingRoute(project.id)
                                    ProjectType.VIDEO -> RoninRoutes.videoRoute(project.id)
                                    ProjectType.SOUND -> RoninRoutes.soundRoute(project.id)
                                }
                                navController.navigate(route)
                            },
                            onExportProject = { project ->
                                navController.navigate(RoninRoutes.exportRoute(project.id))
                            }
                        )
                    }

                    composable(
                        route = "${RoninRoutes.ROUTE_EXPORT}?${RoninRoutes.ARG_PROJECT_ID}={${RoninRoutes.ARG_PROJECT_ID}}",
                        arguments = listOf(navArgument(RoninRoutes.ARG_PROJECT_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { entry ->
                        val projectId = entry.arguments?.getString(RoninRoutes.ARG_PROJECT_ID)
                        val vm: ExportViewModel = viewModel(
                            factory = ExportViewModel.Factory(container.repository, projectId)
                        )
                        ExportScreen(
                            viewModel = vm,
                            onBack = {
                                if (!navController.popBackStack()) {
                                    navigateTopLevel(RoninRoutes.ROUTE_DASHBOARD)
                                }
                            }
                        )
                    }
                }
            }
        }
        }

        if (showIntroOverlay) {
            KatanaIntroOverlay(
                onFinished = { showIntroOverlay = false }
            )
        }
    }
}
