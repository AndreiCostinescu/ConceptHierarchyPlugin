package com.github.andreicostinescu.concepthierarchyplugin.services

import com.github.andreicostinescu.concepthierarchyplugin.settings.RootFileSettings
import com.github.andreicostinescu.concepthierarchyplugin.utils.JsonIncludeResolver
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.json.psi.JsonFile

@Service(Service.Level.PROJECT)
class ModelService(private val project: Project) {

    data class Model(val rootFilePath: String, val includedFiles: List<String>)

    @Volatile
    private var currentModel: Model? = null

    fun rebuildModel() {
        val settings = project.getService(RootFileSettings::class.java)
        val rootPath = settings.getRootPath() ?: return
        val projectBase = project.baseDir ?: project.projectFile?.parent
        val abs =
            if (projectBase != null) VfsUtil.findRelativeFile(rootPath, projectBase) else LocalFileSystem.getInstance()
                .findFileByPath(rootPath)
        val psiRoot = abs?.let { PsiManager.getInstance(project).findFile(it) as? JsonFile } ?: return

        ReadAction.run<RuntimeException> {
            val includes = JsonIncludeResolver.findIncludes(psiRoot, settings.getIncludePropertyName())
            this.currentModel = Model(rootPath, includes.map { it.target?.path ?: it.path })
        }
    }

    fun getModel(): Model? = this.currentModel
}