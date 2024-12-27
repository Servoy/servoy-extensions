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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.swing.JMenuItem;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;

/**
* The <code>Xport menu enabler</code> plugin provides tools for exporting and importing data to and from Excel files, offering flexibility in managing worksheets, templates, and data structures. It supports enabling or disabling export and import functionalities through its <code>exportEnabled</code> and <code>importEnabled</code> properties, allowing seamless integration into applications.
*
* <h2>Primary Method</h2>
* <p>
* The plugin’s primary method, <code>excelExport</code>, facilitates exporting data from specified data sources to Excel files. Users can customize the process by:
* <ul>
*   <li>Choosing data attributes</li>
*   <li>Applying templates</li>
*   <li>Setting sheet names</li>
*   <li>Defining column headers</li>
*   <li>Specifying starting rows and columns</li>
* </ul>
* This ensures structured and well-formatted Excel outputs tailored to specific requirements.
* </p>
*
* <h2>Features</h2>
* <p>
* With features like template support, custom headers, and flexible positioning, <code>excelxport</code> simplifies the interaction with Excel files. It enhances workflows by enabling dynamic data handling and output formatting, making it an essential tool for data-driven applications requiring Excel integration.
* </p>
*
* @author jblok
*/
@ServoyDocumented(publicName = ExcelXportPlugin.PLUGIN_NAME, scriptingName = "plugins." + ExcelXportPlugin.PLUGIN_NAME)
public class Enabler implements IScriptable
{
	private JMenuItem imp;
	private JMenuItem exp;

	Enabler()
	{
		//only for use in eclipse
	}

	Enabler(JMenuItem imp, JMenuItem exp)
	{
		this.imp = imp;
		this.exp = exp;
	}

	public void js_setExportEnabled(boolean b)
	{
		if (exp != null) exp.setEnabled(b);
	}

	/**
	 * Enable the export feature of this plugin.
	 *
	 * @sample
	 * plugins.excelxport.exportEnabled = true;
	 * var isEnabled = plugins.excelxport.exportEnabled;
	 *
	 * @return true if the export feature is enabled; otherwise, false.
	 */
	public boolean js_getExportEnabled()
	{
		if (exp != null)
		{
			return exp.isEnabled();
		}
		else
		{
			return false;
		}
	}

	public void js_setImportEnabled(boolean b)
	{
		if (imp != null)
		{
			imp.setEnabled(b);
		}
	}

	/**
	 * Enable the import feature of this plugin.
	 *
	 * @sample
	 * plugins.excelxport.importEnabled = true;
	 * var isEnabled = plugins.excelxport.importEnabled;
	 *
	 * @return true if the import feature is enabled; otherwise, false.
	 */
	public boolean js_getImportEnabled()
	{
		if (imp != null)
		{
			return imp.isEnabled();
		}
		else
		{
			return false;
		}
	}

	public void setEnabled(boolean b)
	{
		if (exp != null)
		{
			exp.setEnabled(b);
		}
		if (imp != null)
		{
			imp.setEnabled(b);
		}
	}

	/**
	 * Export to Excel data
	 *
	 * @sample
	 * //export in new byte array
	 * var bytes = plugins.excelxport.excelExport(forms.form1.foundset, ['id','name']);
	 * //export by adding to templateXLS in default (new) 'Servoy Data' worksheet
	 * var bytes = plugins.excelxport.excelExport(forms.form1.foundset, ['id','name'],templateXLS);
	 * //export by adding to templateXLS, in 'mySheet' worksheet, starting at default(1/1) row/column
	 * var bytes = plugins.excelxport.excelExport(forms.form1.foundset, ['id','name'],templateXLS, 'mySheet');
	  * //export by adding to templateXLS, in 'mySheet' worksheet, with column names 'ID' and 'Name' starting at default(1/1) row/column
	 * var bytes = plugins.excelxport.excelExport(forms.form1.foundset, ['id','name'],templateXLS, 'mySheet', ['ID', 'Name']);
	 * //export by adding to templateXLS, in 'mySheet' worksheet, starting at 3rd row and 5th column
	 * var bytes = plugins.excelxport.excelExport(forms.form1.foundset, ['id','name'],templateXLS, 'mySheet',3,5);
	 * * //export by adding to templateXLS, in 'mySheet' worksheet, with column names 'ID' and 'Name', starting at 3rd row and 5th column
	 * var bytes = plugins.excelxport.excelExport(forms.form1.foundset, ['id','name'],templateXLS, 'mySheet', ['ID', 'Name'], 3, 5);
	 *
	 * @param foundSet the foundset on which to export
	 * @param dataProviderIds the ids of the dataproviders
	 *
	 * @return A byte array representing the exported Excel data.
	 */
	public byte[] js_excelExport(IFoundSet foundSet, String[] dataProviderIds) throws IOException
	{
		return js_excelExport(foundSet, dataProviderIds, null, "Servoy data", Integer.valueOf(1), Integer.valueOf(1));
	}

	/**
	 * @clonedesc js_excelExport(IFoundSet, String[])
	 * @sampleas js_excelExport(IFoundSet, String[])
	 * @param foundSet the foundset on which to export
	 * @param dataProviderIds the ids of the dataproviders
	 * @param templateXLS the xls template to export in
	 *
	 * @return A byte array representing the exported Excel data.
	 */
	public byte[] js_excelExport(IFoundSet foundSet, String[] dataProviderIds, byte[] templateXLS) throws IOException
	{
		return js_excelExport(foundSet, dataProviderIds, templateXLS, "Servoy data", 1, 1);
	}

