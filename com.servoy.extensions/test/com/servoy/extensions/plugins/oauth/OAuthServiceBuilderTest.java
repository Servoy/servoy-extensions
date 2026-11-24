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

import static com.servoy.extensions.plugins.oauth.OAuthTestSupport.decode;
import static com.servoy.extensions.plugins.oauth.OAuthTestSupport.object;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.servoy.base.solutionmodel.IBaseSMVariable;
import com.servoy.j2db.solutionmodel.ISMMethod;

/**
 * The service builder: authorization url (scope, state, PKCE, response type/mode, additional parameters), redirect
 * url and what build() sets up in the client (deeplink/global methods, background task, redirect to the login page).
 * The background task is captured from the mocked executor and not run here; that is the token flow (next batch).
 */
public class OAuthServiceBuilderTest
{
	private OAuthTestSupport client;
	private OAuthProvider provider;

	@BeforeEach
	public void setUp() throws Exception
	{
		client = new OAuthTestSupport();
		provider = client.provider;
		when(Boolean.valueOf(client.access.showURL(anyString(), anyString(), any()))).thenReturn(Boolean.TRUE);
	}

	private OAuthServiceBuilder builder()
	{
		return provider.serviceBuilder("client-id").clientSecret("secret");
	}

	/**
	 * The authorization url for GitHub. getUsedAuthorizationURL(String) runs the full build(), which needs a callback,
	 * so one is set here; that build also redirects (mocked showURL) and starts the wait task (mocked executor).
	 */
	private static String url(OAuthServiceBuilder builder) throws Exception
	{
		return decode(builder.callback(OAuthTestSupport.callback(), 30).getUsedAuthorizationURL(OAuthProviders.GITHUB));
	}

	// --- authorization url ---

	@Test
	public void authorizationUrlContainsClientRedirectAndResponseType() throws Exception
	{
		String url = url(builder());

		assertTrue(url.startsWith("https://github.com/login/oauth/authorize?"), url);
		assertTrue(url.contains("response_type=code"), url);
		assertTrue(url.contains("client_id=client-id"), url);
		assertTrue(url.contains("redirect_uri=https://example.com/solutions/mysol/m/deeplink_svy_oauth"), url);
	}

	@Test
	public void scopesStateAndDeeplinkAreUsed() throws Exception
	{
		String url = url(builder().defaultScope("email").state("st4te").deeplink("my_dl"));

		assertTrue(url.contains("scope=email"), url);
		assertTrue(url.contains("state=st4te"), url);
		assertTrue(url.contains("redirect_uri=https://example.com/solutions/mysol/m/my_dl"), url);
	}

	@Test
	public void aRequestScopeIsUsedForTheAuthorizationUrl() throws Exception
	{
		assertTrue(url(builder().scope("read write")).contains("scope=read write"));
	}

	@Test
	public void pkceAddsTheCodeChallenge() throws Exception
	{
		String url = url(builder().withPKCE());

		assertTrue(url.contains("code_challenge="), url);
		assertTrue(url.contains("code_challenge_method=S256"), url);
		assertFalse(url(builder()).contains("code_challenge"));
	}

	@Test
	public void responseTypeTokenGoesThroughTheLandingServlet() throws Exception
	{
		String url = url(builder().responseType("id_token token"));

		assertTrue(url.contains("response_type=id_token token"), url);
		assertTrue(url.contains("redirect_uri=https://example.com/servoy-service/oauth/solutions/mysol/m/deeplink_svy_oauth"), url);
	}

	@Test
	public void responseModeIsAddedAsParameter() throws Exception
	{
		assertTrue(url(builder().responseMode("fragment")).contains("response_mode=fragment"));
		assertTrue(url(builder().responseMode("QUERY")).contains("response_mode=QUERY"));
	}

	@Test
	public void anInvalidResponseModeIsRejected()
	{
		Exception e = assertThrows(Exception.class, () -> builder().responseMode("form_post"));
		assertTrue(e.getMessage().startsWith("'form_post' is not a valid response mode."), e.getMessage());
	}

