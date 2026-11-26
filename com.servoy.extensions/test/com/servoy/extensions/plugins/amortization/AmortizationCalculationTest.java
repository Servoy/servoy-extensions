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

import static com.servoy.extensions.plugins.amortization.AmortizationCalculation.NUMBER_UNLIMITED;
import static com.servoy.extensions.plugins.amortization.AmortizationCalculation.PERIOD_ANNUALY;
import static com.servoy.extensions.plugins.amortization.AmortizationCalculation.PERIOD_BI_MONTHLY;
import static com.servoy.extensions.plugins.amortization.AmortizationCalculation.PERIOD_MONTHLY;
import static com.servoy.extensions.plugins.amortization.AmortizationCalculation.PERIOD_NONE;
import static com.servoy.extensions.plugins.amortization.AmortizationCalculation.STARTDAY_NORMAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.GregorianCalendar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.servoy.j2db.dataprocessing.IDataSet;

/**
 * The amortization calculation with known values: events, validation, schedules, interest with annual and monthly
 * compounding and solving for an unknown payment or rate. Pure calculation, no client.
 */
public class AmortizationCalculationTest
{
	private static final double CENT = 0.005;

	/** A local date, month 1 based. */
	static Date date(int year, int month, int day)
	{
		return new GregorianCalendar(year, month - 1, day).getTime();
	}

	private static Object[] row(IDataSet set, int i)
	{
		return set.getRow(i);
	}

	private static double num(Object[] row, int column)
	{
		return ((Number)row[column]).doubleValue();
	}

	/** 10% a year, compounded yearly from 2005-01-01, a loan of 1000 on 2005-01-01. */
	private static AmortizationCalculation yearlyLoan()
	{
		AmortizationCalculation c = new AmortizationCalculation();
		assertTrue(c.js_addRateChange(0.10, date(2005, 1, 1)));
		assertTrue(c.js_addCompoundPeriodChange(PERIOD_ANNUALY, date(2005, 1, 1)));
		assertTrue(c.js_addLoan(1000, date(2005, 1, 1)));
		return c;
	}

	// --- events and validation ---

	@Test
	public void eventsAreStoredWithTheirParameters()
	{
		AmortizationCalculation c = new AmortizationCalculation();

		assertTrue(c.js_addLoan(1000, date(2005, 1, 1)));
		assertTrue(c.js_addPayment(100, date(2005, 2, 15), date(2005, 5, 15), PERIOD_MONTHLY));
		assertTrue(c.js_addRateChange(0.05, date(2005, 1, 1)));
		assertTrue(c.js_addCompoundPeriodChange(PERIOD_MONTHLY, date(2005, 1, 1)));

		IDataSet events = c.getEvents();
		assertEquals(4, events.getRowCount());
		assertEquals("loan", row(events, 0)[0]);
		assertEquals(1000d, num(row(events, 0), 1));
		assertEquals("payment", row(events, 1)[0]);
		assertEquals(date(2005, 5, 15), row(events, 1)[3]);
		assertEquals(Integer.valueOf(PERIOD_MONTHLY), row(events, 1)[4]);
		assertEquals(Integer.valueOf(NUMBER_UNLIMITED), row(events, 1)[5]);
		// the normal start day is the day of the month of the first date
		assertEquals(Integer.valueOf(15), row(events, 1)[6]);
		assertEquals("rate", row(events, 2)[0]);
		assertEquals("period", row(events, 3)[0]);
		assertEquals(Integer.valueOf(PERIOD_MONTHLY), row(events, 3)[4]);
	}

	@Test
	public void allOverloadsAddEvents()
	{
		AmortizationCalculation c = new AmortizationCalculation();

		assertTrue(c.js_addLoan(1, date(2005, 1, 31), null, PERIOD_MONTHLY, 2, STARTDAY_NORMAL));
		assertTrue(c.js_addLoan(1, date(2005, 1, 31), null, PERIOD_MONTHLY, 2));
		assertTrue(c.js_addLoan(1, date(2005, 1, 31), date(2005, 6, 1), PERIOD_MONTHLY));
		assertTrue(c.js_addPayment(1, date(2005, 1, 31), null, PERIOD_MONTHLY, 2, STARTDAY_NORMAL));
		assertTrue(c.js_addPayment(1, date(2005, 1, 31), null, PERIOD_MONTHLY, 2));
		assertTrue(c.js_addPayment(1, date(2005, 1, 31)));

		assertEquals(6, c.getEvents().getRowCount());
	}

