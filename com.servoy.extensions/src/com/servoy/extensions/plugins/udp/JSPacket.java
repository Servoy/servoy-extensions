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

import java.io.UTFDataFormatException;
import java.net.DatagramPacket;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * <p>The UDP packet class facilitates handling packet data through a set of
 * properties and methods designed for reading, writing, and managing byte
 * arrays. It provides access to metadata such as the originating host, port,
 * and packet length. The <code>index</code> property tracks the current
 * position in the byte array, which is automatically updated after each read
 * or write operation.</p>
 *
 * <p>Methods include the ability to read and write various data types such
 * as bytes, integers, shorts, and UTF-8 strings. Additionally, the class supports
 * extracting the entire byte array of the packet and encoding custom data
 * for transmission. Advanced use cases involve combining these capabilities
 * for constructing or parsing complex packet structures.</p>
 */
@ServoyDocumented(scriptingName = "JSPacket")
public class JSPacket implements IScriptable
{
	static final int MAX_PACKET_LENGTH = 64000;
	private final DatagramPacket enclosed;
	private int index = 0;
	private int writtenBytes = 0;

	public JSPacket()
	{
		enclosed = new DatagramPacket(new byte[MAX_PACKET_LENGTH], MAX_PACKET_LENGTH);
	}

	public JSPacket(DatagramPacket dp)
	{
		enclosed = dp;
		writtenBytes = dp.getLength();
	}

	/**
	 * Returns the current position in the byte array of the packet. The next read/write operation will occur at this position.
	 * This is a 0 based index.
	 *
	 * @sample
	 * var packet;
	 * while (packet = plugins.udp.getReceivedPacket()) {
	 * 	application.output('packet received from ' + packet.getHost() + ':' + packet.getPort());
	 * 	if (packet.getLength() > 0) {
	 * 		application.output('an int is: ' + packet.readInt());
	 * 		application.output('moved to index: ' + packet.index);
	 * 		application.output('a short is: ' + packet.readShort());
	 * 		application.output('moved to index: ' + packet.index);
	 * 		application.output('a byte is: ' + packet.readByte());
	 * 		application.output('moved to index: ' + packet.index);
	 * 		application.output('a byte is: ' + packet.readByte());
	 * 		application.output('moved to index: ' + packet.index);
	 * 	}
	 * 	else {
	 * 		application.output('end of communication.');
	 * 		break;
	 * 	}
	 * }
	 *
	 * @return The zero-based index indicating the current read/write position in the packet's byte array.
	 */
	public int js_getIndex()
	{
		return getIndex();
	}

	public void js_setIndex(int i)
	{
		setIndex(i);
	}

	/**
	 * Returns the name of the host that sent the packet.
	 *
	 * @sample
	 * var packet;
	 * while (packet = plugins.udp.getReceivedPacket()) {
	 * 	application.output('packet received from ' + packet.getHost() + ':' + packet.getPort());
	 * 	if (packet.getLength() > 0) {
	 * 		application.output('message is: ' + packet.readUTF());
	 * 	}
	 * 	else {
	 * 		application.output('end of communication.');
	 * 		break;
	 * 	}
	 * }
	 *
	 * @return The hostname or IP address from which this packet was sent.
	 */
	public String js_getHost()
	{
		return getHost();
	}

	/**
	 * Returns the content of the package into a byte array.
	 *
	 * @sample
	 * var packet;
	 * while (packet = plugins.udp.getReceivedPacket()) {
	 * 	application.output('packet received from ' + packet.getHost() + ':' + packet.getPort());
	 * 	if (packet.getLength() > 0) {
	 * 		var bytes = packet.getByteArray();
	 * 		application.output('received a packet of length: ' + bytes.length);
	 * 		for (var i=0; i<bytes.length; i++)
	 * 			application.output(bytes[i]);
	 * 		}
	 * 	else {
	 * 		application.output('end of communication.');
	 * 		break;
	 * 	}
	 * }
	 *
	 * @return A copy of the packet's payload data as a byte array.
	 */
	public byte[] js_getByteArray()
	{
		return getByteArray();
	}

