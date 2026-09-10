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
package com.servoy.extensions.plugins.rest_ws.servlets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import org.junit.Test;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

/**
 * Reproduces the Servoy 2026.3.2 regression where every POST handled by the rest_ws plugin failed with
 * {@code NoSuchMethodError: JakartaServletFileUpload.isMultipartRequestMethod(String)}.
 *
 * The servlet's {@code getContents} calls a multipart detector for every POST, before Content-Type is
 * even inspected. When that detector was {@code JakartaServletFileUpload.isMultipartContent}, a version
 * mismatch between the plugin jar and the platform-provided commons-fileupload2 jar broke every POST
 * (JSON/XML/text included), not just file uploads.
 *
 * These tests assert on {@link RestWSServlet#isMultipartContent(HttpServletRequest)}, the plugin's own
 * header-based detector, so they pin down the intended behavior: non-multipart POSTs must NOT be routed
 * into the file-upload path, and real multipart POSTs must still be recognized.
 */
public class RestWSServletMultipartDetectionTest
{
	@Test
	public void jsonPostIsNotTreatedAsMultipart()
	{
		assertFalse("application/json POST must not be routed into the file-upload path",
			RestWSServlet.isMultipartContent(request("POST", "application/json")));
	}

	@Test
	public void xmlPostIsNotTreatedAsMultipart()
	{
		assertFalse("application/xml POST must not be routed into the file-upload path",
			RestWSServlet.isMultipartContent(request("POST", "application/xml")));
		assertFalse("text/xml POST must not be routed into the file-upload path",
			RestWSServlet.isMultipartContent(request("POST", "text/xml")));
	}

	@Test
	public void textPostIsNotTreatedAsMultipart()
	{
		assertFalse("text/plain POST must not be routed into the file-upload path",
			RestWSServlet.isMultipartContent(request("POST", "text/plain")));
	}

	@Test
	public void formPostIsNotTreatedAsMultipart()
	{
		assertFalse("urlencoded form POST must not be routed into the file-upload path",
			RestWSServlet.isMultipartContent(request("POST", "application/x-www-form-urlencoded")));
	}

	@Test
	public void postWithoutContentTypeIsNotTreatedAsMultipart()
	{
		assertFalse("POST without Content-Type must not be routed into the file-upload path",
			RestWSServlet.isMultipartContent(request("POST", null)));
	}

	@Test
	public void multipartPostIsStillRecognized()
	{
		assertTrue("a real multipart/form-data upload must still be recognized",
			RestWSServlet.isMultipartContent(request("POST", "multipart/form-data; boundary=----abc123")));
		assertTrue("multipart Content-Type detection must be case-insensitive",
			RestWSServlet.isMultipartContent(request("POST", "Multipart/Form-Data; boundary=xyz")));
	}

	@Test
	public void nonPostMethodIsNeverMultipart()
	{
		assertFalse("a GET is never a multipart upload even with a multipart Content-Type",
			RestWSServlet.isMultipartContent(request("GET", "multipart/form-data; boundary=x")));
	}

	/**
	 * Guards against reintroducing the fragile dependency: the detector must remain header-based and must
	 * not require the commons-fileupload2 internal {@code isMultipartRequestMethod(String)} that is absent
	 * in some milestones shipped by the platform.
	 */
	@Test
	public void detectorDoesNotDependOnCommonsInternalMethod()
	{
		try
		{
			RestWSServlet.isMultipartContent(request("POST", "application/json"));
		}
		catch (NoSuchMethodError e)
		{
			throw new AssertionError(
				"multipart detection must not depend on a commons-fileupload2 internal that can be missing at runtime",
				e);
		}
	}

	private static HttpServletRequest request(String method, String contentType)
	{
		return new FakeHttpServletRequest(method, contentType);
	}

	/**
	 * Minimal hand-written {@link HttpServletRequest} that only answers the method and Content-Type used
	 * by {@link RestWSServlet#isMultipartContent}. Everything else throws, so any accidental extra
	 * dependency in the detector is caught by the test rather than silently mocked away.
	 */
	private static final class FakeHttpServletRequest implements HttpServletRequest
	{
		private final String method;
		private final String contentType;

		FakeHttpServletRequest(String method, String contentType)
		{
			this.method = method;
			this.contentType = contentType;
		}

		@Override
		public String getMethod()
		{
			return method;
		}

		@Override
		public String getContentType()
		{
			return contentType;
		}

		private static UnsupportedOperationException notNeeded()
		{
			return new UnsupportedOperationException("not needed for multipart detection");
		}

		@Override
		public Object getAttribute(String name)
		{
			throw notNeeded();
		}

		@Override
		public Enumeration<String> getAttributeNames()
		{
			throw notNeeded();
		}

		@Override
		public String getCharacterEncoding()
		{
			throw notNeeded();
		}

		@Override
		public void setCharacterEncoding(String env)
		{
			throw notNeeded();
		}

		@Override
		public int getContentLength()
		{
			throw notNeeded();
		}

		@Override
		public long getContentLengthLong()
		{
			throw notNeeded();
		}

		@Override
		public ServletInputStream getInputStream()
		{
			throw notNeeded();
		}

		@Override
		public String getParameter(String name)
		{
			throw notNeeded();
		}

