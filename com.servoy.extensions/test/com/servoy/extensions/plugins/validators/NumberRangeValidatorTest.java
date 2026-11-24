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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.servoy.j2db.dataprocessing.IRecordMarkers;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.util.ILogLevel;

public class NumberRangeValidatorTest
{
	private final NumberRangeValidator validator = new NumberRangeValidator();
	private final Map<String, String> range = Map.of("from", "1", "to", "10");

	@Test
	public void exposesNameTypesAndDefaultPropertiesInOrder()
	{
		assertEquals("servoy.NumberRangeValidator", validator.getName());
		assertArrayEquals(new int[] { IColumnTypes.INTEGER, IColumnTypes.NUMBER }, validator.getSupportedColumnTypes());
		assertEquals(List.of("from", "to"), List.copyOf(validator.getDefaultProperties().keySet()));
	}

	@Test
	public void valuesInsideTheRangeIncludingBoundsAreAccepted()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(range, Integer.valueOf(1), "dp", markers, null);
		validator.validate(range, Integer.valueOf(5), "dp", markers, null);
		validator.validate(range, Double.valueOf(10), "dp", markers, null);
		validator.validate(range, "7.5", "dp", markers, null);
		verifyNoInteractions(markers);
	}

	@Test
	public void nullAndBlankAreIgnored()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(range, null, "dp", markers, null);
		validator.validate(range, "  ", "dp", markers, null);
		verifyNoInteractions(markers);
	}

	@Test
	public void valueBelowTheRangeIsReported()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		Object state = new Object();

		validator.validate(range, Integer.valueOf(0), "dp", markers, state);

		verify(markers).report("i18n:servoy.validator.range", "dp", ILogLevel.ERROR, state,
			new Object[] { Integer.valueOf(0), "dp", Double.valueOf(1), Double.valueOf(10) });
	}

	@Test
	public void valueAboveTheRangeIsReported()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);

		validator.validate(range, Integer.valueOf(11), "dp", markers, null);

		verify(markers).report("i18n:servoy.validator.range", "dp", ILogLevel.ERROR, null,
			new Object[] { Integer.valueOf(11), "dp", Double.valueOf(1), Double.valueOf(10) });
	}

	@Test
	public void outOfRangeWithoutMarkersThrows()
	{
		assertThrows(IllegalArgumentException.class, () -> validator.validate(range, Integer.valueOf(11), "dp", null, null));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(range, Integer.valueOf(11)));
		assertDoesNotThrow(() -> validator.validate(range, Integer.valueOf(5)));
	}
}
