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
package com.servoy.extensions.plugins.http;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IIconProvider;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.serialize.JSONConverter;

/**
 * @author jblok
 */
public class HttpPlugin implements IClientPlugin, IIconProvider
{
	public static final String PLUGIN_NAME = "http"; //$NON-NLS-1$

	private IClientPluginAccess access;
	private HttpProvider impl;
	private JSONConverter jsonConverter;

	private final HashMap<WeakReference<HttpClient>, CloseableHttpAsyncClient> openClients = new HashMap<>();
	private final ReferenceQueue<HttpClient> queue = new ReferenceQueue<>();


	private final ExecutorService executor = Executors.newCachedThreadPool();

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
		closeClients();
		access = null;
		impl = null;
		executor.shutdownNow();
	}

	/**
	 *
	 */
	private void closeClients()
	{
		for (CloseableHttpAsyncClient httpClient : openClients.values())
		{
			try
			{
				httpClient.close();
			}
			catch (IOException e)
			{
				// ignore
			}
		}
		openClients.clear();
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "HTTP Plugin"); //$NON-NLS-1$
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
		if (impl == null)
		{
			impl = new HttpProvider(this);
		}
		return impl;
	}

	IClientPluginAccess getClientPluginAccess()
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
		java.net.URL iconUrl = this.getClass().getResource("images/http.png"); //$NON-NLS-1$
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
		if ("solution".equals(evt.getPropertyName()) && evt.getNewValue() == null) //$NON-NLS-1$
		{
			closeClients();
		}
	}

	public JSONConverter getJSONConverter()
	{
		if (jsonConverter == null)
		{
			jsonConverter = new JSONConverter(getClientPluginAccess().getDatabaseManager());
		}
		return jsonConverter;
	}

	/**
	 * @param httpClient
	 */
	void clientClosed(HttpClient httpClient)
	{
		for (WeakReference<HttpClient> element : openClients.keySet())
		{
			if (element.get() == httpClient)
			{
				openClients.remove(element);
				break;
			}
		}
	}

	/**
	 * @param httpClient
	 */
	void clientCreated(HttpClient httpClient)
	{
		Reference< ? extends HttpClient> ref = queue.poll();
		while (ref != null)
		{
			CloseableHttpAsyncClient client = openClients.remove(ref);
			if (client != null)
			{
				try
				{
					client.close();
				}
				catch (IOException e)
				{
					// ignore
				}
			}
			ref = queue.poll();
		}
		// The HttpClient object is the scriptable that should not have any reference to itself then scripting
		// so if scripting doesn't reference it anymore then it should be garbage collected.
		// Then the actaul commons http client can be closed correctly
		openClients.put(new WeakReference<HttpClient>(httpClient, queue), httpClient.client);
	}

	/**
	 * @return
	 */
	public Executor getExecutor()
	{
		return executor;
	}

	@Override
	public URL getIconUrl()
	{
		return this.getClass().getResource("images/http.png"); //$NON-NLS-1$
	}

}