	@Test
	public void additionalStringParametersAreAdded() throws Exception
	{
		org.mozilla.javascript.NativeObject params = object("prompt", "consent", "login_hint", "user@example.com");
		params.put("number", params, Integer.valueOf(1));

		String url = url(builder().additionalParameters(params).additionalParameters("ignored, not an object"));

		assertTrue(url.contains("prompt=consent"), url);
		assertTrue(url.contains("login_hint=user@example.com"), url);
		assertFalse(url.contains("number="), url);
	}

	@Test
	public void reservedAdditionalParametersAreRejected()
	{
		assertThrows(IllegalArgumentException.class, () -> builder().additionalParameters(object("redirect_uri", "https://evil")));
		assertThrows(IllegalArgumentException.class, () -> builder().additionalParameters(object("response_mode", "fragment")));
		assertThrows(IllegalArgumentException.class, () -> builder().additionalParameters(object("response_type", "token")));
	}

	@Test
	public void anEmptyClientSecretIsRejected()
	{
		assertThrows(IllegalArgumentException.class, () -> provider.serviceBuilder("client-id").clientSecret(""));
	}

	@Test
	public void theTenantSelectsTheMicrosoftAdEndpoint() throws Exception
	{
		String url = decode(builder().tenant("my-tenant").callback(OAuthTestSupport.callback(), 30).getUsedAuthorizationURL(OAuthProviders.MICROSOFT_AD));

		assertTrue(url.startsWith("https://login.microsoftonline.com/my-tenant/oauth2/v2.0/authorize?"), url);
	}

	@Test
	public void theDomainSelectsTheOktaEndpoint() throws Exception
	{
		String url = decode(builder().domain("dev.okta.example").callback(OAuthTestSupport.callback(), 30).getUsedAuthorizationURL(OAuthProviders.OKTA));

		assertTrue(url.startsWith("https://dev.okta.example/oauth2/default/v1/authorize?"), url);
	}

	@Test
	public void authorizationUrlForACustomApi() throws Exception
	{
		CustomApiBuilder api = provider.customApi("https://auth.example/authorize", "https://auth.example/token");

		String url = decode(builder().state("s").getUsedAuthorizationURL(api));

		assertTrue(url.startsWith("https://auth.example/authorize?"), url);
		assertTrue(url.contains("state=s"), url);
	}

	@Test
	public void theDebugAuthorizationUrlForAProviderRunsTheWholeLoginFlow() throws Exception
	{
		// current behaviour (bug): "for debugging purposes only", but without a callback it fails and with one it generates
		// the deeplink methods, starts the wait task and redirects the user to the login page
		assertThrows(Exception.class, () -> builder().getUsedAuthorizationURL(OAuthProviders.GITHUB));

		builder().callback(OAuthTestSupport.callback(), 30).getUsedAuthorizationURL(OAuthProviders.GITHUB);

		verify(client.executor).execute(any(Runnable.class));
		verify(client.access).showURL(startsWith("https://github.com/login/oauth/authorize"), eq("_self"), isNull());
	}

	@Test
	public void theDebugAuthorizationUrlForACustomApiHasNoSideEffects() throws Exception
	{
		builder().getUsedAuthorizationURL(provider.customApi("https://auth.example/authorize", "https://auth.example/token"));

		verify(client.executor, never()).execute(any());
		verify(client.access, never()).showURL(anyString(), anyString(), any());
	}

	// --- build ---

	@Test
	public void buildWithoutCallbackOrExistingDeeplinkIsRejected()
	{
		Exception e = assertThrows(Exception.class, () -> builder().build(OAuthProviders.GITHUB));
		assertTrue(e.getMessage().startsWith("Cannot build OAuth service. Please specify a callback function"), e.getMessage());
	}

	@Test
	public void buildWithAnExistingDeeplinkMethodReturnsTheServiceWithoutRedirecting() throws Exception
	{
		when(client.solutionModel.getGlobalMethod("globals", "my_dl")).thenReturn(org.mockito.Mockito.mock(ISMMethod.class));

		OAuthService service = builder().deeplink("my_dl").build(OAuthProviders.GITHUB);

		assertNotNull(service);
		assertTrue(decode(service.getAuthorizationURL()).contains("redirect_uri=https://example.com/solutions/mysol/m/my_dl"));
		verify(client.access, never()).showURL(anyString(), anyString(), any());
		verify(client.executor, never()).execute(any());
	}

