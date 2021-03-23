/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2019 Servoy BV

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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.annotations.JSFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.utils.Preconditions;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.base.solutionmodel.IBaseSMVariable;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IAllWebClientPluginAccess;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.solutionmodel.ISolutionModel;
import com.servoy.j2db.util.Debug;

/**
 * @author emera
 */
@ServoyDocumented(scriptingName = "OAuthServiceBuilder")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthServiceBuilder implements IScriptable, IJavaScriptType
{
	private Function _callback;
	private final ServiceBuilder builder;
	private String _tenant;
	private String _state;
	private int _timeout;
	private String _deeplink;
	private final OAuthProvider provider;
	private long redirectToAuthUrlTime;

	private static final String GET_CODE_METHOD = "getSvyOAuthCode";
	private static final String SVY_AUTH_CODE_VAR = "svy_authCode";
	private static final String GLOBALS_SCOPE = "globals";
	private static final String DEFAULT_GET_FUNCTION = "function getSvyOAuthCode(){ return svy_authCode; }";
	private static final String DEFAULT_DEEPLINK_FUNCTION_BODY = "(a,b){ svy_authCode = b; }";
	private static final String DEEPLINK_METHOD_NAME = "deeplink_svy_oauth";
	private static final Logger log = LoggerFactory.getLogger(OAuthServiceBuilder.class.getCanonicalName());

	public OAuthServiceBuilder(OAuthProvider provider, String clientID)
	{
		this.provider = provider;
		this.builder = new ServiceBuilder(clientID);
	}

	/**
	 * Configure the service with a callback function to be executed when the service is ready to use.
	 * After the access token is returned by the server, this callback function is executed.
	 * @param callback a function in a scope or form
	 * @param timeout max number of seconds in which the callback method should be executed (with success or error message)
	 * 			Please note that the timeout should be enough for the user to login and accept permissions.
	 * @return the service builder for method chaining
	 */
	@JSFunction
	public OAuthServiceBuilder callback(Function callback, int timeout)
	{
		this._timeout = timeout;
		this._callback = callback;
		return this;
	}


	/**
	 * Set the client secret of the application.
	 * @param clientSecret a secret known only to the application and the authorization server
	 * @return the service builder for method chaining
	 */
	@JSFunction
	public OAuthServiceBuilder clientSecret(String clientSecret)
	{
		Preconditions.checkEmptyString(clientSecret, "Invalid client secret");
		builder.apiSecret(clientSecret);
		return this;
	}

	/**
	 * Request always the same scope.
	 * Scope is a mechanism in OAuth 2.0 to limit an application's access to a user's account.
	 * An application can request one or more scopes, separated by space.
	 * This information is then presented to the user in the consent screen, and the access token issued
	 * to the application will be limited to the scopes granted.
	 * @param scope the default scope
	 * @return the service builder for method chaining
	 */
	@JSFunction
	public OAuthServiceBuilder defaultScope(String scope)
	{
		builder.defaultScope(scope);
		return this;
	}

	/**
	 * Request any unique scope per each access token request.
	 * Scope is a mechanism in OAuth 2.0 to limit an application's access to a user's account.
	 * An application can request one or more scopes, separated by space.
	 * This information is then presented to the user in the consent screen, and the access token issued
	 * to the application will be limited to the scopes granted.
	 * @param scope one or multiple scopes separated by space
	 * @return the service builder for method chaining
	 */
	@JSFunction
	public OAuthServiceBuilder scope(String scope)
	{
		builder.withScope(scope);
		return this;
	}

	/**
	 * Configures the anti forgery session state. This is required in some APIs (like Facebook's).
	 * @param state
	 * @return the service builder for method chaining
	 */
	@JSFunction
	public OAuthServiceBuilder state(String state)
	{
		this._state = state;
		return this;
	}

	/**
	 * OPTIONAL This is a way to override the default deeplink method name, which is 'deeplink_svy_oauth'.
	 * The deeplink method is a global method that receives the code needed to obtain the access token from the OAuth provider.
	 *
	 * NOTE: The deeplink method name is strongly related to the redirect url configured for the application.
	 * If the OAuth provider (eg. Microsoft AD, Likedin) requires to configure a full redirect url then it should be of the form:
	 * https://example.com/<solution_name>/m/<deeplinkmethod> - where <deeplinkmethod> is the name configured with the service builder
	 * https://example.com/<solution_name>/m/deeplink_svy_oauth - if the deeplink method name was not overridden
	 *
	 * If the deeplink method with the provided name does not exist in the solution,
	 * then a default deeplink method is generated under the hood with the solution model.
	 * If a global method with the provided name already exists in the solution, then it should set the access
	 * token on the service and handle possible errors.
	 *
	 * @param deeplink a global scope method name
	 * @return the service builder for method chaining
	 */
	@JSFunction
	public OAuthServiceBuilder deeplink(String deeplink)
	{
		_deeplink = deeplink;
		return this;
	}

	/**
	 * Set the tenant identifiers/organization if the API supports it (e.g.Microsoft AD)
	 * @param tenant
	 * @return the service builder for method chaining
	 */
	@JSFunction
	public OAuthServiceBuilder tenant(String tenant)
	{
		this._tenant = tenant;
		return this;
	}


	/**
	 * Creates an OAuth service that can be used to obtain an access token and access protected data.
	 * @param api an OAuth provider id, see plugins.oauth.OAuthProviders
	 * @return an OAuthService object that can be used to make signed requests to the api
	 * @throws Exception if the service cannot be created
	 */
	@JSFunction
	public OAuthService build(String api) throws Exception
	{
		boolean generateGlobalMethods = _deeplink == null || provider.getPluginAccess().getSolutionModel().getGlobalMethod(GLOBALS_SCOPE, _deeplink) == null;
		if (generateGlobalMethods && _callback == null)
		{
			throw new Exception("Cannot build OAuth service. Please specify a callback function, see serviceBuilder.callback() docs for more details.");
		}
		String deeplink_name = _deeplink == null ? DEEPLINK_METHOD_NAME : _deeplink;
		builder.callback(provider.getRedirectURL(deeplink_name));
		if (log.isDebugEnabled()) log.debug("Redirect url " + provider.getRedirectURL(deeplink_name));

		OAuthService service = new OAuthService(builder.build(OAuthProvider.getApiInstance(api, _tenant)), _state);
		return _callback != null ? buildWithCallback(generateGlobalMethods, deeplink_name, service) : service;
	}

	private OAuthService buildWithCallback(boolean generateGlobalMethods, String deeplink_name, OAuthService service)
	{
		ISolutionModel sm = provider.getPluginAccess().getSolutionModel();
		if (generateGlobalMethods)
		{
			sm.newGlobalVariable(GLOBALS_SCOPE, SVY_AUTH_CODE_VAR, IBaseSMVariable.MEDIA);
			sm.newGlobalMethod(GLOBALS_SCOPE, "function " + deeplink_name + DEFAULT_DEEPLINK_FUNCTION_BODY);
			sm.newGlobalMethod(GLOBALS_SCOPE, DEFAULT_GET_FUNCTION);
		}

		try
		{
			String authURL = service.getAuthorizationURL();
			if (log.isDebugEnabled()) log.debug("authorization url " + authURL);
			ExecutorService executor = Executors.newFixedThreadPool(1);
			executor.submit(() -> {
				try
				{
					Object code = null;
					int count = 0;
					String errorMessage = null;
					if (generateGlobalMethods)
					{
						while ((code = provider.getPluginAccess().executeMethod(null, GET_CODE_METHOD, new Object[] { }, false)) == null && count++ <= _timeout)
						{
							TimeUnit.SECONDS.sleep(1);
						}

						if (code instanceof Scriptable)
						{
							Scriptable result = ((Scriptable)code);
							if (result.has("code", result))
							{
								try
								{
									if (log.isDebugEnabled()) log.debug("Received code in " + (System.currentTimeMillis() - redirectToAuthUrlTime) / 1000 +
										"s since the beginning of the request.");
									service.setAccessToken((String)result.get("code", result));
									if (log.isDebugEnabled())
										log.debug("Received access token in  " + (System.currentTimeMillis() - redirectToAuthUrlTime) / 1000 +
											"s since the beginning of the request.");
								}
								catch (Exception e)
								{
									errorMessage = "Could not set the oauth code";
									Debug.error("Could not set the oauth code " + e.getMessage(), e);
								}
							}
							else
							{
								//error is required by the protocol if code is not present, should not be null
								//error_message is optional, but human readable, so we prefer that one
								errorMessage = result.has("error_message", result) ? (String)result.get("error_message", result)
									: (String)result.get("error", result);
							}
						}
						else
						{
							errorMessage = "Request timed out. Did not obtain the authorization code within " + _timeout + "seconds";
						}
						sm.removeGlobalMethod(GLOBALS_SCOPE, deeplink_name);
						sm.removeGlobalMethod(GLOBALS_SCOPE, GET_CODE_METHOD);
						sm.removeGlobalVariable(GLOBALS_SCOPE, SVY_AUTH_CODE_VAR);
					}
					else
					{
						while (service.getAccessToken() == null && count++ <= _timeout)
						{
							TimeUnit.SECONDS.sleep(1);
						}
						if (service.getAccessToken() == null)
						{
							errorMessage = "Request timed out. Did not obtain the authorization code within " + _timeout + "seconds.";
						}
					}

					executor.shutdown();

					FunctionDefinition fd = new FunctionDefinition(_callback);
					if (log.isDebugEnabled())
					{
						log.debug(
							"Callback function called in " + (System.currentTimeMillis() - redirectToAuthUrlTime) / 1000 +
								"s since the beginning of the request.");
					}
					fd.executeAsync(provider.getPluginAccess(),
						new Object[] { errorMessage != null ? Boolean.FALSE : Boolean.TRUE, errorMessage != null ? errorMessage : service });
				}
				catch (InterruptedException e)
				{
					throw new IllegalStateException("Task interrupted", e);
				}
				catch (Exception e)
				{
					Debug.error(e);
				}
			});

			if (!((IAllWebClientPluginAccess)provider.getPluginAccess()).showURL(authURL, "_self", null))
			{
				Debug.error("Could not redirect to the login page.");
			}
			redirectToAuthUrlTime = System.currentTimeMillis();
		}
		catch (Exception e)
		{
			Debug.error(e);
			return null;
		}
		return service;
	}
}
