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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;

/**
 * OAuthService against a scribejava service whose transport is a {@link FakeHttpClient}: token requests, refresh,
 * expiry, signed resource requests and the response types. No network.
 */
public class OAuthServiceTest
{
	private static final String AUTH = "https://auth.example/authorize";
	private static final String TOKEN = "https://auth.example/token";

	private FakeHttpClient http;

	@BeforeEach
	public void setUp()
	{
		http = new FakeHttpClient();
	}

	private OAuthService service(String state, CustomApiBuilder api)
	{
		return new OAuthService(new ServiceBuilder("client-id").apiSecret("secret").callback("https://example.com/cb").defaultScope("read")
			.httpClient(http).build(api.build()), state);
	}

	private OAuthService service()
	{
		return service(null, new CustomApiBuilder(AUTH, TOKEN));
	}

	private OAuthService serviceWithToken(Integer expiresIn, String refreshToken) throws Exception
	{
		OAuthService service = service();
		http.token("at-1", expiresIn, refreshToken, "read");
		service.setAccessToken("the-code");
		return service;
	}

	// --- authorization url ---

	@Test
	public void authorizationUrlUsesTheStateAndAdditionalParameters()
	{
		OAuthService service = service("st4te", new CustomApiBuilder(AUTH, TOKEN));

		String plain = decode(service.getAuthorizationURL());
		String extra = decode(service.getAuthorizationURL(object("prompt", "consent")));

		assertTrue(plain.startsWith(AUTH + "?"), plain);
		assertTrue(plain.contains("state=st4te") && plain.contains("scope=read"), plain);
		assertTrue(extra.contains("prompt=consent") && extra.contains("state=st4te"), extra);
		assertEquals(plain, decode(service.getAuthorizationURL(null)));
		assertFalse(decode(service().getAuthorizationURL()).contains("state="));
		assertNotNull(service.getAuthorizatinUrlBuilder());
	}

	// --- access token ---

	@Test
	public void setAccessTokenExchangesTheCode() throws Exception
	{
		OAuthService service = serviceWithToken(Integer.valueOf(3600), "rt-1");

		assertEquals("at-1", service.getAccessToken());
		assertEquals("rt-1", service.getRefreshToken());
		assertEquals(3600, service.getAccessTokenLifetime());
		long left = service.getAccessExpiresIn();
		assertTrue(left > 3590 && left <= 3600, String.valueOf(left));
		assertFalse(service.isAccessTokenExpired());
		FakeHttpClient.Request request = http.last();
		assertEquals(Verb.POST, request.verb());
		assertEquals(TOKEN, request.url());
		assertTrue(request.body().contains("code=the-code"), request.body());
		assertTrue(request.body().contains("grant_type=authorization_code"), request.body());
		assertTrue(request.headers().get("Authorization").startsWith("Basic "), request.headers().toString());
	}

	@Test
	public void setAccessTokenWithAScope() throws Exception
	{
		OAuthService service = service();
		http.token("at-2", null, null, null);

		service.setAccessToken("c", "write");

		assertEquals("at-2", service.getAccessToken());
		assertTrue(http.last().body().contains("scope=write"), http.last().body());
	}

	@Test
	public void setAccessTokenWithPkceParameters() throws Exception
	{
		OAuthService service = service();
		http.token("at-3", Integer.valueOf(60), null, null);

		service.setAccessToken(AccessTokenRequestParams.create("c").pkceCodeVerifier("verifier-123"));

		assertEquals("at-3", service.getAccessToken());
		assertTrue(http.last().body().contains("code_verifier=verifier-123"), http.last().body());
	}

	@Test
	public void aFailingTokenRequestIsReportedWithAGenericMessage()
	{
		OAuthService service = service();
		http.fail(new IOException("connection refused")).fail(new IOException("down")).fail(new IOException("down"));

		Exception e = assertThrows(Exception.class, () -> service.setAccessToken("c"));
		assertEquals("Could not set the access token. See the log for more details", e.getMessage());
		assertThrows(Exception.class, () -> service.setAccessToken("c", "s"));
		assertThrows(Exception.class, () -> service.setAccessToken(AccessTokenRequestParams.create("c")));
		assertNull(service.getAccessToken());
	}

	@Test
	public void anOAuthErrorResponseIsNotCaught()
	{
		OAuthService service = service();
		http.respond(400, "application/json", "{\"error\":\"invalid_grant\",\"error_description\":\"bad code\"}");

		// current behaviour: OAuth2AccessTokenErrorResponse is not an IOException, so it reaches the script as is
		Exception e = assertThrows(Exception.class, () -> service.setAccessToken("c"));
		assertTrue(e.getMessage().contains("invalid_grant") || e.getMessage().contains("bad code"), e.getMessage());
	}

