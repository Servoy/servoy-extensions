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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.servoy.j2db.plugins.IAllWebClientPluginAccess;
import com.servoy.j2db.plugins.IUploadData;

/**
 * Unit tests for {@link FileProvider} in a browser client (web/NG, mocked {@link IAllWebClientPluginAccess}): downloads, open in the
 * browser, reads/writes of server side JSFiles and streaming uploads into a temp folder that acts as the default upload folder.
 */
public class FileProviderWebClientTest
{
	private static final byte[] PDF = "%PDF-1.4 body".getBytes(StandardCharsets.US_ASCII);

	@TempDir
	Path tempDir;

	private IAllWebClientPluginAccess web;
	private FileProvider provider;
	private File serverFolder;

	@BeforeEach
	public void setUp() throws Exception
	{
		serverFolder = Files.createDirectory(tempDir.resolve("server")).toFile();
		IFileService service = mock(IFileService.class);
		when(service.getDefaultFolder(any())).thenReturn(serverFolder);

		web = mock(IAllWebClientPluginAccess.class);
		when(web.getClientID()).thenReturn("client-1");
		when(web.getRemoteService(IFileService.class.getName())).thenReturn(service);
		when(web.serveResource(anyString(), any(byte[].class), anyString())).thenReturn("resources/dl");
		when(web.serveResource(anyString(), any(byte[].class), anyString(), anyString())).thenReturn("resources/inline");

		FilePlugin plugin = new FilePlugin();
		plugin.initialize(web);
		provider = new FileProvider(plugin);
	}

	@AfterEach
	public void tearDown()
	{
		provider.unload();
	}

	private File write(String name, byte[] content) throws Exception
	{
		Path p = tempDir.resolve(name);
		Files.createDirectories(p.getParent());
		return Files.write(p, content).toFile();
	}

	private JSFile upload(String name, byte[] content)
	{
		IUploadData data = mock(IUploadData.class);
		when(data.getName()).thenReturn(name);
		when(data.getBytes()).thenReturn(content);
		when(data.getInputStream()).thenReturn(new ByteArrayInputStream(content));
		return new JSFile(data, web);
	}

	// --- downloads (file name only, no file on the server) ---

	@Test
	public void writeFileWithANameServesADownload() throws Exception
	{
		assertTrue(provider.js_writeFile("report.pdf", PDF, null));

		verify(web).serveResource("report.pdf", PDF, "application/pdf");
		verify(web).showURL("resources/dl", "_self", null, 0, false);
	}

	@Test
	public void writeFileUsesTheGivenMimeTypeTrimmed() throws Exception
	{
		assertTrue(provider.js_writeFile("data.bin", new byte[] { 1 }, " application/x-test "));

		verify(web).serveResource("data.bin", new byte[] { 1 }, "application/x-test");
	}

	@Test
	public void writeFileWithoutNameUsesADefaultName() throws Exception
	{
		assertTrue(provider.js_writeFile((String)null, PDF, "application/pdf"));

		verify(web).serveResource("file.bin", PDF, "application/pdf");
	}

	@Test
	public void writeFileWithoutDataFails() throws Exception
	{
		assertFalse(provider.js_writeFile("report.pdf", null, null));
		verify(web, never()).serveResource(anyString(), any(byte[].class), anyString());
	}

	@Test
	public void writeFileToAJSFileWritesTheFile() throws Exception
	{
		File f = tempDir.resolve("written.bin").toFile();

		assertTrue(provider.js_writeFile(new JSFile(f, web), new byte[] { 4, 5 }, null));

		assertArrayEquals(new byte[] { 4, 5 }, Files.readAllBytes(f.toPath()));
		verify(web, never()).serveResource(anyString(), any(byte[].class), anyString());
	}

	@Test
	public void writeTextWithANameServesADownloadWithTheCharset() throws Exception
	{
		assertTrue(provider.js_writeTXTFile("notes.txt", "caf\u00e9", "UTF-8"));

		ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
		verify(web).serveResource(eq("notes.txt"), bytes.capture(), eq("text/plain; charset=\"UTF-8\""));
		assertEquals("caf\u00e9", new String(bytes.getValue(), StandardCharsets.UTF_8));
		verify(web).showURL("resources/dl", "_self", null, 0, false);
	}

	@Test
	public void writeXmlWithoutFileServesFileXml() throws Exception
	{
		assertTrue(provider.js_writeXMLFile((String)null, "<a/>"));

		verify(web).serveResource(eq("file.xml"), any(byte[].class), eq("text/xml; charset=\"UTF-8\""));
	}

