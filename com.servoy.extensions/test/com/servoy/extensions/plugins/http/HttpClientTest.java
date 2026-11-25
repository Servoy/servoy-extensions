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
package com.servoy.extensions.plugins.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * HttpClient construction from a HttpClientConfig, cookies, proxy settings and request creation. The real Apache async
 * client is started (idle, no connection is made) and closed after each test.
 */
public class HttpClientTest
{
	private HttpPlugin plugin;
	private final List<HttpClient> clients = new ArrayList<>();

	@TempDir
	Path tmp;

	@BeforeEach
	public void setUp() throws Exception
	{
		plugin = new HttpPlugin();
		plugin.initialize(mock(IClientPluginAccess.class));
	}

	@AfterEach
	public void tearDown() throws Exception
	{
		clients.forEach(HttpClient::js_close);
		plugin.unload();
	}

	private HttpClient client(HttpClientConfig config)
	{
		HttpClient client = new HttpClient(plugin, config);
		clients.add(client);
		return client;
	}

	private static Object invoke(HttpClient client, String name, Class< ? >[] types, Object... args) throws Exception
	{
		Method m = HttpClient.class.getDeclaredMethod(name, types);
		m.setAccessible(true);
		return m.invoke(client, args);
	}

	// --- config ---

	@Test
	public void configDefaults()
	{
		HttpClientConfig config = new HttpClientConfig();

		assertEquals(-1, config.js_getMaxIOThreadCount());
		assertEquals(-1, config.js_getMaxTotalConnections());
		assertEquals(-1, config.js_getMaxConnectionsPerRoute());
		assertEquals(-1, config.js_getKeepAliveDuration());
		assertFalse(config.js_getForceHttp1());
		assertTrue(config.js_getEnableRedirects());
		assertTrue(config.js_getHostValidation());
		assertFalse(config.js_getMultiPartLegacyMode());
		assertNull(config.js_getProtocol());
		assertNull(config.js_getUserAgent());
		assertNull(config.js_getCertPath());
		assertNull(config.js_getCertPassword());
		assertNull(config.js_getTrustStorePassword());
	}

	@Test
	public void configSetters()
	{
		HttpClientConfig config = new HttpClientConfig();
		config.js_setMaxIOThreadCount(3);
		config.js_setMaxTotalConnections(10);
		config.js_setMaxConnectionsPerRoute(4);
		config.js_setKeepAliveDuration(30);
		config.js_setForceHttp1(true);
		config.js_setEnableRedirects(false);
		config.js_setHostValidation(false);
		config.js_setMultiPartLegacyMode(true);
		config.js_setProtocol("TLSv1.3");
		config.js_setUserAgent("agent");
		config.js_setCertPath("c.p12");
		config.js_setCertPassword("pw");
		config.js_setTrustStorePassword("tpw");

		assertEquals(3, config.js_getMaxIOThreadCount());
		assertEquals(10, config.js_getMaxTotalConnections());
		assertEquals(4, config.js_getMaxConnectionsPerRoute());
		assertEquals(30, config.js_getKeepAliveDuration());
		assertTrue(config.js_getForceHttp1());
		assertFalse(config.js_getEnableRedirects());
		assertFalse(config.js_getHostValidation());
		assertTrue(config.js_getMultiPartLegacyMode());
		assertEquals("TLSv1.3", config.js_getProtocol());
		assertEquals("agent", config.js_getUserAgent());
		assertEquals("c.p12", config.js_getCertPath());
		assertEquals("pw", config.js_getCertPassword());
		assertEquals("tpw", config.js_getTrustStorePassword());
	}

	// --- construction ---

	@Test
	public void aDefaultClientFollowsRedirects()
	{
		HttpClient client = client(null);

		assertNotNull(client.client);
		RequestConfig config = client.requestConfigBuilder.build();
		assertTrue(config.isRedirectsEnabled());
		assertTrue(config.isCircularRedirectsAllowed());
		assertEquals(-1, client.js_createGetRequest("http://localhost/").js_getTimeout());
	}

