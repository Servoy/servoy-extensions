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

import java.io.ByteArrayInputStream;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.activation.DataHandler;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import com.servoy.base.util.ITagResolver;
import com.servoy.extensions.plugins.mail.client.Attachment;
import com.servoy.extensions.plugins.mail.client.MailMessage;
import com.servoy.j2db.Messages;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Text;
import com.servoy.j2db.util.UUID;

/**
 * Server mail component
 *
 * @author jblok
 */
@SuppressWarnings("nls")
public class MailServer implements IMailService, IServerPlugin
{
	private Properties settings;
	private IServerAccess application;

	public MailServer()//must have default constructor
	{
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, Messages.getString("servoy.plugin.mailserver.displayname"));
		return props;
	}

	public void load()
	{
	}

	public void initialize(IServerAccess app)
	{
		application = app;
		settings = app.getSettings();

		try
		{
			app.registerRemoteService(IMailService.class.getName(), this);
		}
		catch (Exception e)
		{
			Debug.error(e);
		}

		//copy mail settings to system props, since more libs may need those
		Iterator it = settings.keySet().iterator();
		while (it.hasNext())
		{
			String key = (String)it.next();
			if (key.startsWith("mail."))
			{
				String value = settings.getProperty(key);
				if (value != null && !value.trim().equals(""))
				{
					System.setProperty(key, value);
				}
			}
		}
	}

	public void unload()
	{
		settings = null;
	}

	public Map<String, String> getRequiredPropertyNames()
	{
		Map<String, String> req = new LinkedHashMap<String, String>();
		req.put("mail.server.allowUnauthenticatedRMIAccess", "Allow mailserver access for unauthenticated smart (rmi) client (true/false), defaults to false");
		req.put("mail.pop3.host", "The name of POP3 server to recieve mails from");
		req.put("mail.pop3.apop.enable", "Whether or not to use APOP for authentication (true/false), defaults to false.");
		req.put("mail.smtp.host", "The name of SMTP server to deliver the mails to");
		req.put("mail.smtp.port", "The port of SMTP server to deliver the mails to");
		req.put("mail.from", "Default 'from' address if none is specified");
		req.put("mail.smtp.auth", "Use authentication (true/false), defaults to false.");
		req.put("mail.smtp.username", "Specify username if using authentication");
		req.put("mail.smtp.password", "Specify password if using authentication");
		req.put("mail.smtp.connectiontimeout", "Socket connection timeout value in milliseconds. Default is infinite timeout.");
		req.put("mail.smtp.timeout", "Socket I/O timeout value in milliseconds. Default is infinite timeout.");
		req.put("mail.smtp.ssl.enable", "Use SSL (true/false), defaults to false .");
		req.put("mail.smtp.starttls.enable", "Use START/TLS (true/false), defaults to false .");
		req.put("mail.mime.charset",
			"Specify the name of the charset to use for mail encoding (leave emtpy for system default), see http://java.sun.com/j2se/1.4.2/docs/api/java/nio/charset/Charset.html forinfo which charset names are usable");
		req.put("mail.development.override.address",
			"Specify an email address to which all email will be send instead of the specified To, Cc and Bcc addresses.\nThe specified to, Cc and Bcc addresses will be added to the Subject.");
		return req;
	}

	public void sendBulkMail(String clientId, String to, String from, String subject, String rawMsgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties) throws RemoteException, Exception
	{
		if (!checkAccess(clientId)) return;

		ClassLoader saveCl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(javax.mail.Session.class.getClassLoader());

			MimeMessage message = createMessage(to, from, subject, rawMsgText, cc, bcc, attachments, overrideProperties);
			message.setHeader("Precedence", "bulk");
			Transport.send(message);
		}
		catch (Exception ex)
		{
			Debug.error("sendBulkMail " + ex.getMessage(), ex);
			throw new Exception(ex.getMessage()); //not all exceptions are serializable
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(saveCl);
		}
	}

	public void sendMail(String clientId, String to, String from, String subject, String rawMsgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties) throws RemoteException, Exception
	{
		if (!checkAccess(clientId)) return;

		ClassLoader saveCl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(javax.mail.Session.class.getClassLoader());

			MimeMessage message = createMessage(to, from, subject, rawMsgText, cc, bcc, attachments, overrideProperties);
			Transport.send(message);
		}
		catch (Exception ex)
		{
			Debug.error("sendMail " + ex.getMessage(), ex);
			throw new Exception(ex.getMessage()); //not all exceptions are serializable
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(saveCl);
		}
	}

	private MimeMessage createMessage(String to, String from, String subject, String rawMsgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties) throws Exception
	{
		// create a new Session object
		Properties properties = overrideProperties(settings, overrideProperties);
		Session session = Session.getInstance(properties, new SMTPAuthenticator(properties));

		String encoding = properties.getProperty("mail.mime.encoding");
		String charset = properties.getProperty("mail.mime.charset");
		if (charset == null) charset = "UTF-8";

		String plainTextContentType = "text/plain; charset=" + charset;
		String htmlContentType = "text/html; charset=" + charset;

		// create a new MimeMessage object (using the Session created above)
		MimeMessage message = new MimeMessage(session);
		String reply = null;
		if (from == null)
		{
			message.setFrom();//defaults to "mail.from" mail property
		}
		else
		{
			String[] from_array = from.split(",");
			message.setFrom(new InternetAddress(from_array[0]));
			if (from_array.length > 1) reply = from_array[1];
		}
		if (reply != null) message.setReplyTo(new InternetAddress[] { new InternetAddress(reply) });
		message.setSentDate(new Date());
		String overrideAddress = properties.getProperty("mail.development.override.address");
		String overrideFeedback = null;
		if (overrideAddress != null && !overrideAddress.trim().isEmpty())
		{
			addRecipients(message, overrideAddress, Message.RecipientType.TO);
			overrideFeedback = " (Override: TO=" + to + ", CC: " + (cc == null ? "" : cc) + ", BCC: " + (bcc == null ? "" : bcc) + ")";
		}
		else
		{
			addRecipients(message, to, Message.RecipientType.TO);
			addRecipients(message, cc, Message.RecipientType.CC);
			addRecipients(message, bcc, Message.RecipientType.BCC);
		}

		String sub = (subject == null ? Messages.getString("servoy.plugin.mailserver.defaultsubject") : subject);
		if (overrideFeedback != null) sub = sub + overrideFeedback;
		message.setSubject(sub, charset);

		String msgText = (rawMsgText == null) ? "" : rawMsgText.trim();

		int htmlIndex = msgText.toLowerCase().indexOf("<html");
		boolean hasHTML = (htmlIndex != -1);
		boolean hasPlain = !hasHTML || htmlIndex > 0;

		String plain = hasHTML ? msgText.substring(0, htmlIndex) : msgText;
		String html = hasHTML ? msgText.substring(htmlIndex) : null;

		// Make html multipart if it contains embedded images.
		MimeMultipart htmlMultipart = null;
		if (hasHTML && attachments != null && attachments.length > 0)
		{
			// Add embedded attachments.
			EmbeddedImageTagResolver resolver = new EmbeddedImageTagResolver(attachments);
			html = Text.processTags(html, resolver);
			List<MimeBodyPart> embeddedImages = resolver.getMimeBodyParts();
			if (embeddedImages.size() > 0)
			{
				htmlMultipart = new MimeMultipart("related");
				MimeBodyPart htmlBodyPart = new MimeBodyPart();
				htmlBodyPart.setHeader("Content-Type", htmlContentType);
				if (encoding != null)
				{
					htmlBodyPart.setHeader("Content-Transfer-Encoding", encoding);
				}

				htmlBodyPart.setContent(html, htmlContentType);
				htmlMultipart.addBodyPart(htmlBodyPart);

				Iterator<MimeBodyPart> iterator = embeddedImages.iterator();
				while (iterator.hasNext())
				{
					htmlMultipart.addBodyPart(iterator.next());
				}
			}
		}

		// Make message multipart if needed (only html with embeded images or combined plain + html messages).
		MimeMultipart messageMultipart = null;
		if (htmlMultipart != null && !hasPlain)
		{
			messageMultipart = htmlMultipart;
		}
		else if (hasPlain && hasHTML)
		{
			messageMultipart = new MimeMultipart("alternative");

			MimeBodyPart textBodyPart = new MimeBodyPart();
			if (encoding != null)
			{
				textBodyPart.setHeader("Content-Transfer-Encoding", encoding);
			}
			textBodyPart.setHeader("Content-Type", plainTextContentType);
			textBodyPart.setContent(plain, plainTextContentType);
			messageMultipart.addBodyPart(textBodyPart);

			MimeBodyPart htmlBodyPart = new MimeBodyPart();
			if (htmlMultipart != null)
			{
				htmlBodyPart.setContent(htmlMultipart);
			}
			else
			{
				htmlBodyPart.setContent(html, htmlContentType);
				htmlBodyPart.setHeader("Content-Type", htmlContentType);
				if (encoding != null)
				{
					htmlBodyPart.setHeader("Content-Transfer-Encoding", encoding);
				}
			}
			messageMultipart.addBodyPart(htmlBodyPart);
		}

		// Add attachments that were not embedded.
		MimeMultipart mixedMultipart = null;

		for (int i = 0; attachments != null && i < attachments.length; i++)
		{
			Attachment attachment = attachments[i];
			if (attachment == null || attachment.isEmbedded()) continue;

			if (mixedMultipart == null)
			{
				mixedMultipart = new MimeMultipart("mixed");
				MimeBodyPart messageBodyPart = new MimeBodyPart();
				if (messageMultipart != null)
				{
					messageBodyPart.setContent(messageMultipart);
				}
				else
				{
					messageBodyPart.setContent(msgText, hasHTML ? htmlContentType : plainTextContentType);
					messageBodyPart.setHeader("Content-Type", hasHTML ? htmlContentType : plainTextContentType);
					if (encoding != null)
					{
						messageBodyPart.setHeader("Content-Transfer-Encoding", encoding);
					}
				}
				mixedMultipart.addBodyPart(messageBodyPart);
			}

			MimeBodyPart attachmentBodyPart = new MimeBodyPart();
			attachmentBodyPart.setDisposition(Part.ATTACHMENT);
			attachmentBodyPart.setDataHandler(new DataHandler(new AttachmentDataSource(attachment)));
			String fileName = attachment.getName();
			fileName = MimeUtility.encodeText(fileName, charset, null);
			attachmentBodyPart.setFileName(fileName);
			mixedMultipart.addBodyPart(attachmentBodyPart);
		}


		if (mixedMultipart != null)
		{
			message.setContent(mixedMultipart);
		}
		else if (messageMultipart != null)
		{
			message.setContent(messageMultipart);
		}
		else
		{
			message.setContent(msgText, hasHTML ? htmlContentType : plainTextContentType);
			message.setHeader("Content-Type", hasHTML ? htmlContentType : plainTextContentType);
			if (encoding != null)
			{
				message.setHeader("Content-Transfer-Encoding", encoding);
			}
		}
		return message;
	}

	private void addRecipients(Message message, String recp, Message.RecipientType type) throws Exception
	{
		if (recp != null)
		{
			StringTokenizer tk = new StringTokenizer(recp, ";,");
			int nrto = tk.countTokens();
			InternetAddress[] addresses = new InternetAddress[nrto];
			for (int i = 0; i < nrto; i++)
			{
				String rcpt = tk.nextToken();
				addresses[i] = new InternetAddress(rcpt);
			}
			message.setRecipients(type, addresses);
		}
	}

	private Properties overrideProperties(Properties defaults, String[] overrideProperties)
	{
		Properties properties = new Properties(defaults);

		if (overrideProperties == null) return properties;

		for (String property : overrideProperties)
		{
			if (property != null)
			{
				int j = property.indexOf('=');
				if (j > 0)
				{
					String propertyName = property.substring(0, j);
					String propertyValue = property.substring(j + 1);
					properties.put(propertyName, propertyValue);
				}
			}
		}
		return properties;
	}

	public MailMessage[] receiveMail(String clientId, String userName, String password, boolean leaveMsgsOnServer, int recieveMode,
		Date onlyRecieveMsgWithSentDate, String[] overrideProperties) throws RemoteException
	{
		if (!checkAccess(clientId)) return null;

		Store store = null;
		Folder folder = null;
		ClassLoader saveCl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(javax.mail.Session.class.getClassLoader());
			// Create empty properties
			// -- Get hold of the default session --
			Properties properties = overrideProperties(settings, overrideProperties);
			Session session = Session.getDefaultInstance(properties, null);
			session.setDebug(Boolean.valueOf(settings.getProperty("mail.pop3.debug", "false")).booleanValue());
			// -- Get hold of a POP3 message store, and connect to it --
			store = session.getStore("pop3");
			String host = properties.getProperty("mail.pop3.host");
			Debug.trace("receiveMail: host=" + host + ", user=" + userName);
			store.connect(host, userName, password);
			// -- Try to get hold of the default folder --
			folder = store.getDefaultFolder();
			if (folder == null) throw new Exception("No default folder");
			// -- ...and its INBOX --
			folder = folder.getFolder("INBOX");
			if (folder == null) throw new Exception("No POP3 INBOX found");
			// -- Open the folder for read only --
			folder.open(leaveMsgsOnServer ? Folder.READ_ONLY : Folder.READ_WRITE);
			// -- Get the message wrappers and process them --
			Message[] message = folder.getMessages();
			if (recieveMode == IMailService.HEADERS_ONLY || onlyRecieveMsgWithSentDate != null)
			{
				FetchProfile fp = new FetchProfile();
				fp.add(FetchProfile.Item.ENVELOPE);
				folder.fetch(message, fp);
			}
			MailMessage[] retval = new MailMessage[message.length];
			for (int i = 0; i < message.length; i++)
			{
				Message msg = message[i];
				if (onlyRecieveMsgWithSentDate != null)
				{
					if (!onlyRecieveMsgWithSentDate.equals(msg.getSentDate())) continue;
				}
				retval[i] = MailServerUtils.createMailMessage(msg, recieveMode);
				if (!leaveMsgsOnServer) msg.setFlag(Flags.Flag.DELETED, true);
			}
			return retval;
		}
		catch (Exception ex)
		{
			Debug.error(ex);
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(saveCl);

			// -- Close down nicely --
			try
			{
				if (folder != null) folder.close(!leaveMsgsOnServer);
			}
			catch (Exception ex1)
			{
				Debug.error(ex1);
			}
			try
			{
				if (store != null) store.close();
			}
			catch (Exception ex2)
			{
				Debug.error(ex2);
			}
		}
		return null;
	}

	/*
	 * @see IPlugin#getPreferencePanels()
	 */
	public PreferencePanel[] getPreferencePanels()
	{
		return new PreferencePanel[] { new MailPreferencePanel(application) };
	}

	private class SMTPAuthenticator extends javax.mail.Authenticator
	{
		private final Properties properties;

		public SMTPAuthenticator(Properties properties)
		{
			this.properties = properties;
		}

		@Override
		public PasswordAuthentication getPasswordAuthentication()
		{
			String username = properties.getProperty("mail.smtp.username");
			String password = properties.getProperty("mail.smtp.password");
			return new PasswordAuthentication(username, password);
		}
	}

	public MailMessage createMailMessageFromBinary(String clientId, byte[] data) throws RemoteException
	{
		if (!checkAccess(clientId)) return null;

		MimeMessage mm = null;
		ClassLoader saveCl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(javax.mail.Session.class.getClassLoader());
			try
			{
				ByteArrayInputStream is = new ByteArrayInputStream(data);
				mm = new MimeMessage(null, is);
				is.close();
			}
			catch (Exception e)
			{
				Debug.error(e);
			}

			MailMessage sm = null;
			try
			{
				if (mm != null) sm = MailServerUtils.createMailMessage(mm, IMailService.FULL);
			}
			catch (Exception e)
			{
				Debug.error(e);
			}
			return sm;
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(saveCl);
		}
	}

	private class EmbeddedImageTagResolver implements ITagResolver
	{
		Map<String, Attachment> attachmentMap;
		List<MimeBodyPart> mimeBodyParts;

		public EmbeddedImageTagResolver(Attachment[] attachments)
		{
			mimeBodyParts = new ArrayList<MimeBodyPart>();
			attachmentMap = new HashMap<String, Attachment>();
			for (Attachment element : attachments)
			{
				attachmentMap.put(element.getName(), element);
			}
		}

		public String getStringValue(String name)
		{
			Attachment attachment = attachmentMap.get(name);
			if (attachment != null)
			{
				try
				{
					MimeBodyPart mbp = new MimeBodyPart();
					mbp.setDisposition(Part.ATTACHMENT);
					mbp.setDataHandler(new DataHandler(new AttachmentDataSource(attachment)));
					mimeBodyParts.add(mbp);
					attachment.setEmbedded(true);
					String contentID = "servoy-" + UUID.randomUUID();
					mbp.setContentID("<" + contentID + ">");
					return "cid:" + contentID;
				}
				catch (MessagingException e)
				{
					Debug.error(e);
				}
			}
			return "%%" + name + "%%";
		}

		public List<MimeBodyPart> getMimeBodyParts()
		{
			return mimeBodyParts;
		}
	}

	protected final boolean checkAccess(String clientId)
	{
		// this plugin may be accessed by server processes or authenticated clients, unless overridden in props
		boolean access = Boolean.valueOf(settings.getProperty("mail.server.allowUnauthenticatedRMIAccess", "false")).booleanValue() ||
			application.isServerProcess(clientId) || application.isAuthenticated(clientId);
		if (!access)
		{
			Debug.warn("Rejected unauthenticated access");
		}
		return access;
	}

	@Override
	public String getPluginProperty(String propertyName)
	{
		return settings.getProperty(propertyName);
	}
}
