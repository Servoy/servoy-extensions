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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;

/**
 * <p>The <code>MailMessage</code> object acts as a container for email messages,
 * providing methods to retrieve various components of an email. These include
 * attachments, sender and recipient addresses, email headers, plain text or HTML
 * content, and metadata like the sent date and subject.</p>
 *
 * <h2>Functionality</h2>
 * <p><code>MailMessage</code> provides a range of methods to interact with email
 * components. First, <code>getAttachments()</code> returns an array of attachments
 * in the email. Second, address-related methods such as <code>getFromAddresses()</code>,
 * <code>getRecipientAddresses()</code>, <code>getCCAddresses()</code>, and
 * <code>getReplyAddresses()</code> retrieve sender, recipient, CC, and reply-to
 * addresses respectively. Third, message content is accessible via
 * <code>getPlainMsg()</code> for plain text and <code>getHtmlMsg()</code> for HTML
 * content. Finally, metadata such as the subject, sent date, and headers can be
 * fetched using <code>getSubject()</code>, <code>getSentDate()</code>, and
 * <code>getHeaders()</code>.</p>
 *
 * <p>These methods facilitate comprehensive handling of email messages, including
 * parsing, processing, and saving data like attachments and content. References to
 * <a href="../../../servoycore/dev-api/js-lib/date.md">Date</a> and
 * <a href="../../../servoycore/dev-api/js-lib/string.md">String</a> types in the
 * methods ensure consistent data handling.</p>
 *
 * @author jblok
 */
@ServoyDocumented
public class MailMessage implements Serializable, IJavaScriptType
{
	public List<Attachment> attachments = new ArrayList<Attachment>();
	public String fromAddresses;
	public String recipientAddresses;
	public String ccAddresses;
	public String replyAddresses;
	public String subject;
	public String headers;
	public Date receivedDate;
	public Date sentDate;
	public String plainMsg;
	public String htmlMsg;

	/**
	 * Returns an array of Attachment instances corresponding to the attachments of this message.
	 *
	 * @sample
	 * var msgs = plugins.mail.receiveMail(username, password, true, 0, null, properties);
	 * if (msgs != null)
	 * {
	 * 	for (var i=0; i < msgs.length; i++)
	 * 	{
	 * 		var msg = msgs[i];
	 * 		var str = '';
	 * 		str += 'From: ' + msg.getFromAddresses() + '\n';
	 * 		str += 'To: ' + msg.getRecipientAddresses() + '\n';
	 * 		str += 'CC: ' + msg.getCCAddresses() + '\n';
	 * 		str += 'Reply to: ' + msg.getReplyAddresses() + '\n';
	 * 		str += 'Received on: ' + msg.getReceivedDate() + '\n';
	 * 		str += 'Sent on: ' + msg.getSentDate() + '\n\n';
	 * 		str += 'Subject: ' + msg.getSubject() + '\n\n';
	 * 		str += 'Plain message: ' + msg.getPlainMsg() + '\n\n';
	 * 		str += 'HTML message: ' + msg.getHtmlMsg() + '\n\n';
	 * 		str += 'Headers: ' + msg.getHeaders() + '\n\n';
	 * 		var attachments = msg.getAttachments();
	 * 		if (attachments != null) {
	 * 			str += 'Number of attachments: ' + attachments.length + '\n\n';
	 * 			for (var j=0; j < attachments.length; j++)
	 * 			{
	 * 				var attachment = attachments[j];
	 * 				str += 'Attachment ' + j + '\n';
	 * 				str += '	Name: ' + attachment.getName() + '\n';
	 * 				str += '	Size: ' + attachment.getData().length + '\n\n';
	 * 			}
	 * 		}
	 * 		plugins.file.writeTXTFile('msg' + i + '.txt', str);
	 * 		application.output('Message ' + i + ' retrieved.');
	 * 	}
	 * }
	 * else
	 * {
	 * 	application.output("Failed to retrieve messages.");
	 * }
	 *
	 * @return An array of Attachment instances corresponding to the attachments of this message.
	 */
	public Attachment[] js_getAttachments()
	{
		return getAttachments();
	}

	/**
	 * Returns a String with all addresses present in the CC field of this message.
	 *
	 * @sampleas js_getAttachments()
	 *
	 * @return A string containing all addresses in the CC field of this message.
	 */
	public String js_getCCAddresses()
	{
		return getCCAddresses();
	}

