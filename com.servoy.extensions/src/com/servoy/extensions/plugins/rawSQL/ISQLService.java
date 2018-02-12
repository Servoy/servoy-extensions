/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

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
package com.servoy.extensions.plugins.rawSQL;

import java.rmi.Remote;
import java.rmi.RemoteException;

import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.persistence.RepositoryException;

public interface ISQLService extends Remote
{
	public boolean executeSQL(String clientId, String server, String sql, Object[] sql_args, String server_transaction_id) throws RemoteException;

	public IDataSet executeStoredProcedure(String clientId, String serverName, String transaction_id, String procedureDeclaration, Object[] args,
		int[] inOutType, int startRow, int maxNumberOfRowsToRetrieve) throws RepositoryException, RemoteException;

	public IDataSet[] executeStoredProcedure(String clientId, String serverName, String transaction_id, String procedureDeclaration, Object[] args,
		int startRow, int maxNumberOfRowsToRetrieve) throws RepositoryException, RemoteException;

	public boolean flushAllClientsCache(String client_id, boolean notifySelf, String server_name, String table, String transaction_id) throws RemoteException;

	public boolean notifyDataChange(String client_id, boolean notifySelf, String server_name, String tableName, IDataSet pks, int action, String transaction_id)
		throws RemoteException;
}
