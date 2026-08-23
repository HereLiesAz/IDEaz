package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.ProjectType

/**
 * Maps a [ProjectType] to the GitHub "template repository" new projects are
 * generated from (via the GitHub generate-from-template API). These are the
 * official IDEaz starters; see the `templates/` folder in the IDEaz repo for
 * their source.
 *
 * When a type has an entry here, the create flow generates the user's new repo
 * from it and clones the result. When it doesn't (or generation fails), the
 * flow falls back to the bundled assets template in [TemplateManager].
 */
object TemplateRegistry {

    /** Owner of the official template repositories. */
    const val OWNER = "HereLiesAz"

    /** Template repo name for [type], or `null` if none is registered. */
    fun repoFor(type: ProjectType): String? = when (type) {
        ProjectType.ANDROID -> "ideaz-android"
        ProjectType.WEB -> "ideaz-web"
        ProjectType.PWA -> "ideaz-pwa"
        ProjectType.REACT -> "ideaz-react"
        else -> null
    }
}
