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
package com.servoy.extensions.plugins.serialize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.servoy.j2db.dataprocessing.IColumnConverter;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.util.serialize.JSONSerializerWrapper;

public class SerializePluginTest
{
	private final SerializePlugin plugin = new SerializePlugin();

	@Test
	public void exposesNameAndDisplayName()
	{
		assertEquals("serialize", plugin.getName());
		assertEquals("Serialize Plugin", plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
	}

	@Test
	public void initializeStoresTheClientPluginAccess() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		plugin.initialize(access);
		assertSame(access, plugin.getClientPluginAccess());
	}

	@Test
	public void scriptObjectIsASingleSerializeProvider()
	{
		assertInstanceOf(SerializeProvider.class, plugin.getScriptObject());
		assertSame(plugin.getScriptObject(), plugin.getScriptObject());
	}

	@Test
	public void providesBlobAndStringSerializerOnce()
	{
		IColumnConverter[] converters = plugin.getColumnConverters();
		assertEquals(2, converters.length);
		assertInstanceOf(BlobSerializer.class, converters[0]);
		assertInstanceOf(StringSerializer.class, converters[1]);
		assertSame(converters, plugin.getColumnConverters());
	}

	@Test
	public void jsonSerializerIsCachedUntilUnload() throws Exception
	{
		JSONSerializerWrapper first = plugin.getJSONSerializer();
		Object provider = plugin.getScriptObject();
		assertSame(first, plugin.getJSONSerializer());

		plugin.unload();

		assertNotSame(first, plugin.getJSONSerializer());
		assertNotSame(provider, plugin.getScriptObject());
	}

	@Test
	public void imageAndIconUrlAreConsistent()
	{
		assertEquals(plugin.getIconUrl() == null, plugin.getImage() == null);
	}

	@Test
	public void lifecycleCallbacksAreNoOps() throws Exception
	{
		plugin.load();
		plugin.propertyChange(null);
	}
}
