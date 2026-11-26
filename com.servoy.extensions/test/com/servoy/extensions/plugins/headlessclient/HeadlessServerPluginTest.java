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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.JavaScriptException;

import com.servoy.j2db.ExitScriptException;
import com.servoy.j2db.IServiceProvider;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.server.shared.IHeadlessClient;

/**
 * The server side of the headless client plugin with mocked headless clients (the factory is replaced through the
 * package-private createHeadlessClient seam); values travel as JSON like over the remote interface.
 */
public class HeadlessServerPluginTest
{
	private IServerAccess access;
	private final List<String> created = new ArrayList<>();
	private final List<IHeadlessClient> toCreate = new ArrayList<>();
	private HeadlessServerPlugin server;

	@BeforeEach
	public void setUp()
	{
		access = mock(IServerAccess.class);
		when(Boolean.valueOf(access.isAuthenticated("caller"))).thenReturn(Boolean.TRUE);
		when(Boolean.valueOf(access.isServerProcess("batch"))).thenReturn(Boolean.TRUE);
		server = new HeadlessServerPlugin()
		{
			@Override
			IHeadlessClient createHeadlessClient(String solutionname, String username, String password, Object[] solutionOpenMethodArgs)
			{
				created.add(solutionname + "/" + username + "/" + password + "/" + (solutionOpenMethodArgs == null ? 0 : solutionOpenMethodArgs.length));
				return toCreate.remove(0);
			}
		};
		server.load();
		server.initialize(access);
	}

	/** A valid headless client that has the solution open. */
	static IHeadlessClient client(String solution)
	{
		IHeadlessClient client = mock(IHeadlessClient.class, withSettings().extraInterfaces(IServiceProvider.class));
		when(Boolean.valueOf(client.isValid())).thenReturn(Boolean.TRUE);
		Solution sol = mock(Solution.class);
		when(sol.getName()).thenReturn(solution);
		when(((IServiceProvider)client).getSolution()).thenReturn(sol);
		IClientPluginAccess pluginAccess = mock(IClientPluginAccess.class);
		when(client.getPluginAccess()).thenReturn(pluginAccess);
		return client;
	}

	@Test
	public void thePluginRegistersItselfAsRemoteService() throws Exception
	{
		verify(access).registerRemoteService(IHeadlessServer.class.getName(), server);
		assertEquals("HeadlessServerPlugin", server.getProperties().get(IServerPlugin.DISPLAY_NAME));
		assertTrue(server.getRequiredPropertyNames().isEmpty());
		assertNull(server.getPreferencePanels());
		server.unload();

		IServerAccess failing = mock(IServerAccess.class);
		doThrow(new java.rmi.RemoteException("no rmi")).when(failing).registerRemoteService(anyString(), any());
		// a failing registration is only logged
		new HeadlessServerPlugin().initialize(failing);
	}

	// --- creating clients ---

	@Test
	public void onlyAuthenticatedClientsOrServerProcessesCanCreateClients() throws Exception
	{
		toCreate.add(client("crm"));

		assertThrows(SecurityException.class, () -> server.createClient("crm", "u", "p", null, "stranger"));
		String key = server.createClient("crm", "u", "p", new Object[] { "a" }, "batch");

		assertEquals(List.of("crm/u/p/1"), created);
		assertTrue(server.isValid(key));
		assertFalse(server.isValid("unknown"));
	}

	@Test
	public void eachCreateGivesANewClient() throws Exception
	{
		toCreate.add(client("crm"));
		toCreate.add(client("crm"));

		assertNotEquals(server.createClient("crm", "u", "p", null, "caller"), server.createClient("crm", "u", "p", null, "caller"));
		assertEquals(2, created.size());
	}

	@Test
	public void getOrCreateReusesTheClientWithTheSameSolution() throws Exception
	{
		toCreate.add(client("crm"));

		assertEquals("key1", server.getOrCreateClient("key1", "crm", "u", "p", null, "caller"));
		assertEquals("key1", server.getOrCreateClient("key1", "crm", "u", "p", null, "caller"));

		assertEquals(1, created.size());
	}

	@Test
	public void aClientWithAnotherSolutionIsNotReused() throws Exception
	{
		toCreate.add(client("crm"));
		server.getOrCreateClient("key1", "crm", "u", "p", null, "caller");

		ClientNotFoundException ex = assertThrows(ClientNotFoundException.class, () -> server.getOrCreateClient("key1", "hr", "u", "p", null, "caller"));

		assertEquals("crm", ex.getInfo());
		assertEquals("Client 'key1' not found", ex.getMessage());
	}

