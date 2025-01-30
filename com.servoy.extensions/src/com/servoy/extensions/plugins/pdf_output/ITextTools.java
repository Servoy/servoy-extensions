package com.servoy.extensions.plugins.pdf_output;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfEncryptor;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.servoy.j2db.Messages;

/**
 * Utility Adapted from the PDF Pro plugin with full approval from the author
 *
 * @author Scott Buttler
 * @author Patrick Talbot
 */
public class ITextTools
{

	/**
	 * Adds page numbers to the PDF provided as an {@link InputStream}<br/>
	 * Method adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @author Scott Buttler
	 *
	 * @param inputStream the PDF
	 * @param fontSize the font size to use
	 * @param locationX the x location of the number
	 * @param locationY the y location of the number
	 * @param font the font to use
	 * @param fontColor the font color to use
	 * @return the PDF with added numbered page
	 *
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static byte[] numberPDF(InputStream inputStream, int fontSize, int locationX, int locationY, String font, Color fontColor) throws IOException,
		DocumentException
	{
		//http://itext.ugent.be/library/com/lowagie/examples/general/copystamp/AddWatermarkPageNumbers.java

		PdfReader reader = new PdfReader(inputStream);
		int totalPages = reader.getNumberOfPages();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		// Create a writer for the outputstream
		PdfStamper stamp = new PdfStamper(reader, outputStream);
		try
		{
			int i = 0;
			PdfContentByte over = null;

			BaseFont bf = BaseFont.createFont(font, BaseFont.CP1252, BaseFont.EMBEDDED);
			while (i < totalPages)
			{
				i++;
				over = stamp.getOverContent(i);
				over.beginText();
				over.setColorFill(fontColor);
				over.setFontAndSize(bf, fontSize);
				over.setTextMatrix(locationX, locationY);// x, y
				over.showText("Page " + i + " of " + totalPages); //$NON-NLS-1$//$NON-NLS-2$
				over.endText();
			}

		}
		finally
		{
			stamp.close();
			outputStream.close();
		}
		return outputStream.toByteArray();

	}

	/**
	 * Adds a watermark to the PDF provided as an {@link InputStream}<br/>
	 * Method adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @author Scott Buttler
	 *
	 * @param inputStream the PDF
	 * @param watermark the image to use
	 * @param locationX the x location of the image
	 * @param locationY the y location of the image
	 * @param isOver whether the watermark should be put over the content
	 * @param pages an array of pages where to put the watermark on
	 *
	 * @return the PDF with added watermark
	 *
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static byte[] watermarkPDF(InputStream inputStream, Image watermark, int locationX, int locationY, boolean isOver, String[] pages)
		throws IOException, DocumentException
	{
		PdfReader reader = new PdfReader(inputStream);
		int totalPages = reader.getNumberOfPages();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		// Create a writer for the outputstream
		PdfStamper stamp = new PdfStamper(reader, outputStream);
		try
		{
			watermark.setAbsolutePosition(locationX, locationY);
			int i = 0;
			PdfContentByte under = null;

			int[] pagesToWatermark = convertStringArrayToIntArray(pages);

			while (i < totalPages)
			{
				i++;
				if (pagesToWatermark == null || Arrays.binarySearch(pagesToWatermark, i) >= 0)
				{
					if (isOver) under = stamp.getOverContent(i);
					else under = stamp.getUnderContent(i);
					under.beginText();
					under.addImage(watermark);
					under.endText();
				}
			}

		}
		finally
		{
			stamp.close();
			inputStream.close();
		}

		return outputStream.toByteArray();
	}

	/**
	 * Adds an overlay to the PDF provided as an {@link InputStream}<br/>
	 * Method adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @author Scott Buttler
	 *
	 * @param inputStream the base PDF
	 * @param mergeInputStream the PDF to use as overly
	 * @param isOver whether the overlay will be placed over the content
	 * @param pages an array of pages where to put the watermark on
	 *
	 * @return the PDF with added overlay
	 *
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static byte[] overlay(InputStream inputStream, InputStream mergeInputStream, boolean isOver, String[] pages) throws IOException, DocumentException
	{
		PdfReader reader = new PdfReader(inputStream);
		int totalPages = reader.getNumberOfPages();

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfStamper stamp = new PdfStamper(reader, outputStream);

		PdfContentByte under;

		PdfReader reader2 = new PdfReader(mergeInputStream);

		try
		{
			int i = 0;
			int[] pagesToStamp = convertStringArrayToIntArray(pages);

			if (pages != null)
			{
				while (i < totalPages)
				{
					i++;

					if (Arrays.binarySearch(pagesToStamp, i) >= 0)
					{
						if (isOver) under = stamp.getOverContent(i);
						else under = stamp.getUnderContent(i);

						under.addTemplate(stamp.getImportedPage(reader2, 1), 1, 0, 0, 1, 0, 0);
					}
				}
			}
			else
			{
				while (i < totalPages)
				{
					i++;

					if (isOver) under = stamp.getOverContent(i);
					else under = stamp.getUnderContent(i);

					under.addTemplate(stamp.getImportedPage(reader2, 1), 1, 0, 0, 1, 0, 0);
				}
			}
		}
		finally
		{
			stamp.close();
			inputStream.close();
			mergeInputStream.close();
		}

		return outputStream.toByteArray();
	}

	/**
	 * Adds a text overlay to the PDF provided as an {@link InputStream}<br/>
	 * Method adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @author Scott Buttler
	 *
	 * @param inputStream the base PDF
	 * @param text the text to use as overlay
	 * @param locationX the x location of the text
	 * @param locationY the y location of the text
	 * @param isOver whether the overlay will be placed over the content
	 * @param fontSize the font size to use
	 * @param font the font to use
	 * @param fontColor the font color to use
	 *
	 * @return the PDF with added overlay
	 *
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static byte[] overlayText(InputStream inputStream, String text, int locationX, int locationY, boolean isOver, int fontSize, String font,
		Color fontColor) throws IOException, DocumentException
	{
		return overlayText(inputStream, text, locationX, locationY, isOver, fontSize, font, fontColor, 45);
	}

	/**
	 * Adds a text overlay to the PDF provided as an {@link InputStream}<br/>
	 * Method adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @author Scott Buttler
	 *
	 * @param inputStream the base PDF
	 * @param text the text to use as overlay
	 * @param locationX the x location of the text
	 * @param locationY the y location of the text
	 * @param isOver whether the overlay will be placed over the content
	 * @param fontSize the font size to use
	 * @param font the font to use
	 * @param fontColor the font color to use
	 * @param angle the angle of the text
	 *
	 * @return the PDF with added overlay
	 *
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static byte[] overlayText(InputStream inputStream, String text, int locationX, int locationY, boolean isOver, int fontSize, String font,
		Color fontColor, int angle) throws IOException, DocumentException
	{
		PdfReader reader = new PdfReader(inputStream);
		int totalPages = reader.getNumberOfPages();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		// Create a writer for the outputstream
		PdfStamper stamp = new PdfStamper(reader, outputStream);

		try
		{
			int i = 0;
			PdfContentByte over = null;

			BaseFont bf = BaseFont.createFont(font, BaseFont.CP1252, BaseFont.EMBEDDED);
			while (i < totalPages)
			{
				i++;
				if (isOver) over = stamp.getOverContent(i);
				else over = stamp.getUnderContent(i);
				over.beginText();
				over.setColorFill(fontColor);
				over.setFontAndSize(bf, fontSize);
				over.showTextAligned(Element.ALIGN_CENTER, text, locationX, locationY, angle);
				over.endText();
			}
		}
		finally
		{
			stamp.close();
			outputStream.close();
		}

		return outputStream.toByteArray();
	}

	/**
	 * Adds meta data to the PDF provided as an {@link InputStream}<br/>
	 * Method adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @author Scott Buttler
	 *
	 * @param inputStream the PDF
	 * @param metaData the metaData to add
	 *
	 * @return the PDF with added metaData
	 *
	 * @throws DocumentException
	 * @throws IOException
	 */
	public static byte[] addMetaData(InputStream inputStream, Map<String, String> metaData) throws DocumentException, IOException
	{
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfStamper stamp = new PdfStamper(new PdfReader(inputStream), outputStream);
		try
		{
			stamp.setInfoDictionary(metaData);
		}
		finally
		{
			stamp.close();
			outputStream.close();
		}
		return outputStream.toByteArray();
	}

