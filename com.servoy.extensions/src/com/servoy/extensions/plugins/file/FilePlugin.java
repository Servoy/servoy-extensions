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
package com.servoy.extensions.plugins.file;

import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.j2db.Messages;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IIconProvider;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;

/**
 * The main {@link IClientPlugin} implementation
 *
 * @author jcompagner
 */
public class FilePlugin implements IClientPlugin, IIconProvider
{
	public static final String PLUGIN_NAME = "file"; //$NON-NLS-1$

	private IClientPluginAccess access;
	private FileProvider fileProvider;

	public void initialize(IClientPluginAccess app) throws PluginException
	{
		access = app;
	}

	public PreferencePanel[] getPreferencePanels()
	{
		return null;
	}

	public String getName()
	{
		return PLUGIN_NAME;
	}

	public Icon getImage()
	{
		java.net.URL iconUrl = this.getClass().getResource("images/file.png"); //$NON-NLS-1$
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
		if (fileProvider == null)
		{
			if (access != null &&
				(access.getApplicationType() == IClientPluginAccess.WEB_CLIENT || access.getApplicationType() == IClientPluginAccess.NG_CLIENT))
			{
				fileProvider = new WebFileProvider(this);
			}
			else
			{
				fileProvider = new FileProvider(this);
			}
		}
		return fileProvider;
	}

	IClientPluginAccess getClientPluginAccess()
	{
		return access;
	}

	public void load() throws PluginException
	{
	}

	public void unload() throws PluginException
	{
		access = null;
		if (fileProvider != null)
		{
			fileProvider.unload();
		}
		fileProvider = null;

	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, Messages.getString("servoy.plugin.file.displayname")); //$NON-NLS-1$
		return props;
	}

	public void propertyChange(PropertyChangeEvent evt)
	{
		if (fileProvider != null && "solution".equals(evt.getPropertyName()) && evt.getNewValue() == null) //$NON-NLS-1$
		{
			fileProvider.deleteTrackedFiles();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.plugins.IIconProvider#getIconUrl()
	 */
	@Override
	public URL getIconUrl()
	{
		return this.getClass().getResource("images/file.png"); //$NON-NLS-1$
	}
}
