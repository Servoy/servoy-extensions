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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.j2db.dataprocessing.BufferedDataSet;
import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IConstantsObject;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.SafeArrayList;

/**
 * This module details an amortization calculation system that defines constants for various periodic intervals and events, as well as methods for managing loans, payments, rate changes, and schedule calculations.
 *
 * <h2>Constants</h2>
 * <p>
 * The module includes constants representing various time periods, such as annual, bi-annual, monthly, and weekly intervals, as well as unique identifiers like <code>NUMBER_UNLIMITED</code> for unlimited occurrences and <code>STARTDAY_NORMAL</code> for consistent start-day tracking. These constants simplify configuring amortization schedules with precise periodicity and event behaviors.
 * </p>
 *
 * <h2>Methods</h2>
 * <p>
 * Key methods facilitate:
 * <ul>
 *   <li><b>Loan and Payment Management</b>: Functions like <code>addLoan</code> and <code>addPayment</code> support different configurations, including start and end dates, periodic intervals, and repetitions.</li>
 *   <li><b>Rate Changes and Period Adjustments</b>: <code>addRateChange</code> and <code>addCompoundPeriodChange</code> enable dynamic updates to interest rates and compounding periods.</li>
 *   <li><b>Amortization Schedule Handling</b>: Methods like <code>calculateAmortizationSchedule</code> and <code>getAmortizationSchedule</code> compute and retrieve detailed schedules, while <code>getRestBalance</code> and <code>solveForUnknown</code> provide insights into financial balances and unknown variables.</li>
 * </ul>
 * </p>
 *
 * <h2>Features</h2>
 * <p>
 * <ul>
 *   <li>Utilities like <code>isValidPeriod</code> and <code>roundMoney</code> ensure input validity and precision.</li>
 *   <li>Event management and sorting (<code>sortEvents</code>, <code>getEvents</code>) organize financial activities over time.</li>
 * </ul>
 * </p>
 * <p>
 * This module streamlines the creation and management of customized amortization schedules with precision and adaptability.
 * </p>
 *
 * @author sebster
 */
@ServoyDocumented
public class AmortizationCalculation implements IScriptable, IConstantsObject
{

	/**
	 * The date format used for debug logging.
	 */
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd"); //$NON-NLS-1$


	/**
	 * The string constant used to identify a loan event.
	 */
	private static final String EVENT_LOAN = "loan"; //$NON-NLS-1$

	/**
	 * The string constant used to identify a payment event.
	 */
	private static final String EVENT_PAYMENT = "payment"; //$NON-NLS-1$

	/**
	 * The string constant used to identify a rate change event.
	 */
	private static final String EVENT_RATE = "rate"; //$NON-NLS-1$

	/**
	 * The string constant used to identify a compounding period change event.
	 */
	private static final String EVENT_PERIOD = "period"; //$NON-NLS-1$


	/**
	 * The numeric constant used to identify a daily period.
	 *
	 * @sample
	 * var c = plugins.amortization.newCalculation();
	 * c.addPayment(500, new Date(2005, 1, 28), null,
	 * 			plugins.amortization.AmortizationCalculation.PERIOD_DAILY, 5,
	 * 			plugins.amortization.AmortizationCalculation.STARTDAY_NORMAL);
	 *
	 * var c2 = plugins.amortization.newCalculation();
	 * c2.addPayment(300, new Date(2006, 11, 24), new Date(2006, 12, 24),
	 * 			plugins.amortization.AmortizationCalculation.PERIOD_BI_MONTHLY,
	 * 			plugins.amortization.AmortizationCalculation.NUMBER_UNLIMITED, 30);
	 */
	public static final int PERIOD_DAILY = 365;

