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
package com.servoy.extensions.plugins.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.junit.Test;

/**
 * SVY-21518 - the http plugin's {@link MultiPartEntityProducer} must honour the httpcore5
 * {@link DataStreamChannel#write(ByteBuffer)} partial-write contract so that a multipart body stays
 * well-formed over HTTP/2 (where the channel frequently accepts fewer bytes than offered, or zero).
 *
 * <p>These tests drive the producer with hand-written {@link DataStreamChannel} stubs - no Mockito,
 * following the house style of {@code RestWSServletMultipartDetectionTest} (unused interface methods
 * throw). The short-write stubs reassemble every byte the producer emits and the tests assert the
 * framing is intact (boundaries and part headers written exactly once, not truncated/duplicated),
 * the reassembled length equals {@link MultiPartEntityProducer#getContentLength()} byte-for-byte, and
 * the BROWSER_COMPATIBLE {@code Content-Type}-only-for-files header policy is preserved. The same
 * assertions must also hold with a full-accept stub so the fix did not merely move the regression.
 */
public class MultiPartEntityProducerTest
{
	private static final String OCTET = "application/octet-stream";

	/** Guard so a mis-behaving produce()/requestOutput() loop fails the test instead of hanging. */
	private static final int MAX_ITERATIONS = 100000;

	/**
	 * AC1 + AC2 + AC3: a short-write stub (N bytes per write, with periodic zero writes re-driven via
	 * requestOutput/produce) drives a mix of a plain string parameter part and two file/byte parts to
	 * completion. The reassembled bytes must be a well-formed multipart body, its length must equal
	 * getContentLength() exactly, and only the file parts carry a Content-Type line.
	 */
	@Test
	public void wellFormedMultipartUnderShortWrites() throws IOException
	{
		MultiPartEntityProducer producer = buildMixedProducer();
		long advertised = producer.getContentLength();

		byte[] body = drive(producer, new ShortWriteChannel(3, 7));

		assertEquals("reassembled body length must equal the advertised Content-Length byte-for-byte", advertised, body.length);
		assertWellFormed(body, producer.boundary);
	}

	/**
	 * AC4: the exact same assertions must hold with a full-accept stub (writes everything in one go),
	 * i.e. the HTTP/1.1-style path where every buffer drains in a single write - proving the fix did
	 * not only shift the bug onto the back-pressured path.
	 */
	@Test
	public void wellFormedMultipartUnderFullAccept() throws IOException
	{
		MultiPartEntityProducer producer = buildMixedProducer();
		long advertised = producer.getContentLength();

		byte[] body = drive(producer, new FullAcceptChannel());

		assertEquals("full-accept path must also match the advertised Content-Length exactly", advertised, body.length);
		assertWellFormed(body, producer.boundary);
	}

	/**
	 * AC5 (the exact regression): a stub that accepts only 1 byte per write forces the very first part
	 * header to span many produce() calls. The header must be emitted exactly once (not re-built and
	 * duplicated when produce() resumes), the body must stay intact and the total length must still
	 * equal getContentLength().
	 */
	@Test
	public void partHeaderWrittenExactlyOnceWhenHeaderSpansMultipleProduceCalls() throws IOException
	{
		MultiPartEntityProducer producer = buildMixedProducer();
		long advertised = producer.getContentLength();

		byte[] body = drive(producer, new ShortWriteChannel(1, 1));

		assertEquals("one-byte-at-a-time path must still match the advertised Content-Length exactly", advertised, body.length);
		assertWellFormed(body, producer.boundary);

		String text = new String(body, StandardCharsets.ISO_8859_1);
		assertEquals("the string-parameter part header must appear exactly once (not duplicated by a resumed produce())", 1,
			countOccurrences(text, "name=\"field\""));
		assertEquals("the first file part header must appear exactly once", 1, countOccurrences(text, "filename=\"a.bin\""));
		assertEquals("the second file part header must appear exactly once", 1, countOccurrences(text, "filename=\"b.txt\""));
	}

