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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.extensions.plugins.mail.client.Attachment;
import com.servoy.extensions.plugins.mail.client.MailMessage;
import com.servoy.j2db.plugins.IServerAccess;

import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/**
 * Building the MIME message that MailServer sends (plain, html, alternative, embedded images, attachments, override
 * address) and parsing it back with createMailMessageFromBinary. Everything in memory, nothing is sent.
 */
public class MailMessageBuildingTest
{
	private static final byte[] PNG = { (byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	private Properties settings;
	private MailServer server;

	@BeforeEach
	public void setUp() throws Exception
	{
		settings = new Properties();
		IServerAccess access = mock(IServerAccess.class);
		when(access.getSettings()).thenReturn(settings);
		when(Boolean.valueOf(access.isAuthenticated("client"))).thenReturn(Boolean.TRUE);
		server = new MailServer();
		server.initialize(access);
		// added after initialize, so they are not copied to the system properties
		settings.setProperty("mail.from", "default@example.com");
		settings.setProperty("mail.host", "example.com");
		settings.setProperty("mail.user", "test");
	}

	private MimeMessage build(String to, String from, String subject, String text, String cc, String bcc, Attachment[] attachments, String... override)
		throws Exception
	{
		Method m = MailServer.class.getDeclaredMethod("createMessage", String.class, String.class, String.class, String.class, String.class,
			String.class, Attachment[].class, String[].class);
		m.setAccessible(true);
		try
		{
			MimeMessage message = (MimeMessage)m.invoke(server, to, from, subject, text, cc, bcc, attachments, override.length == 0 ? null : override);
			message.saveChanges();
			return message;
		}
		catch (InvocationTargetException e)
		{
			throw (Exception)e.getCause();
		}
	}

	private static byte[] bytes(MimeMessage message) throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		message.writeTo(out);
		return out.toByteArray();
	}

	private MailMessage roundTrip(MimeMessage message) throws Exception
	{
		return server.createMailMessageFromBinary("client", bytes(message));
	}

	// --- addresses and subject ---

	@Test
	public void recipientsAreSplitOnSemicolonsAndCommas() throws Exception
	{
		MimeMessage message = build("a@example.com;b@example.com", "me@example.com", "Hi", "text", "c@example.com, d@example.com", "e@example.com",
			null);

		assertEquals(2, message.getRecipients(Message.RecipientType.TO).length);
		assertEquals("d@example.com", ((InternetAddress)message.getRecipients(Message.RecipientType.CC)[1]).getAddress());
		assertEquals(1, message.getRecipients(Message.RecipientType.BCC).length);
		assertEquals("me@example.com", ((InternetAddress)message.getFrom()[0]).getAddress());
		assertEquals("Hi", message.getSubject());
		assertNotNull(message.getSentDate());
	}

	@Test
	public void extraFromAddressesAreReplyTo() throws Exception
	{
		MimeMessage message = build("a@example.com", "me@example.com,r1@example.com,r2@example.com", "s", "t", null, null, null);

		assertEquals(2, message.getReplyTo().length);
		assertEquals("r2@example.com", ((InternetAddress)message.getReplyTo()[1]).getAddress());
	}

	@Test
	public void anInvalidReplyToAddressIsSkipped() throws Exception
	{
		MimeMessage message = build("a@example.com", "me@example.com,not valid@@", "s", "t", null, null, null);

		// only the from address is left as reply to (the default when no reply to is set)
		assertEquals("me@example.com", ((InternetAddress)message.getReplyTo()[0]).getAddress());
	}

	@Test
	public void withoutFromTheDefaultAddressIsUsed() throws Exception
	{
		MimeMessage message = build("a@example.com", null, null, "t", null, null, null);

		assertEquals("default@example.com", ((InternetAddress)message.getFrom()[0]).getAddress());
		assertEquals("<no subject>", message.getSubject());
	}

	@Test
	public void anInvalidRecipientFails()
	{
		assertThrows(Exception.class, () -> build("not valid@@", "me@example.com", "s", "t", null, null, null));
	}

	@Test
	public void theDevelopmentOverrideAddressReplacesAllRecipients() throws Exception
	{
		settings.setProperty("mail.development.override.address", "dev@example.com");

		MimeMessage message = build("a@example.com", "me@example.com", "Report", "t", "c@example.com", null, null);

		assertEquals(1, message.getAllRecipients().length);
		assertEquals("dev@example.com", ((InternetAddress)message.getAllRecipients()[0]).getAddress());
		assertEquals("Report (Override: TO=a@example.com, CC: c@example.com, BCC: )", message.getSubject());
	}

	@Test
	public void propertiesCanBeOverriddenPerMail() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "t", null, null, null, "mail.from=other@example.com", "invalid", null, "=x");

