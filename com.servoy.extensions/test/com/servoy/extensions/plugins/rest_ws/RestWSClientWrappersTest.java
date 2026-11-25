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
package com.servoy.extensions.plugins.rest_ws;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.fileupload2.core.DiskFileItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.j2db.plugins.IClientPlugin;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The rest_ws client plugin and its scripting wrappers around the running servlet request and response (mocked).
 */
public class RestWSClientWrappersTest
{
	private RestWSClientPlugin plugin;
	private RestWSClientProvider provider;
	private HttpServletRequest request;
	private HttpServletResponse response;

	@TempDir
	Path tmp;

	@BeforeEach
	public void setUp() throws Exception
	{
		plugin = new RestWSClientPlugin();
		plugin.load();
		plugin.initialize(null);
		provider = (RestWSClientProvider)plugin.getScriptObject();
		request = mock(HttpServletRequest.class);
		response = mock(HttpServletResponse.class);
	}

	private DiskFileItem item(String field, String name, String contentType, String content) throws IOException
	{
		DiskFileItem item = DiskFileItem.builder().setFieldName(field).setFileName(name).setContentType(contentType).setPath(tmp).get();
		try (OutputStream out = item.getOutputStream())
		{
			out.write(content.getBytes(StandardCharsets.UTF_8));
		}
		return item;
	}

	// --- plugin and provider ---

	@Test
	public void pluginDescription() throws Exception
	{
		assertEquals("rest_ws", plugin.getName());
		assertEquals("Servoy REST-WS client plugin", plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertNull(plugin.getPreferencePanels());
		assertNotNull(plugin.getImage());
		assertNotNull(plugin.getIconUrl());
		assertSame(provider, plugin.getScriptObject());
		plugin.propertyChange(null);

		plugin.unload();

		assertNotSame(provider, plugin.getScriptObject());
	}

	private static void assertNotSame(Object a, Object b)
	{
		assertFalse(a == b);
	}

	@Test
	public void withoutRunningRequestThereIsNoRequestOrResponse()
	{
		assertFalse(provider.isRunningRequest());
		assertEquals("Not running request", assertThrows(IllegalStateException.class, provider::getRequest).getMessage());
		assertThrows(IllegalStateException.class, provider::getResponse);
	}

	@Test
	public void theRunningRequestIsExposed()
	{
		plugin.setRequestResponseContents(request, response, List.of());

		assertTrue(provider.isRunningRequest());
		assertNotNull(provider.getRequest());
		assertNotNull(provider.getResponse());
		assertTrue(plugin.getContents().isEmpty());
	}

	@Test
	public void userPropertiesHeadersAreOnlySetWhenAsked()
	{
		assertNull(plugin.isSendUserPropertiesHeaders());

		provider.sendResponseUserPropertiesHeaders(false);

		assertEquals(Boolean.FALSE, plugin.isSendUserPropertiesHeaders());
	}

	// --- cookie ---

	@Test
	public void cookieProperties()
	{
		WsCookie cookie = provider.createCookie("sid", "abc");
		cookie.setComment("c");
		cookie.setDomain("example.com");
		cookie.setMaxAge(60);
		cookie.setPath("/app");
		cookie.setSecure(true);
		cookie.setValue("def");
		cookie.setVersion(1);
		cookie.setHttpOnly(true);

		assertEquals("sid", cookie.getName());
		assertEquals("def", cookie.getValue());
		assertEquals("example.com", cookie.getDomain());
		assertEquals(60, cookie.getMaxAge());
		assertEquals("/app", cookie.getPath());
		assertTrue(cookie.getSecure());
		assertTrue(cookie.isHttpOnly());
		// jakarta servlet 6 ignores comment and version
		assertNull(cookie.getComment());
		assertEquals(0, cookie.getVersion());
		assertNotNull(cookie.toString());
	}

	@Test
	public void cookieEquality()
	{
		Cookie servletCookie = new Cookie("a", "1");
		WsCookie cookie = new WsCookie(servletCookie);

		assertEquals(cookie, new WsCookie(servletCookie));
		assertEquals(cookie.hashCode(), new WsCookie(servletCookie).hashCode());
		assertNotEquals(cookie, null);
		assertNotEquals(cookie, "a");
		assertSame(servletCookie, cookie.getCookie());
		assertThrows(NullPointerException.class, () -> new WsCookie(null));
	}

	// --- request ---

	@Test
	public void requestDelegatesToTheServletRequest() throws Exception
	{
		plugin.setRequestResponseContents(request, response, null);
		when(request.getHeader("X-A")).thenReturn("a");
		when(request.getCharacterEncoding()).thenReturn("UTF-8");
		when(Long.valueOf(request.getContentLengthLong())).thenReturn(Long.valueOf(42));
		when(request.getContentType()).thenReturn("application/json");
		when(request.getParameter("p")).thenReturn("1");
		when(request.getParameterNames()).thenReturn(Collections.enumeration(List.of("p", "q")));
		when(request.getParameterValues("p")).thenReturn(new String[] { "1", "2" });
		when(request.getParameterMap()).thenReturn(Map.of("p", new String[] { "1" }));
		when(request.getProtocol()).thenReturn("HTTP/1.1");
		when(request.getScheme()).thenReturn("https");
		when(request.getServerName()).thenReturn("host");
		when(Integer.valueOf(request.getServerPort())).thenReturn(Integer.valueOf(8443));
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		when(request.getRemoteHost()).thenReturn("client");
		when(request.getLocale()).thenReturn(Locale.forLanguageTag("nl-NL"));
		when(request.getLocales()).thenReturn(Collections.enumeration(List.of(Locale.forLanguageTag("nl-NL"), Locale.ENGLISH)));
		when(Boolean.valueOf(request.isSecure())).thenReturn(Boolean.TRUE);
		when(Integer.valueOf(request.getRemotePort())).thenReturn(Integer.valueOf(5555));
		when(request.getLocalName()).thenReturn("local");
		when(request.getLocalAddr()).thenReturn("127.0.0.1");
		when(Integer.valueOf(request.getLocalPort())).thenReturn(Integer.valueOf(8080));
		when(Long.valueOf(request.getDateHeader("If-Modified-Since"))).thenReturn(Long.valueOf(1000));
		when(request.getHeaders("X-A")).thenReturn(new Vector<>(List.of("a", "b")).elements());
		when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of("X-A")));
		when(Integer.valueOf(request.getIntHeader("X-N"))).thenReturn(Integer.valueOf(7));
		when(request.getMethod()).thenReturn("POST");
		when(request.getPathInfo()).thenReturn("/sol/form/1");
		when(request.getPathTranslated()).thenReturn("/real/sol");
		when(request.getContextPath()).thenReturn("/ctx");
		when(request.getQueryString()).thenReturn("a=1");
		when(request.getRequestURI()).thenReturn("/ctx/servoy-service/rest_ws/sol/form/1");
		when(request.getRequestURL()).thenReturn(new StringBuffer("https://host/ctx/servoy-service/rest_ws/sol/form/1"));
		when(request.getServletPath()).thenReturn("/servoy-service");

