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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

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
import com.servoy.j2db.plugins.IDataNotifyService;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * @author jcompagner
 */
@SuppressWarnings("nls")
public class DataNotifyBroadCaster implements IServerPlugin
{
	private static final String EXCHANGE_NAME = "databroadcast";
	private static final String ROUTING_KEY = "";
	public static final String ORIGIN_SERVER_UUID = UUID.randomUUID().toString();

	private Connection connection;
	private Channel channel;

	private IBroadcastMessageConsumer messageConsumer;
	private IServerAccess application;

	@Override
	public void load() throws PluginException
	{
	}

	@Override
	public void unload() throws PluginException
	{
		try
		{
			if (channel != null) channel.close();
			if (connection != null) connection.close();
		}
		catch (Exception e)
		{
			Debug.error(e);
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
			ConnectionFactory factory = new ConnectionFactory();
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


			String exchangeName = app.getSettings().getProperty("amqpbroadcaster.exchange", EXCHANGE_NAME);
			String routingKey = app.getSettings().getProperty("amqpbroadcaster.routingkey", ROUTING_KEY);

			String dbServersRaw = app.getSettings().getProperty("amqpbroadcaster.dbservers", "");
			final List<String> dbServers;
			if (dbServersRaw.trim().isEmpty())
				dbServers = new ArrayList<>();
			else
				dbServers = Arrays.stream(dbServersRaw.split(",")).map(String::trim).toList();

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
				channel = connection.createChannel();

				channel.exchangeDeclare(exchangeName, "fanout");
				dataNotifyService.registerDataNotifyListener(new DataNotifyListener(ORIGIN_SERVER_UUID, channel, connection, exchangeName, routingKey));

				String queueName = channel.queueDeclare().getQueue();
				channel.queueBind(queueName, exchangeName, routingKey);

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
							if (readObject instanceof NotifyData)
							{
								NotifyData nd = (NotifyData)readObject;
								if (!ORIGIN_SERVER_UUID.equals(nd.originServerUUID))
								{
									if (!dbServers.isEmpty() && !dbServers.contains(nd.server_name))
										return;
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
			catch (Exception e)
			{
				Debug.error("Error in databroadcaster plugin, can't initialize a connection, please check hostname/username/password", e);
			}
		}
	}

	public IBroadcastMessageSender registerMessageBroadcastConsumer(IBroadcastMessageConsumer mc)
	{
		if (this.channel != null)
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
						channel.basicPublish(exchange, routing, null, baos.toByteArray());
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
		req.put("amqpbroadcaster.dbservers", "Set the comma-delimited list of database servers that will support databroadcasting (default value is all servers)");
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
