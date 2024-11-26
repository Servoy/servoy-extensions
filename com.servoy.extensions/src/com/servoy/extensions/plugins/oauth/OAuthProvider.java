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

import java.lang.reflect.Method;

import org.mozilla.javascript.annotations.JSFunction;

import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.extensions.plugins.oauth.apis.OktaApi;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>OAuth</code> plugin simplifies authentication and access to protected resources
 * using OAuth protocols. It supports integration with predefined OAuth providers or custom APIs,
 * enabling secure communication between applications and external services. Core functionalities
 * include creating OAuth services, managing access tokens, and executing authenticated requests.</p>
 *
 * <p>OAuth services can be configured through the <code>serviceBuilder</code>, which allows
 * the definition of parameters such as client ID, client secret, scope, and state for secure
 * sessions. The service builder also facilitates the integration of callback methods for
 * handling responses. For more specific use cases, developers can build custom APIs by defining
 * authorization and token endpoints, ensuring flexibility for various authentication workflows.</p>
 *
 * <p>The plugin provides utility methods to streamline the process. It can generate redirect
 * URLs for configuration on provider platforms and manage token extraction from responses. Once
 * authenticated, OAuth services enable the execution of HTTP requests to protected endpoints,
 * supporting various data formats such as JSON and binary. The plugin also facilitates error
 * handling by capturing and relaying issues related to authentication or access requests.</p>
 *
 * <p>By integrating the <code>OAuth</code> plugin, applications can securely authenticate users,
 * access external APIs, and handle sensitive data interactions while adhering to modern security
 * standards.</p>
 *
 * @author emera
 */
@ServoyClientSupport(ng = true, wc = false, sc = false)
@ServoyDocumented(publicName = OAuthPlugin.PLUGIN_NAME, scriptingName = "plugins." + OAuthPlugin.PLUGIN_NAME)
public class OAuthProvider implements IScriptable, IReturnedTypesProvider
{
	private static final String SOLUTIONS_PATH = "solutions/";
	private static final String SOLUTION_PATH = "solution/";
	private final OAuthPlugin plugin;

	public OAuthProvider()
	{
		this.plugin = null; // this is just for temporary instantiations where docs need to call IReturnedTypesProvider.getAllReturnedTypes()
	}

	public OAuthProvider(OAuthPlugin oAuthPlugin)
	{
		this.plugin = oAuthPlugin;
	}

	/**
	 * Creates an OAuth service configurator.
	 * @sample
	 * plugins.oauth.serviceBuilder("0lqd1s0aw...")		//client/application ID
	 * 				.clientSecret("bIk6163KHi...")		//client secret
	 * 				.defaultScope("email")				//ask permission to get the user email
	 * 				.state("secret123337")				//anti forgery session state, required by the Facebook api
	 * 				.deeplink("deeplink_method")		//OPTIONAL deeplink method name or last part of your redirect URL, see docs
	 * 													//if missing, a global method with the name 'deeplink_svy_oauth' will be generated
	 * 				.callback(myFunction, 30) 			//see function below, timeout is 30 seconds
	 * 				.build(plugins.oauth.OAuthProviders.FACEBOOK);
	 *
	 * function myFunction(result, auth_outcome) {
	 * if (result)
	 * {
	 * 		//SUCCESS
	 * 		var service = auth_outcome;
	 * 		if (service.getAccessToken() == null) return;
	 * 		var response = service.executeGetRequest("https://graph.facebook.com/v2.11/me");
	 * 		if (response.getCode() == 200) {
	 * 			application.output(response.getBody());
	 * 			var json = response.getAsJSON();
	 * 			application.output("Name is "+json.name);
	 *		}
	 *		else {
	 *			application.output('ERROR http status '+response.getCode());
	 *		}
	 *	else {
	 *		//ERROR
	 *		application.output("ERROR "+auth_outcome);//could not get access token, request timed out, etc..
	 *	}
	 *  }
	 * }
	 * @param clientID
	 * @return an OAuth service builder object
	 */
	@JSFunction
	public OAuthServiceBuilder serviceBuilder(String clientID)
	{
		return new OAuthServiceBuilder(this, clientID);
	}

	/**
	 * Creates an OAuth service that can be used to obtain an access token and access protected data.
	 * This method will be deprecated in the following versions, the preferred way is plugins.oauth.serviceBuilder with a callback function.
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
	 * @return the OAuthService.
	 */
	@JSFunction
	public OAuthService getOAuthService(String provider, String clientId, String clientSecret, String scope, String state, String deeplinkmethod)
		throws Exception
	{
		ServiceBuilder builder = new ServiceBuilder(clientId);
		builder.apiSecret(clientSecret);
		if (scope != null) builder.defaultScope(scope);
		if (deeplinkmethod != null) builder.callback(getRedirectURL(deeplinkmethod, false));
		return new OAuthService(builder.build(getApiInstance(provider, null, null)), state);
	}

