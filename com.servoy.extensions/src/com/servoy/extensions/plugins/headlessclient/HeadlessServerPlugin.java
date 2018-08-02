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
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeError;
import org.mozilla.javascript.RhinoException;

import com.servoy.extensions.plugins.headlessclient.ServerPluginDispatcher.Call;
import com.servoy.j2db.ExitScriptException;
import com.servoy.j2db.IServiceProvider;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.server.headlessclient.HeadlessClientFactory;
import com.servoy.j2db.server.shared.IHeadlessClient;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.UUID;
import com.servoy.j2db.util.serialize.JSONConverter;

@SuppressWarnings("nls")
public class HeadlessServerPlugin implements IHeadlessServer, IServerPlugin
{

	private final Map<String, MethodCall> methodCalls = new ConcurrentHashMap<String, MethodCall>();

	private final Map<String, IHeadlessClient> clients = new ConcurrentHashMap<String, IHeadlessClient>();

	private final JSONConverter jsonConverter = new JSONConverter();
	private IServerAccess application;

	private ServerPluginDispatcher<HeadlessServerPlugin> serverPluginDispatcher;


	public HeadlessServerPlugin()//must have default constructor
	{
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
		serverPluginDispatcher = new ServerPluginDispatcher<HeadlessServerPlugin>(this);
	}

	public void unload()
	{
		serverPluginDispatcher.shutdown();
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
		// client exists; we need to know if the solution is the same one
		Pair<String, Boolean> solutionNameAndValidity = serverPluginDispatcher.callOnCorrectServer(new GetSolutionNameCall(clientKey));

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

		if (createNewClient)
		{
			IHeadlessClient c = HeadlessClientFactory.createHeadlessClient(solutionname, username, password, solutionOpenMethodArgs);
			clients.put(clientKey, c);
		}
		return clientKey;
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
	private static class ClearInvalidClients implements Call<HeadlessServerPlugin, Object>
	{
		public Object executeCall(HeadlessServerPlugin correctServerObject)
		{
			Iterator<Entry<String, IHeadlessClient>> clientsIterator = correctServerObject.clients.entrySet().iterator();
			while (clientsIterator.hasNext())
			{
				Entry<String, IHeadlessClient> entry = clientsIterator.next();
				if (!entry.getValue().isValid())
				{
					clientsIterator.remove();
				}
			}
			return null;
		}
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
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
			return serverPluginDispatcher.callOnCorrectServer(new ExecuteMethodCall(clientKey, contextName, methodName, args));
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
				try
				{
					Context.enter();
					return correctServerObject.getJSONConverter().convertToJSON(
						c.getPluginAccess().executeMethod(contextName, methodName, convertedArgs, false));
				}
				finally
				{
					Context.exit();
				}
			}
			catch (JavaScriptException jse)
			{
				if (jse.getValue() instanceof ExitScriptException) return null;
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
				if (e.getCause() instanceof ExitScriptException) return null;
				Debug.error(e);
				// wrap it in a normal exception, else serializeable exceptions will happen.
				throw new ExceptionWrapper(correctServerObject.getJSONConverter().convertToJSON(e.details()));
			}
		}
	}

	private JSONConverter getJSONConverter()
	{
		return jsonConverter;
	}

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

		return serverPluginDispatcher.callOnCorrectServer(new GetDataProviderCall(clientKey, contextName, dataprovider));
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
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
		Boolean validB = serverPluginDispatcher.callOnCorrectServer(new CheckValidityCall(clientKey));
		boolean valid = (validB != null ? validB.booleanValue() : false);

		return valid;
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
	private static class CheckValidityCall implements Call<HeadlessServerPlugin, Boolean>
	{

		private final String clientKey;

		public CheckValidityCall(String clientKey)
		{
			this.clientKey = clientKey;
		}

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
		return serverPluginDispatcher.callOnCorrectServer(new SetDataProviderCall(clientKey, contextName, dataprovider, value));
	}

	// must be static otherwise it would have a back-reference that would make everything (try to) go into shared cluster memory
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
			serverPluginDispatcher.callOnCorrectServer(new ShutDownCall(clientKey, force));
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
	private static class ShutDownCall implements Call<HeadlessServerPlugin, Object>
	{

		private final String clientKey;
		private final boolean force;

		public ShutDownCall(String clientKey, boolean force)
		{
			this.clientKey = clientKey;
			this.force = force;
		}

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
			}
			return null;
		}
	}

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
