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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.util.serialize.JSONSerializerWrapper;

public class StringSerializerTest
{
	private SerializePlugin plugin;
	private StringSerializer serializer;

	@BeforeEach
	public void setUp() throws Exception
	{
		plugin = new SerializePlugin();
		plugin.initialize(mock(IClientPluginAccess.class));
		serializer = new StringSerializer(plugin);
	}

	@Test
	public void exposesNameAndTypes()
	{
		assertEquals(JSONSerializerWrapper.STRING_SERIALIZER_NAME, serializer.getName());
		assertArrayEquals(new int[] { IColumnTypes.TEXT }, serializer.getSupportedColumnTypes());
		assertEquals(IColumnTypes.MEDIA, serializer.getToObjectType(null));
		assertNull(serializer.getDefaultProperties());
	}

	@Test
	public void nullIsPassedThroughBothWays() throws Exception
	{
		assertNull(serializer.convertFromObject(null, IColumnTypes.TEXT, null));
		assertNull(serializer.convertToObject(null, IColumnTypes.TEXT, null));
	}

	@Test
	public void stringIsStoredAsQuotedJson() throws Exception
	{
		assertEquals("\"hello\"", serializer.convertFromObject(null, IColumnTypes.TEXT, "hello"));
	}

	@Test
	public void stringRoundTrips() throws Exception
	{
		Object json = serializer.convertFromObject(null, IColumnTypes.TEXT, "hello \"world\"");
		assertEquals("hello \"world\"", serializer.convertToObject(null, IColumnTypes.TEXT, json));
	}

	@Test
	public void numberAndBooleanRoundTrip() throws Exception
	{
		Object number = serializer.convertToObject(null, IColumnTypes.TEXT, serializer.convertFromObject(null, IColumnTypes.TEXT, Integer.valueOf(42)));
		assertEquals(42, ((Number)number).intValue());
		assertEquals(Boolean.TRUE,
			serializer.convertToObject(null, IColumnTypes.TEXT, serializer.convertFromObject(null, IColumnTypes.TEXT, Boolean.TRUE)));
	}

	@Test
	public void mapIsSerializedWithItsJavaClassAndRoundTrips() throws Exception
	{
		Map<String, Object> map = new HashMap<>();
		map.put("a", "x");

		String json = (String)serializer.convertFromObject(null, IColumnTypes.TEXT, map);

		assertTrue(json.contains("java.util.HashMap"), json);
		assertEquals(map, serializer.convertToObject(null, IColumnTypes.TEXT, json));
	}

	@Test
	public void invalidJsonIsRethrown()
	{
		assertThrows(Exception.class, () -> serializer.convertToObject(null, IColumnTypes.TEXT, "{not json"));
	}
}
