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
package com.servoy.extensions.plugins.validators;

import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.servoy.j2db.dataprocessing.IColumnValidator;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IColumnValidatorProvider;
import com.servoy.j2db.plugins.IIconProvider;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;

public class ValidatorPlugin implements IClientPlugin, IColumnValidatorProvider, IIconProvider
{
	private IClientPluginAccess application;
	private IColumnValidator[] validators;

	public IColumnValidator[] getColumnValidators()
	{
		if (validators == null)
		{
			validators = new IColumnValidator[] { new GlobalMethodValidator(
				application), new EmailValidator(), new RegexValidator(), new NumberRangeValidator(), new SizeValidator(), new IdentValidator() };
		}
		return validators;
	}

	public Icon getImage()
	{
		URL iconUrl = this.getClass().getResource("images/validators.gif"); //$NON-NLS-1$
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		else
		{
			return null;
		}
	}

	public String getName()
	{
		return "servoy_default_validators";
	}

	public PreferencePanel[] getPreferencePanels()
	{
		return null;
	}

	public IScriptable getScriptObject()
	{
		return null;
	}

	public void initialize(IClientPluginAccess app) throws PluginException
	{
		application = app;
		// the init is executed later than getColumnValidators so must be sure plugin access is not null
		if (validators != null)
		{
			for (IColumnValidator validator : validators)
			{
				if (validator instanceof GlobalMethodValidator)
				{
					((GlobalMethodValidator)validator).setClientPluginAccess(app);
				}
			}
		}
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

	@Override
	public URL getIconUrl()
	{
		return this.getClass().getResource("images/validators.gif"); //$NON-NLS-1$
	}
}
