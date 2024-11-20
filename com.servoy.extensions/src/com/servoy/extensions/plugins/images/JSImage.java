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
package com.servoy.extensions.plugins.images;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.TreeMap;

import org.mozilla.javascript.Wrapper;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.Rational;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.jpeg.JpegComponent;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.ImageLoader;
import com.servoy.j2db.util.MimeTypes;
import com.servoy.j2db.util.Utils;

/**
 * <p>The <code>JSImage</code> class provides a scripting interface for image
 * manipulation, enabling operations like resizing, rotating, flipping, and
 * metadata management. It integrates with the images plugin for streamlined
 * handling of image files.</p>
 *
 * <h2>Functionality</h2>
 *
 * <p>The class supports image transformation, including flipping vertically or
 * horizontally with <code>flip(type)</code> and rotating by a specified number
 * of degrees using <code>rotate(degrees)</code>. Images can be resized while
 * maintaining their aspect ratio through <code>resize(width, height)</code>.
 * Methods like <code>getWidth()</code> and <code>getHeight()</code> retrieve
 * image dimensions.</p>
 *
 * <p>For metadata, <code>getMetaDataProperties()</code> lists available
 * properties, while <code>getMetaDataDescription(property)</code> and
 * <code>getMetaDataObject(property)</code> provide detailed information or the
 * actual metadata object. The <code>getContentType()</code> method fetches the
 * MIME type of the image, and <code>getData()</code> retrieves the image bytes
 * for storage or further processing.</p>
 *
 * <p>These features enable comprehensive control and management of image files
 * in scripting environments.</p>
 *
 * <p>For more details, please refer to the
 * <a href="../../../../guides/develop/programming-guide/working-with-files/images.md">
 * Images plugin</a> section of this documentation.</p>
 *
 * @author jcompagner
 */
@ServoyDocumented
public class JSImage implements IScriptable, Wrapper
{

	private byte[] imageData;

	private Dimension dimension;

	private TreeMap metadataMap;

	private final File file;

	// used by the javascript lib
	public JSImage()
	{
		file = null;
	}

	public JSImage(File file)
	{
		super();
		this.file = file;
	}

