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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * <p>The <code>UDP</code> plugin facilitates communication using UDP sockets, enabling the
 * sending and receiving of data packets. It supports creating and manipulating custom data
 * packets, starting and stopping sockets, and testing packet handling logic.</p>
 *
 * <p>For creating packets, use <code>createNewPacket</code> to initialize an empty packet and
 * add data such as UTF strings, integers, or bytes. To receive data, use <code>getReceivedPacket</code>,
 * which retrieves packets from the receive buffer until it is empty. To send data, utilize
 * <code>sendPacket</code> by specifying the destination IP or hostname and an optional port.
 * The <code>testPacket</code> function allows for placing test packets in the receive buffer to
 * validate handling methods.</p>
 *
 * <p>Sockets can be managed using <code>startSocket</code> to bind to a specific port and specify
 * a callback method triggered when packets are received. The <code>stopSocket</code> method halts
 * the socket’s operation.</p>
 *
 * <p>This plugin supports practical use cases like real-time data transfer and network
 * communication testing.</p>
 *
 */
@ServoyDocumented(publicName = UDPPlugin.PLUGIN_NAME, scriptingName = "plugins." + UDPPlugin.PLUGIN_NAME)
public class UDPProvider implements IScriptable, IReturnedTypesProvider
{
	private final UDPPlugin plugin;
	private DatagramHandler listner;
	private List<JSPacket> buffer;
	private FunctionDefinition functionDef;
	private int port;
	private boolean hasSeenEmpty;//special flag to prevent starting while lastone is processing last packet

	UDPProvider(UDPPlugin plugin)
	{
		this.plugin = plugin;
	}

	public UDPProvider()
	{
		this.plugin = null;
	}

	/**
	 * Create a new UDPSocket.
	 *
	 * @sample
	 * var socket = plugins.udp.createSocket(4321);
	 * var packet = plugins.udp.createNewPacket()
	 * packet.writeUTF('hello world!')
	 * socket.sendPacket('10.0.0.1',packet, 4321)
	 * socket.close();
	 *
	 * @param port_number the local port that this UDP socket will bind to.
	 *
	 * @return a new UDPSocket instance for sending and/or receiving UDP packets.
	 */
	@JSFunction
	public UDPSocket createSocket(int port_number)
	{
		return createSocket(port_number, null);
	}

	/**
	 * Create a new UDPSocket that is binded to a specific local address.
	 *
	 * @sample
	 * var address = plugins.udp.getAllInetAddresses().find(xxxx);
	 * var socket = plugins.udp.createSocket(4321, address).start(reveive_callback|);
	 * var packet = plugins.udp.createNewPacket()
	 * packet.writeUTF('hello world!')
	 * socket.sendPacket('10.0.0.1',packet, 4321)
	 * socket.close();
	 *
	 * @param port_number the local port that this UDP socket will bind to.
	 * @param laddr the local address that this UDP socket will bind to
	 *
	 * @return a new UDPSocket instance for sending and/or receiving UDP packets.
	 */
	@JSFunction
	public UDPSocket createSocket(int port_number, InetAddress laddr)
	{
		UDPSocket udpSocket;
		try
		{
			DatagramSocket socket = new DatagramSocket(port_number, laddr);
			udpSocket = new UDPSocket(socket, plugin);
			this.plugin.registerSocket(udpSocket);
		}
		catch (SocketException e)
		{
			throw new RuntimeException(e);
		}

		return udpSocket;
	}

	/**
	 * Get all available network interfaces.
	 *
	 * @return
	 */
	@JSFunction
	public InetAddress[] getAllInetAddresses()
	{
		ArrayList<InetAddress> addresses = new ArrayList<>();
		try
		{
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

			while (networkInterfaces.hasMoreElements())
			{
				NetworkInterface networkInterface = networkInterfaces.nextElement();
				Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

				for (InetAddress inetAddress : Collections.list(inetAddresses))
				{
					addresses.add(inetAddress);
				}
			}
		}
		catch (SocketException e)
		{
			e.printStackTrace();
		}
		return addresses.toArray(new InetAddress[addresses.size()]);
	}

	/**
	 * Returns a InetAddress for a specific (local) ip or hostname.
	 *
	 * @param destIpOrHostname
	 *
	 * @return returns the InetAddress object for the given IP address or hostname or null if not found.
	 */
	@JSFunction
	public InetAddress getInetAddress(String destIpOrHostname)
	{
		try
		{
			return InetAddress.getByName(destIpOrHostname);
		}
		catch (UnknownHostException e)
		{
		}
		return null;
	}


