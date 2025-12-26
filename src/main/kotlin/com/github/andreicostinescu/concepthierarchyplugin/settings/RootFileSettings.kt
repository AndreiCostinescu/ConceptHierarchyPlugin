package com.github.andreicostinescu.concepthierarchyplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.PROJECT)
@State(name = "ConceptHierarchySettings", storages = [Storage("conceptHierarchy.xml")])
class RootFileSettings : PersistentStateComponent<RootFileSettings.State> {
    data class State(
        var rootPath: String? = null,           // Project-relative or absolute path to the root JSON
        var includePropertyName: String = "external"
    )

    private var state = State()

    override fun getState(): State = this.state
    override fun loadState(state: State) {
        this.state = state
    }

    fun setRootPath(path: String?) {
        this.state.rootPath = path
    }

    fun getRootPath(): String? = this.state.rootPath

    fun getIncludePropertyName(): String = this.state.includePropertyName

    /*
    // This should not be set; the 'external' keyword is part of the Concept Hierarchy syntax, not modifiable
    fun setIncludePropertyName(name: String) {
        this.state.includePropertyName = name
    }
    */
}