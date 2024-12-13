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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;
import org.apache.hc.core5.util.ByteArrayBuffer;

import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;

/**
 * @author lvostinar
 *
 */
public class FileOrBinAsyncEntityConsumer extends AbstractBinAsyncEntityConsumer<Pair<byte[], File>>
{

	private final ByteArrayBuffer buffer;
	private File file;
	private boolean initialChunk = true;
	private OutputStream outputStream;

	public FileOrBinAsyncEntityConsumer()
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
		return 1024 * 1024;
	}

	@Override
	protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException
	{
		if (src == null)
		{
			return;
		}
		if (initialChunk && src.remaining() > 10000)
		{
			initialChunk = false;
			this.file = File.createTempFile("upload", ".tmp"); //$NON-NLS-1$ //$NON-NLS-2$
			this.file.deleteOnExit();
			this.outputStream = new BufferedOutputStream(new FileOutputStream(file));
		}
		if (this.outputStream != null)
		{
			byte[] bytesArray = new byte[src.remaining()];
			src.get(bytesArray, 0, bytesArray.length);
			this.outputStream.write(bytesArray);
		}
		else
		{
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
	}

	@Override
	protected Pair<byte[], File> generateContent() throws IOException
	{
		return file != null ? new Pair<byte[], File>(null, file) : new Pair<byte[], File>(buffer.toByteArray(), null);
	}

	@Override
	public void releaseResources()
	{
		buffer.clear();
		if (outputStream != null)
		{
			try
			{
				outputStream.flush();
				outputStream.close();
				outputStream = null;
			}
			catch (IOException e)
			{
				Debug.error(e);
			}
		}
	}

}