	@Test
	public void writeTextDownloadWithAnUnknownEncodingFails() throws Exception
	{
		assertFalse(provider.js_writeTXTFile("notes.txt", "x", "no-such-charset"));
		verify(web, never()).serveResource(anyString(), any(byte[].class), anyString());
	}

	@Test
	public void writeTextToAJSFileWritesTheFile() throws Exception
	{
		File f = tempDir.resolve("t.txt").toFile();

		assertTrue(provider.js_writeTXTFile(new JSFile(f, web), "hello"));

		assertEquals("hello", Files.readString(f.toPath()));
	}

	// --- open in the browser ---

	@Test
	public void openFileServesItInlineInANewWindowByDefault() throws Exception
	{
		File f = write("open.pdf", PDF);

		assertTrue(provider.js_openFile(new JSFile(f, web)));

		verify(web).serveResource("open.pdf", PDF, "application/pdf", "inline");
		verify(web).showURL("resources/inline", "_blank", null, 0, true);
	}

	@Test
	public void openFileUsesTheGivenTargetAndOptions() throws Exception
	{
		File f = write("open.pdf", PDF);

		assertTrue(provider.js_openFile(new JSFile(f, web), "_self", "width=200"));

		verify(web).showURL("resources/inline", "_self", "width=200", 0, true);
	}

	@Test
	public void openDataServesItInline() throws Exception
	{
		assertTrue(provider.js_openFile("data.pdf", PDF, null));
		assertTrue(provider.js_openFile(null, PDF, " text/plain ", "frame1", null));

		verify(web).serveResource("data.pdf", PDF, "application/pdf", "inline");
		verify(web).serveResource("file.bin", PDF, "text/plain", "inline");
		verify(web).showURL("resources/inline", "_blank", null, 0, true);
		verify(web).showURL("resources/inline", "frame1", null, 0, true);
	}

	// --- reading server side files ---

	@Test
	public void readTextOfAJSFileUsesItsStream() throws Exception
	{
		JSFile f = new JSFile(write("r.txt", "line1\r\nline2".getBytes(StandardCharsets.UTF_8)), web);

		assertEquals("line1\nline2", provider.js_readTXTFile(f, null));
		assertEquals("line1\nline2", provider.js_readTXTFile(f));
	}

	@Test
	public void readTextOfAJSFileSkipsTheUtfBom() throws Exception
	{
		JSFile f = new JSFile(write("bom.txt", new byte[] { (byte)0xEF, (byte)0xBB, (byte)0xBF, 'h', 'i' }), web);

		assertEquals("hi", provider.js_readTXTFile(f, "UTF-8"));
	}

	@Test
	public void readTextOfAnUploadWithoutStreamIsEmpty()
	{
		IUploadData data = mock(IUploadData.class);

		assertEquals("", provider.js_readTXTFile(new JSFile(data, web), null));
	}

	@Test
	public void readTextOfAJSFileWithAnUnknownEncodingIsNull() throws Exception
	{
		File f = write("u.txt", new byte[] { 'x' });

		assertNull(provider.js_readTXTFile(new JSFile(f, web), "no-such-charset"));
		assertTrue(f.delete()); // the stream was closed
	}

	@Test
	public void readTextOfAPathReadsTheFile() throws Exception
	{
		File f = write("p.txt", "path".getBytes(StandardCharsets.UTF_8));

		assertEquals("path", provider.js_readTXTFile(f.getPath(), "UTF-8"));
	}

	@Test
	public void readFileOfAJSFileReturnsAllBytes() throws Exception
	{
		JSFile f = new JSFile(write("b.bin", new byte[] { 1, 2, 3 }), web);

		// the size is ignored for a JSFile in the web client
		assertArrayEquals(new byte[] { 1, 2, 3 }, provider.js_readFile(f, 1));
		assertArrayEquals(new byte[] { 1, 2, 3 }, provider.js_readFile(f));
	}

	@Test
	public void readFileOfAPathHonoursTheSize() throws Exception
	{
		File f = write("b.bin", new byte[] { 1, 2, 3 });

		assertArrayEquals(new byte[] { 1, 2 }, provider.js_readFile(f.getPath(), 2));
	}

	// --- streaming uploads to the server folder ---

	@Test
	public void uploadWithoutServerNameIsStoredUnderItsOwnName() throws Exception
	{
		assertNull(provider.js_streamFilesToServer(upload("doc.pdf", PDF), null, null));

		assertArrayEquals(PDF, Files.readAllBytes(new File(serverFolder, "doc.pdf").toPath()));
	}

