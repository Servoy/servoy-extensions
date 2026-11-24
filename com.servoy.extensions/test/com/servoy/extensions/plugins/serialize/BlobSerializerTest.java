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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.plugins.IClientPluginAccess;

public class BlobSerializerTest
{
	private BlobSerializer serializer;

	@BeforeEach
	public void setUp() throws Exception
	{
		SerializePlugin plugin = new SerializePlugin();
		plugin.initialize(mock(IClientPluginAccess.class));
		serializer = new BlobSerializer(plugin);
	}

	@Test
	public void exposesNameAndTypes()
	{
		assertEquals("BlobSerializer", serializer.getName());
		assertArrayEquals(new int[] { IColumnTypes.MEDIA }, serializer.getSupportedColumnTypes());
		assertEquals(IColumnTypes.MEDIA, serializer.getToObjectType(null));
		assertNull(serializer.getDefaultProperties());
	}

	@Test
	public void nullIsPassedThroughBothWays() throws Exception
	{
		assertNull(serializer.convertFromObject(null, IColumnTypes.MEDIA, null));
		assertNull(serializer.convertToObject(null, IColumnTypes.MEDIA, null));
	}

	@Test
	public void valueIsStoredAsUtf8JsonBytes() throws Exception
	{
		Object stored = serializer.convertFromObject(null, IColumnTypes.MEDIA, "caf\u00e9");

		byte[] bytes = assertInstanceOf(byte[].class, stored);
		assertEquals("\"caf\u00e9\"", new String(bytes, StandardCharsets.UTF_8));
	}

	@Test
	public void stringAndMapRoundTrip() throws Exception
	{
		assertEquals("caf\u00e9", serializer.convertToObject(null, IColumnTypes.MEDIA, serializer.convertFromObject(null, IColumnTypes.MEDIA, "caf\u00e9")));

		Map<String, Object> map = new HashMap<>();
		map.put("k", "v");
		assertEquals(map, serializer.convertToObject(null, IColumnTypes.MEDIA, serializer.convertFromObject(null, IColumnTypes.MEDIA, map)));
	}
}