	@Test
	public void invalidEventsAreRejected()
	{
		AmortizationCalculation c = new AmortizationCalculation();

		assertFalse(c.js_addLoan(1000, null));
		// the last date before the first date
		assertFalse(c.js_addLoan(1000, date(2005, 2, 1), date(2005, 1, 1), PERIOD_MONTHLY));
		// not a period
		assertFalse(c.js_addLoan(1000, date(2005, 1, 1), null, 7, 2));
		assertFalse(c.js_addCompoundPeriodChange(7, date(2005, 1, 1)));
		// only a payment can be unknown (negative)
		assertFalse(c.js_addLoan(-1, date(2005, 1, 1)));
		// a repeated event needs a period
		assertFalse(c.js_addPayment(100, date(2005, 1, 1), null, PERIOD_NONE, 2));
		// an unlimited number needs a last date, and the number must be positive
		assertFalse(c.js_addPayment(100, date(2005, 1, 1), null, PERIOD_MONTHLY));
		assertFalse(c.js_addPayment(100, date(2005, 1, 1), null, PERIOD_MONTHLY, 0));

		assertEquals(0, c.getEvents().getRowCount());
		assertTrue(c.js_addPayment(-1, date(2005, 1, 1)));
	}

	@ParameterizedTest(name = "first date {0}-{1}-{2}, start day {3}: {4}")
	@CsvSource({ //
		"2005, 2, 28, 31, true", // the last day of february may start on the 31st
		"2005, 2, 28, 30, true", //
		"2005, 2, 28, 28, true", // same day
		"2005, 2, 28, 32, false", // no 32nd
		"2005, 2, 15, 20, false", // only from the last day of the month
		"2005, 3, 31, 20, false" // not before the day of the first date
	})
	public void theStartDayOfAPeriodicEvent(int year, int month, int day, int startDay, boolean valid)
	{
		assertEquals(valid, new AmortizationCalculation().js_addPayment(10, date(year, month, day), null, PERIOD_MONTHLY, 3, startDay));
	}

	@Test
	public void eventTypesAreCheckedForTheirOwnParameters() throws Exception
	{
		Method addEvent = AmortizationCalculation.class.getDeclaredMethod("addEvent", String.class, double.class, Date.class, Date.class, int.class,
			int.class, int.class);
		addEvent.setAccessible(true);
		AmortizationCalculation c = new AmortizationCalculation();

		// a rate change has no end date, period or repeat
		assertFalse((Boolean)addEvent.invoke(c, "rate", 0.1, date(2005, 1, 1), date(2005, 2, 1), PERIOD_NONE, 1, -1));
		assertFalse((Boolean)addEvent.invoke(c, "rate", 0.1, date(2005, 1, 1), null, PERIOD_MONTHLY, 1, -1));
		// a period change has no amount
		assertFalse((Boolean)addEvent.invoke(c, "period", 1.0, date(2005, 1, 1), null, PERIOD_MONTHLY, 1, -1));
		assertFalse((Boolean)addEvent.invoke(c, "other", 1.0, date(2005, 1, 1), null, PERIOD_NONE, 1, -1));
		assertFalse((Boolean)addEvent.invoke(c, null, 1.0, date(2005, 1, 1), null, PERIOD_NONE, 1, -1));
		// the event type is not case sensitive and stored in lower case
		assertTrue((Boolean)addEvent.invoke(c, "LOAN", 1.0, date(2005, 1, 1), null, PERIOD_NONE, 1, -1));
		assertEquals("loan", c.getEvents().getRow(0)[0]);
	}

	@ParameterizedTest(name = "period {0}: {1}")
	@CsvSource({ "0, true", "1, true", "2, true", "3, true", "4, true", "6, true", "12, true", "13, true", "24, true", "26, true", "52, true",
		"365, true", "5, false", "7, false", "-1, false", "100, false" })
	public void validPeriods(int period, boolean valid)
	{
		assertEquals(valid, new AmortizationCalculation().js_isValidPeriod(period));
	}

