package io.github.vanillathunder.oxidbuddy.translation

class LangFilesTest : TranslationTestCase() {

    private fun classify(path: String): LangSource? =
        LangFiles.classify(myFixture.findFileInTempDir(path) ?: error("missing fixture file $path"))

    fun testLangFileNames() {
        assertTrue(LangFiles.isLangFileName("lang.php"))
        assertTrue(LangFiles.isLangFileName("cust_lang.php"))
        assertTrue(LangFiles.isLangFileName("shipping_lang.php"))
        assertFalse(LangFiles.isLangFileName("language.php"))
        assertFalse(LangFiles.isLangFileName("theme_options.php"))
    }

    fun testCoreFrontend() {
        val source = classify("source/Application/translations/de/lang.php")!!
        assertEquals(LangScope.FRONTEND, source.scope)
        assertEquals(LangOrigin.CORE, source.origin)
        assertEquals("de", source.language)
    }

    fun testCoreAdmin() {
        val source = classify("source/Application/views/admin/de/lang.php")!!
        assertEquals(LangScope.ADMIN, source.scope)
        assertEquals(LangOrigin.CORE, source.origin)
    }

    fun testTheme() {
        val source = classify("source/Application/views/mytheme/de/cust_lang.php")!!
        assertEquals(LangScope.FRONTEND, source.scope)
        assertEquals(LangOrigin.THEME, source.origin)
        assertEquals("mytheme", source.originName)
    }

    fun testThemeSubshopDirectory() {
        val source = classify("source/Application/views/mytheme/14/de/lang.php")!!
        assertEquals(LangOrigin.THEME, source.origin)
        assertEquals("mytheme", source.originName)
        assertEquals("de", source.language)
    }

    fun testModuleFrontend() {
        val source = classify("source/modules/acme/shipping/translations/nl/shipping_lang.php")!!
        assertEquals(LangScope.FRONTEND, source.scope)
        assertEquals(LangOrigin.MODULE, source.origin)
        assertEquals("acme/shipping", source.originName)
        assertEquals("nl", source.language)
    }

    fun testModuleAdmin() {
        val source = classify("source/modules/acme/shipping/Application/views/admin/de/module_lang.php")!!
        assertEquals(LangScope.ADMIN, source.scope)
        assertEquals(LangOrigin.MODULE, source.origin)
        assertEquals("acme/shipping", source.originName)
    }

    fun testFileOutsideLanguageDirectory() {
        assertNull(classify("source/modules/acme/shipping/Core/helper_lang.php"))
    }
}
