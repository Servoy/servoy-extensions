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
import static com.servoy.extensions.plugins.jwt.client.JwtTestKeys.EC384;
import static com.servoy.extensions.plugins.jwt.client.JwtTestKeys.EC521;
import static com.servoy.extensions.plugins.jwt.client.JwtTestKeys.RSA;
import static com.servoy.extensions.plugins.jwt.client.JwtTestKeys.privateBase64;
import static com.servoy.extensions.plugins.jwt.client.JwtTestKeys.publicBase64;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.rmi.RemoteException;
import java.security.KeyPair;
import java.util.Date;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mozilla.javascript.NativeObject;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.servoy.extensions.plugins.jwt.IJWTService;
import com.servoy.j2db.plugins.IClientPluginAccess;

public class JWTProviderTest
{
	private IClientPluginAccess access;
	private IJWTService service;
	private JWTProvider provider;

	@BeforeEach
	public void setUp() throws Exception
	{
		service = mock(IJWTService.class);
		when(service.getSecret("client-1")).thenReturn("shared-secret");
		access = mock(IClientPluginAccess.class);
		when(access.getClientID()).thenReturn("client-1");
		when(access.getRemoteService(IJWTService.class.getName())).thenReturn(service);
		JWTPlugin plugin = new JWTPlugin();
		plugin.initialize(access);
		provider = (JWTProvider)plugin.getScriptObject();
	}

	private static NativeObject payload(String key, Object value)
	{
		NativeObject object = new NativeObject();
		object.put(key, object, value);
		return object;
	}

	private static long seconds(long epochMillis)
	{
		return epochMillis / 1000 * 1000;
	}

	// --- shared secret (HS256 with jwt.secret.password) ---

	@Test
	public void createAndVerifyWithTheSharedSecret()
	{
		String token = provider.create(payload("user", "admin"));

		assertNotNull(token);
		assertEquals("HS256", JWT.decode(token).getAlgorithm());
		JSONObject verified = (JSONObject)provider.verify(token);
		assertEquals("admin", verified.getString("user"));
	}

	@Test
	public void theSharedSecretIsFetchedFromTheServerOnce() throws Exception
	{
		provider.verify(provider.create(payload("a", "b")));
		provider.verify(provider.create(payload("a", "b")));

		verify(access, times(1)).getRemoteService(IJWTService.class.getName());
		verify(service, times(4)).getSecret("client-1");
	}

	@Test
	public void createWithAnExpiryAddsTheExpClaim()
	{
		Date expires = new Date(seconds(System.currentTimeMillis() + 3_600_000));

		String token = provider.create(payload("user", "admin"), expires);

		assertEquals(expires, JWT.decode(token).getExpiresAt());
		assertNotNull(provider.verify(token));
	}

	@Test
	public void anExpiredTokenIsNotVerified()
	{
		String token = provider.create(payload("user", "admin"), new Date(System.currentTimeMillis() - 60_000));

		assertNotNull(token);
		assertNull(provider.verify(token));
	}

	@Test
	public void withoutASharedSecretNoTokenIsCreatedOrVerified() throws Exception
	{
		when(service.getSecret(anyString())).thenReturn(null);

		assertNull(provider.create(payload("user", "admin")));
		assertNull(provider.verify(provider.builder().withClaim("a", "b").sign(provider.HS256("other"))));
	}

	@Test
	public void aFailingSecretServiceMeansNoSecret() throws Exception
	{
		when(service.getSecret(anyString())).thenThrow(new RemoteException("server down"));
		assertNull(provider.create(payload("user", "admin")));
	}

	@Test
	public void aMissingSecretServiceMeansNoSecret() throws Exception
	{
		when(access.getRemoteService(IJWTService.class.getName())).thenThrow(new IllegalStateException("no server"));
		assertNull(provider.create(payload("user", "admin")));
	}

	// --- HMAC with an explicit password ---

