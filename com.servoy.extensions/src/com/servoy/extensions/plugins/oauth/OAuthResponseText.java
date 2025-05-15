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

import org.mozilla.javascript.annotations.JSFunction;

import com.github.scribejava.core.model.Response;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>OAuthResponseText</code> object is used for handling text-based responses
 * during OAuth authentication. It provides methods to retrieve the response body,
 * HTTP status code, and headers, making it easier to process and evaluate the OAuth
 * service's responses.</p>
 *
 * <p>The <code>getBody()</code> method returns the response body as a string, offering
 * direct access to the textual content. The <code>getCode()</code> method retrieves the
 * HTTP response code, represented as a number, to assess the success or failure of the
 * request. For header management, the <code>getHeader(name)</code> method fetches a
 * specific header's value based on its name, while the <code>getHeaders()</code> method
 * provides an array containing all response headers.</p>
 *
 * <p>For more information about authentication using the OAuth service, refer to the
 * <a href="https://docs.servoy.com/guides/develop/security/authentication#oauth-provider">
 * OAuth Provider</a> from the
 * <a href="ttps://docs.servoy.com/guides/develop/security/authentication">
 * Authentication</a> section of this documentation.</p>
 *
 * @author emera
 */
@ServoyDocumented(scriptingName = "OAuthResponseText")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthResponseText extends OAuthResponse implements IJavaScriptType, IScriptable
{
	public OAuthResponseText(Response response)
	{
		super(response);
	}

	/**
	 *
	 * @return The response body as a string, or null if an error occurs while reading it.
	 */
	@JSFunction
	public String getBody()
	{
		try
		{
			return response.getBody();
		}
		catch (IOException e)
		{
			OAuthService.log.error(e.getMessage());
		}
		return null;
	}
}
