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
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.annotations.JSFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.scribejava.apis.openid.OpenIdOAuth2AccessToken;
import com.github.scribejava.core.exceptions.OAuthException;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.AuthorizationUrlBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Utils;

/**
 * <p>The <code>OAuthService</code> object provides a robust interface for managing
 * OAuth authentication and handling authorized requests. It allows developers to
 * create, configure, and execute HTTP requests such as GET, POST, PUT, and DELETE,
 * ensuring secure access to resources. Developers can also retrieve and manage tokens
 * for maintaining session continuity with the OAuth provider.</p>
 *
 * <p>With methods like <code>createGetRequest()</code> and <code>executeRequest()</code>,
 * the service supports constructing and executing customized requests. Token management
 * is facilitated through methods like <code>getAccessToken()</code>, <code>refreshToken()</code>,
 * and <code>revokeToken()</code>, which ensure proper handling of authentication tokens.
 * The service also includes utilities for token expiration checks and obtaining OpenID
 * tokens where supported.</p>
 *
 * <p>The <code>OAuthService</code> integrates seamlessly with OAuth flows, enabling
 * developers to work efficiently with access and refresh tokens, authorization URLs,
 * and custom request configurations. For additional details about the
 * <a href="https://docs.servoy.com/guides/develop/security/authentication#oauth-provider">
 * OAuth authentication</a>, refer to the
 * <a href="https://docs.servoy.com/guides/develop/security/authentication">
 * Authentication</a> section of this documentation.</p>
 *
 * @author emera
 */
