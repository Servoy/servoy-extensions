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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.j2db.plugins.IAllWebClientPluginAccess;
import com.servoy.j2db.plugins.IClientPluginAccess;

public class LocalFileTest
{
	@TempDir
	Path tempDir;

	private LocalFile local(String name)
	{
		return new LocalFile(tempDir.resolve(name).toFile(), null);
	}

	@Test
	public void delegatesTheBasicPropertiesToTheFile() throws Exception
	{
		Path p = Files.writeString(tempDir.resolve("a.txt"), "abc", StandardCharsets.UTF_8);
		LocalFile f = new LocalFile(p.toFile(), null);

		assertEquals(p.toFile(), f.getFile());
		assertEquals("a.txt", f.getName());
		assertEquals(tempDir.toFile().getPath(), f.getParent());
		assertEquals(p.toFile().getPath(), f.getPath());
		assertEquals(p.toFile().getAbsolutePath(), f.getAbsolutePath());
		assertTrue(f.isAbsolute());
		assertTrue(f.exists());
		assertTrue(f.isFile());
		assertFalse(f.isDirectory());
		assertFalse(f.isHidden());
		assertTrue(f.canRead());
		assertTrue(f.canWrite());
		assertEquals(3, f.size());
		assertEquals(p.toFile().lastModified(), f.lastModified());
		assertEquals(p.toFile().toString(), f.toString());
		assertEquals(p.toFile().hashCode(), f.hashCode());
	}

	@Test
	public void parentAndAbsoluteFileAreLocalFiles()
	{
		LocalFile f = local("sub/a.txt");

		IAbstractFile parent = f.getParentFile();
		assertInstanceOf(LocalFile.class, parent);
		assertEquals(tempDir.resolve("sub").toFile(), ((LocalFile)parent).getFile());
		assertEquals(f.getFile().getAbsoluteFile(), ((LocalFile)f.getAbsoluteFile()).getFile());
	}

	@Test
	public void bytesOfAnExistingFileAreRead() throws Exception
	{
		Files.write(tempDir.resolve("b.bin"), new byte[] { 1, 2, 3 });
		assertArrayEquals(new byte[] { 1, 2, 3 }, local("b.bin").getBytes());
	}

	@Test
	public void bytesOfAMissingFileOrFolderAreNull()
	{
		assertNull(local("missing.bin").getBytes());
		assertNull(new LocalFile(tempDir.toFile(), null).getBytes());
	}

	@Test
	public void setBytesOverwritesAnExistingFile() throws Exception
	{
		Files.writeString(tempDir.resolve("c.txt"), "old");
		LocalFile f = local("c.txt");

		assertTrue(f.setBytes("new".getBytes(StandardCharsets.UTF_8)));
		assertEquals("new", Files.readString(tempDir.resolve("c.txt")));
	}

	@Test
	public void setBytesOnlyCreatesTheFileWhenAsked() throws Exception
	{
		LocalFile f = local("d.txt");

		assertFalse(f.setBytes(new byte[] { 1 }));
		assertFalse(f.exists());

		assertTrue(f.setBytes(new byte[] { 1, 2 }, true));
		assertArrayEquals(new byte[] { 1, 2 }, Files.readAllBytes(tempDir.resolve("d.txt")));
	}

	@Test
	public void setBytesWithNullOrUncreatableTargetFails()
	{
		assertFalse(local("e.txt").setBytes(null, true));
		// parent folder does not exist, so the file cannot be created
		assertFalse(local("nofolder/e.txt").setBytes(new byte[] { 1 }, true));
	}

