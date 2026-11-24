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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.j2db.plugins.IAllWebClientPluginAccess;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IUploadData;

public class JSFileTest
{
	private static final byte[] PDF = "%PDF-1.4 body".getBytes(StandardCharsets.US_ASCII);

	@TempDir
	Path tempDir;

	private JSFile js(String name)
	{
		return new JSFile(tempDir.resolve(name).toFile(), null);
	}

	private JSFile existing(String name, byte[] content) throws Exception
	{
		Path p = tempDir.resolve(name);
		Files.createDirectories(p.getParent());
		Files.write(p, content);
		return new JSFile(p.toFile(), null);
	}

	@Test
	public void localFileConstructorWrapsALocalFile()
	{
		JSFile f = js("a.txt");

		assertInstanceOf(LocalFile.class, f.getAbstractFile());
		assertEquals(tempDir.resolve("a.txt").toFile(), f.getFile());
	}

	@Test
	public void uploadConstructorWrapsPlainUploadsInUploadData()
	{
		IUploadData upload = mock(IUploadData.class);
		assertInstanceOf(UploadData.class, new JSFile(upload, null).getAbstractFile());
	}

	@Test
	public void uploadConstructorKeepsAbstractFilesAsIs()
	{
		UploadData data = new UploadData(mock(IUploadData.class), null);
		assertSame(data, new JSFile(data, null).getAbstractFile());
	}

	@Test
	public void defaultConstructorHasNoFile()
	{
		assertNull(new JSFile().getFile());
	}

	@Test
	public void pathPropertiesDelegateToTheFile() throws Exception
	{
		JSFile f = existing("sub/b.txt", "hello".getBytes(StandardCharsets.UTF_8));
		File file = tempDir.resolve("sub/b.txt").toFile();

		assertEquals("b.txt", f.js_getName());
		assertEquals("b.txt", f.getName());
		assertEquals(file.getParent(), f.js_getParent());
		assertEquals(file.getPath(), f.js_getPath());
		assertEquals(file.getAbsolutePath(), f.js_getAbsolutePath());
		assertTrue(f.js_isAbsolute());
		assertEquals(file.getParentFile(), f.js_getParentFile().getFile());
		assertEquals(file.getAbsoluteFile(), f.js_getAbsoluteFile().getFile());
	}

	@Test
	public void statePropertiesDelegateToTheFile() throws Exception
	{
		JSFile f = existing("c.txt", "hello".getBytes(StandardCharsets.UTF_8));

		assertTrue(f.js_exists());
		assertTrue(f.js_canRead());
		assertTrue(f.js_canWrite());
		assertTrue(f.js_isFile());
		assertFalse(f.js_isDirectory());
		assertFalse(f.js_isHidden());
		assertEquals(5, f.js_size());
		assertEquals(5, f.getSize());
		assertEquals(new Date(tempDir.resolve("c.txt").toFile().lastModified()), f.js_lastModified());
	}

	@Test
	public void bytesAndInputStreamReadTheContent() throws Exception
	{
		JSFile f = existing("d.bin", new byte[] { 1, 2, 3 });

		assertArrayEquals(new byte[] { 1, 2, 3 }, f.jsFunction_getBytes());
		try (InputStream in = f.getInputStream())
		{
			assertArrayEquals(new byte[] { 1, 2, 3 }, in.readAllBytes());
		}
	}

	@Test
	public void contentTypeIsDetected() throws Exception
	{
		JSFile f = existing("e.pdf", PDF);

		assertEquals("application/pdf", f.js_getContentType());
		assertEquals("application/pdf", f.getContentType());
	}

	@Test
	public void createAndDeleteWorkOnDisk() throws Exception
	{
		JSFile f = js("f.txt");

		assertTrue(f.js_createNewFile());
		assertFalse(f.js_createNewFile());
		assertTrue(f.js_deleteFile());
		assertFalse(f.js_exists());

		assertTrue(f.js_createNewFile());
		assertTrue(f.js_delete());
		assertFalse(f.js_exists());
	}

	@Test
	public void mkdirAndMkdirsCreateFolders()
	{
		assertTrue(js("one").js_mkdir());
		assertFalse(js("x/y/z").js_mkdir());
		assertTrue(js("x/y/z").js_mkdirs());
		assertTrue(js("x/y/z").js_isDirectory());
	}

