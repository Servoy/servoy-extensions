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
import java.net.SocketException;
import java.util.function.Consumer;

public class DatagramHandler extends Thread
{
	private boolean listen = true;
	private Consumer<DatagramPacket> provider;
	private DatagramSocket socket;

	DatagramHandler(Consumer<DatagramPacket> provider, DatagramSocket socket)
	{
		this.provider = provider;
		this.socket = socket;
		setDaemon(true);
	}

	@Override
	public void run()
	{
		while (listen)
		{
			try
			{
				DatagramPacket dp = new DatagramPacket(new byte[JSPacket.MAX_PACKET_LENGTH], JSPacket.MAX_PACKET_LENGTH);
				socket.receive(dp);
				provider.accept(dp);
			}
			catch (Throwable e)
			{
				if (socket != null && socket.isClosed())
				{
					listen = false;
				}
				// only log the exception if it wasn't caused by an intentional close of the socket by setListen(false)... "SocketException: socket closed"
				if (listen || !(e instanceof SocketException)) UDPSocket.LOG.error("Error in UDPSocket when reading/listening for packages", e); //$NON-NLS-1$
			}
		}
	}

	void setListen(boolean listen)
	{
		this.listen = listen;
		try
		{
			socket.close();
		}
		catch (Throwable e)
		{
			UDPSocket.LOG.error("Error when calling close on the UDPSocket", e); //$NON-NLS-1$
		}
		socket = null;
		provider = null;
	}

	boolean send(DatagramPacket dp)
	{
		try
		{
			socket.send(dp);
			return true;
		}
		catch (Throwable e)
		{
			UDPSocket.LOG.error("Error in UDPSocket when writing a package to the socket", e); //$NON-NLS-1$
		}
		return false;
	}
}
