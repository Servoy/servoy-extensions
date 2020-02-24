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
package com.servoy.extensions.plugins.images;

import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IIconProvider;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;

/**
 * @author jcompagner
 */
public class ImagePlugin implements IClientPlugin, IIconProvider
{
	public static final String PLUGIN_NAME = "images"; //$NON-NLS-1$

	private IClientPluginAccess access;
	private ImageProvider impl;

	public ImagePlugin()
	{
		super();
	}

	/**
	 * @see com.servoy.j2db.plugins.IClientPlugin#initialize(com.servoy.j2db.plugins.IClientPluginAccess)
	 */
	public void initialize(IClientPluginAccess app) throws PluginException
	{
		this.access = app;
	}

	/**
	 * @see com.servoy.j2db.plugins.IClientPlugin#getPreferencePanels()
	 */
	public PreferencePanel[] getPreferencePanels()
	{
		return null;
	}

	/**
	 * @see com.servoy.j2db.plugins.IClientPlugin#getName()
	 */
	public String getName()
	{
		return PLUGIN_NAME;
	}

	/**
	 * @see com.servoy.j2db.plugins.IClientPlugin#getImage()
	 */
	public Icon getImage()
	{
		java.net.URL iconUrl = this.getClass().getResource("images.png"); //$NON-NLS-1$
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		else
		{
			return null;
		}
	}

	/**
	 * @see com.servoy.j2db.plugins.IClientPlugin#getScriptObject()
	 */
	public IScriptable getScriptObject()
	{
		if (impl == null)
		{
			impl = new ImageProvider(this);
		}
		return impl;
	}

	IClientPluginAccess getClientPluginAccess()
	{
		return access;
	}

	/**
	 * @see com.servoy.j2db.plugins.IPlugin#load()
	 */
	public void load() throws PluginException
	{
	}

	/**
	 * @see com.servoy.j2db.plugins.IPlugin#unload()
	 */
	public void unload() throws PluginException
	{
		access = null;
	}

	/**
	 * @see com.servoy.j2db.plugins.IPlugin#getProperties()
	 */
	public Properties getProperties()
	{
		return null;
	}

	/**
	 * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent evt)
	{
	}

	@Override
	public URL getIconUrl()
	{
		return this.getClass().getResource("images.png"); //$NON-NLS-1$
	}
}
