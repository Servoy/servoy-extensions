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
	long contentLength = -2;

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
		final int count = 40; // rand.nextInt(30, 41); // a random size from 30 to 40
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
		this.initContentLength();
		return this.contentLength;
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
		this.initContentLength();
		return this.contentLength < 0;
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
						MultiPartEntityProducer.this.writePartHeader(channel, getCurrentProducer(), getCurrentProducerName(), getCurrentProducerFileName());
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

	private void writePartHeader(DataStreamChannel channel, AsyncEntityProducer producer, String name, String fileName) throws IOException
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
		if (name != null) fieldParameters.add(new BasicNameValuePair("name", name));
		if (fileName != null) fieldParameters.add(new BasicNameValuePair("filename", fileName));

		if (fieldParameters.size() > 0)
		{
			BasicHeaderValueFormatter.INSTANCE.formatParameters(buf, fieldParameters.toArray(new NameValuePair[0]), true);
		}
		writeBytes(encode(StandardCharsets.ISO_8859_1, buf.toString()), channel);
		writeBytes(CR_LF, channel);
		if (producer.getContentType() != null)
		{
			writeBytes(encode(StandardCharsets.ISO_8859_1, HttpHeaders.CONTENT_TYPE), channel);
			writeBytes(FIELD_SEP, channel);
			writeBytes(encode(StandardCharsets.ISO_8859_1, producer.getContentType()), channel);
			writeBytes(CR_LF, channel);
		}

		writeBytes(CR_LF, channel);
	}

	private void initContentLength()
	{
		if (this.contentLength == -2)
		{
			long totalContentLength = -1;
			for (InnerMultiPartAsyncProducer producer : producers)
			{
				long innerContentLength = producer.producer.getContentLength();
				if (innerContentLength == -1)
				{
					totalContentLength = -1;
					break;
				}
				totalContentLength += innerContentLength;
			}
			if (totalContentLength >= 0)
			{
				for (InnerMultiPartAsyncProducer producer : producers)
				{
					try
					{
						int[] headerLength = new int[] { 0 };
						this.writePartHeader(new DataStreamChannel()
						{

							@Override
							public void endStream() throws IOException
							{

							}

							@Override
							public int write(ByteBuffer src) throws IOException
							{
								headerLength[0] += src.remaining();
								return 0;
							}

							@Override
							public void requestOutput()
							{

							}

							@Override
							public void endStream(List< ? extends Header> trailers) throws IOException
							{

							}
						}, producer.producer, producer.name, producer.fileName);
						totalContentLength += headerLength[0];

					}
					catch (IOException e)
					{
						Debug.error(e);
					}

					totalContentLength += 2;//CR_LF in endstream
				}
				totalContentLength += 6 + this.boundary.length();
			}
			this.contentLength = totalContentLength;
		}
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
