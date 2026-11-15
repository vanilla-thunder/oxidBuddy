package io.github.vanillathunder.oxidbuddy.translation

class PhpTranslationKeyCompletionTest : TranslationTestCase() {

    private fun php(body: String) = "<?php\nuse OxidEsales\\EshopCommunity\\Core\\Registry;\nuse OxidEsales\\EshopCommunity\\Core\\Unrelated;\n$body\n"

    fun testOffersKeysFromAllFrontendSources() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString('<caret>');"))

        assertContainsElements(keys, "CORE_ONLY", "EN_ONLY", "THEME_ONLY", "CUST_KEY", "SUBSHOP_KEY", "ACME_MODULE_KEY", "SHARED")
        assertDoesntContain(keys, "NOT_A_LANG_FILE", "NO_LANGUAGE_DIR", "charset")
    }

    fun testEachKeyIsOfferedOnce() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString('<caret>');"))

        assertEquals(keys.size, keys.toSet().size)
    }

    fun testModuleKeysRankAboveThemeAboveCore() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString('<caret>');"))

        assertTrue(keys.toString(), keys.indexOf("ACME_MODULE_KEY") < keys.indexOf("THEME_ONLY"))
        assertTrue(keys.toString(), keys.indexOf("THEME_ONLY") < keys.indexOf("CORE_ONLY"))
    }

    fun testShowsTextAndSourceOfTheWinningDefinition() {
        complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString('<caret>');"))

        // Defined in core, theme and module: the module definition wins.
        assertEquals("  Modul geteilt", presentation("SHARED").tailText)
        assertEquals("acme/shipping · de", presentation("SHARED").typeText)
        // Defined in de and nl: the preferred language wins.
        assertEquals("  Modul", presentation("ACME_MODULE_KEY").tailText)
        // Only defined in a non-first language.
        assertEquals("core · en", presentation("EN_ONLY").typeText)
        assertEquals("theme mytheme · de", presentation("THEME_ONLY").typeText)
        assertEquals("core · de", presentation("CORE_ONLY").typeText)
    }

    fun testFrontendCodeRanksFrontendKeysFirstAndLabelsAdminKeys() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString('<caret>');"))

        assertTrue(keys.toString(), keys.indexOf("CORE_ONLY") < keys.indexOf("ACME_ADMIN_KEY"))
        assertEquals("acme/shipping · de · admin", presentation("ACME_ADMIN_KEY").typeText)
        // Exists in both scopes: the frontend definition is shown.
        assertEquals("  Frontend", presentation("BOTH_SCOPES").tailText)
    }

    fun testAdminCodeRanksAdminKeysFirst() {
        val keys = complete("source/modules/acme/shipping/Controller/Admin/a.php", php("Registry::getLang()->translateString('<caret>');"))

        assertTrue(keys.toString(), keys.indexOf("ADMIN_CORE") < keys.indexOf("ACME_MODULE_KEY"))
        assertEquals("  Admin", presentation("BOTH_SCOPES").tailText)
        assertEquals("acme/shipping · de · frontend", presentation("ACME_MODULE_KEY").typeText)
    }

    fun testLiteralAdminModeArgumentDecidesTheScope() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString('<caret>', 0, true);"))

        assertTrue(keys.toString(), keys.indexOf("ADMIN_CORE") < keys.indexOf("ACME_MODULE_KEY"))
    }

    fun testMatchesTypedPrefixIgnoringCase() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString('acme_<caret>');"))

        assertSameElements(keys, "ACME_MODULE_KEY", "ACME_ADMIN_KEY")
    }

    fun testInsertsKeyAsDefinedWhenTypedInLowerCase() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString('theme_<caret>');"))

        assertEmpty(keys)
        myFixture.checkResult(php("Registry::getLang()->translateString('THEME_ONLY');"))
    }

    fun testWorksInDoubleQuotedStrings() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString(\"THEME_<caret>\");"))

        // A single match is inserted right away.
        assertEmpty(keys)
        myFixture.checkResult(php("Registry::getLang()->translateString(\"THEME_ONLY\");"))
    }

    fun testCompletesOnUntypedReceiver() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("\$oLang->translateString('<caret>');"))

        assertContainsElements(keys, "CORE_ONLY")
    }

    fun testIgnoresSameMethodNameOnUnrelatedClass() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("(new Unrelated())->translateString('<caret>');"))

        assertDoesntContain(keys, "CORE_ONLY")
    }

    fun testIgnoresOtherMethods() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->getBaseLanguage('<caret>');"))

        assertDoesntContain(keys, "CORE_ONLY")
    }

    fun testIgnoresOtherArguments() {
        val keys = complete("source/modules/acme/shipping/Model/a.php", php("Registry::getLang()->translateString('X', '<caret>');"))

        assertDoesntContain(keys, "CORE_ONLY")
    }
}
