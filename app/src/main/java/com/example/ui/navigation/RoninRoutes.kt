package com.example.ui.navigation

object RoninRoutes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_ANIMATION = "animation"
    const val ROUTE_DRAWING = "drawing"
    const val ROUTE_VIDEO = "video"
    const val ROUTE_SOUND = "sound"
    const val ROUTE_PROJECTS = "projects"
    const val ROUTE_EXPORT = "export"

    const val ARG_PROJECT_ID = "projectId"

    fun animationRoute(projectId: String? = null): String =
        if (projectId != null) "$ROUTE_ANIMATION?$ARG_PROJECT_ID=$projectId" else ROUTE_ANIMATION

    fun drawingRoute(projectId: String? = null): String =
        if (projectId != null) "$ROUTE_DRAWING?$ARG_PROJECT_ID=$projectId" else ROUTE_DRAWING

    fun videoRoute(projectId: String? = null): String =
        if (projectId != null) "$ROUTE_VIDEO?$ARG_PROJECT_ID=$projectId" else ROUTE_VIDEO

    fun soundRoute(projectId: String? = null): String =
        if (projectId != null) "$ROUTE_SOUND?$ARG_PROJECT_ID=$projectId" else ROUTE_SOUND

    fun exportRoute(projectId: String? = null): String =
        if (projectId != null) "$ROUTE_EXPORT?$ARG_PROJECT_ID=$projectId" else ROUTE_EXPORT
}
