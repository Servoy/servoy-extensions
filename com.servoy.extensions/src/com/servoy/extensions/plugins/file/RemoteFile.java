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

import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.UUID;

import com.servoy.j2db.util.Debug;

/**
 * Implementation of an {@link IAbstractFile} for remote (server-side) files<br/>
 *
 * @author jcompagner
 * @author Servoy Stuff
 * @since Servoy 5.2
 */
public class RemoteFile extends AbstractFile
{

	private final IFileService service;
	private final String clientId;

	private volatile RemoteFileData data;
	private volatile String contentType;

	public RemoteFile(RemoteFileData data, IFileService service, String clientId)
	{
		this.data = data;
		this.service = service;
		this.clientId = clientId;
	}

	public String getName()
	{
		return data.getName();
	}

	@Override
	public long size()
	{
		return data.size();
	}

	@Override
	public long lastModified()
	{
		return data.lastModified();
	}

	@Override
	public boolean isDirectory()
	{
		return data.isDirectory();
	}

	@Override
	public boolean isFile()
	{
		return data.isFile();
	}

	@Override
	public boolean canRead()
	{
		return data.canRead();
	}

	@Override
	public boolean canWrite()
	{
		return data.canWrite();
	}

	@Override
	public boolean isHidden()
	{
		return data.isHidden();
	}

	/**
	 * @throws UnsupportedMethodException
	 */
	@Override
	public boolean createNewFile() throws IOException
	{
		throw new UnsupportedMethodException(
			"Creating a new remote file is not allowed, you can only upload to a specific location using the plugins.file.streamToServer() method"); //$NON-NLS-1$
	}

	/**
	 * @throws UnsupportedMethodException
	 */
	@Override
	public boolean mkdir()
	{
		throw new UnsupportedMethodException(
			"Create a remote folder is not allowed, but you can upload a file to a specific location including new folder path using the plugins.file.streamToServer() method"); //$NON-NLS-1$
	}

	/**
	 * @throws UnsupportedMethodException
	 */
	@Override
	public boolean mkdirs()
	{
		throw new UnsupportedMethodException(
			"Create remote folders is not allowed, but you can upload a file to a specific location including new folders path using the plugins.file.streamToServer() method"); //$NON-NLS-1$
	}

	/**
	 * @throws UnsupportedMethodException
	 */
	@Override
	public boolean setLastModified(long time)
	{
		throw new UnsupportedMethodException("Setting the lastModified time on a remote file is not allowed"); //$NON-NLS-1$
	}

	/**
	 * @throws UnsupportedMethodException
	 */
	@Override
	public boolean setReadOnly()
	{
		throw new UnsupportedMethodException("Setting the readOnly flag on a remote file is not allowed"); //$NON-NLS-1$
	}

	@Override
	public boolean delete()
	{
		try
		{
			return service.delete(clientId, getAbsolutePath());
		}
		catch (IOException ex)
		{
			return false;
		}
	}

