package io.github.vanillathunder.oxidbuddy.translation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

/** Reads the `$aLang = [...]` arrays of an OXID language file. */
object LangFilePsi {
    private const val LANG_VARIABLE = "aLang"

    /** Legacy language files declare their encoding as a pseudo key. */
    private const val CHARSET_KEY = "charset"

    /** Every array literal assigned to `$aLang`, in file order. */
    fun langArrays(file: PsiFile): List<ArrayCreationExpression> =
        PsiTreeUtil.findChildrenOfType(file, AssignmentExpression::class.java).mapNotNull { assignment ->
            val variable = assignment.variable as? Variable ?: return@mapNotNull null
            if (variable.name != LANG_VARIABLE) return@mapNotNull null
            assignment.value as? ArrayCreationExpression
        }

    /** All `'KEY' => ...` elements that define a translation key, in file order. */
    fun entries(file: PsiFile): List<ArrayHashElement> =
        langArrays(file).flatMap { array -> array.hashElements.filter { keyOf(it) != null } }

    /** The translation key of an array element, or null when it is not one. */
    fun keyOf(element: ArrayHashElement): String? {
        val key = (element.key as? StringLiteralExpression)?.contents ?: return null
        return key.takeUnless { it.isEmpty() || it == CHARSET_KEY }
    }

    /** The translated text: string contents without quotes, or the source text of other expressions. */
    fun textOf(element: ArrayHashElement): String =
        when (val value: PsiElement? = element.value) {
            null -> ""
            is StringLiteralExpression -> value.contents
            else -> value.text
        }
}
