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

import com.servoy.j2db.plugins.IUploadData;

/**
 * General contract for scripting wrappers representing files, either local, remote or web<br/>
 * Emulates the {@link File} class
 *
 * @author jcompagner
 * @author Servoy Stuff
 * @since Servoy 5.2
 */
public interface IAbstractFile extends IUploadData
{

	/**
	 * @return the parent path
	 */
	String getParent();

	/**
	 * @return the parent file
	 */
	IAbstractFile getParentFile();

	/**
	 * @return the path of this file
	 */
	String getPath();

	/**
	 * @return true if this file's path is absolute
	 */
	boolean isAbsolute();

	/**
	 * @return the absolute path of this file
	 */
	String getAbsolutePath();

	/**
	 * @return the absolute file of this file
	 */
	IAbstractFile getAbsoluteFile();


	/**
	 * @return true if the file exists and is readable
	 */
	boolean canRead();

	/**
	 * @return true if the file exists and is writable
	 */
	boolean canWrite();

	/**
	 * @return true if the file exists
	 */
	boolean exists();

	/**
	 * @return true if the file is not a directory
	 */
	boolean isDirectory();

	/**
	 * @return true if the file is a directory
	 */
	boolean isFile();

	/**
	 * @return true if the file is a hidden file
	 */
	boolean isHidden();

	/**
	 * @return the size of the file
	 */
	long size();

	/**
	 * Tries to create a new physical file based on this abstract representation
	 * @return true if the operation succeeded
	 */
	boolean createNewFile() throws IOException;

	/**
	 * Tries to delete the physical file based on this abstract representation
	 * @return true if the operation succeeded
	 */
	boolean delete();

	/**
	 * Returns a list of names of files contained in this folder
	 * @return the file list as String
	 */
	String[] list();

	/**
	 * Returns a list of files contained in this folder
	 * @return the file list
	 */
	IAbstractFile[] listFiles();

	/**
	 * Tries to create a directory based on this abstract representation
	 * @return true if the operation succeeded
	 */
	boolean mkdir();

	/**
	 * Tries to create directories along the path based on this abstract representation
	 * @return true if the operation succeeded
	 */
	boolean mkdirs();

	/**
	 * Tries to rename a file to another path
	 * @param upload the file to rename to
	 * @return true if the operation succeeded
	 */
	boolean renameTo(IAbstractFile upload);

	/**
	 * Tries to change the lastModified date/time of this file
	 * @param time the new lastModified value to use
	 * @return true if the operation succeeded
	 */
	boolean setLastModified(long time);

	/**
	 * Tries to set the state of this file to readOnly
	 * @return true if the operation succeeded
	 */
	boolean setReadOnly();

	/**
	 * Save data into the {@link File} represented by this object
	 * @param bytes the data
	 * @return true if the bytes were written
	 * @since 5.2.5
	 */
	boolean setBytes(byte[] bytes);


	/**
	 * Save data into the {@link File} represented by this object
	 * @param bytes the data
	 * @param createFile true to create a file if not existing
	 * @return true if the bytes were written
	 * @since 5.2.5
	 */
	boolean setBytes(byte[] bytes, boolean createFile);

	String getRemoteUrl() throws Exception;
}
