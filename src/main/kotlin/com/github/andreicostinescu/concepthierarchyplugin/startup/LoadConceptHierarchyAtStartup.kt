package com.github.andreicostinescu.concepthierarchyplugin.startup

import com.github.andreicostinescu.concepthierarchyplugin.listeners.JsonChangeListener
import com.github.andreicostinescu.concepthierarchyplugin.services.ModelService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager

class LoadConceptHierarchyAtStartup : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        // Subscribe to VFS changes (which trigger a reload of the ConceptHierarchy)
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, JsonChangeListener(project))

        // Wait for indexing to finish, then rebuild
        DumbService.getInstance(project).runWhenSmart {
            project.service<ModelService>().rebuildModel()
        }
    }
}