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
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;

/**
 * Wrapper around the scribe OAuthRequest.
 * @author emera
 */
@ServoyDocumented(publicName = "OAuthRequest", scriptingName = "OAuthRequest")
public class JSOAuthRequest implements IScriptable
{
	protected final OAuthRequest request;
	private final OAuthService service;

	public JSOAuthRequest(OAuthService service, Verb verb, String requestURL)
	{
		this.service = service;
		this.request = new OAuthRequest(verb, requestURL);
	}

	public JSOAuthRequest(OAuthService service, OAuthRequest oAuthRequest)
	{
		this.service = service;
		this.request = oAuthRequest;
	}

	/**
	 * Allows setting a header on the request object.
	 *
	 * @sample
	 * var getRequest = service.createGetRequest("https://api.linkedin.com/v2/me");
	 * getRequest.addHeader("Accept", "application/json");
	 *
	 * @param header the header name
	 * @param value the header value
	 */
	@JSFunction
	public void addHeader(String header, String value)
	{
		request.addHeader(header, value);
	}

	/**
	 * Add a body parameter to the request.
	 *
	 * @sample
	 * var postRequest = service.createPostRequest("https://.....");
	 * postRequest.addBodyParameter("param1", "value1");
	 *
	 * @param key the parameter name
	 * @param value the parameter value
	 */
	@JSFunction
	public void addBodyParameter(String key, String value)
	{
		request.addBodyParameter(key, value);
	}

	/**
	 * Set body payload.
	 *
	 * @sample
	 * var putRequest = service.createPutRequest("https://graph.microsoft.com/v1.0/me/drive/root:/FolderAA/FileBB.txt:/content");
	 * putRequest.addHeader("Content-Type", "text/plain");
	 * putRequest.setPayload("ABC");
	 *
	 * @param data
	 */
	@JSFunction
	public void setPayload(String data)
	{
		request.setPayload(data);//TODO file or other types
	}

	/**
	 * Add a body or a query string parameter, depending on the request type.
	 * If the request allows a body (POST, PUT, DELETE, PATCH) then it adds it as a body parameter.
	 * Otherwise it is added as a query string parameter.
	 * @param key the parameter name
	 * @param value the parameter value
	 */
	@JSFunction
	public void addParameter(String key, String value)
	{
		request.addParameter(key, value);
	}

	/**
	 * Add an OAuth parameter, like 'scope', 'realm' or with the 'oauth_' prefix
	 * @param key one of 'scope', 'realm' or starting with 'oauth_'
	 * @param value the oauth parameter value
	 */
	@JSFunction
	public void addOAuthParameter(String key, String value)
	{
		request.addOAuthParameter(key, value);
	}

	/**
	 * Add a query string parameter.
	 * @param key the query string parameter name
	 * @param value the parameter value
	 */
	@JSFunction
	public void addQuerystringParameter(String key, String value)
	{
		request.addQuerystringParameter(key, value);
	}

	/**
	 * Execute a request that was created with the OAuth service.
	 *
	 * @sample
	 * var request = service.createRequest(plugins.oauth.RequestType.GET, "https://api.linkedin.com/v2/me");
	 * request.addHeader("Accept", "application/json");
	 *
	 * var response = request.execute();
	 * if (response.getCode() == 200) {
	 * 		var json = response.getAsJSON();
	 *		application.output("Name is "+json.firstName);
	 *	}
	 * else
	 * {
	 * 		application.output("ERROR http status "+response.getCode());
	 * 		application.output(response.getBody())
	 * }
	 *
	 *
	 * @return the OAuthResponse object
	 */
	@JSFunction
	public OAuthResponse execute()
	{
		return service.execute(this.getRequest());
	}

	OAuthRequest getRequest()
	{
		return request;
	}
}