	@Test
	public void listAndListFilesReturnTheFolderContent() throws Exception
	{
		existing("dir/one.txt", new byte[0]);
		existing("dir/two.txt", new byte[0]);
		JSFile dir = js("dir");

		String[] names = dir.js_list();
		Arrays.sort(names);
		assertArrayEquals(new String[] { "one.txt", "two.txt" }, names);

		JSFile[] files = dir.js_listFiles();
		assertEquals(2, files.length);
		assertNotNull(files[0].getFile());
	}

	@Test
	public void listFilesOfAFileIsEmpty() throws Exception
	{
		assertEquals(0, existing("g.txt", new byte[0]).js_listFiles().length);
	}

	@Test
	public void listFilesWithoutResultFromTheAbstractFileReturnsNull()
	{
		// AbstractFile.listFiles() returns null (e.g. an upload) - JSFile then returns its (uninitialised) EMPTY array
		JSFile upload = new JSFile(mock(IUploadData.class), null);
		assertNull(upload.js_listFiles());
	}

	@Test
	public void renameToAJSFile() throws Exception
	{
		JSFile f = existing("from.txt", new byte[] { 1 });

		assertTrue(f.js_renameTo(js("to.txt")));
		assertTrue(Files.exists(tempDir.resolve("to.txt")));
	}

	@Test
	public void renameToAPathString() throws Exception
	{
		JSFile f = existing("from2.txt", new byte[] { 1 });

		assertTrue(f.js_renameTo(tempDir.resolve("to2.txt").toString()));
		assertTrue(Files.exists(tempDir.resolve("to2.txt")));
		assertFalse(Files.exists(tempDir.resolve("from2.txt")));
	}

	@Test
	public void renameToAnUnsupportedTypeFails() throws Exception
	{
		assertFalse(existing("from3.txt", new byte[] { 1 }).js_renameTo(Integer.valueOf(1)));
	}

	@Test
	public void renameOfARemoteFileUsesTheServerPath() throws Exception
	{
		File main = Files.createDirectory(tempDir.resolve("server")).toFile();
		File remote = Files.write(main.toPath().resolve("r.txt"), new byte[] { 1 }).toFile();
		JSFile f = new JSFile(new RemoteFile(remote, main, null), null);

		assertTrue(f.js_renameTo("/renamed.txt"));
		assertTrue(new File(main, "renamed.txt").exists());
		assertThrows(IllegalArgumentException.class, () -> f.js_renameTo("no-leading-slash.txt"));
	}

	@Test
	public void setLastModifiedAcceptsDateAndNumber() throws Exception
	{
		JSFile f = existing("h.txt", new byte[0]);

		assertTrue(f.js_setLastModified(new Date(1_000_000_000_000L)));
		assertEquals(1_000_000_000_000L, f.js_lastModified().getTime());

		assertTrue(f.js_setLastModified(Long.valueOf(1_100_000_000_000L)));
		assertEquals(1_100_000_000_000L, f.js_lastModified().getTime());
	}

	@Test
	public void setLastModifiedRejectsOtherTypes() throws Exception
	{
		JSFile f = existing("i.txt", new byte[0]);
		assertFalse(f.js_setLastModified("yesterday"));
		assertFalse(f.js_setLastModified(null));
	}

	@Test
	public void setReadOnlyMakesTheFileUnwritable() throws Exception
	{
		JSFile f = existing("j.txt", new byte[0]);

		assertTrue(f.js_setReadOnly());
		assertFalse(f.js_canWrite());
		f.getFile().setWritable(true);
	}

	@Test
	public void setBytesWritesOnlyExistingFilesUnlessAskedToCreate() throws Exception
	{
		JSFile f = js("k.txt");

		assertFalse(f.js_setBytes(new byte[] { 1 }));
		assertFalse(f.js_exists());
		assertTrue(f.js_setBytes(new byte[] { 1, 2 }, true));
		assertArrayEquals(new byte[] { 1, 2 }, Files.readAllBytes(tempDir.resolve("k.txt")));
		assertTrue(f.js_setBytes(new byte[] { 3 }));
		assertArrayEquals(new byte[] { 3 }, Files.readAllBytes(tempDir.resolve("k.txt")));
	}