	@ParameterizedTest
	@CsvSource({ "34.349384, 34.35", "34.344, 34.34", "-1.005, -1.0", "0, 0", "100, 100" })
	public void moneyIsRoundedToCents(double amount, double rounded)
	{
		assertEquals(rounded, new AmortizationCalculation().js_roundMoney(amount), 1e-9);
	}

	@Test
	public void eventsCanBeSortedByDate()
	{
		AmortizationCalculation c = new AmortizationCalculation();
		c.js_addPayment(100, date(2005, 3, 1));
		c.js_addLoan(1000, date(2005, 1, 1));
		c.js_addPayment(100, date(2005, 2, 1));

		c.js_sortEvents();

		assertEquals(date(2005, 1, 1), c.getEvents().getRow(0)[2]);
		assertEquals(date(2005, 2, 1), c.getEvents().getRow(1)[2]);
		assertEquals(date(2005, 3, 1), c.getEvents().getRow(2)[2]);
	}

	// --- schedules ---

	@Test
	public void withoutInterestTheLoanIsPaidOffByThePayments()
	{
		AmortizationCalculation c = new AmortizationCalculation();
		c.js_addLoan(1000, date(2005, 1, 1));
		c.js_addPayment(250, date(2005, 2, 1), null, PERIOD_MONTHLY, 4);

		assertTrue(c.js_calculateAmortizationSchedule());

		IDataSet schedule = c.getAmortizationSchedule();
		assertEquals(5, schedule.getRowCount());
		Object[] loan = row(schedule, 0);
		assertEquals("loan", loan[0]);
		assertEquals(-1000, num(loan, 2), CENT);
		assertEquals(-1000, num(loan, 5), CENT);
		double[] balances = { -750, -500, -250, 0 };
		for (int i = 0; i < 4; i++)
		{
			Object[] payment = row(schedule, i + 1);
			assertEquals("payment", payment[0]);
			assertEquals(date(2005, 2 + i, 1), payment[1]);
			assertEquals(250, num(payment, 2), CENT);
			assertEquals(0, num(payment, 3), CENT);
			assertEquals(250, num(payment, 4), CENT);
			assertEquals(balances[i], num(payment, 5), CENT);
		}
		assertEquals(0, c.js_getRestBalance(), CENT);
	}

	@Test
	public void yearlyInterestIsChargedOnThePayment()
	{
		AmortizationCalculation c = yearlyLoan();
		c.js_addPayment(1100, date(2006, 1, 1));

		c.js_calculateAmortizationSchedule();

		Object[] payment = row(c.getAmortizationSchedule(), 1);
		// 365 days of 10% on 1000
		assertEquals(100, num(payment, 3), CENT);
		assertEquals(1000, num(payment, 4), CENT);
		assertEquals(0, num(payment, 5), CENT);
		assertEquals(0, c.js_getRestBalance(), CENT);
	}

	@Test
	public void aPartialYearIsChargedPerDay()
	{
		AmortizationCalculation c = yearlyLoan();
		// 2005-07-02 is 182 days after 2005-01-01
		c.js_addPayment(500, date(2005, 7, 2));

		c.js_calculateAmortizationSchedule();

		double interest = 1000 * 0.10 * 182 / 365;
		assertEquals(Math.round(interest * 100) / 100d, num(row(c.getAmortizationSchedule(), 1), 3), CENT);
		assertEquals(-(1000 + interest - 500), c.js_getRestBalance(), CENT);
	}

	@Test
	public void aPeriodChangeFirstChargesTheInterestOfTheOldPeriod()
	{
		AmortizationCalculation c = yearlyLoan();
		c.js_addCompoundPeriodChange(PERIOD_MONTHLY, date(2006, 1, 1));
		c.js_addPayment(1100, date(2006, 1, 1));

		c.js_calculateAmortizationSchedule();

		assertEquals(100, num(row(c.getAmortizationSchedule(), 1), 3), CENT);
		assertEquals(0, c.js_getRestBalance(), CENT);
	}

