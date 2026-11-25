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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mozilla.javascript.JavaScriptException;

import com.servoy.extensions.plugins.rest_ws.servlets.RestWSServlet;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;
import com.servoy.j2db.server.shared.IHeadlessClient;

/**
 * RestWSPlugin settings, the headless client pool configuration and the pool factory, with a mocked server and
 * mocked headless clients (no client is started).
 */
public class RestWSPluginTest
{
	private final Properties settings = new Properties();
	private IServerAccess access;
	private IApplicationServerSingleton server;
	private ScheduledExecutorService executor;
	private RestWSPlugin plugin;

	@BeforeEach
	public void setUp() throws Exception
	{
		ApplicationServerRegistry.destroy();
		server = mock(IApplicationServerSingleton.class);
		ApplicationServerRegistry.setApplicationServerSingleton(server);
		access = mock(IServerAccess.class);
		executor = mock(ScheduledExecutorService.class);
		when(access.getSettings()).thenReturn(settings);
		when(access.getExecutor()).thenReturn(executor);
		plugin = new RestWSPlugin();
		plugin.load();
		plugin.initialize(access);
	}

	@AfterEach
	public void tearDown() throws Exception
	{
		plugin.unload();
		ApplicationServerRegistry.destroy();
	}

	private GenericKeyedObjectPool<String, IHeadlessClient> pool()
	{
		return (GenericKeyedObjectPool<String, IHeadlessClient>)plugin.getClientPool();
	}

	@SuppressWarnings("unchecked")
	private KeyedPooledObjectFactory<String, IHeadlessClient> factory()
	{
		return pool().getFactory();
	}

	private static IHeadlessClient client(boolean valid, boolean inDeveloper, String solution)
	{
		IHeadlessClient client = mock(IHeadlessClient.class);
		IClientPluginAccess pluginAccess = mock(IClientPluginAccess.class);
		when(Boolean.valueOf(client.isValid())).thenReturn(Boolean.valueOf(valid));
		when(client.getPluginAccess()).thenReturn(pluginAccess);
		when(Boolean.valueOf(pluginAccess.isInDeveloper())).thenReturn(Boolean.valueOf(inDeveloper));
		when(pluginAccess.getSolutionName()).thenReturn(solution);
		return client;
	}

	/** Replace the pool by one whose factory hands out the given client, so no headless client is started. */
	private GenericKeyedObjectPool<String, IHeadlessClient> poolWith(IHeadlessClient client) throws Exception
	{
		GenericKeyedObjectPool<String, IHeadlessClient> pool = new GenericKeyedObjectPool<>(new BaseKeyedPooledObjectFactory<String, IHeadlessClient>()
		{
			@Override
			public IHeadlessClient create(String key)
			{
				return client;
			}

			@Override
			public PooledObject<IHeadlessClient> wrap(IHeadlessClient value)
			{
				return new DefaultPooledObject<>(value);
			}
		});
		pool.setMaxTotalPerKey(1);
		pool.setBlockWhenExhausted(false);
		Field field = RestWSPlugin.class.getDeclaredField("clientPool");
		field.setAccessible(true);
		field.set(plugin, pool);
		return pool;
	}

	// --- plugin ---

	@Test
	public void theServletIsRegistered()
	{
		verify(access).registerWebService(eq("rest_ws"), any(RestWSServlet.class));
		assertSame(access, plugin.getServerAccess());
		assertNull(plugin.getPreferencePanels());
		assertEquals("RESTful Web Services Plugin", plugin.getProperties().get(IServerPlugin.DISPLAY_NAME));
		assertEquals(6, plugin.getRequiredPropertyNames().size());
		assertNotNull(plugin.getJSONSerializer());
		assertSame(plugin.getJSONSerializer(), plugin.getJSONSerializer());
		assertTrue(plugin.isAcceptingRequests());
	}

	@Test
	public void defaultSettings()
	{
		assertNull(plugin.getAuthorizedGroups());
		assertTrue(plugin.shouldReloadSolutionAfterRequest());
		assertTrue(plugin.useJSUploadForBinaryData());
		assertTrue(plugin.isSendUserPropertiesHeaders());
	}

	@Test
	public void configuredSettings()
	{
		settings.setProperty("rest_ws_plugin_authorized_groups", "admins,users");
		settings.setProperty("rest_ws_reload_solution_after_request", " FALSE ");
		settings.setProperty("rest_ws_use_jsupload_for_binary_data", "false");
		settings.setProperty("rest_ws_send_user_properties_headers", "false");

		assertArrayEquals(new String[] { "admins", "users" }, plugin.getAuthorizedGroups());
		assertFalse(plugin.shouldReloadSolutionAfterRequest());
		assertFalse(plugin.useJSUploadForBinaryData());
		assertFalse(plugin.isSendUserPropertiesHeaders());
	}

