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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.github.scribejava.apis.openid.OpenIdJsonTokenExtractor;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.OAuth2AccessTokenExtractor;
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth2.clientauthentication.HttpBasicAuthenticationScheme;
import com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme;

public class CustomApiBuilderTest
{
	private static CustomApiBuilder api()
	{
		return new CustomApiBuilder(" https://auth.example/authorize/ ", "https://auth.example/token/");
	}

	@Test
	public void bothUrlsAreRequired()
	{
		assertThrows(NullPointerException.class, () -> new CustomApiBuilder(null, "https://token"));
		assertThrows(NullPointerException.class, () -> new CustomApiBuilder("https://auth", null));
	}

	@Test
	public void urlsAreTrimmedAndLoseTheirTrailingSlash()
	{
		DefaultApi20 api = api().build();

		assertEquals("https://auth.example/token", api.getAccessTokenEndpoint());
		String url = api.getAuthorizationUrl("code", "id", null, null, null, null);
		assertEquals("https://auth.example/authorize?response_type=code&client_id=id", url);
	}

	@Test
	public void defaultsAreThoseOfAStandardOAuth2Api()
	{
		DefaultApi20 api = api().build();

		assertEquals(Verb.POST, api.getAccessTokenVerb());
		assertEquals("https://auth.example/token", api.getRefreshTokenEndpoint());
		assertSame(OAuth2AccessTokenJsonExtractor.instance(), api.getAccessTokenExtractor());
		assertSame(HttpBasicAuthenticationScheme.instance(), api.getClientAuthentication());
		assertThrows(UnsupportedOperationException.class, api::getRevokeTokenEndpoint);
	}

	@Test
	public void tokenRequestMethodCanBeGet()
	{
		assertEquals(Verb.GET, api().withAccessTokenMethod("GET").build().getAccessTokenVerb());
		assertEquals(Verb.POST, api().withAccessTokenMethod("post").build().getAccessTokenVerb());
		assertEquals(Verb.POST, api().withAccessTokenMethod("put").build().getAccessTokenVerb());
	}

	@Test
	public void refreshAndRevokeEndpointsCanBeSet()
	{
		DefaultApi20 api = api().withRefreshTokenEndpoint("https://auth.example/refresh/").withRevokeTokenEndpoint(" https://auth.example/revoke").build();

		assertEquals("https://auth.example/refresh", api.getRefreshTokenEndpoint());
		assertEquals("https://auth.example/revoke", api.getRevokeTokenEndpoint());
	}

	@Test
	public void tokenExtractorsFromTheConstantsAreUsed() throws Exception
	{
		assertSame(OAuth2AccessTokenExtractor.instance(), api().withAccessTokenExtractor(OAuthTokenExtractors.OAuth2).build().getAccessTokenExtractor());
		assertSame(OAuth2AccessTokenJsonExtractor.instance(), api().withAccessTokenExtractor(OAuthTokenExtractors.OAuthJson).build().getAccessTokenExtractor());
		assertSame(OpenIdJsonTokenExtractor.instance(), api().withAccessTokenExtractor(OAuthTokenExtractors.OpenIdJson).build().getAccessTokenExtractor());
	}

	@Test
	public void clientAuthenticationsFromTheConstantsAreUsed() throws Exception
	{
		assertSame(HttpBasicAuthenticationScheme.instance(),
			api().withClientAuthentication(ClientAuthentication.Http_Basic_AuthenticationScheme).build().getClientAuthentication());
		assertSame(RequestBodyAuthenticationScheme.instance(),
			api().withClientAuthentication(ClientAuthentication.Request_Body_AuthenticationScheme).build().getClientAuthentication());
	}

	@Test
	public void aClassWithAnInstanceMethodOfTheWrongTypeIsRejected()
	{
		String notAnExtractor = "com.servoy.extensions.plugins.oauth.apis.IntuitApi";

		Exception extractor = assertThrows(Exception.class, () -> api().withAccessTokenExtractor(notAnExtractor));
		Exception authentication = assertThrows(Exception.class, () -> api().withClientAuthentication(notAnExtractor));

		assertEquals("'" + notAnExtractor + "' extractor was not found or is not a Token Extractor", extractor.getMessage());
		assertEquals("'" + notAnExtractor + "' was not found or is not a client authentication type", authentication.getMessage());
	}

	@Test
	public void unknownOrUnsuitableClassesThrowTheReflectionError()
	{
		// current behaviour: the plugin's own error message is only used for classes that do have an instance() method
		assertThrows(ClassNotFoundException.class, () -> api().withAccessTokenExtractor("no.such.Extractor"));
		assertThrows(NoSuchMethodException.class, () -> api().withAccessTokenExtractor("java.lang.String"));
		assertThrows(ClassNotFoundException.class, () -> api().withClientAuthentication("no.such.Authentication"));
		assertThrows(NoSuchMethodException.class, () -> api().withClientAuthentication("java.lang.String"));
	}
}
