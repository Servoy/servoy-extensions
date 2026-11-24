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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.j2db.plugins.IAllWebClientPluginAccess;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IUploadData;

public class UploadDataTest
{
	private static final byte[] PDF = "%PDF-1.4 body".getBytes(StandardCharsets.US_ASCII);

	@TempDir
	Path tempDir;

	private IUploadData upload;

	@BeforeEach
	public void setUp()
	{
		upload = mock(IUploadData.class);
		when(upload.getName()).thenReturn("doc.pdf");
		when(upload.getBytes()).thenReturn(PDF);
		when(Long.valueOf(upload.lastModified())).thenReturn(Long.valueOf(42L));
	}

	@Test
	public void delegatesToTheUpload()
	{
		InputStream in = new ByteArrayInputStream(PDF);
		when(upload.getInputStream()).thenReturn(in);
		UploadData data = new UploadData(upload, null);

		assertEquals("doc.pdf", data.getName());
		assertArrayEquals(PDF, data.getBytes());
		assertEquals(42L, data.lastModified());
		assertSame(in, data.getInputStream());
		assertNull(data.getFile());
	}

	@Test
	public void contentTypeOfTheUploadIsPreferred()
	{
		when(upload.getContentType()).thenReturn("text/plain");
		assertEquals("text/plain", new UploadData(upload, null).getContentType());
	}

	@Test
	public void contentTypeIsDetectedWhenTheUploadHasNone()
	{
		assertEquals("application/pdf", new UploadData(upload, null).getContentType());
	}

	@Test
	public void sizeIsTheByteLengthWithoutAFile()
	{
		assertEquals(PDF.length, new UploadData(upload, null).size());
	}

	@Test
	public void sizeAndPathComeFromTheFileWhenThereIsOne() throws Exception
	{
		Path p = Files.write(tempDir.resolve("u.bin"), new byte[] { 1, 2, 3, 4, 5 });
		when(upload.getFile()).thenReturn(p.toFile());
		UploadData data = new UploadData(upload, null);

		assertEquals(5, data.size());
		assertEquals(p.toFile().getAbsolutePath(), data.getAbsolutePath());
		assertTrue(data.exists());
	}

	@Test
	public void renameMovesTheBackingFileToALocalFile() throws Exception
	{
		Path p = Files.write(tempDir.resolve("u.bin"), PDF);
		when(upload.getFile()).thenReturn(p.toFile());
		File target = tempDir.resolve("moved.bin").toFile();

		assertTrue(new UploadData(upload, null).renameTo(new LocalFile(target, null)));
		assertTrue(target.exists());
		assertFalse(p.toFile().exists());
	}

	@Test
	public void renameFailsWithoutFileOrForNonLocalTargets() throws Exception
	{
		assertFalse(new UploadData(upload, null).renameTo(new LocalFile(tempDir.resolve("x").toFile(), null)));

		when(upload.getFile()).thenReturn(Files.write(tempDir.resolve("u.bin"), PDF).toFile());
		assertFalse(new UploadData(upload, null).renameTo(mock(IAbstractFile.class)));
	}

	@Test
	public void setBytesIsNotSupported()
	{
		UploadData data = new UploadData(upload, null);
		assertThrows(UnsupportedMethodException.class, () -> data.setBytes(new byte[0], true));
		assertThrows(UnsupportedMethodException.class, () -> data.setBytes(new byte[0]));
	}

	@Test
	public void toStringShowsNameAndContentType()
	{
		assertEquals("UploadData[name:doc.pdf,contenttype:application/pdf]", new UploadData(upload, null).toString());
	}

	@Test
	public void remoteUrlIsServedForAnExistingFileInAWebClient() throws Exception
	{
		File f = Files.write(tempDir.resolve("u.pdf"), PDF).toFile();
		when(upload.getFile()).thenReturn(f);
		IAllWebClientPluginAccess web = mock(IAllWebClientPluginAccess.class);
		when(web.serveResource(eq(f), any(), eq("inline"))).thenReturn("resources/u.pdf");

		assertEquals("resources/u.pdf", new UploadData(upload, web).getRemoteUrl());
	}

	@Test
	public void remoteUrlIsNullWithoutFileOrWebClient() throws Exception
	{
		IAllWebClientPluginAccess web = mock(IAllWebClientPluginAccess.class);
		assertNull(new UploadData(upload, web).getRemoteUrl());
		verifyNoInteractions(web);

		when(upload.getFile()).thenReturn(Files.write(tempDir.resolve("u.pdf"), PDF).toFile());
		assertNull(new UploadData(upload, mock(IClientPluginAccess.class)).getRemoteUrl());
	}
}
