/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2017 Servoy BV

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

package com.servoy.extensions.plugins.clientmanager;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import org.mozilla.javascript.Function;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.smart.ISmartClientPluginAccess;
import com.servoy.j2db.util.Debug;

/**
 * @author jcompagner
 *
 */
@ServoyDocumented
public class Broadcaster implements IBroadcaster, IJavaScriptType
{
	private final BroadcastInfo bci;
	private final ClientManagerPlugin plugin;
	private final FunctionDefinition fd;

	/**
	 * @param name
	 * @param channelName
	 * @param client
	 */
	public Broadcaster(String name, String channelName, Function callback, ClientManagerPlugin plugin)
	{
		this.bci = new BroadcastInfo(this, name, channelName);
		this.plugin = plugin;
		this.fd = new FunctionDefinition(callback);

		if (plugin.getClientPluginAccess() instanceof ISmartClientPluginAccess)
		{
			try
			{
				((ISmartClientPluginAccess)plugin.getClientPluginAccess()).exportObject(this);
			}
			catch (Exception e)
			{
				Debug.error("Couldn't export object for the broadcaster", e);
			}
		}
		try
		{
			plugin.getClientService().registerChannelListener(bci);
		}
		catch (RemoteException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Get the (nick) name for this broadcaster that will be send to other channel listeners.
	 *
	 * @return String
	 */
	public String js_getName()
	{
		return bci.getName();
	}

	/**
	 * get the channel name where this broadcaster listens and sends messages to.
	 *
	 * @return String
	 */
	public String js_getChannelName()
	{
		return bci.getChannelName();
	}

	/**
	 * Destroyes and unregister the listener for this channel.
	 */
	public void js_destroy()
	{
		try
		{
			plugin.getClientService().deregisterChannelListener(bci);
			if (plugin.getClientPluginAccess() instanceof ISmartClientPluginAccess && !plugin.getClientPluginAccess().isInDeveloper())
			{
				UnicastRemoteObject.unexportObject(this, true);
			}
		}
		catch (RemoteException e)
		{
			Debug.error(e);
		}
		plugin.removeLiveBroadcaster(this);
	}

	/**
	 * Sends a message to the all other listeners of the channel of this broadcaster.
	 *
	 * @param message The message to send to the other users of this channel
	 */
	public void js_broadcastMessage(String message)
	{
		try
		{
			plugin.getClientService().broadcastMessage(bci, message);
		}
		catch (RemoteException e)
		{
			Debug.error(e);
		}
	}

	@Override
	public void channelMessage(String name, String message) throws RemoteException
	{
		fd.executeAsync(plugin.getClientPluginAccess(), new Object[] { name, message });
	}
}
