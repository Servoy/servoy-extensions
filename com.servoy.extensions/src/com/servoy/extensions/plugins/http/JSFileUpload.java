/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2024 Servoy BV

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

package com.servoy.extensions.plugins.http;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IFile;
import com.servoy.j2db.plugins.IUploadData;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.util.FileChooserUtils;

/**
 * @author lvostinar
 *
 */
@ServoyDocumented
public class JSFileUpload implements IUploadData, IJavaScriptType, IFile
{
	private final File file;
	private final String contentType;

	public JSFileUpload(File file, String contentType)
	{
		this.file = file;
		this.contentType = contentType;

	}

	@Override
	public long getSize()
	{
		if (file != null)
		{
			return file.length();
		}
		return 0;
	}

	@Override
	public File getFile()
	{
		return file;
	}

	@Override
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
	public String getName()
	{
		if (file != null)
		{
			return file.getName();
		}
		return null;
	}

	@Override
	public String getContentType()
	{
		return contentType;
	}

	@Override
	public InputStream getInputStream()
	{
		if (file == null) return null;
		try
		{
			return new BufferedInputStream(new FileInputStream(file));
		}
		catch (FileNotFoundException e)
		{
			return null;
		}
	}


	@Override
	public long lastModified()
	{
		if (file != null) return file.lastModified();
		return 0;
	}

}
