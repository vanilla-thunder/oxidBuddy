package io.github.vanillathunder.oxidbuddy.translation.compare

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.Action
import javax.swing.JComponent

/** The window around [TranslationComparePanel]. Every change is already written, so it only closes. */
class TranslationCompareDialog(
    project: Project,
    session: TranslationCompareSession,
) : DialogWrapper(project) {

    private val panel = TranslationComparePanel(session)

    init {
        title = "Compare Translations: ${session.left.name} (${session.language(Side.LEFT)} ↔ ${session.language(Side.RIGHT)})"
        setCancelButtonText("Close")
        // The window is modal, so it has to close before the editor can take over.
        panel.onOpenKey = { side, key ->
            close(CANCEL_EXIT_CODE)
            session.navigate(side, key)
        }
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(cancelAction)

    override fun createCenterPanel(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel.table

    override fun getDimensionServiceKey(): String = "OxidBuddy.TranslationCompare"
}
