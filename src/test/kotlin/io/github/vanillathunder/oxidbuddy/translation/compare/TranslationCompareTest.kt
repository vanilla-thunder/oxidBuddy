package io.github.vanillathunder.oxidbuddy.translation.compare

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import io.github.vanillathunder.oxidbuddy.translation.LangFiles
import javax.swing.JButton

class TranslationCompareTest : BasePlatformTestCase() {

    private val dir = "source/Application/translations"

    private fun file(path: String, content: String): PsiFile = myFixture.addFileToProject(path, content)

    private fun lang(path: String, vararg lines: String): PsiFile =
        file(path, "<?php\n\n\$aLang = [\n" + lines.joinToString("") { "$it\n" } + "];\n")

    private fun text(file: PsiFile): String = file.viewProvider.document!!.text

    private fun edit(file: PsiFile, action: () -> Unit) {
        WriteCommandAction.writeCommandAction(project, file).run<RuntimeException> { action() }
    }

    // --- comparison ---------------------------------------------------------------------------

    fun testPairsKeysAndKeepsFileOrder() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a de',", "    'B' => 'b de',", "    'D' => 'd de',")
        val en = lang("$dir/en/lang.php", "    'X' => 'x en',", "    'A' => 'a en',", "    'C' => 'c en',", "    'D' => '',")

        val rows = LangFileComparison.compare(de, en)