	@Test
	public void monthlyCompoundingCostsMoreThanYearly()
	{
		AmortizationCalculation yearly = yearlyLoan();
		yearly.js_addPayment(1, date(2006, 1, 1));
		AmortizationCalculation monthly = new AmortizationCalculation();
		monthly.js_addRateChange(0.10, date(2005, 1, 1));
		monthly.js_addCompoundPeriodChange(PERIOD_MONTHLY, date(2005, 1, 1));
		monthly.js_addLoan(1000, date(2005, 1, 1));
		monthly.js_addPayment(1, date(2006, 1, 1));

		yearly.js_calculateAmortizationSchedule();
		monthly.js_calculateAmortizationSchedule();

		double yearlyInterest = num(row(yearly.getAmortizationSchedule(), 1), 3);
		double monthlyInterest = num(row(monthly.getAmortizationSchedule(), 1), 3);
		assertEquals(100, yearlyInterest, CENT);
		// about 1000 * (1 + 0.1/12)^12 - 1000 = 104.71, with the real number of days per month
		assertTrue(monthlyInterest > 104.4 && monthlyInterest < 105, String.valueOf(monthlyInterest));
	}

	@Test
	public void theStartDayKeepsPaymentsAtTheEndOfTheMonth()
	{
		AmortizationCalculation c = new AmortizationCalculation();
		c.js_addPayment(500, date(2005, 2, 28), null, PERIOD_MONTHLY, 5, 31);

		c.js_calculateAmortizationSchedule();

		IDataSet schedule = c.getAmortizationSchedule();
		assertEquals(date(2005, 2, 28), row(schedule, 0)[1]);
		assertEquals(date(2005, 3, 31), row(schedule, 1)[1]);
		assertEquals(date(2005, 4, 30), row(schedule, 2)[1]);
		assertEquals(date(2005, 5, 31), row(schedule, 3)[1]);
		assertEquals(date(2005, 6, 30), row(schedule, 4)[1]);
		assertEquals(2500, c.js_getRestBalance(), CENT);
	}

	@Test
	@org.junit.jupiter.api.Disabled("bug: with NUMBER_UNLIMITED expandEventList never moves to the next date (only when number > 0), "
		+ "so the loop adds the first date until the heap is full")
	public void unlimitedPaymentsStopAtTheLastDate()
	{
		AmortizationCalculation c = new AmortizationCalculation();
		c.js_addPayment(100, date(2005, 1, 15), date(2005, 4, 15), PERIOD_MONTHLY);

		c.js_calculateAmortizationSchedule();

		assertEquals(4, c.getAmortizationSchedule().getRowCount());
		assertEquals(date(2005, 4, 15), row(c.getAmortizationSchedule(), 3)[1]);
	}

	@Test
	public void aLoanRowShowsTheBalanceAsPrincipal()
	{
		AmortizationCalculation c = yearlyLoan();
		c.js_addLoan(500, date(2006, 1, 1));

		c.js_calculateAmortizationSchedule();

		Object[] secondLoan = row(c.getAmortizationSchedule(), 1);
		assertEquals(100, num(secondLoan, 3), CENT);
		assertEquals(-1600, num(secondLoan, 5), CENT);
		// current behaviour (bug): the principal column of a loan row gets the balance, the computed principal
		// (-500 - 100 = -600) is not used
		assertEquals(-1600, num(secondLoan, 4), CENT);
	}

	@Test
	public void calculatingTwiceAddsTheRowsAgain()
	{
		AmortizationCalculation c = new AmortizationCalculation();
		c.js_addLoan(1000, date(2005, 1, 1));
		c.js_addPayment(1000, date(2005, 2, 1));

		c.js_calculateAmortizationSchedule();
		c.js_calculateAmortizationSchedule();

		// current behaviour (bug): the schedule is not cleared, a second calculation appends the same rows again
		assertEquals(4, c.getAmortizationSchedule().getRowCount());
		assertEquals(0, c.js_getRestBalance(), CENT);
	}

	@Test
	public void theBiMonthlyPeriodIsAcceptedButNotSupported()
	{
		AmortizationCalculation c = new AmortizationCalculation();

		// current behaviour (bug): isValidPeriod accepts PERIOD_BI_MONTHLY (documented as "not supported yet"), so
		// the event is added, but expanding the repeated payments fails
		assertTrue(c.js_addPayment(100, date(2005, 1, 1), null, PERIOD_BI_MONTHLY, 2));
		assertThrows(IllegalArgumentException.class, c::js_calculateAmortizationSchedule);
	}

