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
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoverableConnection;
import com.rabbitmq.client.RecoveryListener;
import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.plugins.IDataNotifyListener;
import com.servoy.j2db.util.Debug;

/**
 * @author jcomp
 *
 */
public class DataNotifyListener implements IDataNotifyListener
{
	private final Channel channel;
	private final String originServerUUID;
	private final List<byte[]> failedList = new ArrayList<>();

	/**
	 * @param channel
	 */
	public DataNotifyListener(String originServerUUID, Channel channel, Connection connection)
	{
		this.originServerUUID = originServerUUID;
		this.channel = channel;
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
	public void flushCachedDatabaseData(String dataSource)
	{
		sendBytes(new NotifyData(originServerUUID, dataSource));
	}

	@Override
	public void notifyDataChange(String server_name, String table_name, IDataSet pks, int action, Object[] insertColumnData)
	{
		sendBytes(new NotifyData(originServerUUID, server_name, table_name, pks, action, insertColumnData));
	}

	/**
	 * @param nd
	 * @return
	 * @throws IOException
	 */
	private void sendBytes(NotifyData nd)
	{
		ByteArrayOutputStream baos;
		try
		{
			baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(nd);
			oos.close();
		}
		catch (Exception e)
		{
			Debug.error("failed to serialize " + nd, e);
			return;
		}
		sendBytes(baos.toByteArray(), true);
	}

	/**
	 * @param channel
	 * @param bytes
	 */
	private void sendBytes(byte[] bytes, boolean testFailedList)
	{
		if (testFailedList && failedList.size() > 0)
		{
			if (!channel.isOpen())
			{
				synchronized (failedList)
				{
					failedList.add(bytes);
				}
				return;
			}
			else
			{
				sendFailedList();
			}
		}
		try
		{
			channel.basicPublish(DataNotifyBroadCaster.EXCHANGE_NAME, "", null, bytes);
		}
		catch (Exception e)
		{
			Debug.error(e);
			synchronized (failedList)
			{
				failedList.add(bytes);
			}
		}
	}

	/**
	 *
	 */
	private void sendFailedList()
	{
		byte[][] failedBytes = null;
		synchronized (failedList)
		{
			failedBytes = new byte[failedList.size()][];
			failedBytes = failedList.toArray(failedBytes);
			failedList.clear();
		}
		for (byte[] bytes : failedBytes)
		{
			sendBytes(bytes, false);
		}
	}
}