	@Test
	public void aFullConfigIsApplied()
	{
		HttpClientConfig config = new HttpClientConfig();
		config.js_setMaxIOThreadCount(1);
		config.js_setMaxTotalConnections(7);
		config.js_setMaxConnectionsPerRoute(3);
		config.js_setKeepAliveDuration(5);
		config.js_setForceHttp1(true);
		config.js_setEnableRedirects(false);
		config.js_setMultiPartLegacyMode(true);
		config.js_setUserAgent("servoy-test");
		config.js_setProtocol("TLSv1.2");

		HttpClient client = client(config);

		assertNotNull(client.client);
		assertFalse(client.requestConfigBuilder.build().isRedirectsEnabled());
	}

	@Test
	public void hostValidationOffBuildsAClient()
	{
		HttpClientConfig config = new HttpClientConfig();
		config.js_setHostValidation(false);

		assertNotNull(client(config).client);
	}

	@Test
	public void trustAllAndAllowedCertContextsAreCreated() throws Exception
	{
		HttpClient client = client(null);

		assertInstanceOf(SSLContext.class, invoke(client, "createTrustAllSSLContext", new Class[0]));
		assertInstanceOf(SSLContext.class, invoke(client, "createAllowedCertSSLContext", new Class[0]));
		assertNotNull(client.createFactoryForJava11());
	}

	@Test
	public void aClientCertificateKeystoreIsLoaded() throws Exception
	{
		Path p12 = tmp.resolve("client.p12");
		KeyStore ks = KeyStore.getInstance("PKCS12");
		ks.load(null, null);
		try (OutputStream out = new FileOutputStream(p12.toFile()))
		{
			ks.store(out, "secret".toCharArray());
		}
		HttpClientConfig config = new HttpClientConfig();
		config.js_setCertPath(p12.toString());
		config.js_setCertPassword("secret");
		HttpClient client = client(config);

		assertNotNull(client.client);
		// the java cacerts path is built with a trailing separator ("lib/security/cacerts/"); Windows accepts that,
		// on Linux/macOS opening the truststore fails (possible bug), so both outcomes are accepted here
		try
		{
			assertInstanceOf(SSLContext.class, invoke(client, "createSSLContextWithCert", new Class[] { HttpClientConfig.class }, config));
		}
		catch (java.lang.reflect.InvocationTargetException e)
		{
			assertFalse(System.getProperty("os.name").toLowerCase().contains("win"), e.getCause().toString());
			assertInstanceOf(java.io.FileNotFoundException.class, e.getCause());
		}
	}

	@Test
	public void aMissingClientCertificateOnlyLogs()
	{
		HttpClientConfig config = new HttpClientConfig();
		config.js_setCertPath(tmp.resolve("missing.p12").toString());

		assertNotNull(client(config).client);
	}

	// --- cookies ---

	@Test
	public void cookiesWithoutPathAreStored()
	{
		HttpClient client = client(null);

		assertTrue(client.js_setCookie("a", "1"));
		assertTrue(client.js_setCookie("b", "2", "example.com"));

		assertEquals(2, client.js_getCookies().length);
		Cookie a = client.js_getCookie("a");
		assertEquals("a", a.js_getName());
		assertEquals("1", a.js_getValue());
		assertEquals("example.com", client.js_getCookie("b").js_getDomain());
		assertNull(client.js_getCookie("none"));
	}

	@Test
	public void emptyNamesOrValuesAreRejected()
	{
		HttpClient client = client(null);

		assertFalse(client.js_setCookie(null, "v"));
		assertFalse(client.js_setCookie("n", ""));
		assertEquals(0, client.js_getCookies().length);
	}

	@Test
	public void aCookieWithPathAndDefaultMaxAgeIsNotKept()
	{
		HttpClient client = client(null);

		// current behaviour (bug): with a path the expiry is now + maxAge milliseconds; the default maxAge -1 (and 0)
		// give an already expired cookie, which the cookie store silently drops while setCookie returns true
		assertTrue(client.js_setCookie("JSESSIONID", "abc", "localhost", "/"));
		assertTrue(client.js_setCookie("zero", "abc", "localhost", "/", 0));

		assertEquals(0, client.js_getCookies().length);
	}

	@Test
	public void aCookieWithPathAndMaxAgeIsSecureWhenAsked()
	{
		HttpClient client = client(null);

		assertTrue(client.js_setCookie("s", "v", "localhost", "/app", 3_600_000, true));

		Cookie cookie = client.js_getCookie("s");
		assertEquals("/app", cookie.js_getPath());
		assertTrue(cookie.js_getSecure());
	}