	/**
	 * Returns the length of the packet in bytes.
	 *
	 * @sampleas js_getHost()
	 *
	 * @return The total number of bytes contained in the packet's payload.
	 */
	public int js_getLength()
	{
		return getLength();
	}

	/**
	 * Returns the port where the packet originated from.
	 *
	 * @sampleas js_getHost()
	 *
	 * @return The port number associated with the sender of this packet.
	 */
	public int js_getPort()
	{
		return getPort();
	}

	/**
	 * Reads an 8 bits byte value from the packet, starting from the current index. Advances the index with one position.
	 *
	 * @sampleas js_getIndex()
	 *
	 * @return A single byte from the current position in the packet, represented as an integer.
	 */
	public int js_readByte()
	{
		return readByte();
	}

	/**
	 * Reads a 32 bits int value from the packet, starting from the current index. Advances the index with 4 positions.
	 *
	 * @sampleas js_getIndex()
	 *
	 * @return A 32-bit integer extracted from the current position in the packet.
	 */
	public int js_readInt()
	{
		return readInt();
	}

	/**
	 * Reads a 32 bits short value from the packet, starting from the current index. Advances the index with 2 positions.
	 *
	 * @sampleas js_getIndex()
	 *
	 * @return A 16-bit short value extracted from the current position in the packet.
	 */
	public int js_readShort()
	{
		return readShort();
	}

	/**
	 * @clonedesc js_readUTF()
	 * @sampleas js_readUTF()
	 *
	 * @param length
	 *
	 * @return A string decoded from the specified number of UTF-8 encoded bytes in the packet.
	 */
	public String js_readUTF(int length)
	{
		return readUTF(Integer.valueOf(length));
	}

	/**
	 * Reads a UTF-8 string from the packet, starting from the current index. If an argument is specified, then it represents the length (in UTF-8 encoded bytes, not characters) of the string to read. If no argument is specified, then first a 32 bits (4 byte) int is read from the packet and that will be the byte length of the string. Advances the index with a number of positions that depends on the length of the read string.
	 *
	 * @sampleas js_getHost()
	 *
	 * @return A string decoded from the packet, with its length determined by the first four bytes.
	 */
	public String js_readUTF()
	{
		return readUTF(null);
	}


	/**
	 * Writes one byte into the packet, at the current index. The index is advanced with one position.
	 *
	 * @sample
	 * if (!plugins.udp.startSocket('5555', packetReceived)) {
	 * 	application.output('Failed to start socket.');
	 * } else {
	 * 	var packet = plugins.udp.createNewPacket();
	 * 	packet.writeUTF('hello world!');
	 * 	plugins.udp.sendPacket('localhost', packet, 1234);
	 * 	packet = plugins.udp.createNewPacket();
	 * 	packet.writeByte(0xFF);
	 * 	packet.writeShort(10001);
	 * 	packet.writeInt(2000000001);
	 * 	plugins.udp.sendPacket('localhost', packet, 1234);
	 * 	var imgBytes = plugins.file.readFile('logo.jpg', 1024);
	 * 	packet = plugins.udp.createNewPacket();
	 * 	packet.writeBytes(imgBytes);
	 * 	plugins.udp.sendPacket('localhost', packet, 1234);
	 * 	plugins.udp.stopSocket();
	 * }
	 *
	 * @param number
	 */
	public void js_writeByte(int number)
	{
		writeByte(number);
	}

	/**
	 * Writes an array of bytes into the packet, at the current index. The index is advanced with a number of positions equal to the length of the written array.
	 *
	 * @sampleas js_writeByte(int)
	 *
	 * @param bytes
	 */
	public void js_writeBytes(byte[] bytes)
	{
		writeBytes(bytes);
	}

