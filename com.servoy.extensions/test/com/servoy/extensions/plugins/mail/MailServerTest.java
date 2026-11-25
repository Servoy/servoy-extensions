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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.extensions.plugins.mail.client.Attachment;
import com.servoy.extensions.plugins.mail.client.MailMessage;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;

/**
 * MailServer plugin setup, access checks and the small data classes of the mail plugin.
 */
public class MailServerTest
{
	private static final String TEST_PROPERTY = "mail.servoytest.copied";

	private Properties settings;
	private IServerAccess access;
	private MailServer server;

	@BeforeEach
	public void setUp()
	{
		settings = new Properties();
		access = mock(IServerAccess.class);
		when(access.getSettings()).thenReturn(settings);
		server = new MailServer();
		server.load();
	}

	@AfterEach
	public void tearDown()
	{
		System.clearProperty(TEST_PROPERTY);
	}

	// --- plugin ---

	@Test
	public void pluginDescription() throws Exception
	{
		server.initialize(access);

		verify(access).registerRemoteService(IMailService.class.getName(), server);
		assertEquals("Mail Server Plugin", server.getProperties().get(IServerPlugin.DISPLAY_NAME));
		assertNull(server.getPreferencePanels());
		assertEquals("Allow mailserver access for clients that are not authenticated (true/false), defaults to false",
			server.getRequiredPropertyNames().get("mail.server.allowUnauthenticatedRMIAccess"));
		assertTrue(server.getRequiredPropertyNames().containsKey("mail.smtp.host"));
		assertEquals(15, server.getRequiredPropertyNames().size());
	}

	@Test
	public void mailSettingsAreCopiedToTheSystemProperties() throws Exception
	{
		settings.setProperty(TEST_PROPERTY, "yes");
		settings.setProperty("mail.servoytest.empty", " ");
		settings.setProperty("other.setting", "x");

		server.initialize(access);

		assertEquals("yes", System.getProperty(TEST_PROPERTY));
		assertNull(System.getProperty("mail.servoytest.empty"));
		assertNull(System.getProperty("other.setting"));
		assertEquals("yes", server.getPluginProperty(TEST_PROPERTY));
	}

	@Test
	public void aFailingRegistrationIsOnlyLogged() throws Exception
	{
		doThrow(new RemoteException("no rmi")).when(access).registerRemoteService(anyString(), any());

		server.initialize(access);

		assertNull(server.getPluginProperty("mail.smtp.host"));
	}

	@Test
	public void unloadForgetsTheSettings() throws Exception
	{
		server.initialize(access);

		server.unload();

		assertThrows(NullPointerException.class, () -> server.getPluginProperty("x"));
	}

	// --- access ---

	@Test
	public void unauthenticatedClientsAreRejected() throws Exception
	{
		server.initialize(access);

		// nothing is sent or received for a client that is not authenticated
		server.sendMail("stranger", "a@example.com", null, "s", "t", null, null, null, null);
		server.sendBulkMail("stranger", "a@example.com", null, "s", "t", null, null, null, null);
		assertNull(server.receiveMail("stranger", "u", "p", true, IMailService.FULL, null, null));
		assertNull(server.createMailMessageFromBinary("stranger", new byte[0]));
	}

	@Test
	public void serverProcessesHaveAccess() throws Exception
	{
		when(Boolean.valueOf(access.isServerProcess("process"))).thenReturn(Boolean.TRUE);
		server.initialize(access);

		assertNotNull(server.createMailMessageFromBinary("process", "Subject: x\r\n\r\nbody".getBytes(StandardCharsets.US_ASCII)));
	}

	// --- error messages ---

	private static String fullMessage(Exception e) throws Exception
	{
		Method m = MailServer.class.getDeclaredMethod("getFullMessage", Exception.class);
		m.setAccessible(true);
		return (String)m.invoke(null, e);
	}

	@Test
	public void theWholeExceptionChainIsInTheMessage() throws Exception
	{
		MessagingException smtp = new MessagingException("550 mailbox unavailable");
		SendFailedException failed = new SendFailedException("Invalid Addresses", smtp, new InternetAddress[0],
			new InternetAddress[0], new InternetAddress[] { new InternetAddress("bad@example.com") });
		MessagingException top = new MessagingException("Sending failed", failed);

		assertEquals("Sending failed; Invalid Addresses; invalid addresses: bad@example.com; 550 mailbox unavailable", fullMessage(top));
	}

