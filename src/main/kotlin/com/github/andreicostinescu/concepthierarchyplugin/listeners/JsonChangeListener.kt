package com.github.andreicostinescu.concepthierarchyplugin.listeners

import com.github.andreicostinescu.concepthierarchyplugin.services.ModelService
import com.github.andreicostinescu.concepthierarchyplugin.settings.RootFileSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class JsonChangeListener(private val project: Project) : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        val settings = project.service<RootFileSettings>()
        val model = project.service<ModelService>().getModel()
        val projectBase = project.baseDir ?: project.projectFile?.parent

        // Resolve root absolute path, if set
        val rootAbs: String? = settings.getRootPath()?.let { rel ->
            val abs = projectBase?.findFileByRelativePath(rel)
            abs?.path ?: rel
        }

        // Paths we care about: root + currently included files
        val interestingPaths = HashSet<String>()
        rootAbs?.let { interestingPaths.add(it) }
        model?.includedFiles?.forEach { interestingPaths.add(it) }

        // If any changed file matches, rebuild
        val shouldRebuild = events.any { ev ->
            val f: VirtualFile? = ev.file
            f != null && f.extension.equals("json", ignoreCase = true) && interestingPaths.contains(f.path)
        }

        if (shouldRebuild) {
            DumbService.getInstance(project).runWhenSmart {
                project.service<ModelService>().rebuildModel()
            }
        }
    }
}