	/**
	 * Writes a 32 bits int into the packet, at the current index. The index is advances with 4 positions.
	 *
	 * @sampleas js_writeByte(int)
	 *
	 * @param number
	 */
	public void js_writeInt(int number)
	{
		writeInt(number);
	}

	/**
	 * Writes a 16 bits short value into the packet, at the current index. The index is advances with 2 positions.
	 *
	 * @sampleas js_writeByte(int)
	 *
	 * @param number
	 */
	public void js_writeShort(int number)
	{
		writeShort(number);
	}

	/**
	 * Writes an UTF-8 encoded string into the packet, at the current index. First the length of the string is written on 4 bytes, then the string is written. The index is advanced with a number of positions equal to the length of the string plus 4.
	 *
	 * @sampleas js_writeByte(int)
	 *
	 * @param string
	 *
	 * @return The total number of bytes written to the packet for the UTF-8 encoded string.
	 */
	public int js_writeUTF(String string)
	{
		return writeUTF(string);
	}

	DatagramPacket getRealPacket()
	{
		enclosed.setLength(index);
		return enclosed;
	}

	public void writeInt(int v)
	{
		byte[] buffer = enclosed.getData();
		buffer[index++] = (byte)(v >>> 24);
		buffer[index++] = (byte)(v >>> 16);
		buffer[index++] = (byte)(v >>> 8);
		buffer[index++] = (byte)(v >>> 0);
		if (index > writtenBytes) writtenBytes = index;
	}

	public int readInt()
	{
		byte[] buffer = enclosed.getData();
		return (((buffer[index++] & 0xFF) << 24) + ((buffer[index++] & 0xFF) << 16) + ((buffer[index++] & 0xFF) << 8) + ((buffer[index++] & 0xFF) << 0));
	}

	public byte[] getByteArray()
	{
		byte[] retval = new byte[writtenBytes];
		System.arraycopy(enclosed.getData(), 0, retval, 0, writtenBytes);
		return retval;
	}

	public void writeBytes(byte[] bytes)
	{
		byte[] buffer = enclosed.getData();
		if (bytes == null || (index + bytes.length) > buffer.length) return; //safety

		System.arraycopy(bytes, 0, buffer, index, bytes.length);
		index += bytes.length;
		if (index > writtenBytes) writtenBytes = index;
	}

	public void writeByte(int v)
	{
		byte[] buffer = enclosed.getData();
		buffer[index++] = (byte)(v >>> 0);
		if (index > writtenBytes) writtenBytes = index;
	}

	public int readByte()
	{
		byte[] buffer = enclosed.getData();
		return ((buffer[index++] & 0xFF) << 0);
	}

	public int readShort()
	{
		byte[] buffer = enclosed.getData();
		return ((buffer[index++] & 0xFF) << 8) + ((buffer[index++] & 0xFF) << 0);
	}

	public void writeShort(int v)
	{
		byte[] buffer = enclosed.getData();
		buffer[index++] = (byte)(v >>> 8);
		buffer[index++] = (byte)(v >>> 0);
		if (index > writtenBytes) writtenBytes = index;
	}

	public int writeUTF(String str)
	{
		int strlen = str.length();
		int utflen = 0;
		char[] charr = new char[strlen];
		int c = 0;
		str.getChars(0, strlen, charr, 0);
		for (int i = 0; i < strlen; i++)
		{
			c = charr[i];
			if ((c >= 0x0001) && (c <= 0x007F))
			{
				utflen++;
			}
			else if (c > 0x07FF)
			{
				utflen += 3;
			}
			else
			{
				utflen += 2;
			}
		}

		byte[] bytearr = enclosed.getData();
		if (utflen + index >= bytearr.length)
		{
			return 0;
		}

		writeInt(utflen);

		for (int i = 0; i < strlen; i++)
		{
			c = charr[i];
			if ((c >= 0x0001) && (c <= 0x007F))
			{
				bytearr[index++] = (byte)c;
			}
			else if (c > 0x07FF)
			{
				bytearr[index++] = (byte)(0xE0 | ((c >> 12) & 0x0F));
				bytearr[index++] = (byte)(0x80 | ((c >> 6) & 0x3F));
				bytearr[index++] = (byte)(0x80 | ((c >> 0) & 0x3F));
			}
			else
			{
				bytearr[index++] = (byte)(0xC0 | ((c >> 6) & 0x1F));
				bytearr[index++] = (byte)(0x80 | ((c >> 0) & 0x3F));
			}
		}
		if (index > writtenBytes) writtenBytes = index;
		return utflen + 2;
	}

