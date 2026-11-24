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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.j2db.plugins.IClientPluginAccess;

public class RemoteFileTest
{
	private static final byte[] PDF = "%PDF-1.4 body".getBytes(StandardCharsets.US_ASCII);

	@TempDir
	Path tempDir;

	private File main;

	@BeforeEach
	public void setUp() throws Exception
	{
		main = Files.createDirectory(tempDir.resolve("server")).toFile();
	}

	private RemoteFile remote(String relativePath)
	{
		return new RemoteFile(new File(main, relativePath), main, null);
	}

	private File write(String relativePath, byte[] content) throws Exception
	{
		Path p = main.toPath().resolve(relativePath);
		Files.createDirectories(p.getParent());
		return Files.write(p, content).toFile();
	}

	@Test
	public void absolutePathIsRelativeToTheServerFolder() throws Exception
	{
		write("docs/a.txt", new byte[0]);
		RemoteFile f = remote("docs/a.txt");

		assertEquals("a.txt", f.getName());
		assertEquals("/docs/a.txt", f.getAbsolutePath());
		assertEquals("/docs/a.txt", f.getPath());
		assertTrue(f.isAbsolute());
		assertSame(f, f.getAbsoluteFile());
		assertEquals(new File(main, "docs/a.txt"), f.getFile());
	}

	@Test
	public void parentChainStopsAtTheServerFolder()
	{
		RemoteFile f = remote("docs/sub/a.txt");

		assertEquals("/docs/sub", f.getParent());
		RemoteFile parent = (RemoteFile)f.getParentFile();
		assertEquals("/docs", parent.getParent());

		RemoteFile root = new RemoteFile(main, main, null);
		assertNull(root.getParentFile());
		assertNull(root.getParent());
	}

	@Test
	public void bytesContentTypeAndStreamReadTheServerFile() throws Exception
	{
		write("doc.pdf", PDF);
		RemoteFile f = remote("doc.pdf");

		assertTrue(f.exists());
		assertArrayEquals(PDF, f.getBytes());
		assertEquals("application/pdf", f.getContentType());
		assertEquals(PDF.length, f.size());
		try (InputStream in = f.getInputStream())
		{
			assertArrayEquals(PDF, in.readAllBytes());
		}
	}

	@Test
	public void missingFileHasNoBytesNoStreamAndNoContentType()
	{
		RemoteFile f = remote("missing.pdf");

		assertFalse(f.exists());
		assertNull(f.getBytes());
		assertNull(f.getInputStream());
		assertNull(f.getContentType());
	}

	@Test
	public void folderHasNoBytes()
	{
		assertNull(new RemoteFile(main, main, null).getBytes());
	}

	@Test
	public void createMkdirsLastModifiedAndReadOnlyWorkOnTheServerFile() throws Exception
	{
		RemoteFile f = remote("new.txt");
		assertTrue(f.createNewFile());
		assertTrue(f.exists());

		assertTrue(f.setLastModified(1_000_000_000_000L));
		assertEquals(1_000_000_000_000L, f.lastModified());
		assertTrue(f.setReadOnly());
		assertFalse(f.canWrite());
		f.getFile().setWritable(true);

		assertTrue(remote("dir").mkdir());
		assertTrue(remote("a/b/c").mkdirs());
		assertTrue(remote("a/b/c").isDirectory());
	}

	@Test
	public void setBytesWritesExistingFilesOrCreatesWhenAsked() throws Exception
	{
		RemoteFile f = remote("data.bin");

		assertFalse(f.setBytes(new byte[] { 1 }, false));
		assertFalse(f.exists());
		assertTrue(f.setBytes(new byte[] { 1, 2 }, true));
		assertArrayEquals(new byte[] { 1, 2 }, Files.readAllBytes(f.getFile().toPath()));
		assertTrue(f.setBytes(new byte[] { 3 }));
		assertArrayEquals(new byte[] { 3 }, Files.readAllBytes(f.getFile().toPath()));
		assertFalse(f.setBytes(null, true));
	}

	@Test
	public void setBytesFailsWhenTheFileCannotBeCreated()
	{
		assertFalse(remote("no/such/folder/x.bin").setBytes(new byte[] { 1 }, true));
	}

