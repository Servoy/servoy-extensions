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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AbstractFileTest
{
	@TempDir
	Path tempDir;

	/** Minimal concrete file, optionally backed by a real file. */
	private static AbstractFile file(File backing)
	{
		return new AbstractFile()
		{
			@Override
			public File getFile()
			{
				return backing;
			}

			public byte[] getBytes()
			{
				return null;
			}

			public String getName()
			{
				return backing != null ? backing.getName() : "memory";
			}

			public boolean setBytes(byte[] bytes, boolean createFile)
			{
				return false;
			}

			public String getRemoteUrl()
			{
				return null;
			}
		};
	}

	@Test
	public void withoutBackingFileTheDefaultsAreReturned() throws Exception
	{
		AbstractFile f = file(null);

		assertNull(f.getContentType());
		assertNull(f.getParent());
		assertNull(f.getParentFile());
		assertNull(f.getPath());
		assertTrue(f.isAbsolute());
		assertNull(f.getAbsolutePath());
		assertNull(f.getAbsoluteFile());
		assertTrue(f.canRead());
		assertFalse(f.canWrite());
		assertTrue(f.exists());
		assertFalse(f.isDirectory());
		assertFalse(f.isFile());
		assertFalse(f.isHidden());
		assertEquals(-1, f.lastModified());
		assertEquals(-1, f.size());
		assertFalse(f.delete());
		assertNull(f.getInputStream());
	}

	@Test
	public void unsupportedOperationsReturnFalseOrEmpty() throws Exception
	{
		AbstractFile f = file(tempDir.resolve("x.txt").toFile());

		assertFalse(f.createNewFile());
		assertArrayEquals(new String[0], f.list());
		assertNull(f.listFiles());
		assertFalse(f.mkdir());
		assertFalse(f.mkdirs());
		assertFalse(f.renameTo(file(null)));
		assertFalse(f.setLastModified(0));
		assertFalse(f.setReadOnly());
		assertFalse(f.setBytes(new byte[] { 1 }));
	}

	@Test
	public void withBackingFileTheFileIsAsked() throws Exception
	{
		Path p = Files.writeString(tempDir.resolve("data.txt"), "hello", StandardCharsets.UTF_8);
		AbstractFile f = file(p.toFile());

		assertEquals(tempDir.toFile().getPath(), f.getParent());
		assertEquals(p.toFile().getPath(), f.getPath());
		assertEquals(p.toFile().getAbsolutePath(), f.getAbsolutePath());
		assertTrue(f.isAbsolute());
		assertTrue(f.canRead());
		assertTrue(f.canWrite());
		assertTrue(f.exists());
		assertTrue(f.isFile());
		assertFalse(f.isDirectory());
		assertFalse(f.isHidden());
		assertEquals(5, f.size());
		assertEquals(p.toFile().lastModified(), f.lastModified());

		assertTrue(f.delete());
		assertFalse(p.toFile().exists());
	}

	@Test
	public void contentTypeIsDetectedFromTheFileHeader() throws Exception
	{
		Path pdf = Files.write(tempDir.resolve("doc.bin"), "%PDF-1.7 rest".getBytes(StandardCharsets.US_ASCII));
		assertEquals("application/pdf", AbstractFile.getContentType(pdf.toFile()));
	}

	@Test
	public void contentTypeIsNullForMissingEmptyOrDirectory() throws Exception
	{
		assertNull(AbstractFile.getContentType(tempDir.resolve("missing.pdf").toFile()));
		assertNull(AbstractFile.getContentType(Files.createFile(tempDir.resolve("empty.pdf")).toFile()));
		assertNull(AbstractFile.getContentType(tempDir.toFile()));
	}
}
