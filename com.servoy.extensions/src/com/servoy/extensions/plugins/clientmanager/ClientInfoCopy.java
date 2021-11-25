/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2018 Servoy BV

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
import java.util.Arrays;
import java.util.Date;

import com.servoy.j2db.server.shared.IClientInformation;

/**
 * @author jcomp
 *
 */
public class ClientInfoCopy implements IClientInformation, Serializable
{
	private final String clientID;
	private final String hostIdentifier;
	private final String hostName;
	private final String hostAddress;
	private final String userName;
	private final String userUID;
	private final int applicationType;
	private final String openSolutionName;
	private final Date lastAccessedTime;
	private final String statusLine;
	private final Date idleTime;
	private final Date loginTime;
	private final String[] clientInfos;

	public ClientInfoCopy(IClientInformation info)
	{
		clientID = info.getClientID();
		hostIdentifier = info.getHostIdentifier();
		hostName = info.getHostName();
		hostAddress = info.getHostAddress();
		userName = info.getUserName();
		userUID = info.getUserUID();
		applicationType = info.getApplicationType();
		openSolutionName = info.getOpenSolutionName();
		lastAccessedTime = info.getLastAccessedTime();
		idleTime = info.getIdleTime();
		loginTime = info.getLoginTime();
		clientInfos = info.getClientInfos();
		statusLine = info.getStatusLine();

	}

	@Override
	public String getClientID()
	{
		return clientID;
	}

	@Override
	public String getHostIdentifier()
	{
		return hostIdentifier;
	}

	@Override
	public String getHostName()
	{
		return hostName;
	}

	@Override
	public String getHostAddress()
	{
		return hostAddress;
	}

	@Override
	public int getApplicationType()
	{
		return applicationType;
	}

	@Override
	public String getUserUID()
	{
		return userUID;
	}

	@Override
	public String getUserName()
	{
		return userName;
	}

	@Override
	public Date getLoginTime()
	{
		return loginTime;
	}

	@Override
	public Date getIdleTime()
	{
		return idleTime;
	}

	@Override
	public String getOpenSolutionName()
	{
		return openSolutionName;
	}

	@Override
	public Date getLastAccessedTime()
	{
		return lastAccessedTime;
	}

	@Override
	public String getStatusLine()
	{
		return statusLine;
	}

	@Override
	public String[] getClientInfos()
	{
		return clientInfos;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString()
	{
		return "CI:[clientID=" + clientID + ", hostIdentifier=" + hostIdentifier + ", hostName=" + hostName + ", hostAddress=" + hostAddress + ", userName=" +
			userName + ", userUID=" + userUID + ", applicationType=" + applicationType + ", openSolutionName=" + openSolutionName + ", lastAccessedTime=" +
			lastAccessedTime + ", idleTime=" + idleTime + ", loginTime=" + loginTime + ", clientInfos=" + Arrays.toString(clientInfos) + "]";
	}

}
