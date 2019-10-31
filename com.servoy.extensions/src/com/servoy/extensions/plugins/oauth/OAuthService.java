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
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * @author emera
 */
@ServoyDocumented(scriptingName = "OAuthService")
public class OAuthService implements IScriptable, IJavaScriptType
{

	private final OAuth20Service service;
	private OAuth2AccessToken accessToken;
	private final String state;
	private long accessTokenExpire;

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

	/**
	 * Configure the oauth service with an access token using the scope that was initially set when creating the service.
	 * @param code the authorization code used to request and access token
	 * @throws Exception if the access token cannot be obtained
	 */
	@JSFunction
	public void setAccessToken(String code) throws Exception
	{
		try
		{
			this.accessToken = service.getAccessToken(code);
			this.accessTokenExpire = System.currentTimeMillis() + accessToken.getExpiresIn().intValue() * 1000;
		}
		catch (IOException | InterruptedException | ExecutionException e)
		{
			Debug.error("Could not set the access token.", e);
			throw new Exception("Could not set the access token. See the log for more details");
		}
	}

	/**
	 * Configure the oauth service with an access token for the specified scope.
	 * @param code the authorization code used to request an access token
	 * @param scope the scope for which to obtain an access token
	 * @throws Exception if the token cannot be obtained
	 */
	@JSFunction
	public void setAccessToken(String code, String scope) throws Exception
	{
		try
		{
			this.accessToken = service.getAccessToken(AccessTokenRequestParams.create(code).scope(scope));
			this.accessTokenExpire = System.currentTimeMillis() + accessToken.getExpiresIn().intValue() * 1000;
		}
		catch (IOException | InterruptedException | ExecutionException e)
		{
			Debug.error("Could not set the access token.", e);
			throw new Exception("Could not set the access token. See the log for more details");
		}
	}

	/**
	 * Get the access token currently set on the service.
	 * @return the access token or null if it was not set
	 */
	@JSFunction
	public String getAccessToken()
	{
		return accessToken != null ? accessToken.getAccessToken() : null;
	}

	/**
	 * Return the refresh token.
	 * @return the refresh token or null if it is not present
	 * @throws Exception if the access token was not set on the service
	 */
	@JSFunction
	public String getRefreshToken() throws Exception
	{
		if (accessToken == null) throw new Exception("Could not refresh the access token, the access token was not set on the service.");
		return accessToken.getRefreshToken();
	}

	/**
	 * Obtains a new access token if the OAuth api supports it.
	 * @sample
	 * accessToken = service.refreshToken();
	 *
	 * @return The new access token issued by the authorization server
	 * @throws Exception if the access token was not set or if the api does not support refreshing the token
	 */
	@JSFunction
	public String refreshToken() throws Exception
	{
		if (accessToken == null) throw new Exception("Could not refresh the access token, the access token was not set on the service.");
		if (accessToken.getRefreshToken() == null || "".equals(accessToken.getRefreshToken()))
			throw new Exception("Could not refresh the access token, the access token does not contain a refresh token.");
		try
		{
			accessToken = service.refreshAccessToken(accessToken.getRefreshToken(), accessToken.getScope());
			this.accessTokenExpire = System.currentTimeMillis() + accessToken.getExpiresIn().intValue() * 1000;
			return accessToken.getAccessToken();
		}
		catch (Exception e)
		{
			Debug.error("Could not get a new access token", e);
			throw new Exception("Could not get a new access token  " + e.getMessage());
		}
	}

	/**
	 * Returns the number of seconds left until the access token expires.
	 * @sample
	 *  var seconds = service.getAccessExpiresIn();
	 *  if (seconds < 60)
	 *  {
	 *  	application.output("The access token is going to expire in less than 1 minute! Use service.refreshToken() to get a new one");
	 *  }
	 *  else
	 *  {
	 *  	application.output("Make some requests");
	 *  }
	 *
	 * @return seconds left untol the access token expires.
	 * @throws Exception if the access token was not set
	 */
	@JSFunction
	public long getAccessExpiresIn() throws Exception
	{
		if (accessToken == null) throw new Exception("Could getAccessExpiresIn, the access token was not set on the service.");
		return (accessTokenExpire - System.currentTimeMillis()) / 1000;
	}

	/**
	 * Checks if the access token is expired.
	 * @return true if the access token is expired, false otherwise
	 */
	@JSFunction
	public boolean isAccessTokenExpired()
	{
		return System.currentTimeMillis() >= accessTokenExpire;
	}

	/**
	 * Return the token lifetime in seconds.
	 * @return the token lifetime as it was retrieved by the OAuth provider with the access token
	 */
	@JSFunction
	public int getAccessTokenLifetime()
	{
		return accessToken.getExpiresIn().intValue();
	}

