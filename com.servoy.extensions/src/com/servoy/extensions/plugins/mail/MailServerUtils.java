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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.StringTokenizer;

import com.servoy.extensions.plugins.mail.client.Attachment;
import com.servoy.extensions.plugins.mail.client.MailMessage;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeUtility;

/**
 * @author jblok
 */
public class MailServerUtils
{
	public static MailMessage createMailMessage(Message m, int receiveMode) throws MessagingException, IOException
	{
		MailMessage mm = new MailMessage();
		if (m != null)
		{
			try
			{
				mm.fromAddresses = createAddressString(m.getFrom());
			}
			catch (Exception e)
			{
				Debug.warn(e);
			}
			try
			{
				mm.recipientAddresses = createAddressString(m.getRecipients(Message.RecipientType.TO));
			}
			catch (Exception e)
			{
				Debug.warn(e);
			}
			try
			{
				mm.ccAddresses = createAddressString(m.getRecipients(Message.RecipientType.CC));
			}
			catch (Exception e)
			{
				Debug.warn(e);
			}
			try
			{
				mm.replyAddresses = createAddressString(m.getReplyTo());
			}
			catch (Exception e)
			{
				Debug.warn(e);
			}
			try
			{
				mm.subject = m.getSubject();
			}
			catch (Exception e)
			{
				Debug.warn(e);
			}
			try
			{
				mm.receivedDate = m.getReceivedDate();
			}
			catch (Exception e)
			{
				Debug.warn(e);
			}
			try
			{
				mm.sentDate = m.getSentDate();
			}
			catch (Exception e)
			{
				Debug.warn(e);
			}
			try
			{
				mm.headers = createHeaderString(m.getAllHeaders());
			}
			catch (Exception e)
			{
				Debug.warn(e);
			}

			if (receiveMode != IMailService.HEADERS_ONLY)
			{
				try
				{
					handlePart(mm, m, receiveMode);
				}
				catch (Exception e)
				{
					Debug.warn(e);
				}
			}
		}
		return mm;
	}

	private static void handleContent(MailMessage mm, Object content, int receiveMode) throws MessagingException, IOException
	{
		if (content instanceof Multipart)
		{
			Multipart multi = (Multipart)content;
			for (int i = 0; i < multi.getCount(); i++)
			{
				handlePart(mm, multi.getBodyPart(i), receiveMode);
			}
		}
		else if (content instanceof BodyPart)
		{
			handlePart(mm, (Part)content, receiveMode);
		}
		else
		{
			Debug.trace("?"); //$NON-NLS-1$
		}
	}

	private static void handlePart(MailMessage mm, Part messagePart, int receiveMode) throws MessagingException, IOException
	{
		// -- Get the content type --
		String contentType = messagePart.getContentType();
		String charset = getCharsetFromContentType(contentType);
		if (contentType.startsWith("text/plain")) //$NON-NLS-1$
		{
			if (messagePart.getFileName() == null)
			{
				mm.plainMsg = createText(messagePart, charset);
				return;
			}
		}
		else if (contentType.startsWith("text/html")) //$NON-NLS-1$
		{
			if (messagePart.getFileName() == null)
			{
				mm.htmlMsg = createText(messagePart, charset);
				return;
			}
		}
		else if (contentType.startsWith("multipart")) //$NON-NLS-1$
		{
			handleContent(mm, messagePart.getContent(), receiveMode);
			return;
		}
		if (receiveMode != IMailService.NO_ATTACHMENTS)
		{
			mm.addAttachment(createAttachment(messagePart));
		}
	}

	/**
	 * @param messagePart
	 * @return
	 */
	private static String createText(Part messagePart, String charsetName) throws MessagingException, IOException
	{
		StringBuffer retval = new StringBuffer();
		InputStream is = messagePart.getInputStream();

		Charset charset = null;
		if (charsetName != null)
		{
			try
			{
				charset = Charset.forName(charsetName);
			}
			catch (Exception ex)
			{
				Debug.trace(ex);//notfound of bad name, dono what todo now, try with default decoder...
			}
		}

		BufferedReader reader = null;
		if (charset != null)
		{
			reader = new BufferedReader(new InputStreamReader(is, charset));
		}
		else
		{
			reader = new BufferedReader(new InputStreamReader(is));
		}

		String line = null;
		while ((line = reader.readLine()) != null)
		{
			retval.append(line);
			retval.append('\n');
		}
		reader.close();
		is.close();
		return retval.toString();
	}

	private static boolean isAttachmentEmbedded(Part messagePart) throws MessagingException
	{
		Enumeration headers = messagePart.getAllHeaders();
		while (headers.hasMoreElements())
		{
			Header header = (Header)headers.nextElement();
			if (header.getName().startsWith("Content-ID")) return true; //$NON-NLS-1$
		}
		return false;
	}

	private static Attachment createAttachment(Part messagePart) throws MessagingException, IOException
	{
		InputStream is = messagePart.getInputStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Utils.streamCopy(is, baos);
		is.close();
		// currenty seems to be enough for deconding attachment file name;
		// we might have to use Normalizer.normalize(decoded, Normalizer.Form.NFC) in the future
		return new Attachment(messagePart.getFileName() != null ? MimeUtility.decodeText(messagePart.getFileName()) : null, baos.toByteArray(),
			isAttachmentEmbedded(messagePart));
	}

	public static String createAddressString(Address[] addresses)
	{
		if (addresses == null) return null;
		StringBuffer retval = new StringBuffer();
		for (int i = 0; i < addresses.length; i++)
		{
			if (addresses[i] != null)
			{
				retval.append(addresses[i].toString());
				if (i < addresses.length - 1)
				{
					retval.append(";"); //$NON-NLS-1$
				}
			}
		}
		return retval.toString();
	}

	private static String createHeaderString(Enumeration headers)
	{
		StringBuffer retval = new StringBuffer();
		while (headers.hasMoreElements())
		{
			Header header = (Header)headers.nextElement();
			retval.append(header.getName());
			retval.append("="); //$NON-NLS-1$
			retval.append(header.getValue());
			if (headers.hasMoreElements())
			{
				retval.append("\n"); //$NON-NLS-1$
			}
		}
		return retval.toString();
	}

	private static String getCharsetFromContentType(String contentType)
	{
		String charset = null;
		if (contentType != null)
		{
			StringTokenizer contentTypeTokens = new StringTokenizer(contentType, ";");
			String ctToken = null;
			while (contentTypeTokens.hasMoreTokens())
			{
				ctToken = contentTypeTokens.nextToken().trim();
				if (ctToken.startsWith("charset"))
				{
					int charsetNameIdx = ctToken.indexOf("=");
					if (charsetNameIdx != -1 && charsetNameIdx < ctToken.length() - 1)
					{
						charset = ctToken.substring(charsetNameIdx + 1).trim();
						// sometimes you find " or ' as well around the charset name
						if (charset.startsWith("\"")) charset = charset.substring(1);
						if (charset.endsWith("\"")) charset = charset.substring(0, charset.length() - 1);
						if (charset.startsWith("\'")) charset = charset.substring(1);
						if (charset.endsWith("\'")) charset = charset.substring(0, charset.length() - 1);
					}
				}
			}
		}
		return charset;
	}
}
