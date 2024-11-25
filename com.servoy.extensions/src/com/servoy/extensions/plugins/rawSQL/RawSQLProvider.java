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

import java.util.Collection;

import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.ClientPluginAccessProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.DataSourceUtils;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.ServoyException;
import com.servoy.j2db.util.UUID;

/**
 * <p>The <code>rawSQL</code> plugin allows developers to execute raw SQL commands and manage advanced
 * database interactions within Servoy. It is intended for scenarios where built-in APIs are insufficient
 * and should be used cautiously to avoid data integrity or performance issues. This plugin supports
 * SQL execution, stored procedure handling, cache management, and data change notifications.</p>
 *
 * <p>Developers can use <code>executeSQL</code> to run custom SQL queries or updates, and
 * <code>executeStoredProcedure</code> to call stored procedures with input and output arguments. The
 * <code>flushAllClientsCache</code> function ensures that database changes are propagated across
 * clients, while <code>notifyDataChange</code> informs clients of record updates or deletions based
 * on primary keys. Additionally, <code>getException</code> provides detailed error information for
 * debugging failed operations.</p>
 *
 * <p>Careful usage of this plugin enables powerful database operations while maintaining system stability.</p>
 *
 * @author jblok
 */
@ServoyDocumented(publicName = RawSQLPlugin.PLUGIN_NAME, scriptingName = "plugins." + RawSQLPlugin.PLUGIN_NAME)
public class RawSQLProvider implements IScriptable
{
	private final RawSQLPlugin plugin;
	private ISQLService sqlService;
	private Exception exception;

	RawSQLProvider(RawSQLPlugin plugin)
	{
		this.plugin = plugin;
	}

	private ISQLService getSQLService() throws Exception
	{
		exception = null;
		if (sqlService == null)
		{
			sqlService = (ISQLService)plugin.getClientPluginAccess().getRemoteService("servoy.ISQLService"); //$NON-NLS-1$
		}
		return sqlService;
	}

	/**
	 * @deprecated Replaced by {@link #getException()}.
	 */
	@Deprecated
	public String js_getExceptionMsg()
	{
		return (exception == null ? null : exception.toString() + ' ' + exception.getMessage());
	}

	/**
	 * If the result from a function was false, it will return the exception object.
	 *
	 * @sampleas js_executeSQL(String,String,String)
	 */
	public Exception js_getException()
	{
		return exception;
	}

	/**
	 * Execute any SQL, returns true if successful.
	 *
	 * @sample
	 * /****************************************************************************
	 * WARNING! You can cause data loss or serious data integrity compromises!
	 * You should have a THOROUGH understanding of both SQL and your backend
	 * database (and other interfaces that may use that backend) BEFORE YOU USE
	 * ANY OF THESE COMMANDS.
	 * You should also READ THE DOCUMENTATION BEFORE USING ANY OF THESE COMMANDS
	 * ****************************************************************************&#47;
	 *
	 * var country = 'NL'
	 * var done = plugins.rawSQL.executeSQL("example_data","employees","update employees set country = ?", [country])
	 * if (done)
	 * {
	 * 	//flush is required when changes are made in db
	 * 	plugins.rawSQL.flushAllClientsCache("example_data","employees")
	 * }
	 * else
	 * {
	 * 	var msg = plugins.rawSQL.getException().getMessage(); //see exception node for more info about the exception obj
	 * 	plugins.dialogs.showErrorDialog('Error',  'SQL exception: '+msg,  'Ok')
	 * }
	 *
	 * // Note that when this function is used to create a new table in the database, this table will only be seen by
	 * // the Servoy Application Server when the table name starts with 'temp_', otherwise a server restart is needed.
	 *
	 * @param serverName the name of the server
	 * @param tableName the name of the table
	 * @param sql the sql query to execute
	 *
	 * @deprecated As of Servoy 8.3 deprecated because tableName parameter is not needed/used.
	 */
	@Deprecated
	public boolean js_executeSQL(String serverName, String tableName, String sql)
	{
		return js_executeSQL(serverName, tableName, sql, null);
	}

	/**
	 * @clonedesc js_executeSQL(String,String,String)
	 *
	 * @sampleas js_executeSQL(String,String,String)
	 *
	 * @param serverName the name of the server
	 * @param tableName the name of the table
	 * @param sql the sql query to execute
	 * @param sql_args the arguments for the query
	 *
	 * @deprecated As of Servoy 8.3 deprecated because tableName parameter is not needed/used.
	 */
	@Deprecated
	public boolean js_executeSQL(String serverName, String tableName, String sql, Object[] sql_args)
	{
		exception = null;
		try
		{
			ServerMapping serverMapping = getServerMapping(serverName);
			return getSQLService().executeSQL(plugin.getClientPluginAccess().getClientID(), serverMapping.remoteServername, sql, sql_args,
				serverMapping.transactionID);
		}
		catch (Exception ex)
		{
			exception = ex;
			Debug.error(ex);
			return false;
		}
	}