	@Test
	public void inputStreamReadsTheContentOrIsNullWhenMissing() throws Exception
	{
		Files.writeString(tempDir.resolve("f.txt"), "stream");
		try (InputStream in = local("f.txt").getInputStream())
		{
			assertEquals("stream", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		assertNull(local("missing.txt").getInputStream());
	}

	@Test
	public void createDeleteAndFolderOperationsWorkOnDisk() throws Exception
	{
		LocalFile f = local("g.txt");
		assertTrue(f.createNewFile());
		assertTrue(f.exists());
		assertTrue(f.delete());
		assertFalse(f.exists());

		assertTrue(local("dir").mkdir());
		assertTrue(local("deep/er/dir").mkdirs());
		assertTrue(local("deep/er/dir").isDirectory());
	}

	@Test
	public void listAndListFilesReturnTheFolderContent() throws Exception
	{
		Files.createFile(tempDir.resolve("one.txt"));
		Files.createFile(tempDir.resolve("two.txt"));
		LocalFile folder = new LocalFile(tempDir.toFile(), null);

		String[] names = folder.list();
		Arrays.sort(names);
		assertArrayEquals(new String[] { "one.txt", "two.txt" }, names);

		IAbstractFile[] files = folder.listFiles();
		assertEquals(2, files.length);
		assertInstanceOf(LocalFile.class, files[0]);
	}

	@Test
	public void listFilesOfAFileIsEmpty() throws Exception
	{
		Files.createFile(tempDir.resolve("h.txt"));
		assertEquals(0, local("h.txt").listFiles().length);
	}

	@Test
	public void renameToOnlyWorksForLocalFiles() throws Exception
	{
		Files.writeString(tempDir.resolve("from.txt"), "x");
		LocalFile from = local("from.txt");

		assertFalse(from.renameTo(mock(IAbstractFile.class)));
		assertTrue(from.renameTo(local("to.txt")));
		assertTrue(Files.exists(tempDir.resolve("to.txt")));
		assertFalse(Files.exists(tempDir.resolve("from.txt")));
	}

	@Test
	public void lastModifiedAndReadOnlyCanBeSet() throws Exception
	{
		Files.createFile(tempDir.resolve("i.txt"));
		LocalFile f = local("i.txt");

		assertTrue(f.setLastModified(1_000_000_000_000L));
		assertEquals(1_000_000_000_000L, f.lastModified());
		assertTrue(f.setReadOnly());
		assertFalse(f.canWrite());
		// make it writable again so @TempDir can clean up
		f.getFile().setWritable(true);
	}

	@Test
	public void contentTypeComesFromTheFileHeader() throws Exception
	{
		Files.write(tempDir.resolve("j.pdf"), "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
		assertEquals("application/pdf", local("j.pdf").getContentType());
	}

	@Test
	public void localFilesOfTheSameFileAreEqual()
	{
		LocalFile f = local("k.txt");
		LocalFile same = local("k.txt");

		assertTrue(f.equals(f));
		assertTrue(f.equals(same));
		assertTrue(same.equals(f));
		assertEquals(f.hashCode(), same.hashCode());
		assertFalse(f.equals(local("other.txt")));
	}

	@Test
	public void localFileIsNotEqualToOtherTypes()
	{
		LocalFile f = local("k.txt");
		assertFalse(f.equals(tempDir.resolve("k.txt").toFile()));
		assertFalse(f.equals(null));
	}

	@Test
	public void localFilesWithoutAFileAreEqual()
	{
		assertTrue(new LocalFile(null, null).equals(new LocalFile(null, null)));
		assertFalse(new LocalFile(null, null).equals(local("k.txt")));
		assertFalse(local("k.txt").equals(new LocalFile(null, null)));
		assertEquals(0, new LocalFile(null, null).hashCode());
	}

	@Test
	public void remoteUrlIsServedByTheWebClientForExistingFiles() throws Exception
	{
		Files.write(tempDir.resolve("l.pdf"), "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
		IAllWebClientPluginAccess web = mock(IAllWebClientPluginAccess.class);
		when(web.serveResource(any(File.class), anyString(), eq("inline"))).thenReturn("resources/l.pdf");

		assertEquals("resources/l.pdf", new LocalFile(tempDir.resolve("l.pdf").toFile(), web).getRemoteUrl());
	}

	@Test
	public void remoteUrlIsNullForMissingFilesOrNonWebClients() throws Exception
	{
		Files.createFile(tempDir.resolve("m.txt"));
		assertNull(new LocalFile(tempDir.resolve("m.txt").toFile(), mock(IClientPluginAccess.class)).getRemoteUrl());
		assertNull(new LocalFile(tempDir.resolve("missing.txt").toFile(), mock(IAllWebClientPluginAccess.class)).getRemoteUrl());
	}
}