	@Test
	public void buildWithCallbackGeneratesTheDeeplinkStartsTheWaitAndRedirects() throws Exception
	{
		OAuthService service = builder().state("st4te").callback(OAuthTestSupport.callback(), 30).build(OAuthProviders.GITHUB);

		assertNotNull(service);
		verify(client.solutionModel).newGlobalVariable("globals", "svy_authCode", IBaseSMVariable.MEDIA);
		ArgumentCaptor<String> methods = ArgumentCaptor.forClass(String.class);
		verify(client.solutionModel, org.mockito.Mockito.times(2)).newGlobalMethod(eq("globals"), methods.capture());
		assertEquals("/**@deeplink*/function deeplink_svy_oauth(a,b){ svy_authCode = b; }", methods.getAllValues().get(0));
		assertTrue(methods.getAllValues().get(1).startsWith("function getSvyOAuthCode()"));
		verify(client.executor).execute(any(Runnable.class));
		ArgumentCaptor<String> redirect = ArgumentCaptor.forClass(String.class);
		verify(client.access).showURL(redirect.capture(), eq("_self"), isNull());
		assertTrue(decode(redirect.getValue()).contains("state=st4te"), redirect.getValue());
	}

	@Test
	public void buildWithCallbackAndAnExistingDeeplinkDoesNotGenerateMethods() throws Exception
	{
		when(client.solutionModel.getGlobalMethod("globals", "my_dl")).thenReturn(org.mockito.Mockito.mock(ISMMethod.class));

		builder().deeplink("my_dl").callback(OAuthTestSupport.callback(), 30).build(OAuthProviders.GITHUB);

		verify(client.solutionModel, never()).newGlobalMethod(anyString(), anyString());
		verify(client.solutionModel, never()).newGlobalVariable(anyString(), anyString(), anyInt());
		verify(client.executor).execute(any(Runnable.class));
		verify(client.access).showURL(startsWith("https://github.com/login/oauth/authorize"), eq("_self"), isNull());
	}

	@Test
	public void aFailingRedirectIsOnlyLogged() throws Exception
	{
		when(Boolean.valueOf(client.access.showURL(anyString(), anyString(), any()))).thenReturn(Boolean.FALSE);

		assertNotNull(builder().callback(OAuthTestSupport.callback(), 30).build(OAuthProviders.GITHUB));
	}

	@Test
	public void buildForACustomApi() throws Exception
	{
		CustomApiBuilder api = provider.customApi("https://auth.example/authorize", "https://auth.example/token");

		OAuthService service = builder().callback(OAuthTestSupport.callback(), 30).build(api);

		assertNotNull(service);
		verify(client.access).showURL(startsWith("https://auth.example/authorize?"), eq("_self"), isNull());
	}

	@Test
	public void buildForAnUnknownProviderFails()
	{
		assertThrows(Exception.class, () -> builder().callback(OAuthTestSupport.callback(), 30).build("no.such.Api"));
	}

	@Test
	public void deeplinkNameAndFragmentDetection()
	{
		assertEquals("deeplink_svy_oauth", builder().getDeeplinkName());
		assertEquals("x", builder().deeplink("x").getDeeplinkName());
		assertFalse(builder().isFragmentResponse());
		assertFalse(builder().responseType("code").isFragmentResponse());
		assertTrue(builder().responseType("token").isFragmentResponse());
		assertTrue(builder().responseType("code id_token").isFragmentResponse());
	}

	@Test
	public void builderMethodsChain() throws Exception
	{
		OAuthServiceBuilder builder = builder();

		assertSame(builder, builder.callback(OAuthTestSupport.callback(), 1).defaultScope("a").scope("b").state("c").deeplink("d").tenant("e").domain("f")
			.responseType("code").responseMode("query").additionalParameters(null).refreshToken("r").withPKCE().withClientCredentials());
		verify(client.executor, never()).execute(any());
		verify(client.access, never()).executeMethod(any(), anyString(), any(), anyBoolean());
	}
}
