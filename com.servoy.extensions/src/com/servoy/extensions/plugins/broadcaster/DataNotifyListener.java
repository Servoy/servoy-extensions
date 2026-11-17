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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoverableConnection;
import com.rabbitmq.client.RecoveryListener;
import com.servoy.j2db.dataprocessing.BroadcastFilter;
import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.plugins.IDataNotifyListener;
import com.servoy.j2db.util.DataSourceUtils;
import com.servoy.j2db.util.Debug;

/**
 * @author jcompagner
 *
 */
@SuppressWarnings("nls")
public class DataNotifyListener implements IDataNotifyListener
{
	private final ChannelHolder channelHolder;
	private final Connection connection;
	private final String originServerUUID;
	// the failed queue carries the typed NotifyData (not raw bytes) so every replay on drain goes
	// through the same size guard: a previously-oversized entry becomes a datasource flush on drain
	// instead of being replayed verbatim as oversized bytes that would kill the rebuilt channel again
	private final List<NotifyData> failedList = new ArrayList<>();
	private final String exchangeName;
	private final String routingKey;
	private final DataNotifyBroadCaster plugin;

	/**
	 * @param channelHolder holder of the current (possibly rebuilt) shared channel
	 */
	public DataNotifyListener(String originServerUUID, ChannelHolder channelHolder, Connection connection, String exchangeName, String routingKey,
		DataNotifyBroadCaster plugin)
	{
		this.originServerUUID = originServerUUID;
		this.channelHolder = channelHolder;
		this.connection = connection;
		this.exchangeName = exchangeName;
		this.routingKey = routingKey;
		this.plugin = plugin;
		if (connection instanceof RecoverableConnection)
		{
			((RecoverableConnection)connection).addRecoveryListener(new RecoveryListener()
			{
				@Override
				public void handleRecoveryStarted(Recoverable recoverable)
				{
					Debug.log("RabbitMQ connection is in recovery mode," + exchangeName + " " + routingKey + " for the datanotify sender");
				}

				@Override
				public void handleRecovery(Recoverable recoverable)
				{
					Debug.log("RabbitMQ connection is recovered," + exchangeName + " " + routingKey + " for the datanotify sender");
					sendFailedList();
				}
			});
		}
		else
		{
			Debug.warn("amqpbroadcaster is not in auto recovery, if the messaging service fails then some databroadcast can be lost");
		}
	}

	@Override
	public void flushCachedDatabaseData(String dataSource, BroadcastFilter[] broadcastFilters)
	{
		publish(new NotifyData(originServerUUID, dataSource, broadcastFilters), true);
	}

	@Override
	public void notifyDataChange(String server_name, String table_name, IDataSet pks, int action, Object[] insertColumnData, BroadcastFilter[] broadcastFilters)
	{
		publish(new NotifyData(originServerUUID, server_name, table_name, pks, action, insertColumnData, broadcastFilters), true);
	}

	/**
	 * Serializes the given NotifyData and publishes it, guarding against an oversized payload that
	 * the broker would reject with a channel-level close (precondition_failed). When the serialized
	 * row data exceeds the effective limit, a coarse datasource flush NotifyData is published instead
	 * of the raw row data so receivers still invalidate their caches and the channel stays alive.
	 *
	 * This is the single guarded publish entry point. Both the live publish path and the failedList
	 * drain path run through here, so a previously-oversized queued entry is converted to a flush on
	 * drain and never reaches basicPublish as raw oversized bytes.
	 *
	 * @param nd the typed payload to publish
	 * @param testFailedList when true (the live publish path) a healthy channel with a backlog triggers a
	 *            drain first; the drain loop itself publishes with false so it can never re-enter the drain
	 *            and perturb FIFO replay order under a transient failure
	 */
	private void publish(NotifyData nd, boolean testFailedList)
	{
		byte[] bytes = serialize(nd);
		if (bytes == null) return;

		long maxSize = plugin.getEffectiveMaxMessageSize(connection);
		if (bytes.length > maxSize)
		{
			String dataSource = nd.dataSource;
			if (dataSource == null && nd.server_name != null && nd.table_name != null)
			{
				dataSource = DataSourceUtils.createDBTableDataSource(nd.server_name, nd.table_name);
			}
			Debug.warn("RabbitMQ databroadcast payload for datasource " + dataSource + " is oversized (" + bytes.length + " bytes, limit " + maxSize +
				" bytes); sending a datasource flush instead of the raw row data to avoid a broker channel close");
			if (dataSource == null)
			{
				Debug.error("RabbitMQ oversized databroadcast could not be reduced to a flush (no datasource), skipping to keep the channel alive: " + nd);
				return;
			}
			NotifyData flush = new NotifyData(originServerUUID, dataSource, nd.broadcastFilters);
			byte[] flushBytes = serialize(flush);
			if (flushBytes == null) return;
			if (flushBytes.length > maxSize)
			{
				Debug.error("RabbitMQ flush fallback for datasource " + dataSource + " is still oversized (" + flushBytes.length + " bytes, limit " + maxSize +
					" bytes), skipping to keep the channel alive");
				return;
			}
			// publish the flush form, and queue the flush NotifyData (not the oversized original) on failure
			sendBytes(flush, flushBytes, testFailedList);
			return;
		}
		sendBytes(nd, bytes, testFailedList);
	}

