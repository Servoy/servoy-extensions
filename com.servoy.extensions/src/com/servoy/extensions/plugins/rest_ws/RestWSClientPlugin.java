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
package com.servoy.extensions.plugins.rest_ws;

import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.apache.commons.fileupload2.core.DiskFileItem;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IIconProvider;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Plugin for accessing the request and response during a REST-WS call.
 *
 * @author rgansevles
 */
public class RestWSClientPlugin implements IClientPlugin, IIconProvider
{
	public static final String PLUGIN_NAME = "rest_ws";

	private RestWSClientProvider impl;

	private HttpServletRequest request;
	private HttpServletResponse response;
	private List<DiskFileItem> contents;
	private Boolean sendUserPropertiesHeaders = null;


	public void load() throws PluginException
	{
	}

	public void initialize(IClientPluginAccess app) throws PluginException
	{
	}

	public void unload() throws PluginException
	{
		impl = null;
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "Servoy REST-WS client plugin");
		return props;
	}

	public PreferencePanel[] getPreferencePanels()
	{
		return null;
	}

	public String getName()
	{
		return PLUGIN_NAME;
	}

	public IScriptable getScriptObject()
	{
		if (impl == null)
		{
			impl = new RestWSClientProvider(this);
		}
		return impl;
	}

	public Icon getImage()
	{
		URL iconUrl = this.getClass().getResource("images/rest_ws.png");
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		return null;
	}

	public void propertyChange(PropertyChangeEvent evt)
	{
		// ignore
	}

	/*
	 * Set/clear request, response and contents, called from server-plugin
	 */
	public void setRequestResponseContents(HttpServletRequest request, HttpServletResponse response, List<DiskFileItem> contents)
	{
		this.request = request;
		this.response = response;
		this.contents = contents;
	}

	HttpServletRequest getRequest()
	{
		return request;
	}

	HttpServletResponse getResponse()
	{
		return response;
	}

	public List<DiskFileItem> getContents()
	{
		return contents;
	}

	@Override
	public URL getIconUrl()
	{
		return this.getClass().getResource("images/rest_ws.png");
	}

	void setSendUserPropertiesHeaders(boolean send)
	{
		sendUserPropertiesHeaders = Boolean.valueOf(send);
	}

	public Boolean isSendUserPropertiesHeaders()
	{
		return sendUserPropertiesHeaders;
	}
}
