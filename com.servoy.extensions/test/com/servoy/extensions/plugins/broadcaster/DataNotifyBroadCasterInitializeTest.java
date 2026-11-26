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
package com.servoy.extensions.plugins.broadcaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.RecoverableConnection;
import com.rabbitmq.client.RecoveryListener;
import com.servoy.j2db.dataprocessing.BufferedDataSet;
import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.plugins.IDataNotifyService;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;

/**
 * initialize() of the AMQP databroadcaster without a broker: the connection factory is replaced through the
 * package-private createConnectionFactory seam and hands out a FakeConnection/FakeChannel (or a mocked recoverable
 * connection). Covers the settings mapping, TLS setup, queue arguments, the consumer, the message broadcast sender,
 * the connection recovery listeners and unload.
 */
@SuppressWarnings("nls")
public class DataNotifyBroadCasterInitializeTest
{
	private final Properties settings = new Properties();
	private IServerAccess access;
	private IDataNotifyService dataNotifyService;
	private FakeConnection connection;
	private FakeChannel channel;
	private Connection connectionToHandOut;
	private IOException connectFailure;
	private ConnectionFactory factory;
	private DataNotifyBroadCaster plugin;

	@BeforeEach
	public void setUp()
	{
		access = mock(IServerAccess.class);
		when(access.getSettings()).thenReturn(settings);
		dataNotifyService = mock(IDataNotifyService.class);
		when(access.getDataNotifyService()).thenReturn(dataNotifyService);
		connection = new FakeConnection(1024 * 1024);
		channel = new FakeChannel();
		connection.enqueueChannel(channel);
		connectionToHandOut = connection;
		plugin = new DataNotifyBroadCaster()
		{
			@Override
			ConnectionFactory createConnectionFactory()
			{
				factory = new ConnectionFactory()
				{
					@Override
					public Connection newConnection() throws IOException
					{
						if (connectFailure != null) throw connectFailure;
						return connectionToHandOut;
					}
				};
				return factory;
			}
		};
	}

	@AfterEach
	public void tearDown() throws Exception
	{
		// stops the executors that initialize() created
		plugin.unload();
	}

	private void initialize() throws Exception
	{
		settings.setProperty("amqpbroadcaster.hostname", "rabbit.example");
		plugin.load();
		plugin.initialize(access);
	}

