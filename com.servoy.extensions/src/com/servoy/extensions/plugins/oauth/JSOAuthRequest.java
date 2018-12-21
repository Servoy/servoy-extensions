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

import org.mozilla.javascript.annotations.JSFunction;

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.servoy.j2db.scripting.IScriptable;

/**
 * Wrapper around the scribe OAuthRequest.
 * @author emera
 */
public class JSOAuthRequest implements IScriptable
{
	protected final OAuthRequest request;

	public JSOAuthRequest(Verb verb, String requestURL)
	{
		this.request = new OAuthRequest(verb, requestURL);
	}

	public JSOAuthRequest(OAuthRequest oAuthRequest)
	{
		this.request = oAuthRequest;
	}

	@JSFunction
	public void addHeader(String header, String value)
	{
		request.addHeader(header, value);
	}

	@JSFunction
	public void addBodyParameter(String key, String value)
	{
		request.addBodyParameter(key, value);
	}

	@JSFunction
	public void setPayload(String data)
	{
		request.setPayload(data);//TODO file or other types
	}

	@JSFunction
	public void addParameter(String key, String value)
	{
		request.addParameter(key, value);
	}

	@JSFunction
	public void addOAuthParameter(String key, String value)
	{
		request.addOAuthParameter(key, value);
	}

	@JSFunction
	public void addQuerystringParameter(String key, String value)
	{
		request.addQuerystringParameter(key, value);
	}

	OAuthRequest getRequest()
	{
		return request;
	}
}