	/**
	 * AC3 in isolation: a plain string parameter added with writeContentType == false must NOT emit a
	 * Content-Type header, while a file part (writeContentType == true) must. Driven under short writes.
	 */
	@Test
	public void contentTypeOnlyForFileParts() throws IOException
	{
		MultiPartEntityProducer producer = buildMixedProducer();
		producer.getContentLength();

		byte[] body = drive(producer, new ShortWriteChannel(2, 5));
		String text = new String(body, StandardCharsets.ISO_8859_1);

		int field = text.indexOf("name=\"field\"");
		int firstFile = text.indexOf("filename=\"a.bin\"");
		assertTrue("string-parameter part must come before the file part in this fixture", field >= 0 && firstFile > field);

		String fieldPart = text.substring(field, firstFile);
		assertFalse("a plain string parameter (writeContentType == false) must NOT emit a Content-Type header",
			fieldPart.toLowerCase().contains("content-type:"));

		int firstFileHeaderEnd = text.indexOf("\r\n\r\n", firstFile);
		String fileHeader = text.substring(firstFile, firstFileHeaderEnd);
		assertTrue("a file part (writeContentType == true) must emit a Content-Type header",
			fileHeader.toLowerCase().contains("content-type:"));
	}

	// ---- fixture ----------------------------------------------------------------------------------

	/**
	 * Mirrors the real call sites in {@code BaseEntityEnclosingRequest#buildEntityProducer}: a string
	 * parameter is a {@link BasicAsyncEntityProducer} added with name only and writeContentType==false;
	 * each file is a {@link BasicAsyncEntityProducer} added with name + fileName and writeContentType==true.
	 * All inner producers have a known content length, so getContentLength() is deterministic.
	 */
	private static MultiPartEntityProducer buildMixedProducer()
	{
		MultiPartEntityProducer producer = new MultiPartEntityProducer();
		producer.addProducer(new BasicAsyncEntityProducer("hello world value".getBytes(StandardCharsets.ISO_8859_1),
			ContentType.create("text/plain", StandardCharsets.ISO_8859_1)), "field", null, false);
		byte[] bin = new byte[500];
		for (int i = 0; i < bin.length; i++)
		{
			bin[i] = (byte)(i % 256);
		}
		producer.addProducer(new BasicAsyncEntityProducer(bin, ContentType.create(OCTET)), "file1", "a.bin", true);
		producer.addProducer(
			new BasicAsyncEntityProducer("second file contents, a bit longer than the first".getBytes(StandardCharsets.ISO_8859_1),
				ContentType.create("text/plain", StandardCharsets.ISO_8859_1)),
			"file2", "b.txt", true);
		return producer;
	}

	// ---- driving ----------------------------------------------------------------------------------

	/** Loops produce() until the stub observes endStream(), guarding against a busy-spin/infinite loop. */
	private static byte[] drive(MultiPartEntityProducer producer, RecordingChannel channel) throws IOException
	{
		int iterations = 0;
		while (!channel.ended)
		{
			if (++iterations > MAX_ITERATIONS)
			{
				fail("producer did not finish within " + MAX_ITERATIONS +
					" produce() calls - likely a back-pressure busy-spin or infinite loop");
			}
			producer.produce(channel);
		}
		return channel.collected();
	}

	// ---- assertions -------------------------------------------------------------------------------

