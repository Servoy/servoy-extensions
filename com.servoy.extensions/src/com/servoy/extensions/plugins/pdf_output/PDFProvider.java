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
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.mozilla.javascript.Scriptable;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.AcroFields;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.FdfReader;
import com.lowagie.text.pdf.PRAcroForm;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.SimpleBookmark;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IRuntimeWindow;
import com.servoy.j2db.plugins.ISmartRuntimeWindow;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.FileChooserUtils;

/**
 * @author jblok
 */
@ServoyDocumented(publicName = PDFPlugin.PLUGIN_NAME, scriptingName = "plugins." + PDFPlugin.PLUGIN_NAME)
@ServoyClientSupport(ng = true, wc = true, sc = true)
public class PDFProvider implements IScriptable
{
	private final PDFPlugin plugin;
	private PDFPrinterJob metaPrintJob;
	private File pdfFile = null;

	public PDFProvider(PDFPlugin plugin)
	{
		this.plugin = plugin;
	}

	/**
	 * Returns a PDF printer that can be used in print calls. Returns the last started meta print job.
	 *
	 * @sample
	 * //to print current record without printdialog to pdf file in temp dir.
	 * controller.print(true,false,plugins.pdf_output.getPDFPrinter());
	 */
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public PrinterJob js_getPDFPrinter()
	{
		return metaPrintJob;
	}

	/**
	 * Returns a PDF printer that can be used in print calls. The PDF printer that generates a PDF into the specified file is returned.
	 *
	 * @sample
	 * //to print current record without printdialog to pdf file in temp dir.
	 * controller.print(true,false,plugins.pdf_output.getPDFPrinter('c:/temp/out.pdf'));
	 *
	 * @param filename the file name
	 */
	@SuppressWarnings("nls")
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public PrinterJob js_getPDFPrinter(String filename)
	{
		try
		{
			if (filename != null)
			{
				File f = new File(filename);
				IClientPluginAccess access = plugin.getClientPluginAccess();
				IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
				Window currentWindow = null;
				if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
				pdfFile = FileChooserUtils.getAWriteFile(currentWindow, f, false);
				FileOutputStream os = new FileOutputStream(pdfFile);
				return new PDFPrinterJob(os, false);
			}
			else
			{
				return metaPrintJob;
			}
		}
		catch (Exception e)
		{
			Debug.error(e);
			throw new RuntimeException("Error getting a PDF PrinterJob for " + filename, e);
		}
	}

	/**
	 * Used for printing multiple things into the same PDF document. Starts a meta print job and all print calls made before ending the meta print job will be done into the same PDF document. The PDF document is stored in memory and can be retrieved when ending the meta print job and can be saved, for example, into a dataprovider.
	 *
	 * @sampleas js_endMetaPrintJob()
	 */
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public boolean js_startMetaPrintJob()
	{
		return js_startMetaPrintJob(null);
	}

	/**
	 * Used for printing multiple things into the same PDF document. Starts a meta print job and all print calls made before ending the meta print job will be done into the same PDF document. The PDF document is generated in a File specified by the filename.
	 *
	 * @sampleas js_endMetaPrintJob()
	 *
	 * @param filename the file name
	 */
	@SuppressWarnings("nls")
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public boolean js_startMetaPrintJob(String filename)
	{
		try
		{
			OutputStream os = null;
			if (filename != null)
			{
				File f = new File(filename.toString());
				IClientPluginAccess access = plugin.getClientPluginAccess();
				IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
				Window currentWindow = null;
				if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
				pdfFile = FileChooserUtils.getAWriteFile(currentWindow, f, false);
				os = new FileOutputStream(pdfFile);
			}
			else
			{
				os = new ByteArrayOutputStream();
			}
			metaPrintJob = new PDFPrinterJob(os, true);
			return true;
		}
		catch (Exception e)
		{
			Debug.error(e);
			String message = "Error starting a meta PrinterJob";
			if (filename != null)
			{
				message += " for " + filename;
			}
			throw new RuntimeException(message, e);
		}
	}

