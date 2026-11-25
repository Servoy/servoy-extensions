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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Function;

import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * UDPSocket and the DatagramHandler receive loop against a {@link FakeDatagramSocket}. The receive loop is run on the
 * test thread, the fake closes itself when it has no more packets.
 */
public class UDPSocketTest
{
	private IClientPluginAccess access;
	private UDPPlugin plugin;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IClientPluginAccess.class);
		plugin = new UDPPlugin();
		plugin.initialize(access);
	}

	static Function callback(String name)
	{
		Function f = mock(Function.class);
		when(f.get("_scopename_", f)).thenReturn("globals");
		when(f.get("_methodname_", f)).thenReturn(name);
		return f;
	}

	private static JSPacket packet(String text)
	{
		JSPacket packet = new JSPacket();
		packet.js_writeUTF(text);
		return packet;
	}

	// --- DatagramHandler ---

	@Test
	public void theReceiveLoopDeliversPacketsUntilTheSocketIsClosed() throws Exception
	{
		FakeDatagramSocket socket = new FakeDatagramSocket().incoming(new byte[] { 1, 2 }).incomingError(new IOException("glitch"))
			.incoming(new byte[] { 3 });
		List<byte[]> received = new ArrayList<>();
		DatagramHandler handler = new DatagramHandler(dp -> received.add(java.util.Arrays.copyOf(dp.getData(), dp.getLength())), socket);

		handler.run();

		// the IOException while open is logged and the loop goes on
		assertEquals(2, received.size());
		assertArrayEquals(new byte[] { 1, 2 }, received.get(0));
		assertArrayEquals(new byte[] { 3 }, received.get(1));
		assertTrue(handler.isDaemon());
	}

	@Test
	public void theHandlerSendsUntilItIsStopped() throws Exception
	{
		FakeDatagramSocket socket = new FakeDatagramSocket();
		DatagramHandler handler = new DatagramHandler(dp -> {
		}, socket);
		DatagramPacket dp = packet("x").getRealPacket();
		dp.setAddress(java.net.InetAddress.getLoopbackAddress());
		dp.setPort(9);

		assertTrue(handler.send(dp));
		socket.sendError = new IOException("full");
		assertFalse(handler.send(dp));

		handler.setListen(false);

		assertTrue(socket.isClosed());
		// after stopping there is no socket any more
		assertFalse(handler.send(dp));
		// stopping twice only logs
		handler.setListen(false);
	}

	// --- UDPSocket ---

	@Test
	public void sendingToAnExplicitPort() throws Exception
	{
		FakeDatagramSocket socket = new FakeDatagramSocket();
		UDPSocket udp = new UDPSocket(socket, plugin);

		assertTrue(udp.sendPacket("127.0.0.1", packet("hi"), 5000));

		assertEquals(1, socket.sent.size());
		assertEquals("127.0.0.1", socket.sent.get(0).host());
		assertEquals(5000, socket.sent.get(0).port());
		assertArrayEquals(new byte[] { 0, 0, 0, 2, 'h', 'i' }, socket.sent.get(0).data());
	}

	@Test
	public void sendingWithoutPortUsesTheRemotePortOfTheSocket() throws Exception
	{
		UDPSocket udp = new UDPSocket(new FakeDatagramSocket(), plugin);

		// current behaviour (bug): documented as "the same port as the socket is bound to", but getPort() is the
		// remote port of a connected socket, -1 for a normal socket, which DatagramPacket rejects
		assertThrows(IllegalArgumentException.class, () -> udp.sendPacket("127.0.0.1", packet("hi")));
	}

	@Test
	public void sendFailures() throws Exception
	{
		FakeDatagramSocket socket = new FakeDatagramSocket();
		UDPSocket udp = new UDPSocket(socket, plugin);

		assertFalse(udp.sendPacket(null, packet("x"), 1));
		assertFalse(udp.sendPacket("127.0.0.1", null, 1));
		// an invalid ipv6 literal fails without a dns lookup
		assertFalse(udp.sendPacket("[::zz]", packet("x"), 1));
		socket.sendError = new IOException("unreachable");
		assertFalse(udp.sendPacket("127.0.0.1", packet("x"), 1));
	}

	@Test
	public void receivedPacketsAreBufferedAndTheCallbackRunsWhenTheBufferWasEmpty() throws Exception
	{
		UDPSocket udp = new UDPSocket(new FakeDatagramSocket(), plugin);
		assertSame(udp, udp.start(callback("onPacket")));

		udp.testPacket(packet("one"));
		udp.testPacket(packet("two"));
		udp.testPacket(null);

		verify(access, times(1)).executeMethod(eq("scopes.globals"), eq("onPacket"), any(), eq(true));
		assertEquals("one", udp.getReceivedPacket().js_readUTF());
		assertEquals("two", udp.getReceivedPacket().js_readUTF());
		assertNull(udp.getReceivedPacket());

		// the buffer was seen empty, the next packet triggers the callback again
		udp.testPacket(packet("three"));
		verify(access, times(2)).executeMethod(eq("scopes.globals"), eq("onPacket"), any(), eq(true));
	}

	@Test
	public void theCallbackGetsTheSocket() throws Exception
	{
		UDPSocket udp = new UDPSocket(new FakeDatagramSocket(), plugin);
		udp.start(callback("onPacket"));
		org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);

		udp.testPacket(packet("x"));

		verify(access).executeMethod(eq("scopes.globals"), eq("onPacket"), args.capture(), eq(true));
		assertArrayEquals(new Object[] { udp }, args.getValue());
	}

	@Test
	public void beforeStartThereIsNoBuffer() throws Exception
	{
		UDPSocket udp = new UDPSocket(new FakeDatagramSocket(), plugin);

		// current behaviour (bug): the buffer is only created by start(), so these fail with a NullPointerException
		assertThrows(NullPointerException.class, udp::getReceivedPacket);
		assertThrows(NullPointerException.class, () -> udp.testPacket(packet("x")));
	}

	@Test
	public void closeBeforeStartClosesTheSocket() throws Exception
	{
		FakeDatagramSocket socket = new FakeDatagramSocket();
		UDPSocket udp = new UDPSocket(socket, plugin);
		plugin.registerSocket(udp);

		udp.close();

		assertTrue(socket.isClosed());
	}

	@Test
	public void closeAfterStartStopsListeningAndDropsTheBuffer() throws Exception
	{
		FakeDatagramSocket socket = new FakeDatagramSocket();
		UDPSocket udp = new UDPSocket(socket, plugin);
		udp.start(callback("onPacket"));
		udp.testPacket(packet("left over"));

		udp.close();

		assertTrue(socket.isClosed());
		assertNull(udp.getReceivedPacket());
	}

	@Test
	public void unloadClosesTheRegisteredSockets() throws Exception
	{
		FakeDatagramSocket socket = new FakeDatagramSocket();
		plugin.registerSocket(new UDPSocket(socket, plugin));

		plugin.unload();

		assertTrue(socket.isClosed());
		assertNull(plugin.getClientPluginAccess());
	}
}