	// --- solving for the unknown ---

	@Test
	public void anUnknownPaymentIsSolved()
	{
		AmortizationCalculation c = yearlyLoan();
		c.js_addPayment(-1, date(2006, 1, 1));

		assertTrue(c.js_solveForUnknown());

		assertEquals(1100, c.js_getUnknown(), 1e-6);
		assertTrue(c.js_getError() <= 1e-8, String.valueOf(c.js_getError()));
	}

	@Test
	public void anUnknownRateIsSolved()
	{
		AmortizationCalculation c = new AmortizationCalculation();
		c.js_addRateChange(-1, date(2005, 1, 1));
		c.js_addCompoundPeriodChange(PERIOD_ANNUALY, date(2005, 1, 1));
		c.js_addLoan(1000, date(2005, 1, 1));
		c.js_addPayment(1100, date(2006, 1, 1));

		assertTrue(c.js_solveForUnknown());

		assertEquals(0.10, c.js_getUnknown(), 1e-9);
		assertTrue(c.js_getError() <= 1e-8, String.valueOf(c.js_getError()));
	}

	@Test
	public void aPeriodChangeIsIncludedWhenSolving()
	{
		AmortizationCalculation c = yearlyLoan();
		c.js_addCompoundPeriodChange(PERIOD_MONTHLY, date(2006, 1, 1));
		c.js_addPayment(-1, date(2006, 1, 1));

		assertTrue(c.js_solveForUnknown());

		assertEquals(1100, c.js_getUnknown(), 1e-6);
	}

	@Test
	public void theSolvedMonthlyPaymentPaysOffTheLoan()
	{
		AmortizationCalculation solve = monthlyAnnuity(-1);
		assertTrue(solve.js_solveForUnknown());
		double payment = solve.js_getUnknown();
		// the textbook annuity is 10000 * 0.005 / (1 - 1.005^-12) = 860.66, with real day counts a little different
		assertTrue(payment > 855 && payment < 866, String.valueOf(payment));
		assertTrue(solve.js_getError() <= 1e-8, String.valueOf(solve.js_getError()));

		AmortizationCalculation check = monthlyAnnuity(payment);
		check.js_calculateAmortizationSchedule();

		assertEquals(13, check.getAmortizationSchedule().getRowCount());
		assertEquals(0, check.js_getRestBalance(), 0.011);
	}

	private static AmortizationCalculation monthlyAnnuity(double payment)
	{
		AmortizationCalculation c = new AmortizationCalculation();
		c.js_addRateChange(0.06, date(2005, 1, 1));
		c.js_addCompoundPeriodChange(PERIOD_MONTHLY, date(2005, 1, 1));
		c.js_addLoan(10000, date(2005, 1, 1));
		c.js_addPayment(payment, date(2005, 2, 1), null, PERIOD_MONTHLY, 12);
		return c;
	}

	@Test
	public void twoUnknownsCannotBeSolved()
	{
		AmortizationCalculation c = new AmortizationCalculation();
		c.js_addRateChange(-1, date(2005, 1, 1));
		c.js_addCompoundPeriodChange(PERIOD_ANNUALY, date(2005, 1, 1));
		c.js_addLoan(1000, date(2005, 1, 1));
		c.js_addPayment(-1, date(2006, 1, 1));

		assertFalse(c.js_solveForUnknown());
	}

	@Test
	public void unknownPaymentThenUnknownRateCannotBeSolved()
	{
		AmortizationCalculation c = new AmortizationCalculation();
		c.js_addLoan(1000, date(2005, 1, 1));
		c.js_addPayment(-1, date(2005, 6, 1));
		c.js_addRateChange(-1, date(2005, 7, 1));

		assertFalse(c.js_solveForUnknown());
	}

	@Test
	public void withoutUnknownNothingIsSolved()
	{
		AmortizationCalculation c = yearlyLoan();
		c.js_addPayment(1100, date(2006, 1, 1));

		assertTrue(c.js_solveForUnknown());

		assertTrue(Double.isNaN(c.js_getUnknown()));
		assertEquals(0, c.js_getError());
	}
}