	@Test
	public void causesAndDuplicatesAreHandled() throws Exception
	{
		IOException io = new IOException("connection reset");
		Exception wrapped = new Exception("connection reset", io);

		assertEquals("connection reset", fullMessage(wrapped));
		assertEquals("java.lang.Exception", fullMessage(new Exception()));
	}

	@Test
	public void aCyclicChainStops() throws Exception
	{
		MessagingException a = new MessagingException("a");
		MessagingException b = new MessagingException("b", a);
		a.setNextException(b);

		assertEquals("a; b", fullMessage(a));
	}

	// --- data classes ---

	@Test
	public void attachmentMimeTypeIsGuessed()
	{
		byte[] png = { (byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

		assertEquals("image/png", new Attachment("x.bin", png).getMimeType());
		assertEquals("text/plain", new Attachment("x", png, "text/plain").getMimeType());
		assertNull(new Attachment().getMimeType());
		assertNull(new Attachment("empty.txt", new byte[0]).getMimeType());
	}

	@Test
	public void attachmentProperties()
	{
		byte[] data = { 1, 2 };
		Attachment attachment = new Attachment("a.bin", data, "application/octet-stream", true);

		assertSame(data, attachment.js_getData());
		assertEquals("a.bin", attachment.js_getName());
		assertEquals("application/octet-stream", attachment.js_getMimeType());
		assertTrue(attachment.js_isEmbedded());
		attachment.setEmbedded(false);
		assertFalse(new Attachment("x", data, false).isEmbedded());
		assertEquals("name: a.bin\nmime type: application/octet-stream\ndatalength: 2\nembedded: false\n", attachment.toString());
		assertTrue(new Attachment().toString().contains("datalength: 0"));
	}

	@Test
	public void attachmentDataSource() throws Exception
	{
		AttachmentDataSource known = new AttachmentDataSource(new Attachment("a.txt", "x".getBytes(StandardCharsets.UTF_8), "text/csv"));
		AttachmentDataSource guessed = new AttachmentDataSource(new Attachment("page.html", null));

		assertEquals("text/csv", known.getContentType());
		assertEquals("a.txt", known.getName());
		assertEquals('x', known.getInputStream().read());
		assertEquals("text/html", guessed.getContentType());
		assertThrows(IOException.class, known::getOutputStream);
	}

	@Test
	public void mailMessageGetters()
	{
		MailMessage message = new MailMessage();
		message.fromAddresses = "f";
		message.recipientAddresses = "r";
		message.ccAddresses = "c";
		message.replyAddresses = "rp";
		message.subject = "s";
		message.headers = "h";
		message.plainMsg = "p";
		message.htmlMsg = "<p>";
		message.receivedDate = new java.util.Date(1);
		message.sentDate = new java.util.Date(2);
		Attachment attachment = new Attachment("a", new byte[] { 1 });
		message.addAttachment(attachment);

		assertEquals("f", message.js_getFromAddresses());
		assertEquals("r", message.js_getRecipientAddresses());
		assertEquals("c", message.js_getCCAddresses());
		assertEquals("rp", message.js_getReplyAddresses());
		assertEquals("s", message.js_getSubject());
		assertEquals("h", message.js_getHeaders());
		assertEquals("p", message.js_getPlainMsg());
		assertEquals("<p>", message.js_getHtmlMsg());
		assertEquals(1, message.js_getReceivedDate().getTime());
		assertEquals(2, message.js_getSentDate().getTime());
		assertArrayEquals(new Attachment[] { attachment }, message.js_getAttachments());
		assertTrue(message.toString().startsWith("from: f\nto: r\ncc: c\n"), message.toString());
	}

	@Test
	public void messageBuilder()
	{
		Attachment[] attachments = { new Attachment() };

		MessageBuilder builder = new MessageBuilder().withTo("t").withSubject("s").withRawMsgText("m").withCC("c").withBCC("b")
			.withAttachments(attachments);

		assertEquals("t", builder.getTo());
		assertEquals("s", builder.getSubject());
		assertEquals("m", builder.getRawMsgText());
		assertEquals("c", builder.getCC());
		assertEquals("b", builder.getBCC());
		assertSame(attachments, builder.getAttachments());
	}
}
