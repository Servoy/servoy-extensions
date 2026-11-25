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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.extensions.plugins.mail.client.MailMessage;
import com.servoy.j2db.plugins.IServerAccess;

import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Sending and receiving through MailServer with the package-private send/getStore seams: sent messages are
 * recorded, the POP3 store and its INBOX are mocks holding in-memory messages. No mail server.
 */
public class MailServerSendReceiveTest
{
	private final List<MimeMessage> sent = new ArrayList<>();
	private Properties settings;
	private Store store;
	private Folder inbox;
	private MessagingException sendError;
	private MailServer server;

	@BeforeEach
	public void setUp() throws Exception
	{
		settings = new Properties();
		IServerAccess access = mock(IServerAccess.class);
		when(access.getSettings()).thenReturn(settings);
		when(Boolean.valueOf(access.isAuthenticated("client"))).thenReturn(Boolean.TRUE);
		store = mock(Store.class);
		inbox = mock(Folder.class);
		Folder root = mock(Folder.class);
		when(store.getDefaultFolder()).thenReturn(root);
		when(root.getFolder("INBOX")).thenReturn(inbox);
		server = new MailServer()
		{
			@Override
			void send(MimeMessage message) throws MessagingException
			{
				if (sendError != null) throw sendError;
				sent.add(message);
			}

			@Override
			Store getStore(Session session)
			{
				return store;
			}
		};
		server.initialize(access);
		settings.setProperty("mail.from", "default@example.com");
		settings.setProperty("mail.pop3.host", "pop.example.com");
	}

	private static MimeMessage mail(String subject, Date sent) throws Exception
	{
		MimeMessage message = new MimeMessage((Session)null);
		message.setFrom(new InternetAddress("sender@example.com"));
		message.setSubject(subject);
		message.setText("body of " + subject, "UTF-8");
		message.setSentDate(sent);
		return message;
	}

	// --- sending ---

	@Test
	public void sendMailSendsTheBuiltMessage() throws Exception
	{
		server.sendMail("client", "a@example.com", "me@example.com", "Hello", "text", null, null, null, null);

		assertEquals(1, sent.size());
		assertEquals("Hello", sent.get(0).getSubject());
		assertNull(sent.get(0).getHeader("Precedence"));
	}

	@Test
	public void bulkMailIsMarkedAsBulk() throws Exception
	{
		server.sendBulkMail("client", "a@example.com", null, "News", "text", null, null, null, null);

		assertEquals("bulk", sent.get(0).getHeader("Precedence")[0]);
	}

	@Test
	public void theContextClassLoaderIsRestored() throws Exception
	{
		ClassLoader before = Thread.currentThread().getContextClassLoader();

		server.sendMail("client", "a@example.com", null, "s", "t", null, null, null, null);
		sendError = new MessagingException("down");
		assertThrows(Exception.class, () -> server.sendBulkMail("client", "a@example.com", null, "s", "t", null, null, null, null));

		assertSame(before, Thread.currentThread().getContextClassLoader());
	}

	@Test
	public void aSendFailureCarriesTheSmtpReply() throws Exception
	{
		sendError = new SendFailedException("Invalid Addresses", new MessagingException("550 no such user"));

		Exception e = assertThrows(Exception.class, () -> server.sendMail("client", "a@example.com", null, "s", "t", null, null, null, null));
		Exception bulk = assertThrows(Exception.class,
			() -> server.sendBulkMail("client", "a@example.com", null, "s", "t", null, null, null, null));

		assertEquals("Invalid Addresses; 550 no such user", e.getMessage());
		assertEquals(e.getMessage(), bulk.getMessage());
		// a plain Exception, the jakarta.mail one may not be serializable to the client
		assertEquals(Exception.class, e.getClass());
	}

	@Test
	public void anInvalidAddressFailsBeforeSending()
	{
		Exception e = assertThrows(Exception.class, () -> server.sendMail("client", "not valid@@", null, "s", "t", null, null, null, null));

		assertTrue(sent.isEmpty());
		assertFalse(e.getMessage().isEmpty());
	}

	@Test
	public void theSmtpCredentialsComeFromThePropertiesOfTheMail() throws Exception
	{
		settings.setProperty("mail.smtp.username", "smtp-user");
		settings.setProperty("mail.smtp.password", "smtp-pw");

		server.sendMail("client", "a@example.com", null, "s", "t", null, null, null, new String[] { "mail.smtp.password=override-pw" });

		PasswordAuthentication auth = sent.get(0).getSession().requestPasswordAuthentication(null, 25, "smtp", null, null);
		assertEquals("smtp-user", auth.getUserName());
		assertEquals("override-pw", auth.getPassword());
	}

