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

package com.servoy.extensions.plugins.jwt.client;

import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.annotations.JSFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.extensions.plugins.jwt.IJWTService;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Utils;

/**
 * @author emera
 */
@ServoyDocumented(publicName = JWTPlugin.PLUGIN_NAME, scriptingName = "plugins." + JWTPlugin.PLUGIN_NAME)
public class JWTProvider implements IScriptable, IReturnedTypesProvider
{
	private final JWTPlugin plugin;
	private IJWTService jwtService;
	public static final Logger log = LoggerFactory.getLogger("plugin.jwt");

	public JWTProvider(JWTPlugin jwtPlugin)
	{
		this.plugin = jwtPlugin;
	}

	private void createJWTService()
	{
		if (jwtService == null)
		{
			try
			{
				IClientPluginAccess access = plugin.getClientPluginAccess();
				jwtService = (IJWTService)access.getRemoteService(IJWTService.class.getName());
			}
			catch (Exception ex)
			{
				log.error(ex.getMessage());
			}
		}
	}

	/**
	 * Create a JSON Web Token for the given payload that is signed with the (shared) secret key 'jwt.secret.password' that has to be configured on the admin page for this plugin.
	 * The payload can be for example a user:username of the current user, so that with this token if it verifies with the same secret key you can assume it is the same user that wants to login.
	 *
	 * @param payload a json containing the data,
	 * 		e.g. {'some': 'data', 'somemore': 'data2'}
	 * @return a string representing the encrypted data
	 * 		or null if the token cannot be generated
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = false)
	@JSFunction
	public String create(Object payload)
	{
		Algorithm alg = new Algorithm(this, JWTAlgorithms.HS256);
		return builder().payload(payload).sign(alg);
	}

	/**
	 * Create a JSON Web Token for the given payload that is signed with the (shared) secret key 'jwt.secret.password' that has to be configured on the admin page for this plugin.
	 * The payload can be for example a user:username of the current user, so that with this token if it verifies with the same secret key you can assume it is the same user that wants to login.
	 * The expiresAt makes sure this token is only valid until that date.
	 *
	 * @param payload a json containing the data,
	 * 		e.g. {'some': 'data', 'somemore': 'data2'}
	 * @param expiresAt the date when the created token expires,
	 * 		after the expired date the token won't be verified
	 * @return a string representing the encrypted data
	 * 		or null if the token cannot be generated
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = false)
	@JSFunction
	public String create(Object payload, Date expiresAt)
	{
		Algorithm alg = new Algorithm(this, JWTAlgorithms.HS256);
		return builder().payload(payload).withExpires(expiresAt).sign(alg);
	}

	/**
	 * Verify a token that is created with the {@link #create(Object)} method.
	 * This will only verify and return the payload that was given if the token could be verified with the (shared) secret key 'jwt.secret.password' that is configured on the admin page.
	 * Will also return null if the token passed its expire date.
	 *
	 * @param token a JSON Web Token
	 * @return the payload or null if the token can't be verified
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = false)
	@JSFunction
	public Object verify(String token)
	{
		Algorithm alg = new Algorithm(this, JWTAlgorithms.HS256);
		return verify(token, alg);
	}

	/**
	 * Verify a token that is created with the {@link #create(Object)} method.
	 * This will only verify and return the payload that was given if the token could be verified with the provided algorithm.
	 * Will also return null if the token passed its expire date.
	 *
	 * @param token a JSON Web Token
	 * @param alg an algorithm used to verify the signature
	 * @return the payload or null if the token can't be verified
	 */
	@JSFunction
	public Object verify(String token, Algorithm alg)
	{
		if (alg != null)
		{
			try
			{
				com.auth0.jwt.algorithms.Algorithm algo = alg.build();
				if (algo != null)
				{
					JWTVerifier jwtVerifier = JWT.require(algo)
						.build();
					DecodedJWT jwt = jwtVerifier.verify(token);
					String payload = new String(Utils.decodeBASE64(jwt.getPayload()));
					return new JSONObject(payload);
				}
			}
			catch (TokenExpiredException e)
			{
				if (log.isTraceEnabled()) log.trace(e.getMessage());
			}
			catch (JWTVerificationException | JSONException e)
			{
				log.error(e.getMessage());
			}
		}
		return null;
	}

