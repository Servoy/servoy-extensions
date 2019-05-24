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

import com.github.scribejava.apis.FacebookApi;
import com.github.scribejava.apis.LinkedInApi20;
import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;

/**
 * @author emera
 */
@ServoyClientSupport(ng = true, wc = false, sc = false)
@ServoyDocumented(publicName = OAuthPlugin.PLUGIN_NAME, scriptingName = "plugins." + OAuthPlugin.PLUGIN_NAME)
public class OAuthProvider implements IScriptable, IReturnedTypesProvider
{
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
	 * service = plugins.oauth.getOAuthService(plugins.oauth.OAuthProviders.FACEBOOK, clientId, clientSecret, null, state, callback)
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
	 * @param callbackmethod the name of a global method
	 * @return
	 */
	@JSFunction
	public OAuthService getOAuthService(int provider, String clientId, String clientSecret, String scope, String state, String callbackmethod)
	{
		ServiceBuilder builder = new ServiceBuilder(clientId);
		builder.apiSecret(clientSecret);
		if (scope != null) builder.defaultScope(scope);
		if (callbackmethod != null) builder.callback(getRedirectURL(callbackmethod));
		//TODO else check plugin.getAccess().getSolutionModel().newGlobalMethod(scopeName, code) and redirect to that
		return new OAuthService(builder.build(getApiInstance(provider)), state);
	}

	private String getRedirectURL(String callbackmethod)
	{
		String redirectURL = plugin.getAccess().getServerURL().toString();
		redirectURL += (!redirectURL.endsWith("/") ? "/" + SOLUTIONS_PATH : SOLUTIONS_PATH);
		redirectURL += plugin.getAccess().getSolutionName() + "/m/" + callbackmethod;
		return redirectURL;
	}

	private DefaultApi20 getApiInstance(int provider)
	{
		switch (provider)
		{
			case OAuthProviders.MICROSOFT_AD :
				return MicrosoftAzureActiveDirectory20Api.instance();
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
