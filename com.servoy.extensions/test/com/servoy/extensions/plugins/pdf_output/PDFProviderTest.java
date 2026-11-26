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
package com.servoy.extensions.plugins.pdf_output;

import static com.servoy.extensions.plugins.pdf_output.PdfTestSupport.pages;
import static com.servoy.extensions.plugins.pdf_output.PdfTestSupport.pdf;
import static com.servoy.extensions.plugins.pdf_output.PdfTestSupport.png;
import static com.servoy.extensions.plugins.pdf_output.PdfTestSupport.text;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mozilla.javascript.NativeObject;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.SimpleBookmark;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * The pdf_output scripting API on PDFs generated in the test: combine, encrypt, metadata, page numbers, watermark,
 * overlays, page count and thumbnails. Every result is read back with OpenPDF or PDFBox.
 */
public class PDFProviderTest
{
	private PDFPlugin plugin;
	private PDFProvider provider;

	@TempDir
	Path tmp;

	@BeforeEach
	public void setUp() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(Integer.valueOf(access.getApplicationType())).thenReturn(Integer.valueOf(IClientPluginAccess.NG_CLIENT));
		plugin = new PDFPlugin();
		plugin.load();
		plugin.initialize(access);
		provider = (PDFProvider)plugin.getScriptObject();
	}

	private static NativeObject object(String... keyValues)
	{
		NativeObject object = new NativeObject();
		for (int i = 0; i < keyValues.length; i += 2)
		{
			object.put(keyValues[i], object, keyValues[i + 1]);
		}
		return object;
	}

	// --- plugin ---

	@Test
	public void plugin() throws Exception
	{
		assertEquals("pdf_output", plugin.getName());
		assertNotNull(plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertSame(provider, plugin.getScriptObject());
		plugin.propertyChange(null);

		plugin.unload();

		assertNull(plugin.getClientPluginAccess());
	}

	// --- combine ---

	@Test
	public void documentsAreCombinedInOrder() throws Exception
	{
		byte[] combined = provider.js_combinePDFDocuments(new Object[] { pdf("first"), "not a pdf", pdf("second", "third") });

		assertEquals(3, pages(combined));
		assertTrue(text(combined, 1).contains("first"));
		assertTrue(text(combined, 3).contains("third"));
	}

	@Test
	public void bookmarksAreShiftedToTheirNewPages() throws Exception
	{
		byte[] combined = provider.js_combinePDFDocuments(new Object[] { pdf(true, "a", "b"), pdf(true, "c") });

		List<Map<String, Object>> bookmarks = SimpleBookmark.getBookmarkList(new PdfReader(combined));
		assertEquals(3, bookmarks.size());
		assertEquals("c", bookmarks.get(2).get("Title"));
		assertTrue(String.valueOf(bookmarks.get(2).get("Page")).startsWith("3 "), String.valueOf(bookmarks.get(2).get("Page")));
	}

	@Test
	public void protectedDocumentsNeedTheirPassword() throws Exception
	{
		byte[] secret = provider.js_encrypt(pdf("secret"), "owner", "user");

		byte[] combined = provider.js_combineProtectedPDFDocuments(new Object[] { pdf("open"), secret }, new Object[] { null, "user" });

		assertEquals(2, pages(combined));
		assertTrue(text(combined, 2).contains("secret"));
		assertThrows(RuntimeException.class, () -> provider.js_combineProtectedPDFDocuments(new Object[] { secret }, new Object[] { "wrong" }));
	}

	@Test
	public void nothingToCombine()
	{
		assertNull(provider.js_combinePDFDocuments(null));
		assertNull(provider.js_combinePDFDocuments(new Object[0]));
		// only entries that are not pdf data: an empty result
		assertEquals(0, provider.js_combinePDFDocuments(new Object[] { "x" }).length);
	}

	// --- encrypt and metadata ---

	@Test
	public void encryptWithAllPermissions() throws Exception
	{
		byte[] encrypted = provider.js_encrypt(pdf("hello"), "owner");

		PdfReader reader = new PdfReader(encrypted, "owner".getBytes());
		assertTrue(reader.isEncrypted());
		assertTrue((reader.getPermissions() & PdfWriter.ALLOW_PRINTING) == PdfWriter.ALLOW_PRINTING);
		assertTrue((reader.getPermissions() & PdfWriter.ALLOW_COPY) == PdfWriter.ALLOW_COPY);
		assertThrows(Exception.class, () -> new PdfReader(encrypted, "wrong".getBytes()));
	}

	@Test
	public void encryptWithoutPermissions() throws Exception
	{
		byte[] encrypted = provider.js_encrypt(pdf("hello"), "owner", "user", false, false, false, false, false, false, false, false);

		PdfReader reader = new PdfReader(encrypted, "user".getBytes());
		assertEquals(0, reader.getPermissions() & (PdfWriter.ALLOW_PRINTING | PdfWriter.ALLOW_COPY | PdfWriter.ALLOW_MODIFY_CONTENTS));
		// 40 bit encryption
		assertTrue(new PdfReader(provider.js_encrypt(pdf("x"), "o", "u", true, true, true, true, true, true, true, true, false), "o".getBytes())
			.isEncrypted());
	}

	@Test
	public void encryptWith40BitAndMetadata() throws Exception
	{
		byte[] encrypted = provider.js_encrypt(pdf("hello"), "owner", "user", true, false, true, false, true, false, true, false, false,
			object("Title", "Report", "Author", "Servoy"));

		PdfReader reader = new PdfReader(encrypted, "owner".getBytes());
		assertEquals("Report", reader.getInfo().get("Title"));
		assertEquals("Servoy", reader.getInfo().get("Author"));
		assertTrue((reader.getPermissions() & PdfWriter.ALLOW_ASSEMBLY) == PdfWriter.ALLOW_ASSEMBLY);
		assertFalse((reader.getPermissions() & PdfWriter.ALLOW_COPY) == PdfWriter.ALLOW_COPY);
	}

	@Test
	public void encryptNeedsData()
	{
		assertThrows(IllegalArgumentException.class, () -> provider.js_encrypt(null, "owner"));
	}

	@Test
	public void metadataIsAdded() throws Exception
	{
		byte[] withMetadata = provider.js_addMetaData(pdf("hello"), object("Title", "Invoice", "Subject", "i18n:servoy.unknown.key"));

		Map<String, String> info = new PdfReader(withMetadata).getInfo();
		assertEquals("Invoice", info.get("Title"));
		assertNotNull(info.get("Subject"));
	}

	@Test
	public void metadataNeedsDataAndValues()
	{
		assertThrows(IllegalArgumentException.class, () -> provider.js_addMetaData(null, object("a", "b")));
		assertThrows(IllegalArgumentException.class, () -> provider.js_addMetaData(pdf("x"), null));
		assertThrows(IllegalArgumentException.class, () -> provider.js_addMetaData(pdf("x"), new NativeObject()));
	}

	@Test
	public void scriptableToMap()
	{
		NativeObject nested = object("inner", "value");
		NativeObject outer = object("plain", "text");
		outer.put("nested", outer, nested);
		outer.put("number", outer, Integer.valueOf(5));

		Map<String, String> map = ITextTools.getMapFromScriptable(outer);

		assertEquals("text", map.get("plain"));
		assertEquals("5", map.get("number"));
		assertEquals("{inner=value}", map.get("nested"));
		assertNull(ITextTools.getMapFromScriptable("not a scriptable"));
		assertNull(ITextTools.getMapFromScriptable(new NativeObject()));
		assertTrue(ITextTools.isNullOrEmpty(" "));
		assertTrue(ITextTools.isNullOrEmpty((Map< ? , ? >)null));
		assertFalse(ITextTools.isNullOrEmpty("x"));
	}

	// --- page numbers, overlays and watermarks ---

	@Test
	public void pagesAreNumbered() throws Exception
	{
		byte[] numbered = provider.js_numberPages(pdf("one", "two"));

		assertTrue(text(numbered, 1).contains("Page 1 of 2"), text(numbered, 1));
		assertTrue(text(numbered, 2).contains("Page 2 of 2"), text(numbered, 2));
	}

	@Test
	public void pageNumbersWithAnOwnFontAndColor() throws Exception
	{
		byte[] numbered = provider.js_numberPages(pdf("one"), 14, 50, 50, "Courier", "#FF0000");

		assertTrue(text(numbered, 1).contains("Page 1 of 1"));
		assertThrows(IllegalArgumentException.class, () -> provider.js_numberPages(null));
		assertThrows(NumberFormatException.class, () -> provider.js_numberPages(pdf("one"), 14, 50, 50, "Courier", "red"));
	}

	@Test
	public void textOverlay() throws Exception
	{
		byte[] over = provider.js_overlayText(pdf("one", "two"), "DRAFT");
		byte[] under = provider.js_overlayText(pdf("one"), "COPY", 100, 100, false, 20, "Helvetica-Bold", "#00FF00", 0);

		assertTrue(text(over, 2).contains("DRAFT"));
		assertTrue(text(under, 1).contains("COPY"));
	}

	@Test
	public void textOverlayNeedsAllArguments()
	{
		assertThrows(IllegalArgumentException.class, () -> provider.js_overlayText(null, "x"));
		assertThrows(IllegalArgumentException.class, () -> provider.js_overlayText(pdf("one"), " "));
		assertThrows(IllegalArgumentException.class, () -> provider.js_overlayText(pdf("one"), "x", 1, 1, true, 10, "", "#000000"));
		assertThrows(IllegalArgumentException.class, () -> provider.js_overlayText(pdf("one"), "x", 1, 1, true, 10, "Helvetica", null));
	}

	@Test
	public void aPdfIsOverlaid() throws Exception
	{
		byte[] base = pdf("base one", "base two", "base three");
		byte[] stamp = pdf("STAMP");

		byte[] all = provider.js_overlay(base, stamp);
		byte[] some = provider.js_overlay(base, stamp, new String[] { "1", "3" });
		byte[] over = provider.js_overlay(base, stamp, true);

		assertTrue(text(all, 2).contains("STAMP"));
		assertTrue(text(some, 1).contains("STAMP"));
		assertFalse(text(some, 2).contains("STAMP"));
		assertTrue(text(some, 3).contains("STAMP"));
		assertTrue(text(over, 3).contains("STAMP") && text(over, 3).contains("base three"));
		assertThrows(IllegalArgumentException.class, () -> provider.js_overlay(base, null));
	}

	@Test
	public void invalidPageNumbersForAnOverlay() throws Exception
	{
		// current behaviour (bug): page numbers that are not numbers make the page list null, and the overlay then
		// fails in Arrays.binarySearch instead of ignoring them or stamping every page
		assertThrows(NullPointerException.class, () -> provider.js_overlay(pdf("a"), pdf("STAMP"), new String[] { "first" }));
	}

	@Test
	public void aWatermarkImageIsAddedToTheChosenPages() throws Exception
	{
		Path image = Files.write(tmp.resolve("mark.png"), png());
		byte[] base = pdf("one", "two", "three");

		byte[] all = provider.js_watermark(base, png());
		byte[] chosen = provider.js_watermark(base, image.toString(), 10, 10, true, new String[] { "2" });
		byte[] under = provider.js_watermark(base, png(), 10, 10, false);

		// the same image is added once per page
		assertEquals(1, imagesOnPage(all, 3));
		assertEquals(0, imagesOnPage(chosen, 1));
		assertEquals(1, imagesOnPage(chosen, 2));
		assertEquals(1, imagesOnPage(under, 1));
		// a page list that is not numbers marks every page
		assertEquals(1, imagesOnPage(provider.js_watermark(base, png(), 10, 10, true, new String[] { "x" }), 3));
	}

	private static int imagesOnPage(byte[] pdf, int page) throws Exception
	{
		try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf))
		{
			int count = 0;
			for (org.apache.pdfbox.cos.COSName name : document.getPage(page - 1).getResources().getXObjectNames())
			{
				if (document.getPage(page - 1).getResources().isImageXObject(name)) count++;
			}
			return count;
		}
	}

	@Test
	public void watermarkNeedsAnImage()
	{
		assertThrows(IllegalArgumentException.class, () -> provider.js_watermark(null, png()));
		assertThrows(IllegalArgumentException.class, () -> provider.js_watermark(pdf("a"), Integer.valueOf(1)));
	}

	// --- pages and thumbnails ---

	@Test
	public void numberOfPages() throws Exception
	{
		assertEquals(3, provider.js_getNumberOfPages(pdf("a", "b", "c")));
	}

	@Test
	public void thumbnailsArePngImagesOfAPage() throws Exception
	{
		byte[] document = pdf("a", "b");

		BufferedImage first = ImageIO.read(new ByteArrayInputStream(provider.js_getThumbnailImage(document)));
		BufferedImage second = ImageIO.read(new ByteArrayInputStream(provider.js_getThumbnailImage(document, 1)));
		BufferedImage large = ImageIO.read(new ByteArrayInputStream(provider.js_getThumbnailImage(document, 0, 144)));

		// A4 is 595 x 842 points, one point is one pixel at 72 dpi
		assertEquals(595, first.getWidth());
		assertEquals(842, first.getHeight());
		assertEquals(595, second.getWidth());
		assertEquals(1190, large.getWidth());
		assertThrows(IndexOutOfBoundsException.class, () -> provider.js_getThumbnailImage(document, 5));
	}

	@Test
	public void encryptedResultsAreStillReadable() throws Exception
	{
		byte[] encrypted = provider.js_encrypt(pdf("hello"), "owner", "");

		assertArrayEquals(new byte[] { '%', 'P', 'D', 'F' }, java.util.Arrays.copyOf(encrypted, 4));
		assertEquals(1, pages(encrypted));
	}
}
