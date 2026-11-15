package io.github.vanillathunder.oxidbuddy.translation

import com.intellij.openapi.util.Pair
import com.jetbrains.smarty.config.SmartyConfiguration

class SmartyTranslationKeyCompletionTest : TranslationTestCase() {

    private lateinit var originalDelimiters: Pair<String, String>

    override fun setUp() {
        super.setUp()
        // OXID templates use [{ }] instead of the Smarty default { }.
        val configuration = SmartyConfiguration.getInstance(project)
        originalDelimiters = configuration.smartyDelimiters
        configuration.smartyDelimiters = Pair.create("[{", "}]")
    }

    override fun tearDown() {
        try {
            SmartyConfiguration.getInstance(project).smartyDelimiters = originalDelimiters
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testOffersKeysInOxmultilangIdent() {
        val keys = complete("source/modules/acme/shipping/views/tpl/a.tpl", "<p>[{oxmultilang ident=\"<caret>\"}]</p>")

        assertContainsElements(keys, "CORE_ONLY", "EN_ONLY", "THEME_ONLY", "CUST_KEY", "SUBSHOP_KEY", "ACME_MODULE_KEY", "SHARED")
        assertDoesntContain(keys, "NOT_A_LANG_FILE", "NO_LANGUAGE_DIR")
    }

    fun testModuleKeysRankAboveThemeAboveCore() {
        val keys = complete("source/modules/acme/shipping/views/tpl/a.tpl", "[{oxmultilang ident=\"<caret>\"}]")

        assertTrue(keys.toString(), keys.indexOf("ACME_MODULE_KEY") < keys.indexOf("THEME_ONLY"))
        assertTrue(keys.toString(), keys.indexOf("THEME_ONLY") < keys.indexOf("CORE_ONLY"))
    }

    fun testShowsTextAndSource() {
        complete("source/modules/acme/shipping/views/tpl/a.tpl", "[{oxmultilang ident=\"<caret>\"}]")

        assertEquals("  Modul geteilt", presentation("SHARED").tailText)
        assertEquals("acme/shipping · de", presentation("SHARED").typeText)
    }

    fun testMatchesTypedPrefixIgnoringCaseAndInsertsSingleMatch() {
        val keys = complete("source/modules/acme/shipping/views/tpl/a.tpl", "[{oxmultilang ident=\"theme_<caret>\" suffix=\"COLON\"}]")

        assertEmpty(keys)
        myFixture.checkResult("[{oxmultilang ident=\"THEME_ONLY\" suffix=\"COLON\"}]")
    }

    fun testWorksWithSingleQuotes() {
        val keys = complete("source/modules/acme/shipping/views/tpl/a.tpl", "[{oxmultilang ident='ACME_<caret>'}]")

        assertSameElements(keys, "ACME_MODULE_KEY", "ACME_ADMIN_KEY")
    }

    fun testFrontendTemplateRanksFrontendKeysFirstAndLabelsAdminKeys() {
        val keys = complete("source/modules/acme/shipping/views/tpl/a.tpl", "[{oxmultilang ident=\"<caret>\"}]")

        assertTrue(keys.toString(), keys.indexOf("CORE_ONLY") < keys.indexOf("ACME_ADMIN_KEY"))
        assertEquals("acme/shipping · de · admin", presentation("ACME_ADMIN_KEY").typeText)
    }

    fun testAdminTemplateRanksAdminKeysFirst() {
        val keys = complete("source/modules/acme/shipping/views/admin/tpl/a.tpl", "[{oxmultilang ident=\"<caret>\"}]")

        assertTrue(keys.toString(), keys.indexOf("ADMIN_CORE") < keys.indexOf("ACME_MODULE_KEY"))
        assertEquals("  Admin", presentation("BOTH_SCOPES").tailText)
    }

    fun testAdminBlockTemplateRanksAdminKeysFirst() {
        val keys = complete("source/modules/acme/shipping/views/blocks/admin_user_main_form.tpl", "[{oxmultilang ident=\"<caret>\"}]")

        assertTrue(keys.toString(), keys.indexOf("ADMIN_CORE") < keys.indexOf("ACME_MODULE_KEY"))
    }

    fun testIgnoresOtherAttributesOfOxmultilang() {
        val keys = complete("source/modules/acme/shipping/views/tpl/a.tpl", "[{oxmultilang ident=\"SHARED\" suffix=\"<caret>\"}]")

        assertDoesntContain(keys, "CORE_ONLY")
    }

    fun testIgnoresIdentOfOtherTags() {
        val keys = complete("source/modules/acme/shipping/views/tpl/a.tpl", "[{oxcontent ident=\"<caret>\"}]")

        assertDoesntContain(keys, "CORE_ONLY")
    }
}
