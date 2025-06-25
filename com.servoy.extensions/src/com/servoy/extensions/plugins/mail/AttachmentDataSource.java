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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.servoy.extensions.plugins.mail.client.Attachment;

import jakarta.activation.DataSource;
import jakarta.activation.MimetypesFileTypeMap;

/**
 * Separate class because we do not have DataSource class on client
 * @author jblok
 */
class AttachmentDataSource implements DataSource
{
	private final Attachment attachment;

	public AttachmentDataSource(Attachment att)
	{
		attachment = att;
	}

	public String getContentType()
	{
		String mimeType = attachment.getMimeType();
		if (mimeType == null)
		{
			MimetypesFileTypeMap mftm = new MimetypesFileTypeMap();
			mimeType = mftm.getContentType(attachment.getName());
		}
		return mimeType;
	}

	public InputStream getInputStream() throws IOException
	{
		return new ByteArrayInputStream(attachment.getData());
	}

	public String getName()
	{
		return attachment.getName();
	}

	public OutputStream getOutputStream() throws IOException
	{
		throw new IOException("not supported to write"); //$NON-NLS-1$
	}
}
