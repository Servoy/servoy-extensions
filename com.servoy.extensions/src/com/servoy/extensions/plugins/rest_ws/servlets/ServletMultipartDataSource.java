/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

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
package com.servoy.extensions.plugins.rest_ws.servlets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;

import jakarta.activation.DataSource;

public class ServletMultipartDataSource implements DataSource
{
	String contentType;
	InputStream inputStream;

	public ServletMultipartDataSource(InputStream inputStream, String contentType)
	{
		this.inputStream = new SequenceInputStream(new ByteArrayInputStream("\n".getBytes()), inputStream);
		this.contentType = contentType;
	}

	public InputStream getInputStream() throws IOException
	{
		return inputStream;
	}

	public OutputStream getOutputStream() throws IOException
	{
		return null;
	}

	public String getContentType()
	{
		return contentType;
	}

	public String getName()
	{
		return "ServletMultipartDataSource";
	}
}