	@Test
	public void settingsAreCachedUntilUnload() throws Exception
	{
		assertTrue(plugin.useJSUploadForBinaryData());
		settings.setProperty("rest_ws_use_jsupload_for_binary_data", "false");
		settings.setProperty("rest_ws_plugin_authorized_groups", " ");

		assertTrue(plugin.useJSUploadForBinaryData());
		assertNull(plugin.getAuthorizedGroups());
		plugin.unload();
		assertFalse(plugin.useJSUploadForBinaryData());
	}

	// --- pool configuration ---

	@Test
	public void inDeveloperThePoolHasOneClient()
	{
		when(Boolean.valueOf(server.isDeveloperStartup())).thenReturn(Boolean.TRUE);
		settings.setProperty("rest_ws_plugin_client_pool_size", "10");

		GenericKeyedObjectPool<String, IHeadlessClient> pool = pool();

		assertEquals(1, pool.getMaxTotalPerKey());
		assertEquals(1, pool.getMaxIdlePerKey());
		assertTrue(pool.getBlockWhenExhausted());
		assertTrue(pool.getTestOnBorrow());
		assertSame(pool, plugin.getClientPool());
	}

	@Test
	public void defaultPoolSize()
	{
		assertEquals(5, pool().getMaxTotalPerKey());
		assertEquals(5, pool().getMaxIdlePerKey());
		assertTrue(pool().getBlockWhenExhausted());
	}

	@Test
	public void configuredPoolSizeAndFailAction()
	{
		settings.setProperty("rest_ws_plugin_client_pool_size", " 8 ");
		settings.setProperty("rest_ws_plugin_client_pool_exhausted_action", " FAIL ");

		assertEquals(8, pool().getMaxTotalPerKey());
		assertFalse(pool().getBlockWhenExhausted());
	}

	@Test
	public void anInvalidPoolSizeFallsBackToTheDefault()
	{
		settings.setProperty("rest_ws_plugin_client_pool_size", "many");

		assertEquals(5, pool().getMaxTotalPerKey());
	}

	@Test
	public void theDeprecatedPoolSizeIsStillUsed()
	{
		settings.setProperty("rest_ws_plugin_client_pool_size_per_solution", "3");
		settings.setProperty("rest_ws_plugin_client_pool_size", "9");

		assertEquals(3, pool().getMaxTotalPerKey());
	}

	@Test
	public void anInvalidDeprecatedPoolSizeIsIgnored()
	{
		settings.setProperty("rest_ws_plugin_client_pool_size_per_solution", "x");
		settings.setProperty("rest_ws_plugin_client_pool_size", "4");

		assertEquals(4, pool().getMaxTotalPerKey());
	}

	@Test
	public void growWithoutLimit()
	{
		settings.setProperty("rest_ws_plugin_client_pool_size", "2");
		settings.setProperty("rest_ws_plugin_client_pool_exhausted_action", "grow");

		assertEquals(-1, pool().getMaxTotalPerKey());
		assertEquals(2, pool().getMaxIdlePerKey());
	}

	@Test
	public void growToAMaximum()
	{
		settings.setProperty("rest_ws_plugin_client_pool_exhausted_action", "grow");
		settings.setProperty("rest_ws_plugin_client_max_grow_pool_size", "12");

		assertEquals(12, pool().getMaxTotalPerKey());
	}

	@Test
	public void growWithAnInvalidMaximum()
	{
		settings.setProperty("rest_ws_plugin_client_pool_exhausted_action", "grow");
		settings.setProperty("rest_ws_plugin_client_max_grow_pool_size", "lots");

		assertEquals(-1, pool().getMaxTotalPerKey());
	}

	@Test
	public void anUnknownActionBlocks()
	{
		settings.setProperty("rest_ws_plugin_client_pool_exhausted_action", "wait");

		assertTrue(pool().getBlockWhenExhausted());
	}

	// --- pool factory ---

	@Test
	public void validationAsksTheClient() throws Exception
	{
		KeyedPooledObjectFactory<String, IHeadlessClient> factory = factory();

		assertTrue(factory.validateObject("sol", new DefaultPooledObject<>(client(true, false, "sol"))));
		assertFalse(factory.validateObject("sol", new DefaultPooledObject<>(client(false, false, "sol"))));
	}

	@Test
	public void inDeveloperAnotherSolutionIsReloaded() throws Exception
	{
		IHeadlessClient client = client(true, true, "other");

		assertTrue(factory().validateObject("sol:nodebug", new DefaultPooledObject<>(client)));

		verify(client).closeSolution(true);
		verify(client).loadSolution("sol");
	}

	@Test
	public void inDeveloperTheSameSolutionIsKept() throws Exception
	{
		IHeadlessClient client = client(true, true, "sol");

		assertTrue(factory().validateObject("sol", new DefaultPooledObject<>(client)));

		verify(client, never()).loadSolution(any());
	}

	@Test
	public void aFailingReloadInvalidatesTheClient() throws Exception
	{
		IHeadlessClient client = client(true, true, "other");
		doThrow(new IllegalStateException("cannot load")).when(client).loadSolution("sol");

		assertFalse(factory().validateObject("sol", new DefaultPooledObject<>(client)));
	}

