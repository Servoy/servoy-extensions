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

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.ServoyException;
import com.servoy.j2db.util.Utils;

/**
 * Rawsql plugin server plugin.
 *
 * @author jblok
 */

public class SQLProcessor implements ISQLService, IServerPlugin
{
	private IServerAccess application;

	public SQLProcessor()//must have default constructor
	{
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "Raw SQL Plugin"); //$NON-NLS-1$
		return props;
	}

	public void load() throws PluginException
	{
	}

	public void initialize(IServerAccess app) throws PluginException
	{
		application = app;
		try
		{
			app.registerRMIService("servoy.ISQLService", this); //$NON-NLS-1$
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
	}

	public void unload() throws PluginException
	{
	}

	/*
	 * @see IPlugin#getPreferencePanels()
	 */
	public PreferencePanel[] getPreferencePanels()
	{
		return null;
	}

	public Map<String, String> getRequiredPropertyNames()
	{
		Map<String, String> req = new HashMap<String, String>();
		req.put("servoy.rawSQL.allowClientCacheFlushes", "In case of performance problem you might want to disable this (true/false)"); //$NON-NLS-1$ //$NON-NLS-2$
		return req;
	}

	public boolean executeSQL(String clientId, String server, String sql, Object[] questiondata, String tid)
	{
		if (!checkAccess(clientId)) return false;

		Connection connection = null;
		Statement ps = null;
		try
		{
			connection = application.getDBServerConnection(server, tid, clientId);
			if (connection != null)
			{
				if (questiondata == null || questiondata.length == 0)
				{
					ps = connection.createStatement();
					long t1 = System.currentTimeMillis();
					Integer perfUuid = application.addPerformanceTiming(server, sql, 0 - t1, clientId);
					try
					{
						ps.execute(sql);
					}
					finally
					{
						application.endPerformanceTiming(server, perfUuid, clientId);
					}
				}
				else
				{
					ps = connection.prepareStatement(sql);
					for (int i = 0; i < questiondata.length; i++)
					{
						Object data = questiondata[i];
						if (data != null && data.getClass().equals(Date.class))
						{
							data = new Timestamp(((Date)data).getTime());
						}
						((PreparedStatement)ps).setObject(i + 1, data);
					}
					long t1 = System.currentTimeMillis();
					Integer perfUuid = application.addPerformanceTiming(server, sql, 0 - t1, clientId);
					try
					{
						((PreparedStatement)ps).execute();
					}
					finally
					{
						application.endPerformanceTiming(server, perfUuid, clientId);
					}
				}
				return true;
			}
			return false;
		}
		catch (Exception ex)
		{
			Debug.error(sql);
			Debug.error(ex);//log on server
			// Don't pass the exception to the RepositoryException as it may not be serializable. The string version is enough.
			throw new RuntimeException(ex.toString() + ' ' + ex.getMessage());
		}
		finally
		{
			Utils.closeStatement(ps);
			if (tid != null)
			{
				Utils.releaseConnection(connection);
			}
			else
			{
				Utils.closeConnection(connection);
			}
		}
	}

	@Override
	public IDataSet executeStoredProcedure(String clientId, String serverName, String transaction_id, String procedureDeclaration, Object[] questiondata,
		int[] inOutType, int startRow, int rowsToRetrieve) throws RepositoryException, RemoteException
	{
		if (!checkAccess(clientId)) return null;

		try
		{
			return application.executeStoredProcedure(clientId, serverName, transaction_id, procedureDeclaration, questiondata, inOutType, startRow,
				rowsToRetrieve);
		}
		catch (ServoyException e)
		{
			throw new RepositoryException(e);
		}
	}

	@Override
	public IDataSet[] executeStoredProcedure(String clientId, String serverName, String transaction_id, String procedureDeclaration, Object[] questiondata,
		int startRow, int rowsToRetrieve) throws RepositoryException, RemoteException
	{
		if (!checkAccess(clientId)) return null;

		try
		{
			return application.executeStoredProcedure(clientId, serverName, transaction_id, procedureDeclaration, questiondata, startRow, rowsToRetrieve);
		}
		catch (ServoyException e)
		{
			throw new RepositoryException(e);
		}
	}

	public boolean flushAllClientsCache(String client_id, boolean notifySelf, String server_name, String table, String tid) throws RemoteException
	{
		if (Utils.getAsBoolean(application.getSettings().getProperty("servoy.rawSQL.allowClientCacheFlushes", "true"))) //$NON-NLS-1$ //$NON-NLS-2$
		{
			return notifyDataChange(client_id, notifySelf, server_name, table, null, 0, tid);
		}
		return false;
	}

	public boolean notifyDataChange(String client_id, boolean notifySelf, String server_name, String tableName, IDataSet pks, int action, String transaction_id)
		throws RemoteException
	{
		if (!checkAccess(client_id)) return false;

		if (Utils.getAsBoolean(application.getSettings().getProperty("servoy.rawSQL.allowClientCacheFlushes", "true"))) //$NON-NLS-1$ //$NON-NLS-2$
		{
			return ApplicationServerRegistry.get().getDataServer().notifyDataChange(notifySelf ? null : client_id, server_name, tableName, pks, action,
				transaction_id);
		}
		return false;
	}

	protected final boolean checkAccess(String clientId)
	{
		// this plugin may be accessed by server processes or authenticated clients
		boolean access = application.isServerProcess(clientId) || application.isAuthenticated(clientId);
		if (!access)
		{
			Debug.warn("Rejected unauthenticated access"); //$NON-NLS-1$
		}
		return access;
	}
}
