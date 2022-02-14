/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2021 Servoy BV

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

import java.lang.reflect.Method;

import org.mozilla.javascript.annotations.JSFunction;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * Enables the use of the OAuth plugin with uncommon providers such as in-house solutions.
 * @author emera
 */
@ServoyDocumented(publicName = "CustomApiBuilder", scriptingName = "CustomApiBuilder")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class CustomApiBuilder
{
	private final String authorizationBaseUrl;
	private final String accessTokenEndpoint;
	private Verb requestMethod = Verb.POST;
	private String _refreshTokenEndpoint;
	private String _revokeTokenEndpoint;
	private TokenExtractor<OAuth2AccessToken> tokenExtractor;
	private ClientAuthentication _clientAuthentication;

	public CustomApiBuilder(String authorizationBaseUrl, String accessTokenEndpoint)
	{
		super();
		if (authorizationBaseUrl == null) throw new NullPointerException("The authorization base url cannot be null");
		if (accessTokenEndpoint == null) throw new NullPointerException("The accessToken endpoint cannot be null");
		this.authorizationBaseUrl = cleanURL(authorizationBaseUrl);
		this.accessTokenEndpoint = cleanURL(accessTokenEndpoint);
	}

	/**
	 * @param authorizationBaseUrl
	 * @return
	 */
	private String cleanURL(String toClean)
	{
		if (toClean == null) return null;
		String url = toClean.trim();
		return url.endsWith("/") ? url.substring(0, url.length() - 1)
			: url;
	}

	/**
	 * The request method used for the access token endpoint (defaults to POST).
	 * @param tokenRequestMethod can be 'post' or 'get'
	 * @return the api builder for method chaining
	 */
	@JSFunction
	public CustomApiBuilder withAccessTokenMethod(String tokenRequestMethod)
	{
		if ("get".equalsIgnoreCase(tokenRequestMethod))
		{
			this.requestMethod = Verb.GET;
		}
		return this;
	}

	/**
	 * Configure the api with the URL that receives the refresh token requests.
	 * @param refreshTokenEndpoint
	 * @return the api builder for method chaining
	 */
	@JSFunction
	public CustomApiBuilder withRefreshTokenEndpoint(String refreshTokenEndpoint)
	{
		this._refreshTokenEndpoint = cleanURL(refreshTokenEndpoint);
		return this;
	}

	/**
	 * Configure the api with the URL that receives the revoke token requests.
	 * @param revokeTokenEndpoint
	 * @return the api builder for method chaining
	 */
	@JSFunction
	public CustomApiBuilder withRevokeTokenEndpoint(String revokeTokenEndpoint)
	{
		this._revokeTokenEndpoint = cleanURL(revokeTokenEndpoint);
		return this;
	}

	/**
	 * Configures the api with a token extractor which parses the concrete type of token from the response string.
	 * @param accessTokenExtractor see plugins.oauth.OAuthTokenExtractors
	 * @return the api builder for method chaining
	 * @throws Exception if the token extractor cannot be created
	 */
	@JSFunction
	public CustomApiBuilder withAccessTokenExtractor(String accessTokenExtractor) throws Exception
	{
		Class< ? > clazz = Class.forName(accessTokenExtractor);
		Method instance = clazz.getDeclaredMethod("instance");
		if (instance != null && TokenExtractor.class.isAssignableFrom(clazz))
		{
			tokenExtractor = (TokenExtractor<OAuth2AccessToken>)instance.invoke(null, (Object[])null);
		}
		else
		{
			throw new Exception("'" + accessTokenExtractor + "' extractor was not found or is not a Token Extractor");
		}
		return this;
	}

	/**
	 * Configures the api with a client authentication method which specifies how the client credentials are sent.
	 * They can be sent as basic Auth header or in the request body.
	 * @param clientAuthentication see plugins.oauth.ClientAuthentication
	 * @return the api builder for method chaining
	 * @throws Exception if the client authentication cannot be created
	 */
	@JSFunction
	public CustomApiBuilder withClientAuthentication(String clientAuthentication) throws Exception
	{
		Class< ? > clazz = Class.forName(clientAuthentication);
		Method instance = clazz.getDeclaredMethod("instance");
		if (instance != null && ClientAuthentication.class.isAssignableFrom(clazz))
		{
			_clientAuthentication = (ClientAuthentication)instance.invoke(null, (Object[])null);
		}
		else
		{
			throw new Exception("'" + clientAuthentication + "' was not found or is not a client authentication type");
		}
		return this;
	}

	public DefaultApi20 build()
	{
		return new DefaultApi20()
		{
			@Override
			protected String getAuthorizationBaseUrl()
			{
				return authorizationBaseUrl;
			}

			@Override
			public String getAccessTokenEndpoint()
			{
				return accessTokenEndpoint;
			}

			@Override
			public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor()
			{
				return tokenExtractor != null ? tokenExtractor : super.getAccessTokenExtractor();
			}

			@Override
			public Verb getAccessTokenVerb()
			{
				return requestMethod;
			}

			@Override
			public String getRefreshTokenEndpoint()
			{
				return _refreshTokenEndpoint != null ? _refreshTokenEndpoint : super.getRefreshTokenEndpoint();
			}

			@Override
			public String getRevokeTokenEndpoint()
			{
				return _revokeTokenEndpoint != null ? _revokeTokenEndpoint : super.getRevokeTokenEndpoint();
			}

			@Override
			public ClientAuthentication getClientAuthentication()
			{
				return _clientAuthentication != null ? _clientAuthentication : super.getClientAuthentication();
			}
		};
	}
}
