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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.servoy.extensions.plugins.broadcaster.BroadcastMessage;
import com.servoy.extensions.plugins.broadcaster.DataNotifyBroadCaster;
import com.servoy.extensions.plugins.broadcaster.IBroadcastMessageConsumer;
import com.servoy.extensions.plugins.broadcaster.IBroadcastMessageSender;
import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.server.shared.IClientInformation;
import com.servoy.j2db.server.shared.ServerDeployDate;

/**
 * The server side of the client manager: delegation to IServerAccess, client info copies, channel listeners and the
 * bridge to the (AMQP) databroadcaster, all with a mocked server access.
 */
public class ClientManagerServerTest
{
	private IServerAccess access;
	private ClientManagerServer server;

	/** A channel listener that records the messages it gets. */
	static final class RecordingBroadcaster implements IBroadcaster
	{
		final List<String> messages = new ArrayList<>();

		@Override
		public void channelMessage(String name, String message)
		{
			messages.add(name + ": " + message);
		}
	}

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IServerAccess.class);
		server = new ClientManagerServer();
		server.load();
		server.initialize(access);
	}

	static IClientInformation client(String id, String... infos)
	{
		IClientInformation info = mock(IClientInformation.class);
		when(info.getClientID()).thenReturn(id);
		when(info.getHostIdentifier()).thenReturn("host-id-" + id);
		when(info.getHostName()).thenReturn("host-" + id);
		when(info.getHostAddress()).thenReturn("10.0.0." + id.length());
		when(info.getUserName()).thenReturn("user-" + id);
		when(info.getUserUID()).thenReturn("uid-" + id);
		when(Integer.valueOf(info.getApplicationType())).thenReturn(Integer.valueOf(8));
		when(info.getOpenSolutionName()).thenReturn("crm");
		when(info.getLastAccessedTime()).thenReturn(new Date(3000));
		when(info.getIdleTime()).thenReturn(new Date(2000));
		when(info.getLoginTime()).thenReturn(new Date(1000));
		when(info.getClientInfos()).thenReturn(infos);
		when(info.getStatusLine()).thenReturn("status " + id);
		return info;
	}

	@Test
	public void thePluginRegistersItselfAsRemoteService() throws Exception
	{
		verify(access).registerRemoteService(IClientManagerService.class.getName(), server);
		assertEquals("Client Manager Service", server.getProperties().get(IServerPlugin.DISPLAY_NAME));
		assertNull(server.getRequiredPropertyNames());
		assertEquals(ServerDeployDate.WAR_DATE, server.getServerBuildDate());
		server.unload();
	}

	@Test
	public void aFailingRegistrationFailsTheInitialization() throws Exception
	{
		IServerAccess failing = mock(IServerAccess.class);
		RemoteException failure = new RemoteException("no rmi");
		doThrow(failure).when(failing).registerRemoteService(anyString(), any());

		PluginException ex = assertThrows(PluginException.class, () -> new ClientManagerServer().initialize(failing));

		assertSame(failure, ex.getCause());
	}

	@Test
	public void theCallsAreDelegatedToTheServer() throws Exception
	{
		JSDataSet locks = new JSDataSet();
		when(Boolean.valueOf(access.isInMaintenanceMode())).thenReturn(Boolean.TRUE);
		when(access.getLocks()).thenReturn(locks);

		assertTrue(server.isInMaintenanceMode());
		assertSame(locks, server.getLocks());
		server.sendMessageToAllClients("hi all");
		server.sendMessageToClient("c1", "hi");
		server.shutDownAllClients("me");
		server.shutDownClient("c2", true);
		server.releaseLocks("c3");

		verify(access).sendMessageToAllClients("hi all");
		verify(access).sendMessageToClient("c1", "hi");
		verify(access).shutDownAllClients("me");
		verify(access).shutDownClient("c2", true);
		verify(access).releaseLocks("c3");
	}

	@Test
	public void theConnectedClientsAreSerializableCopies()
	{
		IClientInformation[] connected = { client("a", "x"), client("bb") };
		when(access.getConnectedClients()).thenReturn(connected);

		IClientInformation[] clients = server.getConnectedClients();

		assertEquals(2, clients.length);
		ClientInfoCopy copy = assertInstanceOf(ClientInfoCopy.class, clients[0]);
		assertEquals("a", copy.getClientID());
		assertEquals("host-id-a", copy.getHostIdentifier());
		assertEquals("host-a", copy.getHostName());
		assertEquals("10.0.0.1", copy.getHostAddress());
		assertEquals("user-a", copy.getUserName());
		assertEquals("uid-a", copy.getUserUID());
		assertEquals(8, copy.getApplicationType());
		assertEquals("crm", copy.getOpenSolutionName());
		assertEquals(new Date(3000), copy.getLastAccessedTime());
		assertEquals(new Date(2000), copy.getIdleTime());
		assertEquals(new Date(1000), copy.getLoginTime());
		assertArrayEquals(new String[] { "x" }, copy.getClientInfos());
		assertEquals("status a", copy.getStatusLine());
		assertTrue(copy.toString().startsWith("CI:[clientID=a, hostIdentifier=host-id-a"), copy.toString());
		assertTrue(copy.toString().endsWith("clientInfos=[x]]"), copy.toString());
		assertEquals("bb", clients[1].getClientID());
	}

	// --- channels ---

	@Test
	public void aMessageGoesToTheOtherListenersOfTheChannel() throws Exception
	{
		RecordingBroadcaster ann = new RecordingBroadcaster();
		RecordingBroadcaster bob = new RecordingBroadcaster();
		RecordingBroadcaster other = new RecordingBroadcaster();
		BroadcastInfo annInfo = new BroadcastInfo(ann, "ann", "chat");
		server.registerChannelListener(annInfo);
		server.registerChannelListener(new BroadcastInfo(bob, "bob", "chat"));
		server.registerChannelListener(new BroadcastInfo(other, "other", "news"));

		server.broadcastMessage(annInfo, "hello");

		// not to the sender itself and not to other channels
		assertEquals(List.of(), ann.messages);
		assertEquals(List.of("ann: hello"), bob.messages);
		assertEquals(List.of(), other.messages);
	}

	@Test
	public void aDeregisteredListenerGetsNoMoreMessages() throws Exception
	{
		RecordingBroadcaster bob = new RecordingBroadcaster();
		BroadcastInfo bobInfo = new BroadcastInfo(bob, "bob", "chat");
		BroadcastInfo annInfo = new BroadcastInfo(new RecordingBroadcaster(), "ann", "chat");
		server.registerChannelListener(bobInfo);
		server.registerChannelListener(annInfo);

		server.deregisterChannelListener(bobInfo);
		server.broadcastMessage(annInfo, "hello");
		server.deregisterChannelListener(annInfo);
		// the channel is gone; a message to it or deregistering again does nothing
		server.broadcastMessage(annInfo, "nobody");
		server.deregisterChannelListener(annInfo);

		assertEquals(List.of(), bob.messages);
	}

	@Test
	public void aFailingListenerDoesNotStopTheOthers() throws Exception
	{
		RecordingBroadcaster bob = new RecordingBroadcaster();
		BroadcastInfo annInfo = new BroadcastInfo(new RecordingBroadcaster(), "ann", "chat");
		server.registerChannelListener(new BroadcastInfo((name, message) -> {
			throw new RemoteException("client gone");
		}, "gone", "chat"));
		server.registerChannelListener(new BroadcastInfo(bob, "bob", "chat"));

		server.broadcastMessage(annInfo, "hello");

		assertEquals(List.of("ann: hello"), bob.messages);
	}

	@Test
	public void broadcastInfosAreUniqueAndNeedANameAndChannel()
	{
		BroadcastInfo info = new BroadcastInfo(null, "ann", "chat");

		assertEquals(info, info);
		assertEquals(info.hashCode(), info.hashCode());
		assertNotEquals(info, new BroadcastInfo(null, "ann", "chat"));
		assertNotEquals(info, "ann");
		assertNull(info.getBroadCaster());
		assertThrows(IllegalArgumentException.class, () -> new BroadcastInfo(null, null, "chat"));
		assertThrows(IllegalArgumentException.class, () -> new BroadcastInfo(null, "ann", null));
	}

	// --- bridge to the databroadcaster (other servers) ---

	@Test
	public void withoutDatabroadcasterMessagesStayOnThisServer() throws Exception
	{
		server.afterInit();
		RecordingBroadcaster bob = new RecordingBroadcaster();
		server.registerChannelListener(new BroadcastInfo(bob, "bob", "chat"));

		server.broadcastMessage(new BroadcastInfo(null, "ann", "chat"), "hello");

		assertEquals(List.of("ann: hello"), bob.messages);
	}

	@Test
	public void withDatabroadcasterMessagesAreSentToAndReceivedFromOtherServers() throws Exception
	{
		DataNotifyBroadCaster broadcaster = mock(DataNotifyBroadCaster.class);
		IBroadcastMessageSender sender = mock(IBroadcastMessageSender.class);
		ArgumentCaptor<IBroadcastMessageConsumer> consumer = ArgumentCaptor.forClass(IBroadcastMessageConsumer.class);
		when(access.getPluginInstance(DataNotifyBroadCaster.class)).thenReturn(broadcaster);
		when(broadcaster.registerMessageBroadcastConsumer(consumer.capture())).thenReturn(sender);
		server.afterInit();
		RecordingBroadcaster bob = new RecordingBroadcaster();
		server.registerChannelListener(new BroadcastInfo(bob, "bob", "chat"));

		server.broadcastMessage(new BroadcastInfo(null, "ann", "chat"), "hello");
		consumer.getValue().handleDelivery(new BroadcastMessage("other-server", "from far", "carl", "chat"));

		ArgumentCaptor<BroadcastMessage> sent = ArgumentCaptor.forClass(BroadcastMessage.class);
		verify(sender).sendMessage(sent.capture());
		assertEquals("hello", sent.getValue().getMessage());
		assertEquals("ann", sent.getValue().getName());
		assertEquals("chat", sent.getValue().getChannelName());
		assertEquals(List.of("ann: hello", "carl: from far"), bob.messages);
	}

	@Test
	public void theBridgeWithoutSenderDoesNothing()
	{
		ClientManagerServerToBroadcasterBridge bridge = new ClientManagerServerToBroadcasterBridge(access, server);

		bridge.broadcastMessageToAllServers(new BroadcastInfo(null, "ann", "chat"), "lost");

		// afterInit was not called, so there is no sender and nothing is looked up or sent
		verify(access, never()).getPluginInstance(DataNotifyBroadCaster.class);
	}
}
