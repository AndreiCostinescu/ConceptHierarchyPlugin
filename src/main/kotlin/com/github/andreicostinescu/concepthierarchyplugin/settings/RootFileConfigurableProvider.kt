package com.github.andreicostinescu.concepthierarchyplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project

class RootFileConfigurableProvider(private val project: Project) : ConfigurableProvider() {
    override fun createConfigurable(): Configurable = RootFileConfigurable(project)
    override fun canCreateConfigurable(): Boolean = true
}