/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2026 Servoy BV

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

import java.rmi.Remote;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.persistence.IServer;
import com.servoy.j2db.plugins.IDataNotifyService;
import com.servoy.j2db.plugins.IPluginManager;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.server.shared.IClientInformation;
import com.servoy.j2db.util.ITaskExecuter;

import jakarta.servlet.http.HttpServlet;

/**
 * Minimal hand-written {@link IServerAccess} the databroadcaster tests feed to
 * {@code getEffectiveMaxMessageSize}. It answers only {@code getSettings()} (so
 * the {@code amqpbroadcaster.maxmessagesize} setting can be controlled) and
 * {@code getDataNotifyService()}; everything else throws so the plugin cannot
 * silently depend on more of the server than the guard needs.
 *
 * @author jcompagner
 */
@SuppressWarnings({ "nls", "deprecation" })
public class FakeServerAccess implements IServerAccess {
	private final Properties settings;

	FakeServerAccess(Properties settings) {
		this.settings = settings;
	}

	private static UnsupportedOperationException notNeeded() {
		return new UnsupportedOperationException("not needed for the databroadcaster tests");
	}

	@Override
	public Properties getSettings() {
		return settings;
	}

	@Override
	public IDataNotifyService getDataNotifyService() {
		return null;
	}

	@Override
	public String getVersion() {
		throw notNeeded();
	}

	@Override
	public int getReleaseNumber() {
		throw notNeeded();
	}

	@Override
	public ScheduledExecutorService getExecutor() {
		throw notNeeded();
	}

	@Override
	public IPluginManager getPluginManager() {
		throw notNeeded();
	}

	@Override
	public int getPlatform() {
		throw notNeeded();
	}

	@Override
	public ITaskExecuter getThreadPool() {
		throw notNeeded();
	}

	@Override
	public void reportError(String msg, Object detail) {
		throw notNeeded();
	}

	@Override
	public void registerWebService(String webServiceAlias, HttpServlet service_servlet) {
		throw notNeeded();
	}

	@Override
	public void registerRMIService(String interfaceClassName, Remote obj) {
		throw notNeeded();
	}

	@Override
	public void registerRemoteService(String interfaceClassName, Remote obj) {
		throw notNeeded();
	}

	@Override
	public Remote getRemoteService(String rmiLookupName) {
		throw notNeeded();
	}

	@Override
	public String getServerLocalClientID() {
		throw notNeeded();
	}

	@Override
	public Connection getDBServerConnection(String server_name, String transactionId) {
		throw notNeeded();
	}

	@Override
	public Connection getDBServerConnection(String server_name, String transactionId, String clientId) {
		throw notNeeded();
	}

	@Override
	public Connection getDBServerConnection(String server_name) {
		throw notNeeded();
	}

	@Override
	public Object getNextSequence(String server_name, String table_name, String column_name) {
		throw notNeeded();
	}

	@Override
	public void flushAllClientsCache(String server_name, String table_name) {
		throw notNeeded();
	}

	@Override
	public boolean flushAllClientsCache(String server_name, String table_name, String transactionId) {
		throw notNeeded();
	}

	@Override
	public boolean notifyDataChange(String server_name, String table_name, IDataSet pks, int action,
			String transactionId) {
		throw notNeeded();
	}

	@Override
	public void endPerformanceTiming(String context, Integer id, String clientId) {
		throw notNeeded();
	}

	@Override
	public Long addPerformanceTiming(String context, String action, long time_ms, String clientId) {
		throw notNeeded();
	}

	@Override
	public void endPerformanceTiming(String context, Long id, String clientId) {
		throw notNeeded();
	}

	@Override
	public void addPerformanceTiming(String context, String action, long time_ms) {
		throw notNeeded();
	}

	@Override
	public void addPerformanceTiming(String action, long time_ms) {
		throw notNeeded();
	}

	@Override
	public IClientInformation[] getConnectedClients() {
		throw notNeeded();
	}

	@Override
	public void sendMessageToAllClients(String message) {
		throw notNeeded();
	}

	@Override
	public void sendMessageToClient(String clientId, String message) {
		throw notNeeded();
	}

	@Override
	public void shutDownAllClients(String skipClientId) {
		throw notNeeded();
	}

	@Override
	public void shutDownAllClients() {
		throw notNeeded();
	}

	@Override
	public void shutDownClient(String clientId) {
		throw notNeeded();
	}

	@Override
	public void shutDownClient(String clientId, boolean forceUnregister) {
		throw notNeeded();
	}

	@Override
	public boolean isInMaintenanceMode() {
		throw notNeeded();
	}

	@Override
	public void setInMaintenanceMode(boolean inMaintenanceMode) {
		throw notNeeded();
	}

	@Override
	public IServer getDBServer(String serverName, boolean mustBeEnabled, boolean mustBeValid) {
		throw notNeeded();
	}

	@Override
	public String[] getDBServerNames(boolean mustBeEnabled, boolean mustBeValid, boolean sort,
			boolean includeDuplicates) {
		throw notNeeded();
	}

	@Override
	public boolean isServerProcess(String clientId) {
		throw notNeeded();
	}

	@Override
	public boolean isAuthenticated(String clientId) {
		throw notNeeded();
	}

	@Override
	public String checkPasswordForUserName(String user, String password) {
		throw notNeeded();
	}

	@Override
	public String[] getUserGroups(String userUid) {
		throw notNeeded();
	}

	@Override
	public java.net.URL getResource(String filename) {
		throw notNeeded();
	}

	@Override
	public java.io.InputStream getResourceAsStream(String filename) {
		throw notNeeded();
	}

	@Override
	public IDataSet executeStoredProcedure(String clientId, String serverName, String transactionId,
			String procedureDeclaration, Object[] questiondata, int[] inOutType, int startRow,
			int maxNumberOfRowsToRetrieve) {
		throw notNeeded();
	}

	@Override
	public IDataSet[] executeStoredProcedure(String clientId, String serverName, String transactionId,
			String procedureDeclaration, Object[] questiondata, int startRow, int maxNumberOfRowsToRetrieve) {
		throw notNeeded();
	}

	@Override
	public Object[] insertDataSet(String clientId, String serverName, String tableName, String transactionId,
			IDataSet dataSet) {
		throw notNeeded();
	}

	@Override
	public void deleteFromDataSet(String clientId, String serverName, String tableName, String transactionId,
			IDataSet dataSet) {
		throw notNeeded();
	}

	@Override
	public JSDataSet getLocks() {
		throw notNeeded();
	}

	@Override
	public <T> T getPluginInstance(Class<T> pluginClass) {
		throw notNeeded();
	}

	@Override
	public void releaseLocks(String clientId) {
		throw notNeeded();
	}

	@Override
	public long addTrackStatement(String clientId, String server, String sql, long t1, Statement ps) {
		throw notNeeded();
	}

	@Override
	public void removeTrackStatement(long statementTrackerId) {
		throw notNeeded();
	}
}
