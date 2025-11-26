package com.hereliesaz.ideaz.utils

import org.cosmic.ide.dependency.resolver.api.Artifact
import java.io.File

object ArtifactUtils {
    fun getLocalPath(localRepoDir: File, artifact: Artifact): File {
        val classifier = if (artifact.classifier != null) "-${artifact.classifier}" else ""
        return File(localRepoDir, "${artifact.groupId.replace('.', '/')}/${artifact.artifactId}/${artifact.version}/${artifact.artifactId}-${artifact.version}${classifier}.${artifact.extension}")
    }
}
