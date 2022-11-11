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
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeaderValueFormatter;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;

import com.servoy.j2db.util.Debug;

/**
 * @author lvostinar
 *
 */
public class MultiPartEntityProducer implements AsyncEntityProducer
{
	List<InnerMultiPartAsyncProducer> producers = new ArrayList<InnerMultiPartAsyncProducer>();
	int currentIndex = 0;
	final ByteArrayBuffer boundaryEncoded;
	final String boundary;

	static final ByteArrayBuffer FIELD_SEP = encode(StandardCharsets.ISO_8859_1, ": ");
	static final ByteArrayBuffer CR_LF = encode(StandardCharsets.ISO_8859_1, "\r\n");
	static final ByteArrayBuffer TWO_HYPHENS = encode(StandardCharsets.ISO_8859_1, "--");

	private final static char[] MULTIPART_CHARS = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
		.toCharArray();

	static ByteArrayBuffer encode(
		final Charset charset, final String string)
	{
		final ByteBuffer encoded = charset.encode(CharBuffer.wrap(string));
		final ByteArrayBuffer bab = new ByteArrayBuffer(encoded.remaining());
		bab.append(encoded.array(), encoded.arrayOffset() + encoded.position(), encoded.remaining());
		return bab;
	}

	public MultiPartEntityProducer()
	{
		this.boundary = this.generateBoundary();
		this.boundaryEncoded = encode(StandardCharsets.ISO_8859_1, this.boundary);
	}

	private String generateBoundary()
	{
		final ThreadLocalRandom rand = ThreadLocalRandom.current();
		final int count = rand.nextInt(30, 41); // a random size from 30 to 40
		final CharBuffer buffer = CharBuffer.allocate(count);
		while (buffer.hasRemaining())
		{
			buffer.put(MULTIPART_CHARS[rand.nextInt(MULTIPART_CHARS.length)]);
		}
		buffer.flip();
		return buffer.toString();
	}

	public void addProducer(AsyncEntityProducer producer, String name, String fileName)
	{
		producers.add(new InnerMultiPartAsyncProducer(producer, name, fileName));
	}

	@Override
	public boolean isRepeatable()
	{
		return true;
	}

	@Override
	public String getContentType()
	{
		return ContentType.MULTIPART_FORM_DATA.withParameters(new BasicNameValuePair("boundary", this.boundary)).toString();
	}

	@Override
	public long getContentLength()
	{
		return -1;
	}

	@Override
	public int available()
	{
		return Integer.MAX_VALUE;
	}

	@Override
	public String getContentEncoding()
	{
		return null;
	}

	@Override
	public boolean isChunked()
	{
		// can it have chunks ?
		return false;
	}

	@Override
	public Set<String> getTrailerNames()
	{
		return null;
	}

	@Override
	public void produce(final DataStreamChannel channel) throws IOException
	{
		if (getCurrentProducer() != null)
		{
			getCurrentProducer().produce(new DataStreamChannel()
			{

				@Override
				public void requestOutput()
				{
					channel.requestOutput();
				}

				@Override
				public int write(final ByteBuffer src) throws IOException
				{
					if (!headerIsWritten())
					{
						writeBytes(TWO_HYPHENS, channel);
						writeBytes(boundaryEncoded, channel);
						writeBytes(CR_LF, channel);

						// write content-disposition
						writeBytes(encode(StandardCharsets.ISO_8859_1, HttpHeaders.CONTENT_DISPOSITION), channel);
						writeBytes(FIELD_SEP, channel);
						final CharArrayBuffer buf = new CharArrayBuffer(64);
						buf.append("form-data");
						buf.append("; ");
						final List<NameValuePair> fieldParameters = new ArrayList<>();
						if (getCurrentProducerName() != null) fieldParameters.add(new BasicNameValuePair("name", getCurrentProducerName()));
						if (getCurrentProducerFileName() != null) fieldParameters.add(new BasicNameValuePair("filename", getCurrentProducerFileName()));

						if (fieldParameters.size() > 0)
						{
							BasicHeaderValueFormatter.INSTANCE.formatParameters(buf, fieldParameters.toArray(new NameValuePair[0]), false);
						}
						writeBytes(encode(StandardCharsets.ISO_8859_1, buf.toString()), channel);
						writeBytes(CR_LF, channel);
						if (getCurrentProducer().getContentType() != null)
						{
							writeBytes(encode(StandardCharsets.ISO_8859_1, HttpHeaders.CONTENT_TYPE), channel);
							writeBytes(FIELD_SEP, channel);
							writeBytes(encode(StandardCharsets.ISO_8859_1, getCurrentProducer().getContentType()), channel);
							writeBytes(CR_LF, channel);
						}

						writeBytes(CR_LF, channel);
						markHeaderWritten();
					}
					return channel.write(src);
				}

				@Override
				public void endStream(final List< ? extends Header> p) throws IOException
				{
					writeBytes(CR_LF, channel);
					getCurrentProducer().releaseResources();
					goToNextProducer();
				}

				@Override
				public void endStream() throws IOException
				{
					endStream(null);
				}
			});
		}
		else
		{
			writeBytes(TWO_HYPHENS, channel);
			writeBytes(boundaryEncoded, channel);
			writeBytes(TWO_HYPHENS, channel);
			writeBytes(CR_LF, channel);
			channel.endStream();
		}
	}

	/**
	 *
	 */
	protected void goToNextProducer()
	{
		this.currentIndex++;
	}

	/**
	 * @return
	 */
	protected boolean headerIsWritten()
	{
		if (currentIndex < this.producers.size())
		{
			return this.producers.get(currentIndex).initialized;
		}
		return true;
	}

	/**
	 *
	 */
	protected void markHeaderWritten()
	{
		if (currentIndex < this.producers.size())
		{
			this.producers.get(currentIndex).initialized = true;
		}
	}

	/**
	 * @return
	 */
	private AsyncEntityProducer getCurrentProducer()
	{
		if (currentIndex < this.producers.size())
		{
			return this.producers.get(currentIndex).producer;
		}
		return null;
	}

	private String getCurrentProducerName()
	{
		if (currentIndex < this.producers.size())
		{
			return this.producers.get(currentIndex).name;
		}
		return null;
	}

	private String getCurrentProducerFileName()
	{
		if (currentIndex < this.producers.size())
		{
			return this.producers.get(currentIndex).fileName;
		}
		return null;
	}

	void writeBytes(
		final ByteArrayBuffer b, DataStreamChannel channel) throws IOException
	{
		channel.write(ByteBuffer.wrap(b.array()));
	}

	@Override
	public void failed(final Exception cause)
	{
		Debug.error(cause);
	}

	@Override
	public void releaseResources()
	{
	}

}

class InnerMultiPartAsyncProducer
{
	AsyncEntityProducer producer;
	boolean initialized = false;
	String name;
	String fileName;

	public InnerMultiPartAsyncProducer(AsyncEntityProducer producer, String name, String fileName)
	{
		this.producer = producer;
		this.name = name;
		this.fileName = fileName;
	}
}
