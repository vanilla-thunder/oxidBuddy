package io.github.vanillathunder.oxidbuddy.translation.compare

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import io.github.vanillathunder.oxidbuddy.translation.LangFilePsi

/**
 * Adds and removes translation keys in a language file. Elements are located through the PHP PSI,
 * the text is changed through the document so that the surrounding formatting stays untouched.
 *
 * All functions must be called inside a write command.
 */
object LangFileEditor {
    private const val DEFAULT_INDENT = "    "
    private val UNESCAPED_QUOTE = Regex("(?<!\\\\)'")

    /**
     * Adds [key] with an empty text to [target], placed after the closest preceding key of [source]
     * that [target] also has, so that both files keep the same order. Without such a neighbour the
     * key goes to the end of the array. Does nothing when [target] already has the key.
     */
    fun copyKey(source: PsiFile, target: PsiFile, key: String) {
        val targetKeys = LangFileComparison.texts(target).keys
        if (key in targetKeys) return
        val sourceKeys = LangFileComparison.texts(source).keys.toList()
        val anchor = sourceKeys.take(sourceKeys.indexOf(key).coerceAtLeast(0)).lastOrNull { it in targetKeys }
        addKey(target, key, anchor)
    }

    /** Adds `'key' => '',` after the element defining [afterKey], or at the end of the array. */
    fun addKey(file: PsiFile, key: String, afterKey: String?) {
        val document = documentOf(file) ?: return
        val entries = LangFilePsi.entries(file)
        val anchor = afterKey?.let { wanted -> entries.lastOrNull { LangFilePsi.keyOf(it) == wanted } }
            ?: entries.lastOrNull()

        if (anchor != null) {
            insertAfter(document, anchor, key)
        } else {
            insertIntoEmptyArray(document, file, key)
        }
        PsiDocumentManager.getInstance(file.project).commitDocument(document)
    }

    /** Removes every definition of [key], together with its line when nothing else is on it. */
    fun removeKey(file: PsiFile, key: String) {
        val document = documentOf(file) ?: return
        // Last to first, so that the offsets of the elements still to be removed stay valid.
        for (element in LangFilePsi.entries(file).filter { LangFilePsi.keyOf(it) == key }.asReversed()) {
            val text = document.charsSequence
            val start = element.textRange.startOffset
            var end = element.textRange.endOffset
            val comma = skipBlanks(text, end)
            if (comma < text.length && text[comma] == ',') end = comma + 1

            val lineStart = document.getLineStartOffset(document.getLineNumber(start))
            val endLine = document.getLineNumber(end)
            val lineEnd = document.getLineEndOffset(endLine)
            val aloneOnLine = text.subSequence(lineStart, start).isBlank() && text.subSequence(end, lineEnd).isBlank()
            if (aloneOnLine) {
                val nextLineStart = if (endLine + 1 < document.lineCount) document.getLineStartOffset(endLine + 1) else lineEnd
                document.deleteString(lineStart, nextLineStart)
            } else {
                document.deleteString(start, skipBlanks(text, end))
            }
        }
        PsiDocumentManager.getInstance(file.project).commitDocument(document)
    }

    private fun insertAfter(document: Document, anchor: ArrayHashElement, key: String) {
        val text = document.charsSequence
        val anchorStart = anchor.textRange.startOffset
        var anchorEnd = anchor.textRange.endOffset

        // The anchor needs a trailing comma before another element can follow it.
        val afterAnchor = skipWhitespace(text, anchorEnd)
        if (afterAnchor < text.length && text[afterAnchor] == ',') {
            anchorEnd = afterAnchor + 1
        } else {
            document.insertString(anchorEnd, ",")
            anchorEnd += 1
        }

        val anchorLineStart = document.getLineStartOffset(document.getLineNumber(anchorStart))
        val indent = text.subSequence(anchorLineStart, anchorStart).toString().takeIf { it.isBlank() } ?: DEFAULT_INDENT

        // Line up the new arrow with the anchor's, as language files are usually formatted.
        val arrow = text.indexOf("=>", anchor.key?.textRange?.endOffset ?: anchorStart)
        val arrowColumn = if (arrow in 0 until anchorEnd) arrow - anchorLineStart else 0

        // After the rest of the anchor's line, so that a trailing comment stays with the anchor.
        val insertAt = document.getLineEndOffset(document.getLineNumber(anchorEnd - 1))
        document.insertString(insertAt, "\n" + line(indent, key, arrowColumn))
    }

    private fun insertIntoEmptyArray(document: Document, file: PsiFile, key: String) {
        val array = LangFilePsi.langArrays(file).firstOrNull() ?: return
        val arrayText = array.text
        val opening = arrayText.indexOfFirst { it == '[' || it == '(' }
        if (opening < 0) return
        val insertAt = array.textRange.startOffset + opening + 1
        val closingOnSameLine = !arrayText.substring(opening + 1).contains('\n')
        document.insertString(insertAt, "\n" + line(DEFAULT_INDENT, key, 0) + if (closingOnSameLine) "\n" else "")
    }

    private fun line(indent: String, key: String, arrowColumn: Int): String {
        val quotedKey = "'" + key.replace(UNESCAPED_QUOTE, "\\\\'") + "'"
        val start = indent + quotedKey
        return start.padEnd(maxOf(arrowColumn, start.length + 1)) + "=> '',"
    }

    private fun documentOf(file: PsiFile): Document? =
        PsiDocumentManager.getInstance(file.project).getDocument(file)

    private fun skipBlanks(text: CharSequence, from: Int): Int {
        var index = from
        while (index < text.length && (text[index] == ' ' || text[index] == '\t')) index++
        return index
    }

    private fun skipWhitespace(text: CharSequence, from: Int): Int {
        var index = from
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }
}
