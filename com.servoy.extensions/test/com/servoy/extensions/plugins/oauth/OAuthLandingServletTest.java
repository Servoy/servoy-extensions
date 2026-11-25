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
package com.servoy.extensions.plugins.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthLandingServletTest
{
	private final OAuthLandingServlet servlet = new OAuthLandingServlet(new OAuthPlugin());

	private String get(String scheme, int port, String pathInfo, String query) throws Exception
	{
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getScheme()).thenReturn(scheme);
		when(request.getServerName()).thenReturn("example.com");
		when(Integer.valueOf(request.getServerPort())).thenReturn(Integer.valueOf(port));
		when(request.getContextPath()).thenReturn("/ctx");
		when(request.getPathInfo()).thenReturn(pathInfo);
		when(request.getQueryString()).thenReturn(query);
		HttpServletResponse response = mock(HttpServletResponse.class);
		StringWriter body = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(body));

		servlet.doGet(request, response);

		verify(response).setContentType("text/html");
		return body.toString();
	}

	@Test
	public void redirectsToTheSolutionWithTheFragmentAsQuery() throws Exception
	{
		String html = get("https", 443, "/oauth/solutions/mysol/m/dl", null);

		assertTrue(html.contains("var url = 'https:\\/\\/example.com\\/ctx\\/solutions\\/mysol\\/m\\/dl?'+window.location.hash.substring(1);"), html);
		assertTrue(html.contains("window.onload = redirectToSolution;"), html);
	}

	@Test
	public void keepsNonDefaultPortsAndTheExistingQuery() throws Exception
	{
		String html = get("http", 8080, "/oauth/solutions/mysol/m/dl", "a=1&b=2");

		assertTrue(html.contains("'http:\\/\\/example.com:8080\\/ctx\\/solutions\\/mysol\\/m\\/dl?a=1&b=2&'"), html);
	}

	@Test
	public void defaultHttpPortIsLeftOut() throws Exception
	{
		assertTrue(get("http", 80, "/oauth/x", null).contains("'http:\\/\\/example.com\\/ctx\\/x?'"));
	}

	@Test
	public void theUrlIsEscapedForTheScript() throws Exception
	{
		String html = get("https", 443, "/oauth/x", "p=';alert(1)//");

		// the quote is escaped, so it cannot end the script string
		assertTrue(html.contains("p=\\';alert(1)"), html);
		assertFalse(html.replace("\\'", "").contains("';alert(1)"), html);
	}

	@Test
	public void aRequestWithoutPathInfoFails()
	{
		// current behaviour (bug): getPathInfo() is used before its null check
		assertThrows(NullPointerException.class, () -> get("https", 443, null, null));
	}
}
