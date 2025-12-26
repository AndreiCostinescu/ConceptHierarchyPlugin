package com.github.andreicostinescu.concepthierarchyplugin.utils

import com.intellij.json.psi.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Reads an "includes" array from the top-level object and resolves each string
 * relative to the root file location.
 */
object JsonIncludeResolver {
    data class Include(val path: String, val target: VirtualFile?, val literal: JsonStringLiteral)

    fun findIncludes(root: JsonFile, includePropertyName: String): List<Include> {
        val topObj = root.topLevelValue as? JsonObject ?: return emptyList()
        val prop = topObj.findProperty(includePropertyName) ?: return emptyList()
        val arr = prop.value as? JsonArray ?: return emptyList()
        val baseDir = root.virtualFile.parent
        return arr.valueList.mapNotNull { v ->
            val lit = v as? JsonStringLiteral ?: return@mapNotNull null
            val path = lit.value
            val vf = VfsUtil.findRelativeFile(path, baseDir)
            Include(path, vf, lit)
        }
    }
}