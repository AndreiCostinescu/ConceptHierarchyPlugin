package com.github.andreicostinescu.concepthierarchyplugin.actions

import com.github.andreicostinescu.concepthierarchyplugin.settings.RootFileSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class SetJsonRootAction : DumbAwareAction("Set as Concept Hierarchy root") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = selectedFile(e)
        e.presentation.isEnabledAndVisible = isJson(file) == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = selectedFile(e) ?: return
        val project = e.project ?: return
        val projectBase = project.baseDir ?: project.projectFile?.parent
        val rel = if (projectBase != null) VfsUtilCore.getRelativePath(file, projectBase, '/')
        else file.path
        project.service<RootFileSettings>().setRootPath(rel ?: file.path)
        Messages.showInfoMessage(project, "Set root JSON:\n${rel ?: file.path}", "Concept Hierarchy")
    }

    private fun selectedFile(e: AnActionEvent): VirtualFile? {
        // Editor popup
        e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { return it }
        // Project view popup (single)
        e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.firstOrNull()?.let { return it }
        return null
    }

    private fun isJson(file: VirtualFile?): Boolean = file != null && file.extension.equals("json", ignoreCase = true)
}
