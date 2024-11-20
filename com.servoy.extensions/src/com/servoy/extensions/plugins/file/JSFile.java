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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IFile;
import com.servoy.j2db.plugins.IUploadData;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * <p>The <code>JSFile</code> is an <code>IScriptObject</code> representation of a file,
 * supporting local, remote, and web contexts. It provides functionalities to manipulate
 * files, such as checking read/write permissions, creating or deleting files, and
 * managing file properties like size, type, and last modified date. Operations on
 * directories are also supported, allowing directory creation and listing of contents.</p>
 *
 * @author jcompagner
 * @author Servoy Stuff
 */
@ServoyDocumented(scriptingName = "JSFile")
public class JSFile implements IScriptable, IJavaScriptType, IFile
{
	private final IAbstractFile file;
	private JSFile[] EMPTY;
	private final IClientPluginAccess application;

	public JSFile()
	{
		//for developer scripting introspection only
		this((File)null, null);
	}

	public JSFile(File file, IClientPluginAccess application)
	{
		this.file = new LocalFile(file, application);
		this.application = application;
	}

	public JSFile(IUploadData upload, IClientPluginAccess application)
	{
		if (upload instanceof IAbstractFile)
		{
			this.file = (IAbstractFile)upload;
		}
		else
		{
			this.file = new UploadData(upload, application);
		}
		this.application = application;
	}

	public IAbstractFile getAbstractFile()
	{
		return this.file;
	}

	/**
	 * Returns the name of the file. The name consists in the last part of the file path - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public String js_getName()
	{
		return file.getName();
	}

	/**
	 * Returns the String representation of the path of the parent of this file - works on remote files too.
	 *
	 * @sampleas js_getAbsoluteFile()
	 */
	public String js_getParent()
	{
		return file.getParent();
	}

	/**
	 * Returns a JSFile instance that corresponds to the parent of this file - works on remote files too.
	 *
	 * @sampleas js_getAbsoluteFile()
	 */
	public JSFile js_getParentFile()
	{
		return new JSFile(file.getParentFile(), application);
	}

	/**
	 * Returns a String holding the path to the file - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public String js_getPath()
	{
		return file.getPath();
	}

	/**
	 * Returns true if the path is absolute. The path is absolute if it starts with '/' on Unix/Linux/MacOS or has a driver letter on Windows - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public boolean js_isAbsolute()
	{
		return file.isAbsolute();
	}

	/**
	 * Returns a String representation of the absolute form of this pathname - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public String js_getAbsolutePath()
	{
		return file.getAbsolutePath();
	}


	/**
	 * Returns a JSFile instance that corresponds to the absolute form of this pathname - works on remote files too.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('story.txt');
	 * // or for a remote file:
	 * // var f = plugins.file.convertToRemoteJSFile('/story.txt');
	 * application.output('parent folder: ' + f.getAbsoluteFile().getParent());
	 * application.output('parent folder has ' + f.getAbsoluteFile().getParentFile().listFiles().length + ' entries');
	 */
	public JSFile js_getAbsoluteFile()
	{
		return new JSFile(file.getAbsoluteFile(), application);
	}

	/**
	 * Returns true if the file exists and is readable (has access to it) - works on remote files too.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('./big.jpg');
	 * // or for a remote file:
	 * // var f = plugins.convertToRemoteJSFile('/images/big.jpg');
	 * if (f && f.exists()) {
	 * 	application.output('is absolute: ' + f.isAbsolute());
	 * 	application.output('is dir: ' + f.isDirectory());
	 * 	application.output('is file: ' + f.isFile());
	 * 	application.output('is hidden: ' + f.isHidden());
	 * 	application.output('can read: ' + f.canRead());
	 * 	application.output('can write: ' + f.canWrite());
	 * 	application.output('last modified: ' + f.lastModified());
	 * 	application.output('name: ' + f.getName());
	 * 	application.output('path: ' + f.getPath());
	 * 	application.output('absolute path: ' + f.getAbsolutePath());
	 * 	application.output('content type: ' + f.getContentType());
	 * 	application.output('size: ' + f.size());
	 * }
	 * else {
	 * 	application.output('File/folder not found.');
	 * }
	 */
	public boolean js_canRead()
	{
		return file.canRead();
	}

	/**
	 * Returns true if the file exists and can be modified - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public boolean js_canWrite()
	{
		return file.canWrite();
	}

	/**
	 * Returns true if the file/directory exists on the filesystem - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public boolean js_exists()
	{
		return file.exists();
	}

	/**
	 * Returns true if the file is a directory - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public boolean js_isDirectory()
	{
		return file.isDirectory();
	}

	/**
	 * Gets the contents (bytes) for the file data.
	 *
	 * @sample
	 * var theFile = plugins.file.showFileOpenDialog();
	 * application.output('The file size in bytes: ' + theFile.getBytes());
	 */
	public byte[] jsFunction_getBytes()
	{
		return file.getBytes();
	}