	/**
	 * Start a UDP socket for a port.
	 *
	 * @sample
	 * plugins.udp.startSocket(1234,my_packet_process_method)
	 *
	 * @param port_number the local port that this UDP socket will bind to.
	 * @param method_to_call_when_packet_received_and_buffer_is_empty when the socket receives one or more packages, it calls this method once.
	 * The method will no longer be called even if new packages are received - until a call to {@link UDPProvider#js_getReceivedPacket()} returns null. So you should
	 * consume all available packets before you expect this method to be called again.
	 *
	 * @return true if the UDP socket was successfully started; otherwise, false.
	 */
	public boolean js_startSocket(int port_number, Object method_to_call_when_packet_received_and_buffer_is_empty)
	{
		//clear if restart
		hasSeenEmpty = true;
		buffer = Collections.synchronizedList(new LinkedList<JSPacket>());

		this.port = port_number;
		if (listner == null)
		{
			try
			{
				if (!(method_to_call_when_packet_received_and_buffer_is_empty instanceof Function)) throw new IllegalArgumentException("method invalid"); //$NON-NLS-1$

				DatagramSocket socket = new DatagramSocket(port_number);
				listner = new DatagramHandler(this::addPacket, socket);
				listner.start();

				functionDef = new FunctionDefinition((Function)method_to_call_when_packet_received_and_buffer_is_empty);
				return true;
			}
			catch (SocketException e)
			{
				Debug.error(e);
			}
		}
		return false;
	}

	/**
	 * Stop the UDP socket for a port.
	 *
	 * @sample
	 * plugins.udp.stopSocket()
	 */
	public void js_stopSocket()
	{
		if (listner != null) listner.setListen(false);
		listner = null;
	}

	/**
	 * Create a new empty packet.
	 *
	 * @sample
	 * var packet = plugins.udp.createNewPacket()
	 * packet.writeUTF('hello world!')//writes UTF
	 * packet.writeInt(12348293)//writes 4 bytes
	 * packet.writeShort(14823)//writes 2 bytes
	 * packet.writeByte(123)//writes 1 byte
	 *
	 * @return a new, empty JSPacket instance for creating and sending UDP packets.
	 */
	public JSPacket js_createNewPacket()
	{
		return new JSPacket();
	}

	/**
	 * Send a packet.
	 *
	 * @sample
	 * var packet = plugins.udp.createNewPacket()
	 * packet.writeUTF('hello world!')
	 * plugins.udp.sendPacket('10.0.0.1',packet)
	 *
	 * @param destIpOrHostname the ip of the destination or the hostname
	 * @param packet the JSPacket to send
	 *
	 * @return true if the packet was successfully sent; otherwise, false.
	 */
	public boolean js_sendPacket(String destIpOrHostname, JSPacket packet)
	{
		return js_sendPacket(destIpOrHostname, packet, port);
	}

	/**
	 * Send a packet on another port.
	 *
	 * @sample
	 * var packet = plugins.udp.createNewPacket()
	 * packet.writeUTF('hello world!')
	 * plugins.udp.sendPacket('10.0.0.1',packet, 4321)
	 *
	 * @param destIpOrHostname the ip of the destination or the hostname
	 * @param packet the JSPacket to send
	 * @param port the port on which to send the packet
	 *
	 * @return true if the packet was successfully sent to the specified port; otherwise, false.
	 */
	public boolean js_sendPacket(String destIpOrHostname, JSPacket packet, @SuppressWarnings("hiding") int port)
	{
		if (destIpOrHostname != null && packet != null)
		{
			try
			{
				InetAddress ip = InetAddress.getByName(destIpOrHostname.toString());
				if (ip != null && listner != null)
				{
					DatagramPacket dp = packet.getRealPacket();
					dp.setAddress(ip);
					dp.setPort(port);
					return listner.send(dp);
				}
			}
			catch (UnknownHostException e)
			{
				Debug.error(e);
			}
		}
		return false;
	}

	/**
	 * Put a test packet in the receive buffer to test your method call and getReceivedPacket.
	 *
	 * @sample
	 * var packet = plugins.udp.createNewPacket()
	 * packet.writeUTF('hello world!')
	 * plugins.udp.testPacket(packet)
	 *
	 * @param packet
	 *
	 * @return true if the test packet was successfully added to the receive buffer; otherwise, false.
	 */
	public boolean js_testPacket(JSPacket packet)
	{
		if (packet != null)
		{
			addPacket(packet.getRealPacket());
		}
		return false;
	}

	/**
	 * @deprecated Replaced by {@link #getReceivedPacket()}.
	 */
	@Deprecated
	public JSPacket js_getRecievedPacket()
	{
		return js_getReceivedPacket();
	}

	/**
	 * Get a packet from receive buffer, read buffer until empty (null is returned).
	 *
	 * @sample
	 * var packet = null
	 * while( ( packet = plugins.udp.getReceivedPacket() ) != null)
	 * {
	 * 	var text = packet.readUTF()
	 * 	var count = packet.readInt()
	 * }
	 *
	 * @return the next JSPacket from the receive buffer, or null if the buffer is empty.
	 */
	public JSPacket js_getReceivedPacket()
	{
		synchronized (buffer)
		{
			if (buffer.size() > 0)
			{
				return buffer.remove(0);
			}
			else
			{
				hasSeenEmpty = true;
				return null;
			}
		}
	}

	void addPacket(DatagramPacket dp)
	{
		boolean mustTrigger;
		synchronized (buffer)
		{
			buffer.add(new JSPacket(dp));
			mustTrigger = hasSeenEmpty;
		}

		if (mustTrigger)
		{
			hasSeenEmpty = false;
			if (functionDef != null)
			{
				functionDef.executeAsync(plugin.getClientPluginAccess(), null);
			}
		}
	}

	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { JSPacket.class, UDPSocket.class };
	}
}
