package com.github.andreicostinescu.concepthierarchyplugin.services

import com.github.andreicostinescu.concepthierarchyplugin.settings.RootFileSettings
import com.github.andreicostinescu.concepthierarchyplugin.utils.JsonIncludeResolver
import com.intellij.json.psi.JsonArray
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral

@Service(Service.Level.PROJECT)
class ModelService(private val project: Project) {

    data class ConceptHierarchy(
        val rootFilePath: String,  // root path
        val includedFiles: List<String>,  // paths of all INCLUDED files that are part of the hierarchy (excl. root path)
        val conceptsDefinedInFiles: Map<String, Set<String>>,  // concepts that are defined in a file (fileName -> concepts)
        val concepts: Set<String>,  // all concepts in the hierarchy
        val directParents: Map<String, List<String>>,  // direct parents of all concepts (concept -> parents)
        val allParents: Map<String, List<String>>,  // all parents of all concepts in topological sort order (concept -> parents)
        val topologicalSort: List<String>,  // topological sort of all concepts
    ) {
        companion object {
            fun empty(root: String, includedFiles: List<String>) = ConceptHierarchy(
                rootFilePath = root,
                includedFiles = includedFiles,
                conceptsDefinedInFiles = emptyMap(),
                concepts = emptySet(),
                directParents = emptyMap(),
                allParents = emptyMap(),
                topologicalSort = emptyList()
            )

            fun create(root: String, includedFiles: List<String>, directParents: Map<String, List<String>>, conceptsDefinedInFiles: Map<String, Set<String>>): ConceptHierarchy {
                val concepts = HashSet<String>()
                for (conceptsPerFile in conceptsDefinedInFiles) {
                    concepts += conceptsPerFile.value
                }

                // Normalize input types
                val normalizedDirectParents: Map<String, List<String>> =
                    directParents.mapValues { it.value.toList() }

                // compute transitive parents
                val allParents: Map<String, List<String>> = computeAllParents(normalizedDirectParents)

                val topologicalOrder = topologicalOrder(concepts, normalizedDirectParents)

                /*
                val log = com.intellij.openapi.diagnostic.Logger.getInstance(ModelService::class.java)
                log.warn("concepts = ${concepts.joinToString("\n")}\n")
                log.warn("allParents = ${allParents.toString()}\n")
                log.warn("topologicalOrder = ${topologicalOrder.joinToString("\n")}\n")
                */

                return ConceptHierarchy(
                    rootFilePath = root,
                    includedFiles = includedFiles,
                    conceptsDefinedInFiles = conceptsDefinedInFiles.mapValues { it.value.toSet() },
                    directParents = normalizedDirectParents,
                    concepts = concepts,
                    allParents = allParents,
                    topologicalSort = topologicalOrder
                )
            }

            // building from mutable inputs while copying for safety
            // Keeping ConceptHierarchy immutable (val properties, read-only interfaces like List/Map/Set)
            //  is ideal for sharing across threads and aligning with your volatile model reference.
            // If inputs may be mutable, prefer copying in a factory (as below)
            //  to prevent accidental mutation after construction.
            fun of(
                root: String,
                includedFiles: Collection<String>,
                conceptsDefinedInFiles: Map<String, Set<String>>,
                concepts: Collection<String>,
                directParents: Map<String, List<String>>,
                allParents: Map<String, List<String>>,
                topologicalSort: List<String>,
            ) = ConceptHierarchy(
                rootFilePath = root,
                includedFiles = includedFiles.toList(),
                conceptsDefinedInFiles = conceptsDefinedInFiles.mapValues { it.value.toSet() },
                concepts = concepts.toSet(),
                directParents = directParents.mapValues { it.value.toList() },
                allParents = allParents.mapValues { it.value.toList() },
                topologicalSort = topologicalSort.toList(),
            )

            private fun computeAllParents(direct: Map<String, List<String>>): Map<String, List<String>> {
                fun ancestorsOf(start: String): List<String> {
                    val seen = mutableListOf<String>()
                    val stack = ArrayDeque<String>()
                    direct[start]?.let { stack.addAll(it) }
                    while (stack.isNotEmpty()) {
                        val p = stack.removeLast()
                        if (seen.add(p)) {
                            // Continue up the chain
                            direct[p]?.let { stack.addAll(it) }
                        }
                    }
                    return seen
                }
                // Build for all concepts that have direct parents entries
                return direct.keys.associateWith { ancestorsOf(it) }
            }

            private fun topologicalOrder(
                concepts: Set<String>,
                directParents: Map<String, List<String>>
            ): List<String> {
                // Include any nodes that appear only as parents
                val nodes: Set<String> = concepts

                // Build parent -> children adjacency and indegree counts for children
                val children = mutableMapOf<String, MutableSet<String>>()
                val indegree = nodes.associateWith { 0 }.toMutableMap()

                for ((child, parents) in directParents) {
                    for (parent in parents) {
                        children.getOrPut(parent) { mutableSetOf() }.add(child)
                        indegree[child] = (indegree[child] ?: 0) + 1
                    }
                }

                // Start with all roots (indegree == 0). Sort for deterministic order.
                val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys.sorted())
                val result = mutableListOf<String>()

                while (queue.isNotEmpty()) {
                    val n = queue.removeFirst()
                    result += n
                    for (c in children[n].orEmpty()) {
                        indegree[c] = indegree[c]!! - 1
                        if (indegree[c] == 0) {
                            queue.addLast(c)
                        }
                    }
                }

                // Cycle detection: if we couldn't schedule all nodes, there is a cycle.
                if (result.size != nodes.size) {
                    val remaining = nodes - result.toSet()
                    throw IllegalStateException("Cycle detected among concepts: $remaining")
                }

                // If you only want concepts (and not parent-only nodes), filter to your set
                // return result.filter { it in concepts }
                return result
            }
        }

