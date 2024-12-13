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


import java.util.Set;

import org.mozilla.javascript.annotations.JSFunction;

import com.github.scribejava.core.model.Response;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>OAuthResponse</code> class is used to handle HTTP responses in the context of
 * OAuth-based API interactions. It provides mechanisms to retrieve the HTTP status code,
 * individual header values, and all header names associated with the response.</p>
 *
 * <p>Key functionalities include the ability to fetch the HTTP status code through
 * <code>getCode()</code>, retrieve a specific header value using <code>getHeader(name)</code>,
 * and obtain a list of all headers with <code>getHeaders()</code>.</p>
 *
 * @author emera
 */
@ServoyDocumented(scriptingName = "OAuthResponse")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthResponse implements IJavaScriptType, IScriptable
{
	protected final Response response;

	public OAuthResponse(Response response)
	{
		this.response = response;
	}

	@JSFunction
	public int getCode()
	{
		return response.getCode();
	}

	@JSFunction
	public String getHeader(String name)
	{
		return response.getHeader(name);
	}

	@JSFunction
	public String[] getHeaders()
	{
		Set<String> headers = response.getHeaders().keySet();
		return headers.toArray(new String[headers.size()]);
	}

}