	/**
	 * Returns a String with all addresses present in the From field of this message.
	 *
	 * @sampleas js_getAttachments()
	 *
	 * @return A string containing all addresses in the From field of this message.
	 */
	public String js_getFromAddresses()
	{
		return getFromAddresses();
	}

	/**
	 * Returns a String with all headers of this message.
	 *
	 * @sampleas js_getAttachments()
	 *
	 * @return A string containing all headers of this message.
	 */
	public String js_getHeaders()
	{
		return getHeaders();
	}

	/**
	 * Returns a String with the HTML content of this message.
	 *
	 * @sampleas js_getAttachments()
	 *
	 * @return The HTML content of this message.
	 */
	public String js_getHtmlMsg()
	{
		return getHtmlMsg();
	}

	/**
	 * Returns a String with the plain content of this message.
	 *
	 * @sampleas js_getAttachments()
	 *
	 * @return The plain text content of this message.
	 */
	public String js_getPlainMsg()
	{
		return getPlainMsg();
	}

	/**
	 * Returns a Date instace corresponding to the moment when the message was received.
	 *
	 * @deprecated No longer supported. Try using  {@link #getHeaders()} for approximate results.
	 *
	 * @sampleas js_getAttachments()
	 *
	 * @return The plain text content of this message.
	 */
	@Deprecated
	public Date js_getReceivedDate()
	{
		return getReceivedDate();
	}

	/**
	 * Returns a String with all addresses in the To field of this message.
	 *
	 * @sampleas js_getAttachments()
	 *
	 * @return A string containing all addresses in the To field of this message.
	 */
	public String js_getRecipientAddresses()
	{
		return getRecipientAddresses();
	}

	/**
	 * Returns a String with all addresses in the Reply-To field of this message.
	 *
	 * @sampleas js_getAttachments()
	 *
	 * @return A string containing all addresses in the Reply-To field of this message.
	 */
	public String js_getReplyAddresses()
	{
		return getReplyAddresses();
	}

	/**
	 * Returns a Date instance corresponding to the moment when this message was sent.
	 *
	 * @sampleas js_getAttachments()
	 *
	 * @return The date and time when this message was sent.
	 */
	public Date js_getSentDate()
	{
		return getSentDate();
	}

	/**
	 * Returns a String with the subject of this message.
	 *
	 * @sampleas js_getSubject()
	 *
	 * @return The subject of this message.
	 */
	public String js_getSubject()
	{
		return getSubject();
	}

	public String getFromAddresses()
	{
		return fromAddresses;
	}

	public Date getReceivedDate()
	{
		return receivedDate;
	}

	public String getRecipientAddresses()
	{
		return recipientAddresses;
	}

	public String getReplyAddresses()
	{
		return replyAddresses;
	}

	public Date getSentDate()
	{
		return sentDate;
	}

	public String getSubject()
	{
		return subject;
	}

	public String getHeaders()
	{
		return headers;
	}

	public String getHtmlMsg()
	{
		return htmlMsg;
	}

	public String getPlainMsg()
	{
		return plainMsg;
	}

	public void addAttachment(Attachment attachment)
	{
		attachments.add(attachment);
	}

	public Attachment[] getAttachments()
	{
		return attachments.toArray(new Attachment[attachments.size()]);
	}

	public String getCCAddresses()
	{
		return ccAddresses;
	}

	@Override
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("from: "); //$NON-NLS-1$
		sb.append(getFromAddresses());
		sb.append("\n"); //$NON-NLS-1$
		sb.append("to: "); //$NON-NLS-1$
		sb.append(getRecipientAddresses());
		sb.append("\n"); //$NON-NLS-1$
		sb.append("cc: "); //$NON-NLS-1$
		sb.append(getCCAddresses());
		sb.append("\n"); //$NON-NLS-1$
		sb.append("sentdate: "); //$NON-NLS-1$
		sb.append(getSentDate());
		sb.append("\n"); //$NON-NLS-1$
		sb.append("subject: "); //$NON-NLS-1$
		sb.append(getSubject());
		sb.append("plain msg: "); //$NON-NLS-1$
		sb.append(getPlainMsg());
		sb.append("\n"); //$NON-NLS-1$
		sb.append("html msg: "); //$NON-NLS-1$
		sb.append(getHtmlMsg());
		sb.append("\n"); //$NON-NLS-1$
		return sb.toString();
	}

}
