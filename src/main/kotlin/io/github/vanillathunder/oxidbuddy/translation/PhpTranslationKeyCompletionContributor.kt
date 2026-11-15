package io.github.vanillathunder.oxidbuddy.translation

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Completes translation keys in the first argument of `Language::translateString()`. */
class PhpTranslationKeyCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(StringLiteralExpression::class.java),
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
            val literal = position.parent as? StringLiteralExpression ?: return
            val arguments = literal.parent as? ParameterList ?: return
            if (arguments.parameters.firstOrNull() !== literal) return
            val call = arguments.parent as? MethodReference ?: return
            val classes = TRANSLATE_METHODS[call.name] ?: return
            if (!isCallOn(call, classes)) return

            val typed = position.text
                .take((parameters.offset - position.textRange.startOffset).coerceIn(0, position.textLength))
                .trimStart('\'', '"')

            val scope = adminModeArgument(arguments)
                ?: TranslationKeyCompletion.guessScope(parameters.originalFile.virtualFile)
            TranslationKeyCompletion.addKeys(position.project, scope, typed, result)
        }

        /**
         * True when the called method belongs to one of [classes] or a subclass. Calls whose target
         * cannot be resolved at all (untyped receiver) are accepted on the method name alone.
         */
        private fun isCallOn(call: MethodReference, classes: Set<String>): Boolean {
            val methods = call.multiResolve(false).mapNotNull { it.element as? Method }
            if (methods.isEmpty()) return true
            return methods.any { method -> method.containingClass?.let { isOneOf(it, classes) } == true }
        }

        private fun isOneOf(phpClass: PhpClass, classes: Set<String>): Boolean {
            var current: PhpClass? = phpClass
            var depth = 0
            while (current != null && depth < MAX_HIERARCHY_DEPTH) {
                if (current.fqn.lowercase() in classes) return true
                current = current.superClass
                depth++
            }
            return false
        }

        /** `translateString($key, $lang, $adminMode)`: a literal third argument states the scope. */
        private fun adminModeArgument(arguments: ParameterList): LangScope? {
            val argument = arguments.parameters.getOrNull(2) as? ConstantReference ?: return null
            return when (argument.text.lowercase()) {
                "true" -> LangScope.ADMIN
                "false" -> LangScope.FRONTEND
                else -> null
            }
        }
    }

    companion object {
        private const val MAX_HIERARCHY_DEPTH = 20

        /** Method name -> lower-cased FQNs of the classes declaring it. */
        private val TRANSLATE_METHODS: Map<String, Set<String>> = mapOf(
            "translateString" to setOf(
                "\\oxidesales\\eshopcommunity\\core\\language",
                "\\oxidesales\\eshop\\core\\language",
                "\\oxlang",
            ),
        )
    }
}
