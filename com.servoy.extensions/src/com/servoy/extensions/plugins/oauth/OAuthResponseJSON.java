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

package com.servoy.extensions.plugins.oauth;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.annotations.JSFunction;

import com.github.scribejava.core.model.Response;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>OAuthResponseJSON</code> object is designed for handling JSON responses
 * during OAuth authentication. It provides methods to extract key elements from the
 * response, such as the body content, HTTP response code, and headers. Additionally,
 * it allows parsing the response body into a JSON object for easier manipulation.</p>
 *
 * <p>The <code>getAsJSON()</code> method converts the response body into a JSON object,
 * enabling structured data handling. For accessing raw content, the <code>getBody()</code>
 * method returns the response body as a string. The <code>getCode()</code> method retrieves
 * the HTTP response code as a number, which is useful for determining the status of the
 * OAuth transaction. To access specific metadata, the <code>getHeader(name)</code> method
 * fetches the value of a header by name, while the <code>getHeaders()</code> method returns
 * all headers in an array format.</p>
 *
 * <p>For more information about authentication using the OAuth service, refer to the
 * <a href="../../../../guides/develop/security/authentication.md#oauth-provider">
 * OAuth Provider</a> from the
 * <a href="../../../../guides/develop/security/authentication.md">
 * Authentication</a> section of this documentation.</p>
 *
 * @author emera
 */
@ServoyDocumented(scriptingName = "OAuthResponseJSON")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthResponseJSON extends OAuthResponseText implements IJavaScriptType, IScriptable
{
	public OAuthResponseJSON(Response response)
	{
		super(response);
	}

	@JSFunction
	public JSONObject getAsJSON()
	{
		JSONObject json = null;
		try
		{
			String response_body = response.getBody();
			if (response_body != null && !response_body.isEmpty())
			{
				//in case the response is just a json array, then wrap it in a json object
				if (response_body.startsWith("["))
				{
					json = new JSONObject();
					json.put("array", new JSONArray(response_body));
				}
				else
				{
					json = new JSONObject(response_body);
				}
			}
		}
		catch (JSONException | IOException e)
		{
			OAuthService.log.error(e.getMessage());
		}
		return json;
	}
}
