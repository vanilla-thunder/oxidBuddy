package io.github.vanillathunder.oxidbuddy.translation

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.CommonProcessors
import com.intellij.util.indexing.FileBasedIndex

/** A translation key together with the definition that wins for display. */
data class TranslationEntry(
    val key: String,
    val text: String,
    val source: LangSource,
)

object TranslationKeys {

    /**
     * Languages whose text is preferred when a key is defined in several languages, best first.
     * Languages not listed here rank below all listed ones.
     */
    val PREFERRED_LANGUAGES: List<String> = listOf("de", "en")

    /**
     * Ranks the definitions of one key, best first: module before theme before core, then
     * preferred languages, then the working copy before its `vendor` original.
     */
    private val BEST_FIRST: Comparator<TranslationEntry> =
        compareByDescending<TranslationEntry> { it.source.origin }
            .thenBy { languageRank(it.source.language) }
            .thenBy { it.source.inVendor }
            .thenBy { it.source.file.path }

    /** All known keys per scope, each with its best definition. */
    fun all(project: Project): Map<LangScope, Map<String, TranslationEntry>> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(
                collect(project),
                ModificationTracker {
                    FileBasedIndex.getInstance().getIndexModificationStamp(TranslationKeyIndex.NAME, project)
                },
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
            )
        }

    private fun languageRank(language: String): Int {
        val index = PREFERRED_LANGUAGES.indexOf(language.lowercase())
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun collect(project: Project): Map<LangScope, Map<String, TranslationEntry>> {
        val index = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)

        val keys = HashSet<String>()
        index.processAllKeys(TranslationKeyIndex.NAME, CommonProcessors.CollectProcessor(keys), searchScope, null)

        val sources = HashMap<VirtualFile, LangSource?>()
        val result = LangScope.entries.associateWith { HashMap<String, TranslationEntry>() }
        for (key in keys) {
            index.processValues(TranslationKeyIndex.NAME, key, null, { file, text ->
                val source = sources.getOrPut(file) { LangFiles.classify(file) }
                if (source != null) {
                    val candidate = TranslationEntry(key, text, source)
                    result.getValue(source.scope).merge(key, candidate) { current, new ->
                        if (BEST_FIRST.compare(new, current) < 0) new else current
                    }
                }
                true
            }, searchScope)
        }
        return result
    }
}
