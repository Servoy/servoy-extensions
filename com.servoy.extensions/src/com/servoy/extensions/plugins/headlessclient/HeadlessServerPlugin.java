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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeError;
import org.mozilla.javascript.RhinoException;

import com.servoy.extensions.plugins.headlessclient.ServerPluginDispatcher.Call;
import com.servoy.j2db.IServiceProvider;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.server.annotations.TerracottaAutolockRead;
import com.servoy.j2db.server.annotations.TerracottaAutolockWrite;
import com.servoy.j2db.server.annotations.TerracottaInstrumentedClass;
import com.servoy.j2db.server.annotations.TerracottaRoot;
import com.servoy.j2db.server.headlessclient.HeadlessClientFactory;
import com.servoy.j2db.server.shared.IHeadlessClient;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.UUID;
import com.servoy.j2db.util.serialize.JSONConverter;

@SuppressWarnings("nls")
@TerracottaInstrumentedClass
public class HeadlessServerPlugin implements IHeadlessServer, IServerPlugin
{

	@TerracottaRoot
	private final Map<String, MethodCall> methodCalls = new ConcurrentHashMap<String, MethodCall>();

	private final Map<String, IHeadlessClient> clients = new ConcurrentHashMap<String, IHeadlessClient>();

	private final JSONConverter jsonConverter = new JSONConverter();
	private IServerAccess application;

	private final String serverPluginID;

	@TerracottaRoot
	private final Map<String, String> clientIdToServerId = new HashMap<String, String>();

	private ServerPluginDispatcher<HeadlessServerPlugin> serverPluginDispatcher;