	/**
	 * The numeric constant used to identify a weekly period.
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_WEEKLY = 52;

	/**
	 * The numeric constant used to identify a two-weekly period (once every two weeks).
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_TWO_WEEKLY = 26;

	/**
	 * The numeric constant used to identify a bi-monthly period (twice every month).
	 * TODO: this period is not supported yet.
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_BI_MONTHLY = 24;

	/**
	 * The numeric constant used to identify a four-weekly period (once every four weeks).
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_FOUR_WEEKLY = 13;

	/**
	 * The numeric constant used to identify a monthly period.
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_MONTHLY = 12;

	/**
	 * The numeric constant used to identify a two-monthly period (once every two months).
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_TWO_MONTHLY = 6;

	/**
	 * The numeric constant used to identify a quarterly period (once every three months).
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_QUARTERLY = 4;

	/**
	 * The numeric constant used to identify a four-monthly period (once every four months).
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_FOUR_MONTHLY = 3;

	/**
	 * The numeric constant used to identify a bi-annual period (twice every year).
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_BI_ANNUALLY = 2;

	/**
	 * The numeric constant used to identify an annual period.
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_ANNUALY = 1;

	/**
	 * The numeric constant used to identify that there is no period.
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int PERIOD_NONE = 0;


	/**
	 * The numeric constant used to identify an unlimited number of repeated events.
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int NUMBER_UNLIMITED = -1;


	/**
	 * The numeric constant used to identify that the same start day should be used as
	 * the day of the month of the starting date of the event.
	 *
	 * @sampleas {@link #PERIOD_DAILY}
	 */
	public static final int STARTDAY_NORMAL = -1;


	protected static final int EI_EVENT = 0;
	protected static final int EI_AMOUNT = 1;
	protected static final int EI_START = 2;
	protected static final int EI_END = 3;
	protected static final int EI_PERIOD = 4;
	protected static final int EI_NUMBER = 5;
	protected static final int EI_STARTDAY = 6;

	protected static final int UNKNOWN_NONE = 0;
	protected static final int UNKNOWN_RATE = 1;
	protected static final int UNKNOWN_PAYMENT = 2;

	// The list of events.
	private final IDataSet events;
	private CalendarEvent[] expandedEvents;

	// The resulting amortization schedule.
	private final IDataSet schedule;

	// Balance and interest variables.
	private Polynomial balancePolynomial;
	private Polynomial interestPolynomial;
	private double unknownValue = Double.NaN;
	private double errorValue = 0;
	private int unknownType;

	private final double[] bni = new double[3];

