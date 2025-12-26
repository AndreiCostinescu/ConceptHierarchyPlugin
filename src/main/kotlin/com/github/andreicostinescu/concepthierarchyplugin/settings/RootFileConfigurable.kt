package com.github.andreicostinescu.concepthierarchyplugin.settings

import com.github.andreicostinescu.concepthierarchyplugin.services.ModelService
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.*
import java.awt.GridBagLayout
import java.awt.GridBagConstraints

class RootFileConfigurable(private val project: Project) : Configurable {
    private val rootField = TextFieldWithBrowseButton()

    override fun getDisplayName(): String = "Concept Hierarchy"

    override fun createComponent(): JComponent {
        // Limit Browse… to JSON files only
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
            .withTitle("Select Root JSON")
            .withDescription("Choose the root JSON file (prefer project-relative paths).")
        this.rootField.addBrowseFolderListener(project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT)

        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; gridx = 0; gridy = 0 }
        panel.add(JLabel("Root JSON file path (relative to project):"), c)
        c.gridy = 1
        panel.add(this.rootField, c)
        return panel
    }

    override fun isModified(): Boolean {
        val s = project.getService(RootFileSettings::class.java).state
        return this.rootField.text != (s.rootPath ?: "")
    }

    override fun apply() {
        val settings = project.service<RootFileSettings>()
        val text = this.rootField.text.trim()

        // // Don't allow clearing the root path
        // settings.setRootPath(this.rootField.text.takeIf { it.isNotBlank() })  // don't allow reset

        // Allow clearing the root path
        if (text.isEmpty()) {
            settings.setRootPath(null)
            // Rebuild (will no-op if no root)
            project.service<ModelService>().rebuildModel();
            return
        }

        // Resolve the file: prefer project-relative, otherwise treat as absolute
        val projectBase: VirtualFile? = project.baseDir ?: project.projectFile?.parent
        val vf: VirtualFile? =
            if (projectBase != null) {
                VfsUtil.findRelativeFile(text, projectBase)
                    ?: LocalFileSystem.getInstance().findFileByPath(text)
                    ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(text)
            } else {
                LocalFileSystem.getInstance().findFileByPath(text)
                    ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(text)
            }

        if (vf == null) {
            val baseMsg = projectBase?.path?.let { " relative to project root: $it" } ?: ""
            throw ConfigurationException("The selected JSON file was not found: '$text'$baseMsg")
        }

        if (!vf.extension.equals("json", ignoreCase = true)) {
            throw ConfigurationException("The selected file is not a JSON file: '${vf.name}'. Please choose a .json file.")
        }

        // Persist path as project-relative when possible
        val relative = if (projectBase != null) VfsUtilCore.getRelativePath(vf, projectBase, '/') else null
        val newRootPath = relative ?: vf.path
        settings.setRootPath(newRootPath)
        this.rootField.text = newRootPath  // update the UI as well to reflect this updated relative path

        // Rebuild the model after successful validation/save
        project.service<ModelService>().rebuildModel()
    }

    override fun reset() {
        val s = project.getService(RootFileSettings::class.java).state
        this.rootField.text = s.rootPath ?: ""
    }

    override fun disposeUIResources() {}
}