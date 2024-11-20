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

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.util.MimeTypes;

/**
 * <p>Email attachments can be managed through an object providing methods to access their content, properties, and state.
 * Attachments can be created using binary or text files and sent as part of an email.</p>
 *
 * <h2>Functionality</h2>
 *
 * <p>Email attachments support several operations. The <code>getData()</code> method retrieves the content of the attachment as a byte array.
 * <code>getMimeType()</code> provides the MIME type, useful for identifying file formats. <code>getName()</code> returns the attachment's name for display or logging purposes.
 * The <code>isEmbedded()</code> method checks if the attachment is embedded in the email body, determined by references in the email's text content.</p>
 *
 * <p>These methods enable robust email attachment handling, including retrieving file properties and ensuring proper integration with email body content.</p>
 *
 * @author jblok
 */
@ServoyDocumented(scriptingName = "Attachment")
public class Attachment implements Serializable, IJavaScriptType
{
	private final String name;
	private String mimeType;
	private final byte[] data;
	private boolean embedded;

	public Attachment()
	{
		//for developer script introspection only
		this(null, null, null);
	}

	public Attachment(String name, byte[] data)
	{
		this(name, data, null);
	}

	public Attachment(String name, byte[] data, boolean embedded)
	{
		this(name, data, null, embedded);
	}

	public Attachment(String name, byte[] data, String mimeType)
	{
		this(name, data, mimeType, false);
	}

	public Attachment(String name, byte[] data, String mimeType, boolean embedded)
	{
		this.name = name;
		this.mimeType = mimeType;
		this.data = data;
		this.embedded = embedded;

		if (mimeType == null && data != null && data.length != 0)
		{
			this.mimeType = MimeTypes.getContentType(data, name);
		}
	}

	/**
	 * Returns a byte array with the content of this attachment.
	 *
	 * @sample
	 * var logo = plugins.mail.createBinaryAttachment('logo.jpg', plugins.file.readFile('d:/logo.jpg'));
	 * var invoice = plugins.mail.createTextAttachment('invoice.txt', plugins.file.readTXTFile('d:/invoice.txt'));
	 * var attachments = new Array(logo, invoice);
	 * var success = plugins.mail.sendMail(toAddress, fromAddress, 'subject line', 'message text', null, null, attachments, properties);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert', 'Failed to send mail', 'OK');
	 * }
	 * else
	 * {
	 * 	plugins.dialogs.showInfoDialog('Success', 'Mail sent', 'OK');
	 * 	application.output('logo attachment name: ' + logo.getName());
	 * 	application.output('logo attachment mime type: ' + logo.getMimeType());
	 * 	application.output('logo attachment size: ' + logo.getData().length);
	 * 	application.output('logo attachment embedded state: ' + logo.isEmbedded());
	 * 	application.output('invoice attachment name: ' + invoice.getName());
	 * 	application.output('invoice attachment mime type: ' + invoice.getMimeType());
	 * 	application.output('invoice attachment size: ' + invoice.getData().length);
	 * 	application.output('invoice attachment embedded state: ' + invoice.isEmbedded());
	 * }
	 */
	public byte[] js_getData()
	{
		return getData();
	}

	/**
	 * Returns the name of this attachment.
	 *
	 * @sampleas js_getData()
	 */
	public String js_getName()
	{
		return getName();
	}

	/**
	 * Returns the Mime type of this attachment.
	 *
	 * @sampleas js_getData()
	 */
	public String js_getMimeType()
	{
		return getMimeType();
	}

	/**
	 * Returns true if this attachment is embedded, false otherwise. Attachments become embedded
	 * if they are references through tags from the body text of the message.
	 *
	 * @sampleas js_getData()
	 */
	public boolean js_isEmbedded()
	{
		return isEmbedded();
	}

	public byte[] getData()
	{
		return data;
	}

	public String getName()
	{
		return name;
	}

	public String getMimeType()
	{
		return mimeType;
	}

	public void setEmbedded(boolean embedded)
	{
		this.embedded = embedded;
	}

	public boolean isEmbedded()
	{
		return embedded;
	}

	@Override
	@SuppressWarnings("nls")
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("name: "); //$NON-NLS-1$
		sb.append(getName());
		sb.append("\n"); //$NON-NLS-1$
		sb.append("mime type: "); //$NON-NLS-1$
		sb.append(getMimeType());
		sb.append("\n"); //$NON-NLS-1$
		sb.append("datalength: "); //$NON-NLS-1$
		sb.append((getData() != null ? getData().length : 0));
		sb.append("\n"); //$NON-NLS-1$
		sb.append("embedded: ");
		sb.append(embedded);
		sb.append("\n");
		return sb.toString();
	}

}
