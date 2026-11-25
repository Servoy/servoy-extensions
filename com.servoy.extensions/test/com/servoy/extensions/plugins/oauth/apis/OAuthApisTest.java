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
package com.servoy.extensions.plugins.oauth.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.scribejava.apis.openid.OpenIdJsonTokenExtractor;
import com.github.scribejava.core.model.Verb;

public class OAuthApisTest
{
	@Test
	public void oktaEndpointsAreBuiltFromTheDomain()
	{
		OktaApi api = OktaApi.custom("dev-1.okta.example");

		assertEquals("https://dev-1.okta.example/oauth2/default/v1/token", api.getAccessTokenEndpoint());
		assertEquals("https://dev-1.okta.example/oauth2/v1/revoke", api.getRevokeTokenEndpoint());
		assertTrue(api.getAuthorizationUrl("code", "id", null, null, null, null).startsWith("https://dev-1.okta.example/oauth2/default/v1/authorize?"));
		assertEquals(Verb.POST, api.getAccessTokenVerb());
		assertSame(OpenIdJsonTokenExtractor.instance(), api.getAccessTokenExtractor());
	}

	@Test
	public void intuitEndpoints()
	{
		IntuitApi api = IntuitApi.instance();

		assertSame(api, IntuitApi.instance());
		assertEquals("https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer", api.getAccessTokenEndpoint());
		assertEquals("https://developer.api.intuit.com/v2/oauth2/tokens/revoke", api.getRevokeTokenEndpoint());
		assertTrue(api.getAuthorizationUrl("code", "id", null, null, null, null).startsWith("https://appcenter.intuit.com/connect/oauth2?"));
		assertEquals(Verb.POST, api.getAccessTokenVerb());
	}

	@Test
	public void upsEndpoints()
	{
		UPSApi api = UPSApi.instance();

		assertSame(api, UPSApi.instance());
		assertEquals("https://wwwcie.ups.com/security/v1/oauth/token", api.getAccessTokenEndpoint());
		assertEquals("https://apis-pt.ups.com/security/v1/oauth/refresh", api.getRefreshTokenEndpoint());
		assertTrue(api.getAuthorizationUrl("code", "id", null, null, null, null).startsWith("https://wwwcie.ups.com/security/v1/oauth/authorize?"));
	}
}