	/**
	 * Execute any SQL, returns true if successful.
	 *
	 * @sample
	 * /****************************************************************************
	 * WARNING! You can cause data loss or serious data integrity compromises!
	 * You should have a THOROUGH understanding of both SQL and your backend
	 * database (and other interfaces that may use that backend) BEFORE YOU USE
	 * ANY OF THESE COMMANDS.
	 * You should also READ THE DOCUMENTATION BEFORE USING ANY OF THESE COMMANDS
	 * ****************************************************************************&#47;
	 *
	 * var country = 'NL'
	 * var done = plugins.rawSQL.executeSQL("example_data","update employees set country = ?", [country])
	 * if (done)
	 * {
	 * 	//flush is required when changes are made in db
	 * 	plugins.rawSQL.flushAllClientsCache("example_data","employees")
	 * }
	 * else
	 * {
	 * 	var msg = plugins.rawSQL.getException().getMessage(); //see exception node for more info about the exception obj
	 * 	plugins.dialogs.showErrorDialog('Error',  'SQL exception: '+msg,  'Ok')
	 * }
	 *
	 * // Note that when this function is used to create a new table in the database, this table will only be seen by
	 * // the Servoy Application Server when the table name starts with 'temp_', otherwise a server restart is needed.
	 *
	 * @param serverName the name of the server
	 * @param sql the sql query to execute
	 */
	public boolean js_executeSQL(String serverName, String sql)
	{
		return js_executeSQL(serverName, sql, (Object[])null);
	}

	/**
	 * @clonedesc js_executeSQL(String,String,String)
	 *
	 * @sampleas js_executeSQL(String,String,String)
	 *
	 * @param serverName the name of the server
	 * @param sql the sql query to execute
	 * @param sql_args the arguments for the query
	 */
	public boolean js_executeSQL(String serverName, String sql, Object[] sql_args)
	{
		exception = null;
		try
		{
			ServerMapping serverMapping = getServerMapping(serverName);
			return getSQLService().executeSQL(plugin.getClientPluginAccess().getClientID(), serverMapping.remoteServername, sql, sql_args,
				serverMapping.transactionID);
		}
		catch (Exception ex)
		{
			exception = ex;
			Debug.error(ex);
			return false;
		}
	}

	/**
	 * Execute a stored procedure.
	 *
	 * @sample
	 * /****************************************************************************
	 * WARNING! You can cause data loss or serious data integrity compromises!
	 * You should have a THOROUGH understanding of both SQL and your backend
	 * database (and other interfaces that may use that backend) BEFORE YOU USE
	 * ANY OF THESE COMMANDS.
	 * You should also READ THE DOCUMENTATION BEFORE USING ANY OF THESE COMMANDS
	 * ****************************************************************************&#47;
	 *
	 * var maxReturnedRows = 10; //useful to limit number of rows
	 * var procedure_declaration = '{?=calculate_interest_rate(?)}'
	 * // define the direction, a 0 for input data, a 1 for output data
	 * var typesArray = [1, 0];
	 * // define the types and values, a value for input data, a sql-type for output data
	 * var args = [java.sql.Types.NUMERIC, 3000]
	 * // A dataset is returned, when no output-parameters defined, the last select-result in the procedure will be returned.
	 * // When one or more output-parameters are defined, the dataset will contain 1 row with the output data.
	 * var dataset = plugins.rawSQL.executeStoredProcedure(databaseManager.getDataSourceServerName(controller.getDataSource()), procedure_declaration, args, typesArray, maxReturnedRows);
	 * var interest_rate = dataset.getValue(1, 1);
	 *
	 * @param serverName
	 * @param procedureDeclaration
	 * @param arguments
	 * @param inOutDirectionality
	 * @param maxNumberOfRowsToRetrieve
	 *
	 * @return a dataset with output (in case of output data) or the last result set executed by the procedure.
	 */
	public JSDataSet js_executeStoredProcedure(String serverName, String procedureDeclaration, Object[] arguments, int[] inOutDirectionality,
		int maxNumberOfRowsToRetrieve)
	{
		if (arguments != null && inOutDirectionality != null && arguments.length != inOutDirectionality.length)
		{
			throw new RuntimeException("In/Out Arguments should be same size as directionality array"); //$NON-NLS-1$
		}

		exception = null;
		try
		{
			ServerMapping serverMapping = getServerMapping(serverName);

			// TODO HOW TO HANDLE ARGS WITH NULL?? sHOULD BE CONVERTED TO NullValue?????
			IDataSet set = getSQLService().executeStoredProcedure(plugin.getClientPluginAccess().getClientID(), serverMapping.remoteServername,
				serverMapping.transactionID, procedureDeclaration, arguments, inOutDirectionality, 0, maxNumberOfRowsToRetrieve);
			return convert(set)[0];
		}
		catch (ServoyException ex)
		{
			exception = ex;
			Debug.error(ex);
			return new JSDataSet(ex);
		}
		catch (Exception e)
		{
			exception = e;
			Debug.error(e);
			return null;
		}
	}

