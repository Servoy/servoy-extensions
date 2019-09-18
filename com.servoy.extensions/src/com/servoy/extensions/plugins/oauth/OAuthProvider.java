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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.annotations.JSFunction;

import com.github.scribejava.apis.FacebookApi;
import com.github.scribejava.apis.LinkedInApi20;
import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.base.solutionmodel.IBaseSMVariable;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IAllWebClientPluginAccess;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.solutionmodel.ISolutionModel;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * @author emera
 */
@ServoyClientSupport(ng = true, wc = false, sc = false)
@ServoyDocumented(publicName = OAuthPlugin.PLUGIN_NAME, scriptingName = "plugins." + OAuthPlugin.PLUGIN_NAME)
public class OAuthProvider implements IScriptable, IReturnedTypesProvider
{
	private static final String GET_CODE_METHOD = "getSvyOAuthCode";
	private static final String SVY_AUTH_CODE_VAR = "svy_authCode";
	private static final String GLOBALS_SCOPE = "globals";
	private static final String DEFAULT_GET_FUNCTION = "function getSvyOAuthCode(){ return svy_authCode; }";
	private static final String DEFAULT_DEEPLINK_FUNCTION_BODY = "(a,b){ svy_authCode = b; }";
	private static final String DEEPLINK_METHOD_NAME = "deeplink_svy_oauth";
	private static final String SOLUTIONS_PATH = "solutions/";

	private final OAuthPlugin plugin;

	public OAuthProvider(OAuthPlugin oAuthPlugin)
	{
		this.plugin = oAuthPlugin;
	}

	/**
	 * Creates an OAuth service that can be used to obtain an access token and access protected data.
	 * @sample
	 * var clientId = "";
	 * var clientSecret = "";
	 * var scope = null;
	 * var state =  "secret123337";
	 * var callback = "deeplink";
	 * service = plugins.oauth.getOAuthService(plugins.oauth.OAuthProviders.FACEBOOK, clientId, clientSecret, null, state, callback, null)
	 * application.showURL(service.getAuthorizationURL());
	 *
	 * function deeplink(a,args) {
	 *   service.setAccessToken(args.code);
	 *   var response = service.executeGetRequest("https://graph.facebook.com/v2.11/me");
	 *   if (response.getCode() == 200) {
	 *   		 application.output(response.getBody());
	 *   		 var json = response.getAsJSON();
	 *   		 application.output("Name is "+json.name);
	 *    }
	 *   else {
	 *     application.output('ERROR http status '+response.getCode());
	 *     }
	 *  }
	 *
	 * @param provider an OAuth provider id, see plugins.oauth.OAuthProviders
	 * @param clientId your app id
	 * @param clientSecret your client secret
	 * @param scope configures the OAuth scope. This is only necessary in some APIs (like Microsoft's).
	 * @param state configures the anti forgery session state. This is available in some APIs (like Facebook's).
	 * @param deeplinkmethod the name of a global method, which will get the code returned by the OAuth provider
	 * @return
	 */
	@JSFunction
	public OAuthService getOAuthService(int provider, String clientId, String clientSecret, String scope, String state, String deeplinkmethod)
	{
		return getOAuthService(provider, clientId, clientSecret, scope, state, deeplinkmethod, null);
	}

