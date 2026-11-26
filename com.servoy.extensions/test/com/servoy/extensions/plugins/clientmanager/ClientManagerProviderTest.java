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
package com.servoy.extensions.plugins.clientmanager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.beans.PropertyChangeEvent;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Function;

import com.servoy.j2db.dataprocessing.BufferedDataSet;
import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.dataprocessing.RowManager;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.server.shared.IClientInformation;

/**
 * The clientmanager scripting API on a (non smart) client. The remote service is a real ClientManagerServer on a mocked
 * server access, or a mocked IClientManagerService for the error paths; no RMI.
 */
public class ClientManagerProviderTest
{
	private IClientPluginAccess access;
	private IServerAccess serverAccess;
	private ClientManagerServer server;
	private ClientManagerPlugin plugin;
	private ClientManagerProvider provider;

	@BeforeEach
	public void setUp() throws Exception
	{
		serverAccess = mock(IServerAccess.class);
		server = new ClientManagerServer();
		server.initialize(serverAccess);
		access = mock(IClientPluginAccess.class);
		when(access.getRemoteService(IClientManagerService.class.getName())).thenReturn(server);
		when(access.getClientID()).thenReturn("me");
		plugin = new ClientManagerPlugin();
		plugin.load();
		plugin.initialize(access);
		provider = (ClientManagerProvider)plugin.getScriptObject();
	}

	static Function callback(String name)
	{
		Function f = mock(Function.class);
		when(f.get("_scopename_", f)).thenReturn("globals");
		when(f.get("_methodname_", f)).thenReturn(name);
		return f;
	}

