package io.github.vanillathunder.oxidbuddy.translation.compare

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.vanillathunder.oxidbuddy.translation.LangFilePsi

enum class Side { LEFT, RIGHT }

/** The comparison of one language file in two languages, and the edits offered on it. */
class TranslationCompareSession(
    private val project: Project,
    val left: VirtualFile,
    val right: VirtualFile,
) {
    /** Name of the language directory of a side, e.g. `de`. */
    fun language(side: Side): String = file(side).parent?.name ?: file(side).name

    /** Rows of the current file contents. Keys translated on both sides are left out unless [showAll]. */
    fun rows(showAll: Boolean): List<CompareRow> {
        val leftPsi = psi(Side.LEFT) ?: return emptyList()
        val rightPsi = psi(Side.RIGHT) ?: return emptyList()
        val rows = LangFileComparison.compare(leftPsi, rightPsi)
        return if (showAll) rows else rows.filter { it.state != RowState.BOTH }
    }

    /** Adds [keys] with an empty text to the file on side [to]. Written immediately, one undo step. */
    fun copyKeys(keys: Collection<String>, to: Side) {
        val source = psi(if (to == Side.LEFT) Side.RIGHT else Side.LEFT) ?: return
        val target = psi(to) ?: return
        // In source order, so that keys copied together land next to each other in the same order.
        val ordered = LangFileComparison.texts(source).keys.filter { it in keys }
        if (ordered.isEmpty()) return
        WriteCommandAction.writeCommandAction(project, target)
            .withName("Add Translation Keys to ${language(to)}/${target.name}")
            .run<RuntimeException> {
                for (key in ordered) LangFileEditor.copyKey(source, target, key)
            }
    }

    /** Removes [keys] from the file on side [from]. Written immediately, one undo step. */
    fun removeKeys(keys: Collection<String>, from: Side) {
        val target = psi(from) ?: return
        if (keys.isEmpty()) return
        WriteCommandAction.writeCommandAction(project, target)
            .withName("Remove Translation Keys from ${language(from)}/${target.name}")
            .run<RuntimeException> {
                for (key in keys) LangFileEditor.removeKey(target, key)
            }
    }

    /**
     * Opens the file on [side] in the editor with the caret in the text of [key], ready to type the
     * translation. Returns false when that file does not define the key.
     */
    fun navigate(side: Side, key: String): Boolean {
        val psiFile = psi(side) ?: return false
        // The last definition is the one PHP uses.
        val element = LangFilePsi.entries(psiFile).lastOrNull { LangFilePsi.keyOf(it) == key } ?: return false
        val value = element.value
        val offset = when (value) {
            null -> element.textOffset
            is StringLiteralExpression -> value.textRange.startOffset + 1
            else -> value.textRange.startOffset
        }
        OpenFileDescriptor(project, file(side), offset).navigate(true)
        return true
    }

    private fun file(side: Side): VirtualFile = if (side == Side.LEFT) left else right

    private fun psi(side: Side): PsiFile? {
        val file = file(side).takeIf { it.isValid } ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        // Pick up edits made in an editor that have not reached the PSI yet.
        PsiDocumentManager.getInstance(project).getDocument(psiFile)?.let {
            PsiDocumentManager.getInstance(project).commitDocument(it)
        }
        return psiFile
    }
}