	@Test
	public void remoteUrlComesFromTheWebClient() throws Exception
	{
		File file = Files.write(tempDir.resolve("l.pdf"), PDF).toFile();
		IAllWebClientPluginAccess web = mock(IAllWebClientPluginAccess.class);
		when(web.serveResource(any(File.class), anyString(), eq("inline"))).thenReturn("resources/l.pdf");

		assertEquals("resources/l.pdf", new JSFile(file, web).js_getRemoteUrl());
		assertNull(js("missing.pdf").js_getRemoteUrl());
	}

	@Test
	public void toStringOfALocalFileIsThePath()
	{
		JSFile f = js("m.txt");
		assertEquals(tempDir.resolve("m.txt").toFile().toString(), f.toString());
	}

	@Test
	public void toStringOfAnUploadShowsNameAndContentType()
	{
		IUploadData upload = mock(IUploadData.class);
		when(upload.getName()).thenReturn("up.pdf");
		when(upload.getBytes()).thenReturn(PDF);

		assertEquals("JSFile[name:up.pdf,contenttype:application/pdf]", new JSFile(upload, null).toString());
	}

	@Test
	public void convertToRemoteCopiesTheContentIntoTheServerFolder() throws Exception
	{
		File main = Files.createDirectory(tempDir.resolve("server")).toFile();
		IFileService service = mock(IFileService.class);
		when(service.getDefaultFolder("client-1")).thenReturn(main);
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(access.getClientID()).thenReturn("client-1");
		when(access.getRemoteService(IFileService.class.getName())).thenReturn(service);
		JSFile local = new JSFile(Files.write(tempDir.resolve("n.txt"), "content".getBytes(StandardCharsets.UTF_8)).toFile(), access);

		JSFile remote = local.js_convertToRemote("copy.txt");
		assertNotNull(remote);

		assertInstanceOf(RemoteFile.class, remote.getAbstractFile());
		assertEquals("/copy.txt", remote.js_getAbsolutePath());
		assertEquals("content", Files.readString(main.toPath().resolve("copy.txt")));
	}

	private IClientPluginAccess accessWithServerFolder(File main) throws Exception
	{
		IFileService service = mock(IFileService.class);
		when(service.getDefaultFolder(any())).thenReturn(main);
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(access.getRemoteService(IFileService.class.getName())).thenReturn(service);
		return access;
	}

	@Test
	public void convertToRemoteOfAMissingSourceGivesAnEmptyRemoteFileWithoutCreatingTheSource() throws Exception
	{
		File main = Files.createDirectory(tempDir.resolve("server")).toFile();
		JSFile local = new JSFile(tempDir.resolve("new.txt").toFile(), accessWithServerFolder(main));

		JSFile remote = local.js_convertToRemote("empty.txt");

		assertFalse(local.js_exists());
		assertTrue(remote.js_exists());
		assertEquals(0, remote.js_size());
	}

	@Test
	public void convertToRemoteOfAMissingSourceEmptiesAnExistingRemoteFile() throws Exception
	{
		File main = Files.createDirectory(tempDir.resolve("server")).toFile();
		Files.writeString(main.toPath().resolve("old.txt"), "old content");
		JSFile local = new JSFile(tempDir.resolve("new.txt").toFile(), accessWithServerFolder(main));

		JSFile remote = local.js_convertToRemote("old.txt");

		assertEquals(0, remote.js_size());
	}

	@Test
	public void convertToRemoteWithoutServerFolderReturnsNullAndWritesNothing() throws Exception
	{
		JSFile local = existing("o.txt", "content".getBytes(StandardCharsets.UTF_8));
		JSFile withoutFolder = new JSFile(local.getFile(), accessWithServerFolder(null));
		String fileName = "svy21823-" + System.nanoTime() + ".txt";

		assertNull(withoutFolder.js_convertToRemote(fileName));
		// before the fix the copy ended up relative to the working directory
		assertFalse(new File(fileName).exists());
	}

	@Test
	public void convertToRemoteReturnsNullWhenTheRemoteServiceFails() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(access.getRemoteService(IFileService.class.getName())).thenThrow(new IllegalStateException("no server"));
		JSFile local = new JSFile(existing("p.txt", new byte[] { 1 }).getFile(), access);

		assertNull(local.js_convertToRemote("p.txt"));
	}
}
