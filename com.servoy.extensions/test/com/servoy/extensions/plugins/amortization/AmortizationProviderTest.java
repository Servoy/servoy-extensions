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
package com.servoy.extensions.plugins.amortization;

import static com.servoy.extensions.plugins.amortization.AmortizationCalculationTest.date;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.servoy.j2db.plugins.IClientPlugin;

/**
 * The amortization plugin and its scripting provider, including nextDate for every period.
 */
public class AmortizationProviderTest
{
	private final AmortizationProvider provider = new AmortizationProvider();

	@Test
	public void plugin() throws Exception
	{
		AmortizationPlugin plugin = new AmortizationPlugin();
		plugin.load();
		plugin.initialize(null);

		assertEquals("amortization", plugin.getName());
		assertEquals("", plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertSame(plugin.getScriptObject(), plugin.getScriptObject());
		plugin.propertyChange(null);
		plugin.unload();
	}

	@Test
	public void factories()
	{
		assertEquals(List.of(AmortizationCalculation.class, Polynomial.class), List.of(provider.getAllReturnedTypes()));
		assertNotSame(provider.js_newCalculation(), provider.js_newCalculation());

		Polynomial p = provider.js_newPolynomial();
		p.js_addTerm(2, 1);
		Polynomial copy = provider.js_newPolynomial(p);
		assertEquals("2.0*x^1", copy.toString());
		assertEquals("0", provider.js_newPolynomial(null).toString());
	}

	@ParameterizedTest(name = "{0}-{1}-{2} + period {3} (start day {4}) = {5}-{6}-{7}")
	@CsvSource({ //
		"2005, 3, 15, 1, -1, 2006, 3, 15", // annually
		"2005, 1, 31, 2, 31, 2005, 7, 31", // bi-annually
		"2005, 1, 31, 3, 31, 2005, 5, 31", // four monthly
		"2005, 1, 31, 4, 31, 2005, 4, 30", // quarterly, april has 30 days
		"2005, 4, 30, 4, 31, 2005, 7, 31", // and back to the start day
		"2005, 1, 31, 6, 31, 2005, 3, 31", // two monthly
		"2005, 2, 28, 12, 31, 2005, 3, 31", // monthly, from the end of february to the end of march
		"2005, 1, 31, 12, 31, 2005, 2, 28", //
		"2005, 1, 1, 13, -1, 2005, 1, 29", // four weekly
		"2005, 1, 1, 26, -1, 2005, 1, 15", // two weekly
		"2005, 1, 1, 52, -1, 2005, 1, 8", // weekly
		"2004, 12, 31, 365, -1, 2005, 1, 1", // daily
		"2005, 6, 1, 0, -1, 2005, 6, 1" // no period
	})
	public void nextDate(int year, int month, int day, int period, int startDay, int nextYear, int nextMonth, int nextDay)
	{
		assertEquals(date(nextYear, nextMonth, nextDay), provider.js_nextDate(date(year, month, day), period, startDay));
	}

	@Test
	public void nextDateEdgeCases()
	{
		assertNull(provider.js_nextDate(null, 12, -1));
		assertThrows(IllegalArgumentException.class, () -> provider.js_nextDate(new Date(), 7, -1));
		// current behaviour (bug): the valid PERIOD_BI_MONTHLY is not supported by nextDate
		assertThrows(IllegalArgumentException.class, () -> provider.js_nextDate(new Date(), AmortizationCalculation.PERIOD_BI_MONTHLY, -1));
	}
}
