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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfAction;
import com.lowagie.text.pdf.PdfDestination;
import com.lowagie.text.pdf.PdfOutline;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Creates small PDFs in memory with OpenPDF and reads their text back with PDFBox.
 */
final class PdfTestSupport
{
	private PdfTestSupport()
	{
	}

	/** An A4 PDF with one page per text. */
	static byte[] pdf(String... pageTexts)
	{
		return pdf(false, pageTexts);
	}

	/** An A4 PDF with one page per text, optionally with a bookmark per page. */
	static byte[] pdf(boolean bookmarks, String... pageTexts)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4);
		PdfWriter writer = PdfWriter.getInstance(document, out);
		document.open();
		for (int i = 0; i < pageTexts.length; i++)
		{
			if (i > 0) document.newPage();
			document.add(new Paragraph(pageTexts[i]));
			if (bookmarks)
			{
				new PdfOutline(writer.getDirectContent().getRootOutline(),
					PdfAction.gotoLocalPage(i + 1, new PdfDestination(PdfDestination.FIT), writer), pageTexts[i]);
			}
		}
		document.close();
		return out.toByteArray();
	}

	/** The text of a page (1 based), read with PDFBox. */
	static String text(byte[] pdf, int page) throws IOException
	{
		try (PDDocument document = Loader.loadPDF(pdf))
		{
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(page);
			stripper.setEndPage(page);
			return stripper.getText(document);
		}
	}

	static int pages(byte[] pdf) throws IOException
	{
		try (PDDocument document = Loader.loadPDF(pdf))
		{
			return document.getNumberOfPages();
		}
	}

	/** A small red PNG. */
	static byte[] png() throws IOException
	{
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 10; x++)
		{
			for (int y = 0; y < 10; y++)
			{
				image.setRGB(x, y, Color.RED.getRGB());
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}
}
