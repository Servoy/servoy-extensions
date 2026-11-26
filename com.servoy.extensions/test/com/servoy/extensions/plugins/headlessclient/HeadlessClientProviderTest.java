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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Undefined;

import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.JSEvent;

/**
 * The headlessclient scripting API (provider and JSClient) against a mocked IHeadlessServer. Queued calls run on a
 * same-thread executor and the shutdown thread runs on the calling thread (runInBackground seam): no waiting.
 */
public class HeadlessClientProviderTest
{
	private IClientPluginAccess access;
	private IHeadlessServer server;
	private HeadlessClientPlugin plugin;
	private HeadlessClientProvider provider;
	private final List<String> backgroundThreads = new ArrayList<>();

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IClientPluginAccess.class);
		server = mock(IHeadlessServer.class);
		when(access.getRemoteService(IHeadlessServer.class.getName())).thenReturn(server);
		when(access.getClientID()).thenReturn("caller");
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		doAnswer(inv -> {
			((Runnable)inv.getArgument(0)).run();
			return null;
		}).when(executor).execute(any(Runnable.class));
		when(access.getExecutor()).thenReturn(executor);
		plugin = new HeadlessClientPlugin()
		{
			@Override
			void runInBackground(Runnable task, String threadName)
			{
				backgroundThreads.add(threadName);
				task.run();
			}
		};
		plugin.load();
		plugin.initialize(access);
		provider = (HeadlessClientProvider)plugin.getScriptObject();
	}

	static Function callback(String name)
	{
		Function f = mock(Function.class);
		when(f.get("_scopename_", f)).thenReturn("globals");
		when(f.get("_methodname_", f)).thenReturn(name);
		return f;
	}

	private JSClient validClient(String id) throws Exception
	{
		when(server.createClient("crm", "u", "p", null, "caller")).thenReturn(id);
		when(Boolean.valueOf(server.isValid(id))).thenReturn(Boolean.TRUE);
		return provider.js_createClient("crm", "u", "p", null);
	}

	/** The JSEvent and JSClient the callback got. */
	private Object[] callbackArguments(String callbackName, int times) throws Exception
	{
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(access, times(times)).executeMethod(eq("scopes.globals"), eq(callbackName), args.capture(), eq(true));
		return args.getValue();
	}

	// --- plugin ---

	@Test
	public void plugin() throws Exception
	{
		assertEquals("headlessclient", plugin.getName());
		assertNull(plugin.getPreferencePanels());
		assertNull(plugin.getProperties());
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertSame(provider, plugin.getScriptObject());
		assertSame(access, plugin.getPluginAccess());
		assertSame(plugin.getJSONConverter(), plugin.getJSONConverter());
		assertEquals(List.of(JSClient.class), List.of(provider.getAllReturnedTypes()));
		assertNotNull(new HeadlessClientProvider().getAllReturnedTypes());
		plugin.propertyChange(null);
		plugin.unload();
		assertEquals("JSClient[null]", new JSClient().toString());
	}

	// --- creating and finding clients ---

	@Test
	public void aCreatedClientIsReusedFromThePool() throws Exception
	{
		JSClient client = validClient("id1");
		when(server.getOrCreateClient("id1", "crm", "u", "p", null, "caller")).thenReturn("id1");

		assertEquals("id1", client.js_getClientID());
		assertEquals("JSClient[id1]", client.toString());
		assertSame(client, provider.js_getClient("id1"));
		assertSame(client, provider.js_getOrCreateClient("id1", "crm", "u", "p", null));
		// the remote service is looked up once
		verify(access).getRemoteService(IHeadlessServer.class.getName());
	}

	@Test
	public void anInvalidClientIsNotReturnedAndLeavesThePool() throws Exception
	{
		JSClient client = validClient("id1");
		when(Boolean.valueOf(server.isValid("id1"))).thenReturn(Boolean.FALSE);

		assertNull(provider.js_getClient("id1"));

		when(Boolean.valueOf(server.isValid("id1"))).thenReturn(Boolean.TRUE);
		assertNotSame(client, provider.js_getClient("id1"));
	}

	@Test
	public void aClientWithAnotherSolutionFails() throws Exception
	{
		when(server.getOrCreateClient("id1", "hr", "u", "p", null, "caller")).thenThrow(new ClientNotFoundException("id1", "crm"));

		RuntimeException ex = assertThrows(RuntimeException.class, () -> provider.js_getOrCreateClient("id1", "hr", "u", "p", null));

		assertEquals("The client with the clientId: id1 was loaded with another solution: crm", ex.getMessage());
	}

	@Test
	public void serverErrorsGiveNoClient() throws Exception
	{
		when(server.createClient(anyString(), anyString(), anyString(), any(), anyString())).thenThrow(new SecurityException("Rejected"));
		when(server.getOrCreateClient(anyString(), anyString(), anyString(), anyString(), any(), anyString())).thenThrow(new SecurityException("Rejected"));
		when(Boolean.valueOf(server.isValid(anyString()))).thenThrow(new RemoteException("gone"));

		assertNull(provider.js_createClient("crm", "u", "p", null));
		assertNull(provider.js_getOrCreateClient("id1", "crm", "u", "p", null));
		assertNull(provider.js_getClient("id1"));
	}

	@Test
	public void noClientIdGivesNoClient() throws Exception
	{
		assertNull(provider.js_createClient("crm", "u", "p", null));
		assertNull(provider.js_getOrCreateClient("id1", "crm", "u", "p", null));
	}

	@Test
	public void withoutRemoteServiceTheCallsGiveNothing() throws Exception
	{
		when(access.getRemoteService(anyString())).thenThrow(new IllegalStateException("not connected"));

		assertNull(provider.js_createClient("crm", "u", "p", null));
		assertNull(provider.js_getClient("id1"));
	}

	// --- queued methods ---

	@Test
	public void aQueuedMethodGivesItsResultToTheCallback() throws Exception
	{
		JSClient client = validClient("id1");
		when(server.executeMethod("id1", "forms.orders", "total", new String[] { "\"x\"", "2" }, "caller")).thenReturn("42");

		client.js_queueMethod("forms.orders", "total", new Object[] { "x", Integer.valueOf(2) }, callback("onDone"));

		Object[] args = callbackArguments("onDone", 1);
		JSEvent event = (JSEvent)args[0];
		assertEquals(JSClient.CALLBACK_EVENT, event.getType());
		assertEquals("total", event.getName());
		assertEquals(Integer.valueOf(42), event.getData());
		assertSame(client, args[1]);
	}

	@Test
	public void aQueuedMethodWithoutCallbackOnlyRuns() throws Exception
	{
		JSClient client = validClient("id1");

		client.js_queueMethod(null, "fireAndForget", null);

		verify(server).executeMethod("id1", null, "fireAndForget", null, "caller");
		verify(access, never()).executeMethod(anyString(), anyString(), any(), org.mockito.ArgumentMatchers.anyBoolean());
	}

	@Test
	public void aScriptErrorGivesTheExceptionCallback() throws Exception
	{
		JSClient client = validClient("id1");
		when(server.executeMethod("id1", null, "fail", null, "caller")).thenThrow(new ExceptionWrapper("\"boom\""));
		when(server.executeMethod("id1", null, "badJson", null, "caller")).thenThrow(new ExceptionWrapper("{not json"));

		client.js_queueMethod(null, "fail", null, callback("onFail"));
		client.js_queueMethod(null, "badJson", null, callback("onBadJson"));

		JSEvent event = (JSEvent)callbackArguments("onFail", 1)[0];
		assertEquals(JSClient.CALLBACK_EXCEPTION_EVENT, event.getType());
		assertEquals("boom", event.getData());
		// when the message is no json it is given as it is
		assertEquals("{not json", ((JSEvent)callbackArguments("onBadJson", 1)[0]).getData());
	}

	@Test
	public void anyOtherErrorGivesItsMessageToTheExceptionCallback() throws Exception
	{
		JSClient client = validClient("id1");
		when(server.executeMethod("id1", null, "gone", null, "caller")).thenThrow(new ClientNotFoundException("id1"));

		client.js_queueMethod(null, "gone", null, callback("onGone"));
		client.js_queueMethod(null, "gone", null);

		JSEvent event = (JSEvent)callbackArguments("onGone", 1)[0];
		assertEquals(JSClient.CALLBACK_EXCEPTION_EVENT, event.getType());
		assertEquals("Client 'id1' not found", event.getData());
	}

	@Test
	public void queuedMethodsRunOneAfterTheOther() throws Exception
	{
		JSClient client = validClient("id1");
		List<String> order = new ArrayList<>();
		when(server.executeMethod(eq("id1"), isNull(), anyString(), isNull(), eq("caller"))).thenAnswer(inv -> {
			String method = inv.getArgument(2);
			order.add("start " + method);
			// queued while the first one runs: it waits until the first one is done
			if (method.equals("first")) client.js_queueMethod(null, "second", null);
			order.add("end " + method);
			return null;
		});

		client.js_queueMethod(null, "first", null);

		assertEquals(List.of("start first", "end first", "start second", "end second"), order);
	}

	@Test
	public void queuedMethodsAreDroppedWhenTheClientIsGone() throws Exception
	{
		JSClient client = validClient("id1");
		when(server.executeMethod(eq("id1"), isNull(), anyString(), isNull(), eq("caller"))).thenAnswer(inv -> {
			if (inv.getArgument(2).equals("first"))
			{
				client.js_queueMethod(null, "second", null);
				when(Boolean.valueOf(server.isValid("id1"))).thenReturn(Boolean.FALSE);
			}
			return null;
		});

		client.js_queueMethod(null, "first", null);

		verify(server, never()).executeMethod("id1", null, "second", null, "caller");
		// and the invalid client left the pool
		when(Boolean.valueOf(server.isValid("id1"))).thenReturn(Boolean.TRUE);
		assertNotSame(client, provider.js_getClient("id1"));
	}

	// --- validity and shutdown ---

	@Test
	public void validity() throws Exception
	{
		JSClient client = validClient("id1");

		assertTrue(client.js_isValid());
		when(Boolean.valueOf(server.isValid("id1"))).thenThrow(new RemoteException("gone"));
		assertFalse(client.js_isValid());
	}

	@Test
	public void shutdownRunsInTheBackground() throws Exception
	{
		JSClient client = validClient("id1");
		doAnswer(inv -> {
			when(Boolean.valueOf(server.isValid("id1"))).thenReturn(Boolean.FALSE);
			return null;
		}).when(server).shutDown("id1", false);

		client.js_shutdown();
		client.js_shutDown(true);
		client.js_shutdown(true);

		verify(server).shutDown("id1", false);
		verify(server, times(2)).shutDown("id1", true);
		assertEquals("JSClient shutdown: id1 by client :caller", backgroundThreads.get(0));
		// the client left the pool
		when(Boolean.valueOf(server.isValid("id1"))).thenReturn(Boolean.TRUE);
		assertNotSame(client, provider.js_getClient("id1"));
	}

	@Test
	public void aFailingShutdownIsLogged() throws Exception
	{
		JSClient client = validClient("id1");
		doThrow(new ClientNotFoundException("id1")).when(server).shutDown("id1", false);

		client.js_shutdown();

		verify(server).shutDown("id1", false);
	}

	// --- data provider values ---

	@Test
	public void dataProviderValuesAreConvertedFromJson() throws Exception
	{
		JSClient client = validClient("id1");
		when(server.getDataProviderValue("id1", null, "scopes.globals.x", "caller", null)).thenReturn("7");
		when(server.getDataProviderValue("id1", null, "scopes.globals.x", "caller", "remote")).thenReturn(UndefinedMarker.INSTANCE);
		when(server.setDataProviderValue("id1", null, "scopes.globals.x", "8", "caller", null)).thenReturn("7");
		when(server.setDataProviderValue("id1", null, "scopes.globals.x", "8", "caller", "remote")).thenReturn(UndefinedMarker.INSTANCE);
		when(server.getDataProviderValue("id1", null, "raw", "caller", null)).thenReturn("{not json");

		assertEquals(Integer.valueOf(7), client.js_getDataProviderValue(null, "scopes.globals.x"));
		assertSame(Undefined.instance, client.js_getDataProviderValue(null, "scopes.globals.x", "remote"));
		assertEquals(Integer.valueOf(7), client.js_setDataProviderValue(null, "scopes.globals.x", Integer.valueOf(8)));
		assertSame(Undefined.instance, client.js_setDataProviderValue(null, "scopes.globals.x", Integer.valueOf(8), "remote"));
		// a value that is no json is given as it is
		assertEquals("{not json", client.js_getDataProviderValue(null, "raw"));
	}

	@Test
	public void dataProviderErrorsAreRethrown() throws Exception
	{
		JSClient client = validClient("id1");
		ClientNotFoundException gone = new ClientNotFoundException("id1");
		when(server.getDataProviderValue("id1", null, "x", "caller", null)).thenThrow(gone);
		when(server.setDataProviderValue("id1", null, "x", "1", "caller", null)).thenThrow(gone);

		assertSame(gone, assertThrows(RuntimeException.class, () -> client.js_getDataProviderValue(null, "x")).getCause());
		assertSame(gone, assertThrows(RuntimeException.class, () -> client.js_setDataProviderValue(null, "x", Integer.valueOf(1))).getCause());
	}
}
