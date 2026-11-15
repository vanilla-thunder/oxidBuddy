package io.github.vanillathunder.oxidbuddy.translation

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import com.jetbrains.smarty.lang.SmartyTokenTypes
import com.jetbrains.smarty.lang.psi.SmartyTag

/** Completes translation keys in `[{oxmultilang ident="..."}]`. */
class SmartyTranslationKeyCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(SmartyTokenTypes.STRING_LITERAL).withParent(SmartyTag::class.java),
            Provider(),
        )
    }

    private class Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val position = parameters.position
            val tag = position.parent as? SmartyTag ?: return
            val attribute = TRANSLATE_TAGS[tag.tagName] ?: return
            if (attributeNameOf(position) != attribute) return

            val typed = position.text
                .take((parameters.offset - position.textRange.startOffset).coerceIn(0, position.textLength))

            val scope = TranslationKeyCompletion.guessScope(parameters.originalFile.virtualFile)
            TranslationKeyCompletion.addKeys(position.project, scope, typed, result)
        }

        /**
         * The Smarty plugin parses the attributes of custom functions as a flat token sequence
         * (`name = " value "`) directly below the tag, so the name is found by walking backwards.
         */
        private fun attributeNameOf(value: PsiElement): String? {
            val quote = previous(value, SmartyTokenTypes.DOUBLE_QUOTE, SmartyTokenTypes.SINGLE_QUOTE) ?: return null
            val equals = previous(quote, SmartyTokenTypes.EQ) ?: return null
            return previous(equals, SmartyTokenTypes.IDENTIFIER)?.text
        }

        private fun previous(element: PsiElement, vararg types: IElementType): PsiElement? =
            PsiTreeUtil.skipWhitespacesBackward(element)?.takeIf { it.elementType in types }
    }

    companion object {
        /** Tag name -> name of the attribute holding the translation key. */
        private val TRANSLATE_TAGS: Map<String, String> = mapOf(
            "oxmultilang" to "ident",
        )
    }
}
