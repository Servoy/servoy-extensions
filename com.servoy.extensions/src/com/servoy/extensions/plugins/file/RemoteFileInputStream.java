/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2020 Servoy BV

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

import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.UUID;

/**
 * @author jcompanger
 * @since 2020.12
 *
 */
public class RemoteFileInputStream extends InputStream
{
	private byte[] array;
	private int counter;
	private final IFileService service;
	private final RemoteFileData data;
	private UUID uuid;

	/**
	 * @param service
	 * @param clientId
	 * @param data
	 */
	public RemoteFileInputStream(IFileService service, String clientId, RemoteFileData data) throws IOException
	{
		this.service = service;
		this.data = data;

		try
		{
			uuid = service.openTransfer(clientId, data.getAbsolutePath());
			array = service.readBytes(uuid, 2048);
			counter = 0;
//			total = array.length;
		}
		catch (Exception e)
		{
			if (uuid != null)
			{
				try
				{
					service.closeTransfer(uuid);
				}
				catch (RemoteException re)
				{
				}
			}
			throw new IOException("Error reading remote file " + data.getAbsolutePath(), e); //$NON-NLS-1$
		}
	}

	@Override
	public int read() throws IOException
	{
		if (array == null || array.length == 0) return -1;
		if (counter == array.length)
		{
			array = service.readBytes(uuid, 2048);
			if (array == null) return -1;
			counter = 0;
			return read();
		}
		return array[counter++] & 0xFF;
	}

	@Override
	public void close() throws IOException
	{
		super.close();
		try
		{
			service.closeTransfer(uuid);
		}
		catch (RemoteException re)
		{
			throw new IOException("error closing remote file " + data.getAbsolutePath(), re); //$NON-NLS-1$
		}
	}

}