	@ParameterizedTest
	@ValueSource(strings = { "HS256", "HS384", "HS512" })
	public void hmacTokensRoundTripWithTheirOwnPassword(String alg)
	{
		Algorithm algorithm = switch (alg)
		{
			case "HS256" -> provider.HS256("pwd");
			case "HS384" -> provider.HS384("pwd");
			default -> provider.HS512("pwd");
		};

		String token = provider.builder().withClaim("n", Integer.valueOf(1)).sign(algorithm);

		assertEquals(alg, JWT.decode(token).getAlgorithm());
		assertEquals(1, ((JSONObject)provider.verify(token, algorithm)).getInt("n"));
	}

	@Test
	public void hmacWithoutPasswordFallsBackToTheSharedSecret() throws Exception
	{
		String token = provider.builder().withClaim("x", "y").sign(provider.HS384());

		assertNotNull(provider.verify(token, provider.HS384("shared-secret")));
		assertNotNull(provider.verify(provider.builder().withClaim("x", "y").sign(provider.HS512()), provider.HS512("shared-secret")));
	}

	@Test
	public void aWrongPasswordOrTamperedTokenIsNotVerified()
	{
		String token = provider.builder().withClaim("user", "admin").sign(provider.HS256("right"));
		String[] parts = token.split("\\.");
		String tampered = parts[0] + "." + JwtTestKeys.base64("{\"user\":\"root\"}".getBytes()).replace("=", "") + "." + parts[2];

		assertNull(provider.verify(token, provider.HS256("wrong")));
		assertNull(provider.verify(tampered, provider.HS256("right")));
		assertNull(provider.verify("not.a.token", provider.HS256("right")));
	}

	@Test
	public void verifyWithoutAlgorithmReturnsNull()
	{
		assertNull(provider.verify(provider.create(payload("a", "b")), null));
	}

	// --- RSA ---

	@Test
	public void rsaTokensRoundTripForAllSizes()
	{
		String pub = publicBase64(RSA);
		String priv = privateBase64(RSA);

		for (Algorithm[] pair : new Algorithm[][] { { provider.RSA256(pub, priv), provider.RSA256(pub) }, { provider.RSA384(pub, priv),
			provider.RSA384(pub) }, { provider.RSA512(pub, priv), provider.RSA512(pub) } })
		{
			String token = provider.builder().withClaim("role", "admin").sign(pair[0]);
			assertEquals("admin", ((JSONObject)provider.verify(token, pair[1])).getString("role"));
		}
	}

	@Test
	public void rsaKeysCanBeGivenAsBytes()
	{
		byte[] pub = publicBase64(RSA).getBytes();
		byte[] priv = privateBase64(RSA).getBytes();

		assertNotNull(provider.verify(provider.builder().withClaim("a", "b").sign(provider.RSA256(pub, priv)), provider.RSA256(pub)));
		assertNotNull(provider.verify(provider.builder().withClaim("a", "b").sign(provider.RSA384(pub, priv)), provider.RSA384(pub)));
		assertNotNull(provider.verify(provider.builder().withClaim("a", "b").sign(provider.RSA512(pub, priv)), provider.RSA512(pub)));
	}

	@Test
	public void rsaKeysCanBeGivenAsPem()
	{
		String pub = JwtTestKeys.pem("PUBLIC KEY", RSA.getPublic().getEncoded());
		String priv = JwtTestKeys.pem("PRIVATE KEY", RSA.getPrivate().getEncoded());

		String token = provider.builder().withClaim("a", "b").sign(provider.RSA256(pub, priv));

		assertNotNull(token);
		assertNotNull(provider.verify(token, provider.RSA256(pub)));
	}

	@Test
	public void rsaWithOnlyAPublicKeyCannotSign()
	{
		assertNull(provider.builder().withClaim("a", "b").sign(provider.RSA256(publicBase64(RSA))));
	}

	@Test
	public void rsaWithoutAnyKeyIsRejected()
	{
		assertNull(provider.builder().withClaim("a", "b").sign(provider.RSA256((String)null, null)));
	}

	@Test
	public void rsaWithAnInvalidKeyIsRejected()
	{
		assertNull(provider.builder().withClaim("a", "b").sign(provider.RSA256("bm90IGEga2V5", "bm90IGEga2V5")));
	}

