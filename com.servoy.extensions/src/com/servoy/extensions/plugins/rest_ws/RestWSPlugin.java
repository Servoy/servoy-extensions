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
package com.servoy.extensions.plugins.rest_ws;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.mozilla.javascript.JavaScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.servoy.extensions.plugins.rest_ws.servlets.RestWSServlet;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.server.headlessclient.HeadlessClientFactory;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IHeadlessClient;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;
import com.servoy.j2db.util.serialize.JSONSerializerWrapper;

/**
 * Servoy Server plugin for the RESTfull webservices servlet.
 * <p>
 * Configuration:
 * <ul>
 * <li>rest_ws_plugin_client_pool_size, default 5
 * <li>rest_ws_plugin_client_pool_exhausted_action [block/fail/grow], default block
 * </ul>
 *
 * @see RestWSServlet
 * @author rgansevles
 */
@SuppressWarnings("nls")
@ServoyDocumented
public class RestWSPlugin implements IServerPlugin
{
	private static final String CLIENT_POOL_SIZE_PROPERTY = "rest_ws_plugin_client_pool_size";
	private static final int CLIENT_POOL_SIZE_DEFAULT = 5;
	private static final String CLIENT_POOL_EXCHAUSTED_ACTION_PROPERTY = "rest_ws_plugin_client_pool_exhausted_action";
	private static final String ACTION_BLOCK = "block";
	private static final String ACTION_FAIL = "fail";
	private static final String ACTION_GROW = "grow";
	private static final String AUTHORIZED_GROUPS_PROPERTY = "rest_ws_plugin_authorized_groups";
	private static final String RELOAD_SOLUTION_AFTER_REQUEST_PROPERTY = "rest_ws_reload_solution_after_request";
	private static final Boolean RELOAD_SOLUTION_AFTER_REQUEST_DEFAULT = Boolean.TRUE;

	public static final String WEBSERVICE_NAME = "rest_ws";
	private static final String[] SOLUTION_OPEN_METHOD_ARGS = new String[] { "rest_ws_server" };

	public final Logger log = LoggerFactory.getLogger(RestWSPlugin.class);

	private JSONSerializerWrapper serializerWrapper;
	private GenericKeyedObjectPool<String, IHeadlessClient> clientPool = null;
	private Boolean shouldReloadSolutionAfterRequest;
	private IServerAccess application;


	public void initialize(IServerAccess app) throws PluginException
	{
		this.application = app;
		app.registerWebService(WEBSERVICE_NAME, new RestWSServlet(WEBSERVICE_NAME, this));
	}

	public PreferencePanel[] getPreferencePanels()
	{
		return null;
	}

	public Map<String, String> getRequiredPropertyNames()
	{
		Map<String, String> req = new HashMap<String, String>();
		req.put(CLIENT_POOL_SIZE_PROPERTY, "Max number of clients used (this defines the number of concurrent requests and licences used), default = " +
			CLIENT_POOL_SIZE_DEFAULT + ", when running in developer this setting is ignored, pool size will always be 1");
		req.put(CLIENT_POOL_EXCHAUSTED_ACTION_PROPERTY, "The following values are supported for this property:\n" +
			//
			ACTION_BLOCK + " (default): requests will wait untill a client becomes available, when running in developer this value will be used\n" +
			//
			ACTION_FAIL + ": the request will fail. The API will generate a SERVICE_UNAVAILABLE response (HTTP " + HttpServletResponse.SC_SERVICE_UNAVAILABLE +
			")\n" +
			//
			ACTION_GROW +
			": allows the pool to temporarily grow, by starting additional clients. These will be automatically removed when not required anymore.");
		req.put(AUTHORIZED_GROUPS_PROPERTY,
			"Only authenticated users in the listed groups (comma-separated) have access, when left empty unauthorised access is allowed");

		// RELOAD_SOLUTION_AFTER_REQUEST_PROPERTY is discouraged so we do not show it in the admin page plugin properties

		return req;
	}

