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
package com.servoy.extensions.plugins.window;

import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * Window plugin
 *
 * @author rgansevles
 */
public class WindowPlugin implements IClientPlugin
{
	public static final String PLUGIN_NAME = "window"; //$NON-NLS-1$

	private IClientPluginAccess access;
	private WindowProvider impl;

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
		if (impl != null)
		{
			impl.unload();
		}
		access = null;
		impl = null;
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "Window plugin"); //$NON-NLS-1$
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
		if (impl == null) impl = new WindowProvider(this);
		return impl;
	}

	public IClientPluginAccess getClientPluginAccess()
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
		URL iconUrl = this.getClass().getResource("images/window.png"); //$NON-NLS-1$
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
		if (impl == null) return;
		if (IPlugin.PROPERTY_SOLUTION.equals(evt.getPropertyName()) && evt.getNewValue() == null)
		{
			Debug.trace("WindowPlugin: solution closed"); //$NON-NLS-1$
			impl.cleanup();
			impl = null;
		}
		else if (IPlugin.PROPERTY_CURRENT_WINDOW.equals(evt.getPropertyName()))
		{
			Debug.trace("WindowPlugin: currentWindow changed to " + evt.getNewValue()); //$NON-NLS-1$
			impl.currentWindowChanged();
		}
	}

	public boolean isSwingClient()
	{
		return getClientPluginAccess().getApplicationType() == IClientPluginAccess.CLIENT ||
			getClientPluginAccess().getApplicationType() == IClientPluginAccess.RUNTIME;
	}
}
