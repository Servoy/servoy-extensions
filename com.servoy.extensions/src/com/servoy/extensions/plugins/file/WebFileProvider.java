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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.mozilla.javascript.Function;

import com.servoy.j2db.plugins.IAllWebClientPluginAccess;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.MimeTypes;

/**
 * Web plugin provider implementation
 * @author jcompagner
 * @author Servoy Stuff
 */
public class WebFileProvider extends FileProvider
{

	/**
	 * @param plugin
	 */
	public WebFileProvider(FilePlugin plugin)
	{
		super(plugin);
	}

	@Override
	public boolean js_writeFile(JSFile f, byte[] data, String mimeType)
	{
		return writeFile(f, data, mimeType);
	}

	@Override
	public boolean js_writeFile(String f, byte[] data, String mimeType)
	{
		return writeFile(f, data, mimeType);
	}

	@Override
	@SuppressWarnings("nls")
	protected boolean writeFile(Object f, byte[] data, String mimeType)
	{
		if (data == null) return false;
		File file = getFileFromArg(f, false);
		if (file == null || f == null)
		{
			String name = "file.bin";
			if (f instanceof JSFile) name = ((JSFile)f).js_getName();
			else if (f != null) name = f.toString();
			IClientPluginAccess access = plugin.getClientPluginAccess();
			String type = (mimeType == null) ? MimeTypes.getContentType(data, name) : mimeType.trim();
			String url = ((IAllWebClientPluginAccess)access).serveResource(name, data, type);
			((IAllWebClientPluginAccess)access).showURL(url, "_self", null, 0, false);
			return true;
		}
		else
		{
			return super.writeFile(file, data, mimeType);
		}
	}

	@Override
	public boolean js_openFile(JSFile file, String webClientTarget, String webClientTargetOptions)
	{
		IClientPluginAccess access = plugin.getClientPluginAccess();
		String fileName = file.js_getName();
		byte[] data = file.jsFunction_getBytes();
		String url = ((IAllWebClientPluginAccess)access).serveResource(fileName, data, MimeTypes.getContentType(data, fileName), "inline"); //$NON-NLS-1$
		((IAllWebClientPluginAccess)access).showURL(url, webClientTarget != null ? webClientTarget : "_blank", webClientTargetOptions, 0, true); //$NON-NLS-1$
		return true;
	}

	@Override
	public boolean js_openFile(String fileName, byte[] data, String mimeType, String webClientTarget, String webClientTargetOptions)
	{
		String name = (fileName == null ? "file.bin" : fileName); //$NON-NLS-1$

		IClientPluginAccess access = plugin.getClientPluginAccess();
		String type = (mimeType == null) ? MimeTypes.getContentType(data, name) : mimeType.trim();
		String url = ((IAllWebClientPluginAccess)access).serveResource(name, data, type, "inline"); //$NON-NLS-1$
		((IAllWebClientPluginAccess)access).showURL(url, webClientTarget != null ? webClientTarget : "_blank", webClientTargetOptions, 0, true); //$NON-NLS-1$
		return true;
	}

	@Override
	public String js_readTXTFile(JSFile file, String charsetname)
	{
		return readTXTFile(file, charsetname);
	}

	@Override
	public String js_readTXTFile(String file, String charsetname)
	{
		return readTXTFile(file, charsetname);
	}

	@SuppressWarnings("nls")
	private String readTXTFile(Object file, String charsetname)
	{
		if (file instanceof JSFile)
		{
			try
			{
				InputStream is = ((JSFile)file).getInputStream();
				if (is != null)
				{
					return readTXTFile(charsetname, is);
				}
			}
			catch (Exception e)
			{
				Debug.error(e);
				return null;
			}
			return "";
		}
		else
		{
			return super.js_readTXTFile((String)file, charsetname);
		}
	}

	@Override
	public byte[] js_readFile(JSFile file, long size)
	{
		return readFile(file, size);
	}

	@Override
	public byte[] js_readFile(String file, long size)
	{
		return readFile(file, size);
	}

	private byte[] readFile(Object file, long size)
	{
		if (file instanceof JSFile)
		{
			return ((JSFile)file).jsFunction_getBytes();
		}
		else return super.js_readFile((String)file, size);
	}