	@Test
	public void theKeyIdIsAddedToTheHeader()
	{
		String token = provider.builder().withClaim("a", "b").sign(provider.RSA256(publicBase64(RSA), privateBase64(RSA)).kid("key-1"));

		assertEquals("key-1", JWT.decode(token).getKeyId());
	}

	// --- ECDSA ---

	@Test
	public void ecdsaTokensRoundTripForAllCurves()
	{
		Object[][] cases = { { EC256, "ES256" }, { EC384, "ES384" }, { EC521, "ES512" } };
		for (Object[] c : cases)
		{
			KeyPair pair = (KeyPair)c[0];
			String pub = publicBase64(pair);
			String priv = privateBase64(pair);
			Algorithm signing = switch ((String)c[1])
			{
				case "ES256" -> provider.ES256(pub, priv);
				case "ES384" -> provider.ES384(pub, priv);
				default -> provider.ES512(pub, priv);
			};
			Algorithm verifying = switch ((String)c[1])
			{
				case "ES256" -> provider.ES256(pub);
				case "ES384" -> provider.ES384(pub);
				default -> provider.ES512(pub);
			};

			String token = provider.builder().withClaim("a", "b").sign(signing);

			assertEquals(c[1], JWT.decode(token).getAlgorithm());
			assertNotNull(provider.verify(token, verifying));
		}
	}

	@Test
	public void ecdsaKeysCanBeGivenAsBytes()
	{
		byte[] pub = publicBase64(EC256).getBytes();
		byte[] priv = privateBase64(EC256).getBytes();

		assertNotNull(provider.verify(provider.builder().withClaim("a", "b").sign(provider.ES256(pub, priv)), provider.ES256(pub)));
		assertNotNull(provider.verify(provider.builder().withClaim("a", "b").sign(provider.ES384(publicBase64(EC384).getBytes(),
			privateBase64(EC384).getBytes())), provider.ES384(publicBase64(EC384).getBytes())));
		assertNotNull(provider.verify(provider.builder().withClaim("a", "b").sign(provider.ES512(publicBase64(EC521).getBytes(),
			privateBase64(EC521).getBytes())), provider.ES512(publicBase64(EC521).getBytes())));
	}

	@Test
	public void aTokenSignedWithOneKeyIsNotVerifiedWithAnother()
	{
		String token = provider.builder().withClaim("a", "b").sign(provider.ES256(publicBase64(EC256), privateBase64(EC256)));

		assertNull(provider.verify(token, provider.ES256(publicBase64(JwtTestKeys.generateEc256()))));
		assertNull(provider.verify(token, provider.RSA256(publicBase64(RSA))));
	}

	// --- not before ---

	@Test
	public void aNotYetValidTokenIsOnlyAcceptedWithinTheLeeway()
	{
		Date nbf = new Date(seconds(System.currentTimeMillis() + 30_000));
		String token = provider.builder().withClaim("nbf", nbf).withClaim("a", "b").sign(provider.HS256("pwd"));

		assertNull(provider.verify(token, provider.HS256("pwd")));
		assertNull(provider.verify(token, provider.HS256("pwd"), 5));
		assertNotNull(provider.verify(token, provider.HS256("pwd"), 120));
	}

	// --- other algorithm names ---

	@Test
	public void anUnknownAlgorithmIsRejected()
	{
		assertNull(new Algorithm(provider, "PS256").password("x").publicKey("x").build());
		assertNull(new Algorithm(provider, "HS1").password("x").build());
		assertNull(new Algorithm(provider, "RS1").publicKey(publicBase64(RSA)).build());
		assertNull(new Algorithm(provider, "ES1").publicKey(publicBase64(EC256)).build());
		assertNull(new Algorithm(provider).build());
	}

	@Test
	public void aDecodedTokenKeepsTheClaimsGivenToTheBuilder() throws Exception
	{
		String token = provider.builder().withClaim(JWTClaims.SUB, "user-1").withClaim(JWTClaims.ISS, "servoy").sign(provider.HS256("pwd"));
		DecodedJWT decoded = JWT.decode(token);

		assertEquals("user-1", decoded.getSubject());
		assertEquals("servoy", decoded.getIssuer());
		verify(service, never()).getSecret(anyString());
	}
}
