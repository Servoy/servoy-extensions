/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */
package com.servoy.extensions.beans.dbtreeview.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.mozilla.javascript.Function;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.extensions.beans.dbtreeview.FoundSetTreeModel;
import com.servoy.extensions.beans.dbtreeview.SwingDBTree;
import com.servoy.extensions.beans.dbtreeview.SwingDBTreeView;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IRuntimeWindow;
import com.servoy.j2db.plugins.ISmartRuntimeWindow;
import com.servoy.j2db.util.DataSourceUtils;
import com.servoy.j2db.util.UIUtils;

/**
 * Class representing the smart client db tree table view
 *
 * @author gboros
 */
@ServoyClientSupport(ng = false, wc = true, sc = true)
public class SwingDBTreeTableView extends SwingDBTreeView implements ITreeTableScriptMethods
{
	private static final long serialVersionUID = 1L;

	private final AbstractTableModel treeTableModel = new AbstractTableModel()
	{
		private static final long serialVersionUID = 1L;

		public int getColumnCount()
		{
			return SwingDBTreeTableView.this.dbTreeTableView.getColumns().size() + 1;
		}

		@Override
		public String getColumnName(int column)
		{

			if (column == 0) return SwingDBTreeTableView.this.dbTreeTableView.getTreeColumnHeader();

			ArrayList<ArrayList<Column>> columns = SwingDBTreeTableView.this.dbTreeTableView.getColumns();

			if (columns.size() >= column)
			{
				String columnName = columns.get(column - 1).get(0).getHeader();

				if (columnName != null) return columnName;
			}

			return super.getColumnName(column);
		}

		public int getRowCount()
		{
			return SwingDBTreeTableView.this.tree.getRowCount();
		}

		public Object getValueAt(int rowIndex, int columnIndex)
		{
			if (columnIndex > 0)
			{

				TreePath rowTreePath = tree.getPathForRow(rowIndex);

				if (rowTreePath != null)
				{
					Object lastComp = rowTreePath.getLastPathComponent();
					if (lastComp instanceof FoundSetTreeModel.UserNode)
					{
						FoundSetTreeModel.UserNode un = (FoundSetTreeModel.UserNode)lastComp;
						ArrayList<ArrayList<Column>> columns = SwingDBTreeTableView.this.dbTreeTableView.getColumns();

						if (columns.size() >= columnIndex)
						{
							String cellValue = "";
							ArrayList<Column> sameHeaderColumns = columns.get(columnIndex - 1);
							Column column;
							for (int i = 0; i < sameHeaderColumns.size(); i++)
							{
								column = sameHeaderColumns.get(i);
								cellValue = SwingDBTreeTableView.this.bindingInfo.getText(un, column.getDataprovider(), column.getDatasource());
								if (!"".equals(cellValue)) break;
							}

							return cellValue;
						}
					}

				}
			}

			return "";

		}

		@Override
		public Class getColumnClass(int columnIndex)
		{
			if (columnIndex == 0)
			{
				return SwingDBTree.class;
			}
			else
			{
//				return super.getColumnClass(columnIndex);
				return String.class;
			}
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex)
		{
			return columnIndex == 0;
		}
	};


	private final DBTreeTableView dbTreeTableView;
	private SwingDBTreeTable treeTable;

	public SwingDBTreeTableView()
	{
		dbTreeTableView = null;
	}

	protected SwingDBTreeTableView(IClientPluginAccess application, DBTreeTableView dbTreeTableView)
	{
		super(application);
		this.dbTreeTableView = dbTreeTableView;
		// set the viewport to table
		setViewportView(treeTable = new SwingDBTreeTable(this, application));

		tree.setOpaque(false);
		tree.setTable(treeTable);
		treeTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		treeTable.getTableHeader().setReorderingAllowed(false);
		treeTable.setShowGrid(false);
		treeTable.setIntercellSpacing(new Dimension(0, 0));
		treeTable.setOpaque(false);

		if (tree.getRowHeight() < 1)
		{
			// Metal looks better like this.
			treeTable.setRowHeight(18);
		}

		treeTable.setDefaultRenderer(String.class, new DefaultTableCellRenderer()
		{
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
			{
				if (isSelected) this.setOpaque(true);
				else this.setOpaque(table.isOpaque());

				Font nodeFont = null;
				if (SwingDBTreeTableView.this.tree.getPathForRow(row).getLastPathComponent() instanceof FoundSetTreeModel.UserNode)
				{
					FoundSetTreeModel.UserNode node = (FoundSetTreeModel.UserNode)SwingDBTreeTableView.this.tree.getPathForRow(row).getLastPathComponent();
					nodeFont = bindingInfo.getFont(node);
				}

				if (nodeFont == null) nodeFont = SwingDBTreeTableView.this.getFont();

				Component cellRenderer = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				cellRenderer.setFont(nodeFont);

				return cellRenderer;
			}

		});

		treeTable.setDefaultRenderer(SwingDBTree.class, tree);
		treeTable.setDefaultEditor(SwingDBTree.class, new SwingDBTreeTableEditor(tree));


		treeTable.setModel(treeTableModel);
		treeTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		treeTable.getSelectionModel().addListSelectionListener(new ListSelectionListener()
		{

			public void valueChanged(ListSelectionEvent e)
			{
				int selectedRow = SwingDBTreeTableView.this.treeTable.getSelectedRow();
				SwingDBTreeTableView.this.tree.setSelectionRow(selectedRow);
			}
		});

		treeTable.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger()) SwingDBTreeTableView.this.mouseRightClick(e);
				else SwingDBTreeTableView.this.mouseClicked(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger()) SwingDBTreeTableView.this.mouseRightClick(e);
			}
		});
	}

	/*
	 * DBTreeTabeView's JTable component
	 */
	public JTable getTreeTable()
	{
		return treeTable;
	}

	@Override
	@Deprecated
	public void js_setRoots(Object[] vargs)
	{
		super.js_setRoots(vargs);
//		resizeTreeColumn();
	}

	@Override
	public void treeCollapsed(TreeExpansionEvent event)
	{
		super.treeCollapsed(event);
		treeTable.repaint();
//		resizeTreeColumn();
	}

	@Override
	public void treeExpanded(TreeExpansionEvent event)
	{
		super.treeExpanded(event);
		treeTable.revalidate();
		treeTable.repaint();
//		resizeTreeColumn();
	}