	/**
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @see com.servoy.extensions.plugins.file.FileProvider#writeTXT(java.lang.Object, java.lang.String, java.lang.String, java.lang.String)
	 */
	@SuppressWarnings("nls")
	@Override
	protected boolean writeTXT(Object f, String data, String encoding, String contentType)
	{
		File file = getFileFromArg(f, false);
		if (file == null || f == null)
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length());
			try
			{
				if (writeToOutputStream(baos, data, encoding))
				{
					String mimeType = contentType;
					if (encoding != null)
					{
						mimeType += "; charset=\"" + encoding + "\"";
					}
					String name = f == null ? "file.txt" : f instanceof JSFile ? ((JSFile)f).js_getName() : f.toString();
					IClientPluginAccess access = plugin.getClientPluginAccess();
					String url = ((IAllWebClientPluginAccess)access).serveResource(name, baos.toByteArray(), mimeType);
					((IAllWebClientPluginAccess)access).showURL(url, "_self", null, 0, false);
					return true;
				}
			}
			catch (Exception e)
			{
				Debug.error(e);
			}
			return false;
		}
		return super.writeTXT(f, data, encoding, contentType);
	}

	@SuppressWarnings("nls")
	@Override
	public JSProgressMonitor js_streamFilesToServer(final Object f, final Object s, final Function callback)
	{
		if (f != null)
		{
			// first get the default upload location (canonical path on the server):
			final String defaultLocation = js_getDefaultUploadLocation();
			if (defaultLocation != null)
			{
				final File serverFolder = new File(defaultLocation);
				final FunctionDefinition function = (callback == null) ? null : new FunctionDefinition(callback);

				final Object[] fileObjects = unwrap(f);
				final Object[] serverFiles = unwrap(s);

				if (fileObjects != null)
				{
					for (int i = 0; i < fileObjects.length; i++)
					{

						InputStream is = null;
						OutputStream os = null;
						Exception ex = null;
						File dest = null;
						try
						{
							// tries to retrieve an inputStream from the IUploadData:
							is = ((JSFile)fileObjects[i]).getAbstractFile().getInputStream();
							if (is != null)
							{
								String serverFileName = null;
								if (serverFiles != null && i < serverFiles.length)
								{
									if (serverFiles[i] instanceof JSFile)
									{
										JSFile jsFile = (JSFile)serverFiles[i];
										IAbstractFile abstractFile = jsFile.getAbstractFile();
										if (abstractFile instanceof RemoteFile)
										{
											serverFileName = extractName(((RemoteFile)abstractFile).getAbsolutePath());
										}
										else
										{
											serverFileName = extractName(abstractFile.getName());
										}
									}
									else
									{
										serverFileName = serverFiles[i].toString();
										FilePluginUtils.filePathCheck(serverFileName);
									}
								}
								else
								{
									// no server file or server file name was provided, so create a default one:
									serverFileName = "/" + extractName(((JSFile)fileObjects[i]).getAbstractFile().getName());
								}
								// serverFileName can point to a folder or a file:
								String clientFileName = extractName(serverFileName);
								if (clientFileName.indexOf('.') < 0)
								{ // no extension, we assume it's a folder:
									if (serverFileName.charAt(serverFileName.length() - 1) != '/')
									{
										serverFileName += "/";
									}
									serverFileName += extractName(((JSFile)fileObjects[i]).getAbstractFile().getName());
								}
								dest = new File(serverFolder, serverFileName);
								if (!FilePluginUtils.checkParentFile(dest, serverFolder))
								{
									// prevents case where serverFileName contains "../" in its path
									throw new SecurityException("Browsing on the server out of the defaultFolder is not allowed");
								}
								if (!dest.exists())
								{
									// doesn't exist? try creating the folder hierarchy:
									dest.getParentFile().mkdirs();
								}
								if ((dest.exists() && dest.canWrite()) || dest.createNewFile())
								{
									os = new BufferedOutputStream(new FileOutputStream(dest));

									final byte[] buffer = new byte[CHUNK_BUFFER_SIZE];
									int read;
									while ((read = is.read(buffer)) != -1)
									{
										os.write(buffer, 0, read);
									}
									os.flush();
								}
							}
						}
						catch (final IOException e)
						{
							Debug.error(e);
							ex = e;
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
							if (function != null)
							{
								try
								{
									final JSFile returnedFile = (dest == null) ? null
										: new JSFile(new RemoteFile(dest, getDefaultFolder(plugin.getClientPluginAccess().getClientID()),
											plugin.getClientPluginAccess()), plugin.getClientPluginAccess());
									function.execute(plugin.getClientPluginAccess(), new Object[] { returnedFile, ex }, true);
								}
								catch (final Exception e)
								{
									Debug.error(e);
								}
							}
						}
					}
				}
			}
		}
		return null; // no JSProgressMonitor since there will be no Thread
	}


	/**
	 * Utility method to extract the name of a path (last part of a path)
	 *
	 * @param path the path that we want to extract the name from
	 * @return the extracted name
	 */
	private String extractName(String path)
	{
		String out = path;
		if (out != null)
		{
			while (out.indexOf('\\') != -1)
			{
				out = out.replace('\\', '/');
			}
			String[] tokens = out.split("/"); //$NON-NLS-1$
			out = tokens[tokens.length - 1];
		}
		return out;
	}
}
