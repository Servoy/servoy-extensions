/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2026 Servoy BV

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

package com.servoy.extensions.plugins.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.extensions.plugins.mail.client.MailProvider;
import com.servoy.extensions.plugins.oauth.OAuthService;
import com.servoy.j2db.scripting.IScriptable;

/**
 * Service for sending SMTP email using either password-based authentication
 * or OAuth 2.0 authentication.
 * <p>
 * When OAuth 2.0 is used, this service integrates with an
 * {@link com.servoy.extensions.plugins.oauth.OAuthService OAuthService}
 * instance provided by the Servoy OAuth plugin. The OAuth service is
 * responsible for obtaining and refreshing access tokens. This SMTP service
 * consumes the access token transparently when sending mail.
 * <p>
 * SMTP configuration is provided using standard JavaMail properties. These
 * properties are not replaced and are only augmented when required by the
 * selected authentication mode.
 *
 * <h3>OAuth notes</h3>
 * <ul>
 *   <li>The OAuth access token is obtained and refreshed by the OAuth service</li>
 *   <li>The SMTP service does not persist or manage tokens</li>
 *   <li>XOAUTH2 is used as the SMTP authentication mechanism</li>
 * </ul>
 *
 * @see com.servoy.extensions.plugins.oauth.OAuthService
 * @see SMTPService
 *
 * @author emera
 */
public class SMTPService implements IScriptable
{
	private final MailProvider provider;
	private OAuthService _oauthService;
	private String[] _properties;
	private boolean debug;
	private String _from;

	public SMTPService(MailProvider mailProvider)
	{
		this.provider = mailProvider;
	}

	/**
	 * Sets the SMTP mail properties used by this SMTP service.
	 * <p>
	 * The properties are provided as an array of {@code "key=value"} strings and
	 * correspond to standard JavaMail SMTP configuration options.
	 * <p>
	 * When OAuth authentication is used, additional properties required for XOAUTH2
	 * are automatically added.
	 *
	 * @sample
	 * var properties = [
	 *     "mail.smtp.host=smtp.gmail.com",
	 *     "mail.smtp.port=587",
	 *     "mail.smtp.starttls.enable=true",
	 *     "mail.smtp.auth=true"
	 * ];
	 *
	 * var smtp = plugins.mail.createSMTPService()
	 *     .withProperties(properties);
	 *
	 * @param properties an array of SMTP properties in {@code "key=value"} format
	 * @return this SMTPService instance for chaining
	 */
	@JSFunction
	public SMTPService withProperties(String[] properties)
	{
		this._properties = properties;
		return this;
	}


	/**
	 * Configures this SMTP service to use OAuth 2.0 authentication.
	 * <p>
	 * The provided {@link OAuthService} is used to obtain and refresh OAuth access
	 * tokens when sending mail. The SMTP service itself does not manage or persist
	 * tokens.
	 * <p>
	 * When an OAuth service is set, SMTP authentication is performed using the
	 * XOAUTH2 mechanism.
	 *
	 * @sample
	 * var oauthBuilder = plugins.oauth.serviceBuilder(clientId)
	 *     .clientSecret(secret)
	 *     .refreshToken(refreshToken)
	 *     .defaultScope("https://mail.google.com/")
	 *     .additionalParameters({ "access_type": "offline" });
	 *
	 * var oauthService = oauthBuilder.build(plugins.oauth.OAuthProviders.GOOGLE);
	 *
	 * var smtp = plugins.mail.createSMTPService()
	 *     .withProperties(properties)
	 *     .withOAuthService(oauthService);
	 *
	 * @param oauthService the OAuth service used for SMTP authentication
	 * @return this SMTPService instance for chaining
	 */
	@JSFunction
	public SMTPService withOAuthService(OAuthService oauthService)
	{
		this._oauthService = oauthService;
		return this;
	}

	/**
	 * Returns the OAuth service configured for this SMTP service.
	 * <p>
	 * The returned {@link OAuthService} is used internally to obtain and refresh
	 * access tokens when sending mail.
	 *
	 * @return the OAuth service, or {@code null} if none is configured
	 */
	@JSFunction
	public OAuthService getOAuthService()
	{
		return _oauthService;
	}

