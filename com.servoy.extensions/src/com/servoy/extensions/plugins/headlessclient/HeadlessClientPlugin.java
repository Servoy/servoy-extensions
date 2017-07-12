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
package com.servoy.extensions.plugins.headlessclient;

import java.beans.PropertyChangeEvent;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.serialize.JSONConverter;

public class HeadlessClientPlugin implements IClientPlugin
{
	public static final String PLUGIN_NAME = "headlessclient"; //$NON-NLS-1$

	private IClientPluginAccess access;
	private HeadlessClientProvider impl;
	private JSONConverter jsonConverter;

	public PreferencePanel[] getPreferencePanels()
	{
		return null;//none
	}

	public String getName()
	{
		return PLUGIN_NAME;
	}

	public Icon getImage()
	{
		java.net.URL iconUrl = this.getClass().getResource("images/headlessclient.png"); //$NON-NLS-1$
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		else
		{
			return null;
		}
	}

	public IScriptable getScriptObject()
	{
		if (impl == null)
		{
			impl = new HeadlessClientProvider(this);
		}
		return impl;
	}

	public void initialize(IClientPluginAccess app) throws PluginException
	{
		access = app;
	}

	public Properties getProperties()
	{
		return null;
	}

	public void load() throws PluginException
	{
	}

	public void unload() throws PluginException
	{
	}

	public void propertyChange(PropertyChangeEvent evt)
	{
	}

	public IClientPluginAccess getPluginAccess()
	{
		return access;
	}

	public JSONConverter getJSONConverter()
	{
		if (jsonConverter == null)
		{
			jsonConverter = new JSONConverter(access.getDatabaseManager());
		}
		return jsonConverter;
	}
}
