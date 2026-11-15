# OXID Buddy

A PhpStorm plugin with development helpers for [OXID eShop](https://www.oxid-esales.com/) projects.

> **This project is AI-coded.** All code, tests and this README were written by an AI model
> (Claude, through Claude Code) working from a human developer's instructions. Commits carry a
> `Co-Authored-By: Claude` trailer. Behaviour is checked by automated headless tests; review the
> code as you would any other before relying on it.

## Requirements

- PhpStorm 2026.3 or newer, with the bundled PHP and Smarty plugins enabled
- For Smarty completion: the project's Smarty delimiters set to `[{` and `}]`
  (*Settings → Languages & Frameworks → PHP → Smarty*)

## Features

### 1. Translation key completion

Suggests translation keys while you type, in two places:

| Where | Example |
|---|---|
| Smarty templates (`.tpl`) | `[{oxmultilang ident="<caret>"}]` |
| PHP | `Registry::getLang()->translateString('<caret>')` |

The tag and the method are fixed for now. In PHP the call has to be on
`OxidEsales\Eshop\Core\Language`, `OxidEsales\EshopCommunity\Core\Language`, `oxLang` or a subclass.
A call on a variable whose type PhpStorm cannot determine is accepted on the method name alone.

**Where keys come from.** Every `lang.php` and `*_lang.php` (so also `cust_lang.php`) that sits in
a language directory such as `de/` or `en_us/` and assigns an array literal to `$aLang`. The files
are indexed, so the suggestions follow your edits.

**What each suggestion shows.** The key, its translated text, and its source, for example
`acme/shipping · de`. The source of a language file is derived from its path:

| Property | Rule |
|---|---|
| Language | Name of the directory the file is in |
| Scope | *admin* if that directory's parent is `admin` or `admin_*`, otherwise *frontend* |
| Origin | *module* if an ancestor directory has a `metadata.php`, *theme* if it has a `theme.php`, otherwise *core* |

**Ordering and duplicates.** Each key is listed once.

- Module keys come first, then theme keys, then core keys.
- If a key is defined in several files, the text shown is the module's, then the theme's, then the
  core's. Within the same origin, `de` is preferred, then `en`, then any other language.
- Frontend and admin keys are kept apart: the keys of the scope you are working in come first, and
  keys that only exist in the other scope follow, labelled `· admin` or `· frontend`. The scope is
  guessed from an `admin` or `admin_*` segment in the path or file name of the file being edited.
  In PHP, a literal `true` or `false` as the third argument of `translateString()` decides instead.

Matching ignores case, so typing `basket_` finds `BASKET_…`; the key is inserted as it is defined.

### 2. Compare translations

Compares one language file key by key with the same file in another language, for example
`Application/translations/de/lang.php` against `Application/translations/en/lang.php`.

**Open it:** right-click a language file in the project tree, in the editor or on its tab, and
choose **Compare Translations With…**. If the file exists in several other languages, pick one.

The window has one row per key and a column per language. A grey *missing* means the file does not
have the key, a grey *empty* means the key has no text.

| Control | Effect |
|---|---|
| **Add key to `<language>`** | Adds the selected keys that are missing in that file, with an empty text (`'KEY' => '',`) |
| **Remove from `<language>`** | Removes the selected keys from that file |
| **Show keys translated in both files** | Also lists keys that have a text in both files (hidden by default) |
| Double-click a row | Closes the window and opens the file with the caret in that key's text |

Details:

- Every button writes to the file immediately. Each click is one undo step. Removing is not
  confirmed first.
- A key is added without a translation on purpose, so that an untranslated text can never be
  mistaken for a translated one. Keys with an empty text stay listed until they are filled in.
- An added key is placed after the same neighbouring key as in the other file, so both files keep
  the same order. If there is no such neighbour it goes to the end of the array. Its `=>` is lined
  up with the neighbour's.
- Removing a key removes every definition of it in that file.
- Double-clicking a language column opens that file. Double-clicking the key column, or the
  column of a file that does not have the key, opens the file that does.

## Known limitations

- Only array literals are read: `$aLang = [...]` or `$aLang = array(...)`. Files that build
  `$aLang` with `array_merge()` or includes are not understood.
- `theme_options.php` and `module_options.php` are not indexed.
- Completion needs a quoted value; `ident=FOO` without quotes is not completed.
- `cust_lang.php` is ranked as an ordinary theme file. OXID loads it last, so at runtime it
  overrides module translations; the text shown in the completion popup can differ from what the
  shop renders.
- The compare window is modal: the editor cannot be used while it is open.

## Installation

Build the plugin (see below), then in PhpStorm: *Settings → Plugins → ⚙ → Install Plugin from
Disk…*, choose `build/distributions/oxidBuddy-<version>.zip`, and restart the IDE.

## Building from source

Requires JDK 21. Gradle is provided by the wrapper. The first build downloads PhpStorm as the
compile and test platform, which takes several minutes and a few gigabytes.

```bash
./gradlew buildPlugin   # build/distributions/oxidBuddy-<version>.zip
./gradlew test          # run the test suite
./gradlew verifyPlugin  # run the JetBrains Plugin Verifier, as JetBrains Marketplace does
./gradlew runIde        # start a sandbox PhpStorm with the plugin installed
```

The target PhpStorm version is `platformVersion` in `gradle.properties`.

## Releasing

1. Set `version` in `gradle.properties` and describe the release in `<change-notes>` in
   `src/main/resources/META-INF/plugin.xml`. JetBrains Marketplace rejects a version it already has.
2. Run `./gradlew test verifyPlugin`.
3. Build the signed ZIP with `./gradlew signPlugin`, or sign and upload in one go with
   `./gradlew publishPlugin`. The very first version has to be uploaded by hand on
   [JetBrains Marketplace](https://plugins.jetbrains.com/); `publishPlugin` works from the second
   version on.

Signing and publishing read their secrets from environment variables. Nothing is stored in the
repository, and `*.pem` / `*.crt` files are ignored by git.

| Variable | Content |
|---|---|
| `CERTIFICATE_CHAIN` | Certificate chain (`chain.crt`), Base64-encoded |
| `PRIVATE_KEY` | Private key (`private.pem`), Base64-encoded |
| `PRIVATE_KEY_PASSWORD` | Password of the private key |
| `PUBLISH_TOKEN` | JetBrains Marketplace token, only for `publishPlugin` |

How to create the key and certificate is described in the JetBrains
[plugin signing guide](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html). Without
these variables `./gradlew buildPlugin` still produces an unsigned ZIP.

## Project layout

```
src/main/resources/META-INF/plugin.xml    plugin descriptor: extensions and actions
src/main/kotlin/io/github/vanillathunder/oxidbuddy/translation/
    LangSource.kt                         language file detection, path -> scope / origin / language
    LangFilePsi.kt                        reads the $aLang arrays of a language file
    TranslationKeyIndex.kt                file-based index: key -> text per language file
    TranslationKeys.kt                    picks the winning definition per key and scope (cached)
    TranslationKeyCompletion.kt           builds and ranks the suggestions
    PhpTranslationKeyCompletionContributor.kt
    SmartyTranslationKeyCompletionContributor.kt
    compare/
        LangFileComparison.kt             pairs two files key by key
        LangFileEditor.kt                 adds and removes keys in a file
        TranslationCompareSession.kt      one comparison: rows, edits, navigation
        TranslationComparePanel.kt        table and buttons
        TranslationCompareDialog.kt       the window
        CompareTranslationsAction.kt      the "Compare Translations With…" menu entry
src/test/kotlin/                          tests, run headlessly against a miniature shop
```

To complete keys in further Smarty tags or PHP methods, extend `TRANSLATE_TAGS` in
`SmartyTranslationKeyCompletionContributor.kt` or `TRANSLATE_METHODS` in
`PhpTranslationKeyCompletionContributor.kt`. The preferred languages are `PREFERRED_LANGUAGES` in
`TranslationKeys.kt`.

## License

[GNU General Public License v3.0](LICENSE).

OXID Buddy is an independent project. It is not affiliated with or endorsed by OXID eSales AG or
JetBrains.
