/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2017 Servoy BV

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoverableConnection;
import com.rabbitmq.client.RecoveryListener;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IDataNotifyService;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * <p>The <code>datanotifybroadcaster</code> plugin enables real-time data synchronization across multiple
 * Servoy server instances using AMQP (Advanced Message Queuing Protocol). It leverages RabbitMQ to broadcast
 * database changes between servers, ensuring that all connected servers maintain consistent cached data.</p>
 *
 * <p>This plugin automatically broadcasts notifications when data changes occur in one server instance,
 * triggering cache invalidation in other servers. It handles connection recovery after network interruptions
 * by flushing all affected datasources to ensure data consistency. The plugin also supports secure
 * communication through TLS with configurable keystore settings and hostname verification.</p>
 *
 * <p>Beyond database synchronization, the plugin provides a messaging infrastructure that allows
 * applications to broadcast custom messages between server instances using the
 * <code>registerMessageBroadcastConsumer</code> method, enabling advanced inter-server communication
 * scenarios.</p>
 *
 * <p>Configuration is handled through server settings, with options for connection parameters,
 * security settings, and timeout values. The plugin automatically creates the necessary AMQP
 * exchanges and queues during initialization.</p>
 *
 * @author jcompagner
 */
@ServoyDocumented(publicName = "datanotifybroadcaster")
@SuppressWarnings("nls")
public class DataNotifyBroadCaster implements IServerPlugin
{
	private static final String EXCHANGE_NAME = "databroadcast";
	private static final String ROUTING_KEY = "";
	public static final String ORIGIN_SERVER_UUID = UUID.randomUUID().toString();

	/**
	 * Conservative safety margin (bytes) subtracted from the broker frame max to leave room for AMQP
	 * frame/header overhead, so we never hand the broker a body that fills the whole frame.
	 */
	private static final int FRAME_SAFETY_MARGIN = 16 * 1024;

	/**
	 * Fallback maximum body size (16 MB minus the safety margin) used when neither
	 * amqpbroadcaster.maxmessagesize nor a usable broker frame max is available. Matches the common
	 * 16 MB broker default that triggered SCS-3537.
	 */
	private static final long DEFAULT_MAX_MESSAGE_SIZE = 16L * 1024 * 1024 - FRAME_SAFETY_MARGIN;

	/** Maximum consecutive channel-rebuild attempts before auto-rebuild gives up until the next connection recovery. */
	private static final int MAX_REBUILD_ATTEMPTS = 3;

	/** Short backoff between channel-rebuild attempts (ms) so we never retry in a tight loop. */
	private static final long REBUILD_BACKOFF_MS = 2000;

	private Connection connection;
	private ChannelHolder channelHolder;
	private DataNotifyListener dataNotifyListener;
	private ExecutorService sharedExecutor;
	private ExecutorService shutdownExecutor;

	private IBroadcastMessageConsumer messageConsumer;
	private IServerAccess application;

	private volatile boolean unloading = false;
	private final AtomicBoolean rebuilding = new AtomicBoolean(false);
	private volatile int rebuildAttempts = 0;

	private String exchangeName;
	private String routingKey;
	private Map<String, Object> queueArgs;

	@Override
	public void load() throws PluginException
	{
	}