	public HeadlessServerPlugin()//must have default constructor
	{
		this.serverPluginID = UUID.randomUUID().toString(); // in case Servoy is running clustered with terracotta, each Servoy server will start it's own plugin
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "HeadlessServerPlugin");
		return props;
	}

	public void load()
	{
	}

	public void initialize(IServerAccess app)
	{
		this.application = app;
		try
		{
			app.registerRemoteService(IHeadlessServer.class.getName(), this);
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		serverPluginDispatcher = new ServerPluginDispatcher<HeadlessServerPlugin>(serverPluginID, this);
	}

	public void unload()
	{
		serverPluginDispatcher.shutdown();
		serverPluginDispatcher.cleanupServer(serverPluginID);
	}

	public Map<String, String> getRequiredPropertyNames()
	{
		return Collections.emptyMap();
	}

	public PreferencePanel[] getPreferencePanels()
	{
		return null;
	}

	public String createClient(String solutionname, String username, String password, Object[] solutionOpenMethodArgs, String callingClientId) throws Exception
	{
		String newClientKey = UUID.randomUUID().toString();
		getOrCreateClient(newClientKey, solutionname, username, password, solutionOpenMethodArgs, callingClientId);
		return newClientKey;
	}

	@TerracottaAutolockWrite
	public String getOrCreateClient(String clientKey, String solutionname, String username, String password, Object[] solutionOpenMethodArgs,
		String callingClientId) throws Exception
	{
		if (!application.isServerProcess(callingClientId) && !application.isAuthenticated(callingClientId))
		{
			throw new SecurityException("Rejected unauthenticated access");
		}

		// clear references to all invalid clients
		serverPluginDispatcher.callOnAllServers(new ClearInvalidClients());

		// search for an existing client
		boolean createNewClient = true;
		String serverId = getServerId(clientKey);
		if (serverId != null)
		{
			// client exists; we need to know if the solution is the same one
			Pair<String, Boolean> solutionNameAndValidity = serverPluginDispatcher.callOnCorrectServer(serverId, new GetSolutionNameCall(clientKey), true);

			if (solutionNameAndValidity.getRight().booleanValue())
			{
				String loadedSolutionName = solutionNameAndValidity.getLeft();
				if (loadedSolutionName == null || !loadedSolutionName.equals(solutionname))
				{
					String name = (loadedSolutionName == null ? "<null>" : loadedSolutionName);
					throw new ClientNotFoundException(clientKey, name);
				}
				createNewClient = false;
			}
		}

		if (createNewClient)
		{
			IHeadlessClient c = HeadlessClientFactory.createHeadlessClient(solutionname, username, password, solutionOpenMethodArgs);
			clients.put(clientKey, c);
			synchronized (clientIdToServerId) // Terracotta WRITE lock
			{
				clientIdToServerId.put(clientKey, serverPluginID);
			}
		}
		return clientKey;
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
	@TerracottaInstrumentedClass
	private static class ClearInvalidClients implements Call<HeadlessServerPlugin, Object>
	{
		@TerracottaAutolockWrite
		public Object executeCall(HeadlessServerPlugin correctServerObject)
		{
			Iterator<Entry<String, IHeadlessClient>> clientsIterator = correctServerObject.clients.entrySet().iterator();
			while (clientsIterator.hasNext())
			{
				Entry<String, IHeadlessClient> entry = clientsIterator.next();
				if (!entry.getValue().isValid())
				{
					clientsIterator.remove();
					synchronized (correctServerObject.clientIdToServerId) // Terracotta WRITE lock
					{
						correctServerObject.clientIdToServerId.remove(entry.getKey());
					}
				}
			}
			return null;
		}
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
	@TerracottaInstrumentedClass
	private static class GetSolutionNameCall implements Call<HeadlessServerPlugin, Pair<String, Boolean>>
	{
		private final String clientKey;

		public GetSolutionNameCall(String clientKey)
		{
			this.clientKey = clientKey;
		}

		public Pair<String, Boolean> executeCall(HeadlessServerPlugin correctServerObject) throws Exception
		{
			Pair<String, Boolean> solutionNameAndValidity = new Pair<String, Boolean>(null, Boolean.FALSE);
			IHeadlessClient c = correctServerObject.getClient(clientKey);
			if (c != null && c.isValid())
			{
				solutionNameAndValidity.setRight(Boolean.TRUE);
				if (c instanceof IServiceProvider)
				{
					Solution sol = ((IServiceProvider)c).getSolution();
					solutionNameAndValidity.setLeft(sol != null ? sol.getName() : null);
				}
			}
			return solutionNameAndValidity;
		}

	}

	private IHeadlessClient getClient(String clientKey) throws ClientNotFoundException
	{
		IHeadlessClient c = clients.get(clientKey);
		if (c != null && c.isValid())
		{
			return c;
		}
		throw new ClientNotFoundException(clientKey);
	}

	@TerracottaAutolockWrite
	public Object executeMethod(final String clientKey, final String contextName, final String methodName, final String[] args, String callingClientId)
		throws Exception
	{
		MethodCall call = new MethodCall(callingClientId, methodName);

		synchronized (methodCalls) // Terracotta WRITE lock
		{
			while (methodCalls.containsKey(clientKey))
				methodCalls.wait();
			methodCalls.put(clientKey, call);
		}

		try
		{
			return serverPluginDispatcher.callOnCorrectServer(getNonNullServerId(clientKey), new ExecuteMethodCall(clientKey, contextName, methodName, args),
				true);
		}
		finally
		{
			synchronized (methodCalls) // Terracotta WRITE lock
			{
				methodCalls.remove(clientKey);
				methodCalls.notifyAll();
			}
		}
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
	@TerracottaInstrumentedClass
	private static class ExecuteMethodCall implements Call<HeadlessServerPlugin, String>
	{

		private final String clientKey;
		private final String contextName;
		private final String methodName;
		private final String[] args;

		public ExecuteMethodCall(String clientKey, String contextName, String methodName, String[] args)
		{
			this.clientKey = clientKey;
			this.contextName = contextName;
			this.methodName = methodName;
			this.args = args;
		}

		public String executeCall(HeadlessServerPlugin correctServerObject) throws Exception
		{
			try
			{
				IHeadlessClient c = correctServerObject.getClient(clientKey);
				Object[] convertedArgs = null;
				if (args != null)
				{
					convertedArgs = new Object[args.length];
					for (int i = 0; i < args.length; i++)
					{
						convertedArgs[i] = correctServerObject.getJSONConverter().convertFromJSON(c.getPluginAccess().getDatabaseManager(), args[i]);
					}
				}
				return correctServerObject.getJSONConverter().convertToJSON(c.getPluginAccess().executeMethod(contextName, methodName, convertedArgs, false));
			}
			catch (JavaScriptException jse)
			{
				Debug.log(jse);
				Object o = jse.getValue();
				if (o instanceof NativeError)
				{
					o = ((NativeError)o).get("message", null);
				}
				throw new ExceptionWrapper(correctServerObject.getJSONConverter().convertToJSON(o));
			}
			catch (RhinoException e)
			{
				Debug.error(e);
				// wrap it in a normal exception, else serializeable exceptions will happen.
				throw new ExceptionWrapper(correctServerObject.getJSONConverter().convertToJSON(e.details()));
			}
		}
	}

	/**
	 * It will either return a non-null server id or throw an exception.
	 */
	private String getNonNullServerId(String clientKey)
	{
		String serverId = getServerId(clientKey);
		if (serverId == null) throw new ClientNotFoundException(clientKey);
		return serverId;
	}

	@TerracottaAutolockRead
	private String getServerId(String clientKey)
	{
		synchronized (clientIdToServerId) // Terracotta READ lock
		{
			return clientIdToServerId.get(clientKey);
		}
	}

	private JSONConverter getJSONConverter()
	{
		return jsonConverter;
	}

	@TerracottaAutolockRead
	public Object getDataProviderValue(String clientKey, String contextName, String dataprovider, String callingClientId, String methodName)
	{
		if (methodName != null)
		{
			synchronized (methodCalls) // Terracotta READ lock
			{
				MethodCall methodCall = methodCalls.get(clientKey);
				if (methodCall == null || !(methodCall.callingClientId.equals(callingClientId) && methodCall.methodName.equals(methodName)))
				{
					return UndefinedMarker.INSTANCE;
				}
			}
		}

		return serverPluginDispatcher.callOnCorrectServer(getNonNullServerId(clientKey), new GetDataProviderCall(clientKey, contextName, dataprovider), true);
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
	@TerracottaInstrumentedClass
	private static class GetDataProviderCall implements Call<HeadlessServerPlugin, String>
	{

		private final String clientKey;
		private final String contextName;
		private final String dataprovider;

		public GetDataProviderCall(String clientKey, String contextName, String dataprovider)
		{
			this.clientKey = clientKey;
			this.contextName = contextName;
			this.dataprovider = dataprovider;
		}

		public String executeCall(HeadlessServerPlugin correctServerObject) throws Exception
		{
			IHeadlessClient c = correctServerObject.getClient(clientKey);
			Object dataProviderValue = c.getDataProviderValue(contextName, dataprovider);
			try
			{
				return correctServerObject.getJSONConverter().convertToJSON(dataProviderValue);
			}
			catch (Exception e)
			{
				throw new RuntimeException("exception when serializing value " + dataProviderValue, e);
			}
		}
	}

	public boolean isValid(String clientKey)
	{
		boolean valid;
		String serverId = getServerId(clientKey);
		if (serverId != null)
		{
			Boolean validB = serverPluginDispatcher.callOnCorrectServer(serverId, new CheckValidityCall(clientKey), true);
			valid = (validB != null ? validB.booleanValue() : false);
		}
		else valid = false;

		return valid;
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
	@TerracottaInstrumentedClass
	private static class CheckValidityCall implements Call<HeadlessServerPlugin, Boolean>
	{

		private final String clientKey;

		public CheckValidityCall(String clientKey)
		{
			this.clientKey = clientKey;
		}

		@TerracottaAutolockWrite
		public Boolean executeCall(HeadlessServerPlugin correctServerObject) throws Exception
		{
			Boolean result;
			IHeadlessClient c = correctServerObject.clients.get(clientKey);
			if (c != null)
			{
				result = Boolean.valueOf(c.isValid());
			}
			else
			{
				result = Boolean.FALSE;
			}
			return result;
		}
	}

	@TerracottaAutolockRead
	public Object setDataProviderValue(String clientKey, String contextName, String dataprovider, String value, String callingClientId, String methodName)
	{
		if (methodName != null)
		{
			synchronized (methodCalls) // Terracotta READ lock
			{
				MethodCall methodCall = methodCalls.get(clientKey);
				if (methodCall == null || !(methodCall.callingClientId.equals(callingClientId) && methodCall.methodName.equals(methodName)))
				{
					return UndefinedMarker.INSTANCE;
				}
			}
		}
		return serverPluginDispatcher.callOnCorrectServer(getNonNullServerId(clientKey), new SetDataProviderCall(clientKey, contextName, dataprovider, value),
			true);
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
	@TerracottaInstrumentedClass
	private static class SetDataProviderCall implements Call<HeadlessServerPlugin, String>
	{

		private final String clientKey;
		private final String contextName;
		private final String dataprovider;
		private final String value;

		public SetDataProviderCall(String clientKey, String contextName, String dataprovider, String value)
		{
			this.clientKey = clientKey;
			this.contextName = contextName;
			this.dataprovider = dataprovider;
			this.value = value;
		}

		public String executeCall(HeadlessServerPlugin correctServerObject) throws Exception
		{
			IHeadlessClient c = correctServerObject.getClient(clientKey);
			Object retValue;
			try
			{
				retValue = c.setDataProviderValue(contextName, dataprovider,
					correctServerObject.getJSONConverter().convertFromJSON(c.getPluginAccess().getDatabaseManager(), value));
			}
			catch (Exception e)
			{
				throw new RuntimeException("exception when deserializing value " + value, e);
			}

			try
			{
				return correctServerObject.getJSONConverter().convertToJSON(retValue);
			}
			catch (Exception e)
			{
				throw new RuntimeException("exception when serializing value " + retValue, e);
			}
		}
	}

	public void shutDown(String clientKey, boolean force)
	{
		MethodCall dummy = null;
		if (!force)
		{
			dummy = new MethodCall(clientKey, "");
			// if not force then wait for the current method calls.
			// this could mean that when 1 is finished but other method calls are waiting
			// that one of those are done first, or that this one gets it and kill the client.
			synchronized (methodCalls) // Terracotta WRITE lock
			{
				while (methodCalls.containsKey(clientKey))
				{
					try
					{
						methodCalls.wait();
						methodCalls.put(clientKey, dummy);
					}
					catch (InterruptedException e)
					{
						Debug.error(e);
					}
				}
			}
		}
		try
		{
			serverPluginDispatcher.callOnCorrectServer(getNonNullServerId(clientKey), new ShutDownCall(clientKey, force), true);
		}
		finally
		{
			if (dummy != null)
			{
				synchronized (methodCalls) // Terracotta WRITE lock
				{
					methodCalls.remove(clientKey);
					methodCalls.notifyAll();
				}
			}
		}
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
	@TerracottaInstrumentedClass
	private static class ShutDownCall implements Call<HeadlessServerPlugin, Object>
	{

		private final String clientKey;
		private final boolean force;

		public ShutDownCall(String clientKey, boolean force)
		{
			this.clientKey = clientKey;
			this.force = force;
		}

		@TerracottaAutolockWrite
		public Object executeCall(HeadlessServerPlugin correctServerObject) throws Exception
		{
			IHeadlessClient c = correctServerObject.getClient(clientKey);
			try
			{
				c.shutDown(force);
			}
			finally
			{
				correctServerObject.clients.remove(clientKey);
				synchronized (correctServerObject.clientIdToServerId) // Terracotta WRITE lock
				{
					correctServerObject.clientIdToServerId.remove(clientKey);
				}
			}
			return null;
		}
	}

	@TerracottaInstrumentedClass
	private static class MethodCall
	{

		private final String callingClientId;
		private final String methodName;

		/**
		 * @param callingClientId
		 * @param methodName
		 */
		public MethodCall(String callingClientId, String methodName)
		{
			this.callingClientId = callingClientId;
			this.methodName = methodName;
		}

	}

}
