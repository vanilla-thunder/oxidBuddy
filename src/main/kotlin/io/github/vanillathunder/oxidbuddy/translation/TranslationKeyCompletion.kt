package io.github.vanillathunder.oxidbuddy.translation

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Shared by the PHP and Smarty completion contributors. */
object TranslationKeyCompletion {

    /** Ranks every key of the expected scope above every key of the other scope. */
    private const val EXPECTED_SCOPE_BONUS = 10.0

    /**
     * Adds all translation keys matching [typed], the text between the opening quote and the caret.
     *
     * Keys of [expectedScope] come first, ordered module, theme, core. Keys that only exist in the
     * other scope follow and are labelled with it, because the scope of a call site can only be
     * guessed (admin blocks, shared models).
     */
    fun addKeys(project: Project, expectedScope: LangScope, typed: String, completion: CompletionResultSet) {
        // Keys are upper case by convention: match regardless of the case typed, insert as defined.
        val result = completion.withPrefixMatcher(CamelHumpMatcher(typed, false))
        val all = TranslationKeys.all(project)
        val expected = all[expectedScope].orEmpty()
        for (entry in expected.values) {
            result.addElement(lookup(entry, expectedScope))
        }
        for ((scope, entries) in all) {
            if (scope == expectedScope) continue
            for (entry in entries.values) {
                if (entry.key !in expected) result.addElement(lookup(entry, expectedScope))
            }
        }
    }

    /** Guesses the scope from the location of the file being edited. */
    fun guessScope(file: VirtualFile?): LangScope {
        if (file == null) return LangScope.FRONTEND
        val isAdmin = file.path.split('/').any(LangFiles::isAdminName)
        return if (isAdmin) LangScope.ADMIN else LangScope.FRONTEND
    }

    private fun lookup(entry: TranslationEntry, expectedScope: LangScope): LookupElement {
        val source = entry.source
        var typeText = "${source.originLabel} · ${source.language}"
        if (source.scope != expectedScope) typeText += " · ${source.scope.label}"

        val element = LookupElementBuilder.create(entry.key)
            .withTailText("  ${entry.text}", true)
            .withTypeText(typeText, true)

        val scopeBonus = if (source.scope == expectedScope) EXPECTED_SCOPE_BONUS else 0.0
        return PrioritizedLookupElement.withPriority(element, scopeBonus + source.origin.ordinal)
    }
}
