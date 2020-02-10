/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2020 Servoy BV

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

package com.servoy.extensions.plugins.jwt.client;

import java.beans.PropertyChangeEvent;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.scripting.IScriptable;

/**
 * JWT client plugin.
 * @author emera
 */
public class JWTPlugin implements IClientPlugin
{
	static final String PLUGIN_NAME = "jwt";
	private IClientPluginAccess access;
	private JWTProvider impl;


	public JWTPlugin()
	{
	}


	@Override
	public void load() throws PluginException
	{
	}

	@Override
	public void unload() throws PluginException
	{
		impl = null;
		access = null;
	}

	@Override
	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "JWT Plugin"); //$NON-NLS-1$
		return props;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt)
	{
	}

	@Override
	public IScriptable getScriptObject()
	{
		if (impl == null)
		{
			impl = new JWTProvider(this);
		}
		return impl;
	}

	@Override
	public void initialize(IClientPluginAccess app) throws PluginException
	{
		this.access = app;
	}

	@Override
	public String getName()
	{
		return PLUGIN_NAME;
	}

	@Override
	public Icon getImage()
	{
		java.net.URL iconUrl = this.getClass().getResource("images/jwt.png"); //$NON-NLS-1$
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		else
		{
			return null;
		}
	}

	IClientPluginAccess getClientPluginAccess()
	{
		return access;
	}
}
