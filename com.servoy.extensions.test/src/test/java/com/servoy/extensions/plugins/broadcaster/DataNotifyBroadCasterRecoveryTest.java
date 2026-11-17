/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2026 Servoy BV

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.servoy.j2db.plugins.IDataNotifyService;
import com.servoy.j2db.util.DataSourceUtils;

/**
 * Broker-free unit tests for the SVY-21803 channel-level recovery and payload
 * size guard added to {@link DataNotifyBroadCaster} /
 * {@link DataNotifyListener}.
 *
 * The plugin's {@code initialize} opens a real AMQP connection, so these tests
 * do not call it. Instead they wire a {@link DataNotifyBroadCaster} with the
 * same collaborators {@code initialize} would build - a {@link FakeConnection},
 * a {@link FakeChannel} in a {@link ChannelHolder}, a
 * {@link DataNotifyListener} and a same-thread shutdown executor - through
 * reflection, then drive the private {@code installShutdownListener} /
 * {@code rebuildChannel} / {@code declareAndConsume} paths exactly as
 * {@code initialize} wires them. Because the shutdown executor runs on the
 * calling thread, firing the fake channel's {@code ShutdownListener} runs the
 * whole rebuild synchronously, so every assertion is deterministic with no
 * timing waits.
 *
 * Covers spec §5 acceptance criteria 1-10.
 */
@SuppressWarnings("nls")
public class DataNotifyBroadCasterRecoveryTest {
	private static final String ORIGIN = DataNotifyBroadCaster.ORIGIN_SERVER_UUID;
	private static final String EXCHANGE = "databroadcast";
	private static final String ROUTING = "";

	private DataNotifyBroadCaster plugin;
	private FakeConnection connection;
	private FakeChannel channel;
	private ChannelHolder holder;
	private DataNotifyListener listener;
	private RecordingDataNotifyService dataNotifyService;

	@Before
	public void setUp() throws Exception {
		plugin = new DataNotifyBroadCaster();
		// a generous 1 MB frame max -> ~1 MB-minus-margin effective limit for most
		// tests
		connection = new FakeConnection(1024 * 1024);
		channel = new FakeChannel();
		holder = new ChannelHolder(channel);
		dataNotifyService = new RecordingDataNotifyService();

		Properties settings = new Properties();
		FakeServerAccess app = new FakeServerAccess(settings);

		set("application", app);
		set("connection", connection);
		set("channelHolder", holder);
		set("exchangeName", EXCHANGE);
		set("routingKey", ROUTING);
		// run the rebuild synchronously on the calling thread so tests are
		// deterministic
		set("shutdownExecutor", new SameThreadExecutorService());

		listener = new DataNotifyListener(ORIGIN, holder, connection, EXCHANGE, ROUTING, plugin);
		set("dataNotifyListener", listener);

		// install the shutdown listener on the initial channel, as initialize() does
		installShutdownListener(channel);
	}

	// ---------------------------------------------------------------------------------------------
	// §5.1 channel rebuild on a broker-initiated close
	// ---------------------------------------------------------------------------------------------

	@Test
	public void brokerInitiatedChannelCloseRebuildsChannelReDeclaresTopologyAndReConsumes() throws Exception {
		FakeChannel rebuilt = new FakeChannel();
		connection.enqueueChannel(rebuilt);

		channel.fireShutdown(shutdown(false));

		assertEquals("a fresh channel must be created from the still-open connection", 1,
				connection.createChannelCount);
		assertSame("the holder must point at the rebuilt channel", rebuilt, holder.getChannel());
		assertEquals("the fanout exchange must be re-declared on the new channel", 1, rebuilt.declaredExchanges.size());
		assertEquals("databroadcast", rebuilt.declaredExchanges.get(0));
		assertEquals("the queue must be re-declared/bound on the new channel", 1, rebuilt.boundQueues.size());
		assertEquals("the consumer must be re-registered on the new channel", 1, rebuilt.consumers.size());
		assertFalse("a fresh shutdown listener must be installed on the new channel",
				rebuilt.shutdownListeners.isEmpty());

		// a publish after the rebuild lands on the NEW channel, not the dead one
		listener.notifyDataChange("srv", "tab", null, 1, null, null);
		assertEquals("nothing should be published on the dead channel after rebuild", 0, channel.publishCount());
		assertEquals("the publish must land on the rebuilt channel", 1, rebuilt.publishCount());
	}

	// ---------------------------------------------------------------------------------------------
	// §5.2 failedList drains on channel recovery, in order
	// ---------------------------------------------------------------------------------------------

	@Test
	public void failedListDrainsInOrderOnChannelRecovery() throws Exception {
		// close the channel first so publishes queue into the failedList
		channel.fireShutdown(shutdown(false)); // this would try to rebuild - give it no channel yet
		// the above rebuild failed (no enqueued channel); reset attempts so the real
		// rebuild can run
		set("rebuildAttempts", Integer.valueOf(0));
		setRebuildingFalse();

		// holder channel is now closed; queue three ordered notifies
		listener.notifyDataChange("srv", "t", dataset(1), 1, null, null);
		listener.notifyDataChange("srv", "t", dataset(2), 1, null, null);
		listener.notifyDataChange("srv", "t", dataset(3), 1, null, null);
		assertEquals("all three must be queued while the channel is closed", 3, failedListSize());

		FakeChannel rebuilt = new FakeChannel();
		connection.enqueueChannel(rebuilt);
		// a second shutdown drives the actual rebuild + drain
		holder.getChannel(); // dead channel still in holder
		rebuildNow();

		assertEquals("the whole failedList must drain onto the rebuilt channel", 3, rebuilt.publishCount());
		assertEquals("the failedList must be emptied after draining", 0, failedListSize());
		assertTrue("all drained bodies must be within the limit",
				rebuilt.publishes.stream().allMatch(p -> p.size() <= effectiveLimit()));
		// FIFO: the three notifies must drain in the order they were queued (pk 1, 2, 3)
		for (int i = 0; i < 3; i++) {
			NotifyData drained = deserialize(rebuilt.publishes.get(i).body);
			assertNotNull(drained);
			assertEquals("drained entries must preserve FIFO order", Integer.valueOf(i + 1),
					drained.pks.getRow(0)[0]);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// §5.3 no rebuild on application-initiated close
	// ---------------------------------------------------------------------------------------------

	@Test
	public void applicationInitiatedCloseDoesNotRebuild() throws Exception {
		FakeChannel rebuilt = new FakeChannel();
		connection.enqueueChannel(rebuilt);

		channel.fireShutdown(shutdown(true));

		assertEquals("an application-initiated close must not create a new channel", 0, connection.createChannelCount);
		assertSame("the holder must still point at the original channel", channel, holder.getChannel());
	}

	// ---------------------------------------------------------------------------------------------
	// §5.4 no rebuild when the connection is also closed
	// ---------------------------------------------------------------------------------------------

	@Test
	public void channelCloseWhileConnectionClosedDoesNotRebuild() throws Exception {
		FakeChannel rebuilt = new FakeChannel();
		connection.enqueueChannel(rebuilt);
		connection.setOpen(false);

		channel.fireShutdown(shutdown(false));

		assertEquals("no rebuild when the connection itself is closing - the connection recovery path owns that", 0,
				connection.createChannelCount);
		assertSame(channel, holder.getChannel());
	}

	@Test
	public void channelCloseWhileUnloadingDoesNotRebuild() throws Exception {
		FakeChannel rebuilt = new FakeChannel();
		connection.enqueueChannel(rebuilt);
		set("unloading", Boolean.TRUE);

		channel.fireShutdown(shutdown(false));

		assertEquals("no rebuild while the plugin is unloading", 0, connection.createChannelCount);
	}

	// ---------------------------------------------------------------------------------------------
	// §5.5 single-flight + bounded attempts
	// ---------------------------------------------------------------------------------------------

	@Test
	public void overlappingShutdownsRebuildOnlyOnce() throws Exception {
		// mark a rebuild as already in progress; a shutdown signal must then be ignored
		Field rebuilding = DataNotifyBroadCaster.class.getDeclaredField("rebuilding");
		rebuilding.setAccessible(true);
		((java.util.concurrent.atomic.AtomicBoolean) rebuilding.get(plugin)).set(true);

		FakeChannel rebuilt = new FakeChannel();
		connection.enqueueChannel(rebuilt);

		channel.fireShutdown(shutdown(false));

		assertEquals("a shutdown while a rebuild is already in progress must not start another", 0,
				connection.createChannelCount);
	}

	@Test
	public void repeatedRebuildFailuresStopAfterBoundedAttempts() throws Exception {
		// every createChannel fails; rebuildChannel must try exactly
		// MAX_REBUILD_ATTEMPTS and then give up
		connection.failCreateChannelTimes = 100;
		// avoid the 2s backoff dominating the test: null the backoff by shortening via
		// reflection is not possible
		// (it is a constant), so accept the bounded 3 attempts with 2 sleeps (~4s) -
		// still bounded, never a tight loop.
		rebuildNow();

		assertEquals("rebuild must stop after MAX_REBUILD_ATTEMPTS (3) failures, not loop forever", 3,
				connection.createChannelCount);
	}

	// ---------------------------------------------------------------------------------------------
	// §5.6 oversize row notify -> datasource flush
	// ---------------------------------------------------------------------------------------------

	@Test
	public void oversizedRowNotifyFallsBackToDatasourceFlush() throws Exception {
		Properties settings = settings();
		settings.setProperty("amqpbroadcaster.maxmessagesize", "2048");

		com.servoy.j2db.dataprocessing.BroadcastFilter[] filters = new com.servoy.j2db.dataprocessing.BroadcastFilter[] {
				new com.servoy.j2db.dataprocessing.BroadcastFilter("tab", "col",
						com.servoy.j2db.dataprocessing.BroadcastFilter.BroadcastFilterOperator.IN,
						new Object[] { "x" }) };

		// a big insert payload well over 2048 bytes
		Object[] big = new Object[] { new byte[8192] };
		listener.notifyDataChange("srv", "tab", dataset(1), 1, big, filters);

		assertEquals("exactly one body must be published", 1, channel.publishCount());
		FakeChannel.Publish p = channel.lastPublish();
		assertTrue("the published body must be within the limit (a small flush, not the oversized row)",
				p.size() <= 2048);

		// the published body must be a datasource FLUSH NotifyData for the correct
		// datasource, filters preserved
		NotifyData published = deserialize(p.body);
		assertNotNull(published);
		assertEquals("the flush must carry the db:/srv/tab datasource",
				DataSourceUtils.createDBTableDataSource("srv", "tab"), published.dataSource);
		assertNull("a flush carries no row data", published.pks);
		assertNotNull("broadcastFilters must be preserved on the flush", published.broadcastFilters);
		assertEquals(1, published.broadcastFilters.length);
	}

	// ---------------------------------------------------------------------------------------------
	// §5.7 in-limit notify publishes unchanged
	// ---------------------------------------------------------------------------------------------

	@Test
	public void inLimitRowNotifyPublishesRawRowDataUnchanged() throws Exception {
		Object[] small = new Object[] { "small" };
		listener.notifyDataChange("srv", "tab", dataset(42), 1, small, null);

		assertEquals(1, channel.publishCount());
		NotifyData published = deserialize(channel.lastPublish().body);
		assertNotNull(published);
		assertEquals("the raw row notify must be published unchanged", "srv", published.server_name);
		assertEquals("tab", published.table_name);
		assertNotNull("the pks (raw row data) must be present", published.pks);
		assertNull("an in-limit row notify must not be converted to a flush", published.dataSource);
	}

	// ---------------------------------------------------------------------------------------------
	// §5.8 maxmessagesize setting honoured / frame-max derived / 0-unlimited
	// fallback
	// ---------------------------------------------------------------------------------------------

	@Test
	public void maxMessageSizeSettingIsHonouredBelowFrameMax() throws Exception {
		settings().setProperty("amqpbroadcaster.maxmessagesize", "1024");
		assertEquals("the explicit setting must win", 1024L, invokeEffectiveMaxMessageSize());
	}

	@Test
	public void frameMaxDerivedLimitUsedWhenSettingUnset() throws Exception {
		// no setting, frameMax 1 MB -> 1 MB minus the 16 KB safety margin
		long expected = (1024 * 1024) - (16 * 1024);
		assertEquals(expected, invokeEffectiveMaxMessageSize());
	}

	@Test
	public void unlimitedFrameMaxFallsBackToSafeDefault() throws Exception {
		connection.setFrameMax(0);
		long expected = (16L * 1024 * 1024) - (16 * 1024);
		assertEquals("a 0/unlimited frame max must fall back to the 16 MB-minus-margin default", expected,
				invokeEffectiveMaxMessageSize());
	}

	// ---------------------------------------------------------------------------------------------
	// §5.9 oversized BroadcastMessage is skipped, not published
	// ---------------------------------------------------------------------------------------------

	@Test
	public void oversizedBroadcastMessageIsSkippedNotPublished() throws Exception {
		settings().setProperty("amqpbroadcaster.maxmessagesize", "256");
		IBroadcastMessageSender sender = plugin.registerMessageBroadcastConsumer(message -> {
			/* no-op consumer */});
		assertNotNull("a sender must be returned when the holder is set", sender);

		StringBuilder huge = new StringBuilder();
		for (int i = 0; i < 2000; i++)
			huge.append('x');
		sender.sendMessage(new BroadcastMessage("other-origin", huge.toString(), "name", "channel"));

		assertEquals("an oversized BroadcastMessage must never reach basicPublish (channel stays open)", 0,
				channel.publishCount());
		assertTrue("the channel must still be open after skipping the oversize", channel.isOpen());
	}

	@Test
	public void inLimitBroadcastMessageIsPublished() throws Exception {
		IBroadcastMessageSender sender = plugin.registerMessageBroadcastConsumer(message -> {
			/* no-op */});
		sender.sendMessage(new BroadcastMessage("other-origin", "hello", "name", "channel"));
		assertEquals("a small message must be published normally", 1, channel.publishCount());
	}

	// ---------------------------------------------------------------------------------------------
	// §5.10 channel never killed by the guard - no oversized body ever reaches
	// basicPublish
	// ---------------------------------------------------------------------------------------------

	@Test
	public void noOversizedBodyEverReachesBasicPublishEvenOnDrain() throws Exception {
		settings().setProperty("amqpbroadcaster.maxmessagesize", "2048");

		// close the channel, queue several oversized row notifies, then rebuild and
		// drain
		channel.fireShutdown(shutdown(false));
		set("rebuildAttempts", Integer.valueOf(0));
		setRebuildingFalse();

		Object[] big = new Object[] { new byte[16384] };
		for (int i = 0; i < 5; i++)
			listener.notifyDataChange("srv", "tab", dataset(i), 1, big, null);
		assertEquals(5, failedListSize());

		FakeChannel rebuilt = new FakeChannel();
		connection.enqueueChannel(rebuilt);
		rebuildNow();

		// every queued oversized entry must have been converted to a small flush on
		// drain
		assertEquals("all five must drain", 5, rebuilt.publishCount());
		for (FakeChannel.Publish p : rebuilt.publishes) {
			assertTrue("no raw oversized body may ever be handed to basicPublish (guards against the churn loop)",
					p.size() <= 2048);
			NotifyData nd = deserialize(p.body);
			assertNotNull(nd);
			assertNull("each drained oversized entry must be a flush, not raw row data", nd.pks);
			assertEquals(DataSourceUtils.createDBTableDataSource("srv", "tab"), nd.dataSource);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// extra: transient publish failure on drain re-queues FIFO and is non-reentrant
	// ---------------------------------------------------------------------------------------------

	@Test
	public void transientPublishFailureOnDrainReQueuesWithoutReentering() throws Exception {
		// close, queue two small notifies
		channel.fireShutdown(shutdown(false));
		set("rebuildAttempts", Integer.valueOf(0));
		setRebuildingFalse();

		listener.notifyDataChange("srv", "tab", dataset(1), 1, new Object[] { "a" }, null);
		listener.notifyDataChange("srv", "tab", dataset(2), 1, new Object[] { "b" }, null);
		assertEquals(2, failedListSize());

		FakeChannel rebuilt = new FakeChannel();
		rebuilt.throwOnNextPublish = new java.io.IOException("transient"); // first drained publish fails
		connection.enqueueChannel(rebuilt);
		rebuildNow();

		// first entry failed -> re-queued; second entry published. One body made it,
		// one is back on the list.
		assertEquals("the transiently failed entry must be re-queued", 1, failedListSize());
		assertEquals("the other entry must have been published", 1, rebuilt.publishCount());
	}

	// =============================================================================================
	// helpers
	// =============================================================================================

	private void installShutdownListener(FakeChannel ch) throws Exception {
		Method m = DataNotifyBroadCaster.class.getDeclaredMethod("installShutdownListener",
				com.rabbitmq.client.Channel.class, IDataNotifyService.class);
		m.setAccessible(true);
		m.invoke(plugin, ch, dataNotifyService);
	}

	private void rebuildNow() throws Exception {
		Method m = DataNotifyBroadCaster.class.getDeclaredMethod("rebuildChannel", IDataNotifyService.class);
		m.setAccessible(true);
		m.invoke(plugin, dataNotifyService);
	}

	private long invokeEffectiveMaxMessageSize() throws Exception {
		Method m = DataNotifyBroadCaster.class.getDeclaredMethod("getEffectiveMaxMessageSize",
				com.rabbitmq.client.Connection.class);
		m.setAccessible(true);
		return ((Long) m.invoke(plugin, connection)).longValue();
	}

	private long effectiveLimit() {
		try {
			return invokeEffectiveMaxMessageSize();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Properties settings() {
		try {
			Field f = DataNotifyBroadCaster.class.getDeclaredField("application");
			f.setAccessible(true);
			FakeServerAccess app = (FakeServerAccess) f.get(plugin);
			return app.getSettings();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private int failedListSize() throws Exception {
		Field f = DataNotifyListener.class.getDeclaredField("failedList");
		f.setAccessible(true);
		return ((List<?>) f.get(listener)).size();
	}

	private void setRebuildingFalse() throws Exception {
		Field rebuilding = DataNotifyBroadCaster.class.getDeclaredField("rebuilding");
		rebuilding.setAccessible(true);
		((java.util.concurrent.atomic.AtomicBoolean) rebuilding.get(plugin)).set(false);
	}

	private void set(String name, Object value) throws Exception {
		Field f = DataNotifyBroadCaster.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(plugin, value);
	}

	private static com.servoy.j2db.dataprocessing.IDataSet dataset(int pk) {
		return new com.servoy.j2db.dataprocessing.BufferedDataSet(new String[] { "pk" },
				java.util.Collections.singletonList(new Object[] { Integer.valueOf(pk) }));
	}

	private static NotifyData deserialize(byte[] body) throws Exception {
		try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(body))) {
			return (NotifyData) ois.readObject();
		}
	}

	/**
	 * A non-application-initiated shutdown fires a rebuild; an
	 * application-initiated one does not.
	 */
	private static com.rabbitmq.client.ShutdownSignalException shutdown(boolean appInitiated) {
		return new com.rabbitmq.client.ShutdownSignalException(false, appInitiated, null, "fake-channel");
	}

	/**
	 * An ExecutorService that runs submitted tasks on the calling thread, so the
	 * rebuild is synchronous.
	 */
	private static final class SameThreadExecutorService extends AbstractExecutorService {
		private volatile boolean shutdown = false;

		@Override
		public void execute(Runnable command) {
			command.run();
		}

		@Override
		public void shutdown() {
			shutdown = true;
		}

		@Override
		public List<Runnable> shutdownNow() {
			shutdown = true;
			return new AbstractList<Runnable>() {
				@Override
				public Runnable get(int index) {
					throw new IndexOutOfBoundsException();
				}

				@Override
				public int size() {
					return 0;
				}
			};
		}

		@Override
		public boolean isShutdown() {
			return shutdown;
		}

		@Override
		public boolean isTerminated() {
			return shutdown;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) {
			return true;
		}
	}

	/**
	 * Records flushCachedDatabaseData / notifyDataChange so the consumer side can
	 * be observed if needed.
	 */
	private static final class RecordingDataNotifyService implements IDataNotifyService {
		@Override
		public void registerDataNotifyListener(com.servoy.j2db.plugins.IDataNotifyListener l) {
		}

		@Override
		public void deregisterDataNotifyListener(com.servoy.j2db.plugins.IDataNotifyListener l) {
		}

		@Override
		public String[] getUsedDataSources() {
			return new String[0];
		}

		@Override
		public void flushCachedDatabaseData(String dataSource,
				com.servoy.j2db.dataprocessing.BroadcastFilter[] clientBroadcastFilters) {
		}

		@Override
		public void notifyDataChange(String server_name, String table_name, com.servoy.j2db.dataprocessing.IDataSet pks,
				int action, Object[] insertColumnData,
				com.servoy.j2db.dataprocessing.BroadcastFilter[] broadcastFilters) {
		}
	}
}