	private static byte[] serialize(Object o) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new ObjectOutputStream(out))
		{
			oos.writeObject(o);
		}
		return out.toByteArray();
	}

	private static Object deserialize(byte[] bytes) throws Exception
	{
		try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes)))
		{
			return ois.readObject();
		}
	}

	private void deliver(Object o) throws IOException
	{
		channel.consumers.get(0).handleDelivery("tag", null, null, serialize(o));
	}

	// --- plugin ---

	@Test
	public void plugin()
	{
		assertEquals("Servoy AMQP Databroadcaster", plugin.getProperties().get(IServerPlugin.DISPLAY_NAME));
		Map<String, String> required = plugin.getRequiredPropertyNames();
		assertTrue(required.containsKey("amqpbroadcaster.hostname"));
		assertTrue(required.containsKey("amqpbroadcaster.maxmessagesize"));
		assertEquals("amqpbroadcaster.hostname", required.keySet().iterator().next());
	}

	@Test
	public void withoutHostnameNothingIsStarted() throws Exception
	{
		settings.setProperty("amqpbroadcaster.hostname", "  ");

		plugin.initialize(access);

		assertNull(factory);
		assertNull(plugin.registerMessageBroadcastConsumer(message -> {
		}));
	}

	// --- settings ---

	@Test
	public void theSettingsConfigureTheConnection() throws Exception
	{
		settings.setProperty("amqpbroadcaster.username", "user");
		settings.setProperty("amqpbroadcaster.password", "secret");
		settings.setProperty("amqpbroadcaster.virtualhost", "/servoy");
		settings.setProperty("amqpbroadcaster.port", "5673");
		settings.setProperty("amqpbroadcaster.connectiontimeout", "1000");
		settings.setProperty("amqpbroadcaster.handshaketimeout", "2000");
		settings.setProperty("amqpbroadcaster.shutdowntimeout", "3000");
		settings.setProperty("amqpbroadcaster.rpctimeout", "4000");

		initialize();

		assertEquals("rabbit.example", factory.getHost());
		assertEquals("user", factory.getUsername());
		assertEquals("secret", factory.getPassword());
		assertEquals("/servoy", factory.getVirtualHost());
		assertEquals(5673, factory.getPort());
		assertEquals(1000, factory.getConnectionTimeout());
		assertEquals(2000, factory.getHandshakeTimeout());
		assertEquals(3000, factory.getShutdownTimeout());
		assertEquals(4000, factory.getChannelRpcTimeout());
		assertFalse(factory.isSSL());
	}

	@Test
	public void theExchangeIsDeclaredAndTheQueueConsumed() throws Exception
	{
		initialize();

		assertEquals(List.of("databroadcast"), channel.declaredExchanges);
		assertEquals(1, channel.boundQueues.size());
		assertEquals(1, channel.consumers.size());
		assertFalse(channel.shutdownListeners.isEmpty());
		// no queue arguments: the plain server named queue
		assertTrue(channel.queueArguments.isEmpty());
		ArgumentCaptor<DataNotifyListener> listener = ArgumentCaptor.forClass(DataNotifyListener.class);
		verify(dataNotifyService).registerDataNotifyListener(listener.capture());

		listener.getValue().flushCachedDatabaseData("db:/crm/orders", null);

		NotifyData published = (NotifyData)deserialize(channel.lastPublish().body);
		assertEquals("db:/crm/orders", published.dataSource);
		assertEquals("databroadcast", channel.lastPublish().exchange);
	}

	@Test
	public void theExchangeRoutingKeyAndQueueArgumentsCanBeSet() throws Exception
	{
		settings.setProperty("amqpbroadcaster.exchange", "myexchange");
		settings.setProperty("amqpbroadcaster.routingkey", "pods");
		settings.setProperty("amqpbroadcaster.queuetype", "quorum");
		settings.setProperty("amqpbroadcaster.singleactiveconsumer", "true");

		initialize();

		assertEquals(List.of("myexchange"), channel.declaredExchanges);
		assertEquals(Map.of("x-queue-type", "quorum", "x-single-active-consumer", Boolean.TRUE), channel.queueArguments.get(0));
	}

	@Test
	public void onlyASingleActiveConsumerArgument() throws Exception
	{
		settings.setProperty("amqpbroadcaster.singleactiveconsumer", "false");

		initialize();

		assertEquals(Map.of("x-single-active-consumer", Boolean.FALSE), channel.queueArguments.get(0));
	}

	@Test
	public void aFailingConnectionIsOnlyLogged() throws Exception
	{
		connectFailure = new IOException("connection refused");

		initialize();

		verify(dataNotifyService, never()).registerDataNotifyListener(any());
		assertNull(plugin.registerMessageBroadcastConsumer(message -> {
		}));
	}

	// --- TLS ---

	@Test
	public void tlsWithoutKeystoreUsesTheProtocol() throws Exception
	{
		settings.setProperty("amqpbroadcaster.tlsprotocols", "TLSv1.2");
		settings.setProperty("amqpbroadcaster.hostnameverification", "true");

		initialize();

		assertTrue(factory.isSSL());
	}

	@Test
	public void anUnknownTlsProtocolIsOnlyLogged() throws Exception
	{
		settings.setProperty("amqpbroadcaster.tlsprotocols", "NO-SUCH-PROTOCOL");

		initialize();

		assertFalse(factory.isSSL());
		// the connection is still made
		assertEquals(1, channel.consumers.size());
	}

	private Path keystore(Path dir, String name, String password) throws Exception
	{
		KeyStore store = KeyStore.getInstance(name.endsWith(".jks") ? "JKS" : "PKCS12");
		store.load(null, password.toCharArray());
		Path file = dir.resolve(name);
		try (FileOutputStream out = new FileOutputStream(file.toFile()))
		{
			store.store(out, password.toCharArray());
		}
		return file;
	}

	@Test
	public void aKeystoreIsReadForTls(@TempDir Path dir) throws Exception
	{
		settings.setProperty("amqpbroadcaster.keystore.path", keystore(dir, "broker.p12", "storepass").toString());
		settings.setProperty("amqpbroadcaster.keystore.password", "storepass");

		initialize();

		// the SSLContext built from the keystore is used for the connection
		assertTrue(factory.isSSL());
		assertEquals(1, channel.consumers.size());
	}

	@Test
	public void aJksKeystoreWithHostnameVerification(@TempDir Path dir) throws Exception
	{
		settings.setProperty("amqpbroadcaster.keystore.path", keystore(dir, "broker.jks", "storepass").toString());
		settings.setProperty("amqpbroadcaster.keystore.password", "storepass");
		settings.setProperty("amqpbroadcaster.tlsprotocols", "TLSv1.3");
		settings.setProperty("amqpbroadcaster.hostnameverification", "true");

		initialize();

		assertTrue(factory.isSSL());
		assertEquals(1, channel.consumers.size());
	}

	@Test
	public void keystoreProblemsAreOnlyLogged(@TempDir Path dir) throws Exception
	{
		settings.setProperty("amqpbroadcaster.keystore.path", keystore(dir, "broker.p12", "storepass").toString());
		settings.setProperty("amqpbroadcaster.keystore.password", "wrong");

		initialize();

		assertFalse(factory.isSSL());
		assertEquals(1, channel.consumers.size());
	}

	@Test
	public void aMissingKeystoreOrUnknownProtocolIsOnlyLogged(@TempDir Path dir) throws Exception
	{
		settings.setProperty("amqpbroadcaster.keystore.path", dir.resolve("missing.p12").toString());
		settings.setProperty("amqpbroadcaster.keystore.password", "storepass");
		initialize();
		plugin.unload();

		FakeChannel second = new FakeChannel();
		connection.enqueueChannel(second);
		channel = second;
		settings.setProperty("amqpbroadcaster.keystore.path", keystore(dir, "broker.p12", "storepass").toString());
		settings.setProperty("amqpbroadcaster.tlsprotocols", "NO-SUCH-PROTOCOL");
		plugin.initialize(access);

		assertEquals(1, second.consumers.size());
	}

	// --- consuming ---

	@Test
	public void changesFromOtherServersAreApplied() throws Exception
	{
		initialize();
		IDataSet pks = new BufferedDataSet(new String[] { "id" }, List.<Object[]> of(new Object[] { Integer.valueOf(1) }));

		deliver(new NotifyData("other-server", "db:/crm/orders", null));
		deliver(new NotifyData("other-server", "crm", "orders", pks, 2, null, null));

		verify(dataNotifyService).flushCachedDatabaseData("db:/crm/orders", null);
		verify(dataNotifyService).notifyDataChange(org.mockito.ArgumentMatchers.eq("crm"), org.mockito.ArgumentMatchers.eq("orders"), any(),
			org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
	}

	@Test
	public void ownChangesAndUnknownObjectsAreIgnored() throws Exception
	{
		initialize();

		deliver(new NotifyData(DataNotifyBroadCaster.ORIGIN_SERVER_UUID, "db:/crm/orders", null));
		deliver("not a NotifyData");
		deliver(new BroadcastMessage("other-server", "hello", "ann", "chat"));

		verify(dataNotifyService, never()).flushCachedDatabaseData(anyString(), any());
	}

	@Test
	public void aBodyThatIsNotAJavaObjectIsSkipped() throws Exception
	{
		initialize();

		// a body that is no serialized java object is only logged, the consumer (and so the channel) keeps working
		channel.consumers.get(0).handleDelivery("tag", null, null, "plain text".getBytes());
		deliver(new NotifyData("other-server", "db:/crm/customers", null));
		verify(dataNotifyService).flushCachedDatabaseData("db:/crm/customers", null);
		// a stream that breaks while reading the object is only logged
		byte[] truncated = serialize(new NotifyData("other-server", "db:/crm/orders", null));
		channel.consumers.get(0).handleDelivery("tag", null, null, java.util.Arrays.copyOf(truncated, truncated.length - 5));
		verify(dataNotifyService, never()).flushCachedDatabaseData(org.mockito.ArgumentMatchers.eq("db:/crm/orders"), any());
	}

	// --- message broadcast between servers ---

	@Test
	public void messagesAreSentToAndReceivedFromOtherServers() throws Exception
	{
		settings.setProperty("amqpbroadcaster.exchange", "myexchange");
		settings.setProperty("amqpbroadcaster.routingkey", "pods");
		initialize();
		List<BroadcastMessage> received = new ArrayList<>();
		IBroadcastMessageSender sender = plugin.registerMessageBroadcastConsumer(received::add);

		sender.sendMessage(new BroadcastMessage(DataNotifyBroadCaster.ORIGIN_SERVER_UUID, "hello", "ann", "chat"));
		BroadcastMessage fromOther = new BroadcastMessage("other-server", "hi", "bob", "chat");
		deliver(fromOther);
		deliver(new BroadcastMessage(DataNotifyBroadCaster.ORIGIN_SERVER_UUID, "echo", "ann", "chat"));

		FakeChannel.Publish publish = channel.lastPublish();
		assertEquals("myexchange", publish.exchange);
		assertEquals("pods", publish.routingKey);
		assertEquals("hello", ((BroadcastMessage)deserialize(publish.body)).getMessage());
		assertEquals(1, received.size());
		assertEquals("hi", received.get(0).getMessage());
		assertEquals("bob", received.get(0).getName());
	}

	@Test
	public void messagesAreNotSentOnAClosedChannelOrWhenTooBig() throws Exception
	{
		initialize();
		IBroadcastMessageSender sender = plugin.registerMessageBroadcastConsumer(message -> {
		});

		settings.setProperty("amqpbroadcaster.maxmessagesize", "10");
		sender.sendMessage(new BroadcastMessage("me", "too big", "ann", "chat"));
		settings.remove("amqpbroadcaster.maxmessagesize");
		channel.throwOnNextPublish = new IOException("broker gone");
		sender.sendMessage(new BroadcastMessage("me", "fails", "ann", "chat"));
		channel.close();
		sender.sendMessage(new BroadcastMessage("me", "closed channel", "ann", "chat"));

		assertEquals(0, channel.publishCount());
	}

	// --- connection recovery and unload ---

	@Test
	public void aRecoveredConnectionFlushesAllUsedDatasources() throws Exception
	{
		RecoverableConnection recoverable = mock(RecoverableConnection.class);
		when(Boolean.valueOf(recoverable.isOpen())).thenReturn(Boolean.TRUE);
		when(recoverable.createChannel()).thenReturn(channel);
		connectionToHandOut = recoverable;
		when(dataNotifyService.getUsedDataSources()).thenReturn(new String[] { "db:/crm/orders", "db:/crm/customers" });
		initialize();
		ArgumentCaptor<RecoveryListener> listeners = ArgumentCaptor.forClass(RecoveryListener.class);
		verify(recoverable, org.mockito.Mockito.times(2)).addRecoveryListener(listeners.capture());
		java.lang.reflect.Field attempts = DataNotifyBroadCaster.class.getDeclaredField("rebuildAttempts");
		attempts.setAccessible(true);
		attempts.set(plugin, Integer.valueOf(3));

		for (RecoveryListener listener : listeners.getAllValues())
		{
			listener.handleRecoveryStarted(recoverable);
			listener.handleRecovery(recoverable);
		}

		verify(dataNotifyService).flushCachedDatabaseData("db:/crm/orders", null);
		verify(dataNotifyService).flushCachedDatabaseData("db:/crm/customers", null);
		// channel auto rebuild is enabled again
		assertEquals(Integer.valueOf(0), attempts.get(plugin));

		plugin.unload();
		verify(recoverable).close();
		assertFalse(channel.isOpen());
	}

	@Test
	public void unloadClosesTheChannelAndConnection() throws Exception
	{
		initialize();

		plugin.unload();

		assertFalse(channel.isOpen());
		assertFalse(connection.isOpen());
		// a second unload with everything closed does nothing
		plugin.unload();
	}

	@Test
	public void aFailingCloseIsOnlyLogged() throws Exception
	{
		RecoverableConnection recoverable = mock(RecoverableConnection.class);
		when(Boolean.valueOf(recoverable.isOpen())).thenReturn(Boolean.TRUE);
		when(recoverable.createChannel()).thenReturn(channel);
		doThrow(new IOException("already gone")).when(recoverable).close();
		connectionToHandOut = recoverable;
		initialize();

		plugin.unload();

		verify(recoverable).close();
		// the executors are stopped even though the close failed: a new initialize starts fresh ones
		assertNotNull(plugin.getProperties());
	}
}
