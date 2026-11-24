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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.servoy.j2db.dataprocessing.IPropertyDescriptor;
import com.servoy.j2db.persistence.Column;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.persistence.IMethodTemplate;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IMethodTemplatesFactory;

public class GlobalMethodConverterTest
{
	private static final String TO = "toObjectMethodName";
	private static final String FROM = "fromObjectMethodName";
	private static final String TYPE = "type";

	private IClientPluginAccess access;
	private GlobalMethodConverter converter;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IClientPluginAccess.class);
		ConverterPlugin plugin = new ConverterPlugin();
		plugin.initialize(access);
		converter = new GlobalMethodConverter(plugin);
	}

	@Test
	public void exposesNameDefaultPropertiesAndTypes()
	{
		assertEquals("GlobalMethodConverter", converter.getName());
		assertEquals(Map.of(FROM, "", TO, "", TYPE, ""), converter.getDefaultProperties());
		int[] expected = { IColumnTypes.DATETIME, IColumnTypes.INTEGER, IColumnTypes.MEDIA, IColumnTypes.NUMBER, IColumnTypes.TEXT, Types.ARRAY };
		assertArrayEquals(expected, converter.getSupportedColumnTypes());
		assertArrayEquals(expected, converter.getSupportedDataproviderTypes());
	}

	@Test
	public void convertFromObjectCallsTheFromMethodWithValueAndDisplayType() throws Exception
	{
		when(access.executeMethod(any(), anyString(), any(), anyBoolean(), anyBoolean())).thenReturn("db");

		Object result = converter.convertFromObject(Map.of(FROM, "scopes.globals.toDb"), IColumnTypes.TEXT, "shown");

		assertEquals("db", result);
		verify(access).executeMethod(isNull(), eq("scopes.globals.toDb"),
			eq(new Object[] { "shown", Column.getDisplayTypeString(IColumnTypes.TEXT) }), eq(false), eq(false));
	}

	@Test
	public void convertToObjectCallsTheToMethod() throws Exception
	{
		when(access.executeMethod(any(), anyString(), any(), anyBoolean(), anyBoolean())).thenReturn("shown");

		Object result = converter.convertToObject(Map.of(TO, "scopes.globals.fromDb"), IColumnTypes.INTEGER, Integer.valueOf(1));

		assertEquals("shown", result);
		verify(access).executeMethod(isNull(), eq("scopes.globals.fromDb"),
			eq(new Object[] { Integer.valueOf(1), Column.getDisplayTypeString(IColumnTypes.INTEGER) }), eq(false), eq(false));
	}

	@Test
	public void valueIsReturnedUnchangedWithoutPropertiesOrMethodName() throws Exception
	{
		Object value = new Object();
		assertSame(value, converter.convertFromObject(null, IColumnTypes.TEXT, value));
		assertSame(value, converter.convertToObject(null, IColumnTypes.TEXT, value));
		assertSame(value, converter.convertFromObject(Map.of(), IColumnTypes.TEXT, value));
		assertSame(value, converter.convertToObject(Map.of(TO, "  "), IColumnTypes.TEXT, value));
		verifyNoInteractions(access);
	}

	@Test
	public void valueIsReturnedUnchangedBeforeThePluginIsInitialized() throws Exception
	{
		GlobalMethodConverter uninitialized = new GlobalMethodConverter(new ConverterPlugin());
		Object value = new Object();
		assertSame(value, uninitialized.convertFromObject(Map.of(FROM, "m"), IColumnTypes.TEXT, value));
	}

	@Test
	public void exceptionOfTheGlobalMethodIsPropagated() throws Exception
	{
		when(access.executeMethod(any(), anyString(), any(), anyBoolean(), anyBoolean())).thenThrow(new IllegalStateException("boom"));
		assertThrows(IllegalStateException.class, () -> converter.convertFromObject(Map.of(FROM, "m"), IColumnTypes.TEXT, "v"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "TEXT", "INTEGER", "NUMBER", "DATETIME", "MEDIA", "ARRAY" })
	public void toObjectTypeMapsTheTypeProperty(String type)
	{
		int expected = switch (type)
		{
			case "TEXT" -> IColumnTypes.TEXT;
			case "INTEGER" -> IColumnTypes.INTEGER;
			case "NUMBER" -> IColumnTypes.NUMBER;
			case "DATETIME" -> IColumnTypes.DATETIME;
			case "MEDIA" -> IColumnTypes.MEDIA;
			default -> Types.ARRAY;
		};
		assertEquals(expected, converter.getToObjectType(Map.of(TYPE, type)));
	}

	@Test
	public void toObjectTypeIsAsIsForUnknownOrMissingType()
	{
		assertEquals(Integer.MAX_VALUE, converter.getToObjectType(null));
		assertEquals(Integer.MAX_VALUE, converter.getToObjectType(Map.of()));
		assertEquals(Integer.MAX_VALUE, converter.getToObjectType(Map.of(TYPE, "<as is>")));
	}

	@Test
	public void propertyDescriptorsAreProvidedForTheThreeProperties()
	{
		assertEquals(IPropertyDescriptor.GLOBAL_METHOD, converter.getPropertyDescriptor(TO).getType());
		assertEquals(IPropertyDescriptor.GLOBAL_METHOD, converter.getPropertyDescriptor(FROM).getType());
		IPropertyDescriptor type = converter.getPropertyDescriptor(TYPE);
		assertNotNull(type);
		assertEquals(IPropertyDescriptor.NUMBER, type.getType());
		assertArrayEquals(new String[] { "<as is>", "TEXT", "INTEGER", "NUMBER", "DATETIME", "MEDIA", "ARRAY" }, type.getChoices());
		assertNull(converter.getPropertyDescriptor("other"));
		converter.validateProperties(Map.of());
	}

	@Test
	public void methodTemplatesAreRegisteredForBothMethodProperties()
	{
		IMethodTemplatesFactory factory = mock(IMethodTemplatesFactory.class);
		IMethodTemplate obj2db = mock(IMethodTemplate.class);
		IMethodTemplate db2obj = mock(IMethodTemplate.class);
		when(factory.createMethodTemplate(eq("globalConverterObj2DB"), anyString(), any(), anyString(), any(), anyString(), anyBoolean())).thenReturn(obj2db);
		when(factory.createMethodTemplate(eq("globalConverterDB2Obj"), anyString(), any(), anyString(), any(), anyString(), anyBoolean())).thenReturn(db2obj);

		Map<String, IMethodTemplate> templates = converter.getMethodTemplates(factory);

		assertEquals(2, templates.size());
		assertSame(obj2db, templates.get(FROM));
		assertSame(db2obj, templates.get(TO));
	}
}
