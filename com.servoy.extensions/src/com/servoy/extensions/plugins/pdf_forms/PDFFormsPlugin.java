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
package com.servoy.extensions.plugins.pdf_forms;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.servoy.extensions.plugins.pdf_forms.servlets.PDFServlet;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;

/**
 * <p>The <code>PDFFormsPlugin</code> is a server plugin designed to handle PDF forms in the Servoy
 * environment. It integrates with a Servoy server by registering a web service named
 * <code>pdf_forms</code> through the <code>PDFServlet</code>. The plugin allows server-side
 * operations to manage PDF templates, values, and actions stored in SQL tables.</p>
 *
 * <p>The plugin provides configuration options via required properties. These include
 * <code>SERVER_NAME_PROPERTY</code>, which specifies the server used to locate SQL tables, and
 * <code>TEMPLATE_LOCATION_PROPERTY</code>, which defines the URL for retrieving PDF templates.
 * Additionally, it allows customizable plugin properties, such as <code>DISPLAY_NAME</code>, set
 * to "PDF Forms Plugin."</p>
 *
 * <p>The plugin lifecycle is defined by methods like <code>initialize()</code> for setup,
 * <code>load()</code> and <code>unload()</code> for resource management, and
 * <code>getProperties()</code> for exposing its configuration details.</p>
 *
 * @author jblok
 */
@ServoyDocumented
public class PDFFormsPlugin implements IServerPlugin
{
	public void initialize(IServerAccess app) throws PluginException
	{
		app.registerWebService("pdf_forms", new PDFServlet(app)); //$NON-NLS-1$
	}

	public PreferencePanel[] getPreferencePanels()
	{
		return null;
	}

	public Map getRequiredPropertyNames()
	{
		HashMap req = new HashMap();
		req.put(PDFServlet.SERVER_NAME_PROPERTY, "The name of the server to locate the required pdf_form_values,pdf_templates,pdf_actions SQL tabels"); //$NON-NLS-1$
		req.put(PDFServlet.TEMPLATE_LOCATION_PROPERTY, "The url to retrieve the pdf templates(using file name from database)"); //$NON-NLS-1$
		return req;
	}

	public void load() throws PluginException
	{
	}

	public void unload() throws PluginException
	{
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "PDF Forms Plugin"); //$NON-NLS-1$
		return props;
	}
}
