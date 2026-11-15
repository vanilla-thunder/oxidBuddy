package io.github.vanillathunder.oxidbuddy.translation.compare

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import io.github.vanillathunder.oxidbuddy.translation.LangFiles

/** Opens the key-by-key comparison of a language file with the same file in another language. */
class CompareTranslationsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = e.project != null && file != null && LangFiles.isLangFile(file)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val siblings = LangFiles.siblings(file)
        when (siblings.size) {
            0 -> Messages.showInfoMessage(
                project,
                "No other language directory next to '${file.parent.name}' has a file named '${file.name}'.",
                "Compare Translations",
            )
            1 -> open(project, file, siblings.single())
            else -> {
                val byLanguage = siblings.associateBy { it.parent.name }
                JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(byLanguage.keys.toList())
                    .setTitle("Compare '${file.parent.name}/${file.name}' With")
                    .setItemChosenCallback { language -> open(project, file, byLanguage.getValue(language)) }
                    .createPopup()
                    .showInBestPositionFor(e.dataContext)
            }
        }
    }

    private fun open(project: Project, left: VirtualFile, right: VirtualFile) {
        TranslationCompareDialog(project, TranslationCompareSession(project, left, right)).show()
    }
}
