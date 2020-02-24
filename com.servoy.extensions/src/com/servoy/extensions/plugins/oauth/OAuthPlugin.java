/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2018 Servoy BV

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

package com.servoy.extensions.plugins.oauth;

import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IIconProvider;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.scripting.IScriptable;

/**
 * @author emera
 */
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthPlugin implements IClientPlugin, IIconProvider
{
	public static final String PLUGIN_NAME = "oauth"; //$NON-NLS-1$

	private OAuthProvider impl;
	private IClientPluginAccess access;

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

	IClientPluginAccess getAccess()
	{
		return access;
	}

	@Override
	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "OAuth Plugin"); //$NON-NLS-1$
		return props;
	}

	@Override
	public void propertyChange(PropertyChangeEvent arg0)
	{
	}

	@Override
	public IScriptable getScriptObject()
	{
		if (impl == null)
		{
			impl = new OAuthProvider(this);
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
		java.net.URL iconUrl = this.getClass().getResource("images/oauth.png"); //$NON-NLS-1$
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		else
		{
			return null;
		}
	}

	@Override
	public URL getIconUrl()
	{
		return this.getClass().getResource("images/oauth.png"); //$NON-NLS-1$
	}
}