	/**
	 * Creates a JSOAuthRequest for with the enum of RequestType (GET, PUT, DELETE, etc) for a resource url.
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
	 * @param requestType one of the types of plugins.oauth.RequestType
	 * @param resourceURL the url of the resource you want to access
	 * @return a JSOAuthRequest object
	 */
	@JSFunction
	public JSOAuthRequest createRequest(Verb requestType, String resourceURL)
	{
		return new JSOAuthRequest(this, requestType, resourceURL);
	}

	/**
	 * Create a GET request for a resource.
	 *
	 * @sample
	 * var getRequest = service.createGetRequest("https://api.linkedin.com/v2/me");
	 * getRequest.addHeader("Accept", "application/json");
	 *
	 * var response = getRequest.execute();
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
	 * @param resourceURL the url of the resource which you want to get
	 * @return the request object
	 */
	@JSFunction
	public JSOAuthRequest createGetRequest(String resourceURL)
	{
		return createRequest(Verb.GET, resourceURL);
	}

	/**
	 * Create a POST request.
	 *
	 * @sample
	 * var postRequest = service.createPostRequest("https://.....");
	 * postRequest.addHeader("Content-Type", "text/plain");
	 * postRequest.addBodyParameter("param1", "value1");
	 * var response = postRequest.execute();
	 *
	 * @param resourceURL the url where the enclosed entity will be stored
	 * @return the request object
	 */
	@JSFunction
	public JSOAuthRequest createPostRequest(String resourceURL)
	{
		return createRequest(Verb.POST, resourceURL);
	}

	/**
	 * Create a PUT request.
	 *
	 * @sample
	 * var putRequest = service.createPutRequest("https://graph.microsoft.com/v1.0/me/drive/root:/FolderAA/FileBB.txt:/content");
	 * putRequest.addHeader("Content-Type", "text/plain");
	 * putRequest.setPayload("ABC");
	 * var response = putRequest.execute();
	 * if (response.getCode() == 201) {
	 *		application.output("New file was created "+response.getBody());
	 *	}
	 * else
	 * {
	 * 		application.output("ERROR http status "+response.getCode());
	 * 		application.output("File could not be created: "+response.getBody())
	 * }
	 *
	 * @param resourceURL the url where the enclosed entity will be stored
	 * @return the request object
	 */
	@JSFunction
	public JSOAuthRequest createPutRequest(String resourceURL)
	{
		return createRequest(Verb.PUT, resourceURL);
	}

	/**
	 * Create a DELETE request.
	 *
	 * @sample
	 * var putRequest = service.createDeleteRequest("https://graph.microsoft.com/v1.0/me/drive/root:/FolderAA/FileBB.txt:/content");
	 * var response = putRequest.execute();
	 * if (response.getCode() == 204) {
	 *		application.output("File was deleted "+response.getBody());
	 *	}
	 * else
	 * {
	 * 		application.output('http status '+response.getCode());
	 * 		application.output("File could not be deleted: "+response.getBody())
	 * }
	 *
	 * @param resourceURL the url of the resource to be deleted
	 * @return the request object
	 */
	@JSFunction
	public JSOAuthRequest createDeleteRequest(String resourceURL)
	{
		return createRequest(Verb.DELETE, resourceURL);
	}


	/**
	 * This is quick method by executing a GET request and returning right away the OAuthResponse
	 * So it would be the same as executeRequest(createRequest(RequestType.GET, url))
	 * @param resourceURL
	 * @return the OAuthResponse object
	 */
	@JSFunction
	public OAuthResponse executeGetRequest(String resourceURL)
	{
		OAuthRequest request = new OAuthRequest(Verb.GET, resourceURL);
		return execute(request);
	}

	protected void checkAccessTokenExpired()
	{
		if (accessToken == null) throw new RuntimeException("Cannot execute request. Please set the acess token first.");
		if (isAccessTokenExpired())
		{
			try
			{
				refreshToken();
			}
			catch (Exception e)
			{
				throw new RuntimeException("Cannot execute request, access token is expired and could not refresh it");
			}
		}
	}

	/**
	 * Method to execute requests that are made, and configured by  {@link #createRequest(Verb, String)}
	 *
	 * @sample
	 * var request = service.createRequest(plugins.oauth.RequestType.GET, "https://api.linkedin.com/v2/me");
	 * request.addHeader("Accept", "application/json");
	 *
	 * var response = service.executeRequest(request);
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
	 * @param request the JSOAuthRequest object that was created by {@link #createRequest(Verb, String)}
	 * @return the OAuthResponse object
	 */
	@JSFunction
	public OAuthResponse executeRequest(JSOAuthRequest request)
	{
		if (request == null) return null;
		OAuthRequest req = request.getRequest();
		return execute(req);
	}

	OAuthResponse execute(OAuthRequest req)
	{
		checkAccessTokenExpired();
		service.signRequest(accessToken, req);
		try
		{
			Response response = service.execute(req);
			return ResponseFactory.create(response);
		}
		catch (InterruptedException | ExecutionException | IOException e)
		{
			Debug.error("Could not execute request " + req.getUrl(), e);
		}
		return null;
	}
}