	/**
	 * Asserts the reassembled bytes form a well-formed BROWSER_COMPATIBLE multipart body for this
	 * fixture: starts with --boundary CRLF, each part header appears exactly once with the right
	 * Content-Disposition, there is a blank line before each body, and it ends with --boundary-- CRLF.
	 */
	private static void assertWellFormed(byte[] body, String boundary)
	{
		String text = new String(body, StandardCharsets.ISO_8859_1);
		String delimiter = "--" + boundary;

		assertTrue("multipart body must start with the opening boundary --<boundary>CRLF", text.startsWith(delimiter + "\r\n"));
		assertTrue("multipart body must end with the closing boundary --<boundary>--CRLF", text.endsWith(delimiter + "--\r\n"));

		// a delimiter occurs once per part plus once for the closing boundary; the fixture has 3 parts
		assertEquals("there must be exactly one boundary delimiter per part plus the closing one (no duplicated/dropped boundaries)", 4,
			countOccurrences(text, delimiter));

		assertEquals("the string parameter part must have exactly one Content-Disposition header", 1,
			countOccurrences(text, "Content-Disposition: form-data; name=\"field\"\r\n"));
		assertEquals("the first file part must have exactly one Content-Disposition header with filename", 1,
			countOccurrences(text, "Content-Disposition: form-data; name=\"file1\"; filename=\"a.bin\"\r\n"));
		assertEquals("the second file part must have exactly one Content-Disposition header with filename", 1,
			countOccurrences(text, "Content-Disposition: form-data; name=\"file2\"; filename=\"b.txt\"\r\n"));

		// every part header ends with a blank line (CRLF CRLF) before the part body
		assertTrue("there must be a blank line separating headers from the part body", text.contains("\r\n\r\n"));

		// the actual body bytes must survive intact
		assertTrue("string parameter value must be present intact", text.contains("hello world value"));
		assertTrue("second file contents must be present intact", text.contains("second file contents, a bit longer than the first"));
	}

	private static int countOccurrences(String haystack, String needle)
	{
		int count = 0;
		int from = 0;
		while (true)
		{
			int idx = haystack.indexOf(needle, from);
			if (idx < 0) break;
			count++;
			from = idx + needle.length();
		}
		return count;
	}

	// ---- channel stubs ----------------------------------------------------------------------------

	/**
	 * Base recording {@link DataStreamChannel} that collects every byte the producer writes and records
	 * endStream(). requestOutput() just flags that produce() should be re-driven - the test harness loops
	 * produce() so no real async engine is needed. All methods not used by the producer throw.
	 */
	private abstract static class RecordingChannel implements DataStreamChannel
	{
		final ByteArrayOutputStream sink = new ByteArrayOutputStream();
		boolean ended = false;
		boolean outputRequested = false;

		byte[] collected()
		{
			return sink.toByteArray();
		}

		@Override
		public void requestOutput()
		{
			outputRequested = true;
		}

		@Override
		public void endStream() throws IOException
		{
			ended = true;
		}

		@Override
		public void endStream(List< ? extends Header> trailers) throws IOException
		{
			endStream();
		}
	}

	/** Accepts the whole buffer in a single write - the HTTP/1.1-style path (AC4). */
	private static final class FullAcceptChannel extends RecordingChannel
	{
		@Override
		public int write(ByteBuffer src)
		{
			int n = src.remaining();
			byte[] chunk = new byte[n];
			src.get(chunk);
			sink.write(chunk, 0, n);
			return n;
		}
	}

	/**
	 * Partial-write stub: accepts at most {@code maxPerWrite} bytes per write, and returns 0 (back-pressure)
	 * once every {@code zeroEvery} successful writes to exercise the requestOutput/produce re-drive path.
	 * It always eventually accepts bytes so the drive() loop terminates.
	 */
	private static final class ShortWriteChannel extends RecordingChannel
	{
		private final int maxPerWrite;
		private final int zeroEvery;
		private int writeCalls = 0;

		ShortWriteChannel(int maxPerWrite, int zeroEvery)
		{
			this.maxPerWrite = Math.max(1, maxPerWrite);
			this.zeroEvery = Math.max(1, zeroEvery);
		}

		@Override
		public int write(ByteBuffer src)
		{
			writeCalls++;
			if (zeroEvery > 1 && writeCalls % zeroEvery == 0)
			{
				// simulate HTTP/2 flow-control back-pressure: accept nothing this time
				return 0;
			}
			int n = Math.min(maxPerWrite, src.remaining());
			if (n <= 0) return 0;
			byte[] chunk = new byte[n];
			src.get(chunk);
			sink.write(chunk, 0, n);
			return n;
		}
	}
}
