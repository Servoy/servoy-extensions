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

import static com.servoy.extensions.plugins.images.JSImageTest.halves;
import static com.servoy.extensions.plugins.images.JSImageTest.png;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.extensions.plugins.file.JSFile;
import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * The images plugin and getImage for files, file names, JSFiles, bytes and java images. No client window.
 */
public class ImageProviderTest
{
	private IClientPluginAccess access;
	private ImagePlugin plugin;
	private ImageProvider provider;

	@TempDir
	Path tmp;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IClientPluginAccess.class);
		plugin = new ImagePlugin();
		plugin.load();
		plugin.initialize(access);
		provider = (ImageProvider)plugin.getScriptObject();
	}

	@Test
	public void plugin() throws Exception
	{
		assertEquals("images", plugin.getName());
		assertNull(plugin.getProperties());
		assertNull(plugin.getPreferencePanels());
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertSame(provider, plugin.getScriptObject());
		assertEquals(List.of(JSImage.class), List.of(provider.getAllReturnedTypes()));
		assertNotNull(new ImageProvider());
		plugin.propertyChange(null);

		plugin.unload();

		assertNull(plugin.getClientPluginAccess());
	}

	@Test
	public void anImageFromBytes() throws Exception
	{
		byte[] data = png();

		JSImage image = provider.js_getImage(data);

		assertSame(data, image.js_getData());
	}

	@Test
	public void anImageFromAFileAFileNameOrAJSFile() throws Exception
	{
		Path file = Files.write(tmp.resolve("a.png"), png());

		assertEquals(40, provider.js_getImage(file.toFile()).js_getWidth());
		assertEquals(40, provider.js_getImage(file.toString()).js_getWidth());
		assertEquals(40, provider.js_getImage(new JSFile(file.toFile(), access)).js_getWidth());
	}

	@Test
	public void aMissingOrEmptyFileIsNoImage() throws Exception
	{
		Path empty = Files.createFile(tmp.resolve("empty.png"));

		// the file cannot be read and is not an image the snapshot code knows: an image without data
		assertNull(provider.js_getImage(tmp.resolve("missing.png").toString()).js_getData());
		assertNull(provider.js_getImage(empty.toFile()).js_getData());
		assertNull(provider.js_getImage(Integer.valueOf(1)).js_getData());
	}

	@Test
	public void aJavaImageIsConverted() throws Exception
	{
		JSImage image = provider.js_getImage(halves());
		JSImage deprecated = provider.js_createJPGImage(halves());

		// current behaviour (bug): SnapShot.createJPGImage asks ImageIO for a writer of "image/jpg", which does not
		// exist (the mime type is image/jpeg), so the "jpg" image falls back to png
		assertEquals("image/png", image.js_getContentType());
		assertEquals(40, image.js_getWidth());
		assertEquals("image/png", deprecated.js_getContentType());
	}

	@Test
	public void scalingAJavaImageNeedsASmartClientWindow()
	{
		// current behaviour: the scaled snapshot needs the window of a smart client as image observer, without
		// one (NG, headless) it fails in the java MediaTracker
		assertThrows(NullPointerException.class, () -> provider.js_createJPGImage(halves(), 20, 10));
	}

	@Test
	public void bytesOfAJpgSnapshotAreTheDecodedImage() throws Exception
	{
		byte[] data = png();

		// a byte array goes straight to a JSImage, it is not re-encoded
		assertArrayEquals(data, provider.js_getImage(data).js_getData());
	}
}