		@Override
		public Enumeration<String> getParameterNames()
		{
			throw notNeeded();
		}

		@Override
		public String[] getParameterValues(String name)
		{
			throw notNeeded();
		}

		@Override
		public Map<String, String[]> getParameterMap()
		{
			throw notNeeded();
		}

		@Override
		public String getProtocol()
		{
			throw notNeeded();
		}

		@Override
		public String getScheme()
		{
			throw notNeeded();
		}

		@Override
		public String getServerName()
		{
			throw notNeeded();
		}

		@Override
		public int getServerPort()
		{
			throw notNeeded();
		}

		@Override
		public BufferedReader getReader()
		{
			throw notNeeded();
		}

		@Override
		public String getRemoteAddr()
		{
			throw notNeeded();
		}

		@Override
		public String getRemoteHost()
		{
			throw notNeeded();
		}

		@Override
		public void setAttribute(String name, Object o)
		{
			throw notNeeded();
		}

		@Override
		public void removeAttribute(String name)
		{
			throw notNeeded();
		}

		@Override
		public java.util.Locale getLocale()
		{
			throw notNeeded();
		}

		@Override
		public Enumeration<java.util.Locale> getLocales()
		{
			throw notNeeded();
		}

		@Override
		public boolean isSecure()
		{
			throw notNeeded();
		}

		@Override
		public RequestDispatcher getRequestDispatcher(String path)
		{
			throw notNeeded();
		}

		@Override
		public int getRemotePort()
		{
			throw notNeeded();
		}

		@Override
		public String getLocalName()
		{
			throw notNeeded();
		}

		@Override
		public String getLocalAddr()
		{
			throw notNeeded();
		}

		@Override
		public int getLocalPort()
		{
			throw notNeeded();
		}

		@Override
		public ServletContext getServletContext()
		{
			throw notNeeded();
		}

		@Override
		public AsyncContext startAsync()
		{
			throw notNeeded();
		}

		@Override
		public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
		{
			throw notNeeded();
		}

		@Override
		public boolean isAsyncStarted()
		{
			throw notNeeded();
		}

		@Override
		public boolean isAsyncSupported()
		{
			throw notNeeded();
		}

		@Override
		public AsyncContext getAsyncContext()
		{
			throw notNeeded();
		}

		@Override
		public DispatcherType getDispatcherType()
		{
			throw notNeeded();
		}

		@Override
		public String getRequestId()
		{
			throw notNeeded();
		}

		@Override
		public String getProtocolRequestId()
		{
			throw notNeeded();
		}

		@Override
		public jakarta.servlet.ServletConnection getServletConnection()
		{
			throw notNeeded();
		}

		@Override
		public String getAuthType()
		{
			throw notNeeded();
		}

		@Override
		public Cookie[] getCookies()
		{
			throw notNeeded();
		}

		@Override
		public long getDateHeader(String name)
		{
			throw notNeeded();
		}

		@Override
		public String getHeader(String name)
		{
			return null;
		}

		@Override
		public Enumeration<String> getHeaders(String name)
		{
			return Collections.emptyEnumeration();
		}

		@Override
		public Enumeration<String> getHeaderNames()
		{
			return Collections.emptyEnumeration();
		}

		@Override
		public int getIntHeader(String name)
		{
			throw notNeeded();
		}

		@Override
		public String getPathInfo()
		{
			throw notNeeded();
		}

		@Override
		public String getPathTranslated()
		{
			throw notNeeded();
		}

		@Override
		public String getContextPath()
		{
			throw notNeeded();
		}

		@Override
		public String getQueryString()
		{
			throw notNeeded();
		}

		@Override
		public String getRemoteUser()
		{
			throw notNeeded();
		}

		@Override
		public boolean isUserInRole(String role)
		{
			throw notNeeded();
		}

		@Override
		public Principal getUserPrincipal()
		{
			throw notNeeded();
		}

		@Override
		public String getRequestedSessionId()
		{
			throw notNeeded();
		}

		@Override
		public String getRequestURI()
		{
			throw notNeeded();
		}

		@Override
		public StringBuffer getRequestURL()
		{
			throw notNeeded();
		}

		@Override
		public String getServletPath()
		{
			throw notNeeded();
		}

		@Override
		public HttpSession getSession(boolean create)
		{
			throw notNeeded();
		}

		@Override
		public HttpSession getSession()
		{
			throw notNeeded();
		}

		@Override
		public String changeSessionId()
		{
			throw notNeeded();
		}

		@Override
		public boolean isRequestedSessionIdValid()
		{
			throw notNeeded();
		}

		@Override
		public boolean isRequestedSessionIdFromCookie()
		{
			throw notNeeded();
		}

		@Override
		public boolean isRequestedSessionIdFromURL()
		{
			throw notNeeded();
		}

		@Override
		public boolean authenticate(HttpServletResponse response)
		{
			throw notNeeded();
		}

		@Override
		public void login(String username, String password)
		{
			throw notNeeded();
		}

		@Override
		public void logout()
		{
			throw notNeeded();
		}

		@Override
		public java.util.Collection<Part> getParts()
		{
			throw notNeeded();
		}

		@Override
		public Part getPart(String name)
		{
			throw notNeeded();
		}

		@Override
		public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass)
		{
			throw notNeeded();
		}
	}
}
