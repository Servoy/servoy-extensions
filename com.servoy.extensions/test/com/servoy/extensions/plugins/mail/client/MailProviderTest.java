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
package com.servoy.extensions.plugins.mail.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.extensions.plugins.mail.IMailService;
import com.servoy.extensions.plugins.mail.MessageBuilder;
import com.servoy.extensions.plugins.mail.SMTPService;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * The mail scripting API with a mocked remote IMailService: argument checks and defaults, the overloads that add the
 * smtp or pop3 host, error reporting through getLastSendMailExceptionMsg and the client side helpers.
 */
public class MailProviderTest
{
	private IClientPluginAccess access;
	private IMailService service;
	private MailPlugin plugin;
	private MailProvider provider;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IClientPluginAccess.class);
		service = mock(IMailService.class);
		when(access.getClientID()).thenReturn("client");
		when(access.getRemoteService(IMailService.class.getName())).thenReturn(service);
		plugin = new MailPlugin();
		plugin.load();
		plugin.initialize(access);
		provider = (MailProvider)plugin.getScriptObject();
	}

	// --- plugin ---

	@Test
	public void pluginDescription() throws Exception
	{
		assertEquals("mail", plugin.getName());
		assertNotNull(plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertNull(plugin.getPreferencePanels());
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertSame(provider, plugin.getScriptObject());
		plugin.propertyChange(null);
		assertEquals(List.of(MailMessage.class, Attachment.class, SMTPService.class, MessageBuilder.class), List.of(provider.getAllReturnedTypes()));
		assertNotNull(new MailProvider());

		plugin.unload();

		assertNull(plugin.getClientPluginAccess());
	}

	// --- sending ---

	@Test
	public void sendMailOverloads() throws Exception
	{
		Attachment attachment = new Attachment("a.txt", new byte[] { 1 });
		Attachment[] attachments = { attachment };
		String[] props = { "mail.smtp.port=2525" };

		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m"));
		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m", "cc@x.com"));
		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com"));
		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments));
		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments, props));
		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments, "smtp.x.com"));
		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachment));
		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachment, props));
		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachment, "smtp.x.com"));
		assertTrue(provider.js_sendMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", (Attachment)null, (String)null));

		verify(service).sendMail("client", "to@x.com", "from@x.com", "s", "m", null, null, null, null);
		verify(service).sendMail("client", "to@x.com", "from@x.com", "s", "m", "cc@x.com", null, null, null);
		// the plain cc/bcc call and the call with a null attachment and a null host
		verify(service, times(2)).sendMail("client", "to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", null, null);
		verify(service, times(2)).sendMail("client", "to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments, null);
		verify(service, times(2)).sendMail("client", "to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments, props);
		verify(service, times(2)).sendMail("client", "to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments,
			new String[] { "mail.smtp.host=smtp.x.com" });
		assertNull(provider.js_getLastSendMailExceptionMsg());
	}

	@Test
	public void bulkMailOverloads() throws Exception
	{
		Attachment attachment = new Attachment("a.txt", new byte[] { 1 });
		Attachment[] attachments = { attachment };
		String[] props = { "mail.smtp.port=2525" };

		assertTrue(provider.js_sendBulkMail("to@x.com", "from@x.com", "s", "m"));
		assertTrue(provider.js_sendBulkMail("to@x.com", "from@x.com", "s", "m", "cc@x.com"));
		assertTrue(provider.js_sendBulkMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com"));
		assertTrue(provider.js_sendBulkMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments));
		assertTrue(provider.js_sendBulkMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments, props));
		assertTrue(provider.js_senBulkdMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments, props));
		assertTrue(provider.js_sendBulkMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments, "smtp.x.com"));
		assertTrue(provider.js_sendBulkMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachment));
		assertTrue(provider.js_sendBulkMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachment, props));
		assertTrue(provider.js_sendBulkMail("to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachment, "smtp.x.com"));

		verify(service).sendBulkMail("client", "to@x.com", "from@x.com", "s", "m", null, null, null, null);
		verify(service, times(3)).sendBulkMail("client", "to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments, props);
		verify(service, times(2)).sendBulkMail("client", "to@x.com", "from@x.com", "s", "m", "cc@x.com", "bcc@x.com", attachments,
			new String[] { "mail.smtp.host=smtp.x.com" });
		verify(service, never()).sendMail(anyString(), anyString(), any(), anyString(), anyString(), any(), any(), any(), any());
	}

	@Test
	public void toSubjectAndTextAreRequired() throws Exception
	{
		assertFalse(provider.js_sendMail(null, "f", "s", "m"));
		assertFalse(provider.js_sendMail("t", "f", null, "m"));
		assertFalse(provider.js_sendMail("t", "f", "s", null));
		assertFalse(provider.js_sendBulkMail(null, "f", "s", "m"));
		assertFalse(provider.js_sendBulkMail("t", "f", null, "m"));

		verify(access, never()).getRemoteService(anyString());
	}

	@Test
	public void aFailureIsKeptAsTheLastException() throws Exception
	{
		doThrow(new Exception("550 rejected", new RuntimeException("mailbox full"))).when(service).sendMail(anyString(), anyString(), any(),
			anyString(), anyString(), any(), any(), any(), any());
		doThrow(new Exception("bulk failed")).when(service).sendBulkMail(anyString(), anyString(), any(), anyString(), anyString(), any(), any(),
			any(), any());

		assertFalse(provider.js_sendMail("t@x.com", null, "s", "m"));
		assertEquals("550 rejected; mailbox full", provider.js_getLastSendMailExceptionMsg());
		assertEquals("550 rejected; mailbox full", provider.js_getLastSendMailException());

		assertFalse(provider.js_sendBulkMail("t@x.com", null, "s", "m"));
		assertEquals("bulk failed", provider.js_getLastSendMailExceptionMsg());
	}

	@Test
	public void aCauseWithoutMessageIsNotAdded() throws Exception
	{
		doThrow(new Exception("failed", new RuntimeException())).when(service).sendMail(anyString(), anyString(), any(), anyString(), anyString(),
			any(), any(), any(), any());
		doThrow(new Exception("bulk failed", new RuntimeException(""))).when(service).sendBulkMail(anyString(), anyString(), any(), anyString(),
			anyString(), any(), any(), any(), any());

		assertFalse(provider.js_sendMail("t@x.com", null, "s", "m"));
		assertEquals("failed", provider.js_getLastSendMailExceptionMsg());

		assertFalse(provider.js_sendBulkMail("t@x.com", null, "s", "m"));
		assertEquals("bulk failed", provider.js_getLastSendMailExceptionMsg());
	}

	@Test
	public void theNextSendClearsTheLastException() throws Exception
	{
		doThrow(new Exception("first")).doNothing().when(service).sendMail(anyString(), anyString(), any(), anyString(), anyString(), any(), any(),
			any(), any());

		provider.js_sendMail("t@x.com", null, "s", "m");
		provider.js_sendMail("t@x.com", null, "s", "m");

		assertNull(provider.js_getLastSendMailExceptionMsg());
	}

	@Test
	public void withoutMailServiceNothingIsSent() throws Exception
	{
		when(access.getRemoteService(IMailService.class.getName())).thenThrow(new RemoteException("server not running"));

		assertFalse(provider.js_sendMail("t@x.com", null, "s", "m"));
		assertFalse(provider.js_sendBulkMail("t@x.com", null, "s", "m"));
		assertNull(provider.js_receiveMail("u", "p", Boolean.TRUE));
		assertNull(provider.js_getMailMessage(new byte[] { 1 }));
		assertNull(provider.getFrom());
		assertNull(provider.createSMTPService());
		assertNull(provider.newMessage());
		assertNull(provider.js_getLastSendMailExceptionMsg());
	}

	@Test
	public void theServiceIsLookedUpOnce() throws Exception
	{
		provider.js_sendMail("t@x.com", null, "s", "m");
		provider.js_sendMail("t@x.com", null, "s", "m");

		verify(access, times(1)).getRemoteService(IMailService.class.getName());
	}

	// --- receiving ---

	@Test
	public void receiveMailOverloads() throws Exception
	{
		MailMessage[] messages = { new MailMessage() };
		when(service.receiveMail(anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any(), any())).thenReturn(messages);
		Date sent = new Date(1000);
		String[] props = { "mail.pop3.port=995" };

		assertSame(messages, provider.js_receiveMail("u", "p", Boolean.FALSE));
		provider.js_receiveMail("u", "p", null, Integer.valueOf(IMailService.HEADERS_ONLY));
		provider.js_receiveMail("u", "p", Boolean.TRUE, null, sent);
		provider.js_receiveMail("u", "p", Boolean.TRUE, Integer.valueOf(2), null, "pop.x.com");
		provider.js_receiveMail("u", "p", Boolean.TRUE, Integer.valueOf(2), null, (String)null);
		provider.js_receiveMail("u", "p", Boolean.TRUE, Integer.valueOf(2), sent, props);

		verify(service).receiveMail("client", "u", "p", false, IMailService.FULL, null, null);
		// leaveMsgsOnServer defaults to true
		verify(service).receiveMail("client", "u", "p", true, IMailService.HEADERS_ONLY, null, null);
		verify(service).receiveMail("client", "u", "p", true, IMailService.FULL, sent, null);
		verify(service).receiveMail("client", "u", "p", true, 2, null, new String[] { "mail.pop3.host=pop.x.com" });
		verify(service).receiveMail("client", "u", "p", true, 2, null, null);
		verify(service).receiveMail("client", "u", "p", true, 2, sent, props);
	}

	@Test
	public void receiveNeedsCredentialsAndReturnsNullOnErrors() throws Exception
	{
		assertNull(provider.js_receiveMail(null, "p", Boolean.TRUE));
		assertNull(provider.js_receiveMail("u", null, Boolean.TRUE));
		when(service.receiveMail(anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any(), any())).thenThrow(new RemoteException("x"));

		assertNull(provider.js_receiveMail("u", "p", Boolean.TRUE));
	}

	@Test
	public void theDeprecatedReceiveTakesAnArgumentArray() throws Exception
	{
		Date sent = new Date(1000);

		assertNull(provider.js_recieveMail(null));
		assertNull(provider.js_recieveMail(new Object[] { "u", "p" }));
		assertNull(provider.js_recieveMail(new Object[] { "u", null, Boolean.TRUE }));
		provider.js_recieveMail(new Object[] { "u", "p", "false" });
		provider.js_recieveMail(new Object[] { "u", "p", Boolean.TRUE, Integer.valueOf(1), sent, "pop.x.com" });
		provider.js_recieveMail(new Object[] { "u", "p", Boolean.TRUE, null, "not a date", new Object[] { "mail.pop3.port=995" } });
		provider.js_recieveMail(new Object[] { "u", "p", Boolean.TRUE, null, null, Integer.valueOf(1) });

		verify(service).receiveMail("client", "u", "p", false, 0, null, null);
		verify(service).receiveMail("client", "u", "p", true, 1, sent, new String[] { "mail.pop3.host=pop.x.com" });
		verify(service).receiveMail("client", "u", "p", true, 0, null, new String[] { "mail.pop3.port=995" });
		verify(service).receiveMail("client", "u", "p", true, 0, null, null);
	}

	// --- parsing ---

	@Test
	public void aMailMessageFromBytesOrText() throws Exception
	{
		MailMessage message = new MailMessage();
		when(service.createMailMessageFromBinary(eq("client"), any())).thenReturn(message);

		assertSame(message, provider.js_getMailMessage(new byte[] { 1, 2 }));
		assertSame(message, provider.js_getMailMessage("Subject: x"));
		assertNull(provider.js_getMailMessage(null));
		assertNull(provider.js_getMailMessage(Integer.valueOf(1)));

		verify(service).createMailMessageFromBinary("client", "Subject: x".getBytes());
	}

	@Test
	public void aNonByteArrayIsNull() throws Exception
	{
		// a class cast error is logged
		assertNull(provider.js_getMailMessage(new Object[] { "x" }));
		when(service.createMailMessageFromBinary(anyString(), any())).thenThrow(new RemoteException("x"));
		assertNull(provider.js_getMailMessage(new byte[] { 1 }));
	}

	// --- helpers ---

	@Test
	public void plainAddresses()
	{
		assertArrayEquals(new String[] { "from_me@example.com", "from_pete@example.com" },
			provider.js_getPlainMailAddresses("John Cobb <From_Me@Example.com>,Pete Cobb<from_pete@example.com>; nobody"));
		assertEquals(0, provider.js_getPlainMailAddresses(null).length);
		assertEquals(0, provider.js_getPlainMailAddresses("no addresses").length);
	}

	@Test
	public void emailValidation()
	{
		assertTrue(provider.js_isValidEmailAddress("me@example.com"));
		assertFalse(provider.js_isValidEmailAddress("not an address"));
	}

	@Test
	public void attachments()
	{
		byte[] png = { (byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

		assertEquals("image/png", provider.js_createBinaryAttachment("a.png", png).getMimeType());
		assertEquals("application/x-test", provider.js_createBinaryAttachment("a", png, "application/x-test").getMimeType());
		Attachment text = provider.js_createTextAttachment("a.txt", "hello");
		assertEquals("text/plain", text.getMimeType());
		assertEquals("hello", new String(text.getData(), StandardCharsets.UTF_8));
		assertEquals("text/csv", provider.js_createTextAttachment("a.csv", "a;b", "text/csv").getMimeType());
		assertNull(provider.js_createBinaryAttachment(null, png));
		assertNull(provider.js_createBinaryAttachment("a", null));
		assertNull(provider.js_createBinaryAttachment(null, png, "x/y"));
		assertNull(provider.js_createTextAttachment("a", null));
		assertNull(provider.js_createTextAttachment(null, "x", "x/y"));
	}

	@Test
	public void theDefaultFromAddress() throws Exception
	{
		when(service.getPluginProperty("mail.from")).thenReturn("default@x.com");

		assertEquals("default@x.com", provider.getFrom());

		when(service.getPluginProperty("mail.from")).thenThrow(new RemoteException("x"));
		assertNull(provider.getFrom());
	}

	@Test
	public void builders()
	{
		assertNotNull(provider.createSMTPService());
		assertNotNull(provider.newMessage());
	}

	@Test
	public void sendWithoutClientIdFails() throws Exception
	{
		when(access.getClientID()).thenReturn(null);

		assertTrue(provider.js_sendMail("t@x.com", null, "s", "m"));

		verify(service).sendMail(isNull(), eq("t@x.com"), isNull(), eq("s"), eq("m"), isNull(), isNull(), isNull(), isNull());
	}
}
