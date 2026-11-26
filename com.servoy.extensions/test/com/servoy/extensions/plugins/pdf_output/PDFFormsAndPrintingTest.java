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

import java.awt.Graphics;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.SimpleDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.AcroFields;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.TextField;
import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * Filling and flattening PDF forms, and printing a java Pageable to PDF: the printer job, the meta print job of the
 * provider (to bytes or a file in a temp dir) and the stream print service. No printer and no dialog.
 */
public class PDFFormsAndPrintingTest
{
	private PDFProvider provider;

	@TempDir
	Path tmp;

	@BeforeEach
	public void setUp() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(Integer.valueOf(access.getApplicationType())).thenReturn(Integer.valueOf(IClientPluginAccess.NG_CLIENT));
		PDFPlugin plugin = new PDFPlugin();
		plugin.initialize(access);
		provider = (PDFProvider)plugin.getScriptObject();
	}

	/** A form with the text fields name and city. */
	static byte[] form() throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4);
		PdfWriter writer = PdfWriter.getInstance(document, out);
		document.open();
		writer.addAnnotation(new TextField(writer, new Rectangle(50, 700, 300, 730), "name").getTextField());
		writer.addAnnotation(new TextField(writer, new Rectangle(50, 650, 300, 680), "city").getTextField());
		document.close();
		return out.toByteArray();
	}

	private static AcroFields fields(byte[] pdf) throws Exception
	{
		return new PdfReader(pdf).getAcroFields();
	}

	// --- forms ---

	@Test
	public void aFormIsFilledFromPairsAndFlattened() throws Exception
	{
		byte[] filled = provider.js_convertPDFFormToPDFDocument(form(),
			new Object[] { new Object[] { "name", "Servoy" }, "city=Amersfoort", new Object[] { "ignored" }, new Object[] { null, "x" }, 5 });

		String text = text(filled, 1);
		assertTrue(text.contains("Servoy"), text);
		assertTrue(text.contains("Amersfoort"), text);
		// flattened: no fields left
		assertTrue(fields(filled).getFields().isEmpty());
	}

	@Test
	public void partialFlatteningKeepsTheFieldsThatWereNotFilled() throws Exception
	{
		byte[] filled = provider.js_convertPDFFormToPDFDocument(form(), new Object[] { "name=Servoy" }, true);

		assertEquals(Set.of("city"), fields(filled).getFields().keySet());
		assertTrue(text(filled, 1).contains("Servoy"));
	}

	@Test
	public void aFormIsFilledFromFdf() throws Exception
	{
		String fdf = "%FDF-1.2\n1 0 obj\n<< /FDF << /Fields [ << /T (name) /V (FromFdf) >> << /T (city) /V (Utrecht) >> ] >> >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n";
		Path fdfFile = Files.writeString(tmp.resolve("values.fdf"), fdf);

		byte[] fromBytes = provider.js_convertPDFFormToPDFDocument(form(), fdf.getBytes(StandardCharsets.ISO_8859_1), true);
		byte[] fromFile = provider.js_convertPDFFormToPDFDocument(form(), fdfFile.toString());

		// the OpenPDF FdfReader names top level fields ".name" and ".city", they are matched without the dot
		assertEquals(Set.of(".name", ".city"), new com.lowagie.text.pdf.FdfReader(fdf.getBytes(StandardCharsets.ISO_8859_1)).getFields().keySet());
		assertTrue(text(fromBytes, 1).contains("FromFdf"), text(fromBytes, 1));
		assertTrue(text(fromBytes, 1).contains("Utrecht"), text(fromBytes, 1));
		assertTrue(text(fromFile, 1).contains("Utrecht"), text(fromFile, 1));
		assertTrue(fields(fromBytes).getFields().isEmpty());
		assertTrue(fields(fromFile).getFields().isEmpty());
	}

	@Test
	public void partialFlatteningWithFdfKeepsTheFieldsThatWereNotFilled() throws Exception
	{
		String fdf = "%FDF-1.2\n1 0 obj\n<< /FDF << /Fields [ << /T (name) /V (OnlyName) >> << /T (unknown) /V (x) >> ] >> >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n";

		byte[] filled = provider.js_convertPDFFormToPDFDocument(form(), fdf.getBytes(StandardCharsets.ISO_8859_1), true);

		assertTrue(text(filled, 1).contains("OnlyName"), text(filled, 1));
		// only the filled field is flattened, a value for a field the form does not have is ignored
		assertEquals(Set.of("city"), fields(filled).getFields().keySet());
	}

	@Test
	public void aProtectedFormNeedsItsPassword() throws Exception
	{
		byte[] protectedForm = provider.js_encrypt(form(), "owner", "user");

		byte[] filled = provider.js_convertProtectedPDFFormToPDFDocument(protectedForm, "owner", new Object[] { "name=Secret" });

		assertTrue(text(filled, 1).contains("Secret"));
		assertThrows(RuntimeException.class, () -> provider.js_convertProtectedPDFFormToPDFDocument(protectedForm, "wrong", null));
	}

	@Test
	public void withoutValuesTheFormIsOnlyFlattened() throws Exception
	{
		byte[] flattened = provider.js_convertPDFFormToPDFDocument(form(), null);

		assertEquals(1, pages(flattened));
		assertTrue(fields(flattened).getFields().isEmpty());
	}

	@Test
	public void aStringValueWithoutEqualsSignIsSkipped() throws Exception
	{
		byte[] filled = provider.js_convertPDFFormToPDFDocument(form(), new Object[] { "name", "=nameless", "city=Amersfoort" }, true);

		assertTrue(text(filled, 1).contains("Amersfoort"));
		assertEquals(Set.of("name"), fields(filled).getFields().keySet());
	}

	@Test
	public void notAPdf()
	{
		assertThrows(RuntimeException.class, () -> provider.js_convertPDFFormToPDFDocument("not a pdf".getBytes(), null));
	}

	// --- printing a Pageable ---

	/** A pageable of the given number of A4 pages, each with its page number as text. */
	static Book pageable(int pageCount)
	{
		Book book = new Book();
		PageFormat format = new PageFormat();
		Paper paper = new Paper();
		paper.setSize(595, 842);
		paper.setImageableArea(36, 36, 523, 770);
		format.setPaper(paper);
		Printable printable = (Graphics graphics, PageFormat pf, int pageIndex) -> {
			graphics.drawString("printed page " + (pageIndex + 1), 100, 100);
			return Printable.PAGE_EXISTS;
		};
		book.append(printable, format, pageCount);
		return book;
	}

	@Test
	public void aPrinterJobWritesThePagesAndCloses() throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PDFPrinterJob job = new PDFPrinterJob(out, false);
		job.setPageable(pageable(2));
		job.setJobName("report");

		job.print();

		byte[] pdf = out.toByteArray();
		assertEquals(2, pages(pdf));
		assertEquals(2, job.getPagesPrinted());
		assertEquals(2, job.getTotalPagesPrinted());
		assertEquals("report", job.getJobName());
	}

	@Test
	public void printerJobDefaults() throws Exception
	{
		PDFPrinterJob job = new PDFPrinterJob(new ByteArrayOutputStream(), false);
		PageFormat format = new PageFormat();

		assertSame(format, job.defaultPage(format));
		assertSame(format, job.validatePage(format));
		assertSame(format, job.pageDialog(format));
		assertNotNull(job.pageDialog((PageFormat)null));
		assertEquals(1, job.getCopies());
		assertEquals("pdfJob", job.getJobName());
		assertNull(job.getUserName());
		assertFalse(job.isCancelled());
		assertTrue(job.printDialog());
		job.cancel();
		job.setCopies(3);
		job.setPrintable(null);
		job.setPrintable(null, format);
		assertEquals(1, job.getCopies());
		// without pageable nothing happens
		job.print();
		assertEquals(0, job.getTotalPagesPrinted());
		assertTrue(job.insertDirectory(tmp.toString()) >= 0);
	}

	@Test
	public void theMetaPrintJobCollectsSeveralPrintsInOneDocument() throws Exception
	{
		assertTrue(provider.js_startMetaPrintJob());
		PDFPrinterJob job = (PDFPrinterJob)provider.js_getPDFPrinter();
		assertSame(job, provider.js_getPDFPrinter(null));

		job.setPageable(pageable(2));
		job.print();
		assertEquals(2, provider.js_getPagesPrinted());
		job.setPageable(pageable(1));
		job.print();
		assertEquals(1, provider.js_getPagesPrinted());
		assertEquals(3, provider.js_getTotalPagesPrinted());
		assertTrue(provider.js_insertFontDirectory(tmp.toString()) >= 0);

		byte[] pdf = provider.js_endMetaPrintJob();

		assertEquals(3, pages(pdf));
		assertTrue(text(pdf, 3).contains("printed page 1"), text(pdf, 3));
		assertNull(provider.js_getPDFPrinter());
		assertEquals(0, provider.js_getTotalPagesPrinted());
		assertEquals(0, provider.js_getPagesPrinted());
		assertEquals(-1, provider.js_insertFontDirectory(tmp.toString()));
		assertNull(provider.js_endMetaPrintJob());
	}

	@Test
	public void aMetaPrintJobToAFile() throws Exception
	{
		Path file = tmp.resolve("meta.pdf");
		assertTrue(provider.js_startMetaPrintJob(file.toString()));
		PDFPrinterJob job = (PDFPrinterJob)provider.js_getPDFPrinter();
		job.setPageable(pageable(1));
		job.print();

		// a file is written, the bytes are only returned for an in-memory job
		assertNull(provider.js_endMetaPrintJob());

		assertEquals(1, pages(Files.readAllBytes(file)));
	}

	@Test
	public void anEmptyMetaPrintJobFileIsDeleted() throws Exception
	{
		Path file = tmp.resolve("empty.pdf");
		provider.js_startMetaPrintJob(file.toString());

		provider.js_endMetaPrintJob();

		assertFalse(Files.exists(file));
	}

	@Test
	public void aMetaPrintJobInAMissingFolderFails()
	{
		assertThrows(RuntimeException.class, () -> provider.js_startMetaPrintJob(tmp.resolve("no/such/folder/x.pdf").toString()));
		assertThrows(RuntimeException.class, () -> provider.js_getPDFPrinter(tmp.resolve("no/such/folder/x.pdf").toString()));
	}

	@Test
	public void aPrinterForAFile() throws Exception
	{
		Path file = tmp.resolve("single.pdf");

		PDFPrinterJob job = (PDFPrinterJob)provider.js_getPDFPrinter(file.toString());
		job.setPageable(pageable(2));
		job.print();

		assertEquals(2, pages(Files.readAllBytes(file)));
	}

	@Test
	public void anEmptyPageableGivesAnEmptyPage() throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PDFPrinterJob job = new PDFPrinterJob(out, false);
		Book book = pageable(1);
		Book empty = new Book()
		{
			@Override
			public int getNumberOfPages()
			{
				return 0;
			}

			@Override
			public PageFormat getPageFormat(int pageIndex)
			{
				return book.getPageFormat(0);
			}

			@Override
			public Printable getPrintable(int pageIndex)
			{
				return book.getPrintable(0);
			}
		};
		job.setPageable(empty);

		job.print();

		assertEquals(1, pages(out.toByteArray()));
		assertEquals(0, job.getPagesPrinted());
	}

	// --- print services ---

	@Test
	public void theStreamPrintServicePrintsAPageable() throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PDFStreamPrintServiceFactory factory = new PDFStreamPrintServiceFactory();
		PDFStreamPrintService service = (PDFStreamPrintService)factory.getPrintService(out);

		DocPrintJob job = service.createPrintJob();
		job.print(new SimpleDoc(pageable(3), DocFlavor.SERVICE_FORMATTED.PAGEABLE, null), null);

		assertEquals(3, pages(out.toByteArray()));
		assertSame(service, job.getPrintService());
		assertTrue(job.getAttributes().isEmpty());
		job.addPrintJobListener(null);
		job.removePrintJobListener(null);
		job.addPrintJobAttributeListener(null, null);
		job.removePrintJobAttributeListener(null);
		assertEquals("application/pdf", factory.getOutputFormat());
		assertEquals("application/pdf", service.getOutputFormat());
		assertArrayEquals(new DocFlavor[] { DocFlavor.SERVICE_FORMATTED.PAGEABLE }, factory.getSupportedDocFlavors());
	}

	@Test
	public void otherDocumentsAreIgnoredByTheStreamPrintService() throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DocPrintJob job = new PDFStreamPrintService(out).createPrintJob();

		job.print(new SimpleDoc("text", DocFlavor.STRING.TEXT_PLAIN, null), null);
		job.print(null, null);

		assertEquals(0, out.size());
	}

	@Test
	public void aFailingPageableIsAPrintException()
	{
		Doc doc = mock(Doc.class);
		try
		{
			when(doc.getPrintData()).thenThrow(new java.io.IOException("broken"));
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException(e);
		}

		assertThrows(PrintException.class, () -> new PDFStreamPrintService(new ByteArrayOutputStream()).createPrintJob().print(doc, null));
	}

	@Test
	public void streamPrintServiceDescription()
	{
		PDFStreamPrintService service = new PDFStreamPrintService(new ByteArrayOutputStream());

		assertEquals("PDF output", service.getName());
		assertTrue(service.isDocFlavorSupported(DocFlavor.SERVICE_FORMATTED.PAGEABLE));
		assertFalse(service.isDocFlavorSupported(DocFlavor.STRING.TEXT_PLAIN));
		assertNull(service.getAttribute(null));
		assertNull(service.getAttributes());
		assertNull(service.getDefaultAttributeValue(null));
		assertNull(service.getServiceUIFactory());
		assertNull(service.getSupportedAttributeCategories());
		assertNull(service.getSupportedAttributeValues(null, null, null));
		assertNull(service.getUnsupportedAttributes(null, null));
		assertFalse(service.isAttributeCategorySupported(null));
		assertFalse(service.isAttributeValueSupported(null, null, null));
		service.addPrintServiceAttributeListener(null);
		service.removePrintServiceAttributeListener(null);
	}

	@Test
	public void smartClientPrintServiceDescription()
	{
		PDFPrintService service = new PDFPrintService(mock(IClientPluginAccess.class));

		assertEquals("Servoy PDF output", service.getName());
		assertTrue(service.isDocFlavorSupported(DocFlavor.SERVICE_FORMATTED.PAGEABLE));
		assertFalse(service.isDocFlavorSupported(DocFlavor.STRING.TEXT_PLAIN));
		assertNull(service.getAttribute(null));
		assertNull(service.getAttributes());
		assertNull(service.getDefaultAttributeValue(null));
		assertNull(service.getServiceUIFactory());
		assertNull(service.getSupportedAttributeCategories());
		assertNull(service.getSupportedAttributeValues(null, null, null));
		assertNull(service.getUnsupportedAttributes(null, null));
		assertFalse(service.isAttributeCategorySupported(null));
		assertFalse(service.isAttributeValueSupported(null, null, null));
		service.addPrintServiceAttributeListener(null);
		service.removePrintServiceAttributeListener(null);
	}
}
