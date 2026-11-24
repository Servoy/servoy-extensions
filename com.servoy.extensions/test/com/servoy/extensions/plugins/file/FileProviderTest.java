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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IUploadData;

/**
 * Unit tests for the local file operations of {@link FileProvider}: copy, move, delete, folders, size/date,
 * folder contents, text/xml/binary read and write, temp files and remote folder contents.
 * Only paths that never open a Swing file chooser are exercised (the chooser is only shown for a missing
 * file or when no file is given).
 */
public class FileProviderTest
{
	@TempDir
	Path tempDir;

	private IClientPluginAccess access;
	private FileProvider provider;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IClientPluginAccess.class);
		FilePlugin plugin = new FilePlugin();
		plugin.initialize(access);
		provider = new FileProvider(plugin);
	}

	@AfterEach
	public void tearDown()
	{
		provider.unload();
	}

	private File file(String name)
	{
		return tempDir.resolve(name).toFile();
	}

	private File write(String name, String content) throws Exception
	{
		Path p = tempDir.resolve(name);
		Files.createDirectories(p.getParent());
		return Files.writeString(p, content, StandardCharsets.UTF_8).toFile();
	}

	private static String read(File f) throws Exception
	{
		return Files.readString(f.toPath(), StandardCharsets.UTF_8);
	}

	// --- conversion ---

	@Test
	public void convertToJSFileAcceptsTheSupportedTypes()
	{
		JSFile js = new JSFile(file("a.txt"), access);

		assertSame(js, provider.js_convertToJSFile(js));
		assertEquals(file("a.txt"), provider.js_convertToJSFile(file("a.txt")).getFile());
		assertEquals(file("a.txt"), provider.js_convertToJSFile(file("a.txt").getPath()).getFile());
		assertInstanceOf(UploadData.class, provider.js_convertToJSFile(mock(IUploadData.class)).getAbstractFile());
		assertNull(provider.js_convertToJSFile(null));
		assertEquals(file("b.txt"), provider.js_createFile(file("b.txt").getPath()).getFile());
		assertEquals(file("c.txt"), provider.js_convertStringToJSFile(file("c.txt").getPath()).getFile());
		assertFalse(file("b.txt").exists());
	}

	@Test
	public void homeFolderIsTheUserHome()
	{
		assertEquals(new File(System.getProperty("user.home")), provider.js_getHomeFolder().getFile());
		assertEquals(new File(System.getProperty("user.home")), provider.js_getHomeDirectory().getFile());
	}

	@Test
	public void diskListContainsTheFileSystemRoots()
	{
		assertEquals(File.listRoots().length, provider.js_getDiskList().length);
	}

	@Test
	public void returnedTypesAreJSFileAndProgressMonitor()
	{
		assertArrayEquals(new Class< ? >[] { JSFile.class, JSProgressMonitor.class }, provider.getAllReturnedTypes());
	}

	// --- copy / move ---

	@Test
	public void copyFileCreatesMissingParentFolders() throws Exception
	{
		File src = write("src.txt", "data");
		File dest = file("deep/er/dest.txt");

		assertTrue(provider.js_copyFile(src, dest));
		assertEquals("data", read(dest));
		assertTrue(src.exists());
	}

	@Test
	public void copyFileOverwritesAnExistingDestination() throws Exception
	{
		File src = write("src.txt", "new");
		File dest = write("dest.txt", "old content");

		assertTrue(provider.js_copyFile(new JSFile(src, access), dest.getPath()));
		assertEquals("new", read(dest));
	}

	@Test
	public void copyFileRejectsNullOrTheSameFile() throws Exception
	{
		File src = write("src.txt", "data");

		assertFalse(provider.js_copyFile(null, src));
		assertFalse(provider.js_copyFile(src, null));
		assertFalse(provider.js_copyFile(src, src));
		assertEquals("data", read(src));
	}

	@Test
	public void copyFileOfAMissingSourceFails()
	{
		assertFalse(provider.js_copyFile(file("missing.txt"), file("dest.txt")));
	}

	@Test
	public void copyFolderCopiesRecursively() throws Exception
	{
		write("src/a.txt", "a");
		write("src/sub/b.txt", "b");

		assertTrue(provider.js_copyFolder(file("src"), file("dest")));
		assertEquals("a", read(file("dest/a.txt")));
		assertEquals("b", read(file("dest/sub/b.txt")));
	}

	@Test
	public void copyFolderOfAFileCopiesTheFile() throws Exception
	{
		File src = write("one.txt", "x");

		assertTrue(provider.js_copyFolder(src, file("two.txt")));
		assertEquals("x", read(file("two.txt")));
	}

	@Test
	public void copyFolderRejectsInvalidArguments() throws Exception
	{
		write("src/a.txt", "a");
		File destFile = write("destfile.txt", "x");

		assertFalse(provider.js_copyFolder(null, file("dest")));
		assertFalse(provider.js_copyFolder(file("src"), file("src")));
		assertFalse(provider.js_copyFolder(file("missing"), file("dest")));
		assertFalse(provider.js_copyFolder(file("src"), destFile));
	}

	@Test
	public void copyFolderIntoAnExistingFolder() throws Exception
	{
		write("src/a.txt", "a");
		Files.createDirectory(tempDir.resolve("dest"));

		assertTrue(provider.js_copyFolder(file("src"), file("dest")));
		assertTrue(file("dest/a.txt").exists());
	}

	@Test
	public void moveFileRenamesTheFile() throws Exception
	{
		File src = write("from.txt", "data");
		File dest = file("sub/to.txt");

		assertTrue(provider.js_moveFile(src, dest));
		assertFalse(src.exists());
		assertEquals("data", read(dest));
	}

	@Test
	public void moveFileReplacesAnExistingDestination() throws Exception
	{
		File src = write("from.txt", "new");
		File dest = write("to.txt", "old");

		assertTrue(provider.js_moveFile(src.getPath(), new JSFile(dest, access)));
		assertEquals("new", read(dest));
	}

	@Test
	public void moveFileRejectsNullArguments() throws Exception
	{
		assertFalse(provider.js_moveFile(null, file("x.txt")));
		assertFalse(provider.js_moveFile(write("a.txt", "a"), null));
	}

	@Test
	public void moveFileOntoItselfFailsAndKeepsTheFile() throws Exception
	{
		File src = write("self.txt", "data");

		assertFalse(provider.js_moveFile(src, src));
		assertEquals("data", read(src));
	}

	@Test
	public void moveFileOntoTheSameFileUnderAnotherPathKeepsTheFile() throws Exception
	{
		File src = write("self.txt", "data");
		File samePath = new File(tempDir.resolve("sub").toFile(), "../self.txt");

		assertFalse(provider.js_moveFile(src, samePath));
		assertEquals("data", read(src));
	}

	@Test
	public void copyFileOntoTheSameFileUnderAnotherPathKeepsTheFile() throws Exception
	{
		File src = write("self.txt", "data");
		Files.createDirectory(tempDir.resolve("sub"));
		File samePath = new File(tempDir.resolve("sub").toFile(), "../self.txt");

		assertFalse(provider.js_copyFile(src, samePath));
		assertEquals("data", read(src));
	}

	@Test
	public void sameFileDetectionComparesNormalizedPaths() throws Exception
	{
		File f = write("same.txt", "x");
		Files.createDirectory(tempDir.resolve("dir"));

		assertTrue(FileProvider.isSameFile(f, f));
		assertTrue(FileProvider.isSameFile(f, new File(tempDir.resolve("dir").toFile(), "../same.txt")));
		assertTrue(FileProvider.isSameFile(file("missing.txt"), new File(tempDir.resolve("dir").toFile(), "../missing.txt")));
		assertFalse(FileProvider.isSameFile(f, file("other.txt")));
	}

	// --- create / delete ---

	@Test
	public void createFolderCreatesTheHierarchyOrAcceptsAnExistingFolder() throws Exception
	{
		assertTrue(provider.js_createFolder(file("a/b/c")));
		assertTrue(file("a/b/c").isDirectory());
		assertTrue(provider.js_createFolder(file("a/b/c").getPath()));
		assertFalse(provider.js_createFolder(null));
	}

	@Test
	public void createFolderFailsWhenAFileHasThatName() throws Exception
	{
		File f = write("taken", "x");
		assertFalse(provider.js_createFolder(f));
	}

	@Test
	public void deleteFileRemovesAFile() throws Exception
	{
		File f = write("del.txt", "x");

		assertTrue(provider.js_deleteFile(f));
		assertFalse(f.exists());
		assertFalse(provider.js_deleteFile(f));
		assertFalse(provider.js_deleteFile(null));
	}

	@Test
	public void deleteFileRemovesAFolderRecursively() throws Exception
	{
		write("tree/a.txt", "a");
		write("tree/sub/b.txt", "b");

		assertTrue(provider.js_deleteFile(file("tree")));
		assertFalse(file("tree").exists());
	}

	@Test
	public void deleteFolderWithWarningOnlyAsksInTheSmartClient() throws Exception
	{
		write("tree/a.txt", "a");
		when(Integer.valueOf(access.getApplicationType())).thenReturn(Integer.valueOf(IClientPluginAccess.NG_CLIENT));

		assertTrue(provider.js_deleteFolder(file("tree").getPath(), true));
		assertFalse(file("tree").exists());
	}

	@Test
	public void deleteFileOfARemoteFileDeletesTheServerFile() throws Exception
	{
		File main = Files.createDirectory(tempDir.resolve("server")).toFile();
		File f = write("server/r.txt", "x");

		assertTrue(provider.js_deleteFile(new JSFile(new RemoteFile(f, main, access), access)));
		assertFalse(f.exists());
	}

	// --- size / date ---

	@Test
	public void fileSizeAndModificationDateOfALocalFile() throws Exception
	{
		File f = write("size.txt", "12345");
		assertTrue(f.setLastModified(1_000_000_000_000L));

		assertEquals(5, provider.js_getFileSize(f));
		assertEquals(5, provider.js_getFileSize(f.getPath()));
		assertEquals(new Date(1_000_000_000_000L), provider.js_getModificationDate(new JSFile(f, access)));
	}

	@Test
	public void fileSizeAndModificationDateOfARemoteFile() throws Exception
	{
		File main = Files.createDirectory(tempDir.resolve("server")).toFile();
		File f = write("server/r.txt", "123");
		assertTrue(f.setLastModified(1_100_000_000_000L));
		JSFile remote = new JSFile(new RemoteFile(f, main, access), access);

		assertEquals(3, provider.js_getFileSize(remote));
		assertEquals(new Date(1_100_000_000_000L), provider.js_getModificationDate(remote));
	}

	@Test
	public void fileSizeAndModificationDateOfNull()
	{
		assertEquals(-1, provider.js_getFileSize(null));
		assertNull(provider.js_getModificationDate(null));
	}

	// --- folder contents ---

	@Test
	public void folderContentsListsEverythingByDefault() throws Exception
	{
		write("dir/a.txt", "");
		write("dir/b.pdf", "");
		Files.createDirectory(tempDir.resolve("dir/sub"));

		assertEquals(3, provider.js_getFolderContents(file("dir").getPath()).length);
		assertEquals(3, provider.js_getFolderContents(new JSFile(file("dir"), access)).length);
	}

	@Test
	public void folderContentsCanBeFilteredByExtensionCaseInsensitive() throws Exception
	{
		write("dir/a.txt", "");
		write("dir/b.TXT", "");
		write("dir/c.pdf", "");

		String[] names = Arrays.stream(provider.js_getFolderContents(file("dir").getPath(), ".Txt")).map(JSFile::js_getName).sorted()
			.toArray(String[]::new);

		assertArrayEquals(new String[] { "a.txt", "b.TXT" }, names);
	}

	@Test
	public void folderContentsAcceptsAnArrayOfExtensions() throws Exception
	{
		write("dir/a.txt", "");
		write("dir/b.pdf", "");
		write("dir/c.doc", "");

		assertEquals(2, provider.js_getFolderContents(file("dir").getPath(), new Object[] { ".txt", ".pdf" }).length);
	}

	@Test
	public void folderContentsCanBeFilteredOnFilesFoldersVisibleAndLocked() throws Exception
	{
		write("dir/a.txt", "");
		File locked = write("dir/locked.txt", "");
		Files.createDirectory(tempDir.resolve("dir/sub"));
		assertTrue(locked.setReadOnly());
		JSFile dir = new JSFile(file("dir"), access);
		try
		{
			assertEquals(2, provider.js_getFolderContents(dir, null, Integer.valueOf(AbstractFile.FILES)).length);
			assertEquals(1, provider.js_getFolderContents(dir, null, Integer.valueOf(AbstractFile.FOLDERS)).length);
			assertEquals(3, provider.js_getFolderContents(dir, null, null, Integer.valueOf(AbstractFile.VISIBLE)).length);
			assertEquals(0, provider.js_getFolderContents(dir, null, null, Integer.valueOf(AbstractFile.NON_VISIBLE)).length);
			assertEquals(1,
				provider.js_getFolderContents(dir, null, Integer.valueOf(AbstractFile.FILES), null, Integer.valueOf(AbstractFile.LOCKED)).length);
			assertEquals(1,
				provider.js_getFolderContents(file("dir").getPath(), null, Integer.valueOf(AbstractFile.FILES), null,
					Integer.valueOf(AbstractFile.NON_LOCKED)).length);
			assertEquals(1, provider.js_getFolderContents(file("dir").getPath(), ".txt", Integer.valueOf(AbstractFile.FILES),
				Integer.valueOf(AbstractFile.ALL), Integer.valueOf(AbstractFile.LOCKED)).length);
		}
		finally
		{
			locked.setWritable(true);
		}
	}

	@Test
	public void folderContentsOfNullOrAMissingFolderIsEmpty()
	{
		assertEquals(0, provider.js_getFolderContents((String)null).length);
		assertEquals(0, provider.js_getFolderContents(file("missing").getPath()).length);
	}

	// --- text files ---

	@Test
	public void writeAndReadTextFileWithDefaultEncoding() throws Exception
	{
		File f = file("t.txt");

		assertTrue(provider.js_writeTXTFile(f.getPath(), "line1\nline2"));
		assertEquals("line1\nline2", provider.js_readTXTFile(f.getPath()));
		assertEquals("line1\nline2", provider.js_readTXTFile(new JSFile(f, access)));
	}

	@Test
	public void writeAndReadTextFileWithAnEncoding() throws Exception
	{
		JSFile f = new JSFile(file("t.txt"), access);

		assertTrue(provider.js_writeTXTFile(f, "caf\u00e9", "ISO-8859-1"));
		assertArrayEquals(new byte[] { 'c', 'a', 'f', (byte)0xe9 }, Files.readAllBytes(f.getFile().toPath()));
		assertEquals("caf\u00e9", provider.js_readTXTFile(f, "ISO-8859-1"));
	}

	@Test
	public void readingUtfSkipsTheByteOrderMark() throws Exception
	{
		File f = file("bom.txt");
		Files.write(f.toPath(), new byte[] { (byte)0xEF, (byte)0xBB, (byte)0xBF, 'h', 'i' });

		assertEquals("hi", provider.js_readTXTFile(f.getPath(), "UTF-8"));
	}

	@Test
	public void readingWindowsLineEndingsNormalizesToNewLines() throws Exception
	{
		File f = write("crlf.txt", "a\r\nb\r\n");
		assertEquals("a\nb", provider.js_readTXTFile(f.getPath(), "UTF-8"));
	}

	@Test
	public void readingWithAnUnknownEncodingReturnsNullAndReleasesTheFile() throws Exception
	{
		File f = write("u.txt", "x");

		assertNull(provider.js_readTXTFile(f.getPath(), "no-such-charset"));
		assertNull(provider.js_readTXTFile(new JSFile(f, access), "illegal charset name"));
		// the stream must be closed, else the file stays locked on Windows and cannot be deleted
		assertTrue(f.delete());
	}

	@Test
	public void readTxtFromAStreamClosesTheStream() throws Exception
	{
		java.util.concurrent.atomic.AtomicInteger closed = new java.util.concurrent.atomic.AtomicInteger();
		class TrackingStream extends java.io.ByteArrayInputStream
		{
			TrackingStream(byte[] buf)
			{
				super(buf);
			}

			@Override
			public void close() throws java.io.IOException
			{
				closed.incrementAndGet();
				super.close();
			}
		}

		assertNull(provider.readTXTFile("no-such-charset", new TrackingStream(new byte[] { 'x' })));
		assertEquals("x", provider.readTXTFile(null, new TrackingStream(new byte[] { 'x' })));
		assertEquals("", provider.readTXTFile("UTF-8", new TrackingStream(new byte[0])));
		assertEquals(3, closed.get());
	}

	@Test
	public void writingNullTextWritesAnEmptyFile() throws Exception
	{
		File f = write("n.txt", "old");

		assertTrue(provider.js_writeTXTFile(new JSFile(f, access), null));
		assertEquals("", read(f));
	}

	@Test
	public void writeTextFileWithMimeTypeAndNullFile() throws Exception
	{
		File f = file("m.txt");
		assertTrue(provider.js_writeTXTFile(f.getPath(), "x", "UTF-8", "text/csv"));
		assertTrue(provider.js_writeTXTFile(new JSFile(f, access), "y", "UTF-8", null));
		assertEquals("y", read(f));
		assertTrue(provider.js_writeTXTFile(f.getPath(), "z", "UTF-8"));
		assertEquals("z", read(f));
		assertFalse(provider.js_writeTXTFile((String)null, "x"));
	}

	@Test
	public void writingWithAnUnknownEncodingFailsAndKeepsTheFile() throws Exception
	{
		File f = write("trunc.txt", "important");

		assertFalse(provider.js_writeTXTFile(f.getPath(), "new", "no-such-charset"));
		assertFalse(provider.js_writeTXTFile(f.getPath(), "new", "illegal charset name"));
		assertEquals("important", read(f));
	}

	@Test
	public void writingWithAnUnknownEncodingDoesNotCreateTheFile()
	{
		File f = file("not-created.txt");

		assertFalse(provider.js_writeTXTFile(f.getPath(), "new", "no-such-charset"));
		assertFalse(provider.js_writeXMLFile(f.getPath(), "<a/>", "no-such-charset"));
		assertFalse(f.exists());
	}

	@Test
	public void appendingWithAnUnknownEncodingFailsAndDoesNotCreateTheFile() throws Exception
	{
		File existing = write("keep.txt", "keep");
		File missing = file("append-missing.txt");

		assertFalse(provider.js_appendToTXTFile(existing.getPath(), "more", "no-such-charset"));
		assertFalse(provider.js_appendToTXTFile(missing.getPath(), "more", "no-such-charset"));
		assertEquals("keep", read(existing));
		assertFalse(missing.exists());
	}

	@Test
	public void supportedEncodingCheck()
	{
		assertTrue(FileProvider.isSupportedEncoding(null));
		assertTrue(FileProvider.isSupportedEncoding("UTF-8"));
		assertFalse(FileProvider.isSupportedEncoding("no-such-charset"));
		assertFalse(FileProvider.isSupportedEncoding("illegal charset name"));
	}

	@Test
	public void writeToOutputStreamRejectsAnUnknownEncodingWithoutWriting() throws Exception
	{
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

		assertFalse(provider.writeToOutputStream(out, "x", "no-such-charset"));
		assertEquals(0, out.size());
		assertTrue(provider.writeToOutputStream(out, "x", null));
		assertEquals("x", out.toString(java.nio.charset.Charset.defaultCharset()));
	}

	@Test
	public void writingIntoAMissingFolderFails()
	{
		assertFalse(provider.js_writeTXTFile(file("nofolder/x.txt").getPath(), "x"));
	}

	@Test
	public void appendAddsTextAndUsesThePlatformLineSeparator() throws Exception
	{
		File f = write("app.txt", "first");

		assertTrue(provider.js_appendToTXTFile(f.getPath(), "\nsecond"));
		assertTrue(provider.js_appendToTXTFile(new JSFile(f, access), "\nthird", "UTF-8"));

		String sep = System.lineSeparator();
		assertEquals("first" + sep + "second" + sep + "third", read(f));
	}

	@Test
	public void appendCreatesTheFileAndRejectsNullText() throws Exception
	{
		File f = file("new-append.txt");

		assertFalse(provider.js_appendToTXTFile(f.getPath(), null));
		assertFalse(f.exists());
		assertTrue(provider.js_appendToTXTFile(new JSFile(f, access), "x"));
		assertEquals("x", read(f));
		assertTrue(provider.js_appendToTXTFile(f.getPath(), "y", "UTF-8"));
		assertEquals("xy", read(f));
	}

	// --- xml files ---

	/** Byte that encodes the e-acute right before the closing "</a>" tag. */
	private static byte eAcuteByte(File f) throws Exception
	{
		byte[] bytes = Files.readAllBytes(f.toPath());
		return bytes[bytes.length - 5];
	}

	@Test
	public void writeXmlUsesTheDeclaredEncodingWhenBothQuoteTypesFollow() throws Exception
	{
		File f = file("x.xml");
		String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a t='1'>caf\u00e9</a>";

		assertTrue(provider.js_writeXMLFile(f.getPath(), xml));
		assertEquals((byte)0xe9, eAcuteByte(f));
	}

	@Test
	public void writeXmlUsesADoubleQuotedDeclaredEncoding() throws Exception
	{
		File dq = file("dq.xml");

		assertTrue(provider.js_writeXMLFile(new JSFile(dq, access), "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>caf\u00e9</a>"));
		assertEquals((byte)0xe9, eAcuteByte(dq));
	}

	@Test
	public void writeXmlUsesASingleQuotedDeclaredEncoding() throws Exception
	{
		File sq = file("sq.xml");

		assertTrue(provider.js_writeXMLFile(sq.getPath(), "<?xml version='1.0' encoding='ISO-8859-1'?><a>caf\u00e9</a>"));
		assertEquals((byte)0xe9, eAcuteByte(sq));
	}

	@Test
	public void writeXmlFallsBackToUtf8ForAnUnquotedOrUnterminatedEncoding() throws Exception
	{
		File unquoted = file("unquoted.xml");
		File unterminated = file("unterminated.xml");

		assertTrue(provider.js_writeXMLFile(unquoted.getPath(), "<?xml encoding=ISO-8859-1?><a>caf\u00e9</a>"));
		assertTrue(provider.js_writeXMLFile(unterminated.getPath(), "<?xml encoding='ISO-8859-1<a>caf\u00e9</a>"));
		assertEquals((byte)0xa9, eAcuteByte(unquoted)); // second byte of the UTF-8 encoding C3 A9
		assertEquals((byte)0xa9, eAcuteByte(unterminated));
	}

	@Test
	public void writeXmlWithAnUnknownDeclaredEncodingFailsAndKeepsTheFile() throws Exception
	{
		File f = write("keep.xml", "<old/>");

		assertFalse(provider.js_writeXMLFile(f.getPath(), "<?xml version='1.0' encoding='no-such-charset'?><a/>"));
		assertEquals("<old/>", read(f));
	}

	@Test
	public void writeXmlWithoutDeclarationIsUtf8() throws Exception
	{
		File f = file("plain.xml");

		assertTrue(provider.js_writeXMLFile(f.getPath(), "<a>caf\u00e9</a>"));
		assertEquals("<a>caf\u00e9</a>", read(f));
	}

	@Test
	public void writeXmlWithExplicitEncoding() throws Exception
	{
		File f = file("enc.xml");

		assertTrue(provider.js_writeXMLFile(f.getPath(), "<a>caf\u00e9</a>", "ISO-8859-1"));
		assertEquals(11, Files.size(f.toPath()));
		assertTrue(provider.js_writeXMLFile(new JSFile(f, access), "<b/>", "UTF-8"));
		assertEquals("<b/>", read(f));
	}

	@Test
	public void writeXmlRejectsNullData()
	{
		assertFalse(provider.js_writeXMLFile(file("x.xml").getPath(), null));
		assertFalse(provider.js_writeXMLFile(file("x.xml").getPath(), null, "UTF-8"));
	}

	// --- binary files ---

	@Test
	public void writeAndReadBinaryFile() throws Exception
	{
		File f = file("bin/data.bin");
		byte[] data = { 1, 2, 3, 4, 5 };

		assertTrue(provider.js_writeFile(f.getPath(), data));
		assertArrayEquals(data, provider.js_readFile(f.getPath()));
		assertArrayEquals(data, provider.js_readFile(new JSFile(f, access)));
		assertArrayEquals(new byte[] { 1, 2 }, provider.js_readFile(f.getPath(), 2));
		assertArrayEquals(new byte[] { 1, 2, 3 }, provider.js_readFile(new JSFile(f, access), 3));
	}

	@Test
	public void writeBinaryFileWithMimeTypeAndJSFile() throws Exception
	{
		JSFile f = new JSFile(file("b.bin"), access);

		assertTrue(provider.js_writeFile(f, new byte[] { 9 }));
		assertTrue(provider.js_writeFile(f, new byte[] { 8 }, "application/octet-stream"));
		assertTrue(provider.js_writeFile(f.getFile().getPath(), new byte[] { 7 }, null));
		assertArrayEquals(new byte[] { 7 }, Files.readAllBytes(f.getFile().toPath()));
	}

	@Test
	public void writeBinaryFileRejectsNullDataAndFolders() throws Exception
	{
		assertFalse(provider.js_writeFile(file("x.bin").getPath(), null));
		assertFalse(provider.js_writeFile(tempDir.toFile().getPath(), new byte[] { 1 }));
	}

	// --- temp and tracked files ---

	@Test
	public void tempFileIsCreatedAndShortPrefixesArePadded() throws Exception
	{
		JSFile temp = provider.js_createTempFile("ab", ".txt");
		try
		{
			assertTrue(temp.js_exists());
			assertTrue(temp.js_getName().startsWith("absvy"));
			assertTrue(temp.js_getName().endsWith(".txt"));
		}
		finally
		{
			temp.getFile().delete();
		}
	}

	@Test
	public void tempFileCanBeAddressedByItsPathAndIsDeletedWithTheTrackedFiles() throws Exception
	{
		JSFile temp = provider.js_createTempFile("svytest", ".txt");
		JSFile tracked = new JSFile(write("tracked.txt", "x"), access);
		provider.js_trackFileForDeletion(tracked);

		assertTrue(provider.js_writeTXTFile(temp.js_getAbsolutePath(), "temp data"));
		assertEquals("temp data", provider.js_readTXTFile(temp.js_getAbsolutePath()));

		provider.deleteTrackedFiles();

		assertFalse(temp.js_exists());
		assertFalse(tracked.js_exists());
	}

	@Test
	public void deletingAnAlreadyRemovedTrackedFileOnlyLogs() throws Exception
	{
		provider.js_trackFileForDeletion(new JSFile(file("never-created.txt"), access));
		provider.deleteTrackedFiles();
	}

	@Test
	public void tempFileWithInvalidSuffixReturnsNull()
	{
		assertNull(provider.js_createTempFile("svytest", "/\u0000"));
	}

	// --- remote (server) files through a mocked file service ---

	private File mockServerFolder() throws Exception
	{
		File main = Files.createDirectories(tempDir.resolve("server")).toFile();
		IFileService service = mock(IFileService.class);
		when(service.getDefaultFolder(any())).thenReturn(main);
		when(access.getRemoteService(IFileService.class.getName())).thenReturn(service);
		return main;
	}

	@Test
	public void convertToRemoteJSFileResolvesAgainstTheServerFolder() throws Exception
	{
		File main = mockServerFolder();

		JSFile remote = provider.js_convertToRemoteJSFile("/docs/a.txt");

		assertInstanceOf(RemoteFile.class, remote.getAbstractFile());
		assertEquals(new File(main, "/docs/a.txt"), remote.getFile());
		assertEquals("/docs/a.txt", remote.js_getAbsolutePath());
	}

	@Test
	public void convertToRemoteJSFileRequiresAnAbsoluteServerPath()
	{
		assertThrows(IllegalArgumentException.class, () -> provider.js_convertToRemoteJSFile(null));
		assertThrows(IllegalArgumentException.class, () -> provider.js_convertToRemoteJSFile("docs/a.txt"));
	}

	@Test
	public void defaultUploadLocationIsTheCanonicalServerFolder() throws Exception
	{
		File main = mockServerFolder();
		assertEquals(main.getCanonicalPath(), provider.js_getDefaultUploadLocation());
	}

	@Test
	public void defaultUploadLocationIsNullWithoutFileService() throws Exception
	{
		when(access.getRemoteService(IFileService.class.getName())).thenThrow(new IllegalStateException("no server"));
		assertNull(provider.js_getDefaultUploadLocation());
	}

	@Test
	public void remoteFolderContentsListsTheServerFolder() throws Exception
	{
		File main = mockServerFolder();
		write("server/docs/a.txt", "");
		write("server/docs/b.pdf", "");

		JSFile[] all = provider.js_getRemoteFolderContents("/docs");
		JSFile[] txt = provider.js_getRemoteFolderContents("/docs", ".TXT");

		assertEquals(2, all.length);
		assertInstanceOf(RemoteFile.class, all[0].getAbstractFile());
		assertEquals(1, txt.length);
		assertEquals("/docs/a.txt", txt[0].js_getAbsolutePath());
		assertEquals(1, provider.js_getRemoteFolderContents(new JSFile(new RemoteFile(new File(main, "docs"), main, access), access),
			new String[] { ".pdf" }).length);
	}

	@Test
	public void remoteFolderContentsRejectsLocalFilesAndHandlesMissingFolders() throws Exception
	{
		mockServerFolder();

		assertEquals(0, provider.js_getRemoteFolderContents((String)null).length);
		assertEquals(0, provider.js_getRemoteFolderContents("/missing").length);
		JSFile local = new JSFile(file("local"), access);
		assertThrows(IllegalArgumentException.class, () -> provider.js_getRemoteFolderContents(local));
	}

	@Test
	public void remoteFolderContentsCanBeFilteredOnFilesAndFolders() throws Exception
	{
		mockServerFolder();
		write("server/docs/a.txt", "");
		Files.createDirectory(tempDir.resolve("server/docs/sub"));

		assertEquals(1, provider.js_getRemoteFolderContents("/docs", null, Integer.valueOf(AbstractFile.FILES)).length);
		assertEquals(1, provider.js_getRemoteFolderContents("/docs", null, Integer.valueOf(AbstractFile.FOLDERS),
			Integer.valueOf(AbstractFile.ALL)).length);
		assertEquals(2, provider.js_getRemoteFolderContents("/docs", null, null, null, Integer.valueOf(AbstractFile.ALL)).length);
		assertNotNull(provider.js_getRemoteList("/docs"));
		assertEquals(1, provider.js_getRemoteList("/docs", true).length);
	}
}
