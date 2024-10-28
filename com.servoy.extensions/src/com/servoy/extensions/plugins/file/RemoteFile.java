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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.FileChooserUtils;

/**
 * Implementation of an {@link IAbstractFile} for remote (server-side) files<br/>
 *
 * @author jcompagner
 * @author Servoy Stuff
 * @since Servoy 5.2
 */
public class RemoteFile extends AbstractFile
{

	private final IClientPluginAccess application;
	private final File file;
	private final File mainFolder;
	private final RemoteFile parentFile;

	public RemoteFile(File file, File mainFolder, IClientPluginAccess application)
	{
		this.file = file;
		this.mainFolder = mainFolder;
		this.application = application;
		parentFile = (file.equals(mainFolder)) ? null : new RemoteFile(file.getParentFile(), mainFolder, application);
	}

	public String getName()
	{
		return file.getName();
	}

	/**
	 * @throws UnsupportedMethodException
	 */
	@Override
	public boolean createNewFile() throws IOException
	{
		return file.createNewFile();
	}

	@Override
	public boolean mkdir()
	{
		return file.mkdir();
	}

	@Override
	public boolean mkdirs()
	{
		return file.mkdirs();
	}

	/**
	 * @throws UnsupportedMethodException
	 */
	@Override
	public boolean setLastModified(long time)
	{
		return file.setLastModified(time);
	}

	/**
	 * @throws UnsupportedMethodException
	 */
	@Override
	public boolean setReadOnly()
	{
		return file.setReadOnly();
	}

	@SuppressWarnings("nls")
	public boolean renameTo(String destPath)
	{
		if (destPath == null || !destPath.startsWith("/"))
		{
			throw new IllegalArgumentException("The renameTo() parameter must be an absolute server path (starting with '/')");
		}
		try
		{
			File dest = new File(mainFolder, destPath);
			boolean result = false;
			if (!dest.exists() || dest.delete())
			{
				result = file.renameTo(dest);
			}
			return result;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Error renaming remote file " + getAbsolutePath() + " to " + destPath, e);
		}
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
		return (parentFile == null) ? null : parentFile.getAbsolutePath();
	}

	@Override
	public IAbstractFile getParentFile()
	{
		return parentFile;
	}

	@Override
	public String getPath()
	{
		return getAbsolutePath();
	}

	@Override
	public boolean isAbsolute()
	{
		return true;
	}

	@Override
	public String getAbsolutePath()
	{
		String filePath = "";
		File currentFile = file;
		while (currentFile != null)
		{
			filePath = '/' + currentFile.getName() + filePath;
			currentFile = currentFile.getParentFile();
			if (mainFolder.equals(currentFile))
				break;
		}
		return filePath;
	}

	@Override
	public IAbstractFile getAbsoluteFile()
	{
		return this;
	}

	@SuppressWarnings("nls")
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

	@Override
	public String getContentType()
	{
		return AbstractFile.getContentType(file);
	}

	@SuppressWarnings("nls")
	@Override
	public IAbstractFile[] listFiles()
	{
		if (file.isDirectory())
		{
			try
			{
				return getFolderContent(null, AbstractFile.ALL, AbstractFile.ALL,
					AbstractFile.ALL);
			}
			catch (Exception e)
			{
				throw new RuntimeException("Error listing remote dir: " + file.getAbsolutePath(), e);
			}
		}
		return null;
	}

	@SuppressWarnings("nls")
	@Override
	public String[] list()
	{
		if (file.isDirectory())
		{
			try
			{
				RemoteFile[] remoteList = getFolderContent(null, AbstractFile.ALL, AbstractFile.ALL,
					AbstractFile.ALL);
				String[] files = new String[remoteList.length];
				for (int i = 0; i < files.length; i++)
				{
					files[i] = remoteList[i].getAbsolutePath();
				}
				return files;
			}
			catch (Exception e)
			{
				throw new RuntimeException("Error listing remote dir: " + file.getAbsolutePath(), e);
			}
		}
		return null;
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

	@Override
	public boolean exists()
	{
		return file.exists();
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
			OutputStream out = null;
			try
			{
				if (createFile && !exists())
				{
					createNewFile();
				}
				out = new BufferedOutputStream(new FileOutputStream(file));
				out.write(bytes);
				out.flush();
				return true;
			}
			catch (Exception ex)
			{
				Debug.error("Error transferring data using setBytes on remote JSFile " + getAbsolutePath(), ex); //$NON-NLS-1$
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
	public File getFile()
	{
		return file;
	}

	@Override
	public InputStream getInputStream() throws IOException
	{
		return new BufferedInputStream(new FileInputStream(file));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.plugins.file.IAbstractFile#getRemoteUrl()
	 */
	@Override
	public String getRemoteUrl() throws Exception
	{
		if (!exists()) throw new RuntimeException("File " + getName() + " does not exist on the server");
		URL serverUrl = application.getServerURL();
		String serverPath = serverUrl.getPath().endsWith("/") ? serverUrl.getPath() : serverUrl.getPath() + '/';
		return new URI(serverUrl.getProtocol(), serverUrl.getAuthority(), serverPath + "servoy-service/file" + getAbsolutePath(), null, null)
			.toURL().toString();
	}

	public RemoteFile[] getFolderContent(final String[] fileFilter, final int filesOption,
		final int visibleOption, final int lockedOption)
	{
		if (file.isDirectory())
		{
			final List<RemoteFile> list = new ArrayList<RemoteFile>();
			final FileFilter ff = new FileFilter()
			{
				public boolean accept(File pathname)
				{
					boolean retVal = true;
					if (fileFilter != null)
					{
						String name = pathname.getName().toLowerCase();
						for (String element : fileFilter)
						{
							retVal = name.endsWith(element);
							if (retVal) break;
						}
					}
					if (!retVal) return retVal;

					// file or folder
					if (filesOption == AbstractFile.FILES)
					{
						retVal = pathname.isFile();
					}
					else if (filesOption == AbstractFile.FOLDERS)
					{
						retVal = pathname.isDirectory();
					}
					if (!retVal) return false;

					boolean hidden = pathname.isHidden();
					if (visibleOption == AbstractFile.VISIBLE) retVal = !hidden;
					else if (visibleOption == AbstractFile.NON_VISIBLE) retVal = hidden;
					if (!retVal) return false;

					boolean canWrite = pathname.canWrite();
					if (lockedOption == AbstractFile.LOCKED) retVal = !canWrite;
					else if (lockedOption == AbstractFile.NON_LOCKED) retVal = canWrite;
					return retVal;
				}
			};
			final File[] files = file.listFiles(ff);
			for (final File file : files)
			{
				list.add(new RemoteFile(file, mainFolder, application));
			}
			return list.toArray(new RemoteFile[0]);
		}
		else
		{
			if (file.exists())
			{
				if (filesOption == AbstractFile.ALL || filesOption == AbstractFile.FILES)
				{
					if (visibleOption == AbstractFile.ALL || (visibleOption == AbstractFile.VISIBLE && !file.isHidden()) ||
						(visibleOption == AbstractFile.NON_VISIBLE && file.isHidden()))
					{
						if (lockedOption == AbstractFile.ALL || (lockedOption == AbstractFile.LOCKED && !file.canWrite()) ||
							(lockedOption == AbstractFile.NON_LOCKED && file.canWrite()))
						{
							return new RemoteFile[] { new RemoteFile(file, mainFolder, application) };
						}
					}
				}
			}
			return null;
		}
	}

}
