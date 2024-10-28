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

/**
 * Utility class holding methods used by {@link FileServerPlugin} and {@link WebFileProvider}
 *
 * @author Servoy Stuff
 *
 * @since Servoy 5.2.2
 */
public class FilePluginUtils
{

	/**
	 * Checks that the parent of the file provided is the defaultFolder<br/>
	 * Added for security check (to prevent traversing the hierarchy of the server up from the defaultFolder).
	 *
	 * @param f the file to check for parents
	 * @param defaultFolder must be a parent of the file
	 *
	 * @return true if parent is defaultFolder
	 */
	public static boolean checkParentFile(final File f, final File defaultFolder)
	{
		if (f == null) return false;
		if (defaultFolder.equals(f) || defaultFolder.equals(f.getParentFile()))
		{
			return true;
		}
		else
		{
			return checkParentFile(f.getParentFile(), defaultFolder);
		}
	}

	/**
	 * Check that the path is 'absolute' on the server
	 *
	 * @param filePath the file path to check - must start with '/'
	 *
	 * @throws IllegalArgumentException if the path doesn't start with '/'
	 */
	public static void filePathCheck(final String filePath) throws IllegalArgumentException
	{
		if (filePath.charAt(0) != '/')
		{
			throw new IllegalArgumentException("Remote path should start with '/'"); //$NON-NLS-1$
		}
	}

}
