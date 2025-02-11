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
package com.servoy.extensions.plugins.tabxport;

import javax.swing.JMenuItem;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>TextXport</code> plugin provides functionality for exporting data from a
 * foundset into text-based formats like <code>.tab</code> or <code>.csv</code>, enabling
 * flexible data management. Exports can be customized with separators and headers depending
 * on requirements.</p>
 *
 * <p>TextXport supports two main approaches for exporting data. The <code>createExporter</code>
 * method allows for setting up complex export configurations, while <code>textExport</code>
 * methods provide simpler, direct options for exporting with various levels of customization.</p>
 *
 * <p>The <code>createExporter</code> method generates an export setup for a specified foundset,
 * allowing configuration of the data separator and the inclusion of headers. This is useful for
 * more advanced export workflows. The <code>textExport</code> methods perform direct exports to
 * text-separated formats, such as tab-separated or comma-separated values. These methods allow
 * specifying the foundset, data providers, separator, and header options to customize the
 * exported output.</p>
 *
 * @author jblok
 */
@ServoyDocumented(publicName = TextXportPlugin.PLUGIN_NAME, scriptingName = "plugins." + TextXportPlugin.PLUGIN_NAME)
@ServoyClientSupport(ng = true, wc = true, sc = true)
public class Enabler implements IReturnedTypesProvider, IScriptable
{
	private JMenuItem imp;
	private JMenuItem exp;
	private IApplication application;

	public Enabler()
	{
		// only used in eclipse and when docs want to look at IReturnedTypesProvider.getAllReturnedTypes()
	}

	Enabler(JMenuItem imp, JMenuItem exp, IApplication application)
	{
		this.imp = imp;
		this.exp = exp;
		this.application = application;
	}

	@Override
	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { TabExporter.class, DataProviderExport.class };
	}

	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public void js_setExportEnabled(boolean b)
	{
		if (exp != null) exp.setEnabled(b);
	}

	/**
	 * Enable the export feature of this plugin.
	 *
	 * @sample
	 * plugins.textxport.exportEnabled = true;
	 * var isEnabled = plugins.textxport.exportEnabled;
	 *
	 * @return true if the export feature is enabled; otherwise, false.
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
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

	@ServoyClientSupport(ng = false, wc = false, sc = true)
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
	 * plugins.textxport.importEnabled = true;
	 * var isEnabled = plugins.textxport.importEnabled;
	 *
	 * @return true if the import feature is enabled; otherwise, false.
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
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
	 * Export to text 'separated value' data (*.tab/*.csv)
	 *
	 * @sample
	 * //export with default separator(tab) and no header
	 * var dataToBeWritten = plugins.textxport.textExport(forms.form1.foundset,['id','name']);
	 *
	 * @param foundSet the foundset to export with
	 * @param dataProviderIds the ids of the dataproviders
	 *
	 * @return A String containing the exported text data with default separator (tab) and no header.
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public String js_textExport(IFoundSet foundSet, String[] dataProviderIds)
	{
		return js_textExport(foundSet, dataProviderIds, "\t", false); //$NON-NLS-1$
	}


	/**
	 * Export to text 'separated value' data (*.tab/*.csv)
	 *
	 * @sample
	 * //export with ';' separator and no header
	 * var dataToBeWritten = plugins.textxport.textExport(forms.form1.foundset,['id','name'],';');
	 *
	 * @param foundSet the foundset to export with
	 * @param dataProviderIds the ids of the dataproviders
	 * @param separator the separator of the data
	 *
	 * @return A string containing the exported data in the specified text-separated format.
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public String js_textExport(IFoundSet foundSet, String[] dataProviderIds, String separator)
	{
		return js_textExport(foundSet, dataProviderIds, separator, false);
	}


	/**
	 * Export to text 'separated value' data (*.tab/*.csv)
	 *
	 * @sample
	 * //export with ';' separator and header
	 * var dataToBeWritten = plugins.textxport.textExport(forms.form1.foundset,['id','name'],';',true);
	 *
	 * @param foundSet the foundset to export with
	 * @param dataProviderIds the ids of the dataproviders
	 * @param separator the separator of the data
	 * @param exportHeader true for exporting with the table header, false for not
	 *
	 * @return A String containing the exported text data with the specified separator and header (if exportHeader is true).
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public String js_textExport(IFoundSet foundSet, String[] dataProviderIds, String separator, boolean exportHeader)
	{
		if (foundSet != null && dataProviderIds != null && dataProviderIds.length > 0)
		{
			StringBuffer fileData = new StringBuffer();
			if (exportHeader) fileData.insert(0, ExportSpecifyFilePanel.createHeader(dataProviderIds, separator));
			fileData.append(ExportSpecifyFilePanel.populateFileData(application, foundSet, dataProviderIds, separator, null, null));

			return fileData.toString();
		}
		return null;
	}

	/**
	 * Create exporter for easier export set up. Can either use this method (for more complex exports) or textExport(...) API
	 *
	 * @sample
	 * //export with ';' separator and no header
	 * var exporter = plugins.textxport.createExporter(forms.form1.foundset,';',false);
	 *
	 * @param foundSet the foundset to export with
	 * @param separator the separator of the data
	 * @param exportHeader export a header
	 *
	 * @return A TabExporter object for easier export setup.
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public TabExporter js_createExporter(IFoundSet foundSet, String separator, boolean exportHeader)
	{
		return new TabExporter(application, foundSet, separator, exportHeader);
	}
}