	/**
	 * Ends a previously started meta print job. For meta print jobs that were stored in memory, not in a file on disk, also returns the content of the generated PDF document.
	 *
	 * @sample
	 * //to print multiple forms to one pdf document (on file system).
	 * var success = plugins.pdf_output.startMetaPrintJob('c:/temp/out.pdf')
	 * if (success)
	 * {
	 * 	forms.form_one.controller.print(false,false,plugins.pdf_output.getPDFPrinter());
	 * 	application.output('form one printed ' + plugins.pdf_output.getPagesPrinted() + ' pages.');
	 * 	forms.form_two.controller.print(false,false,plugins.pdf_output.getPDFPrinter());
	 * 	application.output('form two printed ' + plugins.pdf_output.getPagesPrinted() + ' pages.');
	 * }
	 * application.output('total printed pages: ' + plugins.pdf_output.getTotalPagesPrinted());
	 * plugins.pdf_output.endMetaPrintJob()
	 *
	 * //to print multiple forms to one pdf document (to store in dataprovider).
	 * var success = plugins.pdf_output.startMetaPrintJob()
	 * if (success)
	 * {
	 * 	forms.form_one.controller.print(false,false,plugins.pdf_output.getPDFPrinter());
	 * 	application.output('form one printed ' + plugins.pdf_output.getPagesPrinted() + ' pages.');
	 * 	forms.form_two.controller.print(false,false,plugins.pdf_output.getPDFPrinter());
	 * 	application.output('form two printed ' + plugins.pdf_output.getPagesPrinted() + ' pages.');
	 * }
	 * application.output('total printed pages: ' + plugins.pdf_output.getTotalPagesPrinted());
	 * mediaDataProvider = plugins.pdf_output.endMetaPrintJob()
	 */
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public byte[] js_endMetaPrintJob()
	{
		byte[] retval = null;
		if (metaPrintJob != null)
		{
			retval = metaPrintJob.close();
			if (pdfFile != null && metaPrintJob.getTotalPagesPrinted() == 0)
			{
				pdfFile.delete();//zero byte files makes no sense to leave
			}
			metaPrintJob = null;
		}
		pdfFile = null;
		return retval;
	}

	/**
	 * Add a directory that should be searched for fonts. Call this only in the context of an active meta print job.
	 *
	 * @sample
	 * //Insert font directories for font embedding.
	 * //You must create an MetaPrintJob before using it.
	 * plugins.pdf_output.insertFontDirectory('c:/Windows/Fonts');
	 * plugins.pdf_output.insertFontDirectory('c:/WinNT/Fonts');
	 * plugins.pdf_output.insertFontDirectory('/Library/Fonts');
	 *
	 * @param path the path to use
	 */
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public int js_insertFontDirectory(String path)
	{
		if (metaPrintJob != null)
		{
			return metaPrintJob.insertDirectory(path);
		}
		return -1;
	}

