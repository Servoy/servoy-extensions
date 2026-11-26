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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * JSImage on images generated in memory (PNG with two colored halves, JPEG): size, content type, resize, rotate,
 * flip and metadata. Every result is decoded again with ImageIO.
 */
public class JSImageTest
{
	static final int RED = Color.RED.getRGB();
	static final int BLUE = Color.BLUE.getRGB();

	@TempDir
	Path tmp;

	/** A 40x20 image, left half red and right half blue. */
	static BufferedImage halves()
	{
		BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 40; x++)
		{
			for (int y = 0; y < 20; y++)
			{
				image.setRGB(x, y, x < 20 ? RED : BLUE);
			}
		}
		return image;
	}

	static byte[] encode(BufferedImage image, String format) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	static byte[] png() throws IOException
	{
		return encode(halves(), "png");
	}

	static byte[] jpg() throws IOException
	{
		return encode(halves(), "jpg");
	}

	static BufferedImage decode(JSImage image) throws IOException
	{
		return ImageIO.read(new ByteArrayInputStream(image.js_getData()));
	}

	// --- basic information ---

	@Test
	public void sizeAndContentTypeOfAPng() throws Exception
	{
		JSImage image = new JSImage(png());

		assertEquals(40, image.js_getWidth());
		assertEquals(20, image.js_getHeight());
		assertEquals("image/png", image.js_getContentType());
		assertTrue(image.toString().contains("contenttype:image/png"), image.toString());
		assertSame(image.js_getData(), image.unwrap());
	}

	@Test
	public void aJpegFromAFile() throws Exception
	{
		Path file = Files.write(tmp.resolve("photo.jpg"), jpg());
		JSImage image = new JSImage(file.toFile());

		assertEquals("image/jpeg", image.js_getContentType());
		assertEquals(40, image.js_getWidth());
		assertEquals(20, image.js_getHeight());
		assertArrayEquals(Files.readAllBytes(file), image.js_getData());
		assertEquals("image/jpeg", image.js_getContentType());
	}

	@Test
	public void toStringOfAnImageThatWasNotReadYet() throws Exception
	{
		JSImage image = new JSImage(Files.write(tmp.resolve("a.png"), png()).toFile());

		// current behaviour (bug): toString uses imageData.length, which is only loaded by getData
		assertThrows(NullPointerException.class, image::toString);
		image.js_getData();
		assertTrue(image.toString().startsWith("JSImage[dimensions:"));
	}

	@Test
	public void anEmptyImage()
	{
		JSImage image = new JSImage();

		assertNull(image.js_getContentType());
		assertEquals(0, image.js_getMetaDataProperties().length);
		assertNull(image.js_getData());
	}

	// --- transformations ---

	@Test
	public void resizeKeepsTheAspectRatio() throws Exception
	{
		JSImage resized = new JSImage(png()).js_resize(20, 20);

		BufferedImage decoded = decode(resized);
		assertEquals(20, decoded.getWidth());
		assertEquals(10, decoded.getHeight());
		assertEquals("image/png", resized.js_getContentType());
		// the colors of the halves are kept
		assertEquals(RED, decoded.getRGB(2, 5) | 0xFF000000);
		assertEquals(BLUE, decoded.getRGB(17, 5) | 0xFF000000);
	}

	@Test
	public void resizeAJpeg() throws Exception
	{
		JSImage resized = new JSImage(jpg()).js_resize(80, 80);

		assertEquals(80, decode(resized).getWidth());
		assertEquals(40, decode(resized).getHeight());
	}

	@Test
	public void resizeOfNoImageIsNull()
	{
		assertNull(new JSImage("not an image".getBytes()).js_resize(10, 10));
	}

	@Test
	public void flipHorizontallySwapsTheHalves() throws Exception
	{
		BufferedImage flipped = decode(new JSImage(png()).js_flip(1));

		assertEquals(40, flipped.getWidth());
		assertEquals(BLUE, flipped.getRGB(0, 0) | 0xFF000000);
		assertEquals(RED, flipped.getRGB(39, 19) | 0xFF000000);
	}

	@Test
	public void flipVerticallyKeepsTheHalves() throws Exception
	{
		BufferedImage source = halves();
		source.setRGB(0, 0, Color.GREEN.getRGB());

		BufferedImage flipped = decode(new JSImage(encode(source, "png")).js_flip(0));

		assertEquals(RED, flipped.getRGB(0, 0) | 0xFF000000);
		assertEquals(Color.GREEN.getRGB(), flipped.getRGB(0, 19) | 0xFF000000);
		assertEquals(BLUE, flipped.getRGB(39, 0) | 0xFF000000);
	}

	@Test
	public void rotateBy90Degrees() throws Exception
	{
		JSImage rotated = new JSImage(png()).js_rotate(90);

		BufferedImage decoded = decode(rotated);
		assertEquals(20, decoded.getWidth());
		assertEquals(40, decoded.getHeight());
		// clockwise: the left (red) half ends up at the top
		assertEquals(RED, decoded.getRGB(10, 5) | 0xFF000000);
		assertEquals(BLUE, decoded.getRGB(10, 35) | 0xFF000000);
		assertEquals("image/png", rotated.js_getContentType());
	}

	@Test
	public void rotateBy180And45Degrees() throws Exception
	{
		BufferedImage upsideDown = decode(new JSImage(png()).js_rotate(180));
		BufferedImage diagonal = decode(new JSImage(jpg()).js_rotate(45));

		assertEquals(40, upsideDown.getWidth());
		assertEquals(20, upsideDown.getHeight());
		assertEquals(BLUE, upsideDown.getRGB(2, 10) | 0xFF000000);
		// the bounding box of a rotated 40x20 image, the corners are filled white for a jpeg
		assertEquals(42, diagonal.getWidth());
		assertEquals(42, diagonal.getHeight());
		assertTrue(isWhite(diagonal.getRGB(0, 0)), Integer.toHexString(diagonal.getRGB(0, 0)));
	}

	@Test
	public void rotatingBetween90And180DegreesGivesATooSmallImage() throws Exception
	{
		BufferedImage rotated = decode(new JSImage(png()).js_rotate(135));

		// current behaviour (bug): the new size is |w cos + h sin| instead of |w cos| + |h sin|, for 135 degrees
		// that is 14x14 instead of the 42x42 bounding box, so most of the image is cut off
		assertEquals(14, rotated.getWidth());
		assertEquals(14, rotated.getHeight());
	}

	@Test
	public void anImageWithAlphaGetsTransparentCorners() throws Exception
	{
		BufferedImage source = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 40; x++)
		{
			for (int y = 0; y < 20; y++)
			{
				source.setRGB(x, y, RED);
			}
		}

		BufferedImage rotated = decode(new JSImage(encode(source, "png")).js_rotate(45));

		assertEquals(0, rotated.getRGB(0, 0) >>> 24);
	}

	private static boolean isWhite(int rgb)
	{
		// near white, jpeg compression shifts the colors a little
		return ((rgb >> 16) & 0xFF) > 220 && ((rgb >> 8) & 0xFF) > 220 && (rgb & 0xFF) > 220;
	}

	// --- metadata ---

	@Test
	public void jpegMetadata() throws Exception
	{
		JSImage image = new JSImage(jpg());

		String[] properties = image.js_getMetaDataProperties();

		assertTrue(Arrays.asList(properties).contains("JPEG - Image Width"), Arrays.toString(properties));
		assertEquals("40 pixels", image.js_getMetaDataDescription("JPEG - Image Width"));
		assertEquals(Integer.valueOf(20), image.js_getMetaDataObject("JPEG - Image Height"));
		// the jpeg components are given as their description
		String component = Arrays.stream(properties).filter(p -> p.startsWith("JPEG - Component")).findFirst().orElseThrow();
		assertEquals(image.js_getMetaDataDescription(component), image.js_getMetaDataObject(component));
		assertNull(image.js_getMetaDataDescription("none"));
		assertNull(image.js_getMetaDataObject("none"));
	}

	@Test
	public void metadataOfAFile() throws Exception
	{
		JSImage image = new JSImage(Files.write(tmp.resolve("photo.jpg"), jpg()).toFile());

		assertNotNull(image.js_getMetaDataDescription("JPEG - Image Height"));
	}

	@Test
	public void noMetadataForData()
	{
		assertEquals(0, new JSImage("not an image at all".getBytes()).js_getMetaDataProperties().length);
	}

	@Test
	public void aFileThatCannotBeReadHasNoMetadata()
	{
		JSImage image = new JSImage(tmp.resolve("missing.jpg").toFile());

		assertEquals(0, image.js_getMetaDataProperties().length);
		assertNull(image.js_getContentType());
	}
}
