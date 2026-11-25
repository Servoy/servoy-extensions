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
package com.servoy.extensions.plugins.udp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Reading and writing the binary content of a UDP packet. Pure in memory.
 */
public class JSPacketTest
{
	/** A packet as the socket delivers it: the sender address has no host name. */
	private static JSPacket received(byte[] data)
	{
		try
		{
			return new JSPacket(new DatagramPacket(data, data.length, InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), 4321));
		}
		catch (java.net.UnknownHostException e)
		{
			throw new IllegalStateException(e);
		}
	}

	@Test
	public void numbersAreWrittenBigEndianAndReadBack()
	{
		JSPacket packet = new JSPacket();
		packet.js_writeByte(0xAB);
		packet.js_writeShort(0x1234);
		packet.js_writeInt(-2);

		assertEquals(7, packet.js_getLength());
		assertEquals(7, packet.js_getIndex());
		assertArrayEquals(new byte[] { (byte)0xAB, 0x12, 0x34, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFE }, packet.js_getByteArray());

		JSPacket read = received(packet.js_getByteArray());
		assertEquals(0xAB, read.js_readByte());
		assertEquals(0x1234, read.js_readShort());
		assertEquals(-2, read.js_readInt());
	}

	@Test
	public void bytesAreAppendedAtTheIndex()
	{
		JSPacket packet = new JSPacket();
		packet.js_writeBytes(new byte[] { 1, 2, 3 });
		packet.js_setIndex(1);
		packet.js_writeBytes(new byte[] { 9 });

		// overwriting does not change the length
		assertArrayEquals(new byte[] { 1, 9, 3 }, packet.js_getByteArray());
		assertEquals(2, packet.js_getIndex());
	}

	@Test
	public void tooManyOrNoBytesAreIgnored()
	{
		JSPacket packet = new JSPacket();

		packet.js_writeBytes(null);
		packet.js_writeBytes(new byte[JSPacket.MAX_PACKET_LENGTH + 1]);

		assertEquals(0, packet.js_getLength());
	}

	@Test
	public void theIndexCanOnlyBeSetInsideTheWrittenData()
	{
		JSPacket packet = new JSPacket();
		packet.js_writeInt(1);

		packet.js_setIndex(2);
		assertEquals(2, packet.js_getIndex());
		packet.js_setIndex(10);
		assertEquals(2, packet.js_getIndex());
	}

	@Test
	public void theSentPacketEndsAtTheIndex()
	{
		JSPacket packet = new JSPacket();
		packet.js_writeInt(1);
		packet.js_writeInt(2);
		packet.js_setIndex(4);

		assertEquals(4, packet.getRealPacket().getLength());
	}

	@Test
	public void asciiAndTwoByteStringsRoundTrip()
	{
		JSPacket packet = new JSPacket();
		packet.js_writeUTF("hello");
		packet.js_writeUTF("caf\u00e9");

		JSPacket read = received(packet.js_getByteArray());
		assertEquals("hello", read.js_readUTF());
		assertEquals("caf\u00e9", read.js_readUTF());
		assertEquals(packet.js_getLength(), read.js_getIndex());
	}

	@Test
	public void writeUTFWritesTheLengthOnFourBytes()
	{
		JSPacket packet = new JSPacket();

		int written = packet.js_writeUTF("abc");

		assertArrayEquals(new byte[] { 0, 0, 0, 3, 'a', 'b', 'c' }, packet.js_getByteArray());
		// current behaviour (bug): the documented return is the string length plus 4, the code returns plus 2
		assertEquals(5, written);
	}

	@Test
	public void threeByteCharactersCannotBeReadBack()
	{
		JSPacket packet = new JSPacket();
		packet.js_writeUTF("\u20ac");

		JSPacket read = received(packet.js_getByteArray());

		// current behaviour (bug): the partial character check compares the absolute position with the string length
		// (count > utflen instead of count - index > utflen), so any 3 byte character after the length prefix fails
		assertNull(read.js_readUTF());
	}

	@Test
	public void aStringThatDoesNotFitIsNotWritten()
	{
		JSPacket packet = new JSPacket();
		packet.js_writeBytes(new byte[JSPacket.MAX_PACKET_LENGTH - 2]);

		assertEquals(0, packet.js_writeUTF("abc"));
		assertEquals(JSPacket.MAX_PACKET_LENGTH - 2, packet.js_getLength());
	}

	@Test
	public void aStringOfAGivenLength()
	{
		JSPacket read = received("abcdef".getBytes(StandardCharsets.US_ASCII));

		assertEquals("abc", read.js_readUTF(3));
		assertEquals("def", read.js_readUTF(3));
		// nothing left
		assertNull(read.js_readUTF(1));
	}

	@Test
	public void twoByteCharactersOfAGivenLength()
	{
		JSPacket read = received("\u00e9t\u00e9".getBytes(StandardCharsets.UTF_8));

		assertEquals("\u00e9t\u00e9", read.js_readUTF(5));
	}

	@Test
	public void malformedInputIsNull()
	{
		assertNull(received(new byte[] { (byte)0x80, 'a' }).js_readUTF(2));
		assertNull(received(new byte[] { (byte)0xC3, 'a' }).js_readUTF(2));
		assertNull(received(new byte[] { 'a', (byte)0xC3 }).js_readUTF(2));
		assertNull(received(new byte[] { 0, 0, 0, 0 }).js_readUTF());
	}

	@Test
	public void hostAndPortOfAReceivedPacket()
	{
		JSPacket read = received(new byte[] { 1 });

		assertEquals("127.0.0.1", read.js_getHost());
		assertEquals(4321, read.js_getPort());
		assertEquals(1, read.js_getLength());
	}

	@Test
	public void anAddressWithAHostNameIsCutOff()
	{
		JSPacket packet = new JSPacket(new DatagramPacket(new byte[1], 1, InetAddress.getLoopbackAddress(), 1));

		// current behaviour (bug): the host is InetAddress.toString() without its first character, that only works
		// for "/1.2.3.4"; an address with a host name ("localhost/127.0.0.1") loses its first letter
		assertEquals("ocalhost/127.0.0.1", packet.js_getHost());
	}

	@Test
	public void aNewPacketHasNoHost()
	{
		JSPacket packet = new JSPacket();

		assertEquals(0, packet.js_getPort());
		assertThrows(NullPointerException.class, packet::js_getHost);
	}
}
