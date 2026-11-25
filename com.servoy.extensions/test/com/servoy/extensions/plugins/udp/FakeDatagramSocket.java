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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * An unbound DatagramSocket that never touches the network: receive hands out queued packets (or throws queued
 * errors) and closes itself when the queue is empty, send only records.
 */
class FakeDatagramSocket extends DatagramSocket
{
	record Sent(byte[] data, String host, int port)
	{
	}

	final List<Sent> sent = new ArrayList<>();
	private final Deque<Object> incoming = new ArrayDeque<>();
	private volatile boolean closed;
	IOException sendError;

	FakeDatagramSocket() throws SocketException
	{
		super((SocketAddress)null);
	}

	FakeDatagramSocket incoming(byte[] data)
	{
		incoming.add(data);
		return this;
	}

	FakeDatagramSocket incomingError(IOException e)
	{
		incoming.add(e);
		return this;
	}

	@Override
	public synchronized void receive(DatagramPacket p) throws IOException
	{
		Object next = incoming.poll();
		if (next == null)
		{
			closed = true;
			throw new SocketException("Socket closed");
		}
		if (next instanceof IOException e) throw e;
		byte[] data = (byte[])next;
		System.arraycopy(data, 0, p.getData(), 0, data.length);
		p.setLength(data.length);
	}

	@Override
	public void send(DatagramPacket p) throws IOException
	{
		if (sendError != null) throw sendError;
		sent.add(new Sent(Arrays.copyOf(p.getData(), p.getLength()), p.getAddress().getHostAddress(), p.getPort()));
	}

	@Override
	public void close()
	{
		closed = true;
	}

	@Override
	public boolean isClosed()
	{
		return closed;
	}
}
