package com.github.andreicostinescu.concepthierarchyplugin.references

import com.github.andreicostinescu.concepthierarchyplugin.services.ModelService
import com.github.andreicostinescu.concepthierarchyplugin.settings.RootFileSettings
import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext

class JsonIncludeReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement(JsonStringLiteral::class.java)
                .withLanguage(JsonLanguage.INSTANCE),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val project = element.project
                    val contextWhereExternalFilesAreValid =
                        project.service<RootFileSettings>().getContextWhereExternalFilesAreValid()

                    // Safe cast: if the value is not a string literal, not correct data
                    val path = element as? JsonStringLiteral ?: return PsiReference.EMPTY_ARRAY

                    // differentiate between include files in an array (e.g. "external") or simple string values
                    // val property = element.parent?.parent as? JsonProperty ?: return PsiReference.EMPTY_ARRAY  // works only for path-/string-values inside an array
                    val property = path.parentOfType<JsonProperty>() ?: return PsiReference.EMPTY_ARRAY

                    // check if the property name is one of the valid ones to be interpreted as an external context
                    if (!contextWhereExternalFilesAreValid.contains(property.name)) {
                        return PsiReference.EMPTY_ARRAY
                    }
                    val log = com.intellij.openapi.diagnostic.Logger.getInstance(ModelService::class.java)
                    when (property.name) {
                        "header" -> {
                            // "<conceptName>"-JsonProperty -> dict-JsonObject -> "data"-JsonProperty ->
                            //  -> dict-JsonObject -> "header"-JsonProperty
                            val dataDict = property.parent
                            assert(dataDict is JsonObject)
                            val dataKey = dataDict.parent
                            assert(dataKey is JsonProperty)
                            val conceptDict = dataKey.parent
                            assert(conceptDict is JsonObject)
                            val concept = conceptDict.parent
                            assert(concept is JsonProperty)
                            val conceptName = (concept as JsonProperty).name
                            val model = path.project.service<ModelService>()
                            // a Function is also a ValueDomain => test for Function first and then for ValueDomain
                            return if (model.isFunction(conceptName)) {
                                arrayOf(HeaderIncludeReference(path, HeaderIncludeReference.HeaderType.FunctionHeader))
                            } else if (model.isValueDomain(conceptName)) {
                                arrayOf(
                                    HeaderIncludeReference(
                                        path,
                                        HeaderIncludeReference.HeaderType.ValueDomainHeader
                                    )
                                )
                            } else {
                                log.warn("$conceptName doesn't seem to be a concept!")
                                PsiReference.EMPTY_ARRAY
                            }
                        }
                        "external" -> {
                            // Optional: ensure this literal is inside an array value of the includes property
                            val isInsideArray = property.value is JsonArray
                            if (!isInsideArray) {
                                log.warn("External ConceptHierarchy files not bundled in an array!")
                                return PsiReference.EMPTY_ARRAY
                            }
                            return arrayOf(IncludeReference(path))
                        }
                        else -> {
                            return arrayOf(IncludeReference(path))
                        }
                    }
                }
            }
        )
    }
}

class IncludeReference(private val lit: JsonStringLiteral) :
    PsiReferenceBase<JsonStringLiteral>(lit, true) {

    override fun resolve(): PsiElement? {
        val baseDir = this.lit.containingFile.virtualFile.parent
        val target = VfsUtil.findRelativeFile(this.lit.value, baseDir) ?: return null
        return PsiManager.getInstance(this.lit.project).findFile(target)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

class HeaderIncludeReference(private val lit: JsonStringLiteral, private val headerType: HeaderType) :
    PsiReferenceBase<JsonStringLiteral>(lit, true) {

    enum class HeaderType(val dirName: String) {
        FunctionHeader("functions"),
        ValueDomainHeader("valueDomains")
    }

    override fun resolve(): PsiElement? {
        val project = this.lit.project
        val projectBase: VirtualFile? =
            project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
                ?: project.projectFile?.parent  // .ipr-based projects (if applicable)
                ?: project.workspaceFile?.parent  // directory-based projects; optional fallback
                ?: this.lit.containingFile.virtualFile.parent
        val projectName = project.name
        val adjustedPath = join("include/$projectName/${this.headerType.dirName}", this.lit.value) // include/<projectName>/<conceptType>/<value>
        val target = VfsUtil.findRelativeFile(adjustedPath, projectBase) ?: return null
        return PsiManager.getInstance(project).findFile(target)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    private fun join(base: String, child: String): String {
        val b = base.trimEnd('/')
        val c = child.trimStart('/')
        return "$b/$c"
    }
}
