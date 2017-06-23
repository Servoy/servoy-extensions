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

import java.io.Serializable;
import java.util.Arrays;

import com.servoy.j2db.dataprocessing.IDataSet;

public final class NotifyData implements Serializable
{
	final String originServerUUID;
	final String server_name;
	final String table_name;
	final IDataSet pks;
	final int action;
	final Object[] insertColumnData;
	final String dataSource;

	public NotifyData(String originServerUUID, String server_name, String table_name, IDataSet pks, int action, Object[] insertColumnData)
	{
		this.originServerUUID = originServerUUID;
		this.server_name = server_name;
		this.table_name = table_name;
		this.pks = pks;
		this.action = action;
		this.insertColumnData = insertColumnData;
		this.dataSource = null;
	}

	/**
	 * @param dataSource
	 */
	public NotifyData(String originServerUUID, String dataSource)
	{
		this.originServerUUID = originServerUUID;
		this.dataSource = dataSource;
		this.server_name = null;
		this.table_name = null;
		this.pks = null;
		this.action = 0;
		this.insertColumnData = null;
	}

	@Override
	public String toString()
	{
		return "NotifyData [server_name=" + server_name + ", table_name=" + table_name + ", pks=" + pks + ", action=" + action + ", insertColumnData=" +
			Arrays.toString(insertColumnData) + ", dataSource=" + dataSource + ", origin=" + originServerUUID + "]";
	}
}