/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.servoy.extensions.plugins.oauth.apis.IntuitApi;
import com.servoy.extensions.plugins.oauth.apis.OktaApi;
import com.servoy.extensions.plugins.oauth.apis.UPSApi;
import com.servoy.j2db.plugins.IClientPluginAccess;

public class OAuthProviderTest
{
	@Test
	public void createsBuildersAndReturnsItsScriptingTypes() throws Exception
	{
		OAuthProvider provider = new OAuthTestSupport().provider;

		assertNotNull(provider.serviceBuilder("client-id"));
		assertNotNull(provider.customApi("https://auth", "https://token"));
		List<Class< ? >> types = List.of(new OAuthProvider().getAllReturnedTypes());
		assertEquals(10, types.size());
		assertTrue(types.containsAll(List.of(OAuthServiceBuilder.class, OAuthService.class, JSOAuthRequest.class, OAuthProviders.class)));
	}

	// --- api instances ---

	@Test
	public void microsoftAdUsesTheTenantWhenGiven() throws Exception
	{
		assertTrue(OAuthProvider.getApiInstance(OAuthProviders.MICROSOFT_AD, "my-tenant", null).getAccessTokenEndpoint().contains("my-tenant"));
		assertTrue(OAuthProvider.getApiInstance(OAuthProviders.MICROSOFT_AD, null, null).getAccessTokenEndpoint().contains("common"));
	}

	@Test
	public void oktaUsesTheDomain() throws Exception
	{
		DefaultApi20 api = OAuthProvider.getApiInstance(OAuthProviders.OKTA, null, "dev.okta.example");

		assertInstanceOf(OktaApi.class, api);
		assertEquals("https://dev.okta.example/oauth2/default/v1/token", api.getAccessTokenEndpoint());
	}

	@Test
	public void ownApisAreCreatedThroughTheirInstanceMethod() throws Exception
	{
		assertSame(IntuitApi.instance(), OAuthProvider.getApiInstance(OAuthProviders.INTUIT, null, null));
		assertSame(UPSApi.instance(), OAuthProvider.getApiInstance(OAuthProviders.UPS, null, null));
	}

	@Test
	public void everyProviderConstantCanBeCreated() throws Exception
	{
		List<String> failing = new ArrayList<>();
		for (Field field : OAuthProviders.class.getFields())
		{
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
			String provider = (String)field.get(null);
			try
			{
				assertNotNull(OAuthProvider.getApiInstance(provider, "tenant", "domain.example"));
			}
			catch (Exception e)
			{
				failing.add(field.getName() + ": " + e.getMessage());
			}
		}
		// current behaviour (bug): FrappeApi only has custom(domain), no instance(), so OAuthProviders.FRAPPE cannot be used
		assertEquals(List.of("FRAPPE: Could not create OAuth Service: com.github.scribejava.apis.FrappeApi.instance()"), failing);
	}

	@Test
	public void aClassThatIsNotAnApiIsRejected()
	{
		Exception e = assertThrows(Exception.class, () -> OAuthProvider.getApiInstance("java.lang.String", null, null));
		assertEquals("Could not create OAuth Service: 'java.lang.String' api was not found or is not an OAuth2 api", e.getMessage());
	}

	@Test
	public void anUnknownProviderIsRejected()
	{
		Exception e = assertThrows(Exception.class, () -> OAuthProvider.getApiInstance("no.such.Api", null, null));
		assertTrue(e.getMessage().startsWith("Could not create OAuth Service: "), e.getMessage());
	}

	// --- redirect url ---

	@Test
	public void redirectUrlPointsToTheDeeplinkOfTheSolution() throws Exception
	{
		assertEquals("https://example.com/solutions/mysol/m/my_deeplink", new OAuthTestSupport().provider.getRedirectURL("my_deeplink", false));
		assertEquals("https://example.com/solutions/mysol/m/my_deeplink", new OAuthTestSupport("https://example.com/").provider.getRedirectURL("my_deeplink", false));
	}

