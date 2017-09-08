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
package com.servoy.extensions.plugins.tabxport;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.Messages;
import com.servoy.j2db.plugins.ClientPluginAccessProvider;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.smart.ISmartClientPluginAccess;

/**
 * @author jblok
 */
public class TextXportPlugin implements IClientPlugin, ActionListener
{
	public static final String PLUGIN_NAME = "textxport"; //$NON-NLS-1$

	private IApplication application;
	private Enabler en;

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
		application = ((ClientPluginAccessProvider)app).getApplication();

		if (app instanceof ISmartClientPluginAccess)
		{
			JMenu export_Menu = ((ISmartClientPluginAccess)app).getExportMenu();
			JMenuItem exp = null;
			if (export_Menu != null)
			{
				exp = new JMenuItem(Messages.getString("servoy.plugin.tabxport.menuitem.toTextFile")); //$NON-NLS-1$
				exp.addActionListener(this);
				exp.setActionCommand("export_tab_file"); //$NON-NLS-1$
				export_Menu.add(exp);
			}

			JMenu import_Menu = ((ISmartClientPluginAccess)app).getImportMenu();
			JMenuItem imp = null;
			if (import_Menu != null)
			{
				imp = new JMenuItem(Messages.getString("servoy.plugin.tabxport.menuitem.fromTextFile")); //$NON-NLS-1$
				imp.setActionCommand("import_tab_file"); //$NON-NLS-1$
				imp.addActionListener(this);
				import_Menu.add(imp);
			}
			en = new Enabler(imp, exp, application);
		}
		else
		{
			en = new Enabler(null, null, application);
		}
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, Messages.getString("servoy.plugin.tabxport.displayname")); //$NON-NLS-1$
		return props;
	}

	public void actionPerformed(ActionEvent e)
	{
		String cmd = e.getActionCommand();
		if ("export_tab_file".equals(cmd)) //$NON-NLS-1$
		{
			try
			{
				TextExport export = new TextExport(application);
				export.showFrame();
			}
			catch (Exception ex)
			{
				application.reportError(Messages.getString("servoy.plugin.export.exception"), ex); //$NON-NLS-1$
			}
		}
		else if ("import_tab_file".equals(cmd)) //$NON-NLS-1$
		{
			try
			{
				TextImport importer = new TextImport(application);
				importer.showFrame();
			}
			catch (Exception ex)
			{
				application.reportError(Messages.getString("servoy.plugin.import.exception"), ex); //$NON-NLS-1$
			}
		}
	}


	/*
	 * @see IPlugin#unload()
	 */
	public void unload() throws PluginException
	{
		application = null;
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

	/*
	 * @see IPlugin#getScriptObject()
	 */
	public IScriptable getScriptObject()
	{
		if (en == null) return new Enabler();//return dummy
		return en;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.j2db.plugins.IClientPlugin#getImage()
	 */
	public Icon getImage()
	{
		java.net.URL iconUrl = this.getClass().getResource("images/textexport.png"); //$NON-NLS-1$
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
		if (en != null && "solution".equals(evt.getPropertyName())) //$NON-NLS-1$
		{
			en.setEnabled(true);//restore for new solution
		}
	}
}
