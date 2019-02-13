/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2019 Servoy BV

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

package com.servoy.extensions.plugins.lesscompiler;

import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.scripting.IScriptable;

/**
 * @author jcompagner
 * @since 2019.3
 *
 */
public class LessCompilerPlugin implements IClientPlugin
{

	public static final String PLUGIN_NAME = "lesscompiler"; //$NON-NLS-1$
	private URL iconUrl;
	private IClientPluginAccess pluginAccess;

	@Override
	public void load() throws PluginException
	{
	}

	@Override
	public void unload() throws PluginException
	{
	}

	@Override
	public Properties getProperties()
	{
		return null;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt)
	{
	}

	@Override
	public IScriptable getScriptObject()
	{
		return new LessProvider(this.pluginAccess);
	}

	@Override
	public void initialize(IClientPluginAccess app) throws PluginException
	{
		this.pluginAccess = app;
	}

	@Override
	public String getName()
	{
		return PLUGIN_NAME;
	}

	@Override
	public Icon getImage()
	{
		iconUrl = this.getClass().getResource("less.png"); //$NON-NLS-1$
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		else
		{
			return null;
		}
	}

}