	String getRedirectURL(String callbackmethod, boolean responseModeFragment)
	{
		StringBuilder redirectURL = new StringBuilder(getPluginAccess().getServerURL().toString());
		if (responseModeFragment)
		{
			if (!redirectURL.toString().endsWith("/"))
			{
				redirectURL.append("/");
			}
			redirectURL.append("servoy-service/oauth/");
		}
		String path = getPluginAccess().getRuntimeProperties().containsKey("NG2") && (Boolean)getPluginAccess().getRuntimeProperties().get("NG2")
			? SOLUTION_PATH
			: SOLUTIONS_PATH;
		redirectURL.append(!redirectURL.toString().endsWith("/") ? "/" + path : path);
		redirectURL.append(getPluginAccess().getMainSolutionName() + "/m/" + callbackmethod);
		return redirectURL.toString();
	}

	/**
	 * Help method to get the redirect URL which needs to be configured on the OAuth provider application page.
	 * The url is computed based on what is set on the service builder: deeplink method name, response mode and response type.
	 * @param builder an OAuth service builder
	 * @return the redirect url
	 */
	@JSFunction
	public String getUsedRedirectUrl(OAuthServiceBuilder builder)
	{
		return getRedirectURL(builder.getDeeplinkName(), builder.isFragmentResponse());
	}

	static DefaultApi20 getApiInstance(String provider, String tenant, String domain) throws Exception
	{
		switch (provider)
		{
			case OAuthProviders.MICROSOFT_AD :
				return tenant != null ? MicrosoftAzureActiveDirectory20Api.custom(tenant) : MicrosoftAzureActiveDirectory20Api.instance();
			case OAuthProviders.OKTA :
				return OktaApi.custom(domain);
			default :
				try
				{
					Class< ? > clazz = Class.forName(provider);
					if (DefaultApi20.class.isAssignableFrom(clazz))
					{
						Method instance = clazz.getDeclaredMethod("instance");
						return (DefaultApi20)instance.invoke(null, (Object[])null);
					}
					else
					{
						throw new Exception("'" + provider + "' api was not found or is not an OAuth2 api");
					}
				}
				catch (Exception e)
				{
					throw new Exception("Could not create OAuth Service: " + e.getMessage());
				}
		}
	}

	/**
	 * Create a custom OAuth api builder.
	 * @sample
	 * var customApi = plugins.oauth.customApi("https://example.com/oauth2/default/v1/authorize",  //authorization base url
	 *                                         "https://example.com/oauth2/default/v1/token");     //access token endpoint
	 * customApi = customApi.
	 * plugins.oauth.serviceBuilder("0lqd1s0aw...")		//client/application ID
	 * 				.clientSecret("bIk6163KHi...")		//client secret
	 * 				.state("secret123337")				//anti forgery session state, required by the Facebook api
	 * 				.deeplink("deeplink_method")		//OPTIONAL deeplink method name or last part of your redirect URL, see docs
	 * 													//if missing, a global method with the name 'deeplink_svy_oauth' will be generated
	 * 				.callback(myFunction, 30) 			//see function below, timeout is 30 seconds
	 * 				.build(customApi);                  //the custom api created above
	 *
	 * function myFunction(result, auth_outcome) {
	 * if (result)
	 * {
	 * 		//SUCCESS
	 * 		var service = auth_outcome;
	 * 		if (service.getAccessToken() == null) return;
	 * 		var response = service.executeGetRequest("https://graph.facebook.com/v2.11/me");
	 * 		if (response.getCode() == 200) {
	 * 			application.output(response.getBody());
	 * 			var json = response.getAsJSON();
	 * 			application.output("Name is "+json.name);
	 *		}
	 *		else {
	 *			application.output('ERROR http status '+response.getCode());
	 *		}
	 *	else {
	 *		//ERROR
	 *		application.output("ERROR "+auth_outcome);//could not get access token, request timed out, etc..
	 *	}
	 *  }
	 * }
	 *
	 * @param authorizationBaseUrl the base URL where you should redirect your users to authenticate your application
	 * @param accessTokenEndpoint the URL that receives the access token requests
	 * @return
	 */
	@JSFunction
	public CustomApiBuilder customApi(String authorizationBaseUrl, String accessTokenEndpoint)
	{
		return new CustomApiBuilder(authorizationBaseUrl, accessTokenEndpoint);
	}

	public IClientPluginAccess getPluginAccess()
	{
		return plugin.getAccess();
	}

	@Override
	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { OAuthServiceBuilder.class, OAuthService.class, OAuthProviders.class, OAuthResponseText.class, OAuthResponseJSON.class, OAuthResponseBinary.class, OAuthRequestType.class, JSOAuthRequest.class, OAuthTokenExtractors.class, ClientAuthentication.class };
	}
}