	/**
	 * Combine multiple protected PDF docs into one.
	 * Note: this function may fail when creating large PDF files due to lack of available heap memory. To compensate, please configure the application server with more heap memory via -Xmx parameter.
	 *
	 * @sample
	 * pdf_blob_column = combineProtectedPDFDocuments(new Array(pdf_blob1,pdf_blob2,pdf_blob3), new Array(pdf_blob1_pass,pdf_blob2_pass,pdf_blob3_pass));
	 *
	 * @param pdf_docs_bytearrays  the array of documents to combine
	 * @param pdf_docs_passwords an array of passwords to use
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_combineProtectedPDFDocuments(Object[] pdf_docs_bytearrays, Object[] pdf_docs_passwords)
	{
		if (pdf_docs_bytearrays == null || pdf_docs_bytearrays.length == 0) return null;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try
		{
			int pageOffset = 0;
			List master = new ArrayList();
			Document document = null;
			PdfCopy writer = null;
			for (int f = 0; f < pdf_docs_bytearrays.length; f++)
			{
				if (!(pdf_docs_bytearrays[f] instanceof byte[])) continue;
				byte[] pdf_file = (byte[])pdf_docs_bytearrays[f];

				// we create a reader for a certain document
				byte[] password = null;
				if (pdf_docs_passwords != null && pdf_docs_passwords.length > f && pdf_docs_passwords[f] != null)
				{
					if (pdf_docs_passwords[f] instanceof String) password = pdf_docs_passwords[f].toString().getBytes();
				}
				PdfReader reader = new PdfReader(pdf_file, password);
				reader.consolidateNamedDestinations();
				// we retrieve the total number of pages
				int n = reader.getNumberOfPages();
				List bookmarks = SimpleBookmark.getBookmark(reader);
				if (bookmarks != null)
				{
					if (pageOffset != 0)
					{
						SimpleBookmark.shiftPageNumbers(bookmarks, pageOffset, null);
					}
					master.addAll(bookmarks);
				}
				pageOffset += n;

				if (writer == null)
				{
					// step 1: creation of a document-object
					document = new Document(reader.getPageSizeWithRotation(1));
					// step 2: we create a writer that listens to the document
					writer = new PdfCopy(document, baos);
					// step 3: we open the document
					document.open();
				}

				// step 4: we add content
				PdfImportedPage page;
				for (int i = 0; i < n;)
				{
					++i;
					page = writer.getImportedPage(reader, i);
					writer.addPage(page);
				}
				PRAcroForm form = reader.getAcroForm();
				if (form != null) writer.copyAcroForm(reader);
			}
			if (writer != null && document != null)
			{
				if (master.size() > 0) writer.setOutlines(master);
				// step 5: we close the document
				document.close();
			}
			return baos.toByteArray();
		}
		catch (Throwable e)
		{
			Debug.error(e);
			throw new RuntimeException("Error combinding pdf documents: " + e.getMessage(), e); //$NON-NLS-1$
		}
	}

	/**
	 * Combine multiple PDF docs into one.
	 * Note: this function may fail when creating large PDF files due to lack of available heap memory. To compensate, please configure the application server with more heap memory via -Xmx parameter.
	 *
	 * @sample
	 * pdf_blob_column = combinePDFDocuments(new Array(pdf_blob1,pdf_blob2,pdf_blob3));
	 *
	 * @param pdf_docs_bytearrays the array of documents to combine
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_combinePDFDocuments(Object[] pdf_docs_bytearrays)
	{
		return js_combineProtectedPDFDocuments(pdf_docs_bytearrays, null);
	}

	/**
	 * Convert a protected PDF form to a PDF document.
	 *
	 * @sample
	 * var pdfform = plugins.file.readFile('c:/temp/1040a-form.pdf');
	 * //var field_values = plugins.file.readFile('c:/temp/1040a-data.fdf');//read adobe fdf values or
	 * var field_values = new Array()//construct field values
	 * field_values[0] = 'f1-1=John C.J.'
	 * field_values[1] = 'f1-2=Longlasting'
	 * var result_pdf_doc = plugins.pdf_output.convertProtectedPDFFormToPDFDocument(pdfform, 'pdf_password', field_values)
	 * if (result_pdf_doc != null)
	 * {
	 * 	plugins.file.writeFile('c:/temp/1040a-flatten.pdf', result_pdf_doc)
	 * }
	 *
	 * @param pdf_form the PDF Form to convert
	 * @param pdf_password the password to use
	 * @param field_values the field values to use
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_convertProtectedPDFFormToPDFDocument(byte[] pdf_form, String pdf_password, Object field_values)
	{
		try
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PdfReader reader = new PdfReader(pdf_form, pdf_password != null ? pdf_password.getBytes() : null);
			PdfStamper stamp = new PdfStamper(reader, baos);
			if (field_values != null)
			{
				AcroFields form = stamp.getAcroFields();
				if (field_values instanceof String)
				{
					FdfReader fdf = new FdfReader((String)field_values);
					form.setFields(fdf);
				}
				else if (field_values instanceof byte[])
				{
					FdfReader fdf = new FdfReader((byte[])field_values);
					form.setFields(fdf);
				}
				else if (field_values instanceof Object[])
				{
					for (int i = 0; i < ((Object[])field_values).length; i++)
					{
						Object obj = ((Object[])field_values)[i];
						if (obj instanceof Object[])
						{
							Object[] row = (Object[])obj;
							if (row.length >= 2)
							{
								Object field = row[0];
								Object value = row[1];
								if (field != null && value != null)
								{
									form.setField(field.toString(), value.toString());
								}
							}
//							else if (row.length >= 3)
//							{
//			                	form.setField(field, value);
//							}
						}
						else if (obj instanceof String)
						{
							String s = (String)obj;
							int idx = s.indexOf('=');
							form.setField(s.substring(0, idx), s.substring(idx + 1));
						}
					}
				}
			}
			stamp.setFormFlattening(true);
			stamp.close();
			return baos.toByteArray();
		}
		catch (Exception e)
		{
			Debug.error(e);
			throw new RuntimeException("Error converting pdf form to pdf document", e); //$NON-NLS-1$
		}
	}

	/**
	 * Convert a PDF form to a PDF document.
	 *
	 * @sample
	 * var pdfform = plugins.file.readFile('c:/temp/1040a-form.pdf');
	 * //var field_values = plugins.file.readFile('c:/temp/1040a-data.fdf');//read adobe fdf values or
	 * var field_values = new Array()//construct field values
	 * field_values[0] = 'f1-1=John C.J.'
	 * field_values[1] = 'f1-2=Longlasting'
	 * var result_pdf_doc = plugins.pdf_output.convertPDFFormToPDFDocument(pdfform, field_values)
	 * if (result_pdf_doc != null)
	 * {
	 * 	plugins.file.writeFile('c:/temp/1040a-flatten.pdf', result_pdf_doc)
	 * }
	 *
	 * @param pdf_form the PDF Form to convert
	 * @param field_values the values to use
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_convertPDFFormToPDFDocument(byte[] pdf_form, Object field_values)
	{
		return js_convertProtectedPDFFormToPDFDocument(pdf_form, null, field_values);
	}

	/**
	 * Returns the number of pages printed by the last print call done in the context of a meta print job.
	 *
	 * @sampleas js_endMetaPrintJob()
	 */
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public int js_getPagesPrinted()
	{
		if (metaPrintJob != null)
		{
			return metaPrintJob.getPagesPrinted();
		}
		return 0;
	}

