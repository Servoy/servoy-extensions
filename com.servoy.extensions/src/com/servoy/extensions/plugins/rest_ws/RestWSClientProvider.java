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

import javax.servlet.http.Cookie;

import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;

@ServoyDocumented(publicName = RestWSClientPlugin.PLUGIN_NAME, scriptingName = "plugins." + RestWSClientPlugin.PLUGIN_NAME)
public class RestWSClientProvider implements IScriptable
{
	private final RestWSClientPlugin plugin;

	RestWSClientProvider(RestWSClientPlugin plugin)
	{
		this.plugin = plugin;
	}

	/**
	 *  Check whether the client is currently running a REST-WS request.
	 *  If false, the rest-ws client-plugin features are not available.
	 */
	@JSFunction
	public boolean isRunningRequest()
	{
		return plugin.getRequest() != null;
	}

	/**
	 *  Get the currently running REST-WS request.
	 *  If the client is not currently running in REST-WS, an exception is thrown.
	 */
	@JSFunction
	public WsRequest getRequest()
	{
		checkRunningRequest();
		return new WsRequest(plugin);
	}

	/**
	 * Get the response for the currently running REST-WS request.
	 * If the client is not currently running in REST-WS, an exception is thrown.
	 * @sample
	 * var response = plugins.rest_ws.getResponse();
	 * resp.setHeader("My-Custom-Header", "42");
	 */
	@JSFunction
	public WsResponse getResponse()
	{
		checkRunningRequest();
		return new WsResponse(plugin);
	}

	/**
	 * Create a http cookie.
	 * The cookie name and value allows only a sequence of non-special, non-white space characters, see
	 * the cookie spec https://tools.ietf.org/html/rfc2965
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * var response = plugins.rest_ws.getResponse();
	 * response.addCookie(cookie);
	 *
	 * @param name The name of the cookie
	 * @param value The value of the cookie
	 */
	@JSFunction
	public WsCookie createCookie(String name, String value)
	{
		return new WsCookie(new Cookie(name, value));
	}

	private void checkRunningRequest()
	{
		if (!isRunningRequest())
		{
			throw new IllegalStateException("Not running request");
		}
	}

	/**
	 * Allow or block sending the user properties as response header values.
	 * By default the response headers contain the user properties.
	 * @param send
	 */
	@JSFunction
	public void sendResponseUserPropertiesHeaders(boolean send)
	{
		plugin.setSendUserPropertiesHeaders(send);
	}
}