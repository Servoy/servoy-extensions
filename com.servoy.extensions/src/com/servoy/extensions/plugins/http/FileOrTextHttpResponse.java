/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2024 Servoy BV

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

package com.servoy.extensions.plugins.http;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.BasicHttpResponse;

import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.FileChooserUtils;

/**
 * Contains a response either as bytes or as a file (in order to avoid loading everything in memory)
 * @author lvostinar
 *
 */
public class FileOrTextHttpResponse extends BasicHttpResponse
{
	private ContentType contentType;
	private byte[] bodyBytes;
	private File file;

	public FileOrTextHttpResponse(int code)
	{
		super(code);
	}

	public byte[] getBodyBytes()
	{
		if (bodyBytes != null)
		{
			return bodyBytes;
		}
		else if (file != null && file.exists() && !file.isDirectory())
		{
			try
			{
				return FileChooserUtils.readFile(file);
			}
			catch (Exception e)
			{
				Debug.error(e);
			}
		}
		return null;
	}

	public String getBodyText()
	{
		final Charset charset = (contentType != null ? contentType : ContentType.DEFAULT_TEXT).getCharset();
		if (bodyBytes != null)
		{
			return new String(bodyBytes, charset != null ? charset : StandardCharsets.ISO_8859_1);
		}
		if (file != null)
		{
			try
			{
				return new String(FileChooserUtils.readFile(file), charset != null ? charset : StandardCharsets.ISO_8859_1);
			}
			catch (Exception e)
			{
				Debug.error(e);
			}
		}
		return null;

	}

	public File getFile()
	{
		return file;
	}

	/**
	 * @return
	 */
	public ContentType getContentType()
	{
		return contentType;
	}

	public void setBodyBytes(byte[] byteArray)
	{
		this.bodyBytes = byteArray;

	}

	public void setContentType(ContentType contentType)
	{
		this.contentType = contentType;

	}

	public void setFile(File file)
	{
		this.file = file;
	}
}
