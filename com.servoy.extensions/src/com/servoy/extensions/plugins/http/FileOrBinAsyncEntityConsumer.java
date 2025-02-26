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

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.AbstractBinDataConsumer;

import com.servoy.j2db.util.Pair;

/**
 * @author lvostinar
 *
 */
public class FileOrBinAsyncEntityConsumer extends AbstractBinDataConsumer implements AsyncEntityConsumer<Pair<byte[], File>>
{
	private volatile FutureCallback<Pair<byte[], File>> resultCallback;
	private volatile Pair<byte[], File> content;
	private volatile Thread unzipThread;

	private boolean initialChunk = true;
	private boolean gzipEncoding = false;
	private volatile DeferredFileOutputStream outputStream;
	private PipedOutputStream pos;


	public FileOrBinAsyncEntityConsumer()
	{
		super();
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

		if (initialChunk)
		{
			initialChunk = false;
			// this threshold could maybe be set as a parameter or use the param
			// servoy.ng_web_client.tempfile.threshold, for now we just set it to that default 50kb
			this.outputStream = DeferredFileOutputStream.builder().setThreshold(50 * 1024).setBufferSize(4096).setPrefix("httpplugin").setSuffix(".download")
				.get();
			if (gzipEncoding)
			{
				PipedInputStream pis = new PipedInputStream();
				pos = new PipedOutputStream(pis);
				unzipThread = new Thread(() -> {
					try (GZIPInputStream gzipInputStream = new GZIPInputStream(pis))
					{
						byte[] buffer = new byte[2048];
						int len;
						while ((len = gzipInputStream.read(buffer)) > 0)
						{
							outputStream.write(buffer, 0, len);
						}
						this.outputStream.close();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				});
				unzipThread.start();
			}
		}
		byte[] bytesArray = new byte[src.remaining()];
		src.get(bytesArray, 0, bytesArray.length);
		if (pos != null)
		{
			pos.write(bytesArray);
		}
		else this.outputStream.write(bytesArray);

		if (endOfStream)
		{
			// make sure to flush everything so that the gzip pipeping will finish asap
			if (pos != null)
			{
				pos.flush();
				try
				{
					// now join on the thread so we wait until it really have written everything.
					unzipThread.join();
				}
				catch (InterruptedException e)
				{
				}
			}
			else outputStream.flush();
		}
	}

	protected Pair<byte[], File> generateContent()
	{
		File file = outputStream.getFile();
		if (file != null) file.deleteOnExit();
		return file != null ? new Pair<byte[], File>(null, file) : new Pair<byte[], File>(outputStream.getData(), null);
	}

	@Override
	public void releaseResources()
	{
		if (outputStream != null)
		{
			try
			{
				outputStream.close();
				outputStream = null;
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		if (pos != null)
		{
			try
			{
				pos.close();
				pos = null;
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		unzipThread = null;
	}


	@Override
	public final void streamStart(
		final EntityDetails entityDetails,
		@SuppressWarnings("hiding") final FutureCallback<Pair<byte[], File>> resultCallback) throws IOException, HttpException
	{
		this.resultCallback = resultCallback;
		gzipEncoding = "gzip".equalsIgnoreCase(entityDetails.getContentEncoding()) || "deflate".equalsIgnoreCase(entityDetails.getContentEncoding()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	protected final void completed() throws IOException
	{
		content = generateContent();
		if (resultCallback != null)
		{
			resultCallback.completed(content);
		}
		releaseResources();
	}

	@Override
	public final void failed(final Exception cause)
	{
		if (resultCallback != null)
		{
			resultCallback.failed(cause);
		}
		releaseResources();
	}

	@Override
	public final Pair<byte[], File> getContent()
	{
		return content;
	}

}