	@Test
	public void aClientWithoutSolutionIsNotReused() throws Exception
	{
		IHeadlessClient noSolution = client("crm");
		when(((IServiceProvider)noSolution).getSolution()).thenReturn(null);
		toCreate.add(noSolution);
		server.getOrCreateClient("key1", "crm", "u", "p", null, "caller");

		ClientNotFoundException ex = assertThrows(ClientNotFoundException.class, () -> server.getOrCreateClient("key1", "crm", "u", "p", null, "caller"));

		assertEquals("<null>", ex.getInfo());
	}

	@Test
	public void invalidClientsAreReplaced() throws Exception
	{
		IHeadlessClient first = client("crm");
		toCreate.add(first);
		toCreate.add(client("crm"));
		server.getOrCreateClient("key1", "crm", "u", "p", null, "caller");
		when(Boolean.valueOf(first.isValid())).thenReturn(Boolean.FALSE);

		server.getOrCreateClient("key1", "crm", "u", "p", null, "caller");

		assertEquals(2, created.size());
		assertTrue(server.isValid("key1"));
	}

	// --- calling methods ---

	private IHeadlessClient started(String key) throws Exception
	{
		IHeadlessClient client = client("crm");
		toCreate.add(client);
		server.getOrCreateClient(key, "crm", "u", "p", null, "caller");
		return client;
	}

	@Test
	public void aMethodIsCalledWithJsonArgumentsAndGivesAJsonResult() throws Exception
	{
		IHeadlessClient client = started("key1");
		List<Object> received = new ArrayList<>();
		when(client.getPluginAccess().executeMethod(org.mockito.ArgumentMatchers.eq("forms.orders"), org.mockito.ArgumentMatchers.eq("total"), any(),
			org.mockito.ArgumentMatchers.eq(false))).thenAnswer(inv -> {
				received.addAll(java.util.Arrays.asList((Object[])inv.getArgument(2)));
				return Integer.valueOf(42);
			});

		Object result = server.executeMethod("key1", "forms.orders", "total", new String[] { "\"x\"", "2" }, "caller");

		assertEquals("42", result);
		assertEquals("x", received.get(0));
		assertEquals(2, ((Number)received.get(1)).intValue());
		assertNull(server.executeMethod("key1", null, "noArgs", null, "caller"));
	}

	@Test
	public void aScriptErrorIsWrappedWithItsJsonValue() throws Exception
	{
		IHeadlessClient client = started("key1");
		JavaScriptException thrown = new JavaScriptException("boom", "script", 1);
		when(client.getPluginAccess().executeMethod(null, "fail", null, false)).thenThrow(thrown);
		EvaluatorException rhino = new EvaluatorException("bad script");
		when(client.getPluginAccess().executeMethod(null, "broken", null, false)).thenThrow(rhino);

		ExceptionWrapper js = assertThrows(ExceptionWrapper.class, () -> server.executeMethod("key1", null, "fail", null, "caller"));
		ExceptionWrapper evaluator = assertThrows(ExceptionWrapper.class, () -> server.executeMethod("key1", null, "broken", null, "caller"));

		assertEquals("\"boom\"", js.getMessage());
		assertEquals("\"bad script\"", evaluator.getMessage());
	}

	@Test
	public void anExitOfTheScriptGivesNoResult() throws Exception
	{
		IHeadlessClient client = started("key1");
		JavaScriptException exit = new JavaScriptException(new ExitScriptException("exit"), "script", 1);
		when(client.getPluginAccess().executeMethod(null, "exit", null, false)).thenThrow(exit);

		assertNull(server.executeMethod("key1", null, "exit", null, "caller"));
	}

	@Test
	public void callingAnUnknownClientFails()
	{
		assertThrows(ClientNotFoundException.class, () -> server.executeMethod("nope", null, "m", null, "caller"));
		assertThrows(ClientNotFoundException.class, () -> server.getDataProviderValue("nope", null, "x", "caller", null));
		assertThrows(ClientNotFoundException.class, () -> server.setDataProviderValue("nope", null, "x", "1", "caller", null));
		assertThrows(ClientNotFoundException.class, () -> server.shutDown("nope", true));
	}

