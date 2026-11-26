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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.beans.PropertyChangeEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;

public class FilePluginTest
{
	@TempDir
	Path tempDir;

	private final FilePlugin plugin = new FilePlugin();

	@AfterEach
	public void tearDown() throws Exception
	{
		plugin.unload();
	}

	private IClientPluginAccess access(int applicationType)
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(Integer.valueOf(access.getApplicationType())).thenReturn(Integer.valueOf(applicationType));
		return access;
	}

	@Test
	public void exposesPluginMetadata() throws Exception
	{
		plugin.load();
		assertEquals("file", plugin.getName());
		assertNotNull(plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertEquals(plugin.getIconUrl() == null, plugin.getImage() == null);
	}

	@ParameterizedTest
	@ValueSource(ints = { IClientPluginAccess.CLIENT, IClientPluginAccess.WEB_CLIENT, IClientPluginAccess.NG_CLIENT, IClientPluginAccess.HEADLESS_CLIENT })
	public void everyClientGetsTheSameFileProvider(int applicationType) throws Exception
	{
		IClientPluginAccess access = access(applicationType);
		plugin.initialize(access);

		Object provider = plugin.getScriptObject();

		assertSame(FileProvider.class, provider.getClass());
		assertSame(provider, plugin.getScriptObject());
		assertSame(access, plugin.getClientPluginAccess());
	}

	@Test
	public void withoutInitializeTheLocalFileProviderIsUsed()
	{
		assertSame(FileProvider.class, plugin.getScriptObject().getClass());
	}

	@Test
	public void unloadDropsTheProviderAndTheAccess() throws Exception
	{
		plugin.initialize(access(IClientPluginAccess.CLIENT));
		Object first = plugin.getScriptObject();

		plugin.unload();

		assertNull(plugin.getClientPluginAccess());
		assertNotSame(first, plugin.getScriptObject());
	}

	@Test
	public void closingTheSolutionDeletesTrackedFiles() throws Exception
	{
		IClientPluginAccess access = access(IClientPluginAccess.CLIENT);
		plugin.initialize(access);
		FileProvider provider = (FileProvider)plugin.getScriptObject();
		JSFile tracked = new JSFile(Files.writeString(tempDir.resolve("t.txt"), "x").toFile(), access);
		provider.js_trackFileForDeletion(tracked);

		plugin.propertyChange(new PropertyChangeEvent(this, "solution", "old", "new"));
		assertTrue(tracked.js_exists());

		plugin.propertyChange(new PropertyChangeEvent(this, "other", "old", null));
		assertTrue(tracked.js_exists());

		plugin.propertyChange(new PropertyChangeEvent(this, "solution", "old", null));
		assertFalse(tracked.js_exists());
	}

	@Test
	public void propertyChangeWithoutProviderIsIgnored()
	{
		plugin.propertyChange(new PropertyChangeEvent(this, "solution", "old", null));
	}
}