	// --- receiving ---

	@Test
	public void receiveReadsTheInboxAndDeletesTheMessages() throws Exception
	{
		MimeMessage first = mail("one", new Date(1000));
		MimeMessage second = mail("two", new Date(2000));
		when(inbox.getMessages()).thenReturn(new Message[] { first, second });

		MailMessage[] received = server.receiveMail("client", "user", "pw", false, IMailService.FULL, null, null);

		assertEquals(2, received.length);
		assertEquals("one", received[0].getSubject());
		assertEquals("body of two\n", received[1].getPlainMsg());
		verify(store).connect("pop.example.com", "user", "pw");
		verify(inbox).open(Folder.READ_WRITE);
		assertTrue(first.isSet(Flags.Flag.DELETED));
		assertTrue(second.isSet(Flags.Flag.DELETED));
		verify(inbox).close(true);
		verify(store).close();
		verify(inbox, never()).fetch(any(), any(FetchProfile.class));
	}

	@Test
	public void messagesCanBeLeftOnTheServer() throws Exception
	{
		MimeMessage message = mail("keep", new Date(1000));
		when(inbox.getMessages()).thenReturn(new Message[] { message });

		server.receiveMail("client", "user", "pw", true, IMailService.FULL, null, null);

		verify(inbox).open(Folder.READ_ONLY);
		assertFalse(message.isSet(Flags.Flag.DELETED));
		verify(inbox).close(false);
	}

	@Test
	public void headersOnlyFetchesTheEnvelope() throws Exception
	{
		when(inbox.getMessages()).thenReturn(new Message[] { mail("h", new Date(1000)) });

		MailMessage[] received = server.receiveMail("client", "user", "pw", true, IMailService.HEADERS_ONLY, null, null);

		verify(inbox).fetch(any(), any(FetchProfile.class));
		assertEquals("h", received[0].getSubject());
		assertNull(received[0].getPlainMsg());
	}

	@Test
	public void onlyMessagesWithTheGivenSentDate() throws Exception
	{
		// sent dates in a MIME header have second precision
		Date wanted = new Date(5000);
		MimeMessage match = mail("match", wanted);
		MimeMessage other = mail("other", new Date(9000));
		when(inbox.getMessages()).thenReturn(new Message[] { other, match });

		MailMessage[] received = server.receiveMail("client", "user", "pw", false, IMailService.FULL, wanted, null);

		verify(inbox).fetch(any(), any(FetchProfile.class));
		// current behaviour (bug): the result keeps one slot per inbox message, skipped messages are null entries
		assertEquals(2, received.length);
		assertNull(received[0]);
		assertEquals("match", received[1].getSubject());
		// a skipped message is not deleted
		assertFalse(other.isSet(Flags.Flag.DELETED));
		assertTrue(match.isSet(Flags.Flag.DELETED));
	}

	@Test
	public void theHostCanBeOverriddenPerCall() throws Exception
	{
		when(inbox.getMessages()).thenReturn(new Message[0]);

		assertEquals(0, server.receiveMail("client", "user", "pw", true, IMailService.FULL, null, new String[] { "mail.pop3.host=other.example.com" }).length);

		verify(store).connect("other.example.com", "user", "pw");
	}

	@Test
	public void aFailingConnectReturnsNullAndClosesTheStore() throws Exception
	{
		doThrow(new MessagingException("auth failed")).when(store).connect("pop.example.com", "user", "wrong");

		assertNull(server.receiveMail("client", "user", "wrong", false, IMailService.FULL, null, null));

		verify(store).close();
	}

	@Test
	public void withoutFoldersNothingIsReceived() throws Exception
	{
		when(store.getDefaultFolder()).thenReturn(null);
		assertNull(server.receiveMail("client", "user", "pw", true, IMailService.FULL, null, null));

		Folder root = mock(Folder.class);
		when(store.getDefaultFolder()).thenReturn(root);
		assertNull(server.receiveMail("client", "user", "pw", true, IMailService.FULL, null, null));
	}

	@Test
	public void closeErrorsAreOnlyLogged() throws Exception
	{
		when(inbox.getMessages()).thenReturn(new Message[] { mail("x", new Date(1000)) });
		doThrow(new MessagingException("folder")).when(inbox).close(true);
		doThrow(new MessagingException("store")).when(store).close();

		assertEquals(1, server.receiveMail("client", "user", "pw", false, IMailService.FULL, null, null).length);
	}
}
