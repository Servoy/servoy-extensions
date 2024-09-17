/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2021 Servoy BV

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.annotations.JSFunction;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * JWT builder.
 *
 * @author emera
 */
@ServoyDocumented(scriptingName = "builder")
public class Builder implements IScriptable, IJavaScriptType
{
	private final com.auth0.jwt.JWTCreator.Builder builder;
	private Map<String, Object> headers;

	public Builder()
	{
		this.builder = JWT.create();
	}


	/**
	 * Adds the payload (claims) to the web token.
	 * @param payload a json containing the data,
	 * 		e.g. {'some': 'data', 'somemore': 'data2'}
	 * @return the jwt builder for method chaining
	 */
	@JSFunction
	public Builder payload(Object payload)
	{
		if (payload instanceof Scriptable)
		{
			Scriptable scriptable = (Scriptable)payload;
			for (Object id : scriptable.getIds())
			{
				if (id instanceof String)
				{
					String key = (String)id;
					Object value = scriptable.get(key, null);
					withClaim(key, value);
				}
			}
		}
		return this;
	}

	/**
	 * Adds data to the payload, which contains the claims.
	 * Claims are statements about an entity (typically, the user) and additional data.
	 * @param key a string representing the claim name (e.g. 'iss' which stands for issuer; 'email', 'name', etc.)
	 * @param value can be a string, a boolean, a date, a number or an array
	 * @return the jwt builder for method chaining
	 */
	@JSFunction
	public Builder withClaim(String key, Object value)
	{
		if (value instanceof Boolean) builder.withClaim(key, (Boolean)value);
		if (value instanceof Date) builder.withClaim(key, (Date)value);
		if (value instanceof Double) builder.withClaim(key, (Double)value);
		if (value instanceof Integer) builder.withClaim(key, (Integer)value);
		if (value instanceof Long) builder.withClaim(key, (Long)value);
		if (value instanceof String) builder.withClaim(key, (String)value);
		if (value instanceof NativeArray)
		{
			value = ((NativeArray)value).unwrap();
		}

		if (value instanceof Object[])
		{
			if (isNumbersArray((Object[])value))
			{
				builder.withArrayClaim(key, convertToLongArray((Object[])value));
			}
			else
			{
				builder.withArrayClaim(key, convertToStringArray((Object[])value));
			}
		}
		if (value instanceof NativeObject)
		{
			builder.withClaim(key, convertMap((Map<String, Object>)value));
		}
		return this;
	}

	private Map<String, Object> convertMap(Map<String, Object> value)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		for (Entry<String, Object> pair : value.entrySet())
		{
			Object val = pair.getValue();
			if (val instanceof NativeArray)
			{
				val = ((NativeArray)val).unwrap();
			}
			if (val instanceof Object[])
			{
				if (isNumbersArray((Object[])val))
				{
					val = convertToLongArray((Object[])val);
				}
				else
				{
					val = convertToStringArray((Object[])val);
				}
			}
			if (val instanceof NativeObject)
			{
				val = convertMap((Map<String, Object>)val);
			}

			result.put(pair.getKey(), val);
		}
		return result;
	}

	private Long[] convertToLongArray(Object[] array)
	{
		Long[] result = new Long[array.length];
		for (int i = 0; i < array.length; i++)
		{
			result[i] = array[i] == null ? null : ((Number)array[i]).longValue();
		}
		return result;
	}

	private String[] convertToStringArray(Object[] array)
	{
		String[] result = new String[array.length];
		for (int i = 0; i < array.length; i++)
		{
			result[i] = array[i] == null ? null : array[i].toString();
		}
		return result;
	}

	private boolean isNumbersArray(Object[] value)
	{
		for (Object element : value)
		{
			if (element != null && !(element instanceof Number)) return false;
		}
		return true;
	}

	/**
	 * Adds a header to the JWT token.
	 * @param key a string representing the header name
	 * @param value can be a string
	 * @return the jwt builder for method chaining
	 */
	@JSFunction
	public Builder header(String key, String value)
	{
		if (headers == null)
		{
			headers = new HashMap<>();
		}
		headers.put(key, value);
		return this;
	}

	/**
	 * Add a specific Expires At ("exp") claim to the Payload.
	 * @param expire a date representing the expiration time of the token
	 * @return the jwt builder for method chaining
	 */
	@JSFunction
	public Builder withExpires(Date expire)
	{
		builder.withExpiresAt(expire);
		return this;
	}

	/**
	 * Sign the token with the given algorithm.
	 * The 'alg' claim is automatically added to the token header.
	 * @param alg the algorithm used to sign the token.
	 * @return a string representing the constructed JSON Web Token.
	 */
	@JSFunction
	public String sign(Algorithm alg)
	{
		try
		{
			com.auth0.jwt.algorithms.Algorithm algo = alg.build();
			if (algo != null)
			{
				if (headers != null)
				{
					builder.withHeader(headers);
				}
			}
			return builder.sign(algo);
		}
		catch (JWTCreationException | IllegalArgumentException e)
		{
			JWTProvider.log.error(e.getMessage());
		}
		return null;
	}
}
