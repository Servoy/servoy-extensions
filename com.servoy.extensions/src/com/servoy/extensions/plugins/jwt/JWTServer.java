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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeArray;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * @author emera
 */
public class JWTServer implements IServerPlugin, IJWTService
{
	private static final String ALG = "alg";
	private static final String JWT_SECRET_KEY = "jwt.secret.password";


	private Properties settings;
	private JWTVerifier verifier;
	private Algorithm algorithm;

	private final Map<String, Algorithm> algorithms = new HashMap<>();
	private final Map<String, JWTVerifier> verifiers = new HashMap<>();

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

	private boolean checkInit()
	{
		if (settings.getProperty(JWT_SECRET_KEY) == null)
		{
			Debug.error("Cannot create JWT token, the " + JWT_SECRET_KEY + " was not set on the admin page.");
			return false;
		}
		if (algorithm == null)
		{
			try
			{
				algorithm = Algorithm.HMAC256(settings.getProperty(JWT_SECRET_KEY));
			}
			catch (Exception e)
			{
				Debug.error(e);
				return false;
			}
		}
		return true;
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
	public String create(JSONObject claims, Date expire) throws RemoteException
	{
		try
		{
			if (!checkInit()) return null;
			Builder builder = JWT.create();
			addClaims(claims, builder);
			if (expire != null)
			{
				builder.withExpiresAt(expire);
			}
			return builder.sign(algorithm);
		}
		catch (Exception e)
		{
			Debug.error("Could not create the JWT token", e);
		}
		return null;
	}


	/**
	 * @param claims
	 * @param builder
	 */
	private void addClaims(JSONObject claims, Builder builder)
	{
		if (claims != null)
		{
			for (String key : claims.keySet())
			{
				Object value = claims.get(key);
				if (value instanceof Boolean) builder.withClaim(key, (Boolean)value);
				if (value instanceof Date) builder.withClaim(key, (Date)value);
				if (value instanceof Double) builder.withClaim(key, (Double)value);
				if (value instanceof Integer) builder.withClaim(key, (Integer)value);
				if (value instanceof Long) builder.withClaim(key, (Long)value);
				if (value instanceof String) builder.withClaim(key, (String)value);
				if (value instanceof NativeArray)
				{
					if (isNumbersArray((NativeArray)value))
					{
						builder.withArrayClaim(key, convertToLongArray(value));
					}
					else
					{
						builder.withArrayClaim(key, convertToStringArray(value));
					}
				}
			}
		}
	}

	private Long[] convertToLongArray(Object value)
	{
		NativeArray array = (NativeArray)value;
		int size = Long.valueOf(array.getLength()).intValue();
		Long[] result = new Long[size];
		for (int i = 0; i < size; i++)
		{
			result[i] = array.get(i) == null ? null : ((Number)array.get(i)).longValue();
		}
		return result;
	}

	private String[] convertToStringArray(Object value)
	{
		NativeArray array = (NativeArray)value;
		int size = Long.valueOf(array.getLength()).intValue();
		String[] result = new String[size];
		for (int i = 0; i < size; i++)
		{
			result[i] = array.get(i) == null ? null : array.get(i).toString();
		}
		return result;
	}

	private boolean isNumbersArray(NativeArray value)
	{
		for (int i = 0; i < value.getLength(); i++)
		{
			if (value.get(i) != null && !(value.get(i) instanceof Number)) return false;
		}
		return true;
	}

	@Override
	public JSONObject verify(String token) throws RemoteException
	{
		try
		{
			if (token == null) return null;
			JSONObject headers = new JSONObject(new String(Utils.decodeBASE64(token)));
			String algKey = headers.getString("alg") + headers.optString("kid");

			JWTVerifier jwtVerifier = verifiers.get(algKey);
			if (jwtVerifier == null)
			{
				Algorithm alg = algorithms.get(algKey);
				if (alg == null)
				{
					if (!checkInit()) return null;
					alg = algorithm;
				}
				jwtVerifier = JWT.require(alg)
					.build();
				verifiers.put(algKey, jwtVerifier);
			}
			DecodedJWT jwt = jwtVerifier.verify(token);
			String payload = new String(Utils.decodeBASE64(jwt.getPayload()));
			return new JSONObject(payload);
		}
		catch (TokenExpiredException e)
		{
			Debug.trace(e);
		}
		catch (JWTVerificationException | JSONException e)
		{
			Debug.error(e);
		}
		return null;
	}
}