	public String readUTF(Integer length)
	{
		try
		{
			int utflen = 0;
			if (length == null)
			{
				utflen = readInt();//in.readUnsignedShort();
			}
			else
			{
				utflen = length.intValue();
			}

			if (utflen > 0 && (index + utflen) <= enclosed.getLength())
			{
				byte[] bytearr = enclosed.getData();
				char[] chararr = new char[utflen];

				int c, char2, char3;
				int count = index;
				int chararr_count = 0;
				//		in.readFully(bytearr, 0, utflen);
				while (count < utflen + index)
				{
					c = bytearr[count] & 0xff;
					if (c > 127) break;
					count++;
					chararr[chararr_count++] = (char)c;
				}
				while (count < utflen + index)
				{
					c = bytearr[count] & 0xff;
					switch (c >> 4)
					{
						case 0 :
						case 1 :
						case 2 :
						case 3 :
						case 4 :
						case 5 :
						case 6 :
						case 7 :
							/* 0xxxxxxx */
							count++;
							chararr[chararr_count++] = (char)c;
							break;
						case 12 :
						case 13 :
							/* 110x xxxx 10xx xxxx */
							count += 2;
							if (count - index > utflen) throw new UTFDataFormatException("malformed input: partial character at end"); //$NON-NLS-1$
							char2 = bytearr[count - 1];
							if ((char2 & 0xC0) != 0x80) throw new UTFDataFormatException("malformed input around byte " + count); //$NON-NLS-1$
							chararr[chararr_count++] = (char)(((c & 0x1F) << 6) | (char2 & 0x3F));
							break;
						case 14 :
							/* 1110 xxxx 10xx xxxx 10xx xxxx */
							count += 3;
							if (count > utflen) throw new UTFDataFormatException("malformed input: partial character at end"); //$NON-NLS-1$
							char2 = bytearr[count - 2];
							char3 = bytearr[count - 1];
							if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) throw new UTFDataFormatException(
								"malformed input around byte " + (count - 1)); //$NON-NLS-1$
							chararr[chararr_count++] = (char)(((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
							break;
						default :
							/* 10xx xxxx, 1111 xxxx */
							throw new UTFDataFormatException("malformed input around byte " + count); //$NON-NLS-1$
					}
				}
				index += utflen;
				// The number of chars produced may be less than utflen
				return new String(chararr, 0, chararr_count);
			}
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		return null;
	}

	public int getLength()
	{
		return writtenBytes;
	}

	public int getIndex()
	{
		return index;
	}

	public void setIndex(int i)
	{
		if (i < writtenBytes)
		{
			index = i;
		}

		int buflen = enclosed.getData().length;
		if (index > buflen) //safety
		{
			index = buflen;
		}
	}

	public String getHost()
	{
		return enclosed.getAddress().toString().substring(1);
	}

	public int getPort()
	{
		return enclosed.getPort();
	}

	public static void main(String[] args)
	{
		JSPacket p = new JSPacket();
		System.out.println(p.index);
		p.enclosed.getData()[0] = (byte)-1;
		p.enclosed.getData()[1] = (byte)0;
		p.enclosed.getData()[2] = (byte)-1;
		p.enclosed.getData()[3] = (byte)-1;
		System.out.println(p.readInt());
		System.out.println(p.index);
	}
}
