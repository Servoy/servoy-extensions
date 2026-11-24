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

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.servoy.j2db.dataprocessing.IRecordMarkers;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.util.ILogLevel;

public class RegexValidatorTest
{
	private final RegexValidator validator = new RegexValidator();
	private final Map<String, String> digits = Map.of("regex", "[0-9]+");

	@Test
	public void exposesNameTypesAndDefaultProperties()
	{
		assertEquals("servoy.RegexValidator", validator.getName());
		assertArrayEquals(new int[] { IColumnTypes.TEXT }, validator.getSupportedColumnTypes());
		assertEquals(Map.of("regex", ""), validator.getDefaultProperties());
	}

	@Test
	public void fullMatchIsAccepted()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(digits, "12345", "dp", markers, null);
		verifyNoInteractions(markers);
	}

	@Test
	public void matchingIsCaseInsensitive()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(Map.of("regex", "abc"), "AbC", "dp", markers, null);
		verifyNoInteractions(markers);
	}

	@Test
	public void nullAndBlankAreIgnored()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(digits, null, "dp", markers, null);
		validator.validate(digits, " ", "dp", markers, null);
		verifyNoInteractions(markers);
	}

	@Test
	public void partialMatchIsReportedBecauseTheWholeValueMustMatch()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		Object state = new Object();

		validator.validate(digits, "123abc", "dp", markers, state);

		verify(markers).report("i18n:servoy.validator.regexp", "dp", ILogLevel.ERROR, state, new String[] { "123abc", "[0-9]+", "dp" });
	}

	@Test
	public void mismatchWithoutMarkersThrows()
	{
		assertThrows(IllegalArgumentException.class, () -> validator.validate(digits, "abc", "dp", null, null));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(digits, "abc"));
		assertDoesNotThrow(() -> validator.validate(digits, "123"));
	}
}
