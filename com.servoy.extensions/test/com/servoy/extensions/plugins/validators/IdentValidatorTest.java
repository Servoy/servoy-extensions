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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.servoy.j2db.dataprocessing.IRecordMarkers;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.util.ILogLevel;

public class IdentValidatorTest
{
	private final IdentValidator validator = new IdentValidator();

	@Test
	public void exposesNameTypesAndEmptyDefaultProperties()
	{
		assertEquals("servoy.IdentifierValidator", validator.getName());
		assertArrayEquals(new int[] { IColumnTypes.TEXT }, validator.getSupportedColumnTypes());
		assertTrue(validator.getDefaultProperties().isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "name", "_name", "name1", "camelCase" })
	public void validIdentifiersAreAccepted(String value)
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(null, value, "dp", markers, null);
		verifyNoInteractions(markers);
	}

	@ParameterizedTest
	@ValueSource(strings = { "1name", "na me", "na-me", "" })
	public void invalidIdentifiersAreReported(String value)
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		Object state = new Object();

		validator.validate(null, value, "dp", markers, state);

		verify(markers).report("i18n:servoy.validator.identifier", "dp", ILogLevel.ERROR, state, new Object[] { value, "dp" });
	}

	@Test
	public void nonStringValuesAreNotChecked()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(null, Integer.valueOf(5), "dp", markers, null);
		validator.validate(null, null, "dp", markers, null);
		verifyNoInteractions(markers);
	}

	@Test
	public void invalidIdentifierWithoutMarkersThrows()
	{
		assertThrows(IllegalArgumentException.class, () -> validator.validate(null, "1name", "dp", null, null));
		assertDoesNotThrow(() -> validator.validate(null, "name", "dp", null, null));
	}
}