	public AmortizationCalculation()
	{
		events = new BufferedDataSet(new String[] { "event", "amount", "start_date", "end_date", "period", "number", "start_day" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
			new SafeArrayList<Object[]>());
		schedule = new BufferedDataSet(new String[] { "event", "date", "payment", "interest", "principal", "balance" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			new SafeArrayList<Object[]>());
	}

	/**
	 * Adds a compound period change.
	 *
	 * @sample
	 * var c = plugins.amortization.newCalculation();
	 * c.addRateChange(r, new Date(2005, 0, 1));
	 * c.addCompoundPeriodChange(12, new Date(2005, 0, 1));
	 * c.addLoan(2000, new Date(2005, 0, 1));
	 * c.addPayment(500, new Date(2005, 1, 28), null, 12, 5, 31);
	 *
	 * @param newPeriod
	 * @param date
	 */
	public boolean js_addCompoundPeriodChange(int newPeriod, Date date)
	{
		return addCompoundPeriodChange(newPeriod, date);
	}

	/**
	 * Adds a loan.
	 *
	 * @sampleas js_addCompoundPeriodChange(int, Date)
	 *
	 * @param amount
	 * @param firstDate
	 * @param lastDate
	 * @param period
	 * @param number
	 * @param startday
	 */
	public boolean js_addLoan(double amount, Date firstDate, Date lastDate, int period, int number, int startday)
	{
		return addLoan(amount, firstDate, lastDate, period, number, startday);
	}

	/**
	 * @clonedesc js_addLoan(double, Date, Date, int, int, int)
	 * @sampleas js_addLoan(double, Date, Date, int, int, int)
	 *
	 * @param amount
	 * @param firstDate
	 * @param lastDate
	 * @param period
	 * @param number
	 */
	public boolean js_addLoan(double amount, Date firstDate, Date lastDate, int period, int number)
	{
		return addLoan(amount, firstDate, lastDate, period, number);
	}

	/**
	 * @clonedesc js_addLoan(double, Date, Date, int, int, int)
	 * @sampleas js_addLoan(double, Date, Date, int, int, int)
	 *
	 * @param amount
	 * @param firstDate
	 * @param lastDate
	 * @param period
	 */
	public boolean js_addLoan(double amount, Date firstDate, Date lastDate, int period)
	{
		return addLoan(amount, firstDate, lastDate, period);
	}

	/**
	 * @clonedesc js_addLoan(double, Date, Date, int, int, int)
	 * @sampleas js_addLoan(double, Date, Date, int, int, int)
	 *
	 * @param amount
	 * @param date
	 */
	public boolean js_addLoan(double amount, Date date)
	{
		return addLoan(amount, date);
	}

	/**
	 * Adds a payment.
	 *
	 * @sampleas js_addCompoundPeriodChange(int, Date)
	 *
	 * @param amount
	 * @param firstDate
	 * @param lastDate
	 * @param period
	 * @param number
	 * @param startday
	 */
	public boolean js_addPayment(double amount, Date firstDate, Date lastDate, int period, int number, int startday)
	{
		return addPayment(amount, firstDate, lastDate, period, number, startday);
	}

	/**
	 * @clonedesc js_addPayment(double, Date, Date, int, int, int)
	 * @sampleas js_addPayment(double, Date, Date, int, int, int)
	 *
	 * @param amount
	 * @param firstDate
	 * @param lastDate
	 * @param period
	 * @param number
	 */
	public boolean js_addPayment(double amount, Date firstDate, Date lastDate, int period, int number)
	{
		return addPayment(amount, firstDate, lastDate, period, number);
	}

	/**
	 * @clonedesc js_addPayment(double, Date, Date, int, int, int)
	 * @sampleas js_addPayment(double, Date, Date, int, int, int)
	 *
	 * @param amount
	 * @param firstDate
	 * @param lastDate
	 * @param period
	 */
	public boolean js_addPayment(double amount, Date firstDate, Date lastDate, int period)
	{
		return addPayment(amount, firstDate, lastDate, period);
	}

	/**
	 * @clonedesc js_addPayment(double, Date, Date, int, int, int)
	 * @sampleas js_addPayment(double, Date, Date, int, int, int)
	 *
	 * @param amount
	 * @param date
	 */
	public boolean js_addPayment(double amount, Date date)
	{
		return addPayment(amount, date);
	}

	/**
	 * Sets a new interest rate.
	 *
	 * @sampleas js_addCompoundPeriodChange(int, Date)
	 *
	 * @param newRate
	 * @param date
	 */
	public boolean js_addRateChange(double newRate, Date date)
	{
		return addRateChange(newRate, date);
	}

	/**
	 * Calculates the amortization schedule.
	 *
	 * @sample
	 * plugins.amortization.calculateAmortizationSchedule();
	 */
	public boolean js_calculateAmortizationSchedule()
	{
		return this.calculateAmortizationSchedule();
	}

	/**
	 * Gets the amortization schedule as a JSDataSet.
	 *
	 * @sample
	 * plugins.amortization.getAmortizationSchedule();
	 */
	@JSFunction
	public IDataSet getAmortizationSchedule()
	{
		return schedule;
	}

	/**
	 * Returns the error that remains when solving for the unknown.
	 * Please note that the error should be less or equal to 1E-8 - otherwise, the solveForUnknown value is incorrect.
	 *
	 * @sample
	 * var c = plugins.amortization.newCalculation();
	 * // sets the rate to -1 for unknown.
	 * c.addRateChange(-1, new Date(2005, 0, 1));
	 * c.addCompoundPeriodChange(12, new Date(2005, 0, 1));
	 * c.addLoan(2000, new Date(2005, 0, 1));
	 * var lastDate = null;
	 * var period = 12;
	 * var number_count = 5;
	 * var startday = 31;
	 * c.addPayment(500, new Date(2005, 1, 28), lastDate, period,number_count, startday);
	 * // solves for the interest rate.
	 * c.solveForUnknown();
	 * // gets the interest rate and the error in the calculation.
	 * // which should be small (otherwise the calculation did
	 * // not converge for some reason.
	 * var r = c.getUnknown();
	 * var e = c.getError();
	 */
	public double js_getError()
	{
		return getError();
	}

	/**
	 * Returns all the amortization events - such as rate changes, loan events, payment events, compounding period changes.
	 *
	 * @sample
	 * plugins.amortization.getEvents();
	 */
	@JSFunction
	public IDataSet getEvents()
	{
		return events;
	}

	/**
	 * Gets the rest balance after the amortization schedule.
	 *
	 * @sample
	 * var rb = plugins.amortization.getRestBalance();
	 */
	public double js_getRestBalance()
	{
		return getRestBalance();
	}

	/**
	 * Returns the solveForUnknown value.
	 *
	 * @sample
	 * plugins.amortization.getUnknown();
	 */
	public double js_getUnknown()
	{
		return getUnknown();
	}

	/**
	 * Returns true if the period is valid, or false if the period is not valid.
	 *
	 * @sample
	 * var v_period = plugins.amortization.isValidPeriod(12);
	 *
	 * @param period
	 */
	public boolean js_isValidPeriod(int period)
	{
		return isValidPeriod(period);
	}

	/**
	 * Rounds a number up to the nearest cents.
	 *
	 * @sample
	 * //rounds the number up to 34.35
	 * var rm = plugins.amortization.roundMoney(34.349384);
	 *
	 * @param amount
	 */
	public double js_roundMoney(double amount)
	{
		return roundMoney(amount);
	}

	/**
	 * Returns true if successful or false if the call failed.
	 *
	 * @sample
	 * plugins.amortization.solveForUnknown();
	 */
	public boolean js_solveForUnknown()
	{
		return solveForUnknown();
	}

	/**
	 * Sorts the amortization events ascending by date.
	 *
	 * @sample
	 * plugins.amortization.sortEvents();
	 */
	public void js_sortEvents()
	{
		sortEvents();
	}

	// ADD A LOAN
	public boolean addLoan(double amount, Date firstDate, Date lastDate, int period, int number, int startday)
	{
		return addEvent(EVENT_LOAN, amount, firstDate, lastDate, period, number, startday);
	}

	public boolean addLoan(double amount, Date firstDate, Date lastDate, int period, int number)
	{
		return addEvent(EVENT_LOAN, amount, firstDate, lastDate, period, number, STARTDAY_NORMAL);
	}

	public boolean addLoan(double amount, Date firstDate, Date lastDate, int period)
	{
		return addEvent(EVENT_LOAN, amount, firstDate, lastDate, period, NUMBER_UNLIMITED, STARTDAY_NORMAL);
	}

	public boolean addLoan(double amount, Date date)
	{
		return addEvent(EVENT_LOAN, amount, date, null, PERIOD_NONE, 1, STARTDAY_NORMAL);
	}

	// ADD A PAYMENT
	public boolean addPayment(double amount, Date firstDate, Date lastDate, int period, int number, int startday)
	{
		return addEvent(EVENT_PAYMENT, amount, firstDate, lastDate, period, number, startday);
	}

	public boolean addPayment(double amount, Date firstDate, Date lastDate, int period, int number)
	{
		return addEvent(EVENT_PAYMENT, amount, firstDate, lastDate, period, number, STARTDAY_NORMAL);
	}

	public boolean addPayment(double amount, Date firstDate, Date lastDate, int period)
	{
		return addEvent(EVENT_PAYMENT, amount, firstDate, lastDate, period, NUMBER_UNLIMITED, STARTDAY_NORMAL);
	}

	public boolean addPayment(double amount, Date date)
	{
		return addEvent(EVENT_PAYMENT, amount, date, null, PERIOD_NONE, 1, STARTDAY_NORMAL);
	}

	// ADD A RATE CHANGE
	public boolean addRateChange(double newRate, Date date)
	{
		return addEvent(EVENT_RATE, newRate, date, null, PERIOD_NONE, 1, STARTDAY_NORMAL);
	}

	// ADD A COMPOUNDING PERIOD CHANGE
	public boolean addCompoundPeriodChange(int newPeriod, Date date)
	{
		return addEvent(EVENT_PERIOD, 0.0D, date, null, newPeriod, 1, STARTDAY_NORMAL);
	}

	public void sortEvents()
	{
		events.sort(EI_START, true);
	}

	public boolean solveForUnknown()
	{
		expandEventList();

		double rate = 0.0D;
		int compoundingPeriod = PERIOD_NONE;
		int compoundingStartDay = -1;
		Calendar interestCalendar = null;

		balancePolynomial = new Polynomial();
		interestPolynomial = new Polynomial();

		unknownType = UNKNOWN_NONE;
		for (CalendarEvent expandedEvent : expandedEvents)
		{
			Calendar calendar = expandedEvent.calendar;
			Object[] event = expandedEvent.event;

			if (event[EI_EVENT] == EVENT_RATE)
			{
				rate = ((Double)event[EI_AMOUNT]).doubleValue();
				if (rate < 0) // unknown
				{
					if (unknownType != UNKNOWN_NONE && unknownType != UNKNOWN_RATE) return false;
					unknownType = UNKNOWN_RATE;
				}
				Debug.trace(dateFormat.format(calendar.getTime()) + ": New rate: " + rate); //$NON-NLS-1$
			}
			else if (event[EI_EVENT] == EVENT_PERIOD)
			{
				if (interestCalendar != null && compoundingStartDay != -1)
				{
					updateInterest(balancePolynomial, interestPolynomial, compoundingStartDay, compoundingPeriod, rate, interestCalendar, calendar);
				}
				compoundingPeriod = ((Integer)event[EI_PERIOD]).intValue();
				interestCalendar = (Calendar)calendar.clone();
				compoundingStartDay = interestCalendar.get(Calendar.DAY_OF_MONTH);

				Debug.trace(dateFormat.format(calendar.getTime()) + ": New compounding period: " + compoundingPeriod); //$NON-NLS-1$
			}
			else if (event[EI_EVENT] == EVENT_LOAN)
			{

				updateInterest(balancePolynomial, interestPolynomial, compoundingStartDay, compoundingPeriod, rate, interestCalendar, calendar);
				interestCalendar = (Calendar)calendar.clone();
				balancePolynomial.addTerm(-((Double)event[EI_AMOUNT]).doubleValue(), 0);
				Debug.trace(dateFormat.format(calendar.getTime()) + ": New loan: " + event[EI_AMOUNT]); //$NON-NLS-1$
			}
			else if (event[EI_EVENT] == EVENT_PAYMENT)
			{
				updateInterest(balancePolynomial, interestPolynomial, compoundingStartDay, compoundingPeriod, rate, interestCalendar, calendar);
				interestCalendar = (Calendar)calendar.clone();
				double amount = ((Double)event[EI_AMOUNT]).doubleValue();
				if (amount < 0) // unknown
				{
					if (unknownType != UNKNOWN_NONE && unknownType != UNKNOWN_PAYMENT) return false;
					unknownType = UNKNOWN_PAYMENT;
					balancePolynomial.addTerm(1, 1);
				}
				else
				{
					balancePolynomial.addTerm(amount, 0);
				}
				Debug.trace(dateFormat.format(calendar.getTime()) + ": New payment: " + event[EI_AMOUNT]); //$NON-NLS-1$
			}
		}

		if (unknownType != UNKNOWN_NONE)
		{
			balancePolynomial.addPolynomial(interestPolynomial);
			unknownValue = balancePolynomial.findRoot(1, 5E-14, 100);
			errorValue = Math.abs(balancePolynomial.getValue(unknownValue));
		}

		return true;
	}

	public double getUnknown()
	{
		return unknownValue;
	}

	public double getError()
	{
		return errorValue;
	}

	public boolean calculateAmortizationSchedule()
	{
		expandEventList();

		double rate = 0.0D;
		int compoundingPeriod = PERIOD_NONE;
		int compoundingStartDay = -1;
		Calendar interestCalendar = null;

		bni[0] = bni[1] = bni[2] = 0D;

		for (CalendarEvent expandedEvent : expandedEvents)
		{
			Calendar calendar = expandedEvent.calendar;
			Object[] event = expandedEvent.event;

			if (event[EI_EVENT] == EVENT_RATE)
			{
				rate = ((Double)event[EI_AMOUNT]).doubleValue();
				Debug.trace(dateFormat.format(calendar.getTime()) + ": New rate: " + rate); //$NON-NLS-1$
			}
			else if (event[EI_EVENT] == EVENT_PERIOD)
			{
				if (interestCalendar != null && compoundingStartDay != -1)
				{
					updateInterest(bni, compoundingStartDay, compoundingPeriod, rate, interestCalendar, calendar);
				}
				compoundingPeriod = ((Integer)event[EI_PERIOD]).intValue();
				interestCalendar = (Calendar)calendar.clone();
				compoundingStartDay = interestCalendar.get(Calendar.DAY_OF_MONTH);

				Debug.trace(dateFormat.format(calendar.getTime()) + ": New compounding period: " + compoundingPeriod); //$NON-NLS-1$
			}
			else if (event[EI_EVENT] == EVENT_LOAN)
			{
				updateInterest(bni, compoundingStartDay, compoundingPeriod, rate, interestCalendar, calendar);
				interestCalendar = (Calendar)calendar.clone();
				double amount = ((Double)event[EI_AMOUNT]).doubleValue();
				bni[0] -= amount;
				double interest, balance, principal;
				interest = -roundMoney(bni[2]);
				balance = roundMoney(bni[0] + bni[1]);
				principal = roundMoney(-amount - interest);
				schedule.addRow(new Object[] { EVENT_LOAN, calendar.getTime(), new Double(-amount), new Double(interest), new Double(balance), new Double(
					balance) }); // "event", "date", "payment", "interest", "principal", "balance"
				Debug.trace(dateFormat.format(calendar.getTime()) + ": New loan: " + event[EI_AMOUNT]); //$NON-NLS-1$
				bni[2] = 0;
			}
			else if (event[EI_EVENT] == EVENT_PAYMENT)
			{
				updateInterest(bni, compoundingStartDay, compoundingPeriod, rate, interestCalendar, calendar);
				interestCalendar = (Calendar)calendar.clone();
				double amount = ((Double)event[EI_AMOUNT]).doubleValue();
				bni[0] += amount;
				double interest, balance, principal;
				interest = -roundMoney(bni[2]);
				balance = roundMoney(bni[0] + bni[1]);
				principal = roundMoney(amount - interest);
				schedule.addRow(new Object[] { EVENT_PAYMENT, calendar.getTime(), event[EI_AMOUNT], new Double(interest), new Double(principal), new Double(
					balance) }); // "event", "date", "payment", "interest", "principal", "balance"
				Debug.trace(dateFormat.format(calendar.getTime()) + ": New payment: " + event[EI_AMOUNT]); //$NON-NLS-1$
				bni[2] = 0;
			}
		}

		return true;
	}

	private void updateInterest(double[] bni, int startDay, int compoundingPeriod, double rate, Calendar from, Calendar to)
	{
		if (compoundingPeriod == PERIOD_NONE) return;

		Calendar last = (Calendar)from.clone();
		Calendar calendar = nextCalendar(last, startDay, compoundingPeriod);
		rate = rate / 365D;
		while (getDays(calendar, to) >= 0)
		{
			long days = getDays(last, calendar);
			last = calendar;
			double deltaInterest = days * rate * bni[0];
			bni[1] += deltaInterest;
			bni[2] += deltaInterest;
			bni[0] += bni[1];
			bni[1] = 0.0D;
			calendar = nextCalendar(last, startDay, compoundingPeriod);
		}

		long days = getDays(last, to);
		double deltaInterest = days * rate * bni[0];
		bni[1] += deltaInterest;
		bni[2] += deltaInterest;
	}

	private void updateInterest(Polynomial balancePolynomial, Polynomial interestPolynomial, int startDay, int compoundingPeriod, double rate, Calendar from,
		Calendar to)
	{
		if (compoundingPeriod == PERIOD_NONE) return;

		Calendar last = (Calendar)from.clone();
		Calendar calendar = nextCalendar(last, startDay, compoundingPeriod);
		while (getDays(calendar, to) >= 0)
		{
			double coefficient = (getDays(last, calendar)) / 365D; // Use 365 day year to determine daily interest rate!
			last = calendar;
			Polynomial deltaInterest = new Polynomial(balancePolynomial);
			if (rate < 0) // unknown
				deltaInterest.multiplyByTerm(coefficient, 1);
			else deltaInterest.multiplyByTerm(coefficient * rate, 0);
			interestPolynomial.addPolynomial(deltaInterest);
			balancePolynomial.addPolynomial(interestPolynomial);
			interestPolynomial.setToZero();
			calendar = nextCalendar(last, startDay, compoundingPeriod);
		}

		double coefficient = (getDays(last, to)) / 365D; // Use 365 day year to determine daily interest rate!
		Polynomial deltaInterest = new Polynomial(balancePolynomial);
		if (rate < 0) // unknown
			deltaInterest.multiplyByTerm(coefficient, 1);
		else deltaInterest.multiplyByTerm(coefficient * rate, 0);
		interestPolynomial.addPolynomial(deltaInterest);
	}

	public double getRestBalance()
	{
		return roundMoney(bni[0] + bni[1]);
	}

	public boolean isValidPeriod(int period)
	{
		return period == PERIOD_NONE || period == PERIOD_ANNUALY || period == PERIOD_BI_ANNUALLY || period == PERIOD_FOUR_MONTHLY ||
			period == PERIOD_QUARTERLY || period == PERIOD_TWO_MONTHLY || period == PERIOD_MONTHLY || period == PERIOD_FOUR_WEEKLY ||
			period == PERIOD_BI_MONTHLY || period == PERIOD_TWO_WEEKLY || period == PERIOD_WEEKLY || period == PERIOD_DAILY;
	}

	/**
	 * Returns the specified amount rounded to the nearest hundredth.
	 *
	 * @param amount the amount to round
	 *
	 * @return the amount rounded to the nearest hundredth
	 */
	public double roundMoney(double amount)
	{
		return Math.round(amount * 100D) / 100D;
	}

	/**
	 * Add an event to the event list.
	 *
	 * @param event the event type
	 * @param amount the event amount (a double)
	 * @param firstDate the first date of the event (start date)
	 * @param lastDate the last date of the event (end date)
	 * @param period the period of the event
	 * @param number the number of times the event occurs
	 * @param startDay the day of the month the event occurs on
	 *
	 * @return true if the event was added, false if there was an error adding the event
	 */
	private boolean addEvent(String event, double amount, Date firstDate, Date lastDate, int period, int number, int startDay)
	{
		// The event must be given.
		if (event == null) return false;

		// The first date must be given.
		if (firstDate == null) return false;

		// The last date must be after first date, if given.
		if (lastDate != null && lastDate.compareTo(firstDate) < 0) return false;

		// The compounding period must be one of the supported periods.
		if (!isValidPeriod(period)) return false;

		// Check the parameters for specific event types.
		if (EVENT_LOAN.equalsIgnoreCase(event) || EVENT_PAYMENT.equalsIgnoreCase(event))
		{
			// The amount must be bigger than 0.0 or an unknown payment.
			if (amount < 0D && !EVENT_PAYMENT.equalsIgnoreCase(event)) return false;

			// Check the period of a periodic event.
			if (number > 1 && period == PERIOD_NONE) return false;
		}
		else if (EVENT_RATE.equalsIgnoreCase(event))
		{
			// A rate event must not have last date or period other than 0 or number other than 1.
			if (lastDate != null || period != PERIOD_NONE || number != 1) return false;
		}
		else if (EVENT_PERIOD.equalsIgnoreCase(event))
		{
			// A period event must not have an amount or last date or number other than 1.
			if (amount != 0D || lastDate != null || number != 1) return false;

		}
		else
		{
			// Unknown event.
			return false;
		}

		// The number must be positive, or unlimited.
		if (!(number > 0 || (lastDate != null && number == NUMBER_UNLIMITED))) return false;

		// The start day of the month of a periodic event may different from the day of the
		// month of the first date. If it is not different, then it should be set to
		// number should be set to -1 or the day of the month of the first date.. If it IS
		// different, then the day of the month of the first date must be the last day of the
		// month and the number must be greater than this day of the month and at most 31.
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(firstDate);
		int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
		if (startDay == STARTDAY_NORMAL) startDay = dayOfMonth;
		else if ((startDay != dayOfMonth) && (startDay <= dayOfMonth || startDay > 31 || dayOfMonth != calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
			return false;

		// Add an event row to events list.
		events
			.addRow(new Object[] { event.toLowerCase().intern(), new Double(amount), firstDate, lastDate, new Integer(period), new Integer(number), new Integer(
				startDay) });

		return true;
	}

	/**
	 * Get the exact number of days between two calendars.
	 *
	 * @param from the start calendar
	 * @param to then end calendar
	 *
	 * @return the exact number of days between the start and end calendar
	 */
	private long getDays(Calendar from, Calendar to)
	{
		return (to.getTimeInMillis() - from.getTimeInMillis() + 12 * 60 * 60 * 1000) / (24 * 60 * 60 * 1000);
	}

	/**
	 * Finds the next calendar for the given calendar which is the specified period after the current calendar.
	 * Note that the start day of the period is the day of the month on which the first period started, which may
	 * be a day that does not exist in every month (i.e., the 29th, 30th, or 31st). Thus the current date could
	 * be 28-02-2005, the period monthly, and the start day the 31st; the next calendar then would be 31-03-2005.
	 *
	 * @param calendar the current date
	 * @param startDay the start day of the period
	 * @param period the period
	 *
	 * @return the next calendar which is the specified period after the specified calendar
	 */
	static Calendar nextCalendar(Calendar calendar, int startDay, int period)
	{
		Calendar next = (Calendar)calendar.clone();

		// Get the next date in for the specified periodicity after the specified current date.
		switch (period)
		{
			case PERIOD_ANNUALY :
				next.roll(Calendar.YEAR, 1);
				break;
			case PERIOD_BI_ANNUALLY :
			case PERIOD_FOUR_MONTHLY :
			case PERIOD_QUARTERLY :
			case PERIOD_TWO_MONTHLY :
			case PERIOD_MONTHLY :
				next.add(Calendar.MONTH, 12 / period);
				if (next.get(Calendar.DAY_OF_MONTH) != startDay) next.set(Calendar.DAY_OF_MONTH,
					Math.min(next.getActualMaximum(Calendar.DAY_OF_MONTH), startDay));
				break;
			case PERIOD_FOUR_WEEKLY :
			case PERIOD_TWO_WEEKLY :
			case PERIOD_WEEKLY :
				next.add(Calendar.WEEK_OF_YEAR, 52 / period);
				break;
			case PERIOD_DAILY :
				next.add(Calendar.DAY_OF_YEAR, 1);
				break;
			case PERIOD_NONE :
				break;
			default :
				throw new IllegalArgumentException("unsupported period (" + period + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return next;
	}

	/**
	 * Expand the event list to get a list of calendar events. Repeated events are expanded so that each occurrence is
	 * in the list. The list is sorted but relative order of events at the same time is kept as it is.
	 */
	private void expandEventList()
	{
		// Expand the events to a list.
		ArrayList<CalendarEvent> expandedList = new ArrayList<CalendarEvent>();
		for (int i = 0; i < events.getRowCount(); i++)
		{
			// Get the next event.
			Object[] event = events.getRow(i);

			// Get the start calendar.
			Date startDate = (Date)event[EI_START];
			Calendar startCalendar = Calendar.getInstance();
			startCalendar.setTime(startDate);

			// Get the end calendar.
			Date endDate = (Date)event[EI_END];
			Calendar endCalendar = null;
			if (endDate != null)
			{
				endCalendar = Calendar.getInstance();
				endCalendar.setTime(endDate);
			}

			// Expand the event.
			int number = ((Integer)event[EI_NUMBER]).intValue();
			int period = ((Integer)event[EI_PERIOD]).intValue();
			int startDay = ((Integer)event[EI_STARTDAY]).intValue();

			Calendar calendar = startCalendar;
			while (number > 0 || (number == NUMBER_UNLIMITED && endCalendar != null && !calendar.after(endCalendar)))
			{
				// Create a new event for this date.
				expandedList.add(new CalendarEvent(calendar, event));

				// Update the number of times this event has occurred.
				if (number != NUMBER_UNLIMITED) number--;

				// Find the date of the next event.
				if (number > 0) calendar = nextCalendar(calendar, startDay, period);
			}
		}

		// Convert the list to an array and sort it.
		expandedEvents = new CalendarEvent[expandedList.size()];
		expandedList.toArray(expandedEvents);
		Arrays.sort(expandedEvents);
	}

	/**
	 * This class tags an event with a calendar, and allows these events to be
	 * compared by their date.
	 */
	private class CalendarEvent implements Comparable
	{
		/**
		 * The calendar of this event.
		 */
		private final Calendar calendar;

		/**
		 * The event itself.
		 */
		private final Object[] event;

		/**
		 * Create a new calendar event.
		 *
		 * @param calendar the calendar of the calendar event
		 * @param event the event of the calendar event
		 */
		private CalendarEvent(Calendar calendar, Object[] event)
		{
			this.calendar = calendar;
			this.event = event;
		}

		/**
		 * Compare to calendar events by their date.
		 */
		public int compareTo(Object other)
		{
			return calendar.getTime().compareTo(((CalendarEvent)other).calendar.getTime());
		}
	}

}