	@Test
	public void renameToAServerPathMovesTheFile() throws Exception
	{
		write("from.txt", new byte[] { 1 });

		assertTrue(remote("from.txt").renameTo("/to.txt"));
		assertTrue(new File(main, "to.txt").exists());
		assertFalse(new File(main, "from.txt").exists());
	}

	@Test
	public void renameToReplacesAnExistingTarget() throws Exception
	{
		write("from.txt", "new".getBytes(StandardCharsets.UTF_8));
		write("to.txt", "old".getBytes(StandardCharsets.UTF_8));

		assertTrue(remote("from.txt").renameTo("/to.txt"));
		assertEquals("new", Files.readString(main.toPath().resolve("to.txt")));
	}

	@Test
	public void renameToRequiresAnAbsoluteServerPath() throws Exception
	{
		write("from.txt", new byte[] { 1 });
		RemoteFile f = remote("from.txt");

		assertThrows(IllegalArgumentException.class, () -> f.renameTo("to.txt"));
		assertThrows(IllegalArgumentException.class, () -> f.renameTo((String)null));
	}

	@Test
	public void renameToAnotherRemoteFileUsesItsServerPath() throws Exception
	{
		write("from.txt", new byte[] { 1 });

		assertTrue(remote("from.txt").renameTo((IAbstractFile)remote("moved.txt")));
		assertTrue(new File(main, "moved.txt").exists());
	}

	@Test
	public void renameToANonRemoteFileIsNotSupported() throws Exception
	{
		write("from.txt", new byte[] { 1 });
		RemoteFile f = remote("from.txt");
		LocalFile local = new LocalFile(tempDir.resolve("local.txt").toFile(), null);

		assertThrows(UnsupportedMethodException.class, () -> f.renameTo((IAbstractFile)local));
	}

	@Test
	public void listAndListFilesReturnServerPaths() throws Exception
	{
		write("docs/one.txt", new byte[0]);
		write("docs/two.txt", new byte[0]);
		RemoteFile docs = remote("docs");

		String[] names = docs.list();
		Arrays.sort(names);
		assertArrayEquals(new String[] { "/docs/one.txt", "/docs/two.txt" }, names);

		IAbstractFile[] files = docs.listFiles();
		assertEquals(2, files.length);
		assertInstanceOf(RemoteFile.class, files[0]);
	}

	@Test
	public void listAndListFilesOfAFileAreNull() throws Exception
	{
		write("a.txt", new byte[0]);
		assertNull(remote("a.txt").list());
		assertNull(remote("a.txt").listFiles());
	}

	@Test
	public void folderContentCanBeFilteredByExtension() throws Exception
	{
		write("a.txt", new byte[0]);
		write("b.pdf", new byte[0]);
		write("c.TXT", new byte[0]);

		RemoteFile[] txt = new RemoteFile(main, main, null).getFolderContent(new String[] { ".txt" }, AbstractFile.ALL, AbstractFile.ALL,
			AbstractFile.ALL);

		String[] names = Arrays.stream(txt).map(RemoteFile::getName).sorted().toArray(String[]::new);
		assertArrayEquals(new String[] { "a.txt", "c.TXT" }, names);
	}

	@Test
	public void folderContentCanBeFilteredOnFilesOrFolders() throws Exception
	{
		write("a.txt", new byte[0]);
		Files.createDirectory(main.toPath().resolve("sub"));
		RemoteFile root = new RemoteFile(main, main, null);

		RemoteFile[] files = root.getFolderContent(null, AbstractFile.FILES, AbstractFile.ALL, AbstractFile.ALL);
		RemoteFile[] folders = root.getFolderContent(null, AbstractFile.FOLDERS, AbstractFile.ALL, AbstractFile.ALL);

		assertEquals(1, files.length);
		assertEquals("a.txt", files[0].getName());
		assertEquals(1, folders.length);
		assertEquals("sub", folders[0].getName());
	}

	@Test
	public void folderContentCanBeFilteredOnVisibility() throws Exception
	{
		write("visible.txt", new byte[0]);
		RemoteFile root = new RemoteFile(main, main, null);

		assertEquals(1, root.getFolderContent(null, AbstractFile.ALL, AbstractFile.VISIBLE, AbstractFile.ALL).length);
		assertEquals(0, root.getFolderContent(null, AbstractFile.ALL, AbstractFile.NON_VISIBLE, AbstractFile.ALL).length);
	}