	/**
	 * Returns the most recent OAuth refresh token associated with the configured OAuth service.
	 * <p>
	 * This is a convenience method that delegates to the underlying
	 * {@link OAuthService}. If no OAuth service is configured, {@code null} is
	 * returned.
	 *
	 * @return the OAuth refresh token, or {@code null} if no OAuth service is configured
	 * @throws Exception if the refresh token cannot be retrieved
	 */
	@JSFunction
	public String getRefreshToken() throws Exception
	{
		return _oauthService != null ? _oauthService.getRefreshToken() : null;
	}

	/**
	 * Sets the sender email address used for outgoing messages.
	 * <p>
	 * This value is used as the SMTP {@code FROM} address when sending mail.
	 *
	 * @param from the sender email address
	 * @return this SMTPService instance for chaining
	 */
	@JSFunction
	public SMTPService withFrom(String from)
	{
		this._from = from;
		return this;
	}

	/**
	 * Enables or disables SMTP debug logging.
	 * <p>
	 * When enabled, detailed SMTP and authentication information is written to
	 * the Servoy log output.
	 *
	 * @param debug {@code true} to enable debug logging
	 * @return this SMTPService instance for chaining
	 */
	@JSFunction
	public SMTPService debug(boolean debug)
	{
		this.debug = debug;
		return this;
	}

	/**
	 * Sends a simple text email message.
	 * <p>
	 * The message is sent using the configured SMTP properties and authentication
	 * settings.
	 *
	 * @param to the recipient email address
	 * @param subject the message subject
	 * @param msgText the message body text
	 * @return {@code true} if the message was sent successfully, {@code false} otherwise
	 * @throws Exception if sending fails
	 */
	@JSFunction
	public boolean sendMail(String to, String subject, String msgText) throws Exception
	{
		Properties props = setupProperties();
		return provider.sendMail(to, _from, subject, msgText, null, null, null, toPropertiesArray(props));
	}

	/**
	 * Sends an email message created using a {@link MessageBuilder}.
	 * <p>
	 * This allows sending messages with additional options such as CC, BCC, and
	 * attachments.
	 *
	 * @param message the message builder containing message details
	 * @return {@code true} if the message was sent successfully, {@code false} otherwise
	 * @throws Exception if sending fails
	 */
	@JSFunction
	public boolean sendMail(MessageBuilder message) throws Exception
	{
		Properties props = setupProperties();
		return provider.sendMail(message.getTo(), _from, message.getSubject(), message.getRawMsgText(), message.getCC(), message.getBCC(),
			message.getAttachments(), toPropertiesArray(props));
	}

	private Properties setupProperties() throws Exception
	{
		Properties props = createProperties(_properties);
		if (debug)
		{
			props.put("mail.debug", "true");
		}

		props.put("mail.smtp.auth", "true");

		if (_oauthService != null) setupOAuth(props);
		return props;
	}

	private void setupOAuth(Properties props) throws Exception
	{
		props.put("mail.smtp.username", _from);
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.sasl.enable", "true");
		props.put("mail.smtp.sasl.mechanisms", "XOAUTH2");
		props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
		props.put("mail.smtp.auth.login.disable", "true");
		props.put("mail.smtp.auth.plain.disable", "true");

		String token = _oauthService.getAccessToken();
		if ((token == null || _oauthService.isAccessTokenExpired()) && _oauthService.getRefreshToken() != null)
		{
			token = _oauthService.refreshToken();
		}
		props.put("mail.smtp.password", _oauthService.getAccessToken());
	}

	private Properties createProperties(String[] overrideProperties)
	{
		Properties p = new Properties();

		if (overrideProperties == null) return p;

		for (String property : overrideProperties)
		{
			if (property != null)
			{
				int j = property.indexOf('=');
				if (j > 0)
				{
					String propertyName = property.substring(0, j);
					String propertyValue = property.substring(j + 1);
					p.put(propertyName, propertyValue);
				}
			}
		}
		return p;
	}

	private String[] toPropertiesArray(Properties p)
	{
		if (p == null || p.isEmpty()) return new String[0];
		List<String> result = new ArrayList<>(p.size());
		for (Map.Entry<Object, Object> entry : p.entrySet())
		{
			Object key = entry.getKey();
			Object value = entry.getValue();

			if (key instanceof String && value instanceof String)
			{
				result.add(key + "=" + value);
			}
		}
		return result.toArray(new String[0]);
	}
}