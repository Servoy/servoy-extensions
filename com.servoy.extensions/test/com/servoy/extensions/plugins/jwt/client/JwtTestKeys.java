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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Key pairs generated once per test run (nothing checked in), plus the PEM/base64 forms the plugin accepts.
 */
final class JwtTestKeys
{
	static final KeyPair RSA = generate("RSA", 2048, null);
	static final KeyPair EC256 = generate("EC", 0, "secp256r1");
	static final KeyPair EC384 = generate("EC", 0, "secp384r1");
	static final KeyPair EC521 = generate("EC", 0, "secp521r1");

	private JwtTestKeys()
	{
	}

	private static KeyPair generate(String algorithm, int size, String curve)
	{
		try
		{
			KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
			if (curve != null) generator.initialize(new ECGenParameterSpec(curve));
			else generator.initialize(size);
			return generator.generateKeyPair();
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	/** A fresh key pair, different from {@link #EC256}. */
	static KeyPair generateEc256()
	{
		return generate("EC", 0, "secp256r1");
	}

	static String base64(byte[] der)
	{
		return Base64.getEncoder().encodeToString(der);
	}

	static String publicBase64(KeyPair pair)
	{
		return base64(pair.getPublic().getEncoded());
	}

	static String privateBase64(KeyPair pair)
	{
		return base64(pair.getPrivate().getEncoded());
	}

	/** PEM with 64 character lines, as exported by openssl. */
	static String pem(String type, byte[] der)
	{
		String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
		return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
	}

	/** A JWKS document with one RSA key (n/e as base64url, no padding). */
	static String rsaJwks(String kid, String alg)
	{
		RSAPublicKey key = (RSAPublicKey)RSA.getPublic();
		Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
		String n = url.encodeToString(unsigned(key.getModulus().toByteArray()));
		String e = url.encodeToString(unsigned(key.getPublicExponent().toByteArray()));
		return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid + "\"," + (alg != null ? "\"alg\":\"" + alg + "\"," : "") + "\"use\":\"sig\",\"n\":\"" + n +
			"\",\"e\":\"" + e + "\"}]}";
	}

	/** A JWKS document with one EC P-256 key (x/y as base64url, no padding). */
	static String ecJwks(String kid, String alg)
	{
		java.security.interfaces.ECPublicKey key = (java.security.interfaces.ECPublicKey)EC256.getPublic();
		Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
		String x = url.encodeToString(fixed(key.getW().getAffineX().toByteArray(), 32));
		String y = url.encodeToString(fixed(key.getW().getAffineY().toByteArray(), 32));
		return "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"kid\":\"" + kid + "\"," + (alg != null ? "\"alg\":\"" + alg + "\"," : "") +
			"\"use\":\"sig\",\"x\":\"" + x + "\",\"y\":\"" + y + "\"}]}";
	}

	private static byte[] fixed(byte[] value, int length)
	{
		byte[] trimmed = unsigned(value);
		if (trimmed.length == length) return trimmed;
		byte[] padded = new byte[length];
		System.arraycopy(trimmed, 0, padded, length - trimmed.length, trimmed.length);
		return padded;
	}

	private static byte[] unsigned(byte[] value)
	{
		if (value.length > 1 && value[0] == 0)
		{
			byte[] trimmed = new byte[value.length - 1];
			System.arraycopy(value, 1, trimmed, 0, trimmed.length);
			return trimmed;
		}
		return value;
	}
}
