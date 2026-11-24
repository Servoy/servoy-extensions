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
import static org.junit.jupiter.api.Assertions.assertNull;
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

public class EmailValidatorTest
{
	private final EmailValidator validator = new EmailValidator();

	@Test
	public void exposesNameTypesAndNoDefaultProperties()
	{
		assertEquals("servoy.EmailValidator", validator.getName());
		assertArrayEquals(new int[] { IColumnTypes.TEXT }, validator.getSupportedColumnTypes());
		assertNull(validator.getDefaultProperties());
	}

	@ParameterizedTest
	@ValueSource(strings = { "john@example.com", "john.doe@sub.example.org", "john+tag@example.co", "o'neil@example.com" })
	public void validAddressesAreAccepted(String email)
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(null, email, "email", markers, null);
		verifyNoInteractions(markers);
	}

	@Test
	public void nullAndBlankAreIgnored()
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		validator.validate(null, null, "email", markers, null);
		validator.validate(null, "", "email", markers, null);
		validator.validate(null, "   ", "email", markers, null);
		verifyNoInteractions(markers);
	}

	@ParameterizedTest
	@ValueSource(strings = { "john", "john@", "@example.com", "john@example", "john@@example.com", "john doe@example.com" })
	public void invalidAddressIsReportedOnTheMarkers(String email)
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		Object state = new Object();

		validator.validate(null, email, "email", markers, state);

		verify(markers).report("i18n:servoy.validator.email", "email", ILogLevel.ERROR, state, new String[] { email, "email" });
	}

	@Test
	public void invalidAddressWithoutMarkersThrows()
	{
		assertThrows(IllegalArgumentException.class, () -> validator.validate(null, "nope", "email", null, null));
	}

	@Test
	public void twoArgumentValidateThrowsForInvalidAndAcceptsValid()
	{
		assertThrows(IllegalArgumentException.class, () -> validator.validate(null, "nope"));
		assertDoesNotThrow(() -> validator.validate(null, "john@example.com"));
	}
}
