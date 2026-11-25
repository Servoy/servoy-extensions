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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.j2db.dataprocessing.IDataServer;
import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.dataprocessing.ISQLActionTypes;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;
import com.servoy.j2db.util.ITransactionConnection;
import com.servoy.j2db.util.ServoyException;

/**
 * The server side of rawSQL against a mocked server access and mocked JDBC objects. No database.
 */
public class SQLProcessorTest
{
	private final Properties settings = new Properties();
	private IServerAccess access;
	private IDataServer dataServer;
	private Connection connection;
	private SQLProcessor processor;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IServerAccess.class);
		when(access.getSettings()).thenReturn(settings);
		when(Boolean.valueOf(access.isAuthenticated("client"))).thenReturn(Boolean.TRUE);
		when(Boolean.valueOf(access.isServerProcess("server-process"))).thenReturn(Boolean.TRUE);
		connection = mock(Connection.class);
		when(access.getDBServerConnection(eq("db"), any(), anyString())).thenReturn(connection);
		ApplicationServerRegistry.destroy();
		IApplicationServerSingleton server = mock(IApplicationServerSingleton.class);
		dataServer = mock(IDataServer.class);
		when(server.getDataServer()).thenReturn(dataServer);
		ApplicationServerRegistry.setApplicationServerSingleton(server);
		processor = new SQLProcessor();
		processor.load();
		processor.initialize(access);
	}

	@AfterEach
	public void tearDown() throws Exception
	{
		processor.unload();
		ApplicationServerRegistry.destroy();
	}

	// --- plugin ---

	@Test
	public void pluginDescription() throws Exception
	{
		verify(access).registerRMIService("servoy.ISQLService", processor);
		assertEquals("Raw SQL Plugin", processor.getProperties().get(IServerPlugin.DISPLAY_NAME));
		assertNull(processor.getPreferencePanels());
		assertTrue(processor.getRequiredPropertyNames().containsKey("servoy.rawSQL.allowClientCacheFlushes"));
	}

	@Test
	public void aFailingRegistrationIsOnlyLogged() throws Exception
	{
		IServerAccess failing = mock(IServerAccess.class);
		doThrow(new RemoteException("no rmi")).when(failing).registerRMIService(anyString(), any());

		new SQLProcessor().initialize(failing);
	}

	// --- access ---

	@Test
	public void unauthenticatedClientsAreRejected() throws Exception
	{
		assertFalse(processor.executeSQL("stranger", "db", "delete from x", null, null, 0));
		assertNull(processor.executeStoredProcedure("stranger", "db", null, "{call p()}", null, new int[0], 0, 10));
		assertNull(processor.executeStoredProcedure("stranger", "db", null, "{call p()}", null, 0, 10));
		assertFalse(processor.notifyDataChange("stranger", false, "db", "t", null, 0, null));
		verify(access, never()).getDBServerConnection(anyString(), any(), anyString());
	}

	// --- executeSQL ---

	@Test
	public void aStatementWithoutArgumentsIsTrackedAndTimed() throws Exception
	{
		Statement statement = mock(Statement.class);
		when(connection.createStatement()).thenReturn(statement);
		when(Long.valueOf(access.addTrackStatement(eq("client"), eq("db"), eq("delete from x"), anyLong(), eq(statement)))).thenReturn(Long.valueOf(7));
		when(access.addPerformanceTiming(eq("db"), eq("delete from x"), anyLong(), eq("client"))).thenReturn(Long.valueOf(3));

		assertTrue(processor.executeSQL("client", "db", "delete from x", null, null, 30));

		verify(statement).setQueryTimeout(30);
		verify(statement).execute("delete from x");
		verify(access).endPerformanceTiming("db", Long.valueOf(3), "client");
		verify(access).removeTrackStatement(7);
		verify(statement).close();
		// no transaction: the connection is closed
		verify(connection).close();
	}

	@Test
	public void aServerProcessMayExecute() throws Exception
	{
		when(access.getDBServerConnection(eq("db"), any(), eq("server-process"))).thenReturn(connection);
		when(connection.createStatement()).thenReturn(mock(Statement.class));

		assertTrue(processor.executeSQL("server-process", "db", "update x set a=1", new Object[0], null, 0));
	}

	@Test
	public void argumentsArePreparedAndDatesBecomeTimestamps() throws Exception
	{
		PreparedStatement statement = mock(PreparedStatement.class);
		when(connection.prepareStatement("update x set a=?, b=?, c=? where d=?")).thenReturn(statement);
		Date date = new Date(1000);
		java.sql.Date sqlDate = new java.sql.Date(2000);

		assertTrue(processor.executeSQL("client", "db", "update x set a=?, b=?, c=? where d=?", new Object[] { "v", date, null, sqlDate }, null, 5));

		verify(statement).setObject(1, "v");
		verify(statement).setObject(2, new Timestamp(1000));
		verify(statement).setObject(3, null);
		// only java.util.Date itself is converted, subclasses are passed as is
		verify(statement).setObject(4, sqlDate);
		verify(statement).setQueryTimeout(5);
		verify(statement).execute();
		verify(access, never()).addTrackStatement(anyString(), anyString(), anyString(), anyLong(), any());
	}

	@Test
	public void inATransactionTheConnectionIsReleasedNotClosed() throws Exception
	{
		ITransactionConnection transactionConnection = mock(ITransactionConnection.class);
		when(access.getDBServerConnection("db", "tid-1", "client")).thenReturn(transactionConnection);
		when(transactionConnection.createStatement()).thenReturn(mock(Statement.class));

		assertTrue(processor.executeSQL("client", "db", "delete from x", null, "tid-1", 0));

		verify(transactionConnection).release();
		verify(transactionConnection, never()).close();
	}

	@Test
	public void withoutConnectionNothingIsExecuted() throws Exception
	{
		assertFalse(processor.executeSQL("client", "unknown", "delete from x", null, null, 0));
	}

	@Test
	public void aFailingStatementIsReportedAsARuntimeException() throws Exception
	{
		Statement statement = mock(Statement.class);
		when(connection.createStatement()).thenReturn(statement);
		when(Boolean.valueOf(statement.execute("bad sql"))).thenThrow(new SQLException("syntax error"));

		RuntimeException e = assertThrows(RuntimeException.class, () -> processor.executeSQL("client", "db", "bad sql", null, null, 0));

		assertTrue(e.getMessage().contains("syntax error"), e.getMessage());
		verify(statement).close();
		verify(connection).close();
	}

	// --- stored procedures ---

	@Test
	public void storedProceduresAreDelegated() throws Exception
	{
		IDataSet set = mock(IDataSet.class);
		IDataSet[] sets = { set };
		int[] inOut = { 0, 1 };
		Object[] args = { "a", "b" };
		when(access.executeStoredProcedure("client", "db", "tid", "{call p(?,?)}", args, inOut, 0, 10)).thenReturn(set);
		when(access.executeStoredProcedure("client", "db", "tid", "{call q()}", null, 0, 5)).thenReturn(sets);

		assertSame(set, processor.executeStoredProcedure("client", "db", "tid", "{call p(?,?)}", args, inOut, 0, 10));
		assertSame(sets, processor.executeStoredProcedure("client", "db", "tid", "{call q()}", null, 0, 5));
	}

	@Test
	public void storedProcedureErrorsBecomeRepositoryExceptions() throws Exception
	{
		ServoyException error = new ServoyException(ServoyException.UNKNOWN_DATABASE_EXCEPTION);
		when(access.executeStoredProcedure(anyString(), anyString(), any(), anyString(), any(), any(int[].class), anyInt(), anyInt())).thenThrow(error);
		when(access.executeStoredProcedure(anyString(), anyString(), any(), anyString(), any(), anyInt(), anyInt())).thenThrow(error);

		RepositoryException single = assertThrows(RepositoryException.class,
			() -> processor.executeStoredProcedure("client", "db", null, "{call p()}", null, new int[0], 0, 1));
		assertThrows(RepositoryException.class, () -> processor.executeStoredProcedure("client", "db", null, "{call p()}", null, 0, 1));
		assertInstanceOf(RepositoryException.class, single);
	}

	// --- cache flushes ---

	@Test
	public void notifyDataChangeGoesToTheDataServer() throws Exception
	{
		IDataSet pks = mock(IDataSet.class);
		when(Boolean.valueOf(dataServer.notifyDataChange("client", false, "db", "t", pks, ISQLActionTypes.UPDATE_ACTION, "tid"))).thenReturn(Boolean.TRUE);

		assertTrue(processor.notifyDataChange("client", false, "db", "t", pks, ISQLActionTypes.UPDATE_ACTION, "tid"));
	}

	@Test
	public void aFullFlushIsANotifyWithoutPks() throws Exception
	{
		when(Boolean.valueOf(dataServer.notifyDataChange("client", true, "db", "t", null, ISQLActionTypes.NO_ACTION, null))).thenReturn(Boolean.TRUE);

		assertTrue(processor.flushAllClientsCache("client", true, "db", "t", null));
	}

	@Test
	public void flushesCanBeDisabled() throws Exception
	{
		settings.setProperty("servoy.rawSQL.allowClientCacheFlushes", "false");

		assertFalse(processor.flushAllClientsCache("client", true, "db", "t", null));
		assertFalse(processor.notifyDataChange("client", true, "db", "t", mock(IDataSet.class), ISQLActionTypes.DELETE_ACTION, null));
		verify(dataServer, never()).notifyDataChange(anyString(), any(Boolean.class), anyString(), anyString(), any(), anyInt(), isNull());
	}
}
