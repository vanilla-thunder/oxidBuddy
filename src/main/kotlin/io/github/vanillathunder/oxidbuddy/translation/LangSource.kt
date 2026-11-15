package io.github.vanillathunder.oxidbuddy.translation

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/** Which translation pool a language file belongs to. OXID never mixes the two at runtime. */
enum class LangScope(val label: String) {
    FRONTEND("frontend"),
    ADMIN("admin"),
}

/** Where a language file comes from. Declared in ascending priority order. */
enum class LangOrigin {
    CORE,
    THEME,
    MODULE,
}

/** Everything known about the file a translation key was found in. */
data class LangSource(
    val scope: LangScope,
    val origin: LangOrigin,
    /** Module path (e.g. `acme/shipping`), theme name, or empty for core files. */
    val originName: String,
    /** Name of the language directory, e.g. `de` or `en_us`. */
    val language: String,
    val file: VirtualFile,
) {
    /** Vendor copies are the pristine package files; the working copy outside `vendor` wins. */
    val inVendor: Boolean
        get() = file.path.contains("/vendor/")

    val originLabel: String
        get() = when (origin) {
            LangOrigin.MODULE -> originName
            LangOrigin.THEME -> "theme $originName"
            LangOrigin.CORE -> "core"
        }
}

object LangFiles {
    private val LANGUAGE_DIR = Regex("[A-Za-z]{2}(_[A-Za-z]{2})?")

    /** How far above the language directory a `metadata.php` / `theme.php` is searched for. */
    private const val MAX_ROOT_SEARCH_DEPTH = 6

    fun isLangFileName(name: String): Boolean = name == "lang.php" || name.endsWith("_lang.php")

    fun isLanguageDirName(name: String): Boolean = LANGUAGE_DIR.matches(name)

    /** A language file by name that sits in a language directory. */
    fun isLangFile(file: VirtualFile): Boolean =
        !file.isDirectory && isLangFileName(file.name) && file.parent?.let { isLanguageDirName(it.name) } == true

    /** The files with the same name in the other language directories next to [file]'s, by language. */
    fun siblings(file: VirtualFile): List<VirtualFile> {
        val languageDir = file.parent ?: return emptyList()
        val container = languageDir.parent ?: return emptyList()
        return container.children
            .filter { it.isDirectory && it != languageDir && isLanguageDirName(it.name) }
            .mapNotNull { it.findChild(file.name) }
            .filter { !it.isDirectory }
            .sortedBy { it.parent.name }
    }

    fun isAdminName(name: String): Boolean =
        name.equals("admin", ignoreCase = true) || name.startsWith("admin_", ignoreCase = true)

    /**
     * Derives the source of a language file from its location:
     *
     * - `<...>/<language>/<file>`: the parent directory names the language
     * - a parent of the language directory called `admin` (or `admin_*`) marks admin translations
     * - the closest ancestor holding a `metadata.php` is a module, one holding a `theme.php` a theme
     *
     * Returns null when the file does not sit in a language directory.
     */
    fun classify(file: VirtualFile): LangSource? {
        val languageDir = file.parent ?: return null
        if (!isLanguageDirName(languageDir.name)) return null
        val container = languageDir.parent ?: return null

        val scope = if (isAdminName(container.name)) LangScope.ADMIN else LangScope.FRONTEND

        var dir: VirtualFile? = container
        var depth = 0
        while (dir != null && depth < MAX_ROOT_SEARCH_DEPTH) {
            if (dir.findChild("metadata.php") != null) {
                return LangSource(scope, LangOrigin.MODULE, moduleName(dir), languageDir.name, file)
            }
            if (dir.findChild("theme.php") != null) {
                return LangSource(scope, LangOrigin.THEME, dir.name, languageDir.name, file)
            }
            dir = dir.parent
            depth++
        }
        return LangSource(scope, LangOrigin.CORE, "", languageDir.name, file)
    }

    /** `vendor/module` below a `modules` directory, `vendor/package` below `vendor`, else the directory name. */
    private fun moduleName(moduleRoot: VirtualFile): String {
        var dir: VirtualFile? = moduleRoot.parent
        var depth = 0
        while (dir != null && depth < 3) {
            if (dir.name == "modules" || dir.name == "vendor") {
                return VfsUtilCore.getRelativePath(moduleRoot, dir, '/') ?: moduleRoot.name
            }
            dir = dir.parent
            depth++
        }
        return moduleRoot.name
    }
}
