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
        var externalIncludeHeaderKeyword: String = "external",
        var dataDefinitionKeyword: String = "data",
        var contextWhereExternalFilesAreValid: Array<String> = arrayOf(externalIncludeHeaderKeyword, dataDefinitionKeyword, "header")
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as State

            if (this.rootPath != other.rootPath) return false
            if (this.externalIncludeHeaderKeyword != other.externalIncludeHeaderKeyword) return false
            if (!this.contextWhereExternalFilesAreValid.contentEquals(other.contextWhereExternalFilesAreValid)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = this.rootPath?.hashCode() ?: 0
            result = 31 * result + this.externalIncludeHeaderKeyword.hashCode()
            result = 31 * result + this.contextWhereExternalFilesAreValid.contentHashCode()
            return result
        }
    }

    private var state = State()

    override fun getState(): State = this.state
    override fun loadState(state: State) {
        this.state = state
    }

    fun setRootPath(path: String?) {
        this.state.rootPath = path
    }

    fun getRootPath(): String? = this.state.rootPath

    fun getContextWhereExternalFilesAreValid(): Array<String> = this.state.contextWhereExternalFilesAreValid
}