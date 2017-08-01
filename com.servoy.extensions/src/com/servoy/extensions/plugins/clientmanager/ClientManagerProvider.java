package com.servoy.extensions.plugins.clientmanager;

import java.rmi.RemoteException;

import org.mozilla.javascript.Function;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.server.shared.IClientInformation;
import com.servoy.j2db.util.Debug;

/**
 * @author gerzse
 */
@ServoyDocumented(publicName = ClientManagerPlugin.PLUGIN_NAME, scriptingName = "plugins." + ClientManagerPlugin.PLUGIN_NAME)
@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
@SuppressWarnings("boxing")
public class ClientManagerProvider implements IScriptable, IReturnedTypesProvider
{
	private final ClientManagerPlugin plugin;

	ClientManagerProvider(ClientManagerPlugin plugin)
	{
		this.plugin = plugin;
	}

	public ClientManagerProvider()
	{
		this.plugin = null;
	}

	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { Broadcaster.class, JSClientInformation.class };
	}

	/**
	 * Returns true if the server is in maintenance mode, false otherwise.
	 *
	 * @sample
	 * //Returns true if the server is in maintenance mode, false otherwise.
	 * if (plugins.maintenance.isInMaintenanceMode())
	 * 	application.output("Server is in maintenance mode.");
	 * else
	 * 	application.output("Server is not in maintenance mode.");
	 */
	public boolean js_isInMaintenanceMode()
	{
		try
		{
			return plugin.getClientService().isInMaintenanceMode();
		}
		catch (RemoteException e)
		{
			Debug.error("Exception while reading maintenance mode status.", e); //$NON-NLS-1$
			return false;
		}
	}


	/**
	 * Get a broadcast object giving it a (nick)name and on a specific channel, the callback is used for getting messages of other clients on that channel
	 * The function gets 2 arguments (nickName, message)
	 *
	 * @sample
	 * var broadcaster = plugins.clientmanager.getBroadcaster("nickname","mychatchannel",callback);
	 * broadcaster.broadcastMessage("Hallo");
	 *
	 * @param name The nickname for this user on this channel
	 * @param channelName The channel name where should be listened to (and send messages to)
	 * @param callback The callback when for incomming messages
	 * @return BroadCaster
	 */
	public Broadcaster js_getBroadcaster(String name, String channelName, Function callback)
	{
		Broadcaster broadCaster = new Broadcaster(name, channelName, callback, plugin);
		plugin.addLiveBroadcaster(broadCaster);
		return broadCaster;
	}

	/**
	 * Returns an array of JSClientInformation elements describing the clients connected to the server.
	 *
	 * @sample
	 * //Returns an array of JSClientInformation elements describing the clients connected to the server.
	 * var clients = plugins.clientmanager.getConnectedClients();
	 * application.output("There are " + clients.length + " connected clients.");
	 * for (var i = 0; i < clients.length; i++)
	 * 	application.output("Client has clientId '" + clients[i].getClientID() + "' and has connected from host '" + clients[i].getHostAddress() + "'.");
	 *
	 * @return JSClientInformation[]
	 */
	public JSClientInformation[] js_getConnectedClients()
	{
		try
		{
			IClientInformation[] connectedClients = plugin.getClientService().getConnectedClients();
			JSClientInformation[] infos = new JSClientInformation[connectedClients == null ? 0 : connectedClients.length];
			for (int i = 0; i < infos.length; i++)
			{
				infos[i] = new JSClientInformation(connectedClients[i]);
			}
			return infos;
		}
		catch (Exception e)
		{
			Debug.error("Exception while retrieving connected clients information.", e); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Sends a message to all connected clients.
	 *
	 * @sample
	 * //Sends a message to all connected clients.
	 * plugins.clientmanager.sendMessageToAllClients("Hello, all clients!");
	 *
	 * @param message
	 */
	public void js_sendMessageToAllClients(String message)
	{
		try
		{
			plugin.getClientService().sendMessageToAllClients(message);
		}
		catch (Exception e)
		{
			Debug.error("Exception while sending message to connected clients.", e); //$NON-NLS-1$
		}
	}

	/**
	 * Sends a message to a specific client, identified by its clientId. The clientIds are retrieved by calling the getConnectedClients method.
	 *
	 * @sample
	 * //Sends a message to a specific client, identified by its clientId. The clientIds are retrieved by calling the getConnectedClients method.
	 * var clients = plugins.clientmanager.getConnectedClients();
	 * for (var i=0; i<clients.length; i++)
	 * 	plugins.clientmanager.sendMessageToClient(clients[i].getClientId(), "Hello, client " + clients[i].getClientID() + "!");
	 *
	 * @param clientId
	 * @param message
	 */
	public void js_sendMessageToClient(String clientId, String message)
	{
		try
		{
			plugin.getClientService().sendMessageToClient(clientId, message);
		}
		catch (Exception e)
		{
			Debug.error("Exception while sending message to client '" + clientId + "'.", e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}


	/**
	 * Shuts down all connected clients. This method returns immediately, it does not wait until the client shuts down.
	 *
	 * @sample
	 * //Shuts down all connected clients. This method returns immediately, it does not wait until the client shuts down.
	 * plugins.clientmanager.shutDownAllClients();
	 */
	public void js_shutDownAllClients()
	{
		try
		{
			plugin.getClientService().shutDownAllClients(plugin.getClientPluginAccess().getClientID());
		}
		catch (Exception e)
		{
			Debug.error("Exception while shutting down connected clients.", e); //$NON-NLS-1$
		}
	}

	/**
	 * Shuts down a specific client, identified by its clientId. The clientIds are retrieved by calling the getConnectedClients method. This method returns immediately, it does not wait until the client shuts down.
	 *
	 * @sample
	 * //Shuts down a specific client, identified by its clientId. The clientIds are retrieved by calling the getConnectedClients method. This method returns immediately, it does not wait until the client shuts down.
	 * var clients = plugins.clientmanager.getConnectedClients();
	 * for (var i=0; i<clients.length; i++)
	 * 	plugins.clientmanager.shutDownClient(clients[i].getClientId());
	 *
	 * @param clientId
	 */
	public void js_shutDownClient(String clientId)
	{
		try
		{
			plugin.getClientService().shutDownClient(clientId);
		}
		catch (Exception e)
		{
			Debug.error("Exception while shutting down client '" + clientId + "'.", e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
}