//	private void resizeTreeColumn()
//	{
//		treeTable.getTableHeader().getColumnModel().getColumn(0).setPreferredWidth((int)this.tree.getPreferredSize().getWidth());
//	}

	@Override
	public void setFont(Font f)
	{
		super.setFont(f);
		if (treeTable != null) treeTable.setFont(f);
	}

	@Deprecated
	public Column js_createColumn(String servername, String tablename, String header, String fieldname)
	{
		return js_createColumn(servername, tablename, header, fieldname, -1);
	}

	@Deprecated
	public Column js_createColumn(String servername, String tablename, String header, String fieldname, int preferredWidth)
	{
		return js_createColumn(DataSourceUtils.createDBTableDataSource(servername, tablename), header, fieldname, preferredWidth);
	}

	public Column js_createColumn(String datasource, String header, String fieldname)
	{
		return js_createColumn(datasource, header, fieldname, -1);
	}

	public Column js_createColumn(String datasource, String header, String fieldname, int preferredWidth)
	{
		Column column = new Column();
		column.setDBTreeTableView(dbTreeTableView);
		column.setDatasource(datasource);
		column.setPreferredWidth(preferredWidth);
		column.js_setHeader(header);
		column.js_setDataprovider(fieldname);

		if (preferredWidth != -1) treeTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

		dbTreeTableView.addColumn(column);
		treeTableModel.fireTableStructureChanged();

		return column;
	}

	public void js_removeAllColumns()
	{
		dbTreeTableView.removeAllColumns();
		treeTableModel.fireTableStructureChanged();
	}

	@Override
	public void js_refresh()
	{
		Object[] selectionPath = js_getSelectionPath();
		super.js_refresh();

		treeTableModel.fireTableDataChanged();
		js_setSelectionPath(selectionPath);
	}

	@Override
	public void js_setSelectionPath(Object[] selectionPath)
	{
		super.js_setSelectionPath(selectionPath);
		int[] selectedRows = tree.getSelectionRows();
		if (selectedRows != null && selectedRows.length > 0)
		{
			treeTable.getSelectionModel().setSelectionInterval(selectedRows[0], selectedRows[0]);
			treeTable.scrollRectToVisible(new Rectangle(treeTable.getCellRect(selectedRows[0], 0, true)));
		}
	}

	/*
	 * Tree node checkbox state listener
	 *
	 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
	 */
	@Override
	public void itemStateChanged(ItemEvent e)
	{
		int editingRow = treeTable.getSelectedRow();
		TreePath editingPath = tree.getPathForRow(editingRow);

		if (editingPath != null)
		{
			DefaultMutableTreeNode tn = (DefaultMutableTreeNode)editingPath.getLastPathComponent();
			tree.stopEditing();
			int state = e.getStateChange();
			callMethodOnCheckBoxChange(tn, state);
		}
	}

	/*
	 * bgcolor---------------------------------------------------
	 */
	@Override
	public void setBackground(Color c)
	{
		super.setBackground(c);
		if (treeTable != null)
		{
			treeTable.setBackground(c);
		}
	}

	/*
	 * fgcolor---------------------------------------------------
	 */
	@Override
	public void setForeground(Color c)
	{
		super.setForeground(c);
		if (treeTable != null)
		{
			treeTable.setForeground(c);
		}
	}

	@Override
	public void setOpaque(boolean isOpaque)
	{
		getViewport().setOpaque(isOpaque);
		if (treeTable != null)
		{
			treeTable.setOpaque(isOpaque);
		}
	}

	@Override
	public Class[] getAllReturnedTypes()
	{
		return DBTreeTableView.getAllReturnedTypes();
	}

	public void js_setTreeColumnHeader(String treeColumnHeader)
	{
		dbTreeTableView.setTreeColumnHeader(treeColumnHeader);
		treeTableModel.fireTableStructureChanged();
	}

	public void js_setTreeColumnPreferredWidth(int preferredWidth)
	{
		dbTreeTableView.setTreeColumnPreferredWidth(preferredWidth);
		treeTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		treeTableModel.fireTableStructureChanged();
	}

	@Override
	public void tableChanged(TableModelEvent e)
	{
		super.tableChanged(e);
		repaint();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDrag(org.mozilla.javascript.Function)
	 */
	@Override
	public void js_setOnDrag(Function fOnDrag)
	{
		treeTable.setOnDragCallback(fOnDrag);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDragEnd(org.mozilla.javascript.Function)
	 */
	@Override
	public void js_setOnDragEnd(Function fOnDragEnd)
	{
		treeTable.setOnDragEndCallback(fOnDragEnd);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDragOver(org.mozilla.javascript.Function)
	 */
	@Override
	public void js_setOnDragOver(Function fOnDragOver)
	{
		treeTable.setOnDragOverCallback(fOnDragOver);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDrop(org.mozilla.javascript.Function)
	 */
	@Override
	public void js_setOnDrop(Function fOnDrop)
	{
		treeTable.setOnDropCallback(fOnDrop);
	}

	@Override
	protected void mouseClicked(final MouseEvent e)
	{
		TreePath selectedPath = tree.getSelectionPath();
		if (selectedPath != null)
		{
			final DefaultMutableTreeNode tn = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
			IRuntimeWindow runtimeWindow = application.getCurrentRuntimeWindow();
			Point windowLocation = null;
			if (runtimeWindow instanceof ISmartRuntimeWindow) windowLocation = ((ISmartRuntimeWindow)runtimeWindow).getWindow().getLocationOnScreen();
			Point treeLocation = treeTable.getLocationOnScreen();
			final Point treeLocationToWindow = new Point((int)(treeLocation.getX() - windowLocation.getX()),
				(int)(treeLocation.getY() - windowLocation.getY()));

			if (e.getClickCount() == 1)
			{
				clickTimer = new Timer(UIUtils.getClickInterval(), new ActionListener()
				{
					public void actionPerformed(ActionEvent ev)
					{
						callMethodOnClick(tn, treeLocationToWindow.x + e.getX(), treeLocationToWindow.y + e.getY(),
							Integer.valueOf(treeTable.columnAtPoint(e.getPoint())));
					}
				});
				clickTimer.setRepeats(false);
				clickTimer.start();
			}
			else if (e.getClickCount() == 2)
			{
				clickTimer.stop();
				callMethod(tn);
				callMethodOnDoubleClick(tn, treeLocationToWindow.x + e.getX(), treeLocationToWindow.y + e.getY(),
					Integer.valueOf(treeTable.columnAtPoint(e.getPoint())));
			}
		}
	}

	@Override
	protected void mouseRightClick(MouseEvent e)
	{
		TreePath selectedPath = tree.getPathForRow(treeTable.rowAtPoint(e.getPoint()));
		if (selectedPath != null)
		{
			DefaultMutableTreeNode tn = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
			IRuntimeWindow runtimeWindow = application.getCurrentRuntimeWindow();
			Point windowLocation = null;
			if (runtimeWindow instanceof ISmartRuntimeWindow) windowLocation = ((ISmartRuntimeWindow)runtimeWindow).getWindow().getLocationOnScreen();
			Point treeTableLocation = treeTable.getLocationOnScreen();
			Point treeTableLocationToWindow = new Point((int)(treeTableLocation.getX() - windowLocation.getX()),
				(int)(treeTableLocation.getY() - windowLocation.getY()));

			callMethodOnRightClick(tn, treeTableLocationToWindow.x + e.getX(), treeTableLocationToWindow.y + e.getY(),
				Integer.valueOf(treeTable.columnAtPoint(e.getPoint())));
		}
	}

	public void updateColumnsWidth()
	{
		if (dbTreeTableView != null)
		{
			TableColumnModel tcm = treeTable.getColumnModel();
			int columnCount = tcm.getColumnCount();
			int preferredWidth = dbTreeTableView.getTreeColumnPreferredWidth();
			if (columnCount > 0) tcm.getColumn(0).setPreferredWidth(preferredWidth);


			ArrayList<ArrayList<Column>> columns = dbTreeTableView.getColumns();
			int columnIdx = 1;
			for (ArrayList<Column> sameHeaderColumns : columns)
			{
				preferredWidth = sameHeaderColumns.get(0).getPreferredWidth();
				if (columnIdx < columnCount) tcm.getColumn(columnIdx++).setPreferredWidth(preferredWidth);
			}
		}
	}
}

class SwingDBTreeTableEditor extends AbstractCellEditor implements TableCellEditor
{
	private static final long serialVersionUID = 1L;

	private final SwingDBTree tree;

	SwingDBTreeTableEditor(SwingDBTree tree)
	{
		this.tree = tree;
	}

	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
	{
		return tree;
	}

	public Object getCellEditorValue()
	{
		return null;
	}
}
