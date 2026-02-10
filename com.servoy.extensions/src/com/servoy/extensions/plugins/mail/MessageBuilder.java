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

import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.extensions.plugins.mail.client.Attachment;
import com.servoy.j2db.scripting.IScriptable;

/**
 * Builder for creating email message content to be sent using an SMTP service.
 * <p>
 * This builder allows configuring recipients, subject, message body, CC, BCC,
 * and attachments using a fluent API.
 * <p>
 * Instances of {@code MessageBuilder} are intended for single-use. The builder
 * itself does not send messages; it only defines the message content.
 *
 * @sample
 * var message = plugins.mail.newMessage()
 *     .withTo("user@example.com")
 *     .withSubject("Hello")
 *     .withRawMsgText("This is a test message")
 *     .withCC("cc@example.com")
 *     .withBCC("bcc@example.com");
 *
 * smtpService.sendMail(message);
 *
 * @author emera
 */
public class MessageBuilder implements IScriptable
{
	private String _to;
	private String _subject;
	private String _rawMsgText;
	private String _cc;
	private Attachment[] _attachments;
	private String _bcc;

	public MessageBuilder()
	{
	}

	/**
	 * Sets the recipient email address.
	 *
	 * @param to the recipient email address
	 * @return this MessageBuilder instance for chaining
	 */
	@JSFunction
	public MessageBuilder withTo(String to)
	{
		this._to = to;
		return this;
	}

	/**
	 * Returns the recipient email address.
	 *
	 * @return the recipient email address
	 */
	public String getTo()
	{
		return _to;
	}

	/**
	 * Sets the email subject.
	 *
	 * @param subject the message subject
	 * @return this MessageBuilder instance for chaining
	 */
	@JSFunction
	public MessageBuilder withSubject(String subject)
	{
		this._subject = subject;
		return this;
	}


	/**
	 * Sets the raw message body text.
	 * <p>
	 * The text is sent as-is and is not interpreted as HTML.
	 *
	 * @param rawMsgText the message body text
	 * @return this MessageBuilder instance for chaining
	 */
	@JSFunction
	public MessageBuilder withRawMsgText(String rawMsgText)
	{
		this._rawMsgText = rawMsgText;
		return this;
	}

	/**
	 * Returns the raw message body text.
	 *
	 * @return the message body text
	 */
	public String getRawMsgText()
	{
		return _rawMsgText;
	}

	/**
	 * Returns the message subject.
	 *
	 * @return the message subject
	 */
	public String getSubject()
	{
		return _subject;
	}

	/**
	 * Sets the CC (carbon copy) recipient(s).
	 * <p>
	 * Multiple recipients may be specified as a comma-separated list.
	 *
	 * @param cc the CC recipient email address(es)
	 * @return this MessageBuilder instance for chaining
	 */
	@JSFunction
	public MessageBuilder withCC(String cc)
	{
		this._cc = cc;
		return this;
	}

	/**
	 * Returns the CC (carbon copy) recipient(s).
	 *
	 * @return the CC recipient email address(es)
	 */
	public String getCC()
	{
		return _cc;
	}

	/**
	 * Sets the BCC (blind carbon copy) recipient(s).
	 * <p>
	 * Multiple recipients may be specified as a comma-separated list.
	 *
	 * @param bcc the BCC recipient email address(es)
	 * @return this MessageBuilder instance for chaining
	 */
	@JSFunction
	public MessageBuilder withBCC(String bcc)
	{
		this._bcc = bcc;
		return this;
	}

	/**
	 * Returns the BCC (blind carbon copy) recipient(s).
	 *
	 * @return the BCC recipient email address(es)
	 */
	public String getBCC()
	{
		return _bcc;
	}

	/**
	 * Sets the attachments for this message.
	 *
	 * @param attachments an array of Attachment objects to include with the message
	 * @return this MessageBuilder instance for chaining
	 */
	@JSFunction
	public MessageBuilder withAttachments(Attachment[] attachments)
	{
		this._attachments = attachments;
		return this;
	}

	/**
	 * Returns the attachments configured for this message.
	 *
	 * @return the message attachments
	 */
	public Attachment[] getAttachments()
	{
		return _attachments;
	}
}
