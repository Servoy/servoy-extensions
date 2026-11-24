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
package com.servoy.extensions.plugins.converters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.servoy.j2db.dataprocessing.IColumnConverter;
import com.servoy.j2db.dataprocessing.IUIConverter;
import com.servoy.j2db.plugins.IClientPluginAccess;

public class ConverterPluginTest
{
	private final ConverterPlugin plugin = new ConverterPlugin();

	@Test
	public void exposesPluginMetadata()
	{
		assertEquals("ConverterPlugin", plugin.getName());
		assertNull(plugin.getPreferencePanels());
		assertNull(plugin.getScriptObject());
		assertNull(plugin.getProperties());
	}

	@Test
	public void initializeStoresTheApplication() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		assertNull(plugin.getApplication());
		plugin.initialize(access);
		assertSame(access, plugin.getApplication());
	}

	@Test
	public void providesAGlobalMethodColumnConverterOnce()
	{
		IColumnConverter[] converters = plugin.getColumnConverters();
		assertEquals(1, converters.length);
		assertInstanceOf(GlobalMethodConverter.class, converters[0]);
		assertSame(converters, plugin.getColumnConverters());
	}

	@Test
	public void providesAGlobalMethodUIConverterOnce()
	{
		IUIConverter[] converters = plugin.getUIConverters();
		assertEquals(1, converters.length);
		assertInstanceOf(GlobalMethodConverter.class, converters[0]);
		assertSame(converters, plugin.getUIConverters());
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
		plugin.unload();
		plugin.propertyChange(null);
	}
}