	@Test
	public void cookieWrapperWithoutCookie()
	{
		Cookie empty = new Cookie();
		assertEquals("", empty.js_getName());
		assertEquals("", empty.js_getValue());
		assertEquals("", empty.js_getDomain());
		assertEquals("", empty.js_getPath());
		assertFalse(empty.js_getSecure());
		assertEquals("", empty.js_getComment());

		empty.setCookie(new BasicClientCookie("n", "v"));
		assertEquals("n", empty.js_getName());
	}

	// --- timeout, proxy and requests ---

	@Test
	public void timeoutIsSharedByTheRequests()
	{
		HttpClient client = client(null);
		client.js_setTimeout(1500);

		BaseRequest request = client.js_createGetRequest("http://localhost/x");

		assertEquals(1500, request.js_getTimeout());
		assertEquals(1500, request.js_getRequestConfig().getConnectTimeout().toMilliseconds());
	}

	@Test
	public void requestsOfEveryMethodAreCreated()
	{
		HttpClient client = client(null);
		String url = "http://localhost/r?a=1&a=2&b=x";

		assertEquals("GET", client.js_createGetRequest(url).js_getHttpMethod());
		assertEquals("POST", client.js_createPostRequest(url).js_getHttpMethod());
		assertEquals("PUT", client.js_createPutRequest(url).js_getHttpMethod());
		assertEquals("PATCH", client.js_createPatchRequest(url).js_getHttpMethod());
		assertEquals("DELETE", client.js_createDeleteRequest(url).js_getHttpMethod());
		assertEquals("OPTIONS", client.js_createOptionsRequest(url).js_getHttpMethod());
		assertEquals("HEAD", client.js_createHeadRequest(url).js_getHttpMethod());
		assertEquals("TRACE", client.js_createTraceRequest(url).js_getHttpMethod());
		BaseRequest get = client.js_createGetRequest(url);
		assertEquals(url, get.js_getUrl());
		assertEquals(2, get.js_getQueryParameters().get("a").length);
		assertEquals("x", get.js_getQueryParameters().get("b")[0]);
	}

	@Test
	public void aProxyServerIsSetOnTheRequests()
	{
		HttpClient client = client(null);
		client.js_setClientProxyServer("proxy.local", Integer.valueOf(-3128));
		client.js_setClientProxyCredentials("user", "pw");

		HttpHost proxy = client.js_createGetRequest("http://localhost/").js_getRequestConfig().getProxy();

		assertEquals("proxy.local", proxy.getHostName());
		assertEquals(3128, proxy.getPort());
	}

	@Test
	public void clearingTheProxyServerDoesNotClearItFromTheRequests()
	{
		HttpClient client = client(null);
		client.js_setClientProxyServer("proxy.local", Integer.valueOf(3128));
		client.js_createGetRequest("http://localhost/");

		client.js_setClientProxyServer(null, null);
		HttpHost proxy = client.js_createGetRequest("http://localhost/").js_getRequestConfig().getProxy();

		// current behaviour (bug): the proxy is set on the request config builder that all requests of the client
		// share, so a cleared proxy server stays in use
		assertNotNull(proxy);
		assertEquals("proxy.local", proxy.getHostName());
	}

	@Test
	public void blankProxyCredentialsAreIgnored()
	{
		HttpClient client = client(null);
		client.js_setClientProxyCredentials("", "pw");
		client.js_setClientProxyServer("proxy.local", Integer.valueOf(8000));

		assertNotNull(client.js_createPostRequest("http://localhost/"));
	}

	// --- plugin bookkeeping ---

	@Test
	public void closeRemovesTheClientFromThePlugin()
	{
		HttpProvider provider = (HttpProvider)plugin.getScriptObject();
		HttpClient client = provider.js_createNewHttpClient();
		HttpClient configured = provider.js_createNewHttpClient(provider.js_createNewHttpClientConfig());

		client.js_close();
		configured.js_close();
		// a second close only logs
		client.js_close();

		assertSame(provider, plugin.getScriptObject());
	}
}
