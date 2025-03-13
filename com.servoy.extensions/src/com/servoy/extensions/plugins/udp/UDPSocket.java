/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2025 Servoy BV

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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.annotations.JSFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * @author jcompagner
 *
 */
@ServoyDocumented(scriptingName = "UDPSocket")
public class UDPSocket implements IScriptable, IJavaScriptType
{
	final static Logger LOG = LoggerFactory.getLogger(UDPSocket.class.getCanonicalName());

	private final DatagramSocket socket;
	private final UDPPlugin plugin;
	private FunctionDefinition functionDef;
	private DatagramHandler listner;

	private volatile List<JSPacket> buffer;
	private volatile boolean hasSeenEmpty;

	/**
	 * @param socket
	 */
	public UDPSocket(DatagramSocket socket, UDPPlugin plugin)
	{
		this.socket = socket;
		this.plugin = plugin;
	}

	/**
	 * Starts the socket to listen for incoming packets, no need to call this method if you only want to send packets.
	 * the given function will be called when a packet is received, it will get as a parameter UPPSocket instance itself.
	 *
	 * @sample
	 * var socket = plugins.udp.createSocket(4321).start(callbackFunction);
	 * function callbackFunction() {
	 *   var string = socket.getReceivedPacket().readUTF();
	 *   application.output(string);
	 * }
	 *
	 * @param packageReceivedCallback the callback function that will be called when a package is received, it will get as a parameter UPPSocket instance itself.
	 */
	@JSFunction
	public UDPSocket start(Function packageReceivedCallback)
	{
		this.hasSeenEmpty = true;
		this.buffer = new LinkedList<JSPacket>();
		this.functionDef = new FunctionDefinition(packageReceivedCallback);
		listner = new DatagramHandler(this::packageReceived, socket);
		listner.start();
		return this;
	}

	/**
	 * Send a packet, over this socket, no need to start it if you don't want to listen to incoming packages.
	 * This will send the packet to the same port as the socket itself is bound to.
	 *
	 * @sample
	 * var socket = plugins.udp.createSocket(4321);
	 * var packet = plugins.udp.createNewPacket();
	 * packet.writeUTF('hello world!')
	 * socket.sendPacket('10.0.0.1',packet)
	 * socket.close();
	 *
	 * @param destIpOrHostname the ip of the destination or the hostname
	 * @param packet the JSPacket to send
	 *
	 * @return true if the packet was successfully sent; otherwise, false.
	 */
	@JSFunction
	public boolean sendPacket(String destIpOrHostname, JSPacket packet)
	{
		return sendPacket(destIpOrHostname, packet, socket.getPort());
	}

	/**
	 * Send a packet, over this socket, no need to start it if you don't want to listen to incoming packages.
	 * This will send the packet to the give port on the destination.
	 *
	 * @sample
	 * var socket = plugins.udp.createSocket(4321);
	 * var packet = plugins.udp.createNewPacket()
	 * packet.writeUTF('hello world!')
	 * socket.sendPacket('10.0.0.1',packet, 9999)
	 * socket.close();
	 *
	 * @param destIpOrHostname the ip of the destination or the hostname
	 * @param packet the JSPacket to send
	 * @param port the port on which to send the packet
	 *
	 * @return true if the packet was successfully sent to the specified port; otherwise, false.
	 */
	@JSFunction
	public boolean sendPacket(String destIpOrHostname, JSPacket packet, int port)
	{
		if (destIpOrHostname != null && packet != null)
		{
			try
			{
				InetAddress ip = InetAddress.getByName(destIpOrHostname.toString());
				if (ip != null)
				{
					DatagramPacket dp = packet.getRealPacket();
					dp.setAddress(ip);
					dp.setPort(port);
					try
					{
						socket.send(dp);
					}
					catch (IOException e)
					{
						return false;
					}
					return true;
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
	 */
	@JSFunction
	public void testPacket(JSPacket packet)
	{
		if (packet != null)
		{
			packageReceived(packet.getRealPacket());
		}
	}


	/**
	 * Closes the socket and cleans up the resources.
	 *
	 */
	@JSFunction
	public void close()
	{
		internalClose();
		plugin.removeSocket(this);
	}

	/**
	 * Get a packet from receive buffer, read buffer until empty (null is returned).
	 *
	 * @sample
	 * var packet = null
	 * while( ( packet = socket.getReceivedPacket() ) != null)
	 * {
	 * 	var text = packet.readUTF()
	 * 	var count = packet.readInt()
	 * }
	 *
	 * @return the next JSPacket from the receive buffer, or null if the buffer is empty.
	 */
	@JSFunction
	public JSPacket getReceivedPacket()
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

	void packageReceived(DatagramPacket packet)
	{
		boolean mustTrigger;
		synchronized (buffer)
		{
			buffer.add(new JSPacket(packet));
			mustTrigger = hasSeenEmpty;
			hasSeenEmpty = false;
		}

		if (mustTrigger && functionDef != null)
			functionDef.executeAsync(plugin.getClientPluginAccess(), new Object[] { this });
	}

	@SuppressWarnings("nls")
	void internalClose()
	{
		if (listner != null)
		{
			listner.setListen(false);
			if (buffer.size() > 0)
			{
				LOG.warn("We still have packets in the UDPSocket buffer connected to " + socket + " , we are going to throw them away");
			}
			buffer.clear();
		}
		else socket.close();
	}

}