	/**
	 * Execute a stored procedure, return all created result sets.
	 *
	 * @sample
	 * /****************************************************************************
	 * WARNING! You can cause data loss or serious data integrity compromises!
	 * You should have a THOROUGH understanding of both SQL and your backend
	 * database (and other interfaces that may use that backend) BEFORE YOU USE
	 * ANY OF THESE COMMANDS.
	 * You should also READ THE DOCUMENTATION BEFORE USING ANY OF THESE COMMANDS
	 * ****************************************************************************&#47;
	 *
	 * var maxReturnedRows = 10; //useful to limit number of rows
	 * var procedure_declaration = '{ get_unpaid_orders_and_their_customers(?) }'
	 * var args = [42]
	 * var datasets = plugins.rawSQL.executeStoredProcedure(databaseManager.getDataSourceServerName(controller.getDataSource()), procedure_declaration, args, maxReturnedRows);
	 * for (var i = 0; i < datasets.length; i++) {
	 * 	var ds = datasets[i]
	 * 	// process dataset
	 * }
	 *
	 * @param serverName
	 * @param procedureDeclaration
	 * @param arguments
	 * @param maxNumberOfRowsToRetrieve
	 *
	 * @return the result sets created by the procedure.
	 */
	public JSDataSet[] js_executeStoredProcedure(String serverName, String procedureDeclaration, Object[] arguments, int maxNumberOfRowsToRetrieve)
		throws Exception
	{
		exception = null;
		try
		{
			ServerMapping serverMapping = getServerMapping(serverName);

			// TODO HOW TO HANDLE ARGS WITH NULL?? sHOULD BE CONVERTED TO NullValue?????
			IDataSet[] dataSets = getSQLService().executeStoredProcedure(plugin.getClientPluginAccess().getClientID(), serverMapping.remoteServername,
				serverMapping.transactionID, procedureDeclaration, arguments, 0, maxNumberOfRowsToRetrieve);
			return convert(dataSets);
		}
		catch (ServoyException ex)
		{
			exception = ex;
			Debug.error(ex);
			return new JSDataSet[] { new JSDataSet(ex) };
		}
		catch (Exception e)
		{
			exception = e;
			Debug.error(e);
			return null;
		}
	}

	private JSDataSet[] convert(IDataSet... dataSets)
	{
		JSDataSet[] jsDatasets = new JSDataSet[dataSets.length];
		for (int i = 0; i < dataSets.length; i++)
		{
			jsDatasets[i] = new JSDataSet(((ClientPluginAccessProvider)plugin.getClientPluginAccess()).getApplication(), dataSets[i]);
		}
		return jsDatasets;
	}

	/**
	 * @deprecated Replaced by {@link application#getUUID(String)}
	 *
	 * @sample
	 * /****************************************************************************
	 * WARNING! You can cause data loss or serious data integrity compromises!
	 * You should have a THOROUGH understanding of both SQL and your backend
	 * database (and other interfaces that may use that backend) BEFORE YOU USE
	 * ANY OF THESE COMMANDS.
	 * You should also READ THE DOCUMENTATION BEFORE USING ANY OF THESE COMMANDS
	 * ****************************************************************************&#47;
	 *
	 * var uuid = application.getNewUUID();
	 * plugins.rawSQL.executeSQL(databaseManager.getDataSourceServerName(controller.getDataSource()), 'employees', 'insert into employees (employees_id, creation_date) values (?, ?)', [plugins.rawSQL.convertUUIDToBytes(uuid), new Date()]);
	 */
	@Deprecated
	public byte[] js_convertUUIDToBytes(String uuid)
	{
		return UUID.fromString(uuid).toBytes();
	}

	/**
	 * @deprecated Replaced by {@link application#getUUID(byte[])}
	 *
	 * @param data
	 * @return
	 */
	@Deprecated
	public String js_convertUUIDToString(byte[] data)
	{
		return new UUID(data).toString();
	}