	/**
	 * Creates an OAuth service that can be used to obtain an access token and access protected data.
	 * @sample
	 * var clientId = "";
	 * var clientSecret = "";
	 * var scope = null;
	 * var state =  "secret123337";
	 * var callback = "deeplink";
	 * service = plugins.oauth.getOAuthService(plugins.oauth.OAuthProviders.FACEBOOK, clientId, clientSecret, null, state, callback, null)
	 * application.showURL(service.getAuthorizationURL());
	 *
	 * function deeplink(a,args) {
	 *   service.setAccessToken(args.code);
	 *   var response = service.executeGetRequest("https://graph.facebook.com/v2.11/me");
	 *   if (response.getCode() == 200) {
	 *   		 application.output(response.getBody());
	 *   		 var json = response.getAsJSON();
	 *   		 application.output("Name is "+json.name);
	 *    }
	 *   else {
	 *     application.output('ERROR http status '+response.getCode());
	 *     }
	 *  }
	 *
	 * @param provider an OAuth provider id, see plugins.oauth.OAuthProviders
	 * @param clientId your app id
	 * @param clientSecret your client secret
	 * @param scope configures the OAuth scope. This is only necessary in some APIs (like Microsoft's).
	 * @param state configures the anti forgery session state. This is available in some APIs (like Facebook's).
	 * @param deeplinkmethod the name of a global method, which will get the code returned by the OAuth provider
	 * @param tenant tenant identifiers/organization (eg. Microsoft AD)
	 * @return
	 */
	@JSFunction
	public OAuthService getOAuthService(int provider, String clientId, String clientSecret, String scope, String state, String deeplinkmethod, String tenant)
	{
		ServiceBuilder builder = new ServiceBuilder(clientId);
		builder.apiSecret(clientSecret);
		if (scope != null) builder.defaultScope(scope);
		if (deeplinkmethod != null) builder.callback(getRedirectURL(deeplinkmethod));
		return new OAuthService(builder.build(getApiInstance(provider, tenant)), state);
	}

