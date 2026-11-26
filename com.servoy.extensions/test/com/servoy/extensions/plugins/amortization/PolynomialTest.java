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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Polynomial arithmetic, derivative and Newton root finding.
 */
public class PolynomialTest
{
	/** 3x^2 - 2x + 1 */
	private static Polynomial sample()
	{
		Polynomial p = new Polynomial();
		p.js_addTerm(3, 2);
		p.js_addTerm(-2, 1);
		p.js_addTerm(1, 0);
		return p;
	}

	@Test
	public void valueAndToString()
	{
		Polynomial p = sample();

		assertEquals(1, p.js_getValue(0));
		assertEquals(2, p.js_getValue(1));
		assertEquals(9, p.js_getValue(2));
		assertEquals("1.0 + -2.0*x^1 + 3.0*x^2", p.toString());
		assertEquals("0", new Polynomial().toString());
		assertEquals(0, new Polynomial().js_getValue(5));
	}

	@Test
	public void termsWithTheSameExponentAreAddedAndZeroTermsDisappear()
	{
		Polynomial p = sample();

		p.js_addTerm(2, 1);
		p.js_addTerm(1, 2);

		assertEquals("1.0 + 4.0*x^2", p.toString());
	}

	@Test
	public void addingAPolynomial()
	{
		Polynomial p = sample();
		Polynomial q = new Polynomial();
		q.js_addTerm(1, 3);
		q.js_addTerm(-1, 0);

		p.js_addPolynomial(q);

		assertEquals("-2.0*x^1 + 3.0*x^2 + 1.0*x^3", p.toString());
	}

	@Test
	public void multiplyingByATerm()
	{
		Polynomial p = sample();

		p.js_multiplyByTerm(2, 1);

		assertEquals("2.0*x^1 + -4.0*x^2 + 6.0*x^3", p.toString());
		p.js_multiplyByTerm(0, 3);
		assertEquals("0", p.toString());
	}

	@Test
	public void multiplyingByAPolynomial()
	{
		// (x + 1)(x - 1) = x^2 - 1
		Polynomial p = new Polynomial();
		p.js_addTerm(1, 1);
		p.js_addTerm(1, 0);
		Polynomial q = new Polynomial();
		q.js_addTerm(1, 1);
		q.js_addTerm(-1, 0);

		p.js_multiplyByPolynomial(q);

		assertEquals("-1.0 + 1.0*x^2", p.toString());
		p.js_multiplyByPolynomial(new Polynomial());
		assertEquals("0", p.toString());
	}

	@Test
	public void derivative()
	{
		Polynomial p = sample();

		assertEquals("-2.0 + 6.0*x^1", p.js_getDerivative().toString());
		assertEquals(10, p.js_getDerivativeValue(2));
		// the derivative is a copy
		p.js_getDerivative().js_addTerm(100, 0);
		assertEquals(10, p.js_getDerivativeValue(2));
	}

	@Test
	public void newtonFindsARoot()
	{
		Polynomial p = new Polynomial();
		p.js_addTerm(1, 2);
		p.js_addTerm(-4, 0);

		assertEquals(2, p.js_findRoot(1, 1e-12, 100), 1e-9);
		assertEquals(-2, p.js_findRoot(-1, 1e-12, 100), 1e-9);
		// a flat start has no next step
		assertTrue(Double.isNaN(p.js_findRoot(0, 1e-12, 100)));
		// no iterations: the start value
		assertEquals(5, p.js_findRoot(5, 1e-12, 0));
	}

	@Test
	public void setToZero()
	{
		Polynomial p = sample();
		p.js_getValue(1);

		p.js_setToZero();

		assertEquals("0", p.toString());
		assertEquals(0, p.js_getValue(3));
	}

	@Test
	public void aCopyHasItsOwnTerms()
	{
		Polynomial p = sample();
		Polynomial copy = new Polynomial(p);

		copy.js_addTerm(5, 4);

		assertEquals("1.0 + -2.0*x^1 + 3.0*x^2", p.toString());
		assertEquals("1.0 + -2.0*x^1 + 3.0*x^2 + 5.0*x^4", copy.toString());
		assertEquals(2, p.js_getValue(1));
	}

	@Test
	public void aCopyMadeAfterAnEvaluationSharesTheCachedTerms()
	{
		Polynomial p = new Polynomial();
		p.js_addTerm(2, 1);
		assertEquals(2, p.js_getValue(1));
		Polynomial copy = new Polynomial(p);

		p.js_addTerm(3, 1);

		assertEquals("2.0*x^1", copy.toString());
		// current behaviour (bug): the copy reuses the cached term array of the original, and addTerm changes the
		// coefficient of that same Term object, so the copy now evaluates as 5x instead of 2x
		assertEquals(5, copy.js_getValue(1));
	}

	@Test
	public void termOrdering()
	{
		Polynomial p = new Polynomial();
		Polynomial.Term a = p.new Term(2, 1);
		Polynomial.Term b = p.new Term(3, 1);
		Polynomial.Term c = p.new Term(1, 2);

		assertTrue(a.compareTo(b) < 0);
		assertTrue(c.compareTo(a) > 0);
		assertEquals(0, a.compareTo(p.new Term(a)));
		assertEquals("2.0*x^1", a.toString());
		assertEquals("4.0", p.new Term(4, 0).toString());
	}
}
