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

import static com.servoy.extensions.plugins.oauth.OAuthTestSupport.object;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mozilla.javascript.NativeObject;

import com.servoy.j2db.solutionmodel.ISMMethod;

/**
 * The background task that build() starts: it waits for the code delivered by the deeplink (polled through the
 * generated getSvyOAuthCode global method), exchanges it for a token and calls the callback. The task is taken from
 * the mocked executor and run on the test thread; the code is already available, so it does not sleep.
 */
public class OAuthServiceBuilderFlowTest
{
	private OAuthTestSupport client;
	private FakeHttpClient http;
	private final List<Object[]> callbacks = new ArrayList<>();

	@BeforeEach
	public void setUp() throws Exception
	{
		client = new OAuthTestSupport();
		http = new FakeHttpClient();
		when(Boolean.valueOf(client.access.showURL(anyString(), anyString(), any()))).thenReturn(Boolean.TRUE);
		// the callback is a global method, FunctionDefinition calls it through executeMethod
		doAnswer(inv -> {
			callbacks.add(inv.getArgument(2));
			return null;
		}).when(client.access).executeMethod(eq("scopes.globals"), eq("onAuth"), any(), anyBoolean());
	}

	private OAuthServiceBuilder builder()
	{
		// the http client of scribejava is reached through the builder of the plugin; inject the fake transport
		OAuthServiceBuilder builder = client.provider.serviceBuilder("client-id").clientSecret("secret").callback(OAuthTestSupport.callback(), 0);
		builder.httpClientForTest(http);
		return builder;
	}

	private OAuthService buildAndRun(OAuthServiceBuilder builder, Object deliveredCode) throws Exception
	{
		when(client.access.executeMethod(isNull(), eq("getSvyOAuthCode"), any(), eq(false))).thenReturn(deliveredCode);
		OAuthService service = builder.build(client.provider.customApi("https://auth.example/authorize", "https://auth.example/token"));
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(client.executor).execute(task.capture());
		task.getValue().run();
		return service;
	}

	private void assertGlobalsRemoved()
	{
		verify(client.solutionModel).removeGlobalMethod("globals", "deeplink_svy_oauth");
		verify(client.solutionModel).removeGlobalMethod("globals", "getSvyOAuthCode");
		verify(client.solutionModel).removeGlobalVariable("globals", "svy_authCode");
	}

	@Test
	public void theDeliveredCodeIsExchangedForATokenAndTheCallbackGetsTheService() throws Exception
	{
		http.token("at-1", Integer.valueOf(3600), "rt-1", null);

		OAuthService service = buildAndRun(builder().state("st4te"), object("code", "the-code", "state", "st4te"));

		assertEquals("at-1", service.getAccessToken());
		assertTrue(http.last().body().contains("code=the-code"), http.last().body());
		assertEquals(1, callbacks.size());
		assertEquals(Boolean.TRUE, callbacks.get(0)[0]);
		assertSame(service, callbacks.get(0)[1]);
		verify(client.access).clearUrlState();
		assertGlobalsRemoved();
	}

	@Test
	public void aWrongStateIsRejectedAsPossibleCsrf() throws Exception
	{
		OAuthService service = buildAndRun(builder().state("st4te"), object("code", "the-code", "state", "other"));

		assertNull(service.getAccessToken());
		assertTrue(http.requests.isEmpty());
		assertEquals(Boolean.FALSE, callbacks.get(0)[0]);
		assertEquals("Invalid state returned by the server. Possible CSRF attack.", callbacks.get(0)[1]);
		assertGlobalsRemoved();
	}

	@Test
	public void pkceSendsTheCodeVerifierOfTheAuthorizationRequest() throws Exception
	{
		http.token("at-pkce", null, null, null);

		OAuthService service = buildAndRun(builder().withPKCE(), object("code", "c"));

		assertEquals("at-pkce", service.getAccessToken());
		assertTrue(http.last().body().contains("code_verifier="), http.last().body());
	}

	@Test
	public void theImplicitFlowTakesTheTokenFromTheRedirect() throws Exception
	{
		OAuthService service = buildAndRun(builder().responseType("token"), object("access_token", "implicit", "token_type", "bearer"));

		assertEquals("implicit", service.getAccessToken());
		assertTrue(http.requests.isEmpty());
		assertEquals(Boolean.TRUE, callbacks.get(0)[0]);
	}

