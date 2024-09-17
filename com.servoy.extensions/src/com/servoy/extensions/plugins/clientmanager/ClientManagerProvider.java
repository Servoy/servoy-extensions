package com.servoy.extensions.plugins.clientmanager;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.dataprocessing.RowManager;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.server.shared.IClientInformation;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * Client manager scripting object.
 *
 * @author gerzse
 */
@ServoyDocumented(publicName = ClientManagerPlugin.PLUGIN_NAME, scriptingName = "plugins." + ClientManagerPlugin.PLUGIN_NAME)
@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
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
	 * if (plugins.clientmanager.isInMaintenanceMode())
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
	 * The function gets 3 arguments (nickName, message, channelName)
	 *
	 * @sample
	 * function callback(nickName, message, channelName) {
	 *    application.output('message received from ' + nickName + ' on channel ' + channelName + ': ' + message)
	 * }
	 * var broadcaster = plugins.clientmanager.getBroadcaster("nickname", "mychatchannel", callback);
	 * broadcaster.broadcastMessage("Hallo");
	 *
	 * @param name The nickname for this user on this channel
	 * @param channelName The channel name where should be listened to (and send messages to)
	 * @param callback The callback when for incomming messages
	 * @return BroadCaster
	 * @deprecated replaced with plugins.clientmanager.createBroadcaster(name, channelName, callback) to create a channel and
	 * plugins.clientmanager.getOrCreateBroadcaster(name, channelName) to retrieve and send to a channel
	 */
	@Deprecated
	public Broadcaster js_getBroadcaster(String name, String channelName, Function callback)
	{
		Broadcaster broadCaster = new Broadcaster(name, channelName, callback, plugin);
		plugin.addLiveBroadcaster(broadCaster);
		return broadCaster;
	}

	/**
	 * Get a broadcast object giving it a (nick)name and on a specific channel, the callback is used for getting messages of other clients on that channel
	 * The function gets 3 arguments (nickName, message, channelName)
	 *
	 * @sample
	 * function callback(nickName, message, channelName) {
	 *    application.output('message received from ' + nickName + ' on channel ' + channelName + ': ' + message)
	 * }
	 * var broadcaster = plugins.clientmanager.getBroadcaster("nickname", "mychatchannel", callback);
	 * broadcaster.broadcastMessage("Hallo");
	 *
	 * @param name The nickname for this user on this channel
	 * @param channelName The channel name where should be listened to (and send messages to)
	 * @param callback The callback when for incomming messages
	 * @return BroadCaster
	 */
	public Broadcaster js_getOrCreateBroadcaster(String name, String channelName, Function callback)
	{
		Broadcaster broadCaster = plugin.getBroadcaster(name, channelName);
		if (broadCaster != null)
		{
			if (!broadCaster.hasCallback() || (broadCaster.hasCallback() && !broadCaster.getCallback().equals(new FunctionDefinition(callback))))
			{
				broadCaster.addCallback(callback);
			}
			return broadCaster;
		}
		else
		{
			broadCaster = new Broadcaster(name, channelName, callback, plugin);
			plugin.addLiveBroadcaster(broadCaster);
			return broadCaster;
		}
	}

	/**
	 * Get a broadcast object giving it a (nick)name and on a specific channel
	 * var broadcaster = plugins.clientmanager.getBroadcaster("nickname", "mychatchannel");
	 * broadcaster.broadcastMessage("Hallo");
	 *
	 * @param name The nickname for this user on this channel
	 * @param channelName The channel name where should be listened to (and send messages to)
	 * @return BroadCaster
	 */
	public Broadcaster js_getOrCreateBroadcaster(String name, String channelName)
	{
		Broadcaster broadCaster = plugin.getBroadcaster(name, channelName);
		if (broadCaster != null)
		{
			return broadCaster;
		}
		else
		{
			broadCaster = new Broadcaster(name, channelName, plugin);
			plugin.addLiveBroadcaster(broadCaster);
			return broadCaster;
		}
	}

	/**
	 * Create a broadcast object giving it a (nick)name and on a specific channel, the callback is used for getting messages of other clients on that channel
	 * The function gets 3 arguments (nickName, message, channelName)
	 *
	 * @sample
	 * function callback(nickName, message, channelName) {
	 *    application.output('message received from ' + nickName + ' on channel ' + channelName + ': ' + message)
	 * }
	 * var broadcaster = plugins.clientmanager.createBroadcaster("nickname", "mychatchannel", callback);
	 * broadcaster.broadcastMessage("Hallo");
	 *
	 * @param name The nickname for this user on this channel
	 * @param channelName The channel name where should be listened to (and send messages to)
	 * @param callback The callback when for incomming messages
	 * @return BroadCaster
	 */
	public Broadcaster js_createBroadcaster(String name, String channelName, Function callback)
	{
		Broadcaster broadCaster = new Broadcaster(name, channelName, callback, plugin);
		plugin.addLiveBroadcaster(broadCaster);
		return broadCaster;
	}

	/**
	 * Get a broadcast object with a specific channelName, if no broadcast, will return null.
	 *
	 * @sample
	 * var broadcaster = plugins.clientmanager.getBroadcaster("mychatchannel");
	 * if (broadcaster) {
	 * 	block of code to be executed...
	 * }
	 *
	 * @param channelName
	 * @return BroadCaster
	 * @deprecated replaced with plugins.clientmanager.getOrCreateBroadcaster(name, channelName) to retrieve and send to a channel
	 */
	@Deprecated
	public Broadcaster js_getBroadcaster(String channelName)
	{
		return plugin.getBroadcaster(null, channelName);
	}

	/**
	 * Returns an array of JSClientInformation elements describing the clients connected to the server. Note this is snapshot information on connected clients, client information will not get updated.
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
		return js_getConnectedClients(null);
	}

	/**
	 * Returns an array of JSClientInformation elements describing the clients connected to the server filtered by the a client info string.
	 * This way you can ask for a specific set of clients that have a specific information added to there client information.
	 * Note this is snapshot information on connected clients, client information will not get updated.
	 *
	 * @sampleas js_getConnectedClients()
	 *
	 * @param clientInfoFilter The filter string
	 *
	 *@return JSClientInformation[]
	 */
	public JSClientInformation[] js_getConnectedClients(String clientInfoFilter)
	{
		try
		{
			List<JSClientInformation> infos = new ArrayList<>();
			IClientInformation[] connectedClients = plugin.getClientService().getConnectedClients();
			for (IClientInformation connectedClient : connectedClients)
			{
				if (clientInfoFilter == null || Arrays.asList(connectedClient.getClientInfos()).contains(clientInfoFilter))
					infos.add(new JSClientInformation(connectedClient));
			}
			return infos.toArray(new JSClientInformation[infos.size()]);
		}
		catch (Exception e)
		{
			Debug.error("Exception while retrieving connected clients information.", e); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Returns the current client JSClientInformation object. Note this is snapshot information, client information will not get updated.
	 *
	 * @return
	 */
	@JSFunction
	public JSClientInformation getClientInformation()
	{
		String clientId = plugin.getClientPluginAccess().getClientID();
		try
		{
			IClientInformation[] connectedClients = plugin.getClientService().getConnectedClients();
			for (IClientInformation connectedClient : connectedClients)
			{
				if (connectedClient.getClientID().equals(clientId))
				{
					return new JSClientInformation(connectedClient);
				}
			}
		}
		catch (RemoteException e)
		{
			Debug.error("Exception while retrieving connected clients information.", e); //$NON-NLS-1$
		}
		return null;
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
			plugin.getClientService().shutDownClient(clientId, false);
		}
		catch (Exception e)
		{
			Debug.error("Exception while shutting down client '" + clientId + "'.", e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/**
	 * Shuts down a specific client, identified by its clientId. The clientIds are retrieved by calling the getConnectedClients method. This method returns immediately, it does not wait until the client shuts down.
	 * If forceUnregister is true, the client will unregister itself from server. Beware this should be used only if you are sure client is already closed (cannot connect anymore)
	 *
	 * @sample
	 * //Shuts down a specific client, identified by its clientId. The clientIds are retrieved by calling the getConnectedClients method. This method returns immediately, it does not wait until the client shuts down.
	 * var clients = plugins.clientmanager.getConnectedClients();
	 * for (var i=0; i<clients.length; i++)
	 * 	plugins.clientmanager.shutDownClient(clients[i].getClientId());
	 *
	 * @param clientId
	 * @param forceUnregister client is forced to unregister from server
	 */
	public void js_shutDownClient(String clientId, boolean forceUnregister)
	{
		try
		{
			plugin.getClientService().shutDownClient(clientId, forceUnregister);
		}
		catch (Exception e)
		{
			Debug.error("Exception while shutting down client '" + clientId + "'.", e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/**
	 *	Get a dataset will all locks on the server. The dataset will have four columns: datasource, acquireDate, clientId, pkHash.
	 *	Each row in the dataset will be a lock.
	 *
	 *	@sample
	 * 	var locks = plugins.clientmanager.getLocks();
	 */
	public JSDataSet js_getLocks()
	{
		try
		{
			return plugin.getClientService().getLocks();
		}
		catch (Exception e)
		{
			Debug.error("Exception while getting locks.", e); //$NON-NLS-1$
		}
		return new JSDataSet();
	}

	/**
	 * Get client that locked the record from a specific datasource or null if record is not locked.
	 *
	 * @sample
	 * var client = plugins.clientmanager.getLockedByClient(foundset.getDataSource(),record.getPKs());
	 *
	 * @param datasource
	 * @param pks
	 * @return Client information
	 */
	public JSClientInformation js_getLockedByClient(String datasource, Object[] pks)
	{
		try
		{
			JSDataSet locks = plugin.getClientService().getLocks();
			if (locks != null && locks.js_getMaxRowIndex() > 0)
			{
				IClientInformation[] connectedClients = plugin.getClientService().getConnectedClients();
				for (int i = 1; i <= locks.js_getMaxRowIndex(); i++)
				{
					for (IClientInformation connectedClient : connectedClients)
					{
						if (Utils.equalObjects(datasource, locks.getValue(i - 1, 0)) &&
							Utils.equalObjects(connectedClient.getClientID(), locks.getValue(i - 1, 2)) &&
							Utils.equalObjects(RowManager.createPKHashKey(pks), locks.getValue(i - 1, 3)))
						{
							return new JSClientInformation(connectedClient);
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			Debug.error("Exception while finding locked by client.", e); //$NON-NLS-1$
		}
		return null;
	}

	/**
	 * Release all locks acquired by a client
	 *
	 * WARNING: use with care
	 *
	 * @param clientId
	 */
	public void js_releaseLocks(String clientId)

	{
		try
		{
			plugin.getClientService().releaseLocks(clientId);
		}
		catch (Exception e)
		{
			Debug.error("Exception while shutting down client '" + clientId + "'.", e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	public Date js_getServerBuildDate()
	{
		try
		{
			return plugin.getClientService().getServerBuildDate();
		}
		catch (Exception e)
		{
			Debug.error("Exception while getting server build date.", e); //$NON-NLS-1$
		}
		return null;
	}
}
