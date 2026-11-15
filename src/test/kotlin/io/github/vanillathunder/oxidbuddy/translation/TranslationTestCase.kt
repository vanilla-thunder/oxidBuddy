package io.github.vanillathunder.oxidbuddy.translation

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Sets up a miniature OXID shop: core, theme and module language files for frontend and admin. */
abstract class TranslationTestCase : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()

        langFile("source/Application/translations/de/lang.php", "CORE_ONLY" to "Kern", "SHARED" to "Kern geteilt")
        langFile("source/Application/translations/en/lang.php", "CORE_ONLY" to "Core", "EN_ONLY" to "English only")
        langFile("source/Application/views/admin/de/lang.php", "ADMIN_CORE" to "Admin Kern", "BOTH_SCOPES" to "Admin")

        myFixture.addFileToProject("source/Application/views/mytheme/theme.php", "<?php \$aTheme = [];")
        langFile("source/Application/views/mytheme/de/lang.php", "THEME_ONLY" to "Thema", "SHARED" to "Thema geteilt")
        langFile("source/Application/views/mytheme/de/cust_lang.php", "CUST_KEY" to "Angepasst")
        langFile("source/Application/views/mytheme/14/de/lang.php", "SUBSHOP_KEY" to "Subshop")

        myFixture.addFileToProject("source/modules/acme/shipping/metadata.php", "<?php \$aModule = [];")
        langFile(
            "source/modules/acme/shipping/translations/de/shipping_lang.php",
            "ACME_MODULE_KEY" to "Modul", "SHARED" to "Modul geteilt", "BOTH_SCOPES" to "Frontend",
        )
        langFile("source/modules/acme/shipping/translations/nl/shipping_lang.php", "ACME_MODULE_KEY" to "Module NL")
        langFile("source/modules/acme/shipping/Application/views/admin/de/module_lang.php", "ACME_ADMIN_KEY" to "Modul Admin")

        // Not language files: wrong name, or not inside a language directory.
        langFile("source/modules/acme/shipping/translations/de/other.php", "NOT_A_LANG_FILE" to "x")
        langFile("source/modules/acme/shipping/Core/helper_lang.php", "NO_LANGUAGE_DIR" to "x")

        myFixture.addFileToProject(
            "source/Core/Language.php",
            """
            <?php
            namespace OxidEsales\EshopCommunity\Core;

            class Language
            {
                public function translateString(${'$'}sStringToTranslate, ${'$'}iLang = null, ${'$'}blAdminMode = null) {}
                public function getBaseLanguage() {}
            }

            class Registry
            {
                /** @return Language */
                public static function getLang() {}
            }

            class Unrelated
            {
                public function translateString(${'$'}text) {}
            }
            """.trimIndent(),
        )
    }

    protected fun langFile(path: String, vararg entries: Pair<String, String>) {
        val body = entries.joinToString("\n") { (key, text) -> "    '$key' => '$text'," }
        myFixture.addFileToProject(path, "<?php\n\n\$sLangName = 'Test';\n\n\$aLang = [\n$body\n];\n")
    }

    /** Completes at the caret of [text] in a file at [path] and returns the offered lookup strings in order. */
    protected fun complete(path: String, text: String): List<String> {
        val file = myFixture.addFileToProject(path, text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    protected fun presentation(lookupString: String): LookupElementPresentation {
        val element: LookupElement = myFixture.lookupElements.orEmpty().first { it.lookupString == lookupString }
        return LookupElementPresentation.renderElement(element)
    }
}