	/**
	 * @clonedesc js_excelExport(IFoundSet, String[])
	 * @sampleas js_excelExport(IFoundSet, String[])
	 * @param foundSet the foundset on which to export
	 * @param dataProviderIds the ids of the dataproviders
	 * @param templateXLS the xls template to export in
	 * @param sheetName the name of the worksheet
	 *
	 * @return A byte array representing the exported Excel data.
	 */
	public byte[] js_excelExport(IFoundSet foundSet, String[] dataProviderIds, byte[] templateXLS, String sheetName) throws IOException
	{
		return js_excelExport(foundSet, dataProviderIds, templateXLS, sheetName, Integer.valueOf(1), Integer.valueOf(1));
	}

	/**
	 * @clonedesc js_excelExport(IFoundSet, String[])
	 * @sampleas js_excelExport(IFoundSet, String[])
	 * @param foundSet the foundset on which to export
	 * @param dataProviderIds the ids of the dataproviders
	 * @param templateXLS the xls template to export in
	 * @param sheetName the name of the worksheet
	 *
	 * @return A byte array representing the exported Excel data.
	 */
	public byte[] js_excelExport(IFoundSet foundSet, String[] dataProviderIds, byte[] templateXLS, String sheetName, String[] outputColumnNames)
		throws IOException
	{
		return js_excelExport(foundSet, dataProviderIds, templateXLS, sheetName, outputColumnNames, Integer.valueOf(1), Integer.valueOf(1));
	}

	/**
	 * @clonedesc js_excelExport(IFoundSet, String[])
	 * @sampleas js_excelExport(IFoundSet, String[])
	 * @param foundSet the foundset on which to export
	 * @param dataProviderIds the ids of the dataproviders
	 * @param templateXLS the xls template to export in
	 * @param sheetName the name of the worksheet
	 * @param outputColumnNames is used to set the column headers independently from the dataprovider names
	 * @param startRow row in the foundset at which to start the export
	 *
	 * @return A byte array representing the exported Excel data.
	 */
	public byte[] js_excelExport(IFoundSet foundSet, String[] dataProviderIds, byte[] templateXLS, String sheetName, Number startRow) throws IOException
	{
		int _startRow = (startRow == null ? 1 : startRow.intValue());
		return js_excelExport(foundSet, dataProviderIds, templateXLS, sheetName, Integer.valueOf(_startRow), Integer.valueOf(1));
	}

	/**
	 * @clonedesc js_excelExport(IFoundSet, String[])
	 * @sampleas js_excelExport(IFoundSet, String[])
	 * @param foundSet the foundset on which to export
	 * @param dataProviderIds the ids of the dataproviders
	 * @param templateXLS the xls template to export in
	 * @param sheetName the name of the worksheet
	 * @param outputColumnNames is used to set the column headers independently from the dataprovider names
	 * @param startRow row in the foundset at which to start the export
	 *
	 * @return A byte array representing the exported Excel data.
	 */
	public byte[] js_excelExport(IFoundSet foundSet, String[] dataProviderIds, byte[] templateXLS, String sheetName, String[] outputColumnNames,
		Number startRow)
		throws IOException
	{
		int _startRow = (startRow == null ? 1 : startRow.intValue());
		return js_excelExport(foundSet, dataProviderIds, templateXLS, sheetName, outputColumnNames, Integer.valueOf(_startRow), Integer.valueOf(1));
	}

	/**
	 * @clonedesc js_excelExport(IFoundSet, String[])
	 * @sampleas js_excelExport(IFoundSet, String[])
	 * @param foundSet the foundset on which to export
	 * @param dataProviderIds the ids of the dataproviders
	 * @param templateXLS the xls template to export in
	 * @param sheetName the name of the worksheet
	 * @param startRow row in the foundset at which to start the export
	 * @param startColumn column in the foundset at which to start the export
	 *
	 * @return A byte array representing the exported Excel data.
	 */
	public byte[] js_excelExport(IFoundSet foundSet, String[] dataProviderIds, byte[] templateXLS, String sheetName, Number startRow, Number startColumn)
		throws IOException
	{
		return js_excelExport(foundSet, dataProviderIds, templateXLS, sheetName, null, startRow, startColumn);
	}

	/**
	 * @clonedesc js_excelExport(IFoundSet, String[])
	 * @sampleas js_excelExport(IFoundSet, String[])
	 * @param foundSet the foundset on which to export
	 * @param dataProviderIds the ids of the dataproviders
	 * @param templateXLS the xls template to export in
	 * @param sheetName the name of the worksheet
	 * @param outputColumnNames is used to set the column headers independently from the dataprovider names
	 * @param startRow row in the foundset at which to start the export
	 * @param startColumn column in the foundset at which to start the export
	 *
	 * @return A byte array representing the exported Excel data.
	 */
	public byte[] js_excelExport(IFoundSet foundSet, String[] dataProviderIds, byte[] templateXLS, String sheetName, String[] outputColumnNames,
		Number startRow, Number startColumn) throws IOException
	{
		int _startRow = (startRow == null ? 1 : startRow.intValue());
		int _startColumn = (startColumn == null ? 1 : startColumn.intValue());

		if (foundSet != null && dataProviderIds != null && dataProviderIds.length > 0)
		{
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			HSSFWorkbook wb = ExportSpecifyFilePanel.populateWb(foundSet, dataProviderIds, templateXLS, outputColumnNames, sheetName, _startRow - 1,
				_startColumn - 1);
			wb.write(buffer);
			return buffer.toByteArray();
		}
		return null;
	}

}
