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
package com.servoy.extensions.plugins.agent;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

import com.servoy.j2db.Messages;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Utils;

/**
 * @author jcompagner
 */
public class AgentPlugin implements IClientPlugin
{
	public static final String PLUGIN_NAME = "agent"; //$NON-NLS-1$

	private IClientPluginAccess access;
	private IJSAgent impl;
	private Component showingIn;

	public AgentPlugin()
	{
		impl = new DummyAgentImpl();
	}

	/*
	 * @see IPlugin#load()
	 */
	public void load() throws PluginException
	{
	}

	/*
	 * @see IPlugin#initialize(IPluginAccess)
	 */
	public void initialize(IClientPluginAccess app) throws PluginException
	{
		access = app;

		if (!GraphicsEnvironment.isHeadless() &&
			(app.getApplicationType() == IClientPluginAccess.CLIENT || app.getApplicationType() == IClientPluginAccess.RUNTIME))
		{
			impl = new AgentImpl();

			// try to show agent
			JFrame frame = app.getMainApplicationFrame();
			if (frame != null)
			{
				Component comp = (Component)impl;
				comp.setLocation(frame.getWidth() - comp.getWidth() - 20, frame.getHeight() - comp.getHeight() - 40);
				frame.getRootPane().getLayeredPane().add(comp, JLayeredPane.MODAL_LAYER);
				setParentComponent(frame.getRootPane());
				if (showingIn != null)
				{
					showingIn.addComponentListener((ComponentListener)impl);
				}

				if (Utils.getAsBoolean(app.getSettings().getProperty("plugin.agent.showAgentOnStart", "false"))) //$NON-NLS-1$ //$NON-NLS-2$
				{
					SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							impl.js_setVisible(true);
						}
					});
				}
			}
		}
	}

	public void unload() throws PluginException
	{
		impl = null;
		access = null;
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, Messages.getString("servoy.plugin.agent.displayname")); //$NON-NLS-1$
		return props;
	}

	public PreferencePanel[] getPreferencePanels()
	{
		return new PreferencePanel[] { new AgentPreferencePanel(access) };
	}

	public String getName()
	{
		return PLUGIN_NAME;
	}

	public IScriptable getScriptObject()
	{
		return impl;
	}

	private void setParentComponent(JComponent comp)
	{
		if (showingIn != null && impl instanceof ComponentListener)
		{
			showingIn.removeComponentListener((ComponentListener)impl);
		}
		showingIn = comp;
		if (showingIn != null && impl instanceof ComponentListener)
		{
			showingIn.addComponentListener((ComponentListener)impl);
		}
	}

	public Icon getImage()
	{
		java.net.URL iconUrl = this.getClass().getResource("images/agent.png"); //$NON-NLS-1$
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		else
		{
			return null;
		}
	}

	public void propertyChange(PropertyChangeEvent evt)
	{
		// ignore
	}
}
