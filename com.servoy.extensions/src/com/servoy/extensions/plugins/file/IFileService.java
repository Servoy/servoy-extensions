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
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The contract of the File service, as seen from the client.<br/>
 * Defines methods to allow streaming bytes[] by chunk using an {@link ITransferObject}
 *
 * @author Servoy Stuff
 * @since Servoy 5.2
 */
@SuppressWarnings("nls")
public interface IFileService extends Remote
{
	@Deprecated
	public static final String SERVICE_NAME = IFileService.class.getName();

	/**
	 * The default folder server property key
	 */
	public static final String DEFAULT_FOLDER_PROPERTY = "servoy.FileServerService.defaultFolder";


	/**
	 * Returns the defaultFolder location as a String (canonical representation of the folder)
	 * @return the defaultFolder
	 */
	public File getDefaultFolder(final String clientId) throws RemoteException;

}