	/**
	 * Returns true if the file is a file and not a regular file - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public boolean js_isFile()
	{
		return file.isFile();
	}

	/**
	 * Returns true if the file is hidden (a file system attribute) - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public boolean js_isHidden()
	{
		return file.isHidden();
	}

	/**
	 * Returns the time/date of the last modification on the file - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public Date js_lastModified()
	{
		return new Date(file.lastModified());
	}

	/**
	 * Returns the size in bytes of the file. Returns 0 if the file does not exist on disk - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public long js_size()
	{
		return file.size();
	}

	/**
	 * Creates the file on disk if needed. Returns true if the file (name) did not already exists and had to be created - for remote, use the streamFilesToServer to stream a file.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('story.txt');
	 * if (!f.exists())
	 * 	f.createNewFile();
	 */
	public boolean js_createNewFile() throws IOException
	{
		return file.createNewFile();
	}

	/**
	 * @deprecated Replaced by {@link #deleteFile()}.
	 */
	@Deprecated
	public boolean js_delete()
	{
		return file.delete();
	}

	/**
	 * Deletes the file from the disk if possible. Returns true if the file could be deleted. If the file is a directory, then it must be empty in order to be deleted - works on remote files too.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('story.txt');
	 * // or for a remote file:
	 * // var f = plugins.convertToRemoteJSFile('/story.txt');
	 * if (f && f.exists())
	 * 	f.deleteFile();
	 */
	public boolean js_deleteFile()
	{
		return file.delete();
	}

	/**
	 * Returns an array of strings naming the files and directories located inside the file, if the file is a directory - works on remote files too.
	 *
	 * @sample
	 * var d = plugins.file.convertToJSFile('plugins');
	 * // or for a remote file:
	 * // var d = plugins.convertToRemoteJSFile('/plugins');
	 * var names = d.list();
	 * application.output('Names:');
	 * for (var i=0; i<names.length; i++)
	 * 	application.output(names[i]);
	 * var files = d.listFiles();
	 * application.output('Absolute paths:');
	 * for (var i=0; i<files.length; i++)
	 * 	application.output(files[i].getAbsolutePath());
	 */
	public String[] js_list()
	{
		return file.list();
	}

	/**
	 * Returns an array of JSFiles naming the files and directories located inside the file, if the file is a directory - works on remote files too.
	 *
	 * @sampleas js_list()
	 */
	public JSFile[] js_listFiles()
	{
		IAbstractFile[] files = file.listFiles();
		if (files == null) return EMPTY;
		JSFile[] retArray = new JSFile[files.length];
		for (int i = 0; i < files.length; i++)
		{
			retArray[i] = new JSFile(files[i], application);
		}
		return retArray;
	}

	/**
	 * Creates a directory on disk if possible. Returns true if a new directory was created - for remote, use the streamFilesToServer to create the directory instead.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('one/two/three/four');
	 * f.mkdirs(); // Create all four levels of folders in one step.
	 * var g = plugins.file.convertToJSFile('one/two/three/four/five');
	 * g.mkdir(); // This will work because all parent folders are already created.
	 */
	public boolean js_mkdir()
	{
		return file.mkdir();
	}

	/**
	 * Creates a directory on disk, together with all its parent directories, if possible. Returns true if the hierarchy of directories is created - for remote, use the streamFilesToServer to create the directories instead.
	 *
	 * @sampleas js_mkdir()
	 */
	public boolean js_mkdirs()
	{
		return file.mkdirs();
	}

	/**
	 * Renames the file to a different name. Returns true if the file could be renamed - works on remote files too.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('story.txt');
	 * f.renameTo('otherstory.txt');
	 * // or for a remote file:
	 * // var f = plugins.convertToRemoteJSFile('/story.txt');
	 * // f.renameTo('/otherstory.txt');
	 *
	 * @param destination
	 */
	public boolean js_renameTo(Object destination)
	{
		if (destination instanceof JSFile)
		{
			return file.renameTo(((JSFile)destination).file);
		}
		else if (destination instanceof String)
		{
			if (file instanceof RemoteFile)
			{
				return ((RemoteFile)file).renameTo((String)destination);
			}
			else
			{
				return file.renameTo(new LocalFile(new File((String)destination), application));
			}
		}
		return false;
	}

