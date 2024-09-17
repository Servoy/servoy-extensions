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
 * OAuth text response.
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
