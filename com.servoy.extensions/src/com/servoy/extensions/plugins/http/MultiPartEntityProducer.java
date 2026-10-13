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

	/**
	 * Unwritten remainder of the current framing chunk (part header, trailing part separator or closing boundary).
	 * httpcore5's {@link DataStreamChannel#write(ByteBuffer)} is a partial write: it can accept fewer bytes than
	 * requested (or zero) under HTTP/2 flow control. When that happens we keep the remainder here and resume
	 * flushing it on the next {@link #produce(DataStreamChannel)} invocation, before emitting any new bytes.
	 */
	private ByteBuffer pending;
	/**
	 * When the pending buffer is the trailing {@value #CR_LF}-separator written after a part body, the producer must
	 * advance to the next inner producer only once that separator is fully drained.
	 */
	private boolean advanceAfterPending = false;
	/**
	 * When the pending buffer is the closing {@code --boundary-- CRLF} framing, the producer must call
	 * {@link DataStreamChannel#endStream()} only once it is fully drained.
	 */
	private boolean endStreamAfterPending = false;
	/**
	 * When the pending buffer is a part header that back-pressured mid-flush, the resumed {@link #produce(DataStreamChannel)}
	 * must mark the header written once the remainder drains and then continue to the body - without re-emitting the header.
	 */
	private boolean markHeaderAfterPending = false;

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

	public void addProducer(AsyncEntityProducer producer, String name, String fileName, boolean writeContentType)
	{
		producers.add(new InnerMultiPartAsyncProducer(producer, name, fileName, writeContentType));
	}

	@Override
	public boolean isRepeatable()
	{
		return true;
	}

	@Override
	public String getContentType()
	{
		return ContentType.create("multipart/form-data", new BasicNameValuePair("boundary", this.boundary)).toString();
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
		// First drain any remainder left over from a previous, back-pressured produce() call. httpcore5's
		// DataStreamChannel.write is a partial write, so a framing chunk may need several produce() cycles to flush.
		if (this.pending != null)
		{
			if (!flushPending(channel))
			{
				// still back-pressured; requestOutput() was already called, wait for the next produce()
				return;
			}
			// pending fully drained: run any transition that was waiting on it
			if (this.markHeaderAfterPending)
			{
				// the drained pending buffer was the part header; mark it written and fall through to the body,
				// so the header is NOT rebuilt and re-emitted below
				this.markHeaderAfterPending = false;
				markHeaderWritten();
			}
			else if (this.advanceAfterPending)
			{
				this.advanceAfterPending = false;
				goToNextProducer();
			}
			else if (this.endStreamAfterPending)
			{
				this.endStreamAfterPending = false;
				channel.endStream();
				return;
			}
		}

		if (getCurrentProducer() != null)
		{
			// make sure the part header is fully flushed before any body bytes are written
			if (!headerIsWritten())
			{
				this.pending = ByteBuffer.wrap(
					buildPartHeader(getCurrentProducer(), getCurrentProducerName(), getCurrentProducerFileName(), getCurrentProducerWriteContentType())
						.toByteArray());
				if (!flushPending(channel))
				{
					// header not fully written yet; remember that the pending buffer is the header so the resumed
					// produce() marks it written when the remainder drains instead of re-emitting the whole header
					this.markHeaderAfterPending = true;
					return;
				}
				markHeaderWritten();
			}

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
					// body bytes: return the real count so the wrapped inner producer honours partial writes itself
					return channel.write(src);
				}

				@Override
				public void endStream(final List< ? extends Header> p) throws IOException
				{
					// write the trailing CR_LF separator after the part body through the pending-remainder path;
					// only advance to the next producer once it is fully drained
					MultiPartEntityProducer.this.pending = ByteBuffer.wrap(CR_LF.toByteArray());
					getCurrentProducer().releaseResources();
					if (flushPending(channel))
					{
						goToNextProducer();
					}
					else
					{
						MultiPartEntityProducer.this.advanceAfterPending = true;
					}
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
			// closing framing: --boundary-- CRLF as a single contiguous buffer, fully flushed before endStream()
			final ByteArrayBuffer closing = new ByteArrayBuffer(TWO_HYPHENS.length() + boundaryEncoded.length() + TWO_HYPHENS.length() + CR_LF.length());
			closing.append(TWO_HYPHENS.array(), 0, TWO_HYPHENS.length());
			closing.append(boundaryEncoded.array(), 0, boundaryEncoded.length());
			closing.append(TWO_HYPHENS.array(), 0, TWO_HYPHENS.length());
			closing.append(CR_LF.array(), 0, CR_LF.length());
			this.pending = ByteBuffer.wrap(closing.toByteArray());
			if (flushPending(channel))
			{
				channel.endStream();
			}
			else
			{
				this.endStreamAfterPending = true;
			}
		}
	}

	/**
	 * Flushes the {@link #pending} buffer, honouring httpcore5's partial-write contract. Loops while the channel keeps
	 * accepting bytes; when it returns a zero/short write the unwritten remainder is retained in {@link #pending},
	 * {@link DataStreamChannel#requestOutput()} is called and {@code false} is returned so the async engine re-invokes
	 * {@link #produce(DataStreamChannel)}.
	 *
	 * @return {@code true} when the pending buffer has been fully written (and cleared), {@code false} when the channel
	 *         is back-pressured and some bytes remain pending.
	 */
	private boolean flushPending(DataStreamChannel channel) throws IOException
	{
		while (this.pending.hasRemaining())
		{
			int written = channel.write(this.pending);
			if (written <= 0)
			{
				// back-pressure: keep the remainder and ask to be produced again
				channel.requestOutput();
				return false;
			}
		}
		this.pending = null;
		return true;
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

	private boolean getCurrentProducerWriteContentType()
	{
		if (currentIndex < this.producers.size())
		{
			return this.producers.get(currentIndex).writeContentType;
		}
		return false;
	}

	@Override
	public void failed(final Exception cause)
	{
		Debug.error(cause);
	}

	@Override
	public void releaseResources()
	{
		// reset all state so a repeated send (isRepeatable() == true) starts clean
		this.pending = null;
		this.advanceAfterPending = false;
		this.endStreamAfterPending = false;
		this.markHeaderAfterPending = false;
		this.currentIndex = 0;
		for (InnerMultiPartAsyncProducer producer : this.producers)
		{
			producer.initialized = false;
		}
	}

	/**
	 * Builds the full part header as a single contiguous buffer:
	 * {@code --boundary CRLF Content-Disposition: form-data; ... CRLF [Content-Type: ... CRLF] CRLF}.
	 * Folding the ~10 tiny writes into one buffer leaves a single partial-write seam to resume on back-pressure.
	 * The BROWSER_COMPATIBLE policy is preserved: a Content-Type line is emitted only when
	 * {@code writeContentType == true} and {@code producer.getContentType() != null}. Everything is ISO-8859-1 encoded.
	 * This same method is used by {@link #initContentLength()} to measure the header, so the advertised Content-Length
	 * cannot drift from the bytes actually written.
	 */
	private ByteArrayBuffer buildPartHeader(AsyncEntityProducer producer, String name, String fileName, boolean writeContentType)
	{
		final ByteArrayBuffer header = new ByteArrayBuffer(128);
		append(header, TWO_HYPHENS);
		append(header, boundaryEncoded);
		append(header, CR_LF);

		// write content-disposition
		append(header, encode(StandardCharsets.ISO_8859_1, HttpHeaders.CONTENT_DISPOSITION));
		append(header, FIELD_SEP);
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
		append(header, encode(StandardCharsets.ISO_8859_1, buf.toString()));
		append(header, CR_LF);
		// make it like HttpMultipartMode.BROWSER_COMPATIBLE, only send content-type for files
		if (writeContentType)
		{
			if (producer.getContentType() != null)
			{
				append(header, encode(StandardCharsets.ISO_8859_1, HttpHeaders.CONTENT_TYPE));
				append(header, FIELD_SEP);
				append(header, encode(StandardCharsets.ISO_8859_1, producer.getContentType()));
				append(header, CR_LF);
			}
		}
		append(header, CR_LF);
		return header;
	}

	private static void append(ByteArrayBuffer target, ByteArrayBuffer source)
	{
		target.append(source.array(), 0, source.length());
	}

	private void initContentLength()
	{
		if (this.contentLength == -2)
		{
			long totalContentLength = 0;
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
					// measure the exact same header buffer that produce() writes so the length cannot drift
					totalContentLength += buildPartHeader(producer.producer, producer.name, producer.fileName, producer.writeContentType).length();
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
	boolean writeContentType = false;
	String name;
	String fileName;

	public InnerMultiPartAsyncProducer(AsyncEntityProducer producer, String name, String fileName, boolean writeContentType)
	{
		this.producer = producer;
		this.name = name;
		this.fileName = fileName;
		this.writeContentType = writeContentType;
	}
}
