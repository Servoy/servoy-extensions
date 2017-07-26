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

package com.servoy.extensions.plugins.clientmanager;

import java.io.Serializable;

import com.servoy.j2db.util.UUID;

/**
 * @author jcompagner
 *
 */
public class BroadcastInfo implements Serializable
{
	private final String channelName;
	private final String name;
	private final IBroadcaster broadCaster;
	private final UUID uuid = UUID.randomUUID();

	public BroadcastInfo(IBroadcaster broadCaster, String name, String channelName)
	{
		this.broadCaster = broadCaster;
		this.name = name;
		this.channelName = channelName;
	}

	public String getName()
	{
		return name;
	}

	public String getChannelName()
	{
		return channelName;
	}

	/**
	 * @return the broadCaster
	 */
	public IBroadcaster getBroadCaster()
	{
		return broadCaster;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof BroadcastInfo)
		{
			return uuid.equals(((BroadcastInfo)obj).uuid);
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		return uuid.hashCode();
	}
}
