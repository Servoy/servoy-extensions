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
package com.servoy.extensions.plugins.rawSQL;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.rmi.RemoteException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.dataprocessing.BufferedDataSet;
import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.dataprocessing.IDatabaseManager;
import com.servoy.j2db.dataprocessing.ISQLActionTypes;
import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.plugins.ClientPluginAccessProvider;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.scripting.IExecutingEnviroment;
import com.servoy.j2db.util.ServoyException;
import com.servoy.j2db.util.UUID;

/**
 * The rawSQL scripting API with a mocked remote ISQLService and database manager: server name mapping (logical vs
 * switched server), transactions, stored procedures and the cache flushes.
 */
public class RawSQLProviderTest
{
	private ClientPluginAccessProvider access;
	private IDatabaseManager databaseManager;
	private ISQLService service;
	private RawSQLPlugin plugin;
	private RawSQLProvider provider;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(ClientPluginAccessProvider.class);
		databaseManager = mock(IDatabaseManager.class);
		service = mock(ISQLService.class);
		when(access.getDatabaseManager()).thenReturn(databaseManager);
		when(access.getClientID()).thenReturn("client");
		when(access.getRemoteService("servoy.ISQLService")).thenReturn(service);
		// JSDataSet asks the application for its solution scope (null is fine)
		IApplication application = mock(IApplication.class);
		when(application.getScriptEngine()).thenReturn(mock(IExecutingEnviroment.class));
		when(access.getApplication()).thenReturn(application);
		// "example" is a logical server, switched to the real server "example_prod"
		when(databaseManager.getOriginalServerNames("example")).thenReturn(List.of("example"));
		when(databaseManager.getSwitchedToServerName("example")).thenReturn("example_prod");
		plugin = new RawSQLPlugin();
		plugin.load();
		plugin.initialize(access);
		provider = (RawSQLProvider)plugin.getScriptObject();
	}

	// --- plugin ---

	@Test
	public void pluginDescription() throws Exception
	{
		assertEquals("rawSQL", plugin.getName());
		assertEquals("RawSQL plugin", plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertNotNull(plugin.getIconUrl());
		assertNull(plugin.getImage());
		assertSame(provider, plugin.getScriptObject());
		plugin.propertyChange(null);

		plugin.unload();

		assertNull(plugin.getClientPluginAccess());
	}

	// --- executeSQL ---

	@Test
	public void sqlRunsOnTheSwitchedServer() throws Exception
	{
		Object[] args = { "a" };
		when(Boolean.valueOf(service.executeSQL("client", "example_prod", "update t set a=?", args, null, 0))).thenReturn(Boolean.TRUE);

		assertTrue(provider.js_executeSQL("example", "update t set a=?", args));

		assertNull(provider.js_getException());
		assertNull(provider.js_getExceptionMsg());
	}

	@Test
	public void sqlOverloads() throws Exception
	{
		when(Boolean.valueOf(service.executeSQL(anyString(), anyString(), anyString(), any(), any(), anyInt()))).thenReturn(Boolean.TRUE);

		assertTrue(provider.js_executeSQL("example", "delete from t"));
		assertTrue(provider.js_executeSQL("example", "delete from t", null, 15));
		assertTrue(provider.js_executeSQL("example", "t", "delete from t"));
		assertTrue(provider.js_executeSQL("example", "t", "delete from t", new Object[0]));

		verify(service, times(2)).executeSQL("client", "example_prod", "delete from t", null, null, 0);
		verify(service).executeSQL("client", "example_prod", "delete from t", new Object[0], null, 0);
		verify(service).executeSQL("client", "example_prod", "delete from t", null, null, 15);
	}

	@Test
	public void aTransactionIdIsPassed() throws Exception
	{
		when(databaseManager.getTransactionID("example")).thenReturn("tid-1");

		provider.js_executeSQL("example", "delete from t");

		verify(service).executeSQL("client", "example_prod", "delete from t", null, "tid-1", 0);
	}

	@Test
	public void aRealServerNameIsMappedBackToItsLogicalServer() throws Exception
	{
		// deprecated use: the real server name is given, the logical one is looked up
		when(databaseManager.getOriginalServerNames("example_prod")).thenReturn(List.of("example"));
		when(databaseManager.getTransactionID("example")).thenReturn("tid-2");

		provider.js_executeSQL("example_prod", "delete from t");

		verify(service).executeSQL("client", "example_prod", "delete from t", null, "tid-2", 0);
	}

	@Test
	public void anAmbiguousTransactionServerIsAnError() throws Exception
	{
		when(databaseManager.getOriginalServerNames("shared_prod")).thenReturn(List.of("a", "b"));
		when(databaseManager.getTransactionID("a")).thenReturn("tid");

		assertFalse(provider.js_executeSQL("shared_prod", "delete from t"));

		assertTrue(provider.js_getException() instanceof IllegalArgumentException);
		assertTrue(provider.js_getExceptionMsg().contains("[a, b]"), provider.js_getExceptionMsg());
		verify(service, never()).executeSQL(anyString(), anyString(), anyString(), any(), any(), anyInt());
	}

	@Test
	public void aFailureIsKeptAsTheException() throws Exception
	{
		RuntimeException error = new RuntimeException("syntax error");
		when(Boolean.valueOf(service.executeSQL(anyString(), anyString(), anyString(), any(), any(), anyInt()))).thenThrow(error);

		assertFalse(provider.js_executeSQL("example", "bad"));
		assertSame(error, provider.js_getException());
		assertFalse(provider.js_executeSQL("example", "t", "bad", null));
		assertSame(error, provider.js_getException());

		// the next successful call clears it
		doReturn(Boolean.TRUE).when(service).executeSQL(anyString(), anyString(), anyString(), any(), any(), anyInt());
		assertTrue(provider.js_executeSQL("example", "good"));
		assertNull(provider.js_getException());
	}

	@Test
	public void theRemoteServiceIsLookedUpOnce() throws Exception
	{
		provider.js_executeSQL("example", "a");
		provider.js_executeSQL("example", "b");

		verify(access, times(1)).getRemoteService("servoy.ISQLService");
	}

	// --- stored procedures ---

	@Test
	public void aStoredProcedureReturnsADataSet() throws Exception
	{
		IDataSet set = new BufferedDataSet(new String[] { "id" }, List.<Object[]> of(new Object[] { Integer.valueOf(1) }));
		when(service.executeStoredProcedure("client", "example_prod", null, "{call p(?)}", new Object[] { "x" }, new int[] { 0 }, 0, 100)).thenReturn(set);

		JSDataSet result = provider.js_executeStoredProcedure("example", "{call p(?)}", new Object[] { "x" }, new int[] { 0 }, 100);

		assertEquals(1, result.js_getMaxRowIndex());
		assertEquals(Integer.valueOf(1), result.js_getValue(1, 1));
	}

	@Test
	public void storedProcedureArgumentsAndDirectionsMustMatch()
	{
		assertThrows(RuntimeException.class, () -> provider.js_executeStoredProcedure("example", "{call p(?)}", new Object[] { "x" }, new int[0], 10));
	}

	@Test
	public void aStoredProcedureWithMultipleResults() throws Exception
	{
		IDataSet a = new BufferedDataSet(new String[] { "a" }, List.<Object[]> of());
		IDataSet b = new BufferedDataSet(new String[] { "b" }, List.<Object[]> of(new Object[] { "x" }, new Object[] { "y" }));
		when(service.executeStoredProcedure("client", "example_prod", null, "{call q()}", null, 0, 50)).thenReturn(new IDataSet[] { a, b });

		JSDataSet[] results = provider.js_executeStoredProcedure("example", "{call q()}", null, 50);

		assertEquals(2, results.length);
		assertEquals(0, results[0].js_getMaxRowIndex());
		assertEquals(2, results[1].js_getMaxRowIndex());
	}

	@Test
	public void aServoyExceptionIsADataSetWithTheException() throws Exception
	{
		ServoyException error = new ServoyException(ServoyException.UNKNOWN_DATABASE_EXCEPTION);
		when(databaseManager.getOriginalServerNames("broken")).thenThrow(new IllegalStateException("x"));
		when(databaseManager.getTransactionID("example")).thenThrow(error);

		JSDataSet single = provider.js_executeStoredProcedure("example", "{call p()}", null, null, 10);
		assertSame(error, single.js_getException());
		assertSame(error, provider.js_getException());

		JSDataSet[] multiple = provider.js_executeStoredProcedure("example", "{call p()}", null, 10);
		assertEquals(1, multiple.length);
		assertSame(error, multiple[0].js_getException());
	}

	@Test
	public void anyOtherStoredProcedureErrorIsNull() throws Exception
	{
		RemoteException error = new RemoteException("down");
		// any(int[].class) does not match a null directionality array, so match it as an untyped any()
		when(service.executeStoredProcedure(anyString(), anyString(), any(), anyString(), any(), (int[])any(), anyInt(), anyInt())).thenThrow(error);
		when(service.executeStoredProcedure(anyString(), anyString(), any(), anyString(), any(), anyInt(), anyInt())).thenThrow(error);

		assertNull(provider.js_executeStoredProcedure("example", "{call p()}", null, null, 10));
		assertSame(error, provider.js_getException());
		assertNull(provider.js_executeStoredProcedure("example", "{call p()}", null, 10));
	}

	// --- cache flushes ---

	@Test
	public void flushAllClientsCache() throws Exception
	{
		when(Boolean.valueOf(service.flushAllClientsCache("client", true, "example_prod", "orders", null))).thenReturn(Boolean.TRUE);

		assertTrue(provider.js_flushAllClientsCache("example", "orders"));

		// outside a transaction the own client is flushed by the server notify
		verify(databaseManager, never()).notifyDataChange(anyString(), any(), anyInt());
	}

	@Test
	public void inATransactionTheOwnClientIsFlushedDirectly() throws Exception
	{
		when(databaseManager.getTransactionID("example")).thenReturn("tid");

		provider.js_flushAllClientsCache("example", "orders");

		verify(databaseManager).notifyDataChange("db:/example/orders", null, 0);
		verify(service).flushAllClientsCache("client", true, "example_prod", "orders", "tid");
	}

	@Test
	public void aFailingFlushIsFalse() throws Exception
	{
		when(Boolean.valueOf(service.flushAllClientsCache(anyString(), any(Boolean.class), anyString(), anyString(), any()))).thenThrow(new RemoteException("x"));

		assertFalse(provider.js_flushAllClientsCache("example", "orders"));
		assertNotNull(provider.js_getException());
	}

	@Test
	public void notifyDataChangeNeedsPks() throws Exception
	{
		assertFalse(provider.js_notifyDataChange("example", "orders", null, ISQLActionTypes.UPDATE_ACTION));
		assertFalse(provider.js_notifyDataChange("example", "orders", new BufferedDataSet(), ISQLActionTypes.UPDATE_ACTION));
		verify(service, never()).notifyDataChange(anyString(), any(Boolean.class), anyString(), anyString(), any(), anyInt(), any());
	}

	@Test
	public void notifyDataChangeUpdatesTheOwnClientAndTheOthers() throws Exception
	{
		IDataSet pks = new BufferedDataSet(new String[] { "id" }, List.<Object[]> of(new Object[] { Integer.valueOf(5) }));
		when(Boolean.valueOf(service.notifyDataChange("client", false, "example_prod", "orders", pks, ISQLActionTypes.DELETE_ACTION, null)))
			.thenReturn(Boolean.TRUE);

		assertTrue(provider.js_notifyDataChange("example", "orders", pks, ISQLActionTypes.DELETE_ACTION));

		verify(databaseManager).notifyDataChange("db:/example/orders", pks, ISQLActionTypes.DELETE_ACTION);
	}

	@Test
	public void aFailingNotifyIsFalse() throws Exception
	{
		IDataSet pks = new BufferedDataSet(new String[] { "id" }, List.<Object[]> of(new Object[] { Integer.valueOf(5) }));
		when(access.getRemoteService("servoy.ISQLService")).thenThrow(new RemoteException("no service"));

		assertFalse(provider.js_notifyDataChange("example", "orders", pks, ISQLActionTypes.INSERT_ACTION));
		assertNotNull(provider.js_getException());
	}

	// --- uuid helpers ---

	@Test
	public void uuidConversions()
	{
		UUID uuid = UUID.randomUUID();

		byte[] bytes = provider.js_convertUUIDToBytes(uuid.toString());

		assertArrayEquals(uuid.toBytes(), bytes);
		assertEquals(uuid.toString(), provider.js_convertUUIDToString(bytes));
	}
}
