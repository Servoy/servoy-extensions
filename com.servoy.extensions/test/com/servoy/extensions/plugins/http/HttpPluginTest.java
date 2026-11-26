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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.beans.PropertyChangeEvent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.util.Pair;

/**
 * HttpPlugin life cycle, the HttpProvider entry points that do not need a network (file: urls) and the certificate
 * bookkeeping.
 */
public class HttpPluginTest
{
	private static final String PEM = "MIIBRzCB7qADAgECAgkA2QvTDcHy6RYwCgYIKoZIzj0EAwMwFzEVMBMGA1UEAxMMdGVzdC5leGFtcGxlMCAXDTI2MTEyNTA4MjUwMVoYDzIxMjYxMTAxMDgyNTAxWjAXMRUwEwYDVQQDEwx0ZXN0LmV4YW1wbGUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASsP7noVDCkMx9eMYa/bY1oP/UDjK6uXoDuvTHzYuhGr7NzINVGgRRDa3FM40IsTAEL0Y06acdBNEzJSh7HItr9oyEwHzAdBgNVHQ4EFgQUu1eZLxX+ly0OqQX7tsXhDu5AMWcwCgYIKoZIzj0EAwMDSAAwRQIgTKtUAoAiPYNcVWJL2L9++aUjRC+bQ/XfKIQVRi22UJECIQDgftL1SeQDPlhlehSo/1QmUCUe17eP+xV0aYGlecidLw==";

	private HttpPlugin plugin;
	private HttpProvider provider;

	@TempDir
	Path tmp;

	@BeforeEach
	public void setUp() throws Exception
	{
		plugin = new HttpPlugin();
		plugin.load();
		plugin.initialize(mock(IClientPluginAccess.class));
		provider = (HttpProvider)plugin.getScriptObject();
	}

	@AfterEach
	public void tearDown() throws Exception
	{
		plugin.unload();
	}