	/**
	 * @param nd the notify data to serialize
	 * @return the serialized bytes, or null when serialization failed
	 */
	private byte[] serialize(NotifyData nd)
	{
		try
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(nd);
			oos.close();
			return baos.toByteArray();
		}
		catch (Exception e)
		{
			Debug.error("failed to serialize " + nd, e);
			return null;
		}
	}

	/**
	 * Publishes the already-serialized bytes for the given NotifyData. The typed NotifyData is carried
	 * alongside the bytes so that, when the connection/channel is not open or basicPublish throws, the
	 * typed payload (not the raw bytes) is queued on the failedList; every replay then goes back through
	 * the guarded {@link #publish(NotifyData)} path on drain.
	 *
	 * @param nd the typed payload being published, queued on the failedList on failure
	 * @param bytes the already-serialized (and size-guarded) body for nd
	 * @param testFailedList when true and there is a backlog, drain it first before publishing
	 */
	private void sendBytes(NotifyData nd, byte[] bytes, boolean testFailedList)
	{
		if (!connection.isOpen())
		{
			synchronized (failedList)
			{
				failedList.add(nd);
			}
			return;
		}
		Channel channel = channelHolder.getChannel();
		if (testFailedList && failedList.size() > 0)
		{
			if (channel == null || !channel.isOpen())
			{
				synchronized (failedList)
				{
					failedList.add(nd);
				}
				return;
			}
			else
			{
				sendFailedList();
			}
		}
		if (channel == null || !channel.isOpen())
		{
			// on the drain path (testFailedList == false) a null/closed channel would otherwise reach
			// basicPublish and throw a bare NPE/AlreadyClosedException; log meaningfully and re-queue the
			// typed payload so it is replayed (and re-guarded) once the channel is rebuilt
			Debug.warn("RabbitMQ databroadcast channel is not open, queueing the databroadcast for replay after channel recovery: " + nd);
			synchronized (failedList)
			{
				failedList.add(nd);
			}
			return;
		}
		try
		{
			channel.basicPublish(exchangeName, routingKey, null, bytes);
		}
		catch (Exception e)
		{
			Debug.error("RabbitMQ databroadcast publish failed, queueing for replay after channel recovery: " + nd, e);
			synchronized (failedList)
			{
				failedList.add(nd);
			}
		}
	}

	/**
	 * Package-visible hook so the plugin can drain the queued databroadcasts after a channel rebuild.
	 */
	void drainFailedList()
	{
		sendFailedList();
	}

	/**
	 * Drains the failed queue by re-running the guarded publish logic for each queued NotifyData (in
	 * order): each entry is re-serialized, re-checked against the effective limit and, when still
	 * oversized, replaced by a datasource flush - so a previously-oversized entry becomes a flush on
	 * drain and no raw oversized body is ever handed to basicPublish.
	 *
	 * Each entry is published with testFailedList == false so this loop can never re-enter itself
	 * (via sendBytes seeing a non-empty failedList); a transient failure re-queues the typed entry for
	 * a later drain without reordering relative to the entries still being replayed here.
	 */
	private void sendFailedList()
	{
		NotifyData[] failed = null;
		synchronized (failedList)
		{
			failed = failedList.toArray(new NotifyData[failedList.size()]);
			failedList.clear();
		}
		for (NotifyData nd : failed)
		{
			// run each entry back through the size guard, but with testFailedList == false so this drain
			// loop never re-enters sendFailedList(): a re-queued transient failure is simply left on the
			// failedList for a later drain, preserving strict FIFO replay order within this loop
			publish(nd, false);
		}
	}
}
