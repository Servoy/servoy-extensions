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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

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

/**
 * @author jcompagner
 */
public class DataNotifyBroadCaster implements IServerPlugin
{
	public static final String EXCHANGE_NAME = "databroadcast";
	private static final String ORIGIN_SERVER_UUID = UUID.randomUUID().toString();

	private Connection connection;
	private Channel channel;

	@Override
	public void load() throws PluginException
	{
	}

	@Override
	public void unload() throws PluginException
	{
		try
		{
			channel.close();
			connection.close();
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
		String hostname = app.getSettings().getProperty("amqpbroadcaster.hostname");
		if (hostname != null && !hostname.trim().equals(""))
		{
			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost(hostname);
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
						}

						@Override
						public void handleRecovery(Recoverable recoverable)
						{
							// when a connection is recovered, we don't know what we missed so the only thing to do is a full flush
							// of all the touched datasources.
							String[] datasources = dataNotifyService.getUsedDataSources();
							for (String ds : datasources)
							{
								dataNotifyService.flushCachedDatabaseData(ds);
							}
						}
					});
				}
				channel = connection.createChannel();

				channel.exchangeDeclare(EXCHANGE_NAME, "fanout");

				dataNotifyService.registerDataNotifyListener(new DataNotifyListener(ORIGIN_SERVER_UUID, channel, connection));

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
							if (readObject instanceof NotifyData)
							{
								NotifyData nd = (NotifyData)readObject;
								if (!ORIGIN_SERVER_UUID.equals(nd.originServerUUID))
								{
									if (nd.dataSource != null)
									{
										dataNotifyService.flushCachedDatabaseData(nd.dataSource);
									}
									else
									{
										dataNotifyService.notifyDataChange(nd.server_name, nd.table_name, nd.pks, nd.action, nd.insertColumnData);
									}
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
				throw new PluginException("can't start the connection to the message server", e);
			}
		}
	}

	@Override
	public Map<String, String> getRequiredPropertyNames()
	{
		Map<String, String> req = new LinkedHashMap<String, String>();
		req.put("amqpbroadcaster.hostname", "Set the hostname of the AMQP (RabbitMQ) server where to connect to");
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
