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
 * OAuth json response.
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
