/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2014 Servoy BV

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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.util.Utils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author jcompagner
 *
 */
public class FileServlet extends HttpServlet
{
	private final static ConcurrentMap<String, File> registeredFiles = new ConcurrentHashMap<>();

	private final FileServerPlugin fileServerPlugin;
	private final IServerAccess app;

	/**
	 * @param fileServerPlugin
	 * @param app
	 */
	public FileServlet(FileServerPlugin fileServerPlugin, IServerAccess app)
	{
		this.fileServerPlugin = fileServerPlugin;
		this.app = app;
	}

	@SuppressWarnings("nls")
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
	{
		String pathInfo = Paths.get(req.getPathInfo()).normalize().toString().replace('\\', '/');
		if (pathInfo.startsWith("/file/"))
		{
			String filePath = pathInfo.substring(5);
			String uuidString = filePath.substring(1);
			File file = registeredFiles.get(uuidString);
			if (file == null)
			{
				file = new File(fileServerPlugin.getDefaultFolder(app.getServerLocalClientID()), filePath);
			}
			if (file != null && file.exists() && file.isFile())
			{
				String contentType = AbstractFile.getContentType(file);
				if (contentType == null)
				{
					contentType = req.getServletContext().getMimeType(file.getName());
				}
				if (contentType != null) resp.setContentType(contentType);
				resp.setContentLengthLong(file.length());
				String contentDisposition = req.getParameter("c");
				if (contentDisposition != null)
				{
					contentDisposition = contentDisposition.equals("i") ? "inline" : "attachment";
					resp.setHeader("Content-Disposition",
						contentDisposition + "; filename=\"" + file.getName() + "\"; filename*=UTF-8''" + Rfc5987Util.encode(file.getName(), "UTF8") + "");
				}

				BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
				try
				{
					Utils.streamCopy(bis, resp.getOutputStream());
				}
				finally
				{
					bis.close();
				}
			}
			else
			{
				resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
		else
		{
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	static UUID registerFile(File file)
	{
		UUID uuid = UUID.randomUUID();
		registeredFiles.put(uuid.toString(), file);
		return uuid;
	}

	static void unregisterFile(UUID uuid)
	{
		registeredFiles.remove(uuid.toString());
	}
}
