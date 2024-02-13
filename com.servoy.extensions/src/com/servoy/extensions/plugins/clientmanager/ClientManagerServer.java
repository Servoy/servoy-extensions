package com.servoy.extensions.plugins.clientmanager;

import java.rmi.RemoteException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.plugins.IPostInitializeListener;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.server.shared.IClientInformation;
import com.servoy.j2db.util.Debug;

public class ClientManagerServer implements IServerPlugin, IClientManagerService, IPostInitializeListener
{
	private IServerAccess application;
	private final ConcurrentHashMap<String, List<BroadcastInfo>> registeredClients = new ConcurrentHashMap<>();
	private ClientManagerServerToBroadcasterBridge broadcasterBridge;

	public ClientManagerServer()
	{
	}

	@Override
	public Map<String, String> getRequiredPropertyNames()
	{
		return null;
	}

	@Override
	public void initialize(IServerAccess app) throws PluginException
	{
		application = app;
		try
		{
			app.registerRemoteService(IClientManagerService.class.getName(), this);
		}
		catch (RemoteException ex)
		{
			throw new PluginException(ex);
		}
	}

	@Override
	public void afterInit()
	{
		if (isBroadcasterPluginAvailable())
		{
			this.broadcasterBridge = new ClientManagerServerToBroadcasterBridge(application, this);
			this.broadcasterBridge.afterInit();
		}
	}

	@Override
	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "Client Manager Service"); //$NON-NLS-1$
		return props;
	}

	@Override
	public void load() throws PluginException
	{
	}

	@Override
	public void unload() throws PluginException
	{
	}

	@Override
	public boolean isInMaintenanceMode() throws RemoteException
	{
		return application.isInMaintenanceMode();
	}

	@Override
	public IClientInformation[] getConnectedClients()
	{
		IClientInformation[] connectedClients = application.getConnectedClients();
		IClientInformation[] copy = new IClientInformation[connectedClients.length];
		for (int i = 0; i < connectedClients.length; i++)
		{
			copy[i] = new ClientInfoCopy(connectedClients[i]);
		}
		return copy;
	}

	@Override
	public void sendMessageToAllClients(String message)
	{
		application.sendMessageToAllClients(message);
	}

	@Override
	public void sendMessageToClient(String clientId, String message)
	{
		application.sendMessageToClient(clientId, message);
	}

	@Override
	public void shutDownAllClients(String skipClientId)
	{
		application.shutDownAllClients(skipClientId);
	}

	@Override
	public void shutDownClient(String clientId, boolean forceUnregister)
	{
		application.shutDownClient(clientId, forceUnregister);
	}

	@Override
	public void releaseLocks(String clientId)
	{
		application.releaseLocks(clientId);
	}

	@Override
	public void registerChannelListener(BroadcastInfo info) throws RemoteException
	{
		String channel = info.getChannelName();
		List<BroadcastInfo> list = registeredClients.get(channel);
		if (list == null)
		{
			list = new CopyOnWriteArrayList<>();
			List<BroadcastInfo> prev = registeredClients.putIfAbsent(channel, list);
			if (prev != null) list = prev;
		}
		list.add(info);
	}

	@Override
	public void deregisterChannelListener(BroadcastInfo info) throws RemoteException
	{
		String channel = info.getChannelName();
		List<BroadcastInfo> list = registeredClients.get(channel);
		if (list != null)
		{
			list.remove(info);
			if (list.size() == 0)
			{
				registeredClients.remove(channel, list);
				if (list.size() > 0)
				{
					registeredClients.putIfAbsent(channel, list);
				}
			}
		}
	}

	@Override
	public void broadcastMessage(BroadcastInfo info, String message)
	{
		this.broadcastMessageInternal(info, message);
		if (this.broadcasterBridge != null)
		{
			this.broadcasterBridge.broadcastMessageToAllServers(info, message);
		}
	}

	void broadcastMessageInternal(BroadcastInfo info, String message)
	{
		List<BroadcastInfo> list = registeredClients.get(info.getChannelName());
		if (list != null)
		{
			for (BroadcastInfo bci : list)
			{
				if (!bci.equals(info)) try
				{
					bci.getBroadCaster().channelMessage(info.getName(), message);
				}
				catch (RemoteException e)
				{
					Debug.error(e);
				}
			}
		}
	}

	@Override
	public JSDataSet getLocks() throws RemoteException
	{
		return application.getLocks();
	}

	private boolean isBroadcasterPluginAvailable()
	{
		try
		{
			Class.forName("com.servoy.extensions.plugins.broadcaster.DataNotifyBroadCaster", false, getClass().getClassLoader()); //$NON-NLS-1$
			return true;
		}
		catch (Exception e)
		{
			// ignore
		}
		return false;
	}

	@Override
	public Date getServerBuildDate()
	{
		return application.getServerBuildDate();
	}
}