	@Test
	public void destroyShutsTheClientDown() throws Exception
	{
		IHeadlessClient client = client(true, false, "sol");
		IHeadlessClient failing = client(true, false, "sol");
		doThrow(new IllegalStateException("already down")).when(failing).shutDown(true);

		factory().destroyObject("sol", new DefaultPooledObject<>(client));
		factory().destroyObject("sol", new DefaultPooledObject<>(failing));

		verify(client).shutDown(true);
		verify(failing).shutDown(true);
		assertInstanceOf(DefaultPooledObject.class, ((BaseKeyedPooledObjectFactory<String, IHeadlessClient>)factory()).wrap(client));
	}

	// --- borrowing and releasing ---

	@Test
	public void aClientIsBorrowedAndReturned() throws Exception
	{
		IHeadlessClient client = client(true, false, "sol");
		GenericKeyedObjectPool<String, IHeadlessClient> pool = poolWith(client);

		assertSame(client, plugin.getClient("sol"));
		assertEquals(1, pool.getNumActive("sol"));

		plugin.releaseClient("sol", client, false);

		assertEquals(0, pool.getNumActive("sol"));
		assertEquals(1, pool.getNumIdle("sol"));
	}

	@Test
	public void anExhaustedPoolThrowsNoClients() throws Exception
	{
		IHeadlessClient client = client(true, false, "sol");
		poolWith(client);
		plugin.getClient("sol");

		RestWSPlugin.NoClientsException e = assertThrows(RestWSPlugin.NoClientsException.class, () -> plugin.getClient("sol"));
		assertNotNull(e.getCause());
	}

	@Test
	public void returningAnUnknownClientIsOnlyLogged() throws Exception
	{
		poolWith(client(true, false, "sol"));

		plugin.releaseClient("sol", client(true, false, "sol"), false);
	}

	@Test
	public void releaseWithReloadReopensTheSolutionInTheBackground() throws Exception
	{
		IHeadlessClient client = client(true, false, "sol");
		GenericKeyedObjectPool<String, IHeadlessClient> pool = poolWith(client);
		plugin.getClient("sol:nodebug");

		plugin.releaseClient("sol:nodebug", client, true);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).execute(task.capture());
		task.getValue().run();

		verify(client).closeSolution(true);
		verify(client).loadSolution("sol");
		assertEquals(1, pool.getNumIdle("sol:nodebug"));
	}

	@Test
	public void aClientThatCannotReopenIsInvalidated() throws Exception
	{
		IHeadlessClient client = client(true, false, "sol");
		doThrow(new IllegalStateException("broken")).when(client).loadSolution("sol");
		GenericKeyedObjectPool<String, IHeadlessClient> pool = poolWith(client);
		plugin.getClient("sol");

		plugin.releaseClient("sol", client, true);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).execute(task.capture());
		task.getValue().run();

		verify(client).shutDown(true);
		assertEquals(0, pool.getNumIdle("sol"));
		assertEquals(0, pool.getNumActive("sol"));
	}

	@Test
	public void anInvalidClientIsNotReopened() throws Exception
	{
		IHeadlessClient client = client(false, false, "sol");
		GenericKeyedObjectPool<String, IHeadlessClient> pool = poolWith(client);
		pool.setTestOnBorrow(false);
		plugin.getClient("sol");

		plugin.releaseClient("sol", client, true);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).execute(task.capture());
		task.getValue().run();

		verify(client, never()).loadSolution(any());
		assertEquals(0, pool.getNumActive("sol"));
	}

	// --- shutdown and exceptions ---

	@Test
	public void beforeShutdownStopsAcceptingRequests() throws Exception
	{
		GenericKeyedObjectPool<String, IHeadlessClient> pool = poolWith(client(true, false, "sol"));

		plugin.beforeShutdown();

		assertFalse(plugin.isAcceptingRequests());
		// current behaviour (bug): the condition is inverted (clientPool == null && ...), so the pool is never closed
		assertFalse(pool.isClosed());
	}

	@Test
	public void beforeShutdownWithoutPoolFails()
	{
		// current behaviour (bug): with no pool created yet the inverted condition dereferences null
		assertThrows(NullPointerException.class, plugin::beforeShutdown);
		assertFalse(plugin.isAcceptingRequests());
	}

	@Test
	public void exceptions()
	{
		assertEquals("realm", new RestWSPlugin.NotAuthenticatedException("realm").getRealm());
		assertEquals("no", new RestWSPlugin.NotAuthorizedException("no").getMessage());
		IllegalStateException cause = new IllegalStateException("x");
		RestWSPlugin.ExecFailedException failed = new RestWSPlugin.ExecFailedException(cause);
		assertSame(cause, failed.getCause());
		assertFalse(failed.isUserScriptException());
		assertTrue(new RestWSPlugin.ExecFailedException(new JavaScriptException("thrown", "src", 1)).isUserScriptException());
	}
}