	public JSImage(byte[] image)
	{
		super();
		this.imageData = image;
		file = null;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString()
	{
		return "JSImage[dimensions:" + getSize() + ",size:" + imageData.length + ",contenttype:" + js_getContentType() + "]";
	}

	private Dimension getSize()
	{
		if (dimension == null)
		{
			if (imageData != null)
			{
				dimension = ImageLoader.getSize(imageData);
			}
			else
			{
				dimension = ImageLoader.getSize(file);
			}
		}
		return dimension;
	}

	/**
	 * Gets the height of this image.
	 *
	 * @sampleas js_getContentType()
	 */
	public int js_getHeight()
	{
		return getSize().height;
	}

	/**
	 * Gets the width of this image.
	 *
	 * @sampleas js_getContentType()
	 */
	public int js_getWidth()
	{
		return getSize().width;
	}

	/**
	 * Gets the bytes of this image, so that they can be saved to disk or stored the database.
	 *
	 * @sample
	 * var image = plugins.images.getImage(byteArray_or_file_or_filename);//loads the image
	 * image = image.resize(200,200);//resizes it to 200,200
	 * var bytes = image.getData();//gets the image bytes
	 * plugins.file.writeFile('filename',bytes);//saves the image bytes
	 */
	public byte[] js_getData()
	{
		if (imageData == null)
		{
			imageData = Utils.readFile(file, -1);
		}
		return imageData;
	}

	/**
	 * Resizes the image to the width/height given, keeping aspect ratio. A new JSImage is returned.
	 *
	 * @sampleas js_getData()
	 *
	 * @param width
	 * @param height
	 */
	public JSImage js_resize(int width, int height)
	{
		js_getData();
		byte[] array = ImageLoader.resize(imageData, width, height, true);
		if (array != null)
		{
			return new JSImage(array);
		}
		return null;
	}

	/**
	 * Rotates the image the number of degrees that is given. A new JSImage is returned.
	 *
	 * @sample
	 * var image = plugins.images.getImage(byteArray_or_file_or_filename);//loads the image
	 * image = image.rotate(90);//rotate the image 90 degrees
	 * var bytes = image.getData();//gets the image bytes
	 * plugins.file.writeFile('filename',bytes);//saves the image bytes
	 *
	 * @param degrees
	 */
	public JSImage js_rotate(final double degrees)
	{
		js_getData();
		final double radians = Math.toRadians(degrees);

		final int currentWidth = getSize().width;
		final int currentHeight = getSize().height;

		final int newWidth = (int)(Math.abs((currentWidth * Math.cos(radians) + currentHeight * Math.sin(radians))) + 0.5);
		final int newHeight = (int)(Math.abs((currentWidth * Math.sin(radians) + currentHeight * Math.cos(radians))) + 0.5);

		BufferedImage image = ImageLoader.getBufferedImage(imageData, currentWidth, currentHeight, true);

		int type = image.getType();
		if (type == 0) type = BufferedImage.TYPE_INT_ARGB_PRE;

		BufferedImage bi = new BufferedImage(newWidth, newHeight, type);

		Graphics2D g2d = (Graphics2D)bi.getGraphics();
		if (image.getAlphaRaster() == null)
		{
			g2d.setColor(new Color(255, 255, 255, 255));
		}
		else
		{
			g2d.setColor(new Color(255, 255, 255, 0));
		}
		g2d.fillRect(0, 0, newWidth, newHeight);
		AffineTransform origXform = g2d.getTransform();
		AffineTransform newXform = (AffineTransform)(origXform.clone());
		// center of rotation is center of the panel
		int xRot = newWidth / 2;
		int yRot = newHeight / 2;

		newXform.rotate(radians, xRot, yRot);
		g2d.setTransform(newXform);
		// draw image centered in panel
		int x = (newWidth - currentWidth) / 2;
		int y = (newHeight - currentHeight) / 2;
		g2d.drawImage(image, x, y, null);
		g2d.setTransform(origXform);
		g2d.dispose();

		try
		{
			return new JSImage(ImageLoader.getByteArray(js_getContentType(), bi));
		}
		finally
		{
			bi.flush();
		}
	}


	/**
	 * Flips the image verticaly (type param=0) or horizontaly (type param=1). A new JSImage is returned.
	 *
	 * @sample
	 * var image = plugins.images.getImage(byteArray_or_file_or_filename);//loads the image
	 * image = image.flip(0);//flip vertically
	 * var bytes = image.getData();//gets the image bytes
	 * plugins.file.writeFile('filename',bytes);//saves the image bytes
	 *
	 * @param type
	 */
	public JSImage js_flip(int type)
	{
		js_getData();
		final int currentWidth = getSize().width;
		final int currentHeight = getSize().height;

		BufferedImage bufferedImage = ImageLoader.getBufferedImage(imageData, currentWidth, currentHeight, true);

		if (type == 0)
		{
			//	 Flip the image vertically
			AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
			tx.translate(0, -bufferedImage.getHeight(null));
			AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
			bufferedImage = op.filter(bufferedImage, null);
		}
		else
		{
			// Flip the image horizontally
			AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
			tx.translate(-bufferedImage.getWidth(null), 0);
			AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
			bufferedImage = op.filter(bufferedImage, null);
		}
		try
		{
			return new JSImage(ImageLoader.getByteArray(js_getContentType(), bufferedImage));
		}
		finally
		{
			bufferedImage.flush();
		}
	}

	/**
	 * Gets the contenttype (image/jpeg) of this image.
	 *
	 * @sample
	 * var image = plugins.images.getImage(byteArray_or_file);
	 * var width = image.getWidth();
	 * var height = image.getHeight();
	 * var contentType = image.getContentType();
	 */
	public String js_getContentType()
	{
		if (imageData != null)
		{
			return MimeTypes.getContentType(imageData, file != null ? file.getName() : null);
		}
		else if (file != null)
		{
			byte[] bytes = Utils.readFile(file, 32);
			if (bytes != null)
			{
				return MimeTypes.getContentType(bytes, file.getName());
			}
		}
		return null;
	}


	/**
	 * Gets the available metadata properties from the image. Currently only jpg is supported.
	 *
	 * @sample
	 * var image = plugins.images.getImage(byteArray_or_file_or_filename);//loads the image
	 * // get the available metadata properties from the image, currently only jpg is supported
	 * var propertiesArray = image.getMetaDataProperties();
	 * for(var i=0;i<propertiesArray.length;i++)
	 * {
	 * 	var property = propertiesArray[i]
	 * 	application.output("property: " + property);
	 * 	application.output("description (string): " + image.getMetaDataDescription(property))
	 * 	application.output("real object: " + image.getMetaDataObject(property))
	 * }
	 * // Thumbnail data is stored under property 'Exif - Thumbnail Data', extract that and set it in a dataprovider
	 * thumbnail = image.getMetaDataObject("Exif - Thumbnail Data"); // gets thumbnail data from the image
	 */
	public String[] js_getMetaDataProperties()
	{
		generateMetaData();
		return (String[])metadataMap.keySet().toArray(new String[metadataMap.size()]);
	}

	/**
	 * Gets the description of a metadata property from the image. Currently only jpg is supported.
	 *
	 * @sampleas js_getMetaDataProperties()
	 *
	 * @param property
	 */
	public String js_getMetaDataDescription(String property)
	{
		generateMetaData();
		Object object = metadataMap.get(property);
		if (object instanceof Object[] && ((Object[])object).length > 0)
		{
			return (String)((Object[])object)[0];
		}
		return null;
	}

	/**
	 * Gets the real object of a metadata property from the image. Currently only jpg is supported.
	 *
	 * @sampleas js_getMetaDataProperties()
	 *
	 * @param property
	 */
	public Object js_getMetaDataObject(String property)
	{
		generateMetaData();
		Object object = metadataMap.get(property);
		if (object instanceof Object[] && ((Object[])object).length > 1)
		{
			return ((Object[])object)[1];
		}
		return null;
	}

	/**
	 * @see org.mozilla.javascript.Wrapper#unwrap()
	 */
	public Object unwrap()
	{
		return js_getData();
	}

	@SuppressWarnings({ "unchecked", "nls" })
	private void generateMetaData()
	{
		if (metadataMap != null) return;
		metadataMap = new TreeMap();
		if (imageData == null && file == null) return;
		try
		{

			Metadata metadata = null;
			try
			{
				if (imageData != null)
				{
					metadata = ImageMetadataReader.readMetadata(new BufferedInputStream(new ByteArrayInputStream(imageData), 512));
				}
				else
				{
					metadata = ImageMetadataReader.readMetadata(file);
				}
			}
			catch (IOException iOException)
			{
				Debug.error(iOException);
			}
			if (metadata != null)
			{
				for (Directory directory : metadata.getDirectories())
				{
					for (Tag tag : directory.getTags())
					{
						// use Tag.toString()
						if (tag.getDirectoryName().equals("Sony Makernote")) continue;
						String description = "";
						try
						{
							description = tag.getDescription();
							if (tag.getDescription() == null || "".equals(description)) continue;
						}
						catch (Exception ex)
						{
							continue;
						}
						String name = tag.getTagName();
						if (name.indexOf("Unknown tag") != -1) continue;

						Object object = directory.getObject(tag.getTagType());
						if (object instanceof JpegComponent || object instanceof int[] || (object instanceof byte[] && ((byte[])object).length <= 4))
						{
							object = description;
						}
						else if (object instanceof Rational)
						{
							object = ((Rational)object).toString();
						}
						metadataMap.put(tag.getDirectoryName() + " - " + name, new Object[] { description, object });
					}
				}
			}
		}
		catch (ImageProcessingException jpe)
		{
			// ignore not a jpg or valid image for extrating exif.
		}
	}
}