	@Test
	public void redirectUrlUsesTheSolutionPathForTitaniumNg2() throws Exception
	{
		OAuthTestSupport client = new OAuthTestSupport();
		client.runtimeProperties.put("NG2", Boolean.TRUE);

		assertEquals("https://example.com/solution/mysol/m/dl", client.provider.getRedirectURL("dl", false));
	}

	@Test
	public void fragmentResponsesGoThroughTheLandingServlet() throws Exception
	{
		assertEquals("https://example.com/servoy-service/oauth/solutions/mysol/m/dl", new OAuthTestSupport().provider.getRedirectURL("dl", true));
		assertEquals("https://example.com/servoy-service/oauth/solutions/mysol/m/dl",
			new OAuthTestSupport("https://example.com/").provider.getRedirectURL("dl", true));
	}

	@Test
	public void onlyTheNgClientIsSupported() throws Exception
	{
		OAuthTestSupport client = new OAuthTestSupport();
		when(Integer.valueOf(client.access.getApplicationType())).thenReturn(Integer.valueOf(IClientPluginAccess.HEADLESS_CLIENT));

		assertThrows(IllegalStateException.class, () -> client.provider.getRedirectURL("dl", false));
	}

	@Test
	public void usedRedirectUrlFollowsTheBuilderConfiguration() throws Exception
	{
		OAuthProvider provider = new OAuthTestSupport().provider;

		assertEquals("https://example.com/solutions/mysol/m/deeplink_svy_oauth", provider.getUsedRedirectUrl(provider.serviceBuilder("id")));
		assertEquals("https://example.com/solutions/mysol/m/mine", provider.getUsedRedirectUrl(provider.serviceBuilder("id").deeplink("mine")));
		assertEquals("https://example.com/servoy-service/oauth/solutions/mysol/m/deeplink_svy_oauth",
			provider.getUsedRedirectUrl(provider.serviceBuilder("id").responseType("token")));
	}

	@Test
	public void responseModeFragmentDoesNotChangeTheRedirectUrl() throws Exception
	{
		OAuthProvider provider = new OAuthTestSupport().provider;

		// current behaviour (bug): responseMode() only adds the parameter, isFragmentResponse() never sees the mode, so the
		// redirect url does not go through servoy-service/oauth as documented for "fragment"
		assertEquals("https://example.com/solutions/mysol/m/deeplink_svy_oauth",
			provider.getUsedRedirectUrl(provider.serviceBuilder("id").responseMode("fragment")));
	}

	// --- deprecated direct service creation ---

	@Test
	public void getOAuthServiceBuildsAServiceWithTheGivenSettings() throws Exception
	{
		OAuthService service = new OAuthTestSupport().provider.getOAuthService(OAuthProviders.GITHUB, "client-id", "secret", "user:email", "st4te",
			"my_deeplink");

		String url = OAuthTestSupport.decode(service.getAuthorizationURL());
		assertTrue(url.startsWith("https://github.com/login/oauth/authorize?"), url);
		assertTrue(url.contains("client_id=client-id"), url);
		assertTrue(url.contains("redirect_uri=https://example.com/solutions/mysol/m/my_deeplink"), url);
		assertTrue(url.contains("scope=user:email"), url);
		assertTrue(url.contains("state=st4te"), url);
	}

	@Test
	public void getOAuthServiceWithoutScopeStateOrDeeplink() throws Exception
	{
		OAuthService service = new OAuthTestSupport().provider.getOAuthService(OAuthProviders.GITHUB, "client-id", "secret", null, null, null);

		String url = OAuthTestSupport.decode(service.getAuthorizationURL());
		assertTrue(url.contains("client_id=client-id"), url);
		assertTrue(!url.contains("redirect_uri") && !url.contains("state="), url);
	}
}