		WsRequest ws = provider.getRequest();
		ws.setCharacterEncoding("ISO-8859-1");

		verify(request).setCharacterEncoding("ISO-8859-1");
		assertEquals("a", ws.getHeader("X-A"));
		assertEquals("UTF-8", ws.getCharacterEncoding());
		assertEquals(42, ws.getContentLength());
		assertEquals("application/json", ws.getContentType());
		assertEquals("1", ws.getParameter("p"));
		assertArrayEquals(new String[] { "p", "q" }, ws.getParameterNames());
		assertArrayEquals(new String[] { "1", "2" }, ws.getParameterValues("p"));
		assertArrayEquals(new String[] { "1" }, ws.getParameterMap().get("p"));
		assertEquals("HTTP/1.1", ws.getProtocol());
		assertEquals("https", ws.getScheme());
		assertEquals("host", ws.getServerName());
		assertEquals(8443, ws.getServerPort());
		assertEquals("10.0.0.1", ws.getRemoteAddr());
		assertEquals("client", ws.getRemoteHost());
		assertEquals("nl-NL", ws.getLocaleLanguageTag());
		assertArrayEquals(new String[] { "nl-NL", "en" }, ws.getLocalesLanguageTags());
		assertTrue(ws.isSecure());
		assertNull(ws.getRealPath("/x"));
		assertEquals(5555, ws.getRemotePort());
		assertEquals("local", ws.getLocalName());
		assertEquals("127.0.0.1", ws.getLocalAddr());
		assertEquals(8080, ws.getLocalPort());
		assertEquals(1000, ws.getDateHeader("If-Modified-Since"));
		assertArrayEquals(new String[] { "a", "b" }, ws.getHeaders("X-A"));
		assertArrayEquals(new String[] { "X-A" }, ws.getHeaderNames());
		assertEquals(7, ws.getIntHeader("X-N"));
		assertEquals("POST", ws.getMethod());
		assertEquals("/sol/form/1", ws.getPathInfo());
		assertEquals("/real/sol", ws.getPathTranslated());
		assertEquals("/ctx", ws.getContextPath());
		assertEquals("a=1", ws.getQueryString());
		assertEquals("/ctx/servoy-service/rest_ws/sol/form/1", ws.getRequestURI());
		assertEquals("https://host/ctx/servoy-service/rest_ws/sol/form/1", ws.getRequestURL());
		assertEquals("/servoy-service", ws.getServletPath());
	}

	@Test
	public void missingHeaderEnumerationsAreNull()
	{
		plugin.setRequestResponseContents(request, response, null);
		WsRequest ws = provider.getRequest();

		assertNull(ws.getHeaders("X"));
		assertNull(ws.getHeaderNames());
	}

	@Test
	public void requestCookies()
	{
		plugin.setRequestResponseContents(request, response, null);
		WsRequest ws = provider.getRequest();
		assertEquals(0, ws.getCookies().length);
		when(request.getCookies()).thenReturn(new Cookie[0]);
		assertEquals(0, ws.getCookies().length);

		when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("a", "1"), new Cookie("b", "2") });

		WsCookie[] cookies = ws.getCookies();
		assertEquals(2, cookies.length);
		assertEquals("b", cookies[1].getName());
	}

	@Test
	public void multipartContents() throws Exception
	{
		DiskFileItem file = item("upload", "a.txt", "text/plain", "h\u00e9llo");
		plugin.setRequestResponseContents(request, response, List.of(file));
		WsRequest ws = provider.getRequest();

		WsContents[] contents = ws.getContents();

		assertEquals(1, contents.length);
		WsContents content = contents[0];
		assertEquals("a.txt", content.getName());
		assertEquals("upload", content.getFieldName());
		assertEquals("text/plain", content.getContentType());
		assertEquals(6, content.getSize());
		assertArrayEquals("h\u00e9llo".getBytes(StandardCharsets.UTF_8), content.getBytes());
		assertEquals("h\u00e9llo", content.getString("UTF-8"));
		assertEquals(content, new WsContents(file));
		assertNotEquals(content, null);
		assertThrows(NullPointerException.class, () -> new WsContents(null));
	}

	@Test
	public void noContents()
	{
		plugin.setRequestResponseContents(request, response, null);
		assertEquals(0, provider.getRequest().getContents().length);
		plugin.setRequestResponseContents(request, response, List.of());
		assertEquals(0, provider.getRequest().getContents().length);
	}

	// --- response ---

	@Test
	public void responseDelegatesToTheServletResponse() throws Exception
	{
		plugin.setRequestResponseContents(request, response, null);
		when(Integer.valueOf(response.getStatus())).thenReturn(Integer.valueOf(201));
		when(response.getCharacterEncoding()).thenReturn("UTF-8");
		when(response.getContentType()).thenReturn("application/json");
		when(response.getLocale()).thenReturn(Locale.GERMANY);
		when(Boolean.valueOf(response.containsHeader("X-A"))).thenReturn(Boolean.TRUE);
		when(response.getHeader("X-A")).thenReturn("a");
		when(response.getHeaders("X-A")).thenReturn(List.of("a", "b"));
		when(response.getHeaderNames()).thenReturn(List.of("X-A"));
		WsResponse ws = provider.getResponse();
		WsCookie cookie = provider.createCookie("c", "v");

		ws.addCookie(cookie);
		ws.setStatus(201);
		ws.sendError(404);
		ws.sendError(500, "boom");
		ws.setCharacterEncoding("UTF-8");
		ws.setContentType("application/json");
		ws.setLocaleLanguageTag("de-DE");
		ws.setDateHeader("Date", 1);
		ws.addDateHeader("Date", 2);
		ws.setHeader("X-A", "a");
		ws.addHeader("X-A", "b");
		ws.setIntHeader("X-N", 1);
		ws.addIntHeader("X-N", 2);

		verify(response).addCookie(cookie.getCookie());
		verify(response).setStatus(201);
		verify(response).sendError(404);
		verify(response).sendError(500, "boom");
		verify(response).setCharacterEncoding("UTF-8");
		verify(response).setContentType("application/json");
		verify(response).setLocale(Locale.GERMANY);
		verify(response).setDateHeader("Date", 1);
		verify(response).addDateHeader("Date", 2);
		verify(response).setHeader("X-A", "a");
		verify(response).addHeader("X-A", "b");
		verify(response).setIntHeader("X-N", 1);
		verify(response).addIntHeader("X-N", 2);
		assertEquals(201, ws.getStatus());
		assertEquals("UTF-8", ws.getCharacterEncoding());
		assertEquals("application/json", ws.getContentType());
		assertEquals("de-DE", ws.getLocaleLanguageTag());
		assertTrue(ws.containsHeader("X-A"));
		assertEquals("a", ws.getHeader("X-A"));
		assertArrayEquals(new String[] { "a", "b" }, ws.getHeaders("X-A"));
		assertArrayEquals(new String[] { "X-A" }, ws.getHeaderNames());
	}
}
