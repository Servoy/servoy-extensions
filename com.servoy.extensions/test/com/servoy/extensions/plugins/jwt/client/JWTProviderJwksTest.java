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
package com.servoy.extensions.plugins.jwt.client;

import static com.servoy.extensions.plugins.jwt.client.JwtTestKeys.EC256;
import static com.servoy.extensions.plugins.jwt.client.JwtTestKeys.RSA;
import static com.servoy.extensions.plugins.jwt.client.JwtTestKeys.privateBase64;
import static com.servoy.extensions.plugins.jwt.client.JwtTestKeys.publicBase64;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verification against a JSON Web Key Set. The JWKS is a file in a temp folder, read through a file: URL, so no
 * network is used (UrlJwkProvider reads any URL).
 */
public class JWTProviderJwksTest
{
	@TempDir
	Path tempDir;

	private final JWTProvider provider = new JWTProvider();

	private String jwksUrl(String json) throws Exception
	{
		Path file = Files.writeString(tempDir.resolve("jwks.json"), json, StandardCharsets.UTF_8);
		return file.toUri().toURL().toString();
	}

	private String rsaToken(String alg, String kid)
	{
		Algorithm signing = switch (alg)
		{
			case "RS384" -> provider.RSA384(publicBase64(RSA), privateBase64(RSA));
			case "RS512" -> provider.RSA512(publicBase64(RSA), privateBase64(RSA));
			default -> provider.RSA256(publicBase64(RSA), privateBase64(RSA));
		};
		if (kid != null) signing.kid(kid);
		return provider.builder().withClaim("user", "admin").sign(signing);
	}

	@Test
	public void rsaTokenIsVerifiedWithTheKeyOfItsKid() throws Exception
	{
		Algorithm jwk = provider.JWK(jwksUrl(JwtTestKeys.rsaJwks("key-1", "RS256")));

		JSONObject verified = (JSONObject)provider.verify(rsaToken("RS256", "key-1"), jwk);

		assertEquals("admin", verified.getString("user"));
	}

	@Test
	public void withoutAlgInTheJwkTheAlgorithmOfTheTokenIsUsed() throws Exception
	{
		Algorithm jwk = provider.JWK(jwksUrl(JwtTestKeys.rsaJwks("key-1", null)));

		assertNotNull(provider.verify(rsaToken("RS512", "key-1"), jwk));
	}

	@Test
	public void ecTokenIsVerifiedWithAnEcJwk() throws Exception
	{
		String token = provider.builder().withClaim("a", "b").sign(provider.ES256(publicBase64(EC256), privateBase64(EC256)).kid("ec-1"));

		assertNotNull(provider.verify(token, provider.JWK(jwksUrl(JwtTestKeys.ecJwks("ec-1", "ES256")))));
	}

	@Test
	public void anUnknownKidIsNotVerified() throws Exception
	{
		Algorithm jwk = provider.JWK(jwksUrl(JwtTestKeys.rsaJwks("key-1", "RS256")));

		assertNull(provider.verify(rsaToken("RS256", "other-key"), jwk));
	}

	@Test
	public void aTokenWithoutKidIsNotVerified() throws Exception
	{
		Algorithm jwk = provider.JWK(jwksUrl(JwtTestKeys.rsaJwks("key-1", "RS256")));

		assertNull(provider.verify(rsaToken("RS256", null), jwk));
	}

	@Test
	public void aTokenWithoutKidIsRejectedBeforeTheJwksIsRead() throws Exception
	{
		// the url does not exist: a read would fail with a different error, the missing kid must stop the verification first
		Algorithm jwk = provider.JWK(tempDir.resolve("never-read.json").toUri().toURL().toString()).kid("key-1");

		assertNull(provider.verify(rsaToken("RS256", null), jwk));
		assertNull(jwk.getAlgorithm());
	}

	@Test
	public void aTokenSignedWithAnotherKeyIsNotVerified() throws Exception
	{
		Algorithm jwk = provider.JWK(jwksUrl(JwtTestKeys.rsaJwks("ec-1", "RS256")));
		String token = provider.builder().withClaim("a", "b").sign(provider.ES256(publicBase64(EC256), privateBase64(EC256)).kid("ec-1"));

		assertNull(provider.verify(token, jwk));
	}

	@Test
	public void onlyRsaAndEcKeysAreSupportedFromAJwks() throws Exception
	{
		Algorithm jwk = provider.JWK(jwksUrl(JwtTestKeys.rsaJwks("key-1", "HS256")));

		assertNull(provider.verify(rsaToken("RS256", "key-1"), jwk));
	}

	@Test
	public void anUnreachableJwksIsNotVerified() throws Exception
	{
		Algorithm jwk = provider.JWK(tempDir.resolve("missing.json").toUri().toURL().toString());

		assertNull(provider.verify(rsaToken("RS256", "key-1"), jwk));
	}

	@Test
	public void aMalformedJwksUrlIsNotVerified()
	{
		assertNull(provider.verify(rsaToken("RS256", "key-1"), provider.JWK("not a url")));
	}

	@Test
	public void aJwkAlgorithmCannotSign() throws Exception
	{
		Algorithm jwk = provider.JWK(jwksUrl(JwtTestKeys.rsaJwks("key-1", "RS256")));

		assertNull(provider.builder().withClaim("a", "b").sign(jwk));
		assertNull(provider.builder().withClaim("a", "b").sign(jwk.kid("key-1")));
	}

	@Test
	public void aJwkAlgorithmCanBeReusedForTokensWithDifferentAlgorithms() throws Exception
	{
		Algorithm jwk = provider.JWK(jwksUrl(JwtTestKeys.rsaJwks("key-1", null)));

		assertNotNull(provider.verify(rsaToken("RS256", "key-1"), jwk));
		assertNotNull(provider.verify(rsaToken("RS384", "key-1"), jwk));
		assertNotNull(provider.verify(rsaToken("RS512", "key-1"), jwk));
	}

	@Test
	public void verifyDoesNotChangeTheGivenJwkAlgorithm() throws Exception
	{
		Algorithm jwk = provider.JWK(jwksUrl(JwtTestKeys.rsaJwks("key-1", null)));

		provider.verify(rsaToken("RS384", "key-1"), jwk);

		assertNull(jwk.getAlgorithm());
		// without a kid of its own it still cannot sign or build
		assertNull(jwk.build());
	}

	@Test
	public void theCopyKeepsTheConfiguration()
	{
		Algorithm original = provider.RSA256(JwtTestKeys.publicBase64(RSA), JwtTestKeys.privateBase64(RSA)).kid("key-1");

		Algorithm copy = original.copy();

		assertEquals("RS256", copy.getAlgorithm());
		String token = provider.builder().withClaim("a", "b").sign(copy);
		assertEquals("key-1", com.auth0.jwt.JWT.decode(token).getKeyId());
		assertNotNull(provider.verify(token, original));
	}
}