	/**
	 * Create a new Algorithm instance using HmacSHA256. Tokens specify this as "HS256".
	 * The password used to configure the algorithm is the (shared) secret key 'jwt.secret.password' that has to be configured on the admin page for this plugin.
	 * @sample plugins.jwt.HS256()
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = false)
	@JSFunction
	public Algorithm HS256()
	{
		return new Algorithm(this, JWTAlgorithms.HS256);
	}

	/**
	 * Create a new HmacSHA256 Algorithm using the specified password. Tokens specify this as "HS256".
	 * @param password the secret used to encrypt and decrypt the tokens
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm HS256(String password)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.HS256);
		return algorithm.password(password);
	}

	/**
	 * Create a new Algorithm instance using HmacSHA384. Tokens specify this as "HS384".
	 * The password used to configure the algorithm is the (shared) secret key 'jwt.secret.password' that has to be configured on the admin page for this plugin.
	 * @sample plugins.jwt.HS384()
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = false)
	@JSFunction
	public Algorithm HS384()
	{
		return new Algorithm(this, JWTAlgorithms.HS384);
	}

	/**
	 * Create a new HmacSHA384 Algorithm using the specified password. Tokens specify this as "HS384".
	 * @sample plugins.jwt.HS384('your secret password.....')
	 * @param password the secret used to encrypt and decrypt the tokens
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm HS384(String password)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.HS384);
		return algorithm.password(password);
	}


	/**
	 * Create a new Algorithm instance using HmacSHA512. Tokens specify this as "HS512".
	 * The password used to configure the algorithm is the (shared) secret key 'jwt.secret.password' that has to be configured on the admin page for this plugin.
	 * @sample plugins.jwt.HS512.secret()
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = false)
	@JSFunction
	public Algorithm HS512()
	{
		return new Algorithm(this, JWTAlgorithms.HS512);
	}

	/**
	 * Create a new Algorithm instance using HmacSHA512. Tokens specify this as "HS512".
	 * @sample plugins.jwt.HS512.secret('your secret password.....')
	 * @param password the secret used to encrypt and decrypt the tokens
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm HS512(String password)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.HS512);
		return algorithm.password(password);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA256withRSA. Tokens specify this as "RS256".
	 * @sample plugins.jwt.RSA256('MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnzyis...')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm used to sign or verify Json Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA256(String publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS256);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA256withRSA. Tokens specify this as "RS256".
	 * @sample plugins.jwt.RSA256('MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnzyis...', 'MIIEogIBAAKCAQEAnzyis1ZjfNB0bBgKFMSvvkTtwlvB...')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a String representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify Json Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA256(String publicKey, String privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS256);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA256withRSA. Tokens specify this as "RS256".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm used to sign or verify Json Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA256(byte[] publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS256);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA256withRSA. Tokens specify this as "RS256".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a byte array representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify Json Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA256(byte[] publicKey, byte[] privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS256);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}


	/**
	 * Builder to create a new Algorithm instance using SHA384withRSA. Tokens specify this as "RS384".
	 * @sample plugins.jwt.RSA384('MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnzyis...')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA384(String publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS384);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA384withRSA. Tokens specify this as "RS384".
	 * @sample plugins.jwt.RSA384.publicKey('MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnzyis...', 'MIIEogIBAAKCAQEAnzyis1ZjfNB0bBgKFMSvvkTtwlvB...')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a String representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA384(String publicKey, String privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS384);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA384withRSA. Tokens specify this as "RS384".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA384(byte[] publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS384);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA384withRSA. Tokens specify this as "RS384".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a byte array representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA384(byte[] publicKey, byte[] privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS384);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}


	/**
	 * Builder to create a new Algorithm instance using SHA512withRSA. Tokens specify this as "RS512".
	 * @sample plugins.jwt.RSA512('MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnzyis...')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA512(String publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS512);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA512withRSA. Tokens specify this as "RS512".
	 * @sample plugins.jwt.RSA512('MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnzyis...','MIIEogIBAAKCAQEAnzyis1ZjfNB0bBgKFMSvvkTtwlvB...')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a String representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA512(String publicKey, String privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS512);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA512withRSA. Tokens specify this as "RS512".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA512(byte[] publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS512);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA512withRSA. Tokens specify this as "RS512".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a byte array representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm RSA512(byte[] publicKey, byte[] privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.RS512);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA256withECDSA. Tokens specify this as "ES256".
	 * @sample plugins.jwt.ES256('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEV....')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm builder used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES256(String publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES256);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA256withECDSA. Tokens specify this as "ES256".
	 * @sample plugins.jwt.ES256.publicKey('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEV....', 'MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wa...')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a String representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES256(String publicKey, String privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES256);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA256withECDSA. Tokens specify this as "ES256".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm builder used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES256(byte[] publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES256);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA256withECDSA. Tokens specify this as "ES256".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a byte array representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES256(byte[] publicKey, byte[] privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES256);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA384withECDSA. Tokens specify this as "ES384".
	 * @sample plugins.jwt.ES384('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEV....')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm builder used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES384(String publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES384);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA384withECDSA. Tokens specify this as "ES384".
	 * @sample plugins.jwt.ES384.publicKey('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEV....', 'MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wa...')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a String representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES384(String publicKey, String privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES384);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}


	/**
	 * Builder to create a new Algorithm instance using SHA384withECDSA. Tokens specify this as "ES384".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm builder used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES384(byte[] publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES384);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA384withECDSA. Tokens specify this as "ES384".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a byte array representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES384(byte[] publicKey, byte[] privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES384);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA512withECDSA. Tokens specify this as "ES512".
	 * @sample plugins.jwt.ES512('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEV....')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey
	 * @return an algorithm builder used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES512(String publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES512);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA512withECDSA. Tokens specify this as "ES512".
	 * @sample plugins.jwt.ES512.publicKey('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEV....', 'MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wa...')
	 *      .kid('2X9R4H....')
	 * @param publicKey a String representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a String representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES512(String publicKey, String privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES512);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}


	/**
	 * Builder to create a new Algorithm instance using SHA512withECDSA. Tokens specify this as "ES512".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @return an algorithm builder used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES512(byte[] publicKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES512);
		return algorithm.publicKey(publicKey);
	}

	/**
	 * Builder to create a new Algorithm instance using SHA512withECDSA. Tokens specify this as "ES512".
	 * @param publicKey a byte array representing the publicKey (mostly used to verify tokens)
	 * @param privateKey a byte array representing the privateKey (mostly used to create tokens)
	 * @return an algorithm used to sign or verify JSON Web Tokens.
	 */
	@JSFunction
	public Algorithm ES512(byte[] publicKey, byte[] privateKey)
	{
		Algorithm algorithm = new Algorithm(this, JWTAlgorithms.ES512);
		return algorithm.publicKey(publicKey).privateKey(privateKey);
	}


	/**
	 * Returns a JSON Web Token token builder.
	 * @sample var algorithm = plugins.jwt.ES256(publicKey, privateKey);
	 *
	 *		   var token = plugins.jwt.builder()
	 *                     .payload({'some': 'data', 'somemore': 'data2'})
	 *                     .sign(algorithm);
	 *		   if (token != null) {
	 *		       //success
	 *		       application.output(token);
	 *		   }
	 *         else {
	 *             application.output('Could not create a token.');
	 *         }
	 *
	 *         var verified = plugins.jwt.verify(token, algorithm);
	 *         if (verified != null) {
	 *              //success
	 *		       application.output(verified);
	 *         }
	 *         else {
	 *             application.output('The token is not valid.');
	 *         }
	 *
	 *
	 * @return an object which creates a jwt token.
	 */
	@JSFunction
	public Builder builder()
	{
		return new Builder();
	}

	String getSecret()
	{
		createJWTService();
		if (jwtService != null)
		{
			try
			{
				return jwtService.getSecret(plugin.getClientPluginAccess().getClientID());
			}
			catch (Exception e)
			{
				log.error(e.getMessage());
			}
		}
		return null;
	}

	@Override
	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { JWTClaims.class };
	}
}