        assertEquals(
            listOf(
                CompareRow("X", null, "x en"),
                CompareRow("A", "a de", "a en"),
                CompareRow("C", null, "c en"),
                CompareRow("B", "b de", null),
                CompareRow("D", "d de", ""),
            ),
            rows,
        )
        assertEquals(
            listOf(RowState.RIGHT_ONLY, RowState.BOTH, RowState.RIGHT_ONLY, RowState.LEFT_ONLY, RowState.UNTRANSLATED),
            rows.map { it.state },
        )
    }

    fun testReadsOldArraySyntaxAndIgnoresCharset() {
        val de = file("$dir/de/lang.php", "<?php\n\$sLangName = 'Deutsch';\n\$aLang = array(\n'charset' => 'UTF-8',\n'A' => \"a\",\n);\n")

        assertEquals(mapOf("A" to "a"), LangFileComparison.texts(de))
    }

    fun testSessionHidesTranslatedKeysUnlessShowAll() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a de',", "    'B' => 'b de',", "    'D' => 'd de',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a en',", "    'D' => '',")
        val session = TranslationCompareSession(project, de.virtualFile, en.virtualFile)

        assertEquals(listOf("B", "D"), session.rows(showAll = false).map { it.key })
        assertEquals(listOf("A", "B", "D"), session.rows(showAll = true).map { it.key })
        assertEquals("de", session.language(Side.LEFT))
        assertEquals("en", session.language(Side.RIGHT))
    }

    // --- adding keys --------------------------------------------------------------------------

    fun testCopiedKeyIsEmptyAndFollowsTheSameNeighbour() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a de',", "    'B' => 'b de',", "    'C' => 'c de',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a en',", "    'C' => 'c en',")

        TranslationCompareSession(project, de.virtualFile, en.virtualFile).copyKeys(listOf("B"), Side.RIGHT)

        assertEquals("<?php\n\n\$aLang = [\n    'A' => 'a en',\n    'B' => '',\n    'C' => 'c en',\n];\n", text(en))
        assertEquals("<?php\n\n\$aLang = [\n    'A' => 'a de',\n    'B' => 'b de',\n    'C' => 'c de',\n];\n", text(de))
    }

    fun testSeveralCopiedKeysKeepSourceOrder() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a',", "    'B' => 'b',", "    'C' => 'c',", "    'D' => 'd',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a',", "    'D' => 'd',")

        TranslationCompareSession(project, de.virtualFile, en.virtualFile).copyKeys(listOf("C", "B"), Side.RIGHT)

        assertEquals(listOf("A", "B", "C", "D"), LangFileComparison.texts(en).keys.toList())
    }

    fun testCopyToTheLeftSide() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a',", "    'B' => 'b',")

        TranslationCompareSession(project, de.virtualFile, en.virtualFile).copyKeys(listOf("B"), Side.LEFT)

        assertEquals(mapOf("A" to "a", "B" to ""), LangFileComparison.texts(de))
    }

    fun testKeyWithoutNeighbourGoesToTheEnd() {
        val de = lang("$dir/de/lang.php", "    'FIRST' => 'x',", "    'A' => 'a',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a',", "    'Z' => 'z',")

        TranslationCompareSession(project, de.virtualFile, en.virtualFile).copyKeys(listOf("FIRST"), Side.RIGHT)

        assertEquals(listOf("A", "Z", "FIRST"), LangFileComparison.texts(en).keys.toList())
    }

    fun testAddsMissingCommaToLastElement() {
        val en = lang("$dir/en/lang.php", "    'A' => 'a'")

        edit(en) { LangFileEditor.addKey(en, "B", "A") }

        assertEquals("<?php\n\n\$aLang = [\n    'A' => 'a',\n    'B' => '',\n];\n", text(en))
    }

    fun testAlignsArrowAndKeepsTrailingCommentWithItsLine() {
        val en = lang("$dir/en/lang.php", "\t'LONG_KEY_NAME'   => 'a', // keep me", "\t'OTHER'           => 'o',")

        edit(en) { LangFileEditor.addKey(en, "B", "LONG_KEY_NAME") }

        assertEquals(
            "<?php\n\n\$aLang = [\n\t'LONG_KEY_NAME'   => 'a', // keep me\n\t'B'               => '',\n\t'OTHER'           => 'o',\n];\n",
            text(en),
        )
    }

    fun testLongerKeyThanTheArrowColumn() {
        val en = lang("$dir/en/lang.php", "    'A' => 'a',")

        edit(en) { LangFileEditor.addKey(en, "MUCH_LONGER", "A") }

        assertEquals("<?php\n\n\$aLang = [\n    'A' => 'a',\n    'MUCH_LONGER' => '',\n];\n", text(en))
    }

    fun testAddsToEmptyArrays() {
        val multiLine = file("$dir/en/lang.php", "<?php\n\$aLang = [\n];\n")
        val singleLine = file("$dir/fr/lang.php", "<?php\n\$aLang = array();\n")

        edit(multiLine) { LangFileEditor.addKey(multiLine, "A", null) }
        edit(singleLine) { LangFileEditor.addKey(singleLine, "A", null) }

        assertEquals("<?php\n\$aLang = [\n    'A' => '',\n];\n", text(multiLine))
        assertEquals("<?php\n\$aLang = array(\n    'A' => '',\n);\n", text(singleLine))
    }

    fun testCopyDoesNothingWhenTheKeyExists() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a de',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a en',")
        val before = text(en)

        TranslationCompareSession(project, de.virtualFile, en.virtualFile).copyKeys(listOf("A"), Side.RIGHT)

        assertEquals(before, text(en))
    }

    // --- removing keys ------------------------------------------------------------------------

    fun testRemovesTheWholeLine() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a',", "    'B' => 'b',", "    'C' => 'c',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a',")

        TranslationCompareSession(project, de.virtualFile, en.virtualFile).removeKeys(listOf("B"), Side.LEFT)

        assertEquals("<?php\n\n\$aLang = [\n    'A' => 'a',\n    'C' => 'c',\n];\n", text(de))
        assertEquals("<?php\n\n\$aLang = [\n    'A' => 'a',\n];\n", text(en))
    }

    fun testRemovesLastElementWithoutComma() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a',", "    'B' => 'b'")

        edit(de) { LangFileEditor.removeKey(de, "B") }

        assertEquals("<?php\n\n\$aLang = [\n    'A' => 'a',\n];\n", text(de))
    }

    fun testRemovesMultiLineValueAndEveryDuplicate() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a',", "    'B' => 'line one", "line two',", "    'C' => 'c',", "    'B' => 'again',")

        edit(de) { LangFileEditor.removeKey(de, "B") }

        assertEquals("<?php\n\n\$aLang = [\n    'A' => 'a',\n    'C' => 'c',\n];\n", text(de))
    }

    fun testKeepsOtherContentOfASharedLine() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a', 'B' => 'b', // note", "    'C' => 'c',")

        edit(de) { LangFileEditor.removeKey(de, "A") }

        assertEquals("<?php\n\n\$aLang = [\n    'B' => 'b', // note\n    'C' => 'c',\n];\n", text(de))
    }

    fun testRemoveSeveralKeysIsOneUndoableCommand() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a',", "    'B' => 'b',", "    'C' => 'c',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a',")

        TranslationCompareSession(project, de.virtualFile, en.virtualFile).removeKeys(listOf("A", "C"), Side.LEFT)

        assertEquals(listOf("B"), LangFileComparison.texts(de).keys.toList())
    }

    // --- action -------------------------------------------------------------------------------

    private fun isOffered(file: VirtualFile): Boolean {
        val action = CompareTranslationsAction()
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    fun testActionIsOfferedOnLanguageFilesOnly() {
        val langFile = lang("$dir/de/lang.php", "    'A' => 'a',")
        val custLang = lang("source/Application/views/mytheme/en_us/cust_lang.php", "    'A' => 'a',")
        val wrongName = lang("$dir/de/other.php", "    'A' => 'a',")
        val noLanguageDir = lang("source/modules/acme/Core/helper_lang.php", "    'A' => 'a',")

        assertTrue(isOffered(langFile.virtualFile))
        assertTrue(isOffered(custLang.virtualFile))
        assertFalse(isOffered(wrongName.virtualFile))
        assertFalse(isOffered(noLanguageDir.virtualFile))
        assertFalse(isOffered(langFile.virtualFile.parent))
    }

    fun testFindsTheSameFileInOtherLanguages() {
        val de = lang("$dir/de/shop_lang.php", "    'A' => 'a',")
        lang("$dir/nl/shop_lang.php", "    'A' => 'a',")
        lang("$dir/en_us/shop_lang.php", "    'A' => 'a',")
        lang("$dir/fr/other_lang.php", "    'A' => 'a',")
        lang("$dir/notalanguage/shop_lang.php", "    'A' => 'a',")

        assertEquals(listOf("en_us", "nl"), LangFiles.siblings(de.virtualFile).map { it.parent.name })
    }

    // --- dialog -------------------------------------------------------------------------------

    // --- opening a key ------------------------------------------------------------------------

    fun testNavigateOpensTheFileWithTheCaretInTheText() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a de',", "    'B' => 'b de',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a en',", "    'B' => '',")
        val session = TranslationCompareSession(project, de.virtualFile, en.virtualFile)

        assertTrue(session.navigate(Side.RIGHT, "B"))

        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertEquals(en.virtualFile, FileDocumentManager.getInstance().getFile(editor.document))
        assertEquals(text(en).indexOf("'B' => '") + "'B' => '".length, editor.caretModel.offset)
    }

    fun testNavigateToAKeyTheFileDoesNotHave() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a de',", "    'B' => 'b de',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a en',")

        assertFalse(TranslationCompareSession(project, de.virtualFile, en.virtualFile).navigate(Side.RIGHT, "B"))
    }

    fun testDoubleClickOpensTheClickedSideOrTheOneThatHasTheKey() {
        val de = lang("$dir/de/lang.php", "    'BOTH' => 'de',", "    'DE_ONLY' => 'de',")
        val en = lang("$dir/en/lang.php", "    'BOTH' => '',", "    'EN_ONLY' => 'en',")
        val content = TranslationComparePanel(TranslationCompareSession(project, de.virtualFile, en.virtualFile))
        val opened = ArrayList<Pair<Side, String>>()
        content.onOpenKey = { side, key -> opened += side to key }
        val rows = (0 until content.table.rowCount).associateBy { content.table.getValueAt(it, 0) as String }

        content.openKey(rows.getValue("BOTH"), 0)
        content.openKey(rows.getValue("BOTH"), 1)
        content.openKey(rows.getValue("BOTH"), 2)
        content.openKey(rows.getValue("DE_ONLY"), 2)
        content.openKey(rows.getValue("EN_ONLY"), 1)

        assertEquals(
            listOf(
                Side.LEFT to "BOTH",
                Side.LEFT to "BOTH",
                Side.RIGHT to "BOTH",
                Side.LEFT to "DE_ONLY",
                Side.RIGHT to "EN_ONLY",
            ),
            opened,
        )
    }

    fun testDialogOpens() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a de',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a en',")

        val dialog = TranslationCompareDialog(project, TranslationCompareSession(project, de.virtualFile, en.virtualFile))
        Disposer.register(testRootDisposable, dialog.disposable)

        assertEquals("Compare Translations: lang.php (de ↔ en)", dialog.title)
    }

    fun testPanelButtonsEditTheFilesAndRefreshTheTable() {
        val de = lang("$dir/de/lang.php", "    'A' => 'a de',", "    'B' => 'b de',", "    'C' => 'c de',")
        val en = lang("$dir/en/lang.php", "    'A' => 'a en',", "    'X' => 'x en',")
        val content = TranslationComparePanel(TranslationCompareSession(project, de.virtualFile, en.virtualFile))

        val table = content.table
        val buttons = UIUtil.findComponentsOfType(content, JButton::class.java).associateBy { it.text }
        val showAll = UIUtil.findComponentOfType(content, JBCheckBox::class.java)!!
        fun keys() = (0 until table.rowCount).map { table.getValueAt(it, 0) }

        assertEquals(listOf("Key", "de", "en"), (0 until table.columnCount).map { table.getColumnName(it) })
        assertEquals(listOf("X", "B", "C"), keys())

        // Nothing selected: nothing to do.
        assertFalse(buttons.getValue("Add key to en").isEnabled)
        assertFalse(buttons.getValue("Remove from de").isEnabled)

        // 'B' only exists in de: it can be added to en or removed from de, nothing else.
        table.setRowSelectionInterval(1, 1)
        assertTrue(buttons.getValue("Add key to en").isEnabled)
        assertFalse(buttons.getValue("Add key to de").isEnabled)
        assertTrue(buttons.getValue("Remove from de").isEnabled)
        assertFalse(buttons.getValue("Remove from en").isEnabled)

        buttons.getValue("Add key to en").doClick()
        assertEquals(mapOf("A" to "a en", "B" to "", "X" to "x en"), LangFileComparison.texts(en))
        // 'B' is now untranslated in en and stays listed; 'X' follows it in en.
        assertEquals(listOf("B", "X", "C"), keys())
        assertEquals("", table.getValueAt(0, 2))

        // 'X' only exists in en: remove it there.
        table.setRowSelectionInterval(1, 1)
        buttons.getValue("Remove from en").doClick()
        assertEquals(mapOf("A" to "a en", "B" to ""), LangFileComparison.texts(en))
        assertEquals(listOf("B", "C"), keys())

        showAll.doClick()
        assertEquals(listOf("A", "B", "C"), keys())
    }
}