	/**
	 * Encrypt the PDF provided as an {@link InputStream}<br/>
	 * Method adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @author Scott Buttler
	 *
	 * @param inputStream the PDF
	 * @param ownerPassword the owner password
	 * @param userPassword the user password
	 * @param permissions the permissions to set
	 * @param is128bit whether the encryption is 128 bit
	 * @param metaData metaData to add
	 *
	 * @return the encrypted PDF
	 *
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static byte[] encrypt(InputStream inputStream, String ownerPassword, String userPassword, int permissions, boolean is128bit,
		Map<String, String> metaData)
		throws IOException, DocumentException
	{
		PdfReader reader = new PdfReader(inputStream);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		if (metaData != null)
		{
			PdfEncryptor.encrypt(reader, outputStream, userPassword.getBytes(), ownerPassword.getBytes(), permissions, is128bit, metaData);
		}
		else
		{
			PdfEncryptor.encrypt(reader, outputStream, userPassword.getBytes(), ownerPassword.getBytes(), permissions, is128bit);
		}

		return outputStream.toByteArray();
	}

	/**
	 * Utility method to transform an array of String into an array of int<br/>
	 * Method adapted from the PDF Pro plugin with full approval from the author
	 *
	 * @author Scott Buttler
	 *
	 * @param arr the String array to convert
	 * @return an array of int
	 */
	private static int[] convertStringArrayToIntArray(String[] arr)
	{
		try
		{
			if (arr == null) return null;

			int[] intArr = new int[arr.length];
			for (int i = 0; i < arr.length; i++)
			{
				intArr[i] = Integer.parseInt(arr[i]);
			}

			return intArr;
		}
		catch (Exception e)
		{
			return null;
		}
	}


