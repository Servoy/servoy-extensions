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

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>DataProviderExport</code> class facilitates the customization of
 * data exports by allowing the setting of formats, headers, and value lists for
 * data providers. It is designed for seamless integration with export tools.</p>
 *
 * <h2>Functionality</h2>
 *
 * <p>The <code>setFormat(format)</code> method applies a specific format to the
 * data provider's value, enabling control over date, number, or string
 * representations. Headers can be customized using
 * <code>setHeaderText(headerText)</code>, which overrides the default data
 * provider name in the export file. The <code>setValueList(valuelistName)</code>
 * method maps value lists to data providers, resolving display values for more
 * readable exports.</p>
 *
 * @author lvostinar
 */
@ServoyDocumented(scriptingName = "DataProviderExport")
@ServoyClientSupport(ng = true, wc = true, sc = true)
public class DataProviderExport implements IScriptable
{
	final String dataprovider;
	String headerText;
	String valuelistName;
	String format;

	/**
	 * @param dataprovider
	 */
	public DataProviderExport(String dataprovider)
	{
		this.dataprovider = dataprovider;
	}

	/**
	 * Set format for dataprovider value.
	 *
	 * @sample
	 * var exporter = plugins.textxport.createExporter(forms.form1.foundset,';',false);
	 * exporter.addDataProvider('mydate').setFormat('yyyy/dd/MM');
	 * var content = exporter.textExport();
	 *
	 * @param format the dataprovider format
	 *
	 * @return dataprovider export object
	 */
	public DataProviderExport js_setFormat(String format)
	{
		this.format = format;
		return this;
	}

	/**
	 * Set header text for dataprovider value. If no header text is set and exportHeader is true dataprovider will be used as header text.
	 *
	 * @sample
	 * var exporter = plugins.textxport.createExporter(forms.form1.foundset,';',true);
	 * exporter.addDataProvider('orderid').setHeaderText('Order ID');
	 * var content = exporter.textExport();
	 *
	 * @param headerText the header text
	 *
	 * @return dataprovider export object
	 */
	public DataProviderExport js_setHeaderText(String headerText)
	{
		this.headerText = headerText;
		return this;
	}

	/**
	 * Set valuelist to resolve display value for dataprovider.
	 *
	 * @sample
	 * var exporter = plugins.textxport.createExporter(forms.form1.foundset,';',true);
	 * exporter.addDataProvider('item_id').setValueList('myvaluelist');
	 * var content = exporter.textExport();
	 *
	 * @param valuelistName the valuelist name
	 *
	 * @return dataprovider export object
	 */
	public DataProviderExport js_setValueList(String valuelistName)
	{
		this.valuelistName = valuelistName;
		return this;
	}

}
