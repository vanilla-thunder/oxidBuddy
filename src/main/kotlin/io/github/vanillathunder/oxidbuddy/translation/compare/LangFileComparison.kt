package io.github.vanillathunder.oxidbuddy.translation.compare

import com.intellij.psi.PsiFile
import io.github.vanillathunder.oxidbuddy.translation.LangFilePsi

enum class RowState {
    /** The key is missing in the right file. */
    LEFT_ONLY,

    /** The key is missing in the left file. */
    RIGHT_ONLY,

    /** The key exists in both files, but at least one text is empty. */
    UNTRANSLATED,

    /** The key exists in both files with a text. */
    BOTH,
}

/** One translation key with its text in the left and right file; null means the key is missing there. */
data class CompareRow(val key: String, val left: String?, val right: String?) {
    val state: RowState
        get() = when {
            right == null -> RowState.LEFT_ONLY
            left == null -> RowState.RIGHT_ONLY
            left.isEmpty() || right.isEmpty() -> RowState.UNTRANSLATED
            else -> RowState.BOTH
        }
}

object LangFileComparison {

    /** Key -> text in order of first definition. A key defined twice keeps its last text, as in PHP. */
    fun texts(file: PsiFile): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        for (element in LangFilePsi.entries(file)) {
            val key = LangFilePsi.keyOf(element) ?: continue
            result[key] = LangFilePsi.textOf(element)
        }
        return result
    }

    /**
     * Pairs the two files key by key. Rows follow the order of the left file; keys that only exist
     * in the right file follow the key they come after there.
     */
    fun compare(left: PsiFile, right: PsiFile): List<CompareRow> {
        val leftTexts = texts(left)
        val rightTexts = texts(right)

        // Right-only keys, grouped by the closest preceding key that also exists on the left.
        val leading = ArrayList<String>()
        val following = HashMap<String, MutableList<String>>()
        var anchor: String? = null
        for (key in rightTexts.keys) {
            when {
                key in leftTexts -> anchor = key
                anchor == null -> leading += key
                else -> following.getOrPut(anchor) { ArrayList() } += key
            }
        }

        val rows = ArrayList<CompareRow>(leftTexts.size + rightTexts.size)
        leading.mapTo(rows) { CompareRow(it, null, rightTexts[it]) }
        for ((key, text) in leftTexts) {
            rows += CompareRow(key, text, rightTexts[key])
            following[key]?.mapTo(rows) { CompareRow(it, null, rightTexts[it]) }
        }
        return rows
    }
}