	@SuppressWarnings("nls")
	public boolean renameTo(String upload)
	{
		if (upload == null || !upload.startsWith("/"))
		{
			throw new IllegalArgumentException("The renameTo() parameter must be an absolute server path (starting with '/')");
		}
		try
		{
			RemoteFileData renamedData = service.renameTo(clientId, getAbsolutePath(), upload);
			if (renamedData != null)
			{
				this.data = renamedData;
				return true;
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException("Error renaming remote file " + data.getAbsolutePath() + " to " + upload, e);
		}
		return false;
	}

	@Override
	public boolean renameTo(IAbstractFile upload)
	{
		if (upload instanceof RemoteFile)
		{
			return renameTo(upload.getAbsolutePath());
		}
		throw new UnsupportedMethodException("You can only rename to a remote file or a remote String path"); //$NON-NLS-1$
	}

	@Override
	public String getParent()
	{
		RemoteFileData parentData = data.getParent();
		return (parentData == null) ? null : parentData.getAbsolutePath();
	}

	@Override
	public IAbstractFile getParentFile()
	{
		RemoteFileData parentData = data.getParent();
		return (parentData == null) ? null : new RemoteFile(parentData, service, clientId);
	}

	@Override
	public String getPath()
	{
		return data.getAbsolutePath();
	}

	@Override
	public boolean isAbsolute()
	{
		return true;
	}

	@Override
	public String getAbsolutePath()
	{
		return data.getAbsolutePath();
	}

	@Override
	public IAbstractFile getAbsoluteFile()
	{
		return this;
	}

	@SuppressWarnings("nls")
	public byte[] getBytes()
	{
		UUID uuid = null;
		try
		{
			uuid = service.openTransfer(clientId, data.getAbsolutePath());
			return service.readBytes(uuid, size());
		}
		catch (Exception e)
		{
			throw new RuntimeException("Error reading remote file " + data.getAbsolutePath(), e);
		}
		finally
		{
			if (uuid != null)
			{
				try
				{
					service.closeTransfer(uuid);
				}
				catch (RemoteException e)
				{
					throw new RuntimeException("Error closing remote file: " + data.getAbsolutePath(), e);
				}
			}
		}
	}

	@Override
	@SuppressWarnings("nls")
	public String getContentType()
	{
		if (contentType == null)
		{
			try
			{
				contentType = service.getContentType(clientId, data.getAbsolutePath());
			}
			catch (Exception e)
			{
				throw new RuntimeException("Error reading remote file " + data.getAbsolutePath(), e);
			}
		}
		return contentType;
	}

	@SuppressWarnings("nls")
	@Override
	public IAbstractFile[] listFiles()
	{
		if (data.isDirectory())
		{
			try
			{
				RemoteFileData[] remoteList = service.getRemoteFolderContent(clientId, data.getAbsolutePath(), null, AbstractFile.ALL, AbstractFile.ALL,
					AbstractFile.ALL);
				RemoteFile[] files = new RemoteFile[remoteList.length];
				for (int i = 0; i < files.length; i++)
				{
					files[i] = new RemoteFile(remoteList[i], service, clientId);
				}
				return files;
			}
			catch (Exception e)
			{
				throw new RuntimeException("Error listing remote dir: " + data.getAbsolutePath(), e);
			}
		}
		return null;
	}

	@SuppressWarnings("nls")
	@Override
	public String[] list()
	{
		if (data.isDirectory())
		{
			try
			{
				RemoteFileData[] remoteList = service.getRemoteFolderContent(clientId, data.getAbsolutePath(), null, AbstractFile.ALL, AbstractFile.ALL,
					AbstractFile.ALL);
				String[] files = new String[remoteList.length];
				for (int i = 0; i < files.length; i++)
				{
					files[i] = remoteList[i].toString();
				}
				return files;
			}
			catch (Exception e)
			{
				throw new RuntimeException("Error listing remote dir: " + data.getAbsolutePath(), e);
			}
		}
		return null;
	}

	@Override
	public boolean equals(Object obj)
	{
		return data.equals(obj);
	}

	@Override
	public int hashCode()
	{
		return data.hashCode();
	}

	@Override
	public String toString()
	{
		return data.toString();
	}

	@Override
	public boolean exists()
	{
		return data.exists();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.plugins.file.IAbstractFile#setBytes(byte[], boolean)
	 */
	public boolean setBytes(byte[] bytes, boolean createFile)
	{
		if (bytes != null && (exists() || createFile))
		{
			UUID uuid = null;
			try
			{
				uuid = service.openTransfer(clientId, data.getAbsolutePath());
				int start = 0;
				while (start < bytes.length)
				{
					int length = Math.min(bytes.length - start, FileProvider.CHUNK_BUFFER_SIZE);
					service.writeBytes(uuid, bytes, start, length);
					start += length;
				}
				return true;
			}
			catch (Exception ex)
			{
				Debug.error("Error transferring data using setBytes on remote JSFile " + getAbsolutePath(), ex); //$NON-NLS-1$
			}
			finally
			{
				try
				{
					if (uuid != null) data = (RemoteFileData)service.closeTransfer(uuid);
				}
				catch (RemoteException ex)
				{

				}
			}
		}
		return false;
	}

	@Override
	public InputStream getInputStream() throws IOException
	{
		return new RemoteFileInputStream(service, clientId, data);
	}


}
