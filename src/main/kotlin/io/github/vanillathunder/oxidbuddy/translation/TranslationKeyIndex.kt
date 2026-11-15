package io.github.vanillathunder.oxidbuddy.translation

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.PhpFile

/**
 * Maps translation key -> translated text for every OXID language file (`lang.php`, `*_lang.php`).
 *
 * The index only stores what is written in the file. Where the file belongs (module, theme, core,
 * language, admin or frontend) is derived from its path at query time, see [LangFiles.classify].
 */
class TranslationKeyIndex : FileBasedIndexExtension<String, String>() {

    override fun getName(): ID<String, String> = NAME

    override fun getVersion(): Int = 1

    override fun dependsOnFileContent(): Boolean = true

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        object : DefaultFileTypeSpecificInputFilter(PhpFileType.INSTANCE) {
            override fun acceptInput(file: VirtualFile): Boolean = LangFiles.isLangFileName(file.name)
        }

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { inputData ->
        val psiFile = inputData.psiFile as? PhpFile ?: return@DataIndexer emptyMap()
        val result = HashMap<String, String>()
        for (element in LangFilePsi.entries(psiFile)) {
            val key = LangFilePsi.keyOf(element) ?: continue
            result[key] = shorten(LangFilePsi.textOf(element))
        }
        result
    }

    private fun shorten(text: String): String {
        val singleLine = text.replace(WHITESPACE, " ").trim()
        return if (singleLine.length > MAX_TEXT_LENGTH) singleLine.take(MAX_TEXT_LENGTH) + "…" else singleLine
    }

    companion object {
        val NAME: ID<String, String> = ID.create("io.github.vanillathunder.oxidbuddy.translationKeys")

        private const val MAX_TEXT_LENGTH = 200
        private val WHITESPACE = Regex("\\s+")
    }
}
