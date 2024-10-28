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
import java.io.IOException;
import java.io.InputStream;

import com.servoy.j2db.plugins.IAllWebClientPluginAccess;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IUploadData;
import com.servoy.j2db.util.MimeTypes;

/**
 * Implementation of an {@link IAbstractFile} for web accessible file<br/>

 * @author jcompagner
 * @author Servoy Stuff
 * @since Servoy 5.2
 */
public class UploadData extends AbstractFile
{
	private final IClientPluginAccess application;
	private final IUploadData upload;

	public UploadData(IUploadData upload, IClientPluginAccess application)
	{
		this.upload = upload;
		this.application = application;
	}

	@Override
	public File getFile()
	{
		return upload.getFile();
	}

	public byte[] getBytes()
	{
		return upload.getBytes();
	}

	public String getName()
	{
		return upload.getName();
	}

	@Override
	public String getContentType()
	{
		String contentType = upload.getContentType();
		if (contentType != null) return contentType;
		return MimeTypes.getContentType(getBytes(), getName());
	}

	@Override
	public long lastModified()
	{
		return upload.lastModified();
	}

	@Override
	public long size()
	{
		if (getFile() != null) return getFile().length();
		return getBytes().length;
	}

	@Override
	public boolean renameTo(IAbstractFile dest)
	{
		if (getFile() != null && dest instanceof LocalFile)
		{
			return getFile().renameTo(((LocalFile)dest).getFile());
		}
		return false;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString()
	{
		return "UploadData[name:" + getName() + ",contenttype:" + getContentType() + "]";
	}

	@Override
	public InputStream getInputStream() throws IOException
	{
		return upload.getInputStream();
	}

	public boolean setBytes(byte[] bytes, boolean createFile)
	{
		throw new UnsupportedMethodException("JSFile.setBytes() is not web compatible"); //$NON-NLS-1$
	}

	@Override
	public String getRemoteUrl() throws Exception
	{
		if (getFile() != null && getFile().exists() && application instanceof IAllWebClientPluginAccess ngApplication)
		{
			return ngApplication.serveResource(getFile(), getContentType(), "inline");
		}
		return null;
	}
}