        init {
            require(rootFilePath.isNotBlank()) { "rootFilePath must be non-blank" }
        }
    }

    @Volatile
    private var currentModel: ConceptHierarchy? = null

    fun rebuildModel(changedFiles: Set<String>? = null) {
        val log = com.intellij.openapi.diagnostic.Logger.getInstance(ModelService::class.java)
        if (changedFiles == null) {
            log.warn("Rebuilding Concept Hierarchy...")
        } else {
            log.warn("Should rebuild only parts of the Concept Hierarchy ${changedFiles.joinToString(", ")}")
        }

        val settings = this.project.service<RootFileSettings>()
        val settingsState = settings.state
        val rootPath = settings.getRootPath() ?: return
        val projectBase = this.project.baseDir ?: this.project.projectFile?.parent
        val abs =
            if (projectBase != null) VfsUtil.findRelativeFile(rootPath, projectBase) else LocalFileSystem.getInstance()
                .findFileByPath(rootPath)
        val psiRoot = abs?.let { PsiManager.getInstance(this.project).findFile(it) as? JsonFile } ?: return

        log.warn("Reading Concept Hierarchy files...")

        ReadAction.run<RuntimeException> {
            val includes = JsonIncludeResolver.findIncludes(psiRoot, true, settingsState.externalIncludeHeaderKeyword, settingsState.dataDefinitionKeyword)
            val includedFiles = includes.filterNot { it.targetJson == psiRoot }.map { it.target?.path ?: it.path }

            val conceptsDefinedInFiles = HashMap<String, HashSet<String>>()
            val directParents = HashMap<String, List<String>>()
            for (chFile in includes) {
                if (chFile.targetJson != null && !chFile.isPartOfConcept) {
                    val fileContent = chFile.targetJson.topLevelValue as JsonObject
                    val key = chFile.target?.path ?: chFile.path
                    for (conceptDef in fileContent.propertyList) {
                        if (conceptDef.name == settings.state.externalIncludeHeaderKeyword) {
                            continue
                        }
                        val conceptName = conceptDef.name

                        // getOrPut executes the passed function result if the key doesn't exist
                        conceptsDefinedInFiles.getOrPut(key) { HashSet<String>() }.add(conceptName)

                        if (directParents.containsKey(conceptName)) {
                            log.warn("Ignoring duplicate concept $key, but there shouldn't be any concept duplicates!")
                        } else {
                            // Extract directParents safely
                            val parentsArray =
                                (conceptDef.value as? JsonObject)?.findProperty("directParents")?.value as? JsonArray
                            val parentsList =
                                parentsArray?.valueList?.mapNotNull { (it as? JsonStringLiteral)?.value } ?: emptyList()
                            directParents[conceptName] = parentsList
                        }
                    }
                }
            }

            this.currentModel = ConceptHierarchy.create(rootPath, includedFiles, directParents, conceptsDefinedInFiles)
        }
    }

    fun getModel(): ConceptHierarchy? = this.currentModel

    fun isConcept(conceptToTest: String): Boolean {
        // don't test via currentModel != null && currentModel.concepts.contains()
        // because between the two and-conditions, currentModel may change in a different thread!
        return this.currentModel?.concepts?.contains(conceptToTest) ?: false
    }

    fun isValueDomain(conceptToTest: String): Boolean {
        if (!this.isConcept(conceptToTest)) {
            return false
        }
        val allParentsOfConcept = this.currentModel?.allParents[conceptToTest] ?: emptyList()
        return allParentsOfConcept.contains("ValueDomain") || conceptToTest == "ValueDomain"
    }

    fun isFunction(conceptToTest: String): Boolean {
        if (!this.isConcept(conceptToTest)) {
            return false
        }
        val allParentsOfConcept = this.currentModel?.allParents[conceptToTest] ?: emptyList()
        return allParentsOfConcept.contains("Function") || conceptToTest == "Function"
    }
}