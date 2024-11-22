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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import com.github.scribejava.core.model.Response;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>OAuthResponseBinary</code> object is used to handle binary responses
 * during OAuth authentication. It allows developers to retrieve important information
 * such as the HTTP response code, specific header values, or all headers in the response.
 * This makes it easier to process and analyze responses from the OAuth service.</p>
 *
 * <p>The <code>getCode()</code> method provides the HTTP response code as a number,
 * enabling developers to determine the success or failure of a request. The
 * <code>getHeader(name)</code> method retrieves the value of a specific header when
 * given its name, returning the result as a string. For a comprehensive view of the
 * response headers, the <code>getHeaders()</code> method returns all headers in an
 * array format.</p>
 *
 * <p>For more information about authentication using the OAuth service, refer to the
 * <a href="../../../../guides/develop/security/authentication.md#oauth-provider">
 * Oauth Provider</a> from the
 * <a href="../../../../guides/develop/security/authentication.md">
 * Authentication</a> section of this documentation.</p>
 *
 * @author emera
 */
@ServoyDocumented(scriptingName = "OAuthResponseBinary")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthResponseBinary extends OAuthResponse implements IJavaScriptType, IScriptable
{
	private byte[] content;

	public OAuthResponseBinary(Response response)
	{
		super(response);
		try (InputStream is = response.getStream())
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int reads = is.read();
			while (reads != -1)
			{
				baos.write(reads);
				reads = is.read();
			}
			content = baos.toByteArray();
		}
		catch (Exception e)
		{
			OAuthService.log.error(e.getMessage());
		}
	}

	public byte[] getContent()
	{
		return content;
	}
}
