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

package com.servoy.extensions.plugins.jwt.client;

import java.util.Date;

import org.json.JSONObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.extensions.plugins.jwt.IJWTService;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * @author emera
 */
@ServoyDocumented(publicName = JWTPlugin.PLUGIN_NAME, scriptingName = "plugins." + JWTPlugin.PLUGIN_NAME)
public class JWTProvider implements IScriptable
{
	private final JWTPlugin plugin;
	private IJWTService jwtService;

	public JWTProvider(JWTPlugin jwtPlugin)
	{
		this.plugin = jwtPlugin;
	}

	private void createJWTService()
	{
		if (jwtService == null)
		{
			try
			{
				IClientPluginAccess access = plugin.getClientPluginAccess();
				jwtService = (IJWTService)access.getRemoteService(IJWTService.class.getName());
			}
			catch (Exception ex)
			{
				Debug.error(ex);
			}
		}
	}

	/**
	 * Create a JSON Web Token
	 * @param payload a json containing the data,
	 * 		e.g. {'some': 'data', 'somemore': 'data2'}
	 * @return a string representing the encrypted data
	 * 		or null if the token cannot be generated
	 */
	@JSFunction
	public String create(Object payload)
	{
		return create(payload, null);
	}

	/**
	 * Create a JSON Web Token
	 * @param payload a json containing the data,
	 * 		e.g. {'some': 'data', 'somemore': 'data2'}
	 * @param expiresAt the date when the created token expires,
	 * 		after the expired date the token won't be verified
	 * @return a string representing the encrypted data
	 * 		or null if the token cannot be generated
	 */
	@JSFunction
	public String create(Object payload, Date expiresAt)
	{
		createJWTService();

		if (jwtService != null)
		{
			try
			{
				JSONObject obj = new JSONObject();
				if (payload instanceof Scriptable)
				{
					Scriptable scriptable = (Scriptable)payload;
					for (Object id : scriptable.getIds())
					{
						if (id instanceof String)
						{
							obj.put((String)id, scriptable.get((String)id, null));
						}
					}
				}
				return jwtService.create(obj, expiresAt);
			}
			catch (Exception e)
			{
				Debug.error(e);
			}
		}
		return null;
	}

	/**
	 * Verifiy a JWT.
	 * @param token a JSON Web Token
	 * @return the payload or null if the token can't be verified
	 */
	@JSFunction
	public Object verify(String token)
	{
		createJWTService();
		if (jwtService != null)
		{
			try
			{
				return jwtService.verify(token);
			}
			catch (Exception e)
			{
				Debug.error(e);
			}
		}
		return null;
	}
}