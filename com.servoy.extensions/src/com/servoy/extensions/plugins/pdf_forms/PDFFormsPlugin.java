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

import java.beans.PropertyChangeEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.swing.Icon;

import com.servoy.extensions.plugins.pdf_forms.servlets.PDFServlet;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;

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
 *
 * <p><b>Configuration Properties:</b></p>
 *
 * <ul>
 * <li><code>pdf_forms_plugin_template_location</code>: The url to retrieve the pdf templates(using file name from database)</li>
 * <li><code>pdf_forms_plugin_servername</code>: The name of the server to locate the required pdf_form_values,pdf_templates,pdf_actions SQL tabels</li>
 * </ul>
 *
 * @author jblok
 */
@ServoyDocumented(publicName = "pdf_forms", scriptingName = "plugins.pdf_forms")
public class PDFFormsPlugin implements IServerPlugin, IClientPlugin
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

	/*
	 * (non-Javadoc)
	 *
	 * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
	 */
	@Override
	public void propertyChange(PropertyChangeEvent evt)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.j2db.scripting.IScriptableProvider#getScriptObject()
	 */
	@Override
	public IScriptable getScriptObject()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.j2db.plugins.IClientPlugin#initialize(com.servoy.j2db.plugins.IClientPluginAccess)
	 */
	@Override
	public void initialize(IClientPluginAccess app) throws PluginException
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.j2db.plugins.IClientPlugin#getName()
	 */
	@Override
	public String getName()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.j2db.plugins.IClientPlugin#getImage()
	 */
	@Override
	public Icon getImage()
	{
		// TODO Auto-generated method stub
		return null;
	}
}