	/**
	 * Creates an OAuth service that can be used to obtain an access token and access protected data.
	 * Redirects to the OAuth provider login page
	 * and calls the provided callbackMethod when the service object is ready to use.
	 *
	 * @sample
	 * var clientId = "";
	 * var clientSecret = "";
	 * var scope = null;
	 * var state =  "secret123337";
	 * var timeout = 30; //wait at most 30 seconds
	 * plugins.oauth.getOAuthService(plugins.oauth.OAuthProviders.FACEBOOK, clientId, clientSecret, null, state, null, myFunction, timeout)
	 *
	 * function myFunction(result, auth_outcome) {
	 *	if (result)
	 *	{
	 *		//SUCCESS
	 *		var service = auth_outcome;
	 *  	if (service.getAccessToken() == null) return;
	 *		var response = service.executeGetRequest("https://graph.facebook.com/v2.11/me");
	 *   	if (response.getCode() == 200) {
	 *   		 application.output(response.getBody());
	 *   		 var json = response.getAsJSON();
	 *   		 application.output("Name is "+json.name);
	 *    	}
	 *   	else {
	 *    	 application.output('ERROR http status '+response.getCode());
	 *     	}
	 *	 else {
	 *		//ERROR
	 *		application.output("ERROR "+auth_outcome);//could not get access token, request timed out, etc..
	 *	 }
	 *	}
	 * }
	 *
	 * @param provider an OAuth provider id, see plugins.oauth.OAuthProviders
	 * @param clientId your app id
	 * @param clientSecret your client secret
	 * @param scope configures the OAuth scope. This is only necessary in some APIs (like Microsoft's).
	 * @param state configures the anti forgery session state. This is available in some APIs (like Facebook's).
	 * @param deeplinkmethod - a deeplink method name - should be provided when the OAuth provider (eg. Microsoft AD, Likedin) requires
	 * 				to configure a full redirect url, such as https://example.com/<solution_name>/m/<deeplinkmethod>
	 * 				is OPTIONAL when a callback is used,
	 * 				if missing then a default deeplink method is generated under the hood with the solution model,
	 * 				if present then should set the access token on the service
	 * @param callback an actual function to be called when the service is ready to use
	 * @param timeout max number of seconds in which the callbackmethod should be called (with success or error message)
	 * 			Please note that the timeout should be enough for the user to login and accept permissions.
	 * @return an OAuthService object that is ready to use when the callback method is executed.
	 */
	@JSFunction
	public OAuthService getOAuthService(int provider, String clientId, String clientSecret, String scope, String state, String deeplinkmethod, String tenant,
		Function callbackmethod, int timeout)
	{
		ServiceBuilder builder = new ServiceBuilder(clientId);
		builder.apiSecret(clientSecret);
		if (scope != null) builder.defaultScope(scope);
		ISolutionModel sm = plugin.getAccess().getSolutionModel();
		boolean generateGlobalMethods = deeplinkmethod == null || sm.getGlobalMethod(GLOBALS_SCOPE, deeplinkmethod) == null;
		String deeplink_name = deeplinkmethod == null ? DEEPLINK_METHOD_NAME : deeplinkmethod;
		builder.callback(getRedirectURL(deeplink_name));

		OAuthService service = new OAuthService(builder.build(getApiInstance(provider, tenant)), state);

		if (generateGlobalMethods)
		{
			//TODO check if we have a globals scope!
			sm.newGlobalVariable(GLOBALS_SCOPE, SVY_AUTH_CODE_VAR, IBaseSMVariable.MEDIA);
			sm.newGlobalMethod(GLOBALS_SCOPE, "function " + deeplink_name + DEFAULT_DEEPLINK_FUNCTION_BODY);
			sm.newGlobalMethod(GLOBALS_SCOPE, DEFAULT_GET_FUNCTION);
		}

		try
		{
			String authURL = service.getAuthorizationURL();
			ExecutorService executor = Executors.newFixedThreadPool(1);
			executor.submit(() -> {
				try
				{
					Object code = null;
					int count = 0;
					String errorMessage = null;
					if (generateGlobalMethods)
					{
						while ((code = plugin.getAccess().executeMethod(null, GET_CODE_METHOD, new Object[] { }, false)) == null && count++ <= timeout)
						{
							TimeUnit.SECONDS.sleep(1);
						}

						if (code != null)
						{
							JSONObject json = new JSONObject(Utils.getScriptableString(code));
							if (json.has("code"))
							{
								try
								{
									service.setAccessToken(json.optString("code"));
								}
								catch (Exception e)
								{
									errorMessage = "Could not set the oauth code";
								}
							}
							else
							{
								//error is required by the protocol if code is not present, should not be null
								//error_message is optional, but human readable, so we prefer that one
								errorMessage = json.optString("error_message") != null ? json.optString("error_message") : json.optString("error");
							}
						}
						else
						{
							errorMessage = "Request timed out. Did not obtain the authorization code within " + timeout + "seconds";
						}
						sm.removeGlobalMethod(GLOBALS_SCOPE, deeplink_name);
						sm.removeGlobalMethod(GLOBALS_SCOPE, GET_CODE_METHOD);
						sm.removeGlobalVariable(GLOBALS_SCOPE, SVY_AUTH_CODE_VAR);
					}
					else
					{
						while (service.getAccessToken() == null && count++ <= timeout)
						{
							TimeUnit.SECONDS.sleep(1);
						}
						if (service.getAccessToken() == null)
						{
							errorMessage = "Request timed out. Did not obtain the authorization code within " + timeout + "seconds.";
						}
					}

					executor.shutdown();

					FunctionDefinition fd = new FunctionDefinition(callbackmethod);
					fd.executeAsync(plugin.getAccess(),
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

			if (!((IAllWebClientPluginAccess)plugin.getAccess()).showURL(authURL, "_self", null))
			{
				Debug.error("Could not show url");
			}
		}
		catch (Exception e)
		{
			Debug.error(e);
			return null;
		}
		return service;
	}

	private String getRedirectURL(String callbackmethod)
	{
		String redirectURL = plugin.getAccess().getServerURL().toString();
		redirectURL += (!redirectURL.endsWith("/") ? "/" + SOLUTIONS_PATH : SOLUTIONS_PATH);
		redirectURL += plugin.getAccess().getSolutionName() + "/m/" + callbackmethod;
		return redirectURL;
	}

	private DefaultApi20 getApiInstance(int provider, String tenant)
	{
		switch (provider)
		{
			case OAuthProviders.MICROSOFT_AD :
				return tenant != null ? MicrosoftAzureActiveDirectory20Api.custom(tenant) : MicrosoftAzureActiveDirectory20Api.instance();
			case OAuthProviders.FACEBOOK :
				return FacebookApi.instance();
			case OAuthProviders.LINKEDIN :
				return LinkedInApi20.instance();
		}
		return null;
	}

	@Override
	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { OAuthService.class, OAuthProviders.class, OAuthResponseText.class, OAuthResponseJSON.class, OAuthResponseBinary.class, OAuthRequestType.class, JSOAuthRequest.class };
	}

}
