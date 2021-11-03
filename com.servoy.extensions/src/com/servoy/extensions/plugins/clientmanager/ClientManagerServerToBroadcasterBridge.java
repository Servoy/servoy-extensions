/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2021 Servoy BV

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

import com.servoy.extensions.plugins.broadcaster.BroadcastMessage;
import com.servoy.extensions.plugins.broadcaster.DataNotifyBroadCaster;
import com.servoy.extensions.plugins.broadcaster.IBroadcastMessageConsumer;
import com.servoy.extensions.plugins.broadcaster.IBroadcastMessageSender;
import com.servoy.j2db.plugins.IServerAccess;

/**
 * @author lvostinar
 *
 */
public class ClientManagerServerToBroadcasterBridge
{
	private final IServerAccess application;
	private IBroadcastMessageSender broadcastNetworkSender;
	private final ClientManagerServer clientManagerServer;


	public ClientManagerServerToBroadcasterBridge(IServerAccess application, ClientManagerServer clientManagerServer)
	{
		this.application = application;
		this.clientManagerServer = clientManagerServer;
	}

	public void afterInit()
	{
		DataNotifyBroadCaster broadcaster = application.getPluginInstance(DataNotifyBroadCaster.class);
		if (broadcaster != null)
		{
			this.broadcastNetworkSender = broadcaster.registerMessageBroadcastConsumer(new IBroadcastMessageConsumer()
			{

				@Override
				public void handleDelivery(BroadcastMessage message)
				{
					clientManagerServer.broadcastMessageInternal(new BroadcastInfo(null, message.getName(), message.getChannelName()), message.getName());

				}
			});
		}

	}

	public void broadcastMessageToAllServers(BroadcastInfo info, String message)
	{
		if (this.broadcastNetworkSender != null)
		{
			this.broadcastNetworkSender
				.sendMessage(new BroadcastMessage(DataNotifyBroadCaster.ORIGIN_SERVER_UUID, message, info.getName(), info.getChannelName()));
		}
	}
}
