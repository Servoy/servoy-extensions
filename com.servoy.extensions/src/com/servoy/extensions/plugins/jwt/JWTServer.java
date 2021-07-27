/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2020 Servoy BV

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

package com.servoy.extensions.plugins.jwt;

import java.rmi.RemoteException;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeArray;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.ECDSAKeyProvider;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.servoy.extensions.plugins.jwt.client.JWTAlgorithms;
import com.servoy.extensions.plugins.jwt.client.JWTHeaders;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * @author emera
 */
public class JWTServer implements IServerPlugin, IJWTService
{
	private static final String ALG = "alg";
	private static final String JWT_SECRET_KEY = "jwt.secret.password";


	private Properties settings;
	private JWTVerifier verifier;
	private Algorithm algorithm;

	private final Map<String, Algorithm> algorithms = new HashMap<>();
	private final Map<String, JWTVerifier> verifiers = new HashMap<>();

	public JWTServer()
	{
	}


	@Override
	public void load() throws PluginException
	{
	}

	@Override
	public void unload() throws PluginException
	{
		settings = null;
	}

	@Override
	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "JWT Plugin"); //$NON-NLS-1$
		return props;
	}

	@Override
	public void initialize(IServerAccess app) throws PluginException
	{
		settings = app.getSettings();

		try
		{
			app.registerRemoteService(IJWTService.class.getName(), this);
		}
		catch (Exception e)
		{
			Debug.error(e);
		}

		Iterator it = settings.keySet().iterator();
		while (it.hasNext())
		{
			String key = (String)it.next();
			if (key.startsWith("jwt."))
			{
				String value = settings.getProperty(key);
				if (value != null && !value.trim().equals(""))
				{
					System.setProperty(key, value);
				}
			}
		}
	}

	private boolean checkInit()
	{
		if (settings.getProperty(JWT_SECRET_KEY) == null)
		{
			Debug.error("Cannot create JWT token, the " + JWT_SECRET_KEY + " was not set on the admin page.");
			return false;
		}
		if (algorithm == null)
		{
			try
			{
				algorithm = Algorithm.HMAC256(settings.getProperty(JWT_SECRET_KEY));
			}
			catch (Exception e)
			{
				Debug.error(e);
				return false;
			}
		}
		return true;
	}

	@Override
	public Map<String, String> getRequiredPropertyNames()
	{
		Map<String, String> req = new LinkedHashMap<String, String>();
		req.put(JWT_SECRET_KEY,
			"Shared secret, used to sign and verify the JWT tokens. Should be the same on all servers that want to sign or verify the same tokens.");
		return req;
	}

	@Override
	public String create(JSONObject claims, Date expire) throws RemoteException
	{
		try
		{
			if (!checkInit()) return null;
			Builder builder = JWT.create();
			addClaims(claims, builder);
			if (expire != null)
			{
				builder.withExpiresAt(expire);
			}
			return builder.sign(algorithm);
		}
		catch (Exception e)
		{
			Debug.error("Could not create the JWT token", e);
		}
		return null;
	}


	/**
	 * @param claims
	 * @param builder
	 */
	private void addClaims(JSONObject claims, Builder builder)
	{
		if (claims != null)
		{
			for (String key : claims.keySet())
			{
				Object value = claims.get(key);
				if (value instanceof Boolean) builder.withClaim(key, (Boolean)value);
				if (value instanceof Date) builder.withClaim(key, (Date)value);
				if (value instanceof Double) builder.withClaim(key, (Double)value);
				if (value instanceof Integer) builder.withClaim(key, (Integer)value);
				if (value instanceof Long) builder.withClaim(key, (Long)value);
				if (value instanceof String) builder.withClaim(key, (String)value);
				if (value instanceof NativeArray)
				{
					if (isNumbersArray((NativeArray)value))
					{
						builder.withArrayClaim(key, convertToLongArray(value));
					}
					else
					{
						builder.withArrayClaim(key, convertToStringArray(value));
					}
				}
			}
		}
	}


	@Override
	public String create(JSONObject headers, JSONObject claims, Date expire) throws RemoteException, Exception
	{
		try
		{
			Algorithm alg;
			if (headers != null && headers.has(JWTHeaders.ALG))
			{
				alg = algorithms.get(headers.getString(JWTHeaders.ALG) + headers.optString(JWTHeaders.KID, ""));
			}
			else
			{
				if (!checkInit()) return null;
				alg = algorithm;
			}
			Builder builder = JWT.create();
			if (headers != null)
			{
				Map<String, Object> headerClaims = new HashMap<String, Object>();
				for (String k : headers.keySet())
				{
					headerClaims.put(k, headers.get(k));
				}
				builder.withHeader(headerClaims);
			}
			addClaims(claims, builder);
			if (expire != null)
			{
				builder.withExpiresAt(expire);
			}

			return builder.sign(alg);
		}
		catch (Exception e)
		{
			Debug.error("Could not create the JWT token", e);
		}
		return null;
	}

	private ECDSAKeyProvider getECPublicPrivateKeyPair(byte[] publicKey, byte[] privateKey, String kid)
		throws NoSuchAlgorithmException, InvalidKeySpecException
	{
		KeyFactory keyFactory = KeyFactory.getInstance("EC");
		PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(Base64.decodeBase64(privateKey));
		final PrivateKey privKey = keyFactory.generatePrivate(keySpecPKCS8);

		X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.decodeBase64(publicKey));
		final PublicKey pubKey = keyFactory.generatePublic(keySpecX509);

		ECDSAKeyProvider keyProvider = new ECDSAKeyProvider()
		{
			@Override
			public ECPublicKey getPublicKeyById(String keyId)
			{
				return (ECPublicKey)pubKey;
			}

			@Override
			public ECPrivateKey getPrivateKey()
			{
				return (ECPrivateKey)privKey;
			}

			@Override
			public String getPrivateKeyId()
			{
				return kid;
			}
		};

		return keyProvider;
	}

	private RSAKeyProvider getRSAPublicPrivateKeyPair(byte[] publicKey, byte[] privateKey, String kid)
		throws NoSuchAlgorithmException, InvalidKeySpecException
	{
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(Base64.decodeBase64(privateKey));
		final PrivateKey privKey = keyFactory.generatePrivate(keySpecPKCS8);

		X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.decodeBase64(publicKey));
		final PublicKey pubKey = keyFactory.generatePublic(keySpecX509);

		RSAKeyProvider keyProvider = new RSAKeyProvider()
		{

			@Override
			public RSAPublicKey getPublicKeyById(String keyId)
			{
				return (RSAPublicKey)pubKey;
			}

			@Override
			public String getPrivateKeyId()
			{
				return kid;
			}

			@Override
			public RSAPrivateKey getPrivateKey()
			{
				return (RSAPrivateKey)privKey;
			}
		};
		return keyProvider;
	}


	private Long[] convertToLongArray(Object value)
	{
		NativeArray array = (NativeArray)value;
		int size = Long.valueOf(array.getLength()).intValue();
		Long[] result = new Long[size];
		for (int i = 0; i < size; i++)
		{
			result[i] = array.get(i) == null ? null : ((Number)array.get(i)).longValue();
		}
		return result;
	}

	private String[] convertToStringArray(Object value)
	{
		NativeArray array = (NativeArray)value;
		int size = Long.valueOf(array.getLength()).intValue();
		String[] result = new String[size];
		for (int i = 0; i < size; i++)
		{
			result[i] = array.get(i) == null ? null : array.get(i).toString();
		}
		return result;
	}

	private boolean isNumbersArray(NativeArray value)
	{
		for (int i = 0; i < value.getLength(); i++)
		{
			if (value.get(i) != null && !(value.get(i) instanceof Number)) return false;
		}
		return true;
	}

	@Override
	public JSONObject verify(String token) throws RemoteException
	{
		try
		{
			if (token == null) return null;
			JSONObject headers = new JSONObject(new String(Utils.decodeBASE64(token)));
			String algKey = headers.getString(JWTHeaders.ALG) + headers.optString(JWTHeaders.KID);

			JWTVerifier jwtVerifier = verifiers.get(algKey);
			if (jwtVerifier == null)
			{
				if (!checkInit()) return null; //TODO check init alg
				jwtVerifier = JWT.require(algorithms.get(algKey))
					.build();
				verifiers.put(algKey, jwtVerifier);
			}
			DecodedJWT jwt = jwtVerifier.verify(token);
			String payload = new String(Utils.decodeBASE64(jwt.getPayload()));
			return new JSONObject(payload);
		}
		catch (TokenExpiredException e)
		{
			Debug.trace(e);
		}
		catch (JWTVerificationException | JSONException e)
		{
			Debug.error(e);
		}
		return null;
	}

	@Override
	public boolean configureAlgorithm(String alg, String publicKey, String privateKey, String kid)
	{
		byte[] pub = publicKey != null ? publicKey.getBytes() : null;
		byte[] priv = privateKey != null ? privateKey.getBytes() : null;
		return configureAlgorithm(alg, pub, priv, kid);
	}


	@Override
	public boolean configureAlgorithm(String alg, byte[] publicKey, byte[] privateKey, String kid)
	{
		String key = alg + (kid != null ? kid : "");
		try
		{
			switch (alg)
			{
				case JWTAlgorithms.HS256 :
					//(this is the default actually)...
					algorithms.put(key, Algorithm.HMAC256(settings.getProperty(JWT_SECRET_KEY)));
					break;
				case JWTAlgorithms.HS384 :
					algorithms.put(key, Algorithm.HMAC384(settings.getProperty(JWT_SECRET_KEY)));
					break;
				case JWTAlgorithms.HS512 :
					algorithms.put(key, Algorithm.HMAC512(settings.getProperty(JWT_SECRET_KEY)));
					break;

				case JWTAlgorithms.ES256 :
					algorithms.put(key, Algorithm.ECDSA256(getECPublicPrivateKeyPair(publicKey, privateKey, kid)));
					break;
				case JWTAlgorithms.ES384 :
					algorithms.put(key, Algorithm.ECDSA384(getECPublicPrivateKeyPair(publicKey, privateKey, kid)));
					break;
				case JWTAlgorithms.ES512 :
					algorithms.put(key, Algorithm.ECDSA512(getECPublicPrivateKeyPair(publicKey, privateKey, kid)));
					break;

				case JWTAlgorithms.RS256 :
					algorithms.put(key, Algorithm.RSA256(getRSAPublicPrivateKeyPair(publicKey, privateKey, kid)));
					break;
				case JWTAlgorithms.RS384 :
					algorithms.put(key, Algorithm.RSA384(getRSAPublicPrivateKeyPair(publicKey, privateKey, kid)));
					break;
				case JWTAlgorithms.RS512 :
					algorithms.put(key, Algorithm.RSA512(getRSAPublicPrivateKeyPair(publicKey, privateKey, kid)));
					break;
				default :
					Debug.error("Algorithm " + alg + " is not supported by the JWT plugin.");
					return false;
			}
			return true;
		}
		catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e)
		{
			Debug.error(e);
		}
		return false;
	}
}