@ServoyDocumented(scriptingName = "OAuthService")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthService implements IScriptable, IJavaScriptType
{
	public static final Logger log = LoggerFactory.getLogger("plugin.oauth");

	private final OAuth20Service service;
	private OAuth2AccessToken accessToken;
	private String _refreshToken;
	private final String state;
	private Long accessTokenExpire = null;
	private String idToken;

	public OAuthService(OAuth20Service service, String state)
	{
		this.service = service;
		this.state = state;
	}

	/**
	 *
	 * @return The authorization URL for initiating the OAuth flow.
	 */
	@JSFunction
	public String getAuthorizationURL()
	{
		return state != null ? service.getAuthorizationUrl(state) : service.getAuthorizationUrl();
	}

	/**
	 * Get the authorization url with some additional parameters.
	 * @param params  a json containing the parameters and their values
	 * 		e.g. {'param1': 'value1', 'param2': 'value2'}
	 * @return the authorization url with the provided parameters appended to the query string.
	 */
	@JSFunction
	public String getAuthorizationURL(Object params)
	{
		if (params == null) return getAuthorizationURL();

		HashMap<String, String> additionalParameters = new HashMap<String, String>();
		if (params instanceof Scriptable)
		{
			Scriptable scriptable = (Scriptable)params;
			for (Object id : scriptable.getIds())
			{
				if (id instanceof String && scriptable.get((String)id, null) instanceof String)
				{
					additionalParameters.put((String)id, (String)scriptable.get((String)id, null));
				}
			}
		}
		if (state != null) additionalParameters.put("state", state);
		return service.getAuthorizationUrl(additionalParameters);
	}

	public AuthorizationUrlBuilder getAuthorizatinUrlBuilder()
	{
		return service.createAuthorizationUrlBuilder();
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
			if (accessToken != null && accessToken.getExpiresIn() != null)
			{
				this.accessTokenExpire = System.currentTimeMillis() + accessToken.getExpiresIn().longValue() * 1000;
			}
		}
		catch (IOException | InterruptedException | ExecutionException e)
		{
			log.error("Could not set the access token.", e);
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
			if (accessToken != null && accessToken.getExpiresIn() != null)
			{
				this.accessTokenExpire = System.currentTimeMillis() + accessToken.getExpiresIn().longValue() * 1000;
			}
		}
		catch (IOException | InterruptedException | ExecutionException e)
		{
			log.error("Could not set the access token.", e);
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
		return !Utils.stringIsEmpty(accessToken.getRefreshToken()) ? accessToken.getRefreshToken() : _refreshToken;
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
		if (Utils.stringIsEmpty(accessToken.getRefreshToken()) && Utils.stringIsEmpty(_refreshToken))
			throw new Exception("Could not refresh the access token, the access token does not contain a refresh token.");

		return refreshToken(accessToken.getRefreshToken(), accessToken.getScope());
	}

	/**
	 * Obtains a new access token based on the refresh token, if the OAuth api supports it.
	 * @sample
	 * accessToken = service.refreshToken(theRefreshToken, scope);
	 *
	 * @param refreshToken the refresh token string
	 * @param scope optional, if missing then the default scope configured on the service is used
	 * @return The new access token issued by the authorization server
	 * @throws Exception if refreshing the token failed
	 */
	@JSFunction
	public String refreshToken(String refreshToken, String scope) throws Exception
	{
		if (Utils.stringIsEmpty(refreshToken))
			throw new Exception("Could not refresh the access token, the provided refresh token is blank.");

		try
		{
			accessToken = service.refreshAccessToken(refreshToken, scope != null ? scope : service.getDefaultScope());
			if (accessToken != null)
			{
				if (accessToken.getExpiresIn() != null)
				{
					this.accessTokenExpire = System.currentTimeMillis() + accessToken.getExpiresIn().longValue() * 1000;
				}
				_refreshToken = !Utils.stringIsEmpty(accessToken.getRefreshToken()) ? accessToken.getRefreshToken() : refreshToken;
				return accessToken.getAccessToken();
			}
			return null;
		}
		catch (Exception e)
		{
			log.error("Could not get a new access token", e);
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
		if (accessToken == null) throw new Exception("Could not get access token expire information, the access token was not set on the service.");
		if (accessTokenExpire == null) throw new Exception("The access token expire information is not available.");
		return (accessTokenExpire.longValue() - System.currentTimeMillis()) / 1000;
	}

	/**
	 * Checks if the access token is expired. Returns false if the access token expire information is not set.
	 * @return true if the access token is expired, false otherwise
	 */
	@JSFunction
	public boolean isAccessTokenExpired()
	{
		if (accessTokenExpire == null) return false;
		return System.currentTimeMillis() >= accessTokenExpire.longValue();
	}

	/**
	 * Return the token lifetime in seconds.
	 * @return the token lifetime as it was retrieved by the OAuth provider with the access token
	 * @throws Exception  if the access token was not set
	 */
	@JSFunction
	public int getAccessTokenLifetime() throws Exception
	{
		if (accessToken == null) throw new Exception("Could not get access token lifetime, the access token was not set on the service.");
		if (accessTokenExpire == null) throw new Exception("Could not get access token lifetime, the access token expire information is not available.");
		return accessToken.getExpiresIn().intValue();
	}

	/**
	 * Revoke the provided access token.
	 * @param token to revoke
	 * @throws Exception
	 */
	@JSFunction
	public void revokeToken(String token) throws Exception
	{
		service.revokeToken(token);
		accessTokenExpire = null;
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
			log.error("Could not execute request " + req.getUrl(), e);
		}
		return null;
	}

	/**
	 * This is for the implicit grant flow, when we don't need to make a second request because the response already contains the access token.
	 */
	public void setToken(String result)
	{
		try
		{
			this.accessToken = service.getApi().getAccessTokenExtractor().extract(new Response(200, null, null, result));
		}
		catch (OAuthException | IOException e)
		{
			log.error("Could not set the access token.", e);
		}
	}

	public void setIdToken(String string)
	{
		this.idToken = string;
	}

	/**
	 * Obtain the Openid token if it is available.
	 * @return the id token, or null if was not set on the service.
	 */
	@JSFunction
	public String getIdToken()
	{
		if (idToken == null && accessToken instanceof OpenIdOAuth2AccessToken)
		{
			((OpenIdOAuth2AccessToken)accessToken).getOpenIdToken();
		}
		return idToken;
	}

	/**
	 * @param params
	 * @throws Exception
	 */
	public void setAccessToken(AccessTokenRequestParams params) throws Exception
	{
		try
		{
			this.accessToken = service.getAccessToken(params);
			if (accessToken != null && accessToken.getExpiresIn() != null)
			{
				this.accessTokenExpire = System.currentTimeMillis() + accessToken.getExpiresIn().longValue() * 1000;
			}
		}
		catch (IOException | InterruptedException | ExecutionException e)
		{
			log.error("Could not set the access token.", e);
			throw new Exception("Could not set the access token. See the log for more details");
		}

	}

	public void setAccessTokenClientCredentialsGrant() throws Exception
	{
		try
		{
			this.accessToken = service.getAccessTokenClientCredentialsGrant();
			if (accessToken != null && accessToken.getExpiresIn() != null)
			{
				this.accessTokenExpire = System.currentTimeMillis() + accessToken.getExpiresIn().longValue() * 1000;
			}
		}
		catch (IOException | InterruptedException | ExecutionException e)
		{
			log.error("Could not set the client credentials access token.", e);
			throw new Exception("Could not set the client credentials access token. See the log for more details");
		}
	}
}