	public void load() throws PluginException
	{
	}

	public void unload() throws PluginException
	{
		shouldReloadSolutionAfterRequest = null;
		serializerWrapper = null;
		// TODO: clear client pool
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "RESTful Web Services Plugin");
		return props;
	}

	public IServerAccess getServerAccess()
	{
		return application;
	}

	public JSONSerializerWrapper getJSONSerializer()
	{
		if (serializerWrapper == null)
		{
			serializerWrapper = new JSONSerializerWrapper(false);
		}
		return serializerWrapper;
	}

	public String[] getAuthorizedGroups()
	{
		// TODO: cache value
		String property = application.getSettings().getProperty(AUTHORIZED_GROUPS_PROPERTY);
		if (property == null || property.trim().length() == 0)
		{
			return null;
		}
		return property.split(",");
	}

	/*
	 * This is potentially dangerous, only reuse clients with loaded solution if you are very sure the client did not keep state!
	 *
	 * USE AT OWN RISK
	 */
	public boolean shouldReloadSolutionAfterRequest()
	{
		if (shouldReloadSolutionAfterRequest == null)
		{
			String property = application.getSettings().getProperty(RELOAD_SOLUTION_AFTER_REQUEST_PROPERTY);
			shouldReloadSolutionAfterRequest = (property != null && "false".equalsIgnoreCase(property.trim())) ? Boolean.FALSE
				: RELOAD_SOLUTION_AFTER_REQUEST_DEFAULT;
		}

		return shouldReloadSolutionAfterRequest.booleanValue();
	}

	synchronized KeyedObjectPool<String, IHeadlessClient> getClientPool()
	{
		if (clientPool == null)
		{
			GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();

			// in developer multiple clients do not work well with debugger
			config.setBlockWhenExhausted(true);
			config.setMaxTotal(1);

			if (!ApplicationServerRegistry.get().isDeveloperStartup())
			{
				int poolSize;
				try
				{
					poolSize = Integer.parseInt(application.getSettings().getProperty(CLIENT_POOL_SIZE_PROPERTY, "" + CLIENT_POOL_SIZE_DEFAULT).trim());
				}
				catch (NumberFormatException nfe)
				{
					poolSize = CLIENT_POOL_SIZE_DEFAULT;
				}
				config.setMaxTotal(poolSize);

				String exchaustedActionCode = application.getSettings().getProperty(CLIENT_POOL_EXCHAUSTED_ACTION_PROPERTY);
				if (exchaustedActionCode != null) exchaustedActionCode = exchaustedActionCode.trim();
				if (ACTION_FAIL.equalsIgnoreCase(exchaustedActionCode))
				{
					config.setBlockWhenExhausted(false);
					if (log.isDebugEnabled()) log.debug("Client pool, exchaustedAction=" + ACTION_FAIL);
				}
				else if (ACTION_GROW.equalsIgnoreCase(exchaustedActionCode))
				{
					config.setMaxTotal(-1);
					if (log.isDebugEnabled()) log.debug("Client pool, exchaustedAction=" + ACTION_GROW);
				}
				else
				{
					// defaults apply
					if (log.isDebugEnabled()) log.debug("Client pool, exchaustedAction=" + ACTION_BLOCK);
				}
			}

			if (log.isDebugEnabled()) log.debug("Creating client pool, maxSize=" + config.getMaxTotal());

			clientPool = new GenericKeyedObjectPool<>(new BaseKeyedPooledObjectFactory<String, IHeadlessClient>()
			{
				@Override
				public IHeadlessClient create(String key) throws Exception
				{
					if (log.isDebugEnabled()) log.debug("creating new session client for solution '" + key + '\'');
					String solutionName = key;
					String[] solOpenArgs = SOLUTION_OPEN_METHOD_ARGS;

					String[] arr = solutionName.split(":");
					if (arr.length == 2)
					{
						solutionName = arr[0];
						solOpenArgs = Utils.arrayJoin(SOLUTION_OPEN_METHOD_ARGS, new String[] { "nodebug" });
					}
					return HeadlessClientFactory.createHeadlessClient(solutionName, solOpenArgs);
				}

				@Override
				public PooledObject<IHeadlessClient> wrap(IHeadlessClient value)
				{
					return new DefaultPooledObject<>(value);
				}

				@Override
				public boolean validateObject(String key, PooledObject<IHeadlessClient> pooledObject)
				{
					IHeadlessClient client = pooledObject.getObject();
					if (client.getPluginAccess().isInDeveloper())
					{
						String solutionName = key;
						if (solutionName.contains(":")) solutionName = solutionName.split(":")[0];

						if (!solutionName.equals(client.getPluginAccess().getSolutionName()))
						{
							try
							{
								client.closeSolution(true);
								client.loadSolution(solutionName);
							}
							catch (Exception ex)
							{
								return false;
							}
						}
					}
					boolean valid = client.isValid();
					if (log.isDebugEnabled()) log.debug("Validated session client for solution '" + key + "', valid = " + valid);
					return valid;
				}

				@Override
				public void destroyObject(String key, PooledObject<IHeadlessClient> pooledObject) throws Exception
				{
					if (log.isDebugEnabled()) log.debug("Destroying session client for solution '" + key + "'");
					IHeadlessClient client = pooledObject.getObject();
					try
					{
						client.shutDown(true);
					}
					catch (Exception e)
					{
						Debug.error(e);
					}
				}
			});
			clientPool.setConfig(config);
			clientPool.setTestOnBorrow(true);
		}
		return clientPool;
	}

	public IHeadlessClient getClient(String solutionName) throws Exception
	{
		try
		{
			return getClientPool().borrowObject(solutionName);
		}
		catch (NoSuchElementException e)
		{
			// no more licenses
			throw new NoClientsException();
		}
	}

	public void releaseClient(final String poolKey, final IHeadlessClient client, boolean reloadSolution)
	{
		if (reloadSolution)
		{
			application.getExecutor().execute(new Runnable()
			{
				@Override
				public void run()
				{
					boolean solutionReopened = false;
					try
					{
						if (client.isValid())
						{
							client.closeSolution(true);
							String[] arr = poolKey.split(":");
							client.loadSolution(arr.length == 2 ? arr[0] : poolKey); // avoid the ":nodebug" part from the pool key...
							solutionReopened = true;
						}
					}
					catch (Exception ex)
					{
						Debug.error("cannot reopen solution " + poolKey, ex);
						client.shutDown(true);
					}
					finally
					{
						try
						{
							if (solutionReopened) getClientPool().returnObject(poolKey, client);
							else getClientPool().invalidateObject(poolKey, client);
						}
						catch (Exception ex)
						{
							Debug.error(ex);
						}
					}
				}
			});
		}
		else
		{
			// This is potentially dangerous, only reuse clients with loaded solution if you are very sure the client did not keep state!
			try
			{
				getClientPool().returnObject(poolKey, client);
			}
			catch (Exception ex)
			{
				Debug.error(ex);
			}
		}
	}

	public static class NoClientsException extends Exception
	{
	}
	public static class NotAuthorizedException extends Exception
	{
		public NotAuthorizedException(String message)
		{
			super(message);
		}
	}

	public static class NotAuthenticatedException extends Exception
	{
		private final String realm;

		public NotAuthenticatedException(String realm)
		{
			this.realm = realm;
		}

		public String getRealm()
		{
			return realm;
		}
	}

	public static class ExecFailedException extends Exception
	{
		public ExecFailedException(Exception e)
		{
			super(e);
		}

		@Override
		public Exception getCause()
		{
			return (Exception)super.getCause();
		}

		public boolean isUserScriptException()
		{
			return getCause() instanceof JavaScriptException;
		}
	}
}