	private static X509Certificate certificate() throws Exception
	{
		return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(PEM)));
	}

	// --- plugin ---

	@Test
	public void pluginDescription()
	{
		assertEquals("http", plugin.getName());
		assertEquals("HTTP Plugin", plugin.getProperties().get(com.servoy.j2db.plugins.IClientPlugin.DISPLAY_NAME));
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertNotNull(plugin.getExecutor());
		assertNotNull(plugin.getJSONConverter());
		assertSame(plugin.getJSONConverter(), plugin.getJSONConverter());
		assertSame(provider, plugin.getScriptObject());
	}

	@Test
	public void closingTheSolutionClosesTheOpenClients()
	{
		HttpClient client = provider.js_createNewHttpClient();

		plugin.propertyChange(new PropertyChangeEvent(this, "solution", "old", null));
		plugin.propertyChange(new PropertyChangeEvent(this, "solution", null, "new"));
		plugin.propertyChange(new PropertyChangeEvent(this, "other", "old", null));

		assertFalse(client.client.getStatus() == org.apache.hc.core5.reactor.IOReactorStatus.ACTIVE);
	}

	@Test
	public void unloadClosesTheClients() throws Exception
	{
		HttpClient client = provider.js_createNewHttpClient();

		plugin.unload();

		assertFalse(client.client.getStatus() == org.apache.hc.core5.reactor.IOReactorStatus.ACTIVE);
		assertNull(plugin.getClientPluginAccess());
		plugin = new HttpPlugin();
	}

	// --- provider ---

	@Test
	public void returnedTypes()
	{
		List<Class< ? >> types = List.of(provider.getAllReturnedTypes());

		assertTrue(types.contains(HttpClient.class) && types.contains(HttpClientConfig.class) && types.contains(Response.class), types.toString());
		assertEquals(14, types.size());
		assertNotNull(new HttpProvider().js_createNewHttpClientConfig());
	}

	@Test
	public void pageDataIsReadFromAFileUrl() throws Exception
	{
		Path page = Files.writeString(tmp.resolve("page.txt"), "hello \u20ac", StandardCharsets.UTF_8);

		Pair<String, String> data = HttpProvider.getPageDataOldImpl(page.toUri().toString(), 1000);

		assertTrue(data.getLeft().startsWith("hello"), data.getLeft());
		assertTrue(provider.js_getPageData(page.toUri().toString()).startsWith("hello"));
	}

	@Test
	public void unreadablePageDataIsEmpty()
	{
		assertEquals("", HttpProvider.getPageDataOldImpl("not a url", -1).getLeft());
		assertNull(HttpProvider.getPageDataOldImpl("not a url", -1).getRight());
		assertEquals("", HttpProvider.getPageDataOldImpl(tmp.resolve("missing.txt").toUri().toString(), -1).getLeft());
		assertEquals("", provider.js_getPageData("not a url"));
	}

	@Test
	public void mediaDataIsReadFromAFileUrl() throws Exception
	{
		byte[] content = { 0, 1, 2, (byte)200 };
		Path file = Files.write(tmp.resolve("data.bin"), content);

		assertArrayEquals(content, provider.js_getMediaData(file.toUri().toString()));
		assertNull(provider.js_getMediaData(null));
		assertEquals(0, provider.js_getMediaData(tmp.resolve("missing.bin").toUri().toString()).length);
	}

	@Test
	public void anExplicitProxyGetsCredentials()
	{
		RequestConfig.Builder builder = RequestConfig.custom();

		BasicCredentialsProvider credentials = HttpProvider.setHttpClientProxy(builder, "http://example.com", "user", "pw", "proxy.local", 3128);

		assertEquals("proxy.local", builder.build().getProxy().getHostName());
		assertNotNull(credentials.getCredentials(new AuthScope("proxy.local", 3128), null));
		assertNull(HttpProvider.setHttpClientProxy(builder, "http://example.com", null, null, "proxy.local", 3128));
		assertNotNull(HttpProvider.setHttpClientProxy(builder, "http://example.com", "user", null, "proxy.local", 3128));
	}

	@Test
	public void withoutProxyTheSystemProxiesAreAsked()
	{
		RequestConfig.Builder builder = RequestConfig.custom();

		assertNull(HttpProvider.setHttpClientProxy(builder, "http://localhost/", null, null, null, 0));
		// an invalid url is only logged
		assertNull(HttpProvider.setHttpClientProxy(builder, "http://bad host/", null, null, null, 0));
	}

	// --- certificates ---

	@Test
	public void certificatesHolderMatchesWholeChains() throws Exception
	{
		X509Certificate cert = certificate();
		CertificatesHolder holder = new CertificatesHolder();
		assertFalse(holder.isValid(new X509Certificate[] { cert }));

		holder.add(new X509Certificate[] { cert });

		assertTrue(holder.isValid(new X509Certificate[] { cert }));
		assertFalse(holder.isValid(new X509Certificate[] { cert, cert }));
		holder.add(new X509Certificate[] { cert, cert });
		assertTrue(holder.isValid(new X509Certificate[] { cert, cert }));
	}

	@Test
	public void certificatesHolderComparesEachCertificate() throws Exception
	{
		X509Certificate cert = certificate();
		X509Certificate other = mock(X509Certificate.class);
		CertificatesHolder holder = new CertificatesHolder();
		holder.add(new X509Certificate[] { cert, cert });

		assertFalse(holder.isValid(new X509Certificate[] { cert, other }));
	}

	@Test
	public void trustStrategyRemembersTheLastChain() throws Exception
	{
		AllowedCertTrustStrategy strategy = new AllowedCertTrustStrategy();
		X509Certificate[] chain = { certificate() };

		// the chain is not in the holder (a fresh one, unless the user accepted exactly this test certificate)
		assertFalse(strategy.isTrusted(chain, "EC"));

		assertSame(chain, strategy.getAndClearLastCertificates());
		assertNull(strategy.getAndClearLastCertificates());
		assertSame(strategy.getCertificatesHolder(), strategy.getCertificatesHolder());
	}
}
