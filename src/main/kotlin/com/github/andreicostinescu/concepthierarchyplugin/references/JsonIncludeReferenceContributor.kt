package com.github.andreicostinescu.concepthierarchyplugin.references

import com.github.andreicostinescu.concepthierarchyplugin.settings.RootFileSettings
import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.string
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil

class JsonIncludeReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement(JsonStringLiteral::class.java)
                .withLanguage(JsonLanguage.INSTANCE)
                .and(
                    psiElement(JsonStringLiteral::class.java)
                        .withSuperParent(2, psiElement(JsonProperty::class.java).withName(string()))
                ),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val project = element.project
                    val includePropName = project.getService(RootFileSettings::class.java).getIncludePropertyName()
                    val property = element.parent?.parent as? JsonProperty ?: return PsiReference.EMPTY_ARRAY
                    if (property.name != includePropName) return PsiReference.EMPTY_ARRAY
                    return arrayOf(IncludeReference(element as JsonStringLiteral))
                }
            }
        )
    }
}

class IncludeReference(private val lit: JsonStringLiteral) :
    PsiReferenceBase<JsonStringLiteral>(lit, true) {

    override fun resolve(): PsiElement? {
        val baseDir = lit.containingFile.virtualFile.parent
        val target = VfsUtil.findRelativeFile(lit.value, baseDir) ?: return null
        return PsiManager.getInstance(lit.project).findFile(target)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
