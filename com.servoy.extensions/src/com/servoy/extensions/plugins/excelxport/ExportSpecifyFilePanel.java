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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.servoy.extensions.plugins.excelxport.ExportSpecifyDestinationPanel.DataProviderWithLabel;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.Messages;
import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.dataprocessing.IRecord;
import com.servoy.j2db.util.gui.FileNameSuggestionFileChooser;
import com.servoy.j2db.util.wizard.IWizard;
import com.servoy.j2db.util.wizard.IWizardPanel;
import com.servoy.j2db.util.wizard.IWizardState;

/**
 * @author jblok
 */
public class ExportSpecifyFilePanel extends JPanel implements ActionListener, IWizardPanel
{
	private final IApplication application;
	private HSSFWorkbook wb;
	private final IWizard parent;
	private final IWizardState state;
//	private JCheckBox header;
	private final JButton browse;

	public ExportSpecifyFilePanel(IWizard parent, IWizardState state, IApplication application)
	{
		this.parent = parent;
		this.state = state;
		this.application = application;
		setName("SpecifyFilePanel"); //$NON-NLS-1$
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setLayout(new BorderLayout(10, 10));

		JPanel fieldPanel = new JPanel();
//		fieldPanel.setPreferredSize(new Dimension(500,20));
//		fieldPanel.setMaximumSize(new Dimension(500,20));
//		fieldPanel.setSize(new Dimension(500,20));
		fieldPanel.setLayout(new BoxLayout(fieldPanel, BoxLayout.X_AXIS));
		fieldPanel.add(Box.createHorizontalGlue());
		fieldPanel.setBorder(new TitledBorder(BorderFactory.createEtchedBorder(), Messages.getString("servoy.plugin.exportImport.specifyFileTitle"))); //$NON-NLS-1$

		browse = new JButton(Messages.getString("servoy.button.browse")); //$NON-NLS-1$
		browse.setActionCommand("browse"); //$NON-NLS-1$
		browse.addActionListener(this);

		JLabel label = new JLabel(Messages.getString("servoy.plugin.exportImport.specifyFileLabel"), SwingConstants.LEFT); //$NON-NLS-1$

		fieldPanel.add(label);
		fieldPanel.add(Box.createRigidArea(new Dimension(10, 0)));
		fieldPanel.add(browse);
		fieldPanel.add(Box.createHorizontalGlue());

		add(fieldPanel, BorderLayout.CENTER);

//		JPanel titlePanel = new JPanel();
//		header = new JCheckBox("Export header");
//		titlePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
//		titlePanel.add(header);
//		titlePanel.setBorder(new TitledBorder(BorderFactory.createEtchedBorder(), "File Options"));
//		add(titlePanel,BorderLayout.NORTH);

		setPreferredSize(new Dimension(550, 400));
	}

	public void actionPerformed(ActionEvent event)
	{
		String command = event.getActionCommand();
		if (command.equals("browse")) browse(); //$NON-NLS-1$
	}

	private void browse()
	{
		try
		{
			boolean suc6 = false;
			FileNameSuggestionFileChooser fc = new FileNameSuggestionFileChooser();
			String fName = "export.xls"; //$NON-NLS-1$
			fc.suggestFileName(fName);
			int returnVal = fc.showSaveDialog(parent.getMainApplicationWindow());
			if (returnVal == JFileChooser.APPROVE_OPTION)
			{
				// Write the output to a file
				FileOutputStream fileOut = new FileOutputStream(fc.getSelectedFile());
				wb.write(fileOut);
				fileOut.close();
				suc6 = true;

				wb = null;
				if (suc6) JOptionPane.showMessageDialog(this, Messages.getString("servoy.plugin.export.success", new Object[] { new Integer(rows) })); //$NON-NLS-1$
				browse.setEnabled(false);
			}
		}
		catch (Exception ex)
		{
			parent.reportError(Messages.getString("servoy.plugin.exportImport.fileSelect.exception"), ex); //$NON-NLS-1$
		}
	}

	public String getNextPanelName()
	{
		return null;//"TransferExportPanel";
	}

	public boolean isDone()
	{
		return (wb == null && !browse.isEnabled());
	}

	private int rows;

	public Runnable needsToRunFirst(boolean forward)
	{
		browse.setEnabled(forward);

		return new Runnable()
		{
			public void run()
			{
				parent.blockGUI(Messages.getString("servoy.plugin.export.status.loadingData")); //$NON-NLS-1$
				try
				{

					DefaultListModel dlm = (DefaultListModel)state.getProperty("dataProviderIDs"); //$NON-NLS-1$

					String[] dataProviders = new String[dlm.getSize()];
					for (int i = 0; i < dlm.getSize(); i++)
					{
						dataProviders[i] = ((DataProviderWithLabel)dlm.get(i)).dataProvider.getDataProviderID();
					}
					IFoundSet data = (IFoundSet)state.getProperty("foundset"); //$NON-NLS-1$
					wb = populateWb(data, dataProviders, null, null, null, 0, 0);
					rows = data.getSize();
				}
				catch (Exception ex)
				{
					rows = 0;
					parent.reportError(Messages.getString("servoy.plugin.export.exception"), ex); //$NON-NLS-1$
				}
				finally
				{
					parent.releaseGUI();
				}
			}
		};
	}

	public static HSSFWorkbook populateWb(IFoundSet foundSet, String[] dataProviders, byte[] templateXLS, String[] outputColumnNames, String sheetName,
		int startRow, int startColumn) throws IOException
	{
		HSSFWorkbook hwb;
		if (templateXLS == null)
		{
			hwb = new HSSFWorkbook();
		}
		else
		{
			InputStream buff = new ByteArrayInputStream(templateXLS);
			hwb = new HSSFWorkbook(buff);
		}
		if (sheetName == null) sheetName = "Servoy Data";
		HSSFSheet sheet = hwb.getSheet(sheetName);
		if (sheet == null) sheet = hwb.createSheet(sheetName);
		sheet.setActive(true);

		if (outputColumnNames != null && outputColumnNames.length != dataProviders.length)
		{
			throw new RuntimeException("The arrays 'output column names' and 'data provider ids' must have the same length."); //$NON-NLS-1$
		}
		String[] columnNames = outputColumnNames != null ? outputColumnNames : dataProviders;
		HSSFRow header = sheet.createRow(startRow);
		for (int k = 0; k < columnNames.length; k++)
		{
			HSSFCell cell = header.createCell(k + startColumn);
			cell.setCellValue(columnNames[k]);
		}
		HSSFCellStyle[] cellStyles = new HSSFCellStyle[dataProviders.length];
		for (int i = 0; i < foundSet.getSize(); i++)
		{
			HSSFRow row = sheet.createRow(i + 1 + startRow);
			IRecord s = foundSet.getRecord(i);
			for (int k = 0; k < dataProviders.length; k++)
			{
				HSSFCell cell = row.createCell(k + startColumn);

				Object obj = s.getValue(dataProviders[k]);
				if (obj instanceof Date)
				{
					if (cellStyles[k] == null)
					{
						HSSFCellStyle cellStyle = hwb.createCellStyle();
						cellStyle.setDataFormat((short)16);
						cellStyles[k] = cellStyle;
					}
					cell.setCellValue((Date)obj);
					cell.setCellStyle(cellStyles[k]);
				}
				else if (obj instanceof String)
				{
					cell.setCellValue((String)obj);
				}
				else if (obj instanceof Number)
				{
					cell.setCellValue(((Number)obj).doubleValue());
				}
				else
				{
					cell.setCellValue(""); //$NON-NLS-1$
				}
			}
		}

		return hwb;
	}
}
