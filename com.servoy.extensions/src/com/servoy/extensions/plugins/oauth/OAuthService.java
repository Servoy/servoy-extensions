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
import java.util.concurrent.ExecutionException;

import org.mozilla.javascript.annotations.JSFunction;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * @author emera
 */
public class OAuthService implements IScriptable, IJavaScriptType
{

	private final OAuth20Service service;
	private OAuth2AccessToken accessToken;
	private final String state;

	public OAuthService(OAuth20Service service, String state)
	{
		this.service = service;
		this.state = state;
	}

	@JSFunction
	public String getAuthorizationURL()
	{
		return state != null ? service.getAuthorizationUrl(state) : service.getAuthorizationUrl();
	}

	@JSFunction
	public void setAccessToken(String code) throws Exception
	{
		try
		{
			this.accessToken = service.getAccessToken(code);
		}
		catch (IOException | InterruptedException | ExecutionException e)
		{
			Debug.error("Could not set the access token.", e);
			throw new Exception("Could not set the access token. See the log for more details");
		}
	}

	@JSFunction
	public void setAccessToken(String code, String scope) throws Exception
	{
		try
		{
			this.accessToken = service.getAccessToken(AccessTokenRequestParams.create(code).scope(scope));
		}
		catch (IOException | InterruptedException | ExecutionException e)
		{
			Debug.error("Could not set the access token.", e);
			throw new Exception("Could not set the access token. See the log for more details");
		}

	}

	@JSFunction
	public String getAccessToken()
	{
		return accessToken != null ? accessToken.getAccessToken() : null;
	}

	@JSFunction
	public JSOAuthRequest createRequest(Verb verb, String resourceURL)
	{
		return new JSOAuthRequest(verb, resourceURL);
	}

	//just a convenience method, not really needed
	@JSFunction
	public OAuthResponse executeGetRequest(String resourceURL)
	{
		if (accessToken == null) throw new RuntimeException("Cannot execute request. Please set the acess token first.");
		OAuthRequest request = new OAuthRequest(Verb.GET, resourceURL);
		service.signRequest(accessToken, request);
		try
		{
			Response response = service.execute(request);
			return ResponseFactory.create(response);
		}
		catch (InterruptedException | ExecutionException | IOException e)
		{
			Debug.error("Could not execute request " + resourceURL, e);
		}
		return null;
	}

	@JSFunction
	public OAuthResponse executeRequest(JSOAuthRequest request)
	{
		if (request == null) return null;
		//TODO check if expired?
		if (accessToken == null) throw new RuntimeException("Cannot execute request. Please set the acess token first.");
		OAuthRequest req = request.getRequest();
		service.signRequest(accessToken, req);
		try
		{
			Response response = service.execute(req);
			return ResponseFactory.create(response);
		}
		catch (InterruptedException | ExecutionException | IOException e)
		{
			Debug.error("Could not execute request " + request.getRequest().getUrl(), e);
		}
		return null;

	}
}