	private IClientManagerService failingService() throws Exception
	{
		IClientManagerService service = mock(IClientManagerService.class);
		RemoteException failure = new RemoteException("server gone");
		when(Boolean.valueOf(service.isInMaintenanceMode())).thenThrow(failure);
		when(service.getConnectedClients()).thenThrow(failure);
		when(service.getLocks()).thenThrow(failure);
		when(service.getServerBuildDate()).thenThrow(failure);
		org.mockito.Mockito.doThrow(failure).when(service).sendMessageToAllClients(anyString());
		org.mockito.Mockito.doThrow(failure).when(service).sendMessageToClient(anyString(), anyString());
		org.mockito.Mockito.doThrow(failure).when(service).shutDownAllClients(anyString());
		org.mockito.Mockito.doThrow(failure).when(service).shutDownClient(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
		org.mockito.Mockito.doThrow(failure).when(service).releaseLocks(anyString());
		org.mockito.Mockito.doThrow(failure).when(service).registerChannelListener(any());
		org.mockito.Mockito.doThrow(failure).when(service).deregisterChannelListener(any());
		org.mockito.Mockito.doThrow(failure).when(service).broadcastMessage(any(), anyString());
		when(access.getRemoteService(IClientManagerService.class.getName())).thenReturn(service);
		return service;
	}

	// --- plugin ---

	@Test
	public void plugin()
	{
		assertEquals("clientmanager", plugin.getName());
		assertNull(plugin.getProperties());
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertSame(provider, plugin.getScriptObject());
		assertSame(access, plugin.getClientPluginAccess());
		assertEquals(List.of(Broadcaster.class, JSClientInformation.class), List.of(provider.getAllReturnedTypes()));
		assertNotNull(new ClientManagerProvider().getAllReturnedTypes());
	}

	// --- server state and clients ---

	@Test
	public void theServerStateIsRead() throws Exception
	{
		when(Boolean.valueOf(serverAccess.isInMaintenanceMode())).thenReturn(Boolean.TRUE);

		assertTrue(provider.js_isInMaintenanceMode());
		assertEquals(server.getServerBuildDate(), provider.js_getServerBuildDate());
	}

	@Test
	public void theConnectedClientsCanBeFiltered()
	{
		IClientInformation[] connected = { ClientManagerServerTest.client("me", "batch"), ClientManagerServerTest.client("other") };
		when(serverAccess.getConnectedClients()).thenReturn(connected);

		JSClientInformation[] all = provider.js_getConnectedClients();
		JSClientInformation[] batch = provider.js_getConnectedClients("batch");

		assertEquals(2, all.length);
		assertEquals(1, batch.length);
		JSClientInformation me = batch[0];
		assertEquals("me", me.js_getClientID());
		assertEquals("me", me.js_getClientId());
		assertEquals(8, me.js_getApplicationType());
		assertEquals(new Date(1000), me.js_getLoginTime());
		assertEquals(new Date(2000), me.js_getIdleTime());
		assertEquals(new Date(3000), me.js_getLastAccessedTime());
		assertEquals("crm", me.js_getOpenSolutionName());
		assertEquals("10.0.0.2", me.js_getHostAddress());
		assertEquals("host-id-me", me.js_getHostIdentifier());
		assertEquals("host-me", me.js_getHostName());
		assertEquals("user-me", me.js_getUserName());
		assertEquals("uid-me", me.js_getUserUID());
		assertEquals("uid-me", me.js_getUserUid());
		assertArrayEquals(new String[] { "batch" }, me.getClientInfos());
		assertEquals("status me", me.js_getStatusLine());
		assertTrue(me.toString().startsWith("JSClientInformation [CI:[clientID=me"), me.toString());
	}

	@Test
	public void theCurrentClientIsFoundByItsId()
	{
		IClientInformation[] connected = { ClientManagerServerTest.client("other"), ClientManagerServerTest.client("me") };
		when(serverAccess.getConnectedClients()).thenReturn(connected);

		assertEquals("me", provider.getClientInformation().js_getClientID());

		when(access.getClientID()).thenReturn("unknown");
		assertNull(provider.getClientInformation());
	}

	@Test
	public void messagesShutdownsAndLocksGoToTheServer()
	{
		provider.js_sendMessageToAllClients("hi all");
		provider.js_sendMessageToClient("c1", "hi");
		provider.js_shutDownAllClients();
		provider.js_shutDownClient("c2");
		provider.js_shutDownClient("c3", true);
		provider.js_releaseLocks("c4");

		verify(serverAccess).sendMessageToAllClients("hi all");
		verify(serverAccess).sendMessageToClient("c1", "hi");
		// the own client is skipped
		verify(serverAccess).shutDownAllClients("me");
		verify(serverAccess).shutDownClient("c2", false);
		verify(serverAccess).shutDownClient("c3", true);
		verify(serverAccess).releaseLocks("c4");
	}

	@Test
	public void theLockingClientIsFoundByDatasourceAndPk()
	{
		String hash = RowManager.createPKHashKey(new Object[] { Integer.valueOf(42) });
		JSDataSet locks = new JSDataSet(new BufferedDataSet(new String[] { "datasource", "acquireDate", "clientId", "pkHash" },
			List.<Object[]> of(new Object[] { "db:/crm/orders", new Date(), "other", RowManager.createPKHashKey(new Object[] { Integer.valueOf(7) }) },
				new Object[] { "db:/crm/orders", new Date(), "me", hash })));
		when(serverAccess.getLocks()).thenReturn(locks);
		IClientInformation[] connected = { ClientManagerServerTest.client("other"), ClientManagerServerTest.client("me") };
		when(serverAccess.getConnectedClients()).thenReturn(connected);

		assertSame(locks, provider.js_getLocks());
		assertEquals("me", provider.js_getLockedByClient("db:/crm/orders", new Object[] { Integer.valueOf(42) }).js_getClientID());
		assertNull(provider.js_getLockedByClient("db:/crm/customers", new Object[] { Integer.valueOf(42) }));
		assertNull(provider.js_getLockedByClient("db:/crm/orders", new Object[] { Integer.valueOf(1) }));

		when(serverAccess.getLocks()).thenReturn(new JSDataSet());
		assertNull(provider.js_getLockedByClient("db:/crm/orders", new Object[] { Integer.valueOf(42) }));
	}

	@Test
	public void serverErrorsAreLoggedAndGiveDefaults() throws Exception
	{
		IClientManagerService service = failingService();

		assertFalse(provider.js_isInMaintenanceMode());
		assertNull(provider.js_getConnectedClients());
		assertNull(provider.getClientInformation());
		assertNull(provider.js_getServerBuildDate());
		assertNull(provider.js_getLockedByClient("db:/crm/orders", new Object[] { Integer.valueOf(1) }));
		assertEquals(0, provider.js_getLocks().js_getMaxRowIndex());
		provider.js_sendMessageToAllClients("x");
		provider.js_sendMessageToClient("c", "x");
		provider.js_shutDownAllClients();
		provider.js_shutDownClient("c");
		provider.js_shutDownClient("c", true);
		provider.js_releaseLocks("c");

		verify(service).releaseLocks("c");
	}

	@Test
	public void withoutRemoteServiceTheCallsFail() throws Exception
	{
		when(access.getRemoteService(anyString())).thenThrow(new RemoteException("not connected"));

		assertNull(plugin.getClientService());
		// current behaviour: the methods that only catch RemoteException fail with a NullPointerException
		assertThrows(NullPointerException.class, () -> provider.js_isInMaintenanceMode());
		assertThrows(NullPointerException.class, () -> provider.getClientInformation());
		// the others log it
		assertNull(provider.js_getConnectedClients());
		provider.js_sendMessageToAllClients("x");
	}

	@Test
	public void aClientWithoutClientInfosIsNotMatchedByAFilter()
	{
		IClientInformation noInfos = ClientManagerServerTest.client("plain");
		when(noInfos.getClientInfos()).thenReturn(null);
		IClientInformation[] connected = { noInfos, ClientManagerServerTest.client("me", "batch") };
		when(serverAccess.getConnectedClients()).thenReturn(connected);

		// current behaviour (bug): a client without client infos makes the filtered call fail (NullPointerException in
		// Arrays.asList), it is logged and null is returned instead of the matching clients
		assertNull(provider.js_getConnectedClients("batch"));
		assertEquals(2, provider.js_getConnectedClients().length);
	}

	// --- broadcasters ---

	@Test
	public void broadcastersOnAChannelReceiveEachOthersMessages() throws Exception
	{
		Broadcaster ann = provider.js_createBroadcaster("ann", "chat", callback("onAnn"));
		Broadcaster bob = provider.js_getOrCreateBroadcaster("bob", "chat", callback("onBob"));

		assertEquals("ann", ann.js_getName());
		assertEquals("chat", ann.js_getChannelName());
		assertTrue(ann.hasCallback());
		ann.js_broadcastMessage("hello");

		verify(access).executeMethod("scopes.globals", "onBob", new Object[] { "ann", "hello", "chat" }, true);
		verify(access, never()).executeMethod(org.mockito.ArgumentMatchers.eq("scopes.globals"), org.mockito.ArgumentMatchers.eq("onAnn"), any(),
			org.mockito.ArgumentMatchers.anyBoolean());
		assertSame(bob, plugin.getBroadcaster("bob", "chat"));
	}

	@Test
	public void getOrCreateReturnsTheExistingBroadcaster()
	{
		Broadcaster first = provider.js_getOrCreateBroadcaster("ann", "chat");
		Function onAnn = callback("onAnn");

		assertSame(first, provider.js_getOrCreateBroadcaster("ann", "chat"));
		assertFalse(first.hasCallback());
		assertSame(first, provider.js_getOrCreateBroadcaster("ann", "chat", onAnn));
		assertTrue(first.hasCallback());
		// the same callback again is not added a second time
		assertSame(first, provider.js_getOrCreateBroadcaster("ann", "chat", callback("onAnn")));
		assertNotSame(first, provider.js_getOrCreateBroadcaster("ann", "news"));
		assertNotSame(first, provider.js_getOrCreateBroadcaster("bob", "chat"));
	}

	@Test
	public void aNewCallbackRegistersTheBroadcasterAgain() throws Exception
	{
		Broadcaster ann = provider.js_createBroadcaster("ann", "chat", callback("first"));
		Broadcaster bob = provider.js_createBroadcaster("bob", "chat", callback("onBob"));

		assertSame(ann, provider.js_getOrCreateBroadcaster("ann", "chat", callback("second")));
		bob.js_broadcastMessage("hello");

		// current behaviour (bug): the listener is registered a second time, so it gets every message twice (both on the
		// new callback), and destroy only removes one of the registrations
		verify(access, times(2)).executeMethod("scopes.globals", "second", new Object[] { "bob", "hello", "chat" }, true);
		ann.js_destroy();
		bob.js_broadcastMessage("after destroy");
		verify(access).executeMethod("scopes.globals", "second", new Object[] { "bob", "after destroy", "chat" }, true);
	}

	@Test
	public void theDeprecatedGetBroadcaster() throws Exception
	{
		Broadcaster ann = provider.js_getBroadcaster("ann", "chat", callback("onAnn"));

		assertSame(ann, plugin.getBroadcaster("ann", "chat"));
		// current behaviour (bug): looking up by channel only compares the name with null, broadcasters always have a
		// name, so nothing is ever found
		assertNull(provider.js_getBroadcaster("chat"));
	}

	@Test
	public void destroyedBroadcastersAreForgotten() throws Exception
	{
		Broadcaster ann = provider.js_createBroadcaster("ann", "chat", callback("onAnn"));
		Broadcaster bob = provider.js_createBroadcaster("bob", "chat", callback("onBob"));

		ann.js_destroy();
		bob.js_broadcastMessage("hello");

		assertNull(plugin.getBroadcaster("ann", "chat"));
		verify(access, never()).executeMethod("scopes.globals", "onAnn", new Object[] { "bob", "hello", "chat" }, true);
	}

	@Test
	public void closingTheSolutionDestroysAllBroadcasters() throws Exception
	{
		provider.js_createBroadcaster("ann", "chat", callback("onAnn"));
		provider.js_createBroadcaster("bob", "chat", callback("onBob"));

		plugin.propertyChange(new PropertyChangeEvent(this, "solution", "crm", "other"));
		assertNotNull(plugin.getBroadcaster("ann", "chat"));
		plugin.propertyChange(new PropertyChangeEvent(this, "solution", "crm", null));

		assertNull(plugin.getBroadcaster("ann", "chat"));
		assertNull(plugin.getBroadcaster("bob", "chat"));
		Broadcaster carl = provider.js_createBroadcaster("carl", "chat", callback("onCarl"));
		carl.js_broadcastMessage("anyone?");
		verify(access, never()).executeMethod(anyString(), org.mockito.ArgumentMatchers.eq("onAnn"), any(), org.mockito.ArgumentMatchers.anyBoolean());
	}

	@Test
	public void broadcasterServerErrorsAreLogged() throws Exception
	{
		Broadcaster ann = provider.js_createBroadcaster("ann", "chat", callback("onAnn"));
		IClientManagerService service = failingService();

		ann.js_broadcastMessage("lost");
		ann.js_destroy();
		Broadcaster bob = provider.js_createBroadcaster("bob", "chat", callback("onBob"));

		verify(service).broadcastMessage(any(), org.mockito.ArgumentMatchers.eq("lost"));
		verify(service).registerChannelListener(any());
		assertNull(plugin.getBroadcaster("ann", "chat"));
		assertSame(bob, plugin.getBroadcaster("bob", "chat"));
	}
}