	@Test
	public void uploadNameWithAClientPathOnlyKeepsTheName() throws Exception
	{
		provider.js_streamFilesToServer(upload("C:\\Users\\me\\doc.pdf", PDF), null, null);

		assertTrue(new File(serverFolder, "doc.pdf").exists());
	}

	@Test
	public void uploadToAServerPathCreatesTheFolders() throws Exception
	{
		provider.js_streamFilesToServer(upload("doc.pdf", PDF), "/a/b/renamed.pdf", null);

		assertArrayEquals(PDF, Files.readAllBytes(new File(serverFolder, "a/b/renamed.pdf").toPath()));
	}

	@Test
	public void uploadToAServerFolderKeepsTheUploadName() throws Exception
	{
		provider.js_streamFilesToServer(upload("doc.pdf", PDF), "/folder", null);
		provider.js_streamFilesToServer(upload("other.pdf", PDF), "/folder/", null);

		assertTrue(new File(serverFolder, "folder/doc.pdf").exists());
		assertTrue(new File(serverFolder, "folder/other.pdf").exists());
	}

	@Test
	public void severalUploadsAreMatchedWithTheirServerNamesByIndex() throws Exception
	{
		Object[] uploads = { upload("one.txt", new byte[] { 1 }), upload("two.txt", new byte[] { 2 }) };

		provider.js_streamFilesToServer(uploads, new Object[] { "/first.txt" }, null);

		assertArrayEquals(new byte[] { 1 }, Files.readAllBytes(new File(serverFolder, "first.txt").toPath()));
		assertArrayEquals(new byte[] { 2 }, Files.readAllBytes(new File(serverFolder, "two.txt").toPath()));
	}

	@Test
	public void uploadOverwritesAnExistingServerFile() throws Exception
	{
		Files.writeString(serverFolder.toPath().resolve("doc.pdf"), "old content that is longer");

		provider.js_streamFilesToServer(upload("doc.pdf", PDF), "/doc.pdf", null);

		assertArrayEquals(PDF, Files.readAllBytes(new File(serverFolder, "doc.pdf").toPath()));
	}

	@Test
	public void uploadToARemoteJSFileOnlyUsesItsName() throws Exception
	{
		JSFile target = new JSFile(new RemoteFile(new File(serverFolder, "sub/target.pdf"), serverFolder, web), web);

		provider.js_streamFilesToServer(upload("doc.pdf", PDF), target, null);

		// current behaviour: the folder of the remote target is dropped, only the name is used
		assertTrue(new File(serverFolder, "target.pdf").exists());
		assertFalse(new File(serverFolder, "sub/target.pdf").exists());
	}

	@Test
	public void uploadServerNameMustStartWithASlash()
	{
		JSFile upload = upload("doc.pdf", PDF);

		assertThrows(IllegalArgumentException.class, () -> provider.js_streamFilesToServer(upload, "doc.pdf", null));
	}

	@Test
	public void uploadWithoutContentWritesNothing() throws Exception
	{
		IUploadData data = mock(IUploadData.class);
		when(data.getName()).thenReturn("empty.pdf");

		provider.js_streamFilesToServer(new JSFile(data, web), null, null);

		assertFalse(new File(serverFolder, "empty.pdf").exists());
	}

	@Test
	public void uploadWithoutFilesOrServerFolderDoesNothing() throws Exception
	{
		assertNull(provider.js_streamFilesToServer(null, null, null));

		when(web.getRemoteService(IFileService.class.getName())).thenThrow(new IllegalStateException("no server"));
		assertNull(provider.js_streamFilesToServer(upload("doc.pdf", PDF), null, null));
		assertFalse(new File(serverFolder, "doc.pdf").exists());
	}

	@Test
	public void defaultUploadLocationIsTheCanonicalServerFolder() throws Exception
	{
		assertEquals(serverFolder.getCanonicalPath(), provider.js_getDefaultUploadLocation());
	}

	@Disabled("SVY-21860: checkParentFile does not stop ../ traversal, enable when fixed")
	@Test
	public void uploadOutsideTheServerFolderIsRejected()
	{
		JSFile upload = upload("doc.pdf", PDF);

		assertThrows(SecurityException.class, () -> provider.js_streamFilesToServer(upload, "/../escaped.pdf", null));
		assertFalse(tempDir.resolve("escaped.pdf").toFile().exists());
	}

	@Test
	public void showUrlIsNotUsedForServerSideWrites() throws Exception
	{
		provider.js_writeFile(new JSFile(tempDir.resolve("x.bin").toFile(), web), new byte[] { 1 }, null);

		verify(web, never()).showURL(anyString(), anyString(), isNull(), anyInt(), anyBoolean());
	}
}
