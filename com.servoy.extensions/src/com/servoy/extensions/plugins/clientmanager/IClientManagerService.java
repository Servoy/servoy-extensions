package com.servoy.extensions.plugins.clientmanager;

import java.rmi.Remote;
import java.rmi.RemoteException;

import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.server.shared.IClientInformation;

public interface IClientManagerService extends Remote
{
	IClientInformation[] getConnectedClients() throws RemoteException;

	void sendMessageToAllClients(String message) throws RemoteException;

	void sendMessageToClient(String clientId, String message) throws RemoteException;

	void shutDownAllClients(String skipClientId) throws RemoteException;

	void shutDownClient(String clientId, boolean forceUnregister) throws RemoteException;

	void deregisterChannelListener(BroadcastInfo info) throws RemoteException;

	void broadcastMessage(BroadcastInfo info, String message) throws RemoteException;

	void registerChannelListener(BroadcastInfo info) throws RemoteException;

	boolean isInMaintenanceMode() throws RemoteException;

	JSDataSet getLocks() throws RemoteException;

}
