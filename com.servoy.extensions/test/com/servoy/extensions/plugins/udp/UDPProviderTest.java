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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * The udp scripting API. Sockets are bound to the loopback address on a free (ephemeral) port; the one packet that
 * is sent goes to a loopback socket of the test itself. Nothing leaves the machine.
 */
public class UDPProviderTest
{
	private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

	private IClientPluginAccess access;
	private UDPPlugin plugin;
	private UDPProvider provider;
	private DatagramSocket receiver;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IClientPluginAccess.class);
		plugin = new UDPPlugin();
		plugin.load();
		plugin.initialize(access);
		provider = (UDPProvider)plugin.getScriptObject();
	}

	@AfterEach
	public void tearDown() throws Exception
	{
		provider.js_stopSocket();
		plugin.unload();
		if (receiver != null) receiver.close();
	}

	@SuppressWarnings("unchecked")
	private List<UDPSocket> sockets() throws Exception
	{
		Field field = UDPPlugin.class.getDeclaredField("sockets");
		field.setAccessible(true);
		return (List<UDPSocket>)field.get(plugin);
	}

	private byte[] receive() throws Exception
	{
		DatagramPacket dp = new DatagramPacket(new byte[100], 100);
		receiver.receive(dp);
		return Arrays.copyOf(dp.getData(), dp.getLength());
	}

	private DatagramSocket receiver() throws Exception
	{
		receiver = new DatagramSocket(0, LOOPBACK);
		receiver.setSoTimeout(5000);
		return receiver;
	}

	// --- plugin ---

	@Test
	public void pluginDescription()
	{
		assertEquals("udp", plugin.getName());
		assertEquals("UDP packets plugin", plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertNull(plugin.getPreferencePanels());
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertSame(provider, plugin.getScriptObject());
		assertEquals(List.of(JSPacket.class, UDPSocket.class), List.of(provider.getAllReturnedTypes()));
		assertNotNull(new UDPProvider());
		assertEquals(0, provider.js_createNewPacket().js_getLength());
	}

	// --- sockets ---

	@Test
	public void aCreatedSocketIsRegisteredUntilClosed() throws Exception
	{
		UDPSocket socket = provider.createSocket(0, LOOPBACK);

		assertEquals(List.of(socket), sockets());

		socket.close();

		assertTrue(sockets().isEmpty());
	}

	@Test
	public void aSocketSendsToALoopbackReceiver() throws Exception
	{
		int port = receiver().getLocalPort();
		UDPSocket socket = provider.createSocket(0, LOOPBACK);
		JSPacket packet = provider.js_createNewPacket();
		packet.js_writeUTF("ping");

		assertTrue(socket.sendPacket("127.0.0.1", packet, port));

		assertArrayEquals(new byte[] { 0, 0, 0, 4, 'p', 'i', 'n', 'g' }, receive());
		socket.close();
	}

	@Test
	public void aPortInUseCannotBeBound() throws Exception
	{
		int port = receiver().getLocalPort();

		assertThrows(RuntimeException.class, () -> provider.createSocket(port, LOOPBACK));
	}

	@Test
	public void addresses() throws Exception
	{
		assertTrue(provider.getAllInetAddresses().length > 0);
		assertEquals(LOOPBACK, provider.getInetAddress("127.0.0.1"));
		// an invalid ipv6 literal fails without a dns lookup
		assertNull(provider.getInetAddress("[::zz]"));
	}

	// --- the provider's own socket ---

	@Test
	public void startAndStopTheProviderSocket() throws Exception
	{
		assertTrue(provider.js_startSocket(0, UDPSocketTest.callback("onPacket")));
		// already started
		assertFalse(provider.js_startSocket(0, UDPSocketTest.callback("onPacket")));

		provider.js_stopSocket();

		assertTrue(provider.js_startSocket(0, UDPSocketTest.callback("onPacket")));
	}

	@Test
	public void startNeedsAFunction()
	{
		assertThrows(IllegalArgumentException.class, () -> provider.js_startSocket(0, "not a function"));
	}

	@Test
	public void aPortInUseCannotBeStarted() throws Exception
	{
		int port = receiver().getLocalPort();

		assertFalse(provider.js_startSocket(port, UDPSocketTest.callback("onPacket")));
	}

	@Test
	public void theProviderSocketSendsToALoopbackReceiver() throws Exception
	{
		int port = receiver().getLocalPort();
		provider.js_startSocket(0, UDPSocketTest.callback("onPacket"));
		JSPacket packet = provider.js_createNewPacket();
		packet.js_writeInt(42);

		assertTrue(provider.js_sendPacket("127.0.0.1", packet, port));

		assertArrayEquals(new byte[] { 0, 0, 0, 42 }, receive());
	}

	@Test
	public void sendingNeedsAStartedSocket() throws Exception
	{
		assertFalse(provider.js_sendPacket("127.0.0.1", provider.js_createNewPacket()));
		assertFalse(provider.js_sendPacket(null, provider.js_createNewPacket(), 1));
		assertFalse(provider.js_sendPacket("127.0.0.1", null, 1));
		assertFalse(provider.js_sendPacket("[::zz]", provider.js_createNewPacket(), 1));
	}

	@Test
	public void testPacketsAreBufferedAndTheCallbackRunsWhenTheBufferWasEmpty() throws Exception
	{
		provider.js_startSocket(0, UDPSocketTest.callback("onPacket"));
		JSPacket one = provider.js_createNewPacket();
		one.js_writeByte(1);
		JSPacket two = provider.js_createNewPacket();
		two.js_writeByte(2);

		assertFalse(provider.js_testPacket(one));
		provider.js_testPacket(two);
		provider.js_testPacket(null);

		verify(access, times(1)).executeMethod(eq("scopes.globals"), eq("onPacket"), isNull(), eq(true));
		assertEquals(1, provider.js_getReceivedPacket().js_readByte());
		assertEquals(2, provider.js_getRecievedPacket().js_readByte());
		assertNull(provider.js_getReceivedPacket());
		provider.js_testPacket(one);
		verify(access, times(2)).executeMethod(eq("scopes.globals"), eq("onPacket"), any(), eq(true));
	}

	@Test
	public void beforeStartThereIsNoBuffer()
	{
		// current behaviour (bug): the buffer is only created by startSocket
		assertThrows(NullPointerException.class, provider::js_getReceivedPacket);
		assertThrows(NullPointerException.class, () -> provider.js_testPacket(provider.js_createNewPacket()));
	}

	@Test
	public void closingTheSolutionStopsTheProviderSocket() throws Exception
	{
		provider.js_startSocket(0, UDPSocketTest.callback("onPacket"));

		plugin.propertyChange(new PropertyChangeEvent(this, "solution", "sol", null));

		assertTrue(provider.js_startSocket(0, UDPSocketTest.callback("onPacket")));
		plugin.propertyChange(new PropertyChangeEvent(this, "solution", null, "sol"));
		plugin.propertyChange(new PropertyChangeEvent(this, "other", "sol", null));
		assertFalse(provider.js_startSocket(0, UDPSocketTest.callback("onPacket")));
	}
}