	@Test
	public void folderContentCanBeFilteredOnLocked() throws Exception
	{
		write("open.txt", new byte[0]);
		File locked = write("locked.txt", new byte[0]);
		assertTrue(locked.setReadOnly());
		RemoteFile root = new RemoteFile(main, main, null);
		try
		{
			RemoteFile[] lockedFiles = root.getFolderContent(null, AbstractFile.ALL, AbstractFile.ALL, AbstractFile.LOCKED);
			RemoteFile[] openFiles = root.getFolderContent(null, AbstractFile.ALL, AbstractFile.ALL, AbstractFile.NON_LOCKED);

			assertEquals(1, lockedFiles.length);
			assertEquals("locked.txt", lockedFiles[0].getName());
			assertEquals(1, openFiles.length);
			assertEquals("open.txt", openFiles[0].getName());
		}
		finally
		{
			locked.setWritable(true);
		}
	}

	@Test
	public void folderContentOfASingleFileReturnsItWhenTheFiltersMatch() throws Exception
	{
		File a = write("a.txt", new byte[0]);
		RemoteFile f = remote("a.txt");

		assertEquals(1, f.getFolderContent(null, AbstractFile.ALL, AbstractFile.ALL, AbstractFile.ALL).length);
		assertEquals(1, f.getFolderContent(null, AbstractFile.FILES, AbstractFile.VISIBLE, AbstractFile.NON_LOCKED).length);
		assertNull(f.getFolderContent(null, AbstractFile.FOLDERS, AbstractFile.ALL, AbstractFile.ALL));
		assertNull(f.getFolderContent(null, AbstractFile.ALL, AbstractFile.NON_VISIBLE, AbstractFile.ALL));
		assertNull(f.getFolderContent(null, AbstractFile.ALL, AbstractFile.ALL, AbstractFile.LOCKED));
		assertTrue(a.setReadOnly());
		try
		{
			assertEquals(1, f.getFolderContent(null, AbstractFile.ALL, AbstractFile.ALL, AbstractFile.LOCKED).length);
		}
		finally
		{
			a.setWritable(true);
		}
	}

	@Test
	public void folderContentOfAMissingFileIsNull()
	{
		assertNull(remote("missing.txt").getFolderContent(null, AbstractFile.ALL, AbstractFile.ALL, AbstractFile.ALL));
	}

	@Test
	public void remoteUrlPointsToTheFileServiceOfTheServer() throws Exception
	{
		write("docs/my file.pdf", PDF);
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(access.getServerURL()).thenReturn(new URL("http://localhost:8080/ctx"));

		String url = new RemoteFile(new File(main, "docs/my file.pdf"), main, access).getRemoteUrl();

		assertEquals("http://localhost:8080/ctx/servoy-service/file/docs/my%20file.pdf", url);
	}

	@Test
	public void remoteUrlKeepsASingleSlashAfterTheServerPath() throws Exception
	{
		write("a.pdf", PDF);
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(access.getServerURL()).thenReturn(new URL("https://example.com/"));

		assertEquals("https://example.com/servoy-service/file/a.pdf", new RemoteFile(new File(main, "a.pdf"), main, access).getRemoteUrl());
	}

	@Test
	public void remoteUrlOfAMissingFileFails()
	{
		assertThrows(RuntimeException.class, () -> remote("missing.pdf").getRemoteUrl());
	}

	@Test
	public void toStringAndHashCodeDelegateToTheServerFile()
	{
		RemoteFile f = remote("a.txt");
		assertEquals(new File(main, "a.txt").toString(), f.toString());
		assertEquals(new File(main, "a.txt").hashCode(), f.hashCode());
	}

	@Test
	public void equalsComparesWithTheWrappedFile()
	{
		RemoteFile f = remote("a.txt");
		// current behaviour, same pattern as LocalFile had: equal to the java.io.File, not to another RemoteFile
		assertTrue(f.equals(new File(main, "a.txt")));
		assertFalse(f.equals(remote("a.txt")));
	}
}