	/**
	 * Flush cached database data. Use with extreme care, its affecting the performance of clients! This method will take into account tenants, just like databroadcast from user interface.
	 *
	 * @sampleas js_executeSQL(String,String,String)
	 *
	 * @param serverName
	 * @param tableName
	 */
	public boolean js_flushAllClientsCache(String serverName, String tableName)
	{
		try
		{
			ServerMapping serverMapping = getServerMapping(serverName);
			if (serverMapping.transactionID != null)
			{
				// flush data locally within the transaction
				plugin.getClientPluginAccess().getDatabaseManager().notifyDataChange(
					DataSourceUtils.createDBTableDataSource(serverMapping.localServername, tableName), null, 0);
			}
			return getSQLService().flushAllClientsCache(plugin.getClientPluginAccess().getClientID(), true, serverMapping.remoteServername, tableName,
				serverMapping.transactionID);
		}
		catch (Exception ex)
		{
			exception = ex;
			Debug.error(ex);
			return false;
		}
	}

	/**
	 * Notify clients about changes in records, based on pk(s). Use with extreme care, its affecting the performance of clients! This method will take into account tenants, just like databroadcast from user interface.
	 *
	 * @sample
	 * /****************************************************************************
	 * WARNING! You can cause data loss or serious data integrity compromises!
	 * You should have a THOROUGH understanding of both SQL and your backend
	 * database (and other interfaces that may use that backend) BEFORE YOU USE
	 * ANY OF THESE COMMANDS.
	 * You should also READ THE DOCUMENTATION BEFORE USING ANY OF THESE COMMANDS
	 * ****************************************************************************&#47;
	 *
	 * var action = SQL_ACTION_TYPES.DELETE_ACTION //pks deleted
	 * //var action = SQL_ACTION_TYPES.INSERT_ACTION //pks inserted
	 * //var action = SQL_ACTION_TYPES.UPDATE_ACTION //pks updates
	 * var pksdataset = databaseManager.convertToDataSet(new Array(12,15,16,21))
	 * var ok = plugins.rawSQL.notifyDataChange(databaseManager.getDataSourceServerName(controller.getDataSource()), 'employees', pksdataset,action)
	 *
	 * @param serverName
	 * @param tableName
	 * @param pksDataset
	 * @param action
	 */
	public boolean js_notifyDataChange(String serverName, String tableName, IDataSet pksDataset, int action)
	{
		if (pksDataset == null || pksDataset.getRowCount() == 0) return false; //make sure developer does not call this without knowing this would be the same as flushAllClientsCache function

		try
		{
			ServerMapping serverMapping = getServerMapping(serverName);
			// flush data locally
			plugin.getClientPluginAccess().getDatabaseManager().notifyDataChange(
				DataSourceUtils.createDBTableDataSource(serverMapping.localServername, tableName), pksDataset, action);
			// notifySelf = false so that the client id is sent as well (needed for tenant id)
			return getSQLService().notifyDataChange(plugin.getClientPluginAccess().getClientID(), false, serverMapping.remoteServername, tableName, pksDataset,
				action, serverMapping.transactionID);
		}
		catch (Exception ex)
		{
			exception = ex;
			Debug.error(ex);
			return false;
		}
	}

	private ServerMapping getServerMapping(String serverName) throws ServoyException
	{
		// This will always return a collection of at least 1 server name.
		Collection<String> originalServerNames = plugin.getClientPluginAccess().getDatabaseManager().getOriginalServerNames(serverName);

		String remoteServername;
		String localServername;
		if (originalServerNames.contains(serverName))
		{
			// not switched or the 'logical' server name was passed (new behaviour)
			remoteServername = plugin.getClientPluginAccess().getDatabaseManager().getSwitchedToServerName(serverName);
			localServername = serverName;
		}
		else
		{
			// the switched-to server was used (old behaviour)
			remoteServername = serverName;
			// pick one of the original servers, unless we have a tx it does not matter which to use.
			localServername = originalServerNames.iterator().next();
			Debug.warn("Deprecated rawSQL call, use logical server name (" + localServername + ") in stead of real server name (" + remoteServername + ")");
		}

		String tid = plugin.getClientPluginAccess().getDatabaseManager().getTransactionID(localServername);
		if (tid != null && !originalServerNames.contains(serverName) && originalServerNames.size() > 1)
		{
			// We have a transaction but don't know for which local server
			throw new IllegalArgumentException("Cannot determine transaction server for rawsql processing, use one of " + originalServerNames);
		}

		return new ServerMapping(localServername, remoteServername, tid);
	}

	private static class ServerMapping
	{
		public final String localServername;
		public final String remoteServername;
		public final String transactionID;

		public ServerMapping(String localServername, String remoteServername, String transactionID)
		{
			this.localServername = localServername;
			this.remoteServername = remoteServername;
			this.transactionID = transactionID;
		}
	}
}
