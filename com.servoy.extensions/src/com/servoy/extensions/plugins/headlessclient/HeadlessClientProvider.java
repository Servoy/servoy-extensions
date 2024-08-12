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
package com.servoy.extensions.plugins.headlessclient;

import java.util.HashMap;
import java.util.Map;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

@ServoyDocumented(publicName = HeadlessClientPlugin.PLUGIN_NAME, scriptingName = "plugins." + HeadlessClientPlugin.PLUGIN_NAME)
public class HeadlessClientProvider implements IScriptable, IReturnedTypesProvider
{
	private final HeadlessClientPlugin plugin;
	private IHeadlessServer headlessServer = null;
	private ClientPool clientPool;

	HeadlessClientProvider(HeadlessClientPlugin plugin)
	{
		this.plugin = plugin;
	}

	// default constructor
	public HeadlessClientProvider()
	{
		this.plugin = null;
	}

	private void createService()
	{
		if (headlessServer == null)
		{
			try
			{
				IClientPluginAccess access = plugin.getPluginAccess();
				headlessServer = (IHeadlessServer)access.getRemoteService(IHeadlessServer.class.getName());
			}
			catch (Exception ex)
			{
				Debug.error(ex);
			}
		}
	}

	/**
	 * This will try to get a existing client by the given id if that client is already created for that specific solution;
	 * it will create a headless client on the server that will open the given solution if it didn't exists yet.
	 *
	 * If the client does exist but it is not loaded with that solution an exception will be thrown.
	 *
	 * NOTE: in the developer this will only load the solution in debug mode when it is the current active solution or a module of the active solution;
	 * you can load any solution from the workspace when you pass "nodebug" as last argument in the arguments list (it should still use the same resources project).
	 * But then you won't be able to debug it, breakpoints won't hit.
	 *
	 * @sample
	 * // Creates a headless client that will open the given solution.
	 * var storedSolutionSpecificID = "aaaabbbbccccc1111";
	 * var headlessClient = plugins.headlessclient.getOrCreateClient(storedSolutionSpecificID, "someSolution", "user", "pass", null);
	 * if (headlessClient != null && headlessClient.isValid()) {
	 * 	var x = new Object();
	 * 	x.name = 'remote1';
	 * 	x.number = 10;
	 * 	headlessClient.queueMethod(null, "remoteMethod", [x], callback);
	 * }
	 *
	 * @param clientId The client to lookup by id, if not found a new headless client is created with this id.
	 * @param solutionname The solution to load
	 * @param username The user name that is used to login to the solution
	 * @param password The password for the user
	 * @param solutionOpenMethodArgs The arguments that will be passed to the solution open method.
	 *
	 * @return An existing JSClient or the JSClient that is created.
	 */
	public JSClient js_getOrCreateClient(String clientId, String solutionname, String username, String password, Object[] solutionOpenMethodArgs)
	{
		//create if not yet created
		createService();

		try
		{
			String clientID = headlessServer.getOrCreateClient(clientId, solutionname, username, password, solutionOpenMethodArgs,
				plugin.getPluginAccess().getClientID());
			if (clientID != null)
			{
				return getClientPool().getOrCreate(clientID);
			}
		}
		catch (ClientNotFoundException ex)
		{
			throw new RuntimeException("The client with the clientId: " + clientId + " was loaded with another solution: " + ex.getInfo());
		}
		catch (Exception ex)
		{
			Debug.error(ex);
		}
		return null;
	}

	/**
	 * Creates a headless client on the server that will open the given solution.<br/>
	 * The clientId of this client can be stored in the database to be shared between clients so that that specific client can be used
	 * over multiple clients later on, or picked up later on by this client. (Even after restart of this client)<br/><br/>
	 *
	 * NOTE: in the developer this will only load one solution in debug mode when it is the current active solution or a module of the active solution.
	 * So calling createClient with the same or another solution/module will replace the existing debug client.
	 * You can load any solution (and have multiple JSClient instances) from the workspace when you pass "nodebug" as last argument in the arguments list (it should still use the same resources project).
	 * But then you won't be able to debug it, breakpoints won't hit.
	 *
	 * @sample
	 * // Creates a headless client that will open the given solution.
	 * var headlessClient = plugins.headlessclient.createClient("someSolution", "user", "pass", null);
	 * if (headlessClient != null && headlessClient.isValid()) {
	 * 	var x = new Object();
	 * 	x.name = 'remote1';
	 * 	x.number = 10;
	 * 	headlessClient.queueMethod(null, "remoteMethod", [x], callback);
	 * }
	 *
	 * @param solutionName
	 * @param username
	 * @param password
	 * @param solutionOpenMethodArgs
	 */
	public JSClient js_createClient(String solutionName, String username, String password, Object[] solutionOpenMethodArgs)
	{
		//create if not yet created
		createService();

		try
		{
			String clientID = headlessServer.createClient(solutionName, username, password, solutionOpenMethodArgs, plugin.getPluginAccess().getClientID());
			if (clientID != null)
			{
				return getClientPool().getOrCreate(clientID);
			}
		}
		catch (Exception ex)
		{
			Debug.error(ex);
		}
		return null;
	}

	/**
	 * Gets an existing headless client for the given client uuid.
	 *
	 * @sample
	 * // Gets an existing headless client for the given client uuid.
	 * var headlessClient = plugins.headlessclient.getClient("clientID");
	 * if (headlessClient != null && headlessClient.isValid()) {
	 * 	 headlessClient.queueMethod(null, "someRemoteMethod", null, callback);
	 * }
	 *
	 * @param clientID
	 */
	public JSClient js_getClient(String clientID)
	{
		//create if not yet created
		createService();

		try
		{
			if (headlessServer.isValid(clientID))
			{
				return getClientPool().getOrCreate(clientID);
			}
			else
			{
				getClientPool().remove(clientID);
			}
		}
		catch (Exception ex)
		{
			Debug.error(ex);
		}
		return null;
	}

	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { JSClient.class };
	}

	private ClientPool getClientPool()
	{
		if (clientPool == null) clientPool = new ClientPool();
		return clientPool;
	}

	class ClientPool
	{
		private Map<String, JSClient> clients = null;

		private ClientPool()
		{
		}

		private synchronized JSClient getOrCreate(String clientID)
		{
			JSClient tmp = get(clientID);
			if (tmp == null)
			{
				tmp = new JSClient(clientID, headlessServer, plugin, this);
				put(clientID, tmp);
			}
			return tmp;
		}

		private synchronized JSClient get(String clientID)
		{
			return clients == null ? null : clients.get(clientID);
		}

		public synchronized void put(String clientID, JSClient client)
		{
			if (clients == null) clients = new HashMap<String, JSClient>();
			clients.put(clientID, client);
		}

		// currently this is called only if a get happens for an invalid headless client, a shutdown is called on that headless client
		// or if it's invalid when it's "isValid" method gets called. If a headless client shuts down or becomes unavailable for unknown reasons
		// (shut down from another client or in case of clustering the server holding that client gets disconnected) without being accessed again,
		// it could remain behind until the current client is closed; if this is a problem in the future we will need to listen for client validity change
		// to the server plugin
		public synchronized void remove(String clientID)
		{
			if (clients != null) clients.remove(clientID);
		}
	}
}
