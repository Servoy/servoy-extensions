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
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p><code>JSOAuthRequest</code> provides a streamlined way to create and execute OAuth requests
 * within an application. It supports various HTTP verbs, such as GET, POST, PUT, DELETE, and PATCH,
 * enabling flexible interactions with OAuth-enabled APIs. This wrapper simplifies the process of
 * setting up requests by offering methods to add headers, parameters, and payloads.</p>
 *
 * <p>Headers can be defined using <code>addHeader</code>, and body parameters can be specified
 * with <code>addBodyParameter</code>. The <code>setPayload</code> function allows for setting the
 * request's body payload, useful for scenarios involving larger data transmissions. For adding
 * parameters, <code>addParameter</code> intelligently determines whether to place the parameter
 * in the body or as a query string based on the HTTP verb. Additionally,
 * <code>addQuerystringParameter</code> explicitly manages query string additions.</p>
 *
 * <p>The class also supports OAuth-specific parameters like <code>scope</code> and others prefixed
 * with <code>oauth_</code> through the <code>addOAuthParameter</code> method. The <code>execute</code>
 * method runs the configured request and returns an <code>OAuthResponse</code> object containing
 * details such as HTTP status codes and the response body.</p>
 *
 * @author emera
 */
@ServoyDocumented(publicName = "OAuthRequest", scriptingName = "OAuthRequest")
@ServoyClientSupport(ng = true, wc = false, sc = false)
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
