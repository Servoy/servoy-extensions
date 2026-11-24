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
package com.servoy.extensions.plugins.validators;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.servoy.j2db.dataprocessing.IRecordMarkers;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.util.ILogLevel;

public class SizeValidatorTest
{
	private final SizeValidator validator = new SizeValidator();
	private final Map<String, String> max3 = Map.of("length", "3");

	@Test
	public void exposesNameTypesAndDefaultProperties()
	{
		assertEquals("servoy.SizeValidator", validator.getName());
		assertArrayEquals(new int[] { IColumnTypes.TEXT, IColumnTypes.MEDIA }, validator.getSupportedColumnTypes());
		assertEquals(Map.of("length", ""), validator.getDefaultProperties());
	}

	@Test
	public void stringsAndBytesUpToTheLimitAreAccepted()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(max3, "abc", "dp", markers, null);
		validator.validate(max3, "", "dp", markers, null);
		validator.validate(max3, new byte[] { 1, 2, 3 }, "dp", markers, null);
		verifyNoInteractions(markers);
	}

	@Test
	public void valuesOfOtherTypesCountAsZeroLength()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(max3, null, "dp", markers, null);
		validator.validate(max3, Integer.valueOf(123456), "dp", markers, null);
		verifyNoInteractions(markers);
	}

	@Test
	public void tooLongStringIsReported()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		Object state = new Object();

		validator.validate(max3, "abcd", "dp", markers, state);

		verify(markers).report("i18n:servoy.validator.size", "dp", ILogLevel.ERROR, state,
			new Object[] { "dp", Integer.valueOf(4), Integer.valueOf(3) });
	}

	@Test
	public void tooLongByteArrayIsReported()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);

		validator.validate(max3, new byte[5], "dp", markers, null);

		verify(markers).report("i18n:servoy.validator.size", "dp", ILogLevel.ERROR, null,
			new Object[] { "dp", Integer.valueOf(5), Integer.valueOf(3) });
	}

	@Test
	public void tooLongWithoutMarkersThrows()
	{
		assertThrows(IllegalArgumentException.class, () -> validator.validate(max3, "abcd", "dp", null, null));
		assertDoesNotThrow(() -> validator.validate(max3, "abc", "dp", null, null));
	}
}
