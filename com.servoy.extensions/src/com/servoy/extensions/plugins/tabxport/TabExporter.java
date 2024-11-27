/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2017 Servoy BV

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

package com.servoy.extensions.plugins.tabxport;

import java.util.ArrayList;
import java.util.List;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>TabExporter</code> plugin enables exporting data from a Servoy <code>FoundSet</code> into
 * text-based formats such as CSV or tab-separated files. This is useful for generating reports or sharing
 * data with external systems.</p>
 *
 * <p>The <code>addDataProvider</code> method allows specifying the data providers (columns) from the
 * <code>FoundSet</code> to include in the export. Each column can be customized with attributes such as
 * header text, value lists, or date formats, providing flexibility in how data is represented.</p>
 *
 * <p>The <code>textExport</code> method generates the export as a text string based on the defined
 * configuration. This string can then be saved to a file or used directly for sharing or further processing.</p>
 *
 * @author lvostinar
 *
 */
@ServoyDocumented(scriptingName = "TabExporter")
@ServoyClientSupport(ng = true, wc = true, sc = true)
public class TabExporter implements IScriptable
{
	private final IApplication application;
	private final IFoundSet foundSet;
	private final String separator;
	private final boolean exportHeader;
	private final List<DataProviderExport> dataproviders = new ArrayList<DataProviderExport>();

	public TabExporter(IApplication application, IFoundSet foundSet, String separator, boolean exportHeader)
	{
		this.application = application;
		this.foundSet = foundSet;
		this.separator = separator != null ? separator : "\t";
		this.exportHeader = exportHeader;
	}

	/**
	 * Add a dataprovider from specified foundset to export.
	 *
	 * @sample
	 * var exporter = plugins.textxport.createExporter(forms.form1.foundset,';',true);
	 * exporter.addDataProvider('orderid').setHeaderText('Order ID');
	 * exporter.addDataProvider('item_id').setValueList('myvaluelist');
	 * exporter.addDataProvider('mydate').setFormat('yyyy/dd/MM');
	 * var content = exporter.textExport();
	 *
	 * @param dataprovider The dataprovider string to add as a column to export
	 *
	 * @return dataprovider export object
	 */
	public DataProviderExport js_addDataProvider(String dataprovider)
	{
		DataProviderExport dpExport = new DataProviderExport(dataprovider);
		dataproviders.add(dpExport);
		return dpExport;
	}

	/**
	 * Export to text 'separated value' data (*.tab/*.csv), based on values set on exporter
	 *
	 * @sample
	 * var exporter = plugins.textxport.createExporter(forms.form1.foundset,';',true);
	 * exporter.addDataProvider('orderid').setHeaderText('Order ID');
	 * exporter.addDataProvider('item_id').setValueList('myvaluelist');
	 * exporter.addDataProvider('mydate').setFormat('yyyy/dd/MM');
	 * var content = exporter.textExport();
	 *
	 * @return exported text
	 */
	public String js_textExport()
	{
		if (foundSet != null && !dataproviders.isEmpty())
		{
			StringBuffer fileData = new StringBuffer();
			if (exportHeader)
			{
				String[] headers = new String[dataproviders.size()];
				for (int i = 0; i < dataproviders.size(); i++)
				{
					String headerText = dataproviders.get(i).headerText;
					if (headerText != null)
					{
						headers[i] = application.getI18NMessageIfPrefixed(headerText);
					}
					else
					{
						headers[i] = dataproviders.get(i).dataprovider;
					}
				}
				fileData.insert(0, ExportSpecifyFilePanel.createHeader(headers, separator));
			}
			String[] dataProviderIds = new String[dataproviders.size()];
			String[] formats = new String[dataproviders.size()];
			String[] valuelists = new String[dataproviders.size()];
			for (int i = 0; i < dataproviders.size(); i++)
			{
				DataProviderExport dpExport = dataproviders.get(i);
				dataProviderIds[i] = dpExport.dataprovider;
				formats[i] = dpExport.format;
				valuelists[i] = dpExport.valuelistName;
			}
			fileData.append(ExportSpecifyFilePanel.populateFileData(application, foundSet, dataProviderIds, separator, formats, valuelists));

			return fileData.toString();
		}
		return null;
	}

}
