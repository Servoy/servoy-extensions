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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.servoy.j2db.plugins.IAllWebClientPluginAccess;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.FileChooserUtils;

/**
 * Implementation of an {@link IAbstractFile} for local (client-side) files
 *
 * @author jcompagner
 * @author Servoy Stuff
 * @since Servoy 5.2
 */
public class LocalFile implements IAbstractFile
{
	private final File file;
	private final IClientPluginAccess application;

	public LocalFile(File file, IClientPluginAccess application)
	{
		this.file = file;
		this.application = application;
	}

	public File getFile()
	{
		return file;
	}

	public byte[] getBytes()
	{
		try
		{
			if (file.exists() && !file.isDirectory()) return FileChooserUtils.readFile(file);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Error getting the bytes of file " + file, e); //$NON-NLS-1$
		}
		return null;
	}

	public String getName()
	{
		return file.getName();
	}

	public String getContentType()
	{
		return AbstractFile.getContentType(file);
	}

	public String getParent()
	{
		return file.getParent();
	}

	public IAbstractFile getParentFile()
	{
		return new LocalFile(file.getParentFile(), application);
	}

	public String getPath()
	{
		return file.getPath();
	}

	public boolean isAbsolute()
	{
		return file.isAbsolute();
	}

	public String getAbsolutePath()
	{
		return file.getAbsolutePath();
	}

	public IAbstractFile getAbsoluteFile()
	{
		return new LocalFile(file.getAbsoluteFile(), application);
	}

	public boolean canRead()
	{
		return file.canRead();
	}

	public boolean canWrite()
	{
		return file.canWrite();
	}

	public boolean exists()
	{
		return file.exists();
	}

	public boolean isDirectory()
	{
		return file.isDirectory();
	}

	public boolean isFile()
	{
		return file.isFile();
	}

	public boolean isHidden()
	{
		return file.isHidden();
	}

	public long lastModified()
	{
		return file.lastModified();
	}

	public long size()
	{
		return file.length();
	}

	public boolean createNewFile() throws IOException
	{
		return file.createNewFile();
	}

	public boolean delete()
	{
		return file.delete();
	}

	public String[] list()
	{
		return file.list();
	}

	public IAbstractFile[] listFiles()
	{
		File[] listFiles = file.listFiles();
		if (listFiles == null) return new LocalFile[0];
		LocalFile[] files = new LocalFile[listFiles.length];
		for (int i = 0; i < listFiles.length; i++)
		{
			files[i] = new LocalFile(listFiles[i], application);
		}
		return files;
	}

	public boolean mkdir()
	{
		return file.mkdir();
	}

	public boolean mkdirs()
	{
		return file.mkdirs();
	}

	public boolean renameTo(IAbstractFile dest)
	{
		if (dest instanceof LocalFile) return file.renameTo(((LocalFile)dest).file);
		return false; // or throw exception of not supported??
	}

	public boolean setLastModified(long time)
	{
		return file.setLastModified(time);
	}

	public boolean setReadOnly()
	{
		return file.setReadOnly();
	}

	@Override
	public boolean equals(Object obj)
	{
		return file.equals(obj);
	}

	@Override
	public int hashCode()
	{
		return file.hashCode();
	}

	@Override
	public String toString()
	{
		return file.toString();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.j2db.plugins.IUploadData#getInputStream()
	 */
	public InputStream getInputStream() throws IOException
	{
		return new BufferedInputStream(new FileInputStream(file));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.plugins.file.IAbstractFile#setBytes(byte[])
	 */
	public boolean setBytes(byte[] bytes)
	{
		return setBytes(bytes, false);
	}


	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.plugins.file.IAbstractFile#setBytes(byte[], boolean)
	 */
	public boolean setBytes(byte[] bytes, boolean createFile)
	{
		if (bytes != null)
		{
			OutputStream out = null;
			try
			{
				if (exists() || createFile && file.createNewFile())
				{
					out = new BufferedOutputStream(new FileOutputStream(file));
					out.write(bytes);
					out.flush();
					return true;
				}
			}
			catch (IOException ex)
			{
				Debug.error("Error transferring data using setBytes on local JSFile " + getAbsolutePath(), ex); //$NON-NLS-1$
			}
			finally
			{
				if (out != null)
				{
					try
					{
						out.close();
					}
					catch (IOException ignore)
					{
					}
				}
			}
		}
		return false;
	}

	@Override
	public String getRemoteUrl() throws Exception
	{
		if (file.exists() && application instanceof IAllWebClientPluginAccess ngApplication)
		{
			return ngApplication.serveResource(file, getContentType(), "inline");
		}
		return null;
	}
}
