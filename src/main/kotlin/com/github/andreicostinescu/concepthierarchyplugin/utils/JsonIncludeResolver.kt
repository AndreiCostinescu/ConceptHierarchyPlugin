package com.github.andreicostinescu.concepthierarchyplugin.utils

import com.github.andreicostinescu.concepthierarchyplugin.services.ModelService
import com.intellij.json.psi.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/**
 * Resolves externally-included-data-files recursively starting from a root JsonFile, relative to the root-file-location
 *
 * Notes:
 * - Caller should invoke under a ReadAction.
 * - Recursively traverses includes found in included files.
 * - Supports both array and single-string values for the include property.
 * - Optional special handling for "header": resolves to include/<projectName>/<value> relative to project root.
 */
object JsonIncludeResolver {
    data class Include(
        val path: String,                  // The literal path string as written in JSON
        val target: VirtualFile?,          // Resolved target VirtualFile (null if unresolved)
        val literal: JsonStringLiteral     // The PSI literal where the include/header was declared
    )

    /**
     * Recursively collect includes starting at [root].
     *
     * @param root The starting JSON file (typically the selected root)
     * @param externalPropertyKeyword The keyword which lists included files (default: "external")
     * @param dataPropertyKeyword The keyword which contains property definition data (default: "data")
     * @param includeHeader If true, also collect/resolve "header" property as include/<projectName>/<value>
     * @return All includes discovered in DFS order, including those coming from nested files.
     */
    fun findIncludes(
        root: JsonFile,
        externalPropertyKeyword: String = "external",
        dataPropertyKeyword: String = "data",
        includeHeader: Boolean = false,
        withLogging: Boolean = false,
    ): List<Include> {
        val project = root.project
        val psiManager = PsiManager.getInstance(project)
        val results = mutableListOf<Include>()

        val log = com.intellij.openapi.diagnostic.Logger.getInstance(ModelService::class.java)

        // Track visited by file URL to avoid infinite loops
        val visited = LinkedHashSet<String>()
        val stack = ArrayDeque<JsonFile>()

        fun enqueueIfJson(vf: VirtualFile?) {
            if (vf == null) return
            val url = vf.url
            if (!visited.add(url)) return
            val psi = psiManager.findFile(vf) as? JsonFile ?: return
            stack.addLast(psi)
        }

        // Seed traversal with the root file (mark visited so we don't attempt to "include" the root itself)
        visited.add(root.virtualFile.url)
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (withLogging) {
                log.warn("At file ${current.virtualFile.path}")
            }
            val topObj = current.topLevelValue as? JsonObject ?: continue
            if (withLogging) {
                log.warn("Content is...")
                log.warn("${current.topLevelValue?.text}")
            }

            // 1) "external" includes
            val externalData = topObj.findProperty(externalPropertyKeyword);
            if (withLogging && externalData != null) {
                log.warn("Found external data files!")
            }
            externalData?.let { prop ->
                if (withLogging) {
                    log.warn("${prop.value?.text}")
                }
                assert(prop.value is JsonArray)
                (prop.value as JsonArray).valueList.forEach { el ->
                    val lit = el as? JsonStringLiteral ?: return@forEach
                    val target = resolveRelativeToFile(current.virtualFile, lit.value)
                    results.add(Include(lit.value, target, lit))
                    if (withLogging) {
                        log.warn("Found external data file ${lit.value} inside ${current.name}")
                    }
                    enqueueIfJson(target)
                }
            }

            // 2) "data" includes
            for (conceptData in topObj.propertyList) {
                conceptData.value?.let { value ->
                    if (value is JsonObject) {
                        val dataProp = value.findProperty(dataPropertyKeyword)
                        if (dataProp?.value is JsonStringLiteral) {
                            val path = (dataProp.value as JsonStringLiteral)
                            val target = resolveRelativeToFile(current.virtualFile, path.value)
                            results.add(Include(path.value, target, path))
                            if (withLogging) {
                                log.warn("Found external data file ${path.value} at ${conceptData.name}")
                            }
                            enqueueIfJson(target)
                        }
                    }
                }
            }

            // 3) optional "header" handling
            if (includeHeader) {
                topObj.findProperty("header")?.value?.let { headerVal ->
                    val lit = headerVal as? JsonStringLiteral
                    if (lit != null) {
                        val target = resolveHeader(project, lit.value)
                        results.add(Include(lit.value, target, lit))
                        if (withLogging) {
                            log.warn("Should not enter in the header-include code-path!!")
                        }
                        enqueueIfJson(target)
                    }
                }
            }
        }

        return results
    }

    // Resolve path relative to the directory of [contextFile]
    private fun resolveRelativeToFile(contextFile: VirtualFile, relativePath: String): VirtualFile? {
        val baseDir = contextFile.parent ?: return null
        val sanitized = relativePath.trim()
        if (sanitized.isEmpty()) return null
        return VfsUtil.findRelativeFile(sanitized, baseDir)
    }

    // Resolve header as include/<projectName>/<value> from project root
    private fun resolveHeader(project: Project, value: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val baseVf = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null
        val adjusted = "include/${project.name}/${value.trimStart('/')}"
        return VfsUtil.findRelativeFile(adjusted, baseVf)
    }
}