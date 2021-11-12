/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2021 Servoy BV

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

import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.mozilla.javascript.annotations.JSFunction;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.interfaces.ECDSAKeyProvider;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * Builder for an algorithm object which is used to sign or verify a JWT token.
 * @author emera
 */
@SuppressWarnings("nls")
@ServoyDocumented(scriptingName = "Algorithm")
public class Algorithm implements IScriptable, IJavaScriptType
{
	private String pwd;
	private byte[] pubKey;
	private byte[] privKey;
	private String keyId;
	private final JWTProvider provider;
	private String alg;
	String jwks_url;
	private Jwk jwk;

	public Algorithm(JWTProvider jwtProvider, String alg)
	{
		this.provider = jwtProvider;
		this.alg = alg;
	}

	public Algorithm(JWTProvider jwtProvider)
	{
		this.provider = jwtProvider;
	}

	/**
	 * The secret password for HMAC algorithms.
	 * @param password
	 * @return the algorithm builder for method chaining
	 */
	public Algorithm password(String password)
	{
		this.pwd = password;
		return this;
	}

	/**
	 * The public key (used to verify the token).
	 * @param publicKey as a string
	 * @return the algorithm builder for method chaining
	 */
	public Algorithm publicKey(String publicKey)
	{
		if (publicKey != null)
		{
			String publicKeyContent = publicKey.replaceAll("\\n", "").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
			this.pubKey = publicKeyContent.getBytes();
		}
		return this;
	}

	/**
	 * The private key (used to create the token).
	 * @param privateKey as a string
	 * @return the algorithm builder for method chaining
	 */
	public Algorithm privateKey(String privateKey)
	{
		if (privateKey != null)
		{
			String privateKeyContent = privateKey.replaceAll("\\n", "").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "");
			this.privKey = privateKeyContent.getBytes();
		}
		return this;
	}

	/**
	 * The public key used to verify the token.
	 * @param publicKey as a byte array
	 * @return the algorithm builder for method chaining
	 */
	public Algorithm publicKey(byte[] publicKey)
	{
		this.pubKey = publicKey;
		return this;
	}

	/**
	 * The private key used to create the token.
	 * @param privateKey as a byte array
	 * @return the algorithm builder for method chaining
	 */
	public Algorithm privateKey(byte[] privateKey)
	{
		this.privKey = privateKey;
		return this;
	}

	/**
	 * The key identifier, will be added to the token header.
	 * @param kid the private key identifier
	 * @return  the algorithm builder for method chaining
	 */
	@JSFunction
	public Algorithm kid(String kid)
	{
		this.keyId = kid;
		return this;
	}

	public Algorithm jwksUrl(String url)
	{
		this.jwks_url = url;
		return this;
	}

	/**
	 * Build the algorithm which is used to create and verify jwt tokens.
	 * @return the algorithm object
	 */
	public com.auth0.jwt.algorithms.Algorithm build()
	{
		try
		{
			if (alg == null && jwks_url != null)
			{
				final JwkProvider jwkStore = new UrlJwkProvider(new URL(jwks_url));
				if (keyId != null)
				{
					jwk = jwkStore.get(keyId);
					alg = jwk.getAlgorithm();
				}
				else
				{
					JWTProvider.log.error("JWK error: public key id was not specified");
				}
			}
			else if (privKey == null && pubKey == null)
			{
				JWTProvider.log.error("Cannot create algorithm. Both public and private keys cannot be null.");
				return null;
			}

			if (alg.startsWith("HS"))
				return buildHSAlgorithm();
			if (alg.startsWith("ES"))
				return buildECDSAAlgorithm();
			if (alg.startsWith("RS"))
				return buildRSAAlgorithm();

			JWTProvider.log.error("Algorithm " + alg + " is not supported by the JWT plugin.");
		}
		catch (NoSuchAlgorithmException | InvalidKeySpecException | JwkException | MalformedURLException e)
		{
			JWTProvider.log.error(e.getMessage());
		}
		return null;
	}

	private com.auth0.jwt.algorithms.Algorithm buildRSAAlgorithm() throws NoSuchAlgorithmException, InvalidKeySpecException
	{
		RSAKeyProvider keyProvider = jwk == null ? getRSAPublicPrivateKeyPair() : getJWKRSAProvider();
		switch (alg)
		{
			case JWTAlgorithms.RS256 :
				return com.auth0.jwt.algorithms.Algorithm.RSA256(keyProvider);
			case JWTAlgorithms.RS384 :
				return com.auth0.jwt.algorithms.Algorithm.RSA384(keyProvider);
			case JWTAlgorithms.RS512 :
				return com.auth0.jwt.algorithms.Algorithm.RSA512(keyProvider);
			default :
				JWTProvider.log.error("Algorithm " + alg + " is not supported by the JWT plugin.");
		}
		return null;
	}

	private com.auth0.jwt.algorithms.Algorithm buildECDSAAlgorithm()
		throws NoSuchAlgorithmException, InvalidKeySpecException
	{
		ECDSAKeyProvider keyProvider = jwk == null ? getECPublicPrivateKeyPair() : getJWKECDSAProvider();
		switch (alg)
		{
			case JWTAlgorithms.ES256 :
				return com.auth0.jwt.algorithms.Algorithm.ECDSA256(keyProvider);
			case JWTAlgorithms.ES384 :
				return com.auth0.jwt.algorithms.Algorithm.ECDSA384(keyProvider);
			case JWTAlgorithms.ES512 :
				return com.auth0.jwt.algorithms.Algorithm.ECDSA512(keyProvider);
			default :
				JWTProvider.log.error("Algorithm " + alg + " is not supported by the JWT plugin.");
		}
		return null;
	}

	private com.auth0.jwt.algorithms.Algorithm buildHSAlgorithm()
	{
		if (pwd == null)
		{
			pwd = provider.getSecret();
		}
		switch (alg)
		{
			case JWTAlgorithms.HS256 :
				return com.auth0.jwt.algorithms.Algorithm.HMAC256(pwd);
			case JWTAlgorithms.HS384 :
				return com.auth0.jwt.algorithms.Algorithm.HMAC384(pwd);
			case JWTAlgorithms.HS512 :
				return com.auth0.jwt.algorithms.Algorithm.HMAC512(pwd);
			default :
				JWTProvider.log.error("Algorithm " + alg + " is not supported by the JWT plugin.");
		}
		return null;
	}


	private ECDSAKeyProvider getECPublicPrivateKeyPair()
		throws NoSuchAlgorithmException, InvalidKeySpecException
	{
		KeyFactory keyFactory = KeyFactory.getInstance("EC");
		PKCS8EncodedKeySpec keySpecPKCS8 = privKey != null ? new PKCS8EncodedKeySpec(Base64.decodeBase64(privKey)) : null;
		final PrivateKey private_Key = privKey != null ? keyFactory.generatePrivate(keySpecPKCS8) : null;

		X509EncodedKeySpec keySpecX509 = pubKey != null ? new X509EncodedKeySpec(Base64.decodeBase64(pubKey)) : null;
		final PublicKey public_Key = pubKey != null ? keyFactory.generatePublic(keySpecX509) : null;

		ECDSAKeyProvider keyProvider = new ECDSAKeyProvider()
		{
			@Override
			public ECPublicKey getPublicKeyById(String k)
			{
				return (ECPublicKey)public_Key;
			}

			@Override
			public ECPrivateKey getPrivateKey()
			{
				return (ECPrivateKey)private_Key;
			}

			@Override
			public String getPrivateKeyId()
			{
				return keyId;
			}
		};
		return keyProvider;
	}

	private RSAKeyProvider getRSAPublicPrivateKeyPair()
		throws NoSuchAlgorithmException, InvalidKeySpecException
	{
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PKCS8EncodedKeySpec keySpecPKCS8 = privKey != null ? new PKCS8EncodedKeySpec(Base64.decodeBase64(privKey)) : null;
		final PrivateKey private_Key = privKey != null ? keyFactory.generatePrivate(keySpecPKCS8) : null;

		X509EncodedKeySpec keySpecX509 = pubKey != null ? new X509EncodedKeySpec(Base64.decodeBase64(pubKey)) : null;
		final PublicKey public_Key = pubKey != null ? keyFactory.generatePublic(keySpecX509) : null;


		RSAKeyProvider keyProvider = new RSAKeyProvider()
		{

			@Override
			public RSAPublicKey getPublicKeyById(String k)
			{
				return (RSAPublicKey)public_Key;
			}

			@Override
			public String getPrivateKeyId()
			{
				return keyId;
			}

			@Override
			public RSAPrivateKey getPrivateKey()
			{
				return (RSAPrivateKey)private_Key;
			}
		};
		return keyProvider;
	}

	private RSAKeyProvider getJWKRSAProvider()
	{
		RSAKeyProvider keyProvider = new RSAKeyProvider()
		{
			@Override
			public RSAPublicKey getPublicKeyById(String kid)
			{
				try
				{
					return (RSAPublicKey)jwk.getPublicKey();
				}
				catch (InvalidPublicKeyException e)
				{
					JWTProvider.log.error("JWK error: Cannot get the RSA public key.");
					return null;
				}
			}

			@Override
			public RSAPrivateKey getPrivateKey()
			{
				return null;
			}

			@Override
			public String getPrivateKeyId()
			{
				return null;
			}
		};
		return keyProvider;
	}

	private ECDSAKeyProvider getJWKECDSAProvider()
	{
		ECDSAKeyProvider keyProvider = new ECDSAKeyProvider()
		{
			@Override
			public ECPublicKey getPublicKeyById(String kid)
			{
				try
				{
					return (ECPublicKey)jwk.getPublicKey();
				}
				catch (InvalidPublicKeyException e)
				{
					JWTProvider.log.error("JWK error: Cannot get the EC public key.");
					return null;
				}
			}

			@Override
			public ECPrivateKey getPrivateKey()
			{
				return null;
			}

			@Override
			public String getPrivateKeyId()
			{
				return null;
			}
		};
		return keyProvider;
	}
}
