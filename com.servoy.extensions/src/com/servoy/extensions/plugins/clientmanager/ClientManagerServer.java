package com.servoy.extensions.plugins.clientmanager;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.servoy.j2db.Messages;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.server.shared.IClientInformation;

public class ClientManagerServer implements IServerPlugin, IClientManagerService
{
	private IServerAccess application;
	private final ConcurrentHashMap<String, List<BroadcastInfo>> registeredClients = new ConcurrentHashMap<>();


	private static IClientManagerService INSTANCE;

	public static IClientManagerService getInstance()
	{
		return INSTANCE;
	}

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
		INSTANCE = this;
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
	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, Messages.getString("servoy.plugin.maintenanceserver.displayname")); //$NON-NLS-1$
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
		return application.getConnectedClients();
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
	public void shutDownClient(String clientId)
	{
		application.shutDownClient(clientId);
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
		// TODO for smart client this is really slow, because getName() call goes to the smart client first all the time.
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
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

}
