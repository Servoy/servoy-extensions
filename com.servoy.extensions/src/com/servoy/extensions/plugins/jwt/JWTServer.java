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

package com.servoy.extensions.plugins.jwt;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.server.shared.IClientInformation;
import com.servoy.j2db.util.Debug;

/**
 * @author emera
 */
public class JWTServer implements IServerPlugin, IJWTService
{
	private static final String JWT_SECRET_KEY = "jwt.secret.password";
	private Properties settings;
	private IServerAccess serverAccess;

	public JWTServer()
	{
	}


	@Override
	public void load() throws PluginException
	{
	}

	@Override
	public void unload() throws PluginException
	{
		settings = null;
	}

	@Override
	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "JWT Plugin"); //$NON-NLS-1$
		return props;
	}

	@Override
	public void initialize(IServerAccess app) throws PluginException
	{
		settings = app.getSettings();
		serverAccess = app;

		try
		{
			app.registerRemoteService(IJWTService.class.getName(), this);
		}
		catch (Exception e)
		{
			Debug.error(e);
		}

		Iterator it = settings.keySet().iterator();
		while (it.hasNext())
		{
			String key = (String)it.next();
			if (key.startsWith("jwt."))
			{
				String value = settings.getProperty(key);
				if (value != null && !value.trim().equals(""))
				{
					System.setProperty(key, value);
				}
			}
		}
	}

	@Override
	public Map<String, String> getRequiredPropertyNames()
	{
		Map<String, String> req = new LinkedHashMap<String, String>();
		req.put(JWT_SECRET_KEY,
			"Shared secret, used to sign and verify the JWT tokens. Should be the same on all servers that want to sign or verify the same tokens.");
		return req;
	}


	@Override
	public String getSecret(String clientID) throws RemoteException
	{
		if (clientID == null) return null;
		Optional<IClientInformation> clientInfo = Arrays.stream(serverAccess.getConnectedClients()).filter(ci -> clientID.equals(ci.getClientID())).findAny();
		if (clientInfo.isPresent() && clientInfo.get().getApplicationType() != IApplication.CLIENT)
		{
			return settings.getProperty(JWT_SECRET_KEY);
		}
		return null;
	}
}
