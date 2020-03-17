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
package com.servoy.extensions.plugins.excelxport;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.servoy.j2db.Messages;
import com.servoy.j2db.util.StringComparator;
import com.servoy.j2db.util.gui.SortedComboModel;
import com.servoy.j2db.util.wizard.IWizard;
import com.servoy.j2db.util.wizard.IWizardPanel;
import com.servoy.j2db.util.wizard.IWizardState;

/**
 * @author jblok
 */
public class ImportSelectSheetPanel extends JPanel implements ActionListener, IWizardPanel
{
	protected SheetTableModel tableModel;

	protected JComboBox sheetSelect = new JComboBox();

	protected JCheckBox headerRows = new JCheckBox(Messages.getString("servoy.plugin.import.rowContainsFieldnames")); //$NON-NLS-1$
//	protected JComboBox textQualifierCombo = new JComboBox();

	protected boolean canceled = true;
	protected boolean next = true;
	private final JTable table;

	private final IWizard parent;
	private final IWizardState state;

	public ImportSelectSheetPanel(IWizard parent, IWizardState state)
	{
		this.parent = parent;
		this.state = state;
		setName("SelectSheetPanel"); //$NON-NLS-1$
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setLayout(new BorderLayout());

		table = new JTable();

		table.setPreferredScrollableViewportSize(new Dimension(500, 70));

		//Create the scroll pane and add the table to it.
		JScrollPane scrollPane = new JScrollPane(table);

		// Lay out topmost Part pane
		JPanel formatPanel = new JPanel();
		formatPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		formatPanel.setBorder(new TitledBorder(BorderFactory.createEtchedBorder(), Messages.getString("servoy.plugin.import.chooseSheet"))); //$NON-NLS-1$
		formatPanel.add(sheetSelect);
		sheetSelect.addActionListener(this);
		formatPanel.add(headerRows);
		headerRows.setActionCommand("UseHeaderRows"); //$NON-NLS-1$
		headerRows.addActionListener(this);

		//Lay out the master content pane.
		add(formatPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	public void actionPerformed(ActionEvent event)
	{
		String command = event.getActionCommand();
		if (command.equals("UseHeaderRows")) //$NON-NLS-1$
		{
			if (tableModel != null) tableModel.setUseHeaderRow(headerRows.isSelected());
		}
		else if (event.getSource() == sheetSelect)
		{
			if (sheetSelect.getSelectedItem() != null)
			{
				HSSFWorkbook wb = (HSSFWorkbook)state.getProperty("workbook"); //$NON-NLS-1$
				HSSFSheet sheet = wb.getSheetAt(wb.getSheetIndex(sheetSelect.getSelectedItem().toString()));
				tableModel = new SheetTableModel(sheet);
				tableModel.setUseHeaderRow(headerRows.isSelected());
				table.setModel(tableModel);
			}
		}
		table.sizeColumnsToFit(JTable.AUTO_RESIZE_ALL_COLUMNS);
		revalidate();
		repaint();
	}

	public AbstractTableModel getModel()
	{
		return tableModel;
	}

	public String getNextPanelName()
	{
		return "SpecifyDestinationPanel"; //$NON-NLS-1$
	}

	public boolean isDone()
	{
		if (tableModel.getRowCount() == 0) return false;
		state.setProperty("data", tableModel); //$NON-NLS-1$
		return true;
	}

	public Runnable needsToRunFirst(final boolean forward)
	{
		return new Runnable()
		{
			public void run()
			{
				try
				{
					parent.blockGUI(Messages.getString("servoy.plugin.import.status.organizingData")); //$NON-NLS-1$
					if (forward)
					{
						HSSFWorkbook wb = (HSSFWorkbook)state.getProperty("workbook"); //$NON-NLS-1$
						SortedComboModel dcm = new SortedComboModel(StringComparator.INSTANCE);
						for (int i = 0; i < wb.getNumberOfSheets(); i++)
						{
							dcm.add(wb.getSheetName(i));
						}
						sheetSelect.setModel(dcm);

						HSSFSheet sheet = wb.getSheetAt(0);//take first default
						tableModel = new SheetTableModel(sheet);
						tableModel.setUseHeaderRow(headerRows.isSelected());
						table.setModel(tableModel);
					}
				}
				finally
				{
					parent.releaseGUI();
				}
			}
		};
	}
}

class SheetTableModel extends AbstractTableModel
{
	private final HSSFSheet sheet;

	SheetTableModel(HSSFSheet sheet)
	{
		this.sheet = sheet;
	}

	private short csize = 0;

	public int getColumnCount()
	{
		if (csize == 0)
		{
			short maxcsize = 0;
			for (int i = 0; i < 50; i++)//test 10 rows
			{
				HSSFRow row = sheet.getRow(i);
				if (row != null)
				{
					HSSFCell cell;
					short j = 0;
					short skipped = 0;
					for (; j < 100; j++)//test at least 100 columns (to overcome empty columns)
					{
						cell = row.getCell(j);
						if (cell != null)
						{
							maxcsize++;
							maxcsize += skipped;
							skipped = 0;
						}
						else
						{
							skipped++;
						}
					}
					while (j > 0 && (cell = row.getCell(j)) != null)//add if there are even more
					{
						maxcsize++;
						j++;
					}
					if (csize < maxcsize)
					{
						csize = maxcsize;
					}
					maxcsize = 0;
				}
			}
		}
		return csize;
	}

	@Override
	public String getColumnName(int column)
	{
		Object obj = getValueAt(-1, column);
		if (obj == null)
		{
			if (column < getColumnCount())
			{
				return "" + column; //$NON-NLS-1$
			}
			else
			{
				return "";
			}
		}
		else
		{
			return obj.toString();
		}
	}

	private boolean useHeaderRow = false;

	public void setUseHeaderRow(boolean b)
	{
		useHeaderRow = b;
		fireTableStructureChanged();
	}

	public int getRowCount()
	{
		if (useHeaderRow)
		{
			return sheet.getLastRowNum();
		}
		else
		{
			return sheet.getLastRowNum() + 1;
		}
	}

	public Object getValueAt(int r, int c)
	{
		if (r == -1)
		{
			r = 0;
		}
		else if (useHeaderRow)
		{
			r++;
		}
		HSSFRow row = sheet.getRow(r);
		if (row != null)
		{
			HSSFCell cell = row.getCell(c);
			if (cell != null)
			{
				switch (cell.getCellType())
				{
					case NUMERIC :
						Number d = new Double(cell.getNumericCellValue());
						if (((int)d.doubleValue()) == Math.ceil(d.doubleValue()))
						{
							d = new Integer(d.intValue());
						}
						return d;
//					case HSSFCell.CELL_TYPE_NUMERIC:
//					return cell.getStringCellValue();

					default :
						return cell.getStringCellValue();
				}
			}
		}
		return null;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex)
	{
		return false;
	}
}