		assertEquals("other@example.com", ((InternetAddress)message.getFrom()[0]).getAddress());
		// the settings themselves are not changed
		assertEquals("default@example.com", settings.getProperty("mail.from"));
	}

	// --- bodies ---

	@Test
	public void aPlainTextMessage() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "  hello\nworld  ", null, null, null);

		assertTrue(message.getContentType().startsWith("text/plain; charset=UTF-8"), message.getContentType());
		assertEquals("hello\nworld", message.getContent());
	}

	@Test
	public void theCharsetAndTransferEncodingCanBeSet() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "h\u00e9", null, null, null, "mail.mime.charset=ISO-8859-1",
			"mail.mime.encoding=base64");

		assertTrue(message.getContentType().contains("ISO-8859-1"), message.getContentType());
		assertEquals("base64", message.getHeader("Content-Transfer-Encoding")[0]);
		assertEquals("h\u00e9", roundTrip(message).getPlainMsg().trim());
	}

	@Test
	public void anHtmlMessage() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "<html><body>Hi</body></html>", null, null, null);

		assertTrue(message.getContentType().startsWith("text/html"), message.getContentType());
		MailMessage parsed = roundTrip(message);
		assertEquals("<html><body>Hi</body></html>\n", parsed.getHtmlMsg());
		assertNull(parsed.getPlainMsg());
	}

	@Test
	public void plainTextBeforeHtmlBecomesAnAlternative() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "Plain version<html><b>Html</b></html>", null, null, null, "mail.mime.encoding=8bit");

		MimeMultipart multipart = (MimeMultipart)message.getContent();
		assertTrue(multipart.getContentType().startsWith("multipart/alternative"), multipart.getContentType());
		assertEquals(2, multipart.getCount());
		MailMessage parsed = roundTrip(message);
		assertEquals("Plain version\n", parsed.getPlainMsg());
		assertEquals("<html><b>Html</b></html>\n", parsed.getHtmlMsg());
	}

	// --- embedded images ---

	@Test
	public void anImageReferencedInTheHtmlIsEmbedded() throws Exception
	{
		Attachment logo = new Attachment("logo.png", PNG);

		MimeMessage message = build("a@example.com", null, "s", "<html><img src=\"%%logo.png%%\"> %%unknown%%</html>", null, null,
			new Attachment[] { logo }, "mail.mime.encoding=quoted-printable");

		assertTrue(logo.isEmbedded());
		MimeMultipart related = (MimeMultipart)message.getContent();
		assertTrue(related.getContentType().startsWith("multipart/related"), related.getContentType());
		String html = (String)related.getBodyPart(0).getContent();
		assertTrue(html.contains("src=\"cid:servoy-"), html);
		assertTrue(html.contains("%%unknown%%"), html);
		String contentId = ((MimeBodyPart)related.getBodyPart(1)).getContentID();
		assertTrue(html.contains(contentId.substring(1, contentId.length() - 1)), contentId);

		MailMessage parsed = roundTrip(message);
		assertEquals(1, parsed.getAttachments().length);
		assertTrue(parsed.getAttachments()[0].isEmbedded());
		assertArrayEquals(PNG, parsed.getAttachments()[0].getData());
	}

	@Test
	public void plainTextWithAnEmbeddedImage() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "Plain<html><img src=\"%%logo.png%%\"></html>", null, null,
			new Attachment[] { new Attachment("logo.png", PNG) });

		MimeMultipart alternative = (MimeMultipart)message.getContent();
		assertTrue(alternative.getContentType().startsWith("multipart/alternative"));
		assertInstanceOf(Multipart.class, alternative.getBodyPart(1).getContent());
		MailMessage parsed = roundTrip(message);
		assertEquals("Plain\n", parsed.getPlainMsg());
		assertNotNull(parsed.getHtmlMsg());
		assertEquals(1, parsed.getAttachments().length);
	}

	@Test
	public void htmlWithoutImageTagsHasNoRelatedPart() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "<html>no images</html>", null, null,
			new Attachment[] { new Attachment("a.txt", "x".getBytes(StandardCharsets.UTF_8)) });

		MimeMultipart mixed = (MimeMultipart)message.getContent();
		assertTrue(mixed.getContentType().startsWith("multipart/mixed"), mixed.getContentType());
		assertTrue(mixed.getBodyPart(0).getContentType().startsWith("text/html"), mixed.getBodyPart(0).getContentType());
	}

	@Test
	public void aNullAttachmentWithHtmlFails()
	{
		// current behaviour (bug): the embedded image resolver reads the name of every attachment, also a null one,
		// while the attachment loop skips null attachments
		assertThrows(NullPointerException.class,
			() -> build("a@example.com", null, "s", "<html>x</html>", null, null, new Attachment[] { null }));
	}

	// --- attachments ---

	@Test
	public void attachmentsAreAddedAsAMixedMultipart() throws Exception
	{
		Attachment text = new Attachment("r\u00e9sum\u00e9.txt", "content".getBytes(StandardCharsets.UTF_8), "text/plain");
		Attachment unknown = new Attachment("data.csv", "a;b".getBytes(StandardCharsets.UTF_8));

		MimeMessage message = build("a@example.com", null, "s", "body", null, null, new Attachment[] { text, null, unknown },
			"mail.mime.encoding=7bit");

		MimeMultipart mixed = (MimeMultipart)message.getContent();
		assertEquals(3, mixed.getCount());
		assertEquals(Part.ATTACHMENT, mixed.getBodyPart(1).getDisposition());
		MailMessage parsed = roundTrip(message);
		assertEquals("body\n", parsed.getPlainMsg());
		Attachment[] attachments = parsed.getAttachments();
		assertEquals(2, attachments.length);
		assertEquals("r\u00e9sum\u00e9.txt", attachments[0].getName());
		assertEquals("content", new String(attachments[0].getData(), StandardCharsets.UTF_8));
		assertFalse(attachments[0].isEmbedded());
		assertEquals("data.csv", attachments[1].getName());
	}

	@Test
	public void attachmentsWithAnAlternativeBody() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "Plain<html>Html</html>", null, null,
			new Attachment[] { new Attachment("a.txt", "x".getBytes(StandardCharsets.UTF_8)) });

		MimeMultipart mixed = (MimeMultipart)message.getContent();
		assertInstanceOf(Multipart.class, mixed.getBodyPart(0).getContent());
		MailMessage parsed = roundTrip(message);
		assertEquals("Plain\n", parsed.getPlainMsg());
		assertEquals("<html>Html</html>\n", parsed.getHtmlMsg());
		assertEquals(1, parsed.getAttachments().length);
	}

	@Test
	public void theXOAuth2MechanismIsAccepted() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "t", null, null, null, "mail.smtp.auth.mechanisms=xoauth2");

		assertEquals("t", message.getContent());
	}

	// --- parsing ---

	@Test
	public void parsedHeadersAndAddresses() throws Exception
	{
		MimeMessage message = build("a@example.com;b@example.com", "me@example.com,reply@example.com", "Sub", "t", "c@example.com", null, null);

		MailMessage parsed = roundTrip(message);

		assertEquals("me@example.com", parsed.getFromAddresses());
		assertEquals("a@example.com;b@example.com", parsed.getRecipientAddresses());
		assertEquals("c@example.com", parsed.getCCAddresses());
		assertEquals("reply@example.com", parsed.getReplyAddresses());
		assertEquals("Sub", parsed.getSubject());
		assertNotNull(parsed.getSentDate());
		assertNull(parsed.getReceivedDate());
		assertTrue(parsed.getHeaders().contains("Subject=Sub"), parsed.getHeaders());
		assertTrue(parsed.toString().contains("subject: Sub"), parsed.toString());
	}

	@Test
	public void parsingNeedsAnAuthenticatedClient() throws Exception
	{
		assertNull(server.createMailMessageFromBinary("stranger", bytes(build("a@example.com", null, "s", "t", null, null, null))));

		settings.setProperty("mail.server.allowUnauthenticatedRMIAccess", "true");
		assertNotNull(server.createMailMessageFromBinary("stranger", bytes(build("a@example.com", null, "s", "t", null, null, null))));
	}

	@Test
	public void unparsableDataGivesAnEmptyMessage() throws Exception
	{
		MailMessage parsed = server.createMailMessageFromBinary("client", "not a mime message".getBytes(StandardCharsets.US_ASCII));

		assertNull(parsed.getSubject());
		assertNull(parsed.getFromAddresses());
	}

	@Test
	public void receiveModes() throws Exception
	{
		MimeMessage message = build("a@example.com", null, "s", "body", null, null,
			new Attachment[] { new Attachment("a.txt", "x".getBytes(StandardCharsets.UTF_8)) });
		MimeMessage parsed = new MimeMessage(null, new java.io.ByteArrayInputStream(bytes(message)));

		MailMessage headersOnly = MailServerUtils.createMailMessage(parsed, IMailService.HEADERS_ONLY);
		MailMessage noAttachments = MailServerUtils.createMailMessage(parsed, IMailService.NO_ATTACHMENTS);
		MailMessage full = MailServerUtils.createMailMessage(parsed, IMailService.FULL);

		assertEquals("s", headersOnly.getSubject());
		assertNull(headersOnly.getPlainMsg());
		assertEquals("body\n", noAttachments.getPlainMsg());
		assertEquals(0, noAttachments.getAttachments().length);
		assertEquals(1, full.getAttachments().length);
	}

	@Test
	public void aMissingMessageIsEmpty() throws Exception
	{
		MailMessage empty = MailServerUtils.createMailMessage(null, IMailService.FULL);

		assertNull(empty.getSubject());
		assertEquals(0, empty.getAttachments().length);
	}

	@Test
	public void quotedAndUnknownCharsets() throws Exception
	{
		MimeMessage quoted = new MimeMessage((jakarta.mail.Session)null);
		quoted.setContent("h\u00e9", "text/plain; charset=\"UTF-8\"");
		quoted.saveChanges();
		MimeMessage unknown = new MimeMessage((jakarta.mail.Session)null);
		unknown.setText("abc", "US-ASCII");
		unknown.setHeader("Content-Type", "text/plain; charset='x-unknown'");

		assertEquals("h\u00e9\n", MailServerUtils.createMailMessage(new MimeMessage(null, new java.io.ByteArrayInputStream(bytes(quoted))),
			IMailService.FULL).getPlainMsg());
		assertEquals("abc\n", MailServerUtils.createMailMessage(unknown, IMailService.FULL).getPlainMsg());
	}

	@Test
	public void aTextPartWithAFileNameIsAnAttachment() throws Exception
	{
		MimeMessage message = new MimeMessage((jakarta.mail.Session)null);
		message.setText("file text", "UTF-8");
		message.setFileName("notes.txt");

		MailMessage parsed = MailServerUtils.createMailMessage(message, IMailService.FULL);

		assertNull(parsed.getPlainMsg());
		assertEquals("notes.txt", parsed.getAttachments()[0].getName());
	}

	@Test
	public void addressStrings()
	{
		assertNull(MailServerUtils.createAddressString(null));
		assertEquals("a@example.com;", MailServerUtils.createAddressString(new jakarta.mail.Address[] { addr("a@example.com"), null }));
		assertEquals("a@example.com;b@example.com",
			MailServerUtils.createAddressString(new jakarta.mail.Address[] { addr("a@example.com"), addr("b@example.com") }));
	}

	private static InternetAddress addr(String address)
	{
		try
		{
			return new InternetAddress(address);
		}
		catch (jakarta.mail.internet.AddressException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