	/**
	 * Recursive utility method to convert JavaScript objects to Map
	 *
	 * @author Patrick Talbot
	 *
	 * @param object a Scriptable to convert to a {@link Map}
	 *
	 * @return a Map constructed from the Scriptable
	 */
	public static Map<String, String> getMapFromScriptable(final Object object)
	{
		Map<String, String> parameters = null;
		if ((object != null) && (object instanceof Scriptable))
		{
			final Scriptable obj = (Scriptable)object;
			final Object[] ids = obj.getIds();
			if ((ids != null) && (ids.length > 0))
			{
				for (final Object id2 : ids)
				{
					final String key = id2.toString();
					Object value = obj.get(key, obj);
					if (value != null)
					{
						if (parameters == null)
						{
							parameters = new HashMap<String, String>();
						}
						if (value instanceof NativeArray)
						{
							value = unwrapNativeArray(value);
						}
						if (value instanceof Scriptable)
						{
							value = getMapFromScriptable(value);
						}
						if (value instanceof NativeJavaObject)
						{
							value = unwrapNativeJavaObject(value);
						}
						if (value instanceof String)
						{
							value = Messages.getStringIfPrefix((String)value);
						}

						parameters.put(key, value != null ? value.toString() : null);
					}
				}
			}
		}
		return parameters;
	}

	/**
	 * Retrieve an array of {@link Object}s from a wrapped object
	 *
	 * @author Patrick Talbot
	 *
	 * @param o the object to unwrap as an array
	 * @return an Array of Object build from the passed object
	 */
	private static Object[] unwrapNativeArray(final Object o)
	{
		if (o != null)
		{
			if (o instanceof NativeArray)
			{
				return (Object[])((NativeArray)o).unwrap();
			}
			else if (o instanceof Object[])
			{
				return (Object[])o;
			}
			else
			{
				return new Object[] { o };
			}
		}
		return null;
	}


	/**
	 * Unwrap a {@link NativeJavaObject}
	 *
	 * @author Patrick Talbot
	 *
	 * @param o the object to unwrap
	 * @return the object unwrapped
	 */
	private static Object unwrapNativeJavaObject(final Object o)
	{
		if (o != null && o instanceof NativeJavaObject)
		{
			return ((NativeJavaObject)o).unwrap();
		}
		return o;
	}

	/**
	 * Tests if a {@link String} is null or empty
	 *
	 * @author Patrick Talbot
	 *
	 * @param s the String
	 * @return true if the String is null or empty
	 */
	public static boolean isNullOrEmpty(String s)
	{
		return s == null || s.trim().length() == 0;
	}

	/**
	 * Tests if a {@link Map} is null or empty
	 *
	 * @author Patrick Talbot
	 *
	 * @param map the Map
	 * @return true if the map is null or empty
	 */
	public static boolean isNullOrEmpty(Map< ? , ? > map)
	{
		return map == null || map.size() == 0;
	}
}
