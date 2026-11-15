package io.github.vanillathunder.oxidbuddy.translation.compare

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel

/**
 * One language file in two languages side by side, one row per translation key. The buttons act on
 * the selected rows and write to the files right away; a double-click opens the key in its file.
 */
class TranslationComparePanel(private val session: TranslationCompareSession) : JPanel(BorderLayout(0, JBUI.scale(8))) {

    private val leftLanguage = session.language(Side.LEFT)
    private val rightLanguage = session.language(Side.RIGHT)

    /** Called with the side and key to open when a row is double-clicked. */
    var onOpenKey: ((Side, String) -> Unit)? = null

    private val model = RowsModel()
    val table = JBTable(model)
    private val showAll = JBCheckBox("Show keys translated in both files")

    private val copyToRight = button("Add key to $rightLanguage", AllIcons.Diff.ArrowRight) {
        session.copyKeys(selectedKeys { it.state == RowState.LEFT_ONLY }, Side.RIGHT)
    }
    private val copyToLeft = button("Add key to $leftLanguage", AllIcons.Diff.Arrow) {
        session.copyKeys(selectedKeys { it.state == RowState.RIGHT_ONLY }, Side.LEFT)
    }
    private val removeLeft = button("Remove from $leftLanguage", AllIcons.General.Remove) {
        session.removeKeys(selectedKeys { it.left != null }, Side.LEFT)
    }
    private val removeRight = button("Remove from $rightLanguage", AllIcons.General.Remove) {
        session.removeKeys(selectedKeys { it.right != null }, Side.RIGHT)
    }

    init {
        table.setDefaultRenderer(Any::class.java, TextRenderer())
        table.emptyText.text = "No differences"
        table.selectionModel.addListSelectionListener { updateButtons() }
        showAll.addActionListener { reload() }
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val row = table.rowAtPoint(event.point)
                if (row < 0) return false
                openKey(row, table.columnAtPoint(event.point))
                return true
            }
        }.installOn(table)

        val controls = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
        listOf(copyToRight, copyToLeft, removeLeft, removeRight).forEach(controls::add)
        controls.add(showAll)

        add(controls, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
        preferredSize = JBUI.size(1000, 600)

        reload()
    }

    private fun button(text: String, icon: Icon, action: () -> Unit): JButton =
        JButton(text, icon).apply {
            addActionListener {
                action()
                reload()
            }
        }

    /**
     * Opens the key of [rowIndex] in the file whose column was double-clicked. The key column, and
     * a file that does not have the key, lead to the file that does (the left one if both do).
     */
    fun openKey(rowIndex: Int, column: Int) {
        val row = model.rows.getOrNull(rowIndex) ?: return
        val side = when {
            column == RIGHT_COLUMN && row.right != null -> Side.RIGHT
            row.left != null -> Side.LEFT
            else -> Side.RIGHT
        }
        onOpenKey?.invoke(side, row.key)
    }

    private fun selectedKeys(filter: (CompareRow) -> Boolean): List<String> =
        table.selectedRows.map { model.rows[it] }.filter(filter).map { it.key }

    /** Re-reads both files and keeps the selection on the same position. */
    private fun reload() {
        val selected = table.selectedRow
        model.rows = session.rows(showAll.isSelected)
        model.fireTableDataChanged()
        if (model.rows.isNotEmpty() && selected >= 0) {
            val row = selected.coerceAtMost(model.rows.lastIndex)
            table.setRowSelectionInterval(row, row)
        }
        updateButtons()
    }

    private fun updateButtons() {
        val selected = table.selectedRows.filter { it < model.rows.size }.map { model.rows[it] }
        copyToRight.isEnabled = selected.any { it.state == RowState.LEFT_ONLY }
        copyToLeft.isEnabled = selected.any { it.state == RowState.RIGHT_ONLY }
        removeLeft.isEnabled = selected.any { it.left != null }
        removeRight.isEnabled = selected.any { it.right != null }
    }

    private inner class RowsModel : AbstractTableModel() {
        var rows: List<CompareRow> = emptyList()

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = 3

        override fun getColumnName(column: Int): String = when (column) {
            0 -> "Key"
            1 -> leftLanguage
            else -> rightLanguage
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = when (columnIndex) {
            0 -> rows[rowIndex].key
            1 -> rows[rowIndex].left
            else -> rows[rowIndex].right
        }
    }

    private companion object {
        const val RIGHT_COLUMN = 2
    }

    /** Null is a key missing in that file, an empty string a key without a text. */
    private class TextRenderer : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(
            table: JTable,
            value: Any?,
            selected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ) {
            when (val text = value as String?) {
                null -> append("missing", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                "" -> append("empty", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                else -> {
                    append(text)
                    toolTipText = text
                }
            }
        }
    }
}