	/**
	 * Returns the total number of pages printed in the context of a meta print job. Call this method before ending the meta print job.
	 *
	 * @sampleas js_endMetaPrintJob()
	 */
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public int js_getTotalPagesPrinted()
	{
		if (metaPrintJob != null)
		{
			return metaPrintJob.getTotalPagesPrinted();
		}
		return 0;
	}

	/**
	 * Add metadata to the PDF, like Author
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sample
	 * // Add metadata to the PDF, like Author
	 * var pdf = plugins.file.showFileOpenDialog();
	 * if (pdf) {
	 * 	var data = plugins.file.readFile(pdf);
	 * 	var metaData = { Author: 'Servoy' };
	 * 	pdfResult = %%elementName%%.addMetaData(data, metaData);
	 * }
	 *
	 * @param data the PDF
	 * @param metaData a JavaScript object ({@link Scriptable}) that contains the metadata as property/value pairs
	 *
	 * @return the PDF with metaData added
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public byte[] js_addMetaData(byte[] data, Scriptable metaData) throws Exception
	{

		if (data == null || metaData == null) throw new IllegalArgumentException("Missing argument"); //$NON-NLS-1$

		Map<String, Object> map = ITextTools.getMapFromScriptable(metaData);
		if (ITextTools.isNullOrEmpty(map)) throw new IllegalArgumentException("No metadata to add"); //$NON-NLS-1$

		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		return ITextTools.addMetaData(bais, map);

	}


	/**
	 *  Add password protection and security options to the PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sample
	 * // Add password protection and security options to the PDF
	 * // NOTE: Passwords are case sensitive
	 * var unEncryptedFile = plugins.file.showFileOpenDialog();
	 * if (unEncryptedFile) {
	 * 	var data = plugins.file.readFile(unEncryptedFile);
	 * 	encryptedResult = %%elementName%%.encrypt(data, 'secretPassword', 'secretUserPassword', false, false, false, false, false, false, false, false, true);
	 * }
	 *
	 * @param data the PDF
	 * @param ownerPassword the owner password
	 *
	 * @return the encrypted PDF
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_encrypt(byte[] data, String ownerPassword) throws Exception
	{
		return js_encrypt(data, ownerPassword, ownerPassword, true, true, true, true, true, true, true, true, true, null);
	}

	/**
	 *  Add password protection and security options to the PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_encrypt(byte[], String)
	 *
	 * @param data the PDF
	 * @param ownerPassword the owner password
	 * @param userPassword the user password
	 *
	 * @return the encrypted PDF
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_encrypt(byte[] data, String ownerPassword, String userPassword) throws Exception
	{
		return js_encrypt(data, ownerPassword, userPassword, true, true, true, true, true, true, true, true, true, null);
	}

	/**
	 *  Add password protection and security options to the PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_encrypt(byte[], String)
	 *
	 * @param data the PDF
	 * @param ownerPassword the owner password
	 * @param userPassword the user password
	 * @param allowAssembly whether to set the allow assembly permission
	 * @param allowCopy whether to set the allow copy permission
	 * @param allowDegradedPrinting whether to set the allow degraded printing permission
	 * @param allowFillIn whether to set the allow fill in permission
	 * @param allowModifyAnnotations whether to set the allow modify annotations permission
	 * @param allowModifyContents whether to set the allow modify contents permission
	 * @param allowPrinting whether to set the allow printing permission
	 * @param allowScreenreaders whether to set the allow screen readers permission
	 *
	 * @return the encrypted PDF
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_encrypt(byte[] data, String ownerPassword, String userPassword, boolean allowAssembly, boolean allowCopy, boolean allowDegradedPrinting,
		boolean allowFillIn, boolean allowModifyAnnotations, boolean allowModifyContents, boolean allowPrinting, boolean allowScreenreaders) throws Exception
	{
		return js_encrypt(data, ownerPassword, userPassword, allowAssembly, allowCopy, allowDegradedPrinting, allowFillIn, allowModifyAnnotations,
			allowModifyContents, allowPrinting, allowScreenreaders, true, null);
	}

	/**
	 *  Add password protection and security options to the PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_encrypt(byte[], String)
	 *
	 * @param data the PDF
	 * @param ownerPassword the owner password
	 * @param userPassword the user password
	 * @param allowAssembly whether to set the allow assembly permission
	 * @param allowCopy whether to set the allow copy permission
	 * @param allowDegradedPrinting whether to set the allow degraded printing permission
	 * @param allowFillIn whether to set the allow fill in permission
	 * @param allowModifyAnnotations whether to set the allow modify annotations permission
	 * @param allowModifyContents whether to set the allow modify contents permission
	 * @param allowPrinting whether to set the allow printing permission
	 * @param allowScreenreaders whether to set the allow screen readers permission
	 * @param is128bit whether to use 128-bit encryption
	 *
	 * @return the encrypted PDF
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_encrypt(byte[] data, String ownerPassword, String userPassword, boolean allowAssembly, boolean allowCopy, boolean allowDegradedPrinting,
		boolean allowFillIn, boolean allowModifyAnnotations, boolean allowModifyContents, boolean allowPrinting, boolean allowScreenreaders, boolean is128bit)
		throws Exception
	{
		return js_encrypt(data, ownerPassword, userPassword, allowAssembly, allowCopy, allowDegradedPrinting, allowFillIn, allowModifyAnnotations,
			allowModifyContents, allowPrinting, allowScreenreaders, is128bit, null);
	}

	/**
	 * Add password protection and security options to the PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_encrypt(byte[], String)
	 *
	 * @param data the PDF
	 * @param ownerPassword the owner password
	 * @param userPassword the user password
	 * @param allowAssembly whether to set the allow assembly permission
	 * @param allowCopy whether to set the allow copy permission
	 * @param allowDegradedPrinting whether to set the allow degraded printing permission
	 * @param allowFillIn whether to set the allow fill in permission
	 * @param allowModifyAnnotations whether to set the allow modify annotations permission
	 * @param allowModifyContents whether to set the allow modify contents permission
	 * @param allowPrinting whether to set the allow printing permission
	 * @param allowScreenreaders whether to set the allow screen readers permission
	 * @param is128bit whether to use 128-bit encryption
	 * @param metaData a JavaScript object ({@link Scriptable}) that contains the metadata as property/value pairs
	 *
	 * @return the encrypted PDF
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_encrypt(byte[] data, String ownerPassword, String userPassword, boolean allowAssembly, boolean allowCopy, boolean allowDegradedPrinting,
		boolean allowFillIn, boolean allowModifyAnnotations, boolean allowModifyContents, boolean allowPrinting, boolean allowScreenreaders, boolean is128bit,
		Scriptable metaData) throws Exception
	{
		if (data == null) throw new IllegalArgumentException("Missing argument"); //$NON-NLS-1$
		int sec = 0;
		if (allowAssembly)
		{
			sec = sec | PdfWriter.ALLOW_ASSEMBLY;
		}
		if (allowCopy)
		{
			sec = sec | PdfWriter.ALLOW_COPY;
		}
		if (allowDegradedPrinting)
		{
			sec = sec | PdfWriter.ALLOW_DEGRADED_PRINTING;
		}
		if (allowFillIn)
		{
			sec = sec | PdfWriter.ALLOW_FILL_IN;
		}
		if (allowModifyAnnotations)
		{
			sec = sec | PdfWriter.ALLOW_MODIFY_ANNOTATIONS;
		}
		if (allowModifyContents)
		{
			sec = sec | PdfWriter.ALLOW_MODIFY_CONTENTS;
		}
		if (allowPrinting)
		{
			sec = sec | PdfWriter.ALLOW_PRINTING;
		}
		if (allowScreenreaders)
		{
			sec = sec | PdfWriter.ALLOW_SCREENREADERS;
		}
		Map<String, Object> map = ITextTools.getMapFromScriptable(metaData);

		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		return ITextTools.encrypt(bais, ownerPassword, userPassword, sec, is128bit, map);
	}


	/**
	 * Add pages numbers to a PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sample
	 * // Add pages numbers to a PDF
	 * var unNumberedFile = plugins.file.showFileOpenDialog();
	 * if (unNumberedFile) {
	 * 	var data = plugins.file.readFile(unNumberedFile);
	 * 	pageNumberedPdf = %%elementName%%.numberPages(data, 12, 520, 30, 'Courier', '#ff0033');
	 * }
	 *
	 * @param data the PDF
	 *
	 * @return the PDF with numbered pages
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_numberPages(byte[] data) throws Exception
	{
		return js_numberPages(data, 10, 520, 30, BaseFont.HELVETICA, "#000000"); //$NON-NLS-1$
	}

	/**
	 * Add pages numbers to a PDF
	 *
	 * @sampleas js_numberPages(byte[])
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @param data the PDF
	 * @param fontSize the font size to use
	 * @param locationX the x location of the numbers
	 * @param locationY the y location of the numbers
	 * @param font the font to use
	 * @param hexColor the font color to use
	 *
	 * @return the PDF with numbered pages
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_numberPages(byte[] data, int fontSize, int locationX, int locationY, String font, String hexColor) throws Exception
	{
		if (data == null) throw new IllegalArgumentException("Missing argument"); //$NON-NLS-1$

		Color myColor = Color.decode(hexColor);

		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		return ITextTools.numberPDF(bais, fontSize, locationX, locationY, font, myColor);
	}


	/**
	 * Add an image as a watermark on every page, or the pages specified as a parameter
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sample
	 * // Add an image as a watermark on every page, or the pages specified as a parameter.
	 * var pdf = plugins.file.showFileOpenDialog();
	 * if (pdf) {
	 * 	var data = plugins.file.readFile(pdf);
	 * 	var image = plugins.file.showFileOpenDialog();
	 * 	modifiedPdf = %%elementName%%.watermark(data, image);
	 * }
	 *
	 * @param data the PDF
	 * @param image the path of an image to use or array of bytes containing actual image
	 *
	 * @return the PDF with added watermak
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_watermark(byte[] data, Object image) throws Exception
	{
		return js_watermark(data, image, 200, 400, false, null);
	}

	/**
	 * Add an image as a watermark on every page, or the pages specified as a parameter
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_watermark(byte[], String)
	 *
	 * @param data the PDF
	 * @param image the path of an image to use or array of bytes containing actual image
	 * @param locationX the x location of the image
	 * @param locationY the y location of the image
	 * @param isOver whether to put over the content
	 *
	 * @return the PDF with added watermak
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_watermark(byte[] data, Object image, int locationX, int locationY, boolean isOver) throws Exception
	{
		return js_watermark(data, image, locationX, locationY, isOver, null);
	}

	/**
	 * Add an image as a watermark on every page, or the pages specified as a parameter
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_watermark(byte[], String)
	 *
	 * @param data the PDF
	 * @param image the path of an image to use or array of bytes containing actual image
	 * @param locationX the x location of the image
	 * @param locationY the y location of the image
	 * @param isOver whether to put over the content
	 * @param pages an array of pages where to apply the watermark
	 *
	 * @return the PDF with added watermak
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_watermark(byte[] data, Object image, int locationX, int locationY, boolean isOver, String[] pages) throws Exception
	{
		if (data == null) throw new IllegalArgumentException("Missing argument"); //$NON-NLS-1$

		Image watermark = null;
		if (image instanceof String)
		{
			watermark = Image.getInstance((String)image);
		}
		else if (image instanceof byte[])
		{
			watermark = Image.getInstance((byte[])image);
		}
		else
		{
			throw new IllegalArgumentException("Image must be a path or array of bytes: " + image); //$NON-NLS-1$
		}
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		return ITextTools.watermarkPDF(bais, watermark, locationX, locationY, isOver, pages);
	}


	/**
	 * Add some PDF based content over a PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sample
	 * // Add some PDF based content over a PDF
	 * var pages = new Array();
	 * pages[0] = '1';
	 * pages[1] = '3';
	 * pages[2] = '5';
	 * var input1 = plugins.file.showFileOpenDialog(1,null,false,'pdf',null,'Select source file');
	 * if (input1) {
	 * 	var data = plugins.file.readFile(input1);
	 * 	var input2 = plugins.file.showFileOpenDialog(1,null,false,'pdf',null,'Select file for overlay');
	 * 	if (input2) {
	 * 		var data2 = plugins.file.readFile(input2);
	 * 		overlayedPdf = %%elementName%%.overlay( data, data2, false, pages );
	 * 		//overlayedPdf = %%elementName%%.overlay( data, data2 );
	 * 		//overlayedPdf = %%elementName%%.overlay( data, data2, false, null );
	 * 		//overlayedPdf = %%elementName%%.overlay( data, data2, pages );
	 * 	}
	 * }
	 *
	 * @param data the PDF
	 * @param forOverlay a PDF to use as overlay
	 *
	 * @return the PDF with added overlay
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_overlay(byte[] data, byte[] forOverlay) throws Exception
	{
		return js_overlay(data, forOverlay, false, null);
	}

	/**
	 * Add some PDF based content over a PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_overlay(byte[], byte[])
	 *
	 * @param data the PDF
	 * @param forOverlay a PDF to use as overlay
	 * @param pages an array of page numbers to put the overlay on
	 *
	 * @return the PDF with added overlay
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_overlay(byte[] data, byte[] forOverlay, String[] pages) throws Exception
	{
		return js_overlay(data, forOverlay, false, pages);
	}

	/**
	 * Add some PDF based content over a PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_overlay(byte[], byte[])
	 *
	 * @param data the PDF
	 * @param forOverlay a PDF to use as overlay
	 * @param isOver whether the overlay will be put over the content
	 *
	 * @return the PDF with added overlay
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_overlay(byte[] data, byte[] forOverlay, boolean isOver) throws Exception
	{
		return js_overlay(data, forOverlay, isOver, null);
	}


	/**
	 * Add some PDF based content over a PDF
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_overlay(byte[], byte[])
	 *
	 * @param data the PDF
	 * @param forOverlay a PDF to use as overlay
	 * @param isOver whether the overlay will be put over the content
	 * @param pages an array of page numbers to put the overlay on
	 *
	 * @return the PDF with added overlay
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_overlay(byte[] data, byte[] forOverlay, boolean isOver, String[] pages) throws Exception
	{
		if (data == null || forOverlay == null) throw new IllegalArgumentException("Missing argument"); //$NON-NLS-1$

		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		ByteArrayInputStream foais = new ByteArrayInputStream(forOverlay);
		return ITextTools.overlay(bais, foais, isOver, pages);
	}


	/**
	 * Add text over every page at a 45 degree angle
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sample
	 * // Add text over every page at a 45 degree angle\m
	 * var pdf = plugins.file.showFileOpenDialog();
	 * if (pdf) {
	 * 	var data = plugins.file.readFile(pdf);
	 * 	modifiedPdf = %%elementName%%.overlayText(data, 'DRAFT', 230, 430, true, 32, 'Helvetica', '#33ff33');
	 * }
	 *
	 * @param data the PDF
	 * @param text the text to use for the overlay
	 *
	 * @return the PDF with added overlay
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_overlayText(byte[] data, String text) throws Exception
	{
		return js_overlayText(data, text, 230, 430, true, 32, BaseFont.HELVETICA, "#000000"); //$NON-NLS-1$
	}

	/**
	 * Add text over every page at a 45 degree angle
	 *
	 * @author Scott Buttler
	 * Adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @sampleas js_overlayText(byte[], String)
	 *
	 * @param data the PDF
	 * @param text the text to use for the overlay
	 * @param locationX the x location of the overlay
	 * @param locationY the y location of the overlay
	 * @param isOver whether to put the overlay over the content
	 * @param fontSize the font size to use
	 * @param font the font to use
	 * @param hexColor the font color to use
	 *
	 * @return the PDF with added overlay
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_overlayText(byte[] data, String text, int locationX, int locationY, boolean isOver, int fontSize, String font, String hexColor)
		throws Exception
	{
		if (data == null || ITextTools.isNullOrEmpty(text) || ITextTools.isNullOrEmpty(font) || ITextTools.isNullOrEmpty(hexColor))
		{
			throw new IllegalArgumentException("Missing argument"); //$NON-NLS-1$
		}

		Color myColor = Color.decode(hexColor);

		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		return ITextTools.overlayText(bais, text, locationX, locationY, isOver, fontSize, font, myColor);
	}

	/**
	 * Create a thumbnail from the provided PDF
	 *
	 * @author mvid
	 *
	 * @sample
	 * //read PDF file data
	 * var myPdf = plugins.file.readFile();
	 * //get the thumbnail (default the first page is rendered with 72 dpi resolution)
	 * var pngImg = plugins.pdf_output.getThumbnailImage(myPdf);
	 * //save PNG image to file
	 * var thumbnailFile = plugins.file.convertToJSFile()
	 * plugins.file.writeFile(thumbnailFile, myThumbnailImg);
	 *
	 * @param data the PDF
	 *
	 * @return the PDF thumbnail as PNG format
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_getThumbnailImage(byte[] data) throws Exception
	{
		return js_getThumbnailImage(data, 0);
	}

	/**
	 * Create a thumbnail from the provided PDF
	 *
	 * @author mvid
	 *
	 * @sampleas js_getThumbnailImage(byte[])
	 *
	 * @param data the PDF
	 * @param int pageNumber to get thumbnail of. This parameter is zero based index.
	 *
	 * @return the PDF thumbnail as PNG format
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_getThumbnailImage(byte[] data, int pageNumber) throws Exception
	{
		return js_getThumbnailImage(data, pageNumber, 72);
	}

	/**
	 * Create a thumbnail from the provided PDF
	 *
	 * @author mvid
	 *
	 * @sampleas js_getThumbnailImage(byte[])
	 *
	 * @param data the PDF
	 * @param int pageNumber to get thumbnail of. This parameter is zero based index.
	 * @param int thumbnail resolution (dpi)
	 *
	 * @return the PDF thumbnail as PNG format
	 *
	 * @throws Exception
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public byte[] js_getThumbnailImage(byte[] data, int pageNumber, int dpi) throws Exception
	{
		PDDocument pdfDoc = PDDocument.load(data);
		PDFRenderer pdfRenderer = new PDFRenderer(pdfDoc);
		BufferedImage myImage = pdfRenderer.renderImageWithDPI(pageNumber, dpi);
		ByteArrayOutputStream baos = new ByteArrayOutputStream(myImage.getData().getDataBuffer().getSize());
		ImageIO.write(myImage, "png", baos);
		baos.flush();
		byte[] imgArray = baos.toByteArray();
		baos.close();
		return imgArray;
	}

}