	// --- data provider values ---

	@Test
	public void dataProviderValuesAreReadAndWrittenAsJson() throws Exception
	{
		IHeadlessClient client = started("key1");
		when(client.getDataProviderValue("forms.orders", "total")).thenReturn(Integer.valueOf(7));
		when(client.setDataProviderValue("forms.orders", "total", Integer.valueOf(8))).thenReturn(Integer.valueOf(7));

		assertEquals("7", server.getDataProviderValue("key1", "forms.orders", "total", "caller", null));
		assertEquals("7", server.setDataProviderValue("key1", "forms.orders", "total", "8", "caller", null));
	}

	@Test
	public void withAMethodNameValuesAreOnlyAccessibleWhileThatMethodRuns() throws Exception
	{
		IHeadlessClient client = started("key1");
		when(client.getDataProviderValue(null, "scopes.globals.x")).thenReturn("value");
		List<Object> duringCall = new ArrayList<>();
		when(client.getPluginAccess().executeMethod(null, "remote", null, false)).thenAnswer(inv -> {
			duringCall.add(server.getDataProviderValue("key1", null, "scopes.globals.x", "caller", "remote"));
			duringCall.add(server.getDataProviderValue("key1", null, "scopes.globals.x", "other", "remote"));
			duringCall.add(server.getDataProviderValue("key1", null, "scopes.globals.x", "caller", "otherMethod"));
			duringCall.add(server.setDataProviderValue("key1", null, "scopes.globals.x", "\"y\"", "caller", "otherMethod"));
			return null;
		});

		server.executeMethod("key1", null, "remote", null, "caller");

		assertEquals(List.of("\"value\"", UndefinedMarker.INSTANCE, UndefinedMarker.INSTANCE, UndefinedMarker.INSTANCE), duringCall);
		// no method is running now
		assertSame(UndefinedMarker.INSTANCE, server.getDataProviderValue("key1", null, "scopes.globals.x", "caller", "remote"));
		assertSame(UndefinedMarker.INSTANCE, server.setDataProviderValue("key1", null, "scopes.globals.x", "\"y\"", "caller", "remote"));
		verify(client, never()).setDataProviderValue(anyString(), anyString(), any());
	}

	@Test
	public void aValueThatCannotBeSerializedFails() throws Exception
	{
		IHeadlessClient client = started("key1");
		when(client.getDataProviderValue(null, "fs")).thenReturn(mock(com.servoy.j2db.dataprocessing.IFoundSet.class));
		when(client.setDataProviderValue(null, "fs", null)).thenReturn(mock(com.servoy.j2db.dataprocessing.IRecord.class));

		assertTrue(assertThrows(RuntimeException.class, () -> server.getDataProviderValue("key1", null, "fs", "caller", null)).getMessage()
			.startsWith("exception when serializing value"));
		assertTrue(assertThrows(RuntimeException.class, () -> server.setDataProviderValue("key1", null, "fs", null, "caller", null)).getMessage()
			.startsWith("exception when serializing value"));
		assertTrue(assertThrows(RuntimeException.class, () -> server.setDataProviderValue("key1", null, "fs", "{not json", "caller", null))
			.getMessage().startsWith("exception when deserializing value"));
	}

	// --- shutdown ---

	@Test
	public void aShutdownClosesAndForgetsTheClient() throws Exception
	{
		IHeadlessClient first = started("key1");
		IHeadlessClient second = started("key2");

		server.shutDown("key1", false);
		server.shutDown("key2", true);

		verify(first).shutDown(false);
		verify(second).shutDown(true);
		assertFalse(server.isValid("key1"));
		assertFalse(server.isValid("key2"));
	}

	@Test
	public void aFailingShutdownStillForgetsTheClient() throws Exception
	{
		IHeadlessClient client = started("key1");
		doThrow(new IllegalStateException("stuck")).when(client).shutDown(anyBoolean());

		assertThrows(IllegalStateException.class, () -> server.shutDown("key1", false));

		assertFalse(server.isValid("key1"));
		// the method call lock is released: a new client with the same key can be used
		IHeadlessClient again = started("key1");
		when(again.getPluginAccess().executeMethod(null, "m", null, false)).thenReturn("ok");
		assertEquals("\"ok\"", server.executeMethod("key1", null, "m", null, "caller"));
	}
}
