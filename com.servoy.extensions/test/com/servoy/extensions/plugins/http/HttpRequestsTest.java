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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mozilla.javascript.Function;

import com.servoy.extensions.plugins.file.JSFile;
import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * Executing http requests against {@link FakeAsyncClient}: the real request producers write the request (headers,
 * form, body, file, multipart) and the real response consumer reads the queued response. No network.
 */
public class HttpRequestsTest
{
	private static final String URL = "http://api.example/items";

	private HttpPlugin plugin;
	private IClientPluginAccess access;
	private FakeAsyncClient http;
	private RequestConfig.Builder config;
	private final List<Object[]> callbacks = new ArrayList<>();

	@TempDir
	Path tmp;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IClientPluginAccess.class);
		plugin = new HttpPlugin();
		plugin.initialize(access);
		http = new FakeAsyncClient();
		config = RequestConfig.custom();
		doAnswer(inv -> {
			callbacks.add(new Object[] { inv.getArgument(1), inv.getArgument(2) });
			return null;
		}).when(access).executeMethod(eq("scopes.globals"), any(), any(), anyBoolean());
	}

	@AfterEach
	public void tearDown() throws Exception
	{
		plugin.unload();
	}

	private static Function callback(String name)
	{
		Function f = mock(Function.class);
		when(f.get("_scopename_", f)).thenReturn("globals");
		when(f.get("_methodname_", f)).thenReturn(name);
		return f;
	}

	private static byte[] bytes(String s)
	{
		return s.getBytes(StandardCharsets.UTF_8);
	}

	private GetRequest get()
	{
		return new GetRequest(URL, http, plugin, config, null);
	}

	private PostRequest post()
	{
		return new PostRequest(URL, http, plugin, config, null);
	}

	// --- GET and the response ---

	@Test
	public void aGetRequestSendsTheHeadersAndReadsTheResponse()
	{
		http.respond(200, "application/json; charset=UTF-8", bytes("{\"a\":\"\u20ac\"}"), "X-Id", "1", "X-Id", "2");
		GetRequest request = get();
		assertTrue(request.js_addHeader("X-Test", "a"));
		assertTrue(request.js_addHeader("X-Test", "b"));
		assertFalse(request.js_addHeader(null, "x"));

		Response response = request.js_executeRequest();

		assertEquals(200, response.js_getStatusCode());
		assertEquals("Reason 200", response.js_getStatusReasonPhrase());
		assertEquals("{\"a\":\"\u20ac\"}", response.js_getResponseBody());
		assertEquals("UTF-8", response.js_getCharset());
		assertArrayEquals(new String[] { "1", "2" }, (String[])response.js_getResponseHeaders("X-Id").get("X-Id"));
		assertEquals(1, response.js_getResponseHeaders().size());
		assertNull(response.js_getException());
		assertNull(response.js_getFileUpload());
		assertTrue(response.js_close());

		FakeAsyncClient.Exchange exchange = http.last();
		assertEquals("GET", exchange.request().getMethod());
		assertEquals(2, exchange.request().getHeaders("X-Test").length);
		assertEquals("gzip", exchange.request().getFirstHeader("Accept-Encoding").getValue());
		assertNull(exchange.entity());
		assertEquals(2, request.js_getHeaders().get("X-Test").length);
	}

	@Test
	public void anOwnAcceptEncodingIsKept()
	{
		http.respond(204, null, null);
		GetRequest request = get();
		request.js_addHeader("accept-encoding", "identity");

		Response response = request.js_executeRequest();

		assertEquals(1, http.last().request().getHeaders("Accept-Encoding").length);
		assertEquals("identity", http.last().request().getFirstHeader("Accept-Encoding").getValue());
		assertEquals(204, response.js_getStatusCode());
		assertNull(response.js_getCharset());
	}

	@Test
	public void binaryResponseIsReadAsMediaData()
	{
		byte[] data = { 1, 2, 3, (byte)250 };
		http.respond(200, "image/png", data);

		Response response = get().js_executeRequest();

		assertArrayEquals(data, response.js_getMediaData());
		// the body was read as bytes, so it is not a text anymore
		assertEquals("", response.js_getResponseBody());
	}

	@Test
	public void aGzippedResponseIsUnpacked() throws IOException
	{
		ByteArrayOutputStream zipped = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(zipped))
		{
			gzip.write(bytes("hello gzip"));
		}
		http.respond(200, "text/plain; charset=UTF-8", "gzip", zipped.toByteArray());

		assertEquals("hello gzip", get().js_executeRequest().js_getResponseBody());
	}

	@Test
	public void aLargeResponseGoesToATemporaryFile() throws IOException
	{
		byte[] big = new byte[60 * 1024];
		Arrays.fill(big, (byte)'x');
		http.respond(200, "application/octet-stream", big);

		Response response = get().js_executeRequest();

		JSFileUpload upload = response.js_getFileUpload();
		assertNotNull(upload);
		assertEquals(big.length, upload.getSize());
		assertEquals("application/octet-stream", upload.getContentType());
		assertTrue(upload.getName().startsWith("httpplugin"));
		assertTrue(upload.lastModified() > 0);
		try (java.io.InputStream in = upload.getInputStream())
		{
			assertNotNull(in);
		}
		assertArrayEquals(big, upload.getBytes());
		assertArrayEquals(big, response.js_getMediaData());
		assertTrue(response.js_close());
		assertFalse(upload.getFile().exists());
		assertNull(upload.getInputStream());
		assertNull(upload.getBytes());
	}

	@Test
	public void aFailedRequestGivesAResponseWithTheException()
	{
		http.fail(new IOException("connection refused"));

		Response response = get().js_executeRequest();

		assertTrue(response.js_getException().contains("connection refused"), response.js_getException());
		assertEquals(0, response.js_getStatusCode());
		assertNull(response.js_getStatusReasonPhrase());
		assertEquals("", response.js_getResponseBody());
		assertNull(response.js_getMediaData());
		assertEquals(0, response.js_getResponseHeaders().size());
		assertNull(response.js_getCharset());
		assertEquals(0, response.getAllowedMethods().length);
	}

	@Test
	public void aCancelledRequest()
	{
		http.cancel();

		assertEquals("Request was cancelled", get().js_executeRequest().js_getException());
	}

	@Test
	public void closingAFailedResponseIsSafe()
	{
		http.fail(new IOException("down"));
		Response response = get().js_executeRequest();

		assertTrue(response.js_close());
		assertNull(response.js_getFileUpload());
		assertTrue(new Response().js_close());
	}

	// --- authentication and proxy ---

	@Test
	public void basicCredentialsAreSetForTheTargetHost() throws Exception
	{
		http.respond(200, "text/plain", bytes("ok"));
		GetRequest request = get();
		request.js_usePreemptiveAuthentication(true);

		request.js_executeRequest("user", "pw");

		HttpClientContext context = (HttpClientContext)http.last().context();
		assertNotNull(context.getCredentialsProvider().getCredentials(new AuthScope(new HttpHost("http", "api.example", -1)), context));
		assertNotNull(context.getAuthExchange(new HttpHost("http", "api.example", -1)).getAuthScheme());
	}

	@Test
	public void windowsCredentials() throws Exception
	{
		http.respond(200, "text/plain", bytes("ok"));

		get().js_executeRequest("user", null, "pc", "DOMAIN");

		HttpClientContext context = (HttpClientContext)http.last().context();
		assertTrue(context.getCredentialsProvider().getCredentials(new AuthScope(new HttpHost("http", "api.example", -1)), context)
			.getClass().getSimpleName().startsWith("NT"));
	}

	@Test
	public void withoutUserOrProxyThereIsNoContext()
	{
		http.respond(200, "text/plain", bytes("ok"));

		get().js_executeRequest("", "pw");

		assertNull(http.last().context());
	}

	@Test
	public void proxyCredentialsAreUsed()
	{
		http.respond(200, "text/plain", bytes("ok"));
		BasicCredentialsProvider proxy = HttpProvider.setHttpClientProxy(config, URL, "puser", "ppw", "proxy.local", 3128);

		new GetRequest(URL, http, plugin, config, proxy).js_executeRequest();

		assertEquals(proxy, ((HttpClientContext)http.last().context()).getCredentialsProvider());
	}

	@Test
	public void anInvalidUrlWithCredentialsIsReported()
	{
		// a url that java.net.URL cannot parse (unknown protocol) but that is a valid URI for the request
		GetRequest request = new GetRequest("unknown://api.example/", http, plugin, config, null);

		Response response = request.js_executeRequest("user", "pw");

		assertNotNull(response.js_getException());
		assertTrue(http.exchanges.isEmpty());
	}

	// --- bodies ---

	@Test
	public void bodyContentIsSentWithItsMimeTypeAndCharset()
	{
		http.respond(201, "text/plain", bytes("created"));
		PostRequest request = post();
		request.js_setBodyContent("{\"a\":1}", "application/json");
		request.js_setCharset("UTF-8");

		request.js_executeRequest();

		assertEquals("{\"a\":1}", http.last().bodyText());
		assertEquals("application/json; charset=UTF-8", http.last().entity().getContentType());
		assertEquals("application/json", request.js_getContentType());
		// the body is consumed by the request
		assertNull(request.js_getHttpBody());
	}

	@Test
	public void formParametersAreUrlEncoded()
	{
		http.respond(200, "text/plain", bytes("ok"));
		PostRequest request = post();
		assertTrue(request.js_addParameter("name", "a b"));
		assertTrue(request.js_addParameter("x", "1&2"));

		request.js_executeRequest();

		String body = http.last().bodyText();
		assertTrue(body.contains("name=a+b") && body.contains("x=1%262"), body);
		assertTrue(http.last().entity().getContentType().startsWith("application/x-www-form-urlencoded"), http.last().entity().getContentType());
		assertEquals("a b", request.js_getParameters().get("name"));
	}

	@Test
	public void aParameterWithoutNameIsTheBody()
	{
		PostRequest request = post();

		assertTrue(request.js_addParameter(null, "raw"));
		assertFalse(request.js_addParameter(null, null));
		assertFalse(request.js_addParameter(null, "v", "text/plain"));

		assertEquals("raw", request.js_getHttpBody());
		assertEquals("text/plain", request.js_getContentType());
		assertTrue(request.js_getParameters().isEmpty());
	}

	@Test
	public void anEmptyBodyIsSent()
	{
		http.respond(200, "text/plain", bytes("ok"));

		post().js_executeRequest();

		assertEquals("", http.last().bodyText());
	}

	@Test
	public void aSingleFileIsTheBody() throws IOException
	{
		Path file = Files.writeString(tmp.resolve("data.txt"), "file content");
		http.respond(200, "text/plain", bytes("ok"));
		PutRequest request = new PutRequest(URL, http, plugin, config, null);
		assertTrue(request.js_setFile(file.toString()));
		assertFalse(request.js_setFile(tmp.resolve("missing.txt").toString()));
		assertTrue(request.js_setFile(file.toString()));

		request.js_executeRequest();

		assertEquals("PUT", http.last().request().getMethod());
		assertEquals("file content", http.last().bodyText());
	}

	@Test
	public void aSingleJSFileWithItsMimeType() throws IOException
	{
		Path file = Files.writeString(tmp.resolve("data.csv"), "a;b");
		http.respond(200, "text/plain", bytes("ok"));
		PostRequest request = post();
		assertTrue(request.js_addFile("f", new JSFile(file.toFile(), access), "text/csv"));

		request.js_executeRequest();

		assertEquals("a;b", http.last().bodyText());
		assertEquals("text/csv", http.last().entity().getContentType());
	}

	@Test
	public void filesThatDoNotExistAreNotAdded()
	{
		PostRequest request = post();
		String missing = tmp.resolve("missing").toString();
		JSFile missingFile = new JSFile(tmp.resolve("missing").toFile(), access);

		assertFalse(request.js_addFile("p", "n", missing));
		assertFalse(request.js_addFile("p", "n", missing, "text/plain"));
		assertFalse(request.js_addFile("p", "n", (String)null));
		assertFalse(request.js_addFile("p", missingFile));
		assertFalse(request.js_addFile("p", missingFile, "text/plain"));
		assertFalse(request.js_addFile("p", "n", (Object)missingFile));
		assertFalse(request.js_addFile("p", "n", missingFile, "text/plain"));
		assertFalse(request.js_addFile("p", "not a file"));
		assertFalse(new PutRequest(URL, http, plugin, config, null).js_setFile((JSFile)null));
	}

	@Test
	public void filesAndParametersAreSentAsMultipart() throws IOException
	{
		Path text = Files.writeString(tmp.resolve("a.txt"), "AAA");
		Path csv = Files.writeString(tmp.resolve("b.csv"), "B;B");
		http.respond(200, "text/plain", bytes("ok"));
		PostRequest request = post();
		assertTrue(request.js_addFile("first", "a.txt", text.toString()));
		assertTrue(request.js_addFile("second", "b.csv", csv.toString(), "text/csv"));
		assertTrue(request.js_addFile("third", new JSFile(text.toFile(), access)));
		assertTrue(request.js_addFile("fourth", "renamed.txt", (Object)new JSFile(text.toFile(), access)));
		assertTrue(request.js_addFile("fifth", "x.bin", new JSFile(text.toFile(), access), "application/x-test"));
		assertTrue(request.js_addParameter("field", "value"));
		assertTrue(request.js_addParameter("json", "{}", "application/json"));

		request.js_executeRequest();

		String contentType = http.last().entity().getContentType();
		assertTrue(contentType.startsWith("multipart/form-data; boundary="), contentType);
		String boundary = contentType.substring(contentType.indexOf('=') + 1);
		String body = http.last().bodyText();
		assertTrue(body.contains("name=\"first\"; filename=\"a.txt\""), body);
		assertTrue(body.contains("Content-Type: text/csv\r\n\r\nB;B\r\n"), body);
		assertTrue(body.contains("name=\"fourth\"; filename=\"renamed.txt\""), body);
		assertTrue(body.contains("Content-Type: application/x-test"), body);
		assertTrue(body.contains("name=\"field\"\r\n\r\nvalue\r\n"), body);
		assertTrue(body.contains("name=\"json\"\r\nContent-Type: application/json"), body);
		assertTrue(body.endsWith("--" + boundary + "--\r\n"), body);
		assertEquals(body.length(), http.last().entity().getContentLength());
		assertFalse(http.last().entity().isChunked());
	}

	@Test
	public void forceMultipartWithOneParameter()
	{
		http.respond(200, "text/plain", bytes("ok"));
		PostRequest request = post();
		request.js_forceMultipart(true);
		request.js_addParameter("only", "1");

		request.js_executeRequest();

		assertTrue(http.last().entity().getContentType().startsWith("multipart/form-data"));
		assertTrue(http.last().bodyText().contains("name=\"only\"\r\n\r\n1\r\n"), http.last().bodyText());
	}

	@Test
	public void multipartProducerBasics()
	{
		MultiPartEntityProducer producer = new MultiPartEntityProducer();

		assertTrue(producer.isRepeatable());
		assertEquals(Integer.MAX_VALUE, producer.available());
		assertNull(producer.getContentEncoding());
		assertNull(producer.getTrailerNames());
		// only the closing boundary
		assertEquals(6 + producer.boundary.length(), producer.getContentLength());
		producer.failed(new IOException("logged only"));
		producer.releaseResources();
	}

	// --- other methods ---

	@Test
	public void optionsRequestReturnsTheAllowedMethods()
	{
		http.respond(200, null, null, "Allow", "GET, POST", "Allow", "OPTIONS");
		OptionsRequest request = new OptionsRequest(URL, http, plugin, config, null);

		String[] methods = request.js_getAllowedMethods(request.js_executeRequest());

		Arrays.sort(methods);
		assertArrayEquals(new String[] { "GET", "OPTIONS", "POST" }, methods);
	}

	@Test
	public void otherRequestTypesExecute()
	{
		http.respond(200, null, null).respond(200, null, null).respond(200, null, null).respond(200, "text/plain", bytes("p"));

		new HeadRequest(URL, http, plugin, config, null).js_executeRequest();
		new DeleteRequest(URL, http, plugin, config, null).js_executeRequest();
		new TraceRequest(URL, http, plugin, config, null).js_executeRequest();
		PatchRequest patch = new PatchRequest(URL, http, plugin, config, null);
		patch.js_setBodyContent("patch");
		patch.js_executeRequest();

		assertEquals(List.of("HEAD", "DELETE", "TRACE", "PATCH"), http.exchanges.stream().map(e -> e.request().getMethod()).toList());
		assertEquals("patch", http.last().bodyText());
	}

	// --- async callbacks ---

	@Test
	public void theSuccessCallbackGetsTheResponseAndTheExtraArguments()
	{
		http.respond(200, "text/plain", bytes("async"));

		get().js_executeAsyncRequest(callback("ok"), callback("ko"), new Object[] { "id-1", Integer.valueOf(7) });

		assertEquals(1, callbacks.size());
		assertEquals("ok", callbacks.get(0)[0]);
		Object[] args = (Object[])callbacks.get(0)[1];
		assertEquals("async", ((Response)args[0]).js_getResponseBody());
		assertEquals("id-1", args[1]);
		assertEquals(7, ((Number)args[2]).intValue());
	}

	@Test
	public void theErrorCallbackGetsTheMessage()
	{
		http.fail(new IOException("unreachable"));

		get().js_executeAsyncRequest("user", "pw", callback("ok"), callback("ko"));

		assertEquals("ko", callbacks.get(0)[0]);
		assertTrue(((String)((Object[])callbacks.get(0)[1])[0]).contains("unreachable"));
	}

	@Test
	public void asyncVariantsWithWindowsAuthentication()
	{
		http.respond(200, "text/plain", bytes("1")).respond(200, "text/plain", bytes("2")).respond(200, "text/plain", bytes("3"))
			.respond(200, "text/plain", bytes("4"));

		get().js_executeAsyncRequest(callback("ok"), callback("ko"));
		get().js_executeAsyncRequest("u", "p", "pc", "DOM", callback("ok"), callback("ko"));
		get().js_executeAsyncRequest("u", "p", callback("ok"), callback("ko"), new Object[0]);
		get().js_executeAsyncRequest("u", "p", "pc", "DOM", callback("ok"), callback("ko"), null);

		assertEquals(4, callbacks.size());
		assertTrue(callbacks.stream().allMatch(c -> "ok".equals(c[0])));
	}

	@Test
	public void aRequestThatCannotStartCallsTheErrorCallback()
	{
		new GetRequest("unknown://api.example/", http, plugin, config, null).js_executeAsyncRequest("user", "pw", callback("ok"), callback("ko"));

		assertEquals("ko", callbacks.get(0)[0]);
	}

	@Test
	public void noCallbacksWithoutAClient() throws Exception
	{
		http.respond(200, "text/plain", bytes("late")).fail(new IOException("late"));
		GetRequest ok = get();
		GetRequest ko = get();
		plugin.unload();

		ok.js_executeAsyncRequest(callback("ok"), callback("ko"));
		ko.js_executeAsyncRequest(callback("ok"), callback("ko"));

		assertTrue(callbacks.isEmpty());
		plugin = new HttpPlugin();
	}
}