	/**
	 * Return a new JSFile instance that contains a remote file with the content of current JSFile.
	 * @param fileName
	 * @return JSFile
	 */
	public JSFile js_convertToRemote(String fileName)
	{
		File mainFolder = null;
		try
		{
			IFileService service = (IFileService)application.getRemoteService(IFileService.class.getName());
			mainFolder = service.getDefaultFolder(application.getClientID());
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		File destFile = new File(mainFolder, fileName);
		if (!file.exists())
		{
			try
			{
				file.createNewFile();
			}
			catch (IOException e)
			{
				Debug.error(e);
			}
		}
		InputStream is = null;
		OutputStream os = null;

		try
		{
			is = getInputStream();
			if (is != null)
			{
				os = new BufferedOutputStream(new FileOutputStream(destFile));

				final byte[] buffer = new byte[FileProvider.CHUNK_BUFFER_SIZE];
				int read;
				while ((read = is.read(buffer)) != -1)
				{
					os.write(buffer, 0, read);
				}
				os.flush();
			}
		}
		catch (final IOException e)
		{
			Debug.error(e);
		}
		finally
		{
			if (is != null)
			{
				try
				{
					is.close();
				}
				catch (final IOException e)
				{
				}
			}
			if (os != null)
			{
				try
				{
					os.close();
				}
				catch (final IOException e)
				{
				}
			}
		}


		RemoteFile remoteFile = new RemoteFile(destFile, mainFolder, application);
		JSFile jsfile = new JSFile(remoteFile, application);
		return jsfile;
	}

	/**
	 * Sets the date/time of the last modification on the file.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('story.txt');
	 * f.createNewFile();
	 * // Make the file look old.
	 * f.setLastModified(new Date(1999, 5, 21));
	 *
	 * @param date
	 */
	public boolean js_setLastModified(Object date)
	{
		long time = -1;
		if (date instanceof Date)
		{
			time = ((Date)date).getTime();
		}
		else if (date instanceof Number)
		{
			time = ((Number)date).longValue();
		}
		if (time != -1)
		{
			return file.setLastModified(time);
		}
		return false;
	}

	/**
	 * Sets the readonly attribute of the file/directory. Returns true on success.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('invoice.txt');
	 * plugins.file.writeTXTFile(f, 'important data that should not be changed');
	 * f.setReadOnly();
	 */
	public boolean js_setReadOnly()
	{
		return file.setReadOnly();
	}

	/**
	 * Returns the contenttype of this file, like for example 'application/pdf' - works on remote files too.
	 *
	 * @sampleas js_canRead()
	 */
	public String js_getContentType()
	{
		return file.getContentType();
	}

	/**
	 * Set the content of the file (local or remote) to the bytes provided<br/>
	 * Will not create a new file if one doesn't exist
	 *
	 * @sample
	 * var file = plugins.file.convertToJSFile('/pathTo/file.jpg');
	 * // or for a remote file:
	 * // var file = plugins.file.convertToRemoteJSFile('/remotePathTo/file.jpg');
	 * var success = file.setBytes(blobDataProvider, true);
	 *
	 * @param bytes the data
	 *
	 * @return true if the operation worked
	 * @since 5.2.5
	 */
	public boolean js_setBytes(byte[] bytes)
	{
		return js_setBytes(bytes, false);
	}

	/**
	 * Set the content of the file (local or remote) to the bytes provided
	 *
	 * @sampleas js_setBytes(byte[])
	 *
	 * @param bytes the data
	 * @param createFile true to create a file if it doesn't exist
	 *
	 * @return true if the operation worked
	 * @since 5.2.5
	 */
	public boolean js_setBytes(byte[] bytes, boolean createFile)
	{
		return file.setBytes(bytes, createFile);
	}

	/**
	 * Get a url from file that can be used to download the file in a browser.
	 * This is a complete url with the server url that is get from application.getServerURL().
	 * If the file is a remote file will be shared using a default folder that requires no session.
	 * If the file is a local file will be available only for current client (url contains session id)
	 *
	 * @return the url
	 */
	public String js_getRemoteUrl() throws Exception
	{
		return file.getRemoteUrl();
	}

	@Override
	public String toString()
	{
		// for backward compatibility we will return to toString() of a File object if it is a File object.
		if (file instanceof LocalFile)
		{
			return file.toString();
		}
		String fName = file.getAbsolutePath() != null ? file.getAbsolutePath() : (file.getName() != null ? file.getName() : ""); //$NON-NLS-1$
		return "JSFile[name:" + fName + ",contenttype:" + file.getContentType() + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	}

	/**
	 * @return
	 */
	public File getFile()
	{
		return file.getFile();
	}

	@Override
	public InputStream getInputStream() throws IOException
	{
		return file.getInputStream();
	}

	@Override
	public String getContentType()
	{
		return file.getContentType();
	}

	@Override
	public long getSize()
	{
		return file.size();
	}
}
