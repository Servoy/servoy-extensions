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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FilePluginUtilsTest
{
	@TempDir
	Path tempDir;

	@Test
	public void folderItselfAndDirectChildrenAreInside()
	{
		File root = tempDir.toFile();
		assertTrue(FilePluginUtils.checkParentFile(root, root));
		assertTrue(FilePluginUtils.checkParentFile(new File(root, "a.txt"), root));
	}

	@Test
	public void nestedFilesAreInside()
	{
		File root = tempDir.toFile();
		assertTrue(FilePluginUtils.checkParentFile(new File(root, "a/b/c.txt"), root));
	}

	@Test
	public void filesOutsideTheFolderAreRejected()
	{
		File root = new File(tempDir.toFile(), "root");
		assertFalse(FilePluginUtils.checkParentFile(new File(tempDir.toFile(), "other/a.txt"), root));
		assertFalse(FilePluginUtils.checkParentFile(tempDir.toFile(), root));
	}

	@Test
	public void nullFileIsRejected()
	{
		assertFalse(FilePluginUtils.checkParentFile(null, tempDir.toFile()));
	}

	@Test
	public void remotePathMustStartWithSlash()
	{
		assertDoesNotThrow(() -> FilePluginUtils.filePathCheck("/folder/file.txt"));
		assertThrows(IllegalArgumentException.class, () -> FilePluginUtils.filePathCheck("folder/file.txt"));
	}

	@Test
	public void emptyOrNullRemotePathIsRejected()
	{
		assertThrows(IllegalArgumentException.class, () -> FilePluginUtils.filePathCheck(""));
		assertThrows(IllegalArgumentException.class, () -> FilePluginUtils.filePathCheck(null));
	}
}
