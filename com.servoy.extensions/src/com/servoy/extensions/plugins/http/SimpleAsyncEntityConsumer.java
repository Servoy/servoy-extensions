/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2022 Servoy BV

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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;
import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * @author lvostinar
 *
 */
public class SimpleAsyncEntityConsumer extends AbstractBinAsyncEntityConsumer<byte[]>
{

	private final ByteArrayBuffer buffer;

	public SimpleAsyncEntityConsumer()
	{
		super();
		this.buffer = new ByteArrayBuffer(1024);
	}

	@Override
	protected void streamStart(final ContentType contentType) throws HttpException, IOException
	{
	}

	@Override
	protected int capacityIncrement()
	{
		return Integer.MAX_VALUE;
	}

	@Override
	protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException
	{
		if (src == null)
		{
			return;
		}
		if (src.hasArray())
		{
			buffer.append(src.array(), src.arrayOffset() + src.position(), src.remaining());
		}
		else
		{
			while (src.hasRemaining())
			{
				buffer.append(src.get());
			}
		}
	}

	@Override
	protected byte[] generateContent() throws IOException
	{
		return buffer.toByteArray();
	}

	@Override
	public void releaseResources()
	{
		buffer.clear();
	}

}