	@Override
	public void unload() throws PluginException
	{
		unloading = true;
		try
		{
			Channel channel = channelHolder != null ? channelHolder.getChannel() : null;
			if (channel != null && channel.isOpen()) channel.close();
			if (connection != null && connection.isOpen()) connection.close();
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		finally
		{
			if (sharedExecutor != null) sharedExecutor.shutdownNow();
			if (shutdownExecutor != null) shutdownExecutor.shutdownNow();
		}
	}

	@Override
	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "Servoy AMQP Databroadcaster");
		return props;
	}

	@Override
	public void initialize(IServerAccess app) throws PluginException
	{
		this.application = app;
		String hostname = app.getSettings().getProperty("amqpbroadcaster.hostname");
		if (hostname != null && !hostname.trim().equals(""))
		{
			ConnectionFactory factory = createConnectionFactory();
			factory.setHost(hostname);
			String username = app.getSettings().getProperty("amqpbroadcaster.username");
			if (username != null) factory.setUsername(username);
			String password = app.getSettings().getProperty("amqpbroadcaster.password");
			if (password != null) factory.setPassword(password);
			String virtualHost = app.getSettings().getProperty("amqpbroadcaster.virtualhost");
			if (virtualHost != null) factory.setVirtualHost(virtualHost);
			String port = app.getSettings().getProperty("amqpbroadcaster.port");
			if (port != null) factory.setPort(Utils.getAsInteger(port));
			String connectionTimeout = app.getSettings().getProperty("amqpbroadcaster.connectiontimeout");
			if (connectionTimeout != null) factory.setConnectionTimeout(Utils.getAsInteger(connectionTimeout));
			String handshakeTimeout = app.getSettings().getProperty("amqpbroadcaster.handshaketimeout");
			if (handshakeTimeout != null) factory.setHandshakeTimeout(Utils.getAsInteger(handshakeTimeout));
			String shutdownTimeout = app.getSettings().getProperty("amqpbroadcaster.shutdowntimeout");
			if (shutdownTimeout != null) factory.setShutdownTimeout(Utils.getAsInteger(shutdownTimeout));
			String channelRpcTimeout = app.getSettings().getProperty("amqpbroadcaster.rpctimeout");
			if (channelRpcTimeout != null) factory.setChannelRpcTimeout(Utils.getAsInteger(channelRpcTimeout));


			int sharedPoolSize = Utils.getAsInteger(app.getSettings().getProperty("amqpbroadcaster.sharedexecutor.poolsize", "4"));
			sharedExecutor = Executors.newFixedThreadPool(sharedPoolSize);
			factory.setSharedExecutor(sharedExecutor);

			int shutdownPoolSize = Utils.getAsInteger(app.getSettings().getProperty("amqpbroadcaster.shutdownexecutor.poolsize", "2"));
			shutdownExecutor = Executors.newFixedThreadPool(shutdownPoolSize);
			factory.setShutdownExecutor(shutdownExecutor);

			String keystorePath = app.getSettings().getProperty("amqpbroadcaster.keystore.path");
			String keystorePassword = app.getSettings().getProperty("amqpbroadcaster.keystore.password");
			String tlsProtocols = app.getSettings().getProperty("amqpbroadcaster.tlsprotocols");

			if (!Utils.stringIsEmpty(keystorePath) && (!Utils.stringIsEmpty(keystorePassword)))
			{
				try
				{
					File keystoreFile = new File(keystorePath);
					if (keystoreFile.exists())
					{
						if (Utils.stringIsEmpty(tlsProtocols)) tlsProtocols = "TLS";
						SSLContext ctx = SSLContext.getInstance(tlsProtocols);
						KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
						String keyStoreType = keystorePath.toLowerCase().endsWith(".jks") ? "JKS" : "PKCS12";
						KeyStore sslKeyStore = KeyStore.getInstance(keyStoreType);
						try (FileInputStream is = new FileInputStream(keystoreFile))
						{
							sslKeyStore.load(is, keystorePassword.toCharArray());
							kmf.init(sslKeyStore, keystorePassword.toCharArray());
							ctx.init(kmf.getKeyManagers(), null, null);
							// use the keystore based context for the connection, else the connection would not use TLS at all
							factory.useSslProtocol(ctx);

							if (Boolean.valueOf(app.getSettings().getProperty("amqpbroadcaster.hostnameverification", "false")).booleanValue())
							{
								factory.enableHostnameVerification();
							}
						}
						catch (IOException | CertificateException | UnrecoverableKeyException | KeyManagementException e)
						{
							Debug.error("Couldnt read in the keystore file or init the SSLContext for keystore: " + keystorePath, e);
						}
					}
					else
					{
						Debug.error("Couldnt read in the keystore file " + keystorePath + " file doesn't exists");
					}
				}
				catch (NoSuchAlgorithmException | KeyStoreException e)
				{
					Debug.error("Couldn't instantiate a SSLContext with the protocol: " + tlsProtocols + " (amqpbroadcaster.tlsprotocols)", e);
				}
			}
			else if (!Utils.stringIsEmpty(tlsProtocols))
			{
				try
				{
					Debug.warn("Rabbit broadcaster will enable TLS for the protocols " + tlsProtocols +
						" without a keystore, because now keystore path and password where set");
					factory.useSslProtocol(tlsProtocols);

					if (Boolean.valueOf(app.getSettings().getProperty("amqpbroadcaster.hostnameverification", "false")).booleanValue())
					{
						factory.enableHostnameVerification();
					}
				}
				catch (Exception e)
				{
					Debug.error("Couldn't instantiate a SSLContext with the protocol: " + tlsProtocols + " (amqpbroadcaster.tlsprotocols)", e);
				}
			}


			this.exchangeName = app.getSettings().getProperty("amqpbroadcaster.exchange", EXCHANGE_NAME);
			this.routingKey = app.getSettings().getProperty("amqpbroadcaster.routingkey", ROUTING_KEY);

			String queuetype = app.getSettings().getProperty("amqpbroadcaster.queuetype", null);
			String singleActiveConsumer = app.getSettings().getProperty("amqpbroadcaster.singleactiveconsumer", null);
			if (queuetype != null || singleActiveConsumer != null)
			{
				this.queueArgs = new HashMap<>();
				if (queuetype != null)
				{
					this.queueArgs.put("x-queue-type", queuetype);
				}
				if (singleActiveConsumer != null)
				{
					this.queueArgs.put("x-single-active-consumer", Boolean.valueOf(singleActiveConsumer));
				}
			}

			try
			{
				final IDataNotifyService dataNotifyService = app.getDataNotifyService();

				connection = factory.newConnection();
				if (connection instanceof RecoverableConnection)
				{
					((RecoverableConnection)connection).addRecoveryListener(new RecoveryListener()
					{
						@Override
						public void handleRecoveryStarted(Recoverable recoverable)
						{
							Debug.log("RabbitMQ connection is in recovery mode," + exchangeName + " " + routingKey + " for the datanotify listener");
						}

						@Override
						public void handleRecovery(Recoverable recoverable)
						{
							Debug.log("RabbitMQ connection is in recovery mode," + exchangeName + " " + routingKey +
								" for the datanotify listener, sending a flush all for all datasources");
							// a genuine connection-level recovery window has opened again, so re-enable channel
							// auto-rebuild that may have been locked out after MAX_REBUILD_ATTEMPTS
							rebuildAttempts = 0;
							// when a connection is recovered, we don't know what we missed so the only thing to do is a full flush
							// of all the touched datasources.
							String[] datasources = dataNotifyService.getUsedDataSources();
							for (String ds : datasources)
							{
								dataNotifyService.flushCachedDatabaseData(ds, null);
							}
						}
					});
				}
				Channel channel = connection.createChannel();
				this.channelHolder = new ChannelHolder(channel);

				declareExchange(channel);
				this.dataNotifyListener = new DataNotifyListener(ORIGIN_SERVER_UUID, channelHolder, connection, exchangeName, routingKey, this);
				dataNotifyService.registerDataNotifyListener(dataNotifyListener);

				declareAndConsume(channel, dataNotifyService);
				installShutdownListener(channel, dataNotifyService);
			}
			catch (Exception e)
			{
				Debug.error("Error in databroadcaster plugin, can't initialize a connection, please check hostname/username/password", e);
			}
		}
	}

	/**
	 * Creates the RabbitMQ connection factory (package-private so a test can hand out a fake connection instead of
	 * connecting to a broker).
	 */
	ConnectionFactory createConnectionFactory()
	{
		return new ConnectionFactory();
	}

	/**
	 * Declares the fanout exchange on the given channel. Shared by the initial setup and the channel
	 * rebuild path so both declare the exchange identically.
	 */
	private void declareExchange(Channel channel) throws IOException
	{
		channel.exchangeDeclare(exchangeName, "fanout");
	}

	/**
	 * Declares and binds the consumer queue (with the same queuetype / singleactiveconsumer args as the
	 * original declare) and registers the DefaultConsumer on the given channel. Shared by the initial
	 * setup and the channel rebuild path.
	 */
	private void declareAndConsume(Channel channel, final IDataNotifyService dataNotifyService) throws IOException
	{
		String queueName = queueArgs != null ? channel.queueDeclare("", false, true, true, queueArgs).getQueue() : channel.queueDeclare().getQueue();
		channel.queueBind(queueName, exchangeName, routingKey);

		Consumer consumer = new DefaultConsumer(channel)
		{

			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException
			{
				// the stream is opened inside the try: a body that is no serialized java object is logged and skipped,
				// it must not throw out of the consumer (the RabbitMQ client would close the channel)
				try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(body)))
				{
					Object readObject = ois.readObject();
					if (readObject instanceof NotifyData)
					{
						NotifyData nd = (NotifyData)readObject;
						if (!ORIGIN_SERVER_UUID.equals(nd.originServerUUID))
						{
							if (nd.dataSource != null)
							{
								dataNotifyService.flushCachedDatabaseData(nd.dataSource, nd.broadcastFilters);
							}
							else
							{
								dataNotifyService.notifyDataChange(nd.server_name, nd.table_name, nd.pks, nd.action, nd.insertColumnData,
									nd.broadcastFilters);
							}
						}
					}
					else if (readObject instanceof BroadcastMessage)
					{
						if (DataNotifyBroadCaster.this.messageConsumer != null)
						{
							if (!ORIGIN_SERVER_UUID.equals(((BroadcastMessage)readObject).originServerUUID))
							{
								DataNotifyBroadCaster.this.messageConsumer.handleDelivery((BroadcastMessage)readObject);
							}
						}
						else
						{
							Debug.error("a message came without messageConsumer being set: " + readObject);
						}
					}
					else
					{
						Debug.error("an object get from the queue that was not an NotifyData: " + readObject);
					}
				}
				catch (Exception e)
				{
					Debug.error(e);
				}
			}
		};
		channel.basicConsume(queueName, true, "", true, false, null, consumer);
	}

	/**
	 * Installs a channel-level ShutdownListener that rebuilds the channel when the broker closes it for
	 * a channel-level protocol error (e.g. precondition_failed, reply-code 406) while the TCP connection
	 * stays open. Application-initiated closes (our own unload()/channel.close()) and closes that happen
	 * because the connection itself is going down are left to the connection-level recovery path.
	 */
	private void installShutdownListener(final Channel channel, final IDataNotifyService dataNotifyService)
	{
		channel.addShutdownListener(cause -> {
			boolean appInitiated = cause.isInitiatedByApplication();
			Debug.warn("RabbitMQ databroadcast channel was shut down (" + (appInitiated ? "application-initiated" : "broker-initiated") + "), reason: " +
				cause.getReason() + ", exchange " + exchangeName + " routingKey " + routingKey);
			if (unloading)
			{
				Debug.log("RabbitMQ databroadcast channel shutdown ignored, the plugin is unloading");
				return;
			}
			if (appInitiated)
			{
				// our own close() / channel.close(); nothing to rebuild
				return;
			}
			if (connection == null || !connection.isOpen())
			{
				// the connection itself is closing/closed; the connection RecoveryListener owns that and
				// will re-flush, so a channel rebuild here would race it
				Debug.log("RabbitMQ databroadcast channel shutdown while the connection is also closing, leaving recovery to the connection recovery path");
				return;
			}
			if (!rebuilding.compareAndSet(false, true))
			{
				// a rebuild is already in progress; do not start another
				Debug.log("RabbitMQ databroadcast channel rebuild already in progress, ignoring this shutdown signal");
				return;
			}
			// never block the RabbitMQ shutdown dispatch thread - run the rebuild on the plugin executor
			ExecutorService executor = shutdownExecutor != null ? shutdownExecutor : sharedExecutor;
			if (executor == null)
			{
				rebuilding.set(false);
				Debug.error("RabbitMQ databroadcast channel cannot be rebuilt, no executor available");
				return;
			}
			executor.submit(() -> {
				try
				{
					rebuildChannel(dataNotifyService);
				}
				finally
				{
					rebuilding.set(false);
				}
			});
		});
	}

	/**
	 * Rebuilds the single shared channel from the still-open connection after a broker-initiated channel
	 * close: creates a fresh channel, re-declares the exchange, re-declares/binds the queue and
	 * re-registers the consumer, installs a fresh ShutdownListener, publishes the new channel into the
	 * holder and drains the publisher's failedList through the guarded publish path. Bounded by
	 * {@link #MAX_REBUILD_ATTEMPTS} with a short backoff so it never retries in a tight loop.
	 */
	private void rebuildChannel(IDataNotifyService dataNotifyService)
	{
		while (rebuildAttempts < MAX_REBUILD_ATTEMPTS)
		{
			if (unloading || connection == null || !connection.isOpen())
			{
				return;
			}
			rebuildAttempts++;
			Debug.warn("RabbitMQ databroadcast channel rebuild started (attempt " + rebuildAttempts + " of " + MAX_REBUILD_ATTEMPTS + "), exchange " +
				exchangeName + " routingKey " + routingKey);
			try
			{
				Channel newChannel = connection.createChannel();
				declareExchange(newChannel);
				declareAndConsume(newChannel, dataNotifyService);
				installShutdownListener(newChannel, dataNotifyService);
				channelHolder.setChannel(newChannel);
				if (dataNotifyListener != null) dataNotifyListener.drainFailedList();
				rebuildAttempts = 0;
				Debug.warn("RabbitMQ databroadcast channel rebuild succeeded, exchange/queue re-declared and consumer re-registered, failedList drained");
				return;
			}
			catch (Exception e)
			{
				Debug.error("RabbitMQ databroadcast channel rebuild failed (attempt " + rebuildAttempts + " of " + MAX_REBUILD_ATTEMPTS + ")", e);
				if (rebuildAttempts >= MAX_REBUILD_ATTEMPTS)
				{
					Debug.error("RabbitMQ databroadcast channel rebuild giving up after " + MAX_REBUILD_ATTEMPTS +
						" attempts, auto-rebuild stops until the next connection-level recovery");
					return;
				}
				try
				{
					Thread.sleep(REBUILD_BACKOFF_MS);
				}
				catch (InterruptedException ie)
				{
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	/**
	 * Computes the effective maximum serialized databroadcast body size in bytes: the
	 * amqpbroadcaster.maxmessagesize setting when set, else the broker frame max minus a safety margin,
	 * else a safe 16 MB-minus-margin default when the frame max is 0/unlimited or unavailable.
	 */
	long getEffectiveMaxMessageSize(Connection conn)
	{
		if (application != null)
		{
			String configured = application.getSettings().getProperty("amqpbroadcaster.maxmessagesize");
			if (!Utils.stringIsEmpty(configured))
			{
				long value = Utils.getAsLong(configured);
				if (value > 0) return value;
			}
		}
		if (conn != null)
		{
			int frameMax = conn.getFrameMax();
			if (frameMax > FRAME_SAFETY_MARGIN)
			{
				return frameMax - FRAME_SAFETY_MARGIN;
			}
		}
		return DEFAULT_MAX_MESSAGE_SIZE;
	}

	public IBroadcastMessageSender registerMessageBroadcastConsumer(IBroadcastMessageConsumer mc)
	{
		if (this.channelHolder != null)
		{
			final String exchange = application.getSettings().getProperty("amqpbroadcaster.exchange", EXCHANGE_NAME);
			final String routing = application.getSettings().getProperty("amqpbroadcaster.routingkey", ROUTING_KEY);
			this.messageConsumer = mc;
			return (message) -> {
				ByteArrayOutputStream baos;
				try
				{
					baos = new ByteArrayOutputStream();
					ObjectOutputStream oos = new ObjectOutputStream(baos);
					oos.writeObject(message);
					oos.close();
				}
				catch (Exception e)
				{
					Debug.error("failed to serialize " + message, e);
					return;
				}
				try
				{
					if (baos != null)
					{
						// read the current (possibly rebuilt) channel from the holder so we publish on a live channel
						Channel channel = channelHolder.getChannel();
						if (!connection.isOpen() || channel == null || !channel.isOpen())
						{
							Debug.warn("RabbitMQ message broadcast skipped, connection or channel is not open");
							return;
						}
						byte[] bytes = baos.toByteArray();
						long maxSize = getEffectiveMaxMessageSize(connection);
						if (bytes.length > maxSize)
						{
							// a BroadcastMessage is opaque so there is no flush fallback; skip it and keep the channel
							// alive rather than letting the broker close it for an oversize (precondition_failed)
							Debug.error("RabbitMQ message broadcast skipped, serialized message is oversized (" + bytes.length + " bytes, limit " + maxSize +
								" bytes): " + message);
							return;
						}
						channel.basicPublish(exchange, routing, null, bytes);
					}
				}
				catch (Exception e)
				{
					Debug.error(e);
				}
			};
		}
		return null;
	}

	@Override
	public Map<String, String> getRequiredPropertyNames()
	{
		Map<String, String> req = new LinkedHashMap<String, String>();
		req.put("amqpbroadcaster.hostname", "Set the hostname of the AMQP (RabbitMQ) server where to connect to (this is mandatory field)");
		req.put("amqpbroadcaster.username", "Set the username of the AMQP (RabbitMQ) server where to connect to (default value is guest)");
		req.put("amqpbroadcaster.password", "Set the password of the AMQP (RabbitMQ) server where to connect to (default value is guest)");
		req.put("amqpbroadcaster.virtualhost", "Set the virtual host of the AMQP (RabbitMQ) server where to connect to (default value is / )");
		req.put("amqpbroadcaster.port",
			"Set the port of the AMQP (RabbitMQ) server where to connect to (default value is 5671 for SSL connection and 5672 for default connection)");
		req.put("amqpbroadcaster.exchange", "Set the exchange through which the databroadcast notifications are send (default value is databroadcast)");
		req.put("amqpbroadcaster.routingkey", "Set the key for routing the databroadcast notifications (default to empty string)");
		req.put("amqpbroadcaster.connectiontimeout", "Set the connection timeout of the AMQP (RabbitMQ) connection (default value 60000 - 60 seconds)");
		req.put("amqpbroadcaster.handshaketimeout", "Set the handshake timeout of the AMQP (RabbitMQ) connection (default value 10000 - 10 seconds)");
		req.put("amqpbroadcaster.shutdowntimeout", "Set the shutdown timeout of the AMQP (RabbitMQ) connection (default value 10000 - 10 seconds)");
		req.put("amqpbroadcaster.rpctimeout", "Set the rpc continuation timeout of the AMQP (RabbitMQ) channel (default value 10 minutes)");
		req.put("amqpbroadcaster.keystore.path",
			"The path on the system that points to a the keystore to enable TLS communication, a .JKS file will use the JKS keystore format, else it will use PKCS12 format");
		req.put("amqpbroadcaster.keystore.password", "The password to access/read the keystore and key of the amqpbroadcaster.keystore.path");
		req.put("amqpbroadcaster.tlsprotocols",
			"When set this will enabled TLS communication over the given protocol like TLSv1.2 or TLSv1.3. WARNING: Without a keystore this will not verify the certificates only enable tls communication");
		req.put("amqpbroadcaster.hostnameverification",
			"When set to true this will enable the hostname verification for the TLS conncetions (TLS must be enabled) (default false)");
		req.put("amqpbroadcaster.queuetype",
			"What type of queue algorithm will be used, quorum or classic (default classic)");
		req.put("amqpbroadcaster.singleactiveconsumer",
			"Single active consumer allows to have only one consumer at a time consuming from a queue and to fail over to another registered consumer in case the active one is cancelled or dies. (default false)");
		req.put("amqpbroadcaster.sharedexecutor.poolsize",
			"Set the thread pool size for the shared executor used by the RabbitMQ client for consumer dispatch and error handling (default 4)");
		req.put("amqpbroadcaster.shutdownexecutor.poolsize",
			"Set the thread pool size for the shutdown executor used to process connection/channel shutdown signals (default 2)");
		req.put("amqpbroadcaster.maxmessagesize",
			"Maximum serialized databroadcast payload size in bytes before falling back to a datasource flush instead of the raw row data (default: derived from the broker frame max with a safety margin, or 16MB minus margin when unlimited)");
		return req;
	}

	public static void main(String[] args) throws Exception
	{
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();
		channel.exchangeDeclare(EXCHANGE_NAME, "fanout");

		String queueName = channel.queueDeclare().getQueue();
		channel.queueBind(queueName, EXCHANGE_NAME, "");

		Consumer consumer = new DefaultConsumer(channel)
		{
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException
			{
				ByteArrayInputStream bais = new ByteArrayInputStream(body);
				ObjectInputStream ois = new ObjectInputStream(bais);
				try
				{
					Object readObject = ois.readObject();
					System.err.println("delivery in reader of " + readObject);
				}
				catch (Exception e)
				{
					Debug.error(e);
				}
			}
		};
		channel.basicConsume(queueName, consumer);
	}
}