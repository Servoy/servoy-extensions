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
package com.servoy.extensions.plugins.udp;

import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;

public class UDPPlugin implements IClientPlugin
{
	public static final String PLUGIN_NAME = "udp"; //$NON-NLS-1$

	private IClientPluginAccess access;
	private UDPProvider impl;
	private URL iconUrl;

	/*
	 * @see IPlugin#load()
	 */
	public void load() throws PluginException
	{
	}

	/*
	 * @see IPlugin#initialize(IApplication)
	 */
	public void initialize(IClientPluginAccess app) throws PluginException
	{
		access = app;
	}

	/*
	 * @see IPlugin#unload()
	 */
	public void unload() throws PluginException
	{
		access = null;
		impl = null;
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "UDP packets plugin"); //$NON-NLS-1$
		return props;
	}

	/*
	 * @see IPlugin#getPreferencePanels()
	 */
	public PreferencePanel[] getPreferencePanels()
	{
		return null;
	}

	/*
	 * @see IPlugin#getName()
	 */
	public String getName()
	{
		return PLUGIN_NAME;
	}

	public IScriptable getScriptObject()
	{
		if (impl == null)
		{
			impl = new UDPProvider(this);
		}
		return impl;
	}

	IClientPluginAccess getClientPluginAccess()
	{
		return access;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.j2db.plugins.IClientPlugin#getImage()
	 */
	public Icon getImage()
	{
		iconUrl = this.getClass().getResource("images/udp.png"); //$NON-NLS-1$
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		else
		{
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent evt)
	{
		if (evt.getPropertyName().equals("solution") && evt.getNewValue() == null && impl != null)
		{
			impl.js_stopSocket();
		}
	}
}