	@Test
	public void anOpenIdTokenIsKept() throws Exception
	{
		OAuthService service = buildAndRun(builder().responseType("id_token"), object("id_token", "oid.jwt"));

		assertEquals("oid.jwt", service.getIdToken());
		assertEquals(Boolean.TRUE, callbacks.get(0)[0]);
	}

	@Test
	public void anErrorFromTheProviderIsPassedToTheCallback() throws Exception
	{
		buildAndRun(builder(), object("error", "access_denied"));
		assertEquals(Boolean.FALSE, callbacks.get(0)[0]);
		assertEquals("access_denied", callbacks.get(0)[1]);
	}

	@Test
	public void theReadableErrorMessageIsPreferred() throws Exception
	{
		buildAndRun(builder(), object("error", "access_denied", "error_message", "The user cancelled the login"));
		assertEquals("The user cancelled the login", callbacks.get(0)[1]);
	}

	@Test
	public void aFailingTokenExchangeIsReportedToTheCallback() throws Exception
	{
		http.fail(new java.io.IOException("down"));

		buildAndRun(builder(), object("code", "c"));

		assertEquals(Boolean.FALSE, callbacks.get(0)[0]);
		assertEquals("Could not set the oauth code", callbacks.get(0)[1]);
	}

	@Test
	public void noCodeWithinTheTimeoutIsReported() throws Exception
	{
		buildAndRun(builder(), "not a scriptable");

		assertEquals(Boolean.FALSE, callbacks.get(0)[0]);
		assertTrue(((String)callbacks.get(0)[1]).startsWith("Request timed out."), (String)callbacks.get(0)[1]);
		assertGlobalsRemoved();
	}

	@Test
	public void withAnExistingDeeplinkTheTaskWaitsForTheTokenSetByTheSolution() throws Exception
	{
		when(client.solutionModel.getGlobalMethod("globals", "my_dl")).thenReturn(org.mockito.Mockito.mock(ISMMethod.class));
		OAuthServiceBuilder builder = builder().deeplink("my_dl");
		OAuthService service = builder.build(client.provider.customApi("https://auth.example/authorize", "https://auth.example/token"));
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(client.executor).execute(task.capture());
		// the solution's own deeplink method already set the token
		service.setToken("{\"access_token\":\"from-deeplink\",\"token_type\":\"bearer\"}");

		task.getValue().run();

		assertEquals(Boolean.TRUE, callbacks.get(0)[0]);
		assertSame(service, callbacks.get(0)[1]);
	}

	@Test
	public void refreshTokenSkipsTheLogin() throws Exception
	{
		http.token("at-refreshed", Integer.valueOf(3600), null, null);

		OAuthService service = builder().refreshToken("rt-1")
			.build(client.provider.customApi("https://auth.example/authorize", "https://auth.example/token"));

		assertEquals("at-refreshed", service.getAccessToken());
		assertEquals(Boolean.TRUE, callbacks.get(0)[0]);
		verify(client.access, org.mockito.Mockito.never()).showURL(anyString(), anyString(), any());
	}

	@Test
	public void aFailingRefreshFallsBackToTheLogin() throws Exception
	{
		http.fail(new java.io.IOException("down"));

		builder().refreshToken("rt-1").build(client.provider.customApi("https://auth.example/authorize", "https://auth.example/token"));

		verify(client.access).showURL(anyString(), eq("_self"), isNull());
	}

	@Test
	public void clientCredentialsDoNotNeedALogin() throws Exception
	{
		http.token("machine", null, null, null);

		OAuthService service = builder().withClientCredentials()
			.build(client.provider.customApi("https://auth.example/authorize", "https://auth.example/token"));

		assertEquals("machine", service.getAccessToken());
		assertInstanceOf(OAuthService.class, callbacks.get(0)[1]);
		verify(client.access, org.mockito.Mockito.never()).showURL(anyString(), anyString(), any());
	}

	@Test
	public void additionalParametersAreSentWithThePkceTokenRequest() throws Exception
	{
		http.token("at", null, null, null);
		NativeObject params = object("audience", "api://x");

		buildAndRun(builder().withPKCE().additionalParameters(params), object("code", "c"));

		assertTrue(http.last().body().contains("audience=api"), http.last().body());
	}
}
