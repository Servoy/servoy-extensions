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

package com.servoy.extensions.plugins.file;

import java.io.File;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.util.Debug;

/**
 * The server plugin, also {@link IFileService} implementation
 *
 * @author Servoy Stuff
 * @since Servoy 5.2
 */
public class FileServerPlugin implements IServerPlugin, IFileService
{
	/**
	 * The default location where files will be saved (or related to it)
	 */
	private volatile File defaultFolder;

	private IServerAccess application;


	@SuppressWarnings("nls")
	public Map<String, String> getRequiredPropertyNames()
	{
		final Map<String, String> req = new HashMap<String, String>();
		req.put(IFileService.DEFAULT_FOLDER_PROPERTY,
			"Set the default folder path (absolute path on the server) to save files sent by clients (will default to user.home/.servoy/uploads/UUID/)");
		return req;
	}

	/**
	 * Reads the default folder property and register the {@link IFileService}
	 */
	public void initialize(IServerAccess app) throws PluginException
	{
		this.application = app;
		setDefaultFolder(app.getSettings().getProperty(IFileService.DEFAULT_FOLDER_PROPERTY));
		try
		{
			app.registerRemoteService(IFileService.class.getName(), this);
		}
		catch (RemoteException ex)
		{
			throw new PluginException(ex);
		}
		app.registerWebService("file", new FileServlet(this, app));
	}

	/**
	 * Initializes the default folder (where the files will be saved)<br/>
	 * First tries to use the folder received in parameter (if not null)<br/>
	 * Then tries to find the Tomcat ROOT context and an /uploads/ directory (create it if needed)<br/>
	 * If all fails will use the home Directory<br/>
	 * Finally, logs the default folder location
	 */
	@SuppressWarnings("nls")
	private void setDefaultFolder(final String folder)
	{
		try
		{
			if (folder != null)
			{
				// try to use the supplied path:
				defaultFolder = new File(folder.trim());
				if (!defaultFolder.exists())
				{
					if (!defaultFolder.mkdirs())
					{
						throw new RuntimeException("Cant set the default folder for the File plugin to '" + folder + "' can't create the directory");
					}
				}
			}
			else
			{
				if (System.getProperty("servoy.application_server.dir") != null)
				{
					defaultFolder = new File(System.getProperty("servoy.application_server.dir"), "uploads");
				}
				else
				{
					defaultFolder = new File(
						System.getProperty("user.home") + File.separator + ".servoy" + File.separator + "uploads" + File.separator + UUID.randomUUID());
				}
				if (!defaultFolder.exists())
				{
					if (!defaultFolder.mkdirs())
					{
						throw new RuntimeException(
							"Cant set the default folder for the File plugin to '" + defaultFolder.getCanonicalPath() + "' can't create the directory");
					}
				}

				application.getSettings().setProperty(IFileService.DEFAULT_FOLDER_PROPERTY, defaultFolder.getCanonicalPath());
				// TODO this should really be saved once.
			}
			// if we made it so far and still haven't found a default folder, then we have a problem!
			if (defaultFolder == null)
			{
				throw new RuntimeException("Default folder couldnt be resolved");
			}

			// ensures that we have a canonical representation of the default folder (to help security checks):
			defaultFolder = defaultFolder.getCanonicalFile();

			Debug.log("Default upload folder location was set to " + defaultFolder.getCanonicalPath());
		}
		catch (final Exception ex)
		{
			defaultFolder = null;
			Debug.error("File plugin error trying to setup the default upload folder", ex);
		}
	}

	@SuppressWarnings("nls")
	public Properties getProperties()
	{
		final Properties props = new Properties();
		props.put(DISPLAY_NAME, "File Plugin");
		return props;
	}

	public void load() throws PluginException
	{
		// ignore
	}

	/**
	 * Takes care of releasing resources, especially in case transfers are still on-going,<br/>
	 * by first closing any opened OutputStream with the help of the ITransferObject<br/>
	 * then deleting the reference to the {@link ConcurrentHashMap}
	 */
	public void unload() throws PluginException
	{
		defaultFolder = null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.plugins.file.IFileService#getDefaultFolderLocation()
	 */
	public File getDefaultFolder(final String clientId) throws RemoteException
	{
		return defaultFolder;

	}
}
