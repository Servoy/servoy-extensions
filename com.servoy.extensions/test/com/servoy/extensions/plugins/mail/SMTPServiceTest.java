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
package com.servoy.extensions.plugins.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.servoy.extensions.plugins.mail.client.Attachment;
import com.servoy.extensions.plugins.mail.client.MailPlugin;
import com.servoy.extensions.plugins.mail.client.MailProvider;
import com.servoy.extensions.plugins.oauth.OAuthService;
import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * The SMTPService builder: which smtp properties it sends along with the mail, with and without an oauth service
 * (mocked). The mail itself goes to a mocked IMailService.
 */
public class SMTPServiceTest
{
	private IMailService service;
	private MailProvider provider;

	@BeforeEach
	public void setUp() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		service = mock(IMailService.class);
		when(access.getClientID()).thenReturn("client");
		when(access.getRemoteService(IMailService.class.getName())).thenReturn(service);
		MailPlugin plugin = new MailPlugin();
		plugin.initialize(access);
		provider = (MailProvider)plugin.getScriptObject();
	}

	private Set<String> sentProperties() throws Exception
	{
		ArgumentCaptor<String[]> props = ArgumentCaptor.forClass(String[].class);
		verify(service).sendMail(eq("client"), anyString(), any(), anyString(), anyString(), any(), any(), any(), props.capture());
		return new TreeSet<>(Arrays.asList(props.getValue()));
	}

	@Test
	public void authenticationIsAlwaysOn() throws Exception
	{
		SMTPService smtp = provider.createSMTPService();

		assertTrue(smtp.sendMail("to@x.com", "Hi", "text"));

		assertEquals(Set.of("mail.smtp.auth=true"), sentProperties());
		verify(service).sendMail("client", "to@x.com", null, "Hi", "text", null, null, null, new String[] { "mail.smtp.auth=true" });
	}

	@Test
	public void propertiesFromAndDebug() throws Exception
	{
		SMTPService smtp = provider.createSMTPService().withProperties(new String[] { "mail.smtp.host=smtp.x.com", "invalid", null, "=x" })
			.withFrom("me@x.com").debug(true);

		smtp.sendMail("to@x.com", "Hi", "text");

		assertEquals(Set.of("mail.smtp.auth=true", "mail.smtp.host=smtp.x.com", "mail.debug=true"), sentProperties());
		verify(service).sendMail(eq("client"), eq("to@x.com"), eq("me@x.com"), eq("Hi"), eq("text"), isNull(), isNull(), isNull(), any());
	}

	@Test
	public void aMessageBuilderIsSentWithAllItsParts() throws Exception
	{
		Attachment[] attachments = { new Attachment("a.txt", new byte[] { 1 }) };
		MessageBuilder message = new MessageBuilder().withTo("to@x.com").withSubject("Sub").withRawMsgText("body").withCC("cc@x.com")
			.withBCC("bcc@x.com").withAttachments(attachments);

		provider.createSMTPService().withFrom("me@x.com").sendMail(message);

		verify(service).sendMail(eq("client"), eq("to@x.com"), eq("me@x.com"), eq("Sub"), eq("body"), eq("cc@x.com"), eq("bcc@x.com"),
			eq(attachments), any());
	}

	@Test
	public void aFailedSendIsFalse() throws Exception
	{
		org.mockito.Mockito.doThrow(new Exception("down")).when(service).sendMail(anyString(), anyString(), any(), anyString(), anyString(), any(),
			any(), any(), any());

		assertEquals(false, provider.createSMTPService().sendMail("to@x.com", "Hi", "text"));
		assertEquals("down", provider.js_getLastSendMailExceptionMsg());
	}

	// --- oauth ---

	@Test
	public void oauthUsesXOAuth2WithTheAccessToken() throws Exception
	{
		OAuthService oauth = mock(OAuthService.class);
		when(oauth.getAccessToken()).thenReturn("token-1");
		when(oauth.getRefreshToken()).thenReturn("refresh-1");
		SMTPService smtp = provider.createSMTPService().withFrom("me@x.com").withOAuthService(oauth);

		smtp.sendMail("to@x.com", "Hi", "text");

		assertSame(oauth, smtp.getOAuthService());
		assertEquals("refresh-1", smtp.getRefreshToken());
		assertEquals(Set.of("mail.smtp.auth=true", "mail.smtp.username=me@x.com", "mail.smtp.password=token-1", "mail.smtp.starttls.enable=true",
			"mail.smtp.sasl.enable=true", "mail.smtp.sasl.mechanisms=XOAUTH2", "mail.smtp.auth.mechanisms=XOAUTH2", "mail.smtp.auth.login.disable=true",
			"mail.smtp.auth.plain.disable=true"), sentProperties());
		verify(oauth, never()).refreshToken();
	}

	@Test
	public void anExpiredTokenIsRefreshed() throws Exception
	{
		OAuthService oauth = mock(OAuthService.class);
		when(oauth.getAccessToken()).thenReturn("old", "new");
		when(Boolean.valueOf(oauth.isAccessTokenExpired())).thenReturn(Boolean.TRUE);
		when(oauth.getRefreshToken()).thenReturn("refresh-1");

		provider.createSMTPService().withFrom("me@x.com").withOAuthService(oauth).sendMail("to@x.com", "Hi", "text");

		verify(oauth).refreshToken();
		assertTrue(sentProperties().contains("mail.smtp.password=new"));
	}

	@Test
	public void anExpiredTokenWithoutRefreshTokenIsStillUsed() throws Exception
	{
		OAuthService oauth = mock(OAuthService.class);
		when(oauth.getAccessToken()).thenReturn("expired");
		when(Boolean.valueOf(oauth.isAccessTokenExpired())).thenReturn(Boolean.TRUE);

		provider.createSMTPService().withFrom("me@x.com").withOAuthService(oauth).sendMail("to@x.com", "Hi", "text");

		verify(oauth, never()).refreshToken();
		assertTrue(sentProperties().contains("mail.smtp.password=expired"));
	}

	@Test
	public void withoutAccessTokenSendingFails() throws Exception
	{
		OAuthService oauth = mock(OAuthService.class);

		// current behaviour (bug): without an access token (and no refresh token) the null password is put in the
		// Properties, which throws a NullPointerException instead of a clear "not logged in" error
		assertThrows(NullPointerException.class,
			() -> provider.createSMTPService().withFrom("me@x.com").withOAuthService(oauth).sendMail("to@x.com", "Hi", "text"));
	}

	@Test
	public void oauthWithoutFromFails()
	{
		OAuthService oauth = mock(OAuthService.class);

		// current behaviour (bug): the from address is the smtp user name, without withFrom it is null and
		// Properties.put throws a NullPointerException
		assertThrows(NullPointerException.class, () -> provider.createSMTPService().withOAuthService(oauth).sendMail("to@x.com", "Hi", "text"));
	}

	@Test
	public void noRefreshTokenWithoutOAuth() throws Exception
	{
		SMTPService smtp = provider.createSMTPService();

		assertNull(smtp.getOAuthService());
		assertNull(smtp.getRefreshToken());
	}
}
