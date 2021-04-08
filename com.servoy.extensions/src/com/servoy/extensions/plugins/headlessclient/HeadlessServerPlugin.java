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
	}

	public void unload()
	{
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
		clearInvalidClients();

		// search for an existing client
		boolean createNewClient = true;
		IHeadlessClient client = clients.get(clientKey);
		if (client != null)
		{
			// client exists; we need to know if the solution is the same one
			Pair<String, Boolean> solutionNameAndValidity = getSolutionName(client);

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
		}
		return clientKey;
	}

	private void clearInvalidClients()
	{
		Iterator<Entry<String, IHeadlessClient>> clientsIterator = clients.entrySet().iterator();
		while (clientsIterator.hasNext())
		{
			Entry<String, IHeadlessClient> entry = clientsIterator.next();
			if (!entry.getValue().isValid())
			{
				clientsIterator.remove();
			}
		}
	}

	private Pair<String, Boolean> getSolutionName(IHeadlessClient client)
	{
		Pair<String, Boolean> solutionNameAndValidity = new Pair<String, Boolean>(null, Boolean.FALSE);
		if (client != null && client.isValid())
		{
			solutionNameAndValidity.setRight(Boolean.TRUE);
			if (client instanceof IServiceProvider)
			{
				Solution sol = ((IServiceProvider)client).getSolution();
				solutionNameAndValidity.setLeft(sol != null ? sol.getName() : null);
			}
		}
		return solutionNameAndValidity;
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
			return executeMethod(clientKey, contextName, methodName, args);
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
	private Object executeMethod(String clientKey, String contextName, String methodName, String[] args) throws Exception
	{
		try
		{
			IHeadlessClient c = getClient(clientKey);
			Object[] convertedArgs = null;
			if (args != null)
			{
				convertedArgs = new Object[args.length];
				for (int i = 0; i < args.length; i++)
				{
					convertedArgs[i] = getJSONConverter().convertFromJSON(c.getPluginAccess().getDatabaseManager(), args[i]);
				}
			}
			try
			{
				Context.enter();
				return getJSONConverter().convertToJSON(c.getPluginAccess().executeMethod(contextName, methodName, convertedArgs, false));
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
			throw new ExceptionWrapper(getJSONConverter().convertToJSON(o));
		}
		catch (RhinoException e)
		{
			if (e.getCause() instanceof ExitScriptException) return null;
			Debug.error(e);
			// wrap it in a normal exception, else serializeable exceptions will happen.
			throw new ExceptionWrapper(getJSONConverter().convertToJSON(e.details()));
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
			synchronized (methodCalls)
			{
				MethodCall methodCall = methodCalls.get(clientKey);
				if (methodCall == null || !(methodCall.callingClientId.equals(callingClientId) && methodCall.methodName.equals(methodName)))
				{
					return UndefinedMarker.INSTANCE;
				}
			}
		}

		IHeadlessClient c = getClient(clientKey);
		Object dataProviderValue = c.getDataProviderValue(contextName, dataprovider);
		try
		{
			return getJSONConverter().convertToJSON(dataProviderValue);
		}
		catch (Exception e)
		{
			throw new RuntimeException("exception when serializing value " + dataProviderValue, e);
		}
	}

	public boolean isValid(String clientKey)
	{
		boolean valid = false;
		IHeadlessClient client = clients.get(clientKey);
		if (client != null)
		{
			return client.isValid();
		}
		return valid;
	}

	public Object setDataProviderValue(String clientKey, String contextName, String dataprovider, String value, String callingClientId, String methodName)
	{
		if (methodName != null)
		{
			synchronized (methodCalls)
			{
				MethodCall methodCall = methodCalls.get(clientKey);
				if (methodCall == null || !(methodCall.callingClientId.equals(callingClientId) && methodCall.methodName.equals(methodName)))
				{
					return UndefinedMarker.INSTANCE;
				}
			}
		}
		IHeadlessClient c = getClient(clientKey);
		Object retValue;
		try
		{
			retValue = c.setDataProviderValue(contextName, dataprovider, getJSONConverter().convertFromJSON(c.getPluginAccess().getDatabaseManager(), value));
		}
		catch (Exception e)
		{
			throw new RuntimeException("exception when deserializing value " + value, e);
		}

		try
		{
			return getJSONConverter().convertToJSON(retValue);
		}
		catch (Exception e)
		{
			throw new RuntimeException("exception when serializing value " + retValue, e);
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
					}
					catch (InterruptedException e)
					{
						Debug.error(e);
					}
				}
				methodCalls.put(clientKey, dummy);
			}
		}
		try
		{
			IHeadlessClient c = getClient(clientKey);
			try
			{
				c.shutDown(force);
			}
			finally
			{
				clients.remove(clientKey);
			}
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