	@Test
	public void clientCredentialsGrant() throws Exception
	{
		OAuthService service = service();
		http.token("machine", Integer.valueOf(10), null, null);

		service.setAccessTokenClientCredentialsGrant();

		assertEquals("machine", service.getAccessToken());
		assertTrue(http.last().body().contains("grant_type=client_credentials"), http.last().body());

		http.fail(new IOException("down"));
		assertThrows(Exception.class, service::setAccessTokenClientCredentialsGrant);
	}

	@Test
	public void implicitGrantTokenIsParsedFromTheRedirectData()
	{
		OAuthService service = service();

		service.setToken("{\"access_token\":\"implicit\",\"token_type\":\"bearer\"}");

		assertEquals("implicit", service.getAccessToken());
		assertTrue(http.requests.isEmpty());
	}

	@Test
	public void anUnparsableImplicitTokenIsOnlyLogged()
	{
		OAuthService service = service();

		service.setToken("not json");

		assertNull(service.getAccessToken());
	}

	// --- without a token ---

	@Test
	public void tokenInformationNeedsAnAccessToken()
	{
		OAuthService service = service();

		assertThrows(Exception.class, service::getRefreshToken);
		assertThrows(Exception.class, service::refreshToken);
		assertThrows(Exception.class, service::getAccessExpiresIn);
		assertThrows(Exception.class, service::getAccessTokenLifetime);
		assertFalse(service.isAccessTokenExpired());
		assertThrows(RuntimeException.class, () -> service.executeGetRequest("https://api.example/me"));
	}

	@Test
	public void expiryInformationNeedsAnExpiresIn() throws Exception
	{
		OAuthService service = serviceWithToken(null, null);

		Exception expiresIn = assertThrows(Exception.class, service::getAccessExpiresIn);
		assertEquals("The access token expire information is not available.", expiresIn.getMessage());
		assertThrows(Exception.class, service::getAccessTokenLifetime);
		assertFalse(service.isAccessTokenExpired());
	}

	// --- refresh ---

	@Test
	public void refreshTokenGetsANewAccessToken() throws Exception
	{
		OAuthService service = serviceWithToken(Integer.valueOf(3600), "rt-1");
		http.token("at-new", Integer.valueOf(3600), "rt-2", "read");

		assertEquals("at-new", service.refreshToken());

		assertEquals("at-new", service.getAccessToken());
		assertEquals("rt-2", service.getRefreshToken());
		assertTrue(http.last().body().contains("grant_type=refresh_token"), http.last().body());
		assertTrue(http.last().body().contains("refresh_token=rt-1"), http.last().body());
	}

	@Test
	public void theOldRefreshTokenIsKeptWhenTheServerDoesNotReturnANewOne() throws Exception
	{
		OAuthService service = service();
		http.token("at-new", null, null, null);

		assertEquals("at-new", service.refreshToken("rt-given", null));

		assertEquals("rt-given", service.getRefreshToken());
		assertTrue(http.last().body().contains("scope=read"), http.last().body());
	}

	@Test
	public void refreshingWithoutRefreshTokenFails() throws Exception
	{
		OAuthService service = serviceWithToken(Integer.valueOf(3600), null);

		assertThrows(Exception.class, service::refreshToken);
		assertThrows(Exception.class, () -> service.refreshToken(" ", null));
		assertThrows(Exception.class, () -> service.refreshToken(null, null));
	}

	@Test
	public void refreshUsesTheRefreshTokenOfAnEarlierRefresh() throws Exception
	{
		OAuthService service = service();
		http.token("at-1", Integer.valueOf(3600), null, null);
		service.refreshToken("rt-kept", null);

		// current behaviour (bug): the remembered refresh token passes the "has a refresh token" check, but refreshToken()
		// then sends the (empty) refresh token of the current access token instead of the remembered one
		Exception e = assertThrows(Exception.class, service::refreshToken);
		assertEquals("Could not refresh the access token, the provided refresh token is blank.", e.getMessage());
	}

	@Test
	public void aFailingRefreshIsReported() throws Exception
	{
		OAuthService service = service();
		http.respond(400, "application/json", "{\"error\":\"invalid_grant\"}");

		Exception e = assertThrows(Exception.class, () -> service.refreshToken("rt", "s"));
		assertTrue(e.getMessage().startsWith("Could not get a new access token"), e.getMessage());
	}

	// --- revoke ---

	@Test
	public void revokeTokenCallsTheRevokeEndpoint() throws Exception
	{
		OAuthService service = service(null, new CustomApiBuilder(AUTH, TOKEN).withRevokeTokenEndpoint("https://auth.example/revoke"));
		http.token("at-1", Integer.valueOf(3600), null, null);
		service.setAccessToken("c");
		http.respond(200, "text/plain", "");

		service.revokeToken("at-1");

		assertEquals("https://auth.example/revoke", http.last().url());
		assertTrue(http.last().body().contains("token=at-1"), http.last().body());
		assertThrows(Exception.class, service::getAccessExpiresIn);
		// current behaviour: the revoked access token is still on the service
		assertEquals("at-1", service.getAccessToken());
	}

	// --- resource requests ---

