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
package com.servoy.extensions.plugins.mail.client;

import java.beans.PropertyChangeEvent;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.j2db.Messages;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;

/**
 * @author jblok
 */
public class MailPlugin implements IClientPlugin
{
	public static final String PLUGIN_NAME = "mail"; //$NON-NLS-1$

	private IClientPluginAccess access;
	private MailProvider impl;

	/**
	 * Dummy constructor.
	 */
	public MailPlugin()
	{
	}

	public void initialize(IClientPluginAccess app) throws PluginException
	{
		access = app;
	}

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
		java.net.URL iconUrl = this.getClass().getResource("images/mail.png"); //$NON-NLS-1$
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
			impl = new MailProvider(this);
		}
		return impl;
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
		impl = null;
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, Messages.getString("servoy.plugin.mail.displayname")); //$NON-NLS-1$
		return props;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent evt)
	{
		// ignore
	}

}