	@Test
	public void requestsAreSignedWithTheAccessToken() throws Exception
	{
		OAuthService service = serviceWithToken(Integer.valueOf(3600), null);
		http.respond(200, "application/json; charset=UTF-8", "{\"name\":\"servoy\"}");

		OAuthResponse response = service.executeGetRequest("https://api.example/me");

		assertInstanceOf(OAuthResponseJSON.class, response);
		assertEquals("servoy", ((OAuthResponseJSON)response).getAsJSON().getString("name"));
		assertEquals(200, response.getCode());
		assertEquals("application/json; charset=UTF-8", response.getHeader("Content-Type"));
		assertEquals(1, response.getHeaders().length);
		assertEquals(Verb.GET, http.last().verb());
		assertEquals("Bearer at-1", http.last().headers().get("Authorization"));
	}

	@Test
	public void createdRequestsCarryHeadersParametersAndPayload() throws Exception
	{
		OAuthService service = serviceWithToken(Integer.valueOf(3600), null);
		http.respond(201, "text/plain", "created").respond(200, "text/plain", "ok").respond(204, "text/plain", "").respond(200, "text/plain", "g");

		JSOAuthRequest put = service.createPutRequest("https://api.example/file");
		put.addHeader("X-Test", "1");
		put.addQuerystringParameter("q", "v");
		put.setPayload("ABC");
		OAuthResponse putResponse = put.execute();

		JSOAuthRequest post = service.createPostRequest("https://api.example/form");
		post.addBodyParameter("field", "value");
		post.addParameter("p", "x");
		post.addOAuthParameter("scope", "s");
		service.executeRequest(post);

		service.createDeleteRequest("https://api.example/file").execute();
		service.createGetRequest("https://api.example/g").execute();

		FakeHttpClient.Request putRequest = http.requests.get(1);
		assertEquals(Verb.PUT, putRequest.verb());
		assertEquals("https://api.example/file?q=v", putRequest.url());
		assertEquals("1", putRequest.headers().get("X-Test"));
		assertEquals("ABC", putRequest.body());
		assertEquals("created", ((OAuthResponseText)putResponse).getBody());
		FakeHttpClient.Request postRequest = http.requests.get(2);
		assertEquals(Verb.POST, postRequest.verb());
		assertTrue(postRequest.body().contains("field=value") && postRequest.body().contains("p=x"), postRequest.body());
		assertEquals(Verb.DELETE, http.requests.get(3).verb());
		assertEquals(Verb.GET, http.requests.get(4).verb());
		assertNull(service.executeRequest(null));
	}

	@Test
	public void anInvalidOAuthParameterIsRejected() throws Exception
	{
		JSOAuthRequest request = serviceWithToken(Integer.valueOf(3600), null).createGetRequest("https://api.example");
		assertThrows(IllegalArgumentException.class, () -> request.addOAuthParameter("custom", "x"));
	}

	@Test
	public void aFailingResourceRequestReturnsNull() throws Exception
	{
		OAuthService service = serviceWithToken(Integer.valueOf(3600), null);
		http.fail(new IOException("timeout"));

		assertNull(service.executeGetRequest("https://api.example/me"));
	}

	@Test
	public void anExpiredTokenIsRefreshedBeforeTheRequest() throws Exception
	{
		OAuthService service = serviceWithToken(Integer.valueOf(0), "rt-1");
		assertTrue(service.isAccessTokenExpired());
		http.token("at-fresh", Integer.valueOf(3600), null, null).respond(200, "text/plain", "data");

		OAuthResponse response = service.executeGetRequest("https://api.example/me");

		assertEquals("data", ((OAuthResponseText)response).getBody());
		assertEquals("Bearer at-fresh", http.last().headers().get("Authorization"));
	}

	@Test
	public void anExpiredTokenWithoutRefreshTokenStopsTheRequest() throws Exception
	{
		OAuthService service = serviceWithToken(Integer.valueOf(0), null);

		RuntimeException e = assertThrows(RuntimeException.class, () -> service.executeGetRequest("https://api.example/me"));
		assertEquals("Cannot execute request, access token is expired and could not refresh it", e.getMessage());
	}

	// --- openid ---

	@Test
	public void idTokenIsTheOneSetOnTheService()
	{
		OAuthService service = service();
		assertNull(service.getIdToken());

		service.setIdToken("id.jwt.token");

		assertEquals("id.jwt.token", service.getIdToken());
	}

	@Test
	public void idTokenOfAnOpenIdTokenResponseIsNotReturned() throws Exception
	{
		OAuthService service = service(null, new CustomApiBuilder(AUTH, TOKEN).withAccessTokenExtractor(OAuthTokenExtractors.OpenIdJson));
		http.respond(200, "application/json", "{\"access_token\":\"at\",\"token_type\":\"bearer\",\"id_token\":\"oid.jwt\"}");
		service.setAccessToken("c");

		// current behaviour (bug): getIdToken() reads the OpenID token of the access token but does not assign it
		assertNull(service.getIdToken());
	}
}
