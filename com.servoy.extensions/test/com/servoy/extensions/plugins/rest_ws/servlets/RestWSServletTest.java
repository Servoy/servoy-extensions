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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeObject;

import com.servoy.extensions.plugins.rest_ws.RestWSClientPlugin;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.IFormManager;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.plugins.ClientPluginAccessProvider;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IPluginManager;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.scripting.GlobalScope;
import com.servoy.j2db.scripting.IExecutingEnviroment;
import com.servoy.j2db.scripting.JSMap;
import com.servoy.j2db.scripting.JSUpload;
import com.servoy.j2db.scripting.ScopesScope;
import com.servoy.j2db.server.shared.IHeadlessClient;
import com.servoy.j2db.util.ServoyException;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RestWSServlet request handling end to end against a mocked headless client: path parsing, method lookup in a
 * global scope, argument building, authentication, body decoding, result encoding and error mapping. No server and
 * no real client.
 */
public class RestWSServletTest
{
	private final Properties settings = new Properties();
	private IServerAccess serverAccess;
	private RestWSPlugin plugin;
	private RestWSServlet servlet;

	private IHeadlessClient client;
	private ClientPluginAccessProvider access;
	private GlobalScope scope;
	private RestWSClientPlugin clientPlugin;
	private final Map<String, String> userProperties = new HashMap<>();
	private GenericKeyedObjectPool<String, IHeadlessClient> pool;

	private HttpServletRequest request;
	private HttpServletResponse response;
	private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
	private final StringWriter responseWriter = new StringWriter();
	private final Map<String, String> requestHeaders = new HashMap<>();
	private final Map<String, String[]> parameters = new LinkedHashMap<>();

	@TempDir
	Path tmp;

	@BeforeEach
	public void setUp() throws Exception
	{
		settings.setProperty("rest_ws_reload_solution_after_request", "false");
		settings.setProperty("servoy.ng_web_client.temp.uploadir", tmp.resolve("uploads").toString());
		serverAccess = mock(IServerAccess.class);
		when(serverAccess.getSettings()).thenReturn(settings);
		when(serverAccess.getExecutor()).thenReturn(mock(ScheduledExecutorService.class));
		plugin = new RestWSPlugin();
		plugin.initialize(serverAccess);
		servlet = new RestWSServlet("rest_ws", plugin);

		client = mock(IHeadlessClient.class);
		access = mock(ClientPluginAccessProvider.class);
		IApplication application = mock(IApplication.class);
		IExecutingEnviroment engine = mock(IExecutingEnviroment.class);
		ScopesScope scopes = mock(ScopesScope.class);
		scope = mock(GlobalScope.class);
		IFormManager formManager = mock(IFormManager.class);
		IPluginManager pluginManager = mock(IPluginManager.class);
		clientPlugin = new RestWSClientPlugin();
		Solution solution = mock(Solution.class);

		when(Boolean.valueOf(client.isValid())).thenReturn(Boolean.TRUE);
		when(client.getPluginAccess()).thenReturn(access);
		doAnswer(inv -> {
			((Runnable)inv.getArgument(0)).run();
			return null;
		}).when(client).invokeAndWait(any());
		when(access.getApplication()).thenReturn(application);
		when(access.getFormManager()).thenReturn(formManager);
		when(access.getPluginManager()).thenReturn(pluginManager);
		when(access.getUserProperties()).thenReturn(userProperties);
		when(access.getSolutionName()).thenReturn("sol");
		when(pluginManager.getPlugin(IClientPlugin.class, "rest_ws")).thenReturn(clientPlugin);
		doAnswer(inv -> {
			((Runnable)inv.getArgument(0)).run();
			return null;
		}).when(application).invokeAndWait(any());
		when(application.getSolution()).thenReturn(solution);
		when(application.getScriptEngine()).thenReturn(engine);
		when(engine.getScopesScope()).thenReturn(scopes);
		when(scopes.getGlobalScope("api")).thenReturn(scope);

		pool = new GenericKeyedObjectPool<>(new BaseKeyedPooledObjectFactory<String, IHeadlessClient>()
		{
			@Override
			public IHeadlessClient create(String key)
			{
				return client;
			}

			@Override
			public PooledObject<IHeadlessClient> wrap(IHeadlessClient value)
			{
				return new DefaultPooledObject<>(value);
			}
		});
		pool.setMaxTotalPerKey(1);
		pool.setBlockWhenExhausted(false);
		Field field = RestWSPlugin.class.getDeclaredField("clientPool");
		field.setAccessible(true);
		field.set(plugin, pool);

		request = mock(HttpServletRequest.class);
		response = mock(HttpServletResponse.class);
		when(request.getHeader(anyString())).thenAnswer(inv -> requestHeaders.get(inv.getArgument(0)));
		when(request.getParameterMap()).thenAnswer(inv -> parameters);
		when(request.getParameter(anyString())).thenAnswer(inv -> {
			String[] v = parameters.get(inv.getArgument(0));
			return v == null ? null : v[0];
		});
		when(request.getContextPath()).thenReturn("/ctx");
		when(request.getServletPath()).thenReturn("/servoy-service");
		when(request.getProtocol()).thenReturn("HTTP/1.1");
		when(response.getOutputStream()).thenReturn(new ServletOutputStream()
		{
			@Override
			public void write(int b)
			{
				responseBody.write(b);
			}

			@Override
			public boolean isReady()
			{
				return true;
			}

			@Override
			public void setWriteListener(WriteListener writeListener)
			{
			}
		});
		when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
	}

	@AfterEach
	public void tearDown()
	{
		servlet.destroy();
		pool.close();
	}

	// --- helpers ---

	private void method(String name, Object result) throws Exception
	{
		when(scope.get(name)).thenReturn(mock(Function.class));
		when(access.executeMethod(eq("scopes.api"), eq(name), any(), eq(false))).thenReturn(result);
	}

	private Object[] argsOf(String name) throws Exception
	{
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(access).executeMethod(eq("scopes.api"), eq(name), args.capture(), eq(false));
		return args.getValue();
	}

	private void call(String httpMethod, String path) throws Exception
	{
		call(httpMethod, path, null, null);
	}

	private void call(String httpMethod, String path, String contentType, byte[] body) throws Exception
	{
		when(request.getMethod()).thenReturn(httpMethod);
		when(request.getPathInfo()).thenReturn(path);
		when(request.getRequestURI()).thenReturn("/ctx/servoy-service" + path);
		if (contentType != null)
		{
			requestHeaders.put("Content-Type", contentType);
			when(request.getContentType()).thenReturn(contentType);
		}
		byte[] content = body == null ? new byte[0] : body;
		when(Long.valueOf(request.getContentLengthLong())).thenReturn(Long.valueOf(content.length));
		when(Integer.valueOf(request.getContentLength())).thenReturn(Integer.valueOf(content.length));
		ByteArrayInputStream in = new ByteArrayInputStream(content);
		when(request.getInputStream()).thenReturn(new ServletInputStream()
		{
			@Override
			public int read()
			{
				return in.read();
			}

			@Override
			public int read(byte[] b, int off, int len)
			{
				return in.read(b, off, len);
			}

			@Override
			public boolean isFinished()
			{
				return in.available() == 0;
			}

			@Override
			public boolean isReady()
			{
				return true;
			}

			@Override
			public void setReadListener(ReadListener readListener)
			{
			}
		});
		servlet.service(request, response);
	}

	private String body()
	{
		return responseBody.toString(StandardCharsets.UTF_8);
	}

	private static byte[] bytes(String s)
	{
		return s.getBytes(StandardCharsets.UTF_8);
	}

	// --- path parsing ---

	@Test
	public void pathsAreParsedWithAndWithoutVersion()
	{
		when(request.getPathInfo()).thenReturn("/rest_ws/sol/api/a/b");
		RestWSServlet.WsRequestPath plain = servlet.parsePath(request);
		when(request.getPathInfo()).thenReturn("/rest_ws/v2/sol/api/x");
		RestWSServlet.WsRequestPath versionFirst = servlet.parsePath(request);
		when(request.getPathInfo()).thenReturn("/rest_ws/sol/v3/api");
		RestWSServlet.WsRequestPath versionAfterSolution = servlet.parsePath(request);

		assertEquals("sol", plain.solutionName);
		assertEquals("api", plain.scope_or_form);
		assertArrayEquals(new String[] { "a", "b" }, plain.args);
		assertEquals("api_v2", versionFirst.scope_or_form);
		assertEquals("api_v3", versionAfterSolution.scope_or_form);
		assertEquals(0, versionAfterSolution.args.length);
		assertTrue(plain.toString().contains("sol"));
		assertEquals(0, versionAfterSolution.getPossibleMethods().count());
		assertEquals(2, plain.getPossibleMethods().count());
	}

	@Test
	public void anInvalidPathIsABadRequest() throws Exception
	{
		call("GET", "/rest_ws/sol");
		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

		call("GET", "/other/sol/api/x");
		call("GET", null);
		verify(access, never()).executeMethod(anyString(), anyString(), any(), anyBoolean());
	}

	// --- GET ---

	@Test
	public void getCallsWsReadWithThePathArgumentsAndReturnsJson() throws Exception
	{
		method("ws_read", new JSONObject().put("id", 1));
		parameters.put("q", new String[] { "x" });

		call("GET", "/rest_ws/sol/api/1/2");

		Object[] args = argsOf("ws_read");
		assertEquals("1", args[0]);
		assertEquals("2", args[1]);
		assertArrayEquals(new String[] { "x" }, (String[])((JSMap< ? , ? >)args[2]).get("q"));
		assertEquals("{\"id\":1}", body());
		verify(response).setHeader("Content-Type", "application/json;charset=UTF-8");
		verify(response).setHeader("Cache-Control", "max-age=0, must-revalidate, proxy-revalidate");
		verify(response).setHeader("Access-Control-Allow-Origin", "*");
		verify(response).setHeader("Access-Control-Expose-Headers", "servoy.userproperties");
		assertEquals(1, pool.getNumIdle("sol"));
	}

	@Test
	public void theMostSpecificMethodNameIsUsed() throws Exception
	{
		method("ws_read_orders", "specific");

		call("GET", "/rest_ws/sol/api/orders/7");

		Object[] args = argsOf("ws_read_orders");
		assertArrayEquals(new Object[] { "7" }, args);
		assertEquals("\"specific\"", body());
	}

	@Test
	public void aNullResultIsNotFound() throws Exception
	{
		method("ws_read", null);

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
		assertNull(argsOf("ws_read"));
	}

	@Test
	public void aMissingMethodIsNotFound() throws Exception
	{
		call("GET", "/rest_ws/sol/api/1");

		verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
	}

	@Test
	public void aNotLoadedSolutionIsUnavailable() throws Exception
	{
		when(access.getApplication().getSolution()).thenReturn(null);

		call("GET", "/rest_ws/sol/api/1");

		verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
	}

	@Test
	public void theAcceptHeaderSelectsXmlAndAJsonpCallback() throws Exception
	{
		method("ws_read", new JSONObject().put("id", 1));
		requestHeaders.put("Accept", "application/xml");

		call("GET", "/rest_ws/sol/api");

		assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<id>1</id>", body());
		verify(response).setHeader("Content-Type", "application/xml;charset=UTF-8");
	}

	@Test
	public void jsonpCallbackWrapsTheResult() throws Exception
	{
		method("ws_read", new JSONObject().put("id", 1));
		parameters.put("callback", new String[] { "cb" });

		call("GET", "/rest_ws/sol/api");

		assertEquals("cb({\"id\":1})", body());
	}

	@Test
	public void textIsReturnedAsIs() throws Exception
	{
		method("ws_read", "plain text");
		requestHeaders.put("Accept", "text/plain; charset=ISO-8859-1");

		call("GET", "/rest_ws/sol/api");

		assertEquals("plain text", body());
		verify(response).setHeader("Content-Type", "text/plain;charset=ISO-8859-1");
	}

	@Test
	public void bytesGetTheirDetectedContentType() throws Exception
	{
		byte[] png = { (byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
		method("ws_read", png);
		requestHeaders.put("Accept", "image/*");

		call("GET", "/rest_ws/sol/api");

		assertArrayEquals(png, responseBody.toByteArray());
		verify(response).setHeader("Content-Type", "image/png");
	}

	@Test
	public void unknownBytesUseTheAcceptHeader() throws Exception
	{
		method("ws_read", new byte[] { 1, 2, 3 });
		requestHeaders.put("Accept", "*/*, application/pdf");

		call("GET", "/rest_ws/sol/api");

		verify(response).setHeader("Content-Type", "application/pdf");
	}

	@Test
	public void unknownBytesWithoutAcceptAreOctetStream() throws Exception
	{
		method("ws_read", new byte[] { 1, 2, 3 });

		call("GET", "/rest_ws/sol/api");

		verify(response).setHeader("Content-Type", "application/octet-stream");
	}

	@Test
	public void binaryRequestedButNoBytesReturned() throws Exception
	{
		method("ws_read", "not bytes");
		requestHeaders.put("Accept", "application/octet-stream");

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	public void aContentTypeSetByTheScriptIsKept() throws Exception
	{
		method("ws_read", "<a/>");
		when(response.getContentType()).thenReturn("text/html");

		call("GET", "/rest_ws/sol/api");

		assertEquals("<a/>", body());
		verify(response).setHeader("Content-Type", "text/html");
	}

	@Test
	public void responseHeadersFromTheSolution() throws Exception
	{
		method("ws_read", "x");
		NativeObject header = new NativeObject();
		header.put("name", header, "X-Obj");
		header.put("value", header, "2");
		method("ws_response_headers", new Object[] { "X-Str=1", header, "invalid", Integer.valueOf(3) });

		call("GET", "/rest_ws/sol/api");

		verify(response).addHeader("X-Str", "1");
		verify(response).addHeader("X-Obj", "2");
	}

	@Test
	public void aSingleResponseHeader() throws Exception
	{
		method("ws_read", "x");
		method("ws_response_headers", "X-One=a=b");

		call("GET", "/rest_ws/sol/api");

		verify(response).addHeader("X-One", "a=b");
	}

	// --- user properties ---

	@Test
	public void userPropertiesComeFromTheHeaderAndGoBack() throws Exception
	{
		method("ws_read", "x");
		requestHeaders.put("servoy.userproperties", "{\"lang\":\"nl\"}");
		userProperties.put("lang", "nl");
		when(Boolean.valueOf(request.isSecure())).thenReturn(Boolean.TRUE);

		call("GET", "/rest_ws/sol/api");

		verify(access).setUserProperties(Map.of("lang", "nl"));
		verify(response).setHeader("servoy.userproperties", "{\"lang\":\"nl\"}");
		ArgumentCaptor<Cookie> cookie = ArgumentCaptor.forClass(Cookie.class);
		verify(response).addCookie(cookie.capture());
		assertEquals("servoy.userproperty.lang", cookie.getValue().getName());
		assertEquals("/ctx/servoy-service/rest_ws/sol", cookie.getValue().getPath());
		assertTrue(cookie.getValue().getSecure());
	}

	@Test
	public void userPropertiesFromCookies() throws Exception
	{
		method("ws_read", "x");
		when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("servoy.userproperty.a", "1"), new Cookie("other", "2") });

		call("GET", "/rest_ws/sol/api");

		verify(access).setUserProperties(Map.of("a", "1"));
	}

	@Test
	public void anInvalidUserPropertiesHeaderIsIgnored() throws Exception
	{
		method("ws_read", "x");
		requestHeaders.put("servoy.userproperties", "not json");

		call("GET", "/rest_ws/sol/api");

		verify(access, never()).setUserProperties(any());
		assertEquals("\"x\"", body());
	}

	@Test
	public void userPropertiesAreNotSentWhenTheScriptSaysSo() throws Exception
	{
		userProperties.put("lang", "nl");
		when(scope.get("ws_read")).thenReturn(mock(Function.class));
		when(access.executeMethod(eq("scopes.api"), eq("ws_read"), any(), eq(false))).thenAnswer(inv -> {
			((com.servoy.extensions.plugins.rest_ws.RestWSClientProvider)clientPlugin.getScriptObject()).sendResponseUserPropertiesHeaders(false);
			return "x";
		});

		call("GET", "/rest_ws/sol/api");

		verify(response, never()).setHeader(eq("servoy.userproperties"), anyString());
	}

	@Test
	public void theNodebugHeaderUsesItsOwnClient() throws Exception
	{
		method("ws_read", "x");
		requestHeaders.put("servoy.nodebug", "true");

		call("GET", "/rest_ws/sol/api");

		assertEquals(1, pool.getNumIdle("sol:nodebug"));
		verify(response).setHeader("Access-Control-Expose-Headers", "servoy.nodebug, servoy.userproperties");
	}

	// --- authentication ---

	private static String basic(String user, String password)
	{
		return "Basic " + Base64.getEncoder().encodeToString(bytes(user + ":" + password));
	}

	@Test
	public void wsAuthenticateGetsTheCredentialsAndItsResultIsPassed() throws Exception
	{
		method("ws_read", "x");
		method("ws_authenticate", "user-info");
		requestHeaders.put("Authorization", basic("john", "secret"));

		call("GET", "/rest_ws/sol/api");

		assertArrayEquals(new Object[] { "john", "secret" }, argsOf("ws_authenticate"));
		JSMap< ? , ? > extra = (JSMap< ? , ? >)argsOf("ws_read")[0];
		assertArrayEquals(new Object[] { "user-info" }, (Object[])extra.get("ws_authenticate"));
	}

	@Test
	public void wsAuthenticateReturningTrueAddsNothing() throws Exception
	{
		method("ws_read", "x");
		method("ws_authenticate", Boolean.TRUE);
		requestHeaders.put("Authorization", basic("john", "secret"));

		call("GET", "/rest_ws/sol/api");

		assertNull(argsOf("ws_read"));
	}

	@Test
	public void missingOrDeniedCredentialsAreUnauthorized() throws Exception
	{
		method("ws_read", "x");
		method("ws_authenticate", Boolean.FALSE);

		call("GET", "/rest_ws/sol/api");
		requestHeaders.put("Authorization", "Bearer token");
		call("GET", "/rest_ws/sol/api");
		requestHeaders.put("Authorization", basic("john", " "));
		call("GET", "/rest_ws/sol/api");
		requestHeaders.put("Authorization", basic("john", "wrong"));
		call("GET", "/rest_ws/sol/api");

		verify(response, org.mockito.Mockito.times(4)).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		verify(response, org.mockito.Mockito.times(4)).setHeader("WWW-Authenticate", "Basic realm=\"sol\"");
	}

	@Test
	public void authorizedGroupsCheckTheServoyUsers() throws Exception
	{
		settings.setProperty("rest_ws_plugin_authorized_groups", "admins, api");
		method("ws_read", "x");
		when(serverAccess.checkPasswordForUserName("john", "secret")).thenReturn("uid-1");
		when(serverAccess.getUserGroups("uid-1")).thenReturn(new String[] { "users", "api" });
		requestHeaders.put("Authorization", basic("john", "secret"));

		call("GET", "/rest_ws/sol/api");

		assertEquals("\"x\"", body());
	}

	@Test
	public void aUserOutsideTheGroupsIsForbidden() throws Exception
	{
		settings.setProperty("rest_ws_plugin_authorized_groups", "admins");
		method("ws_read", "x");
		when(serverAccess.checkPasswordForUserName("john", "secret")).thenReturn("uid-1");
		when(serverAccess.getUserGroups("uid-1")).thenReturn(new String[] { "users" });
		requestHeaders.put("Authorization", basic("john", "secret"));

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
	}

	@Test
	public void anUnknownUserIsUnauthorized() throws Exception
	{
		settings.setProperty("rest_ws_plugin_authorized_groups", "admins");
		method("ws_read", "x");
		requestHeaders.put("Authorization", basic("john", "secret"));

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		verify(response).setHeader("WWW-Authenticate", "Basic realm=\"john\"");
	}

	// --- errors ---

	@Test
	public void aThrownNumberIsTheStatus() throws Exception
	{
		when(scope.get("ws_read")).thenReturn(mock(Function.class));
		when(access.executeMethod(eq("scopes.api"), eq("ws_read"), any(), eq(false)))
			.thenThrow(new JavaScriptException(Double.valueOf(409), "src", 1));

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(409);
		// a user script exception does not force a reload
		assertEquals(1, pool.getNumIdle("sol"));
	}

	@Test
	public void aThrownArrayIsStatusAndBody() throws Exception
	{
		when(scope.get("ws_read")).thenReturn(mock(Function.class));
		org.mozilla.javascript.NativeJavaArray thrown = mock(org.mozilla.javascript.NativeJavaArray.class);
		when(thrown.unwrap()).thenReturn(new Object[] { Integer.valueOf(422), "{\"error\":\"bad\"}" });
		JavaScriptException exception = new JavaScriptException(thrown, "src", 1);
		when(access.executeMethod(eq("scopes.api"), eq("ws_read"), any(), eq(false))).thenThrow(exception);

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(422);
		verify(response).setContentType("application/json");
		assertEquals("{\"error\":\"bad\"}", responseWriter.toString());
	}

	@Test
	public void anyOtherScriptErrorIsAServerError() throws Exception
	{
		when(scope.get("ws_read")).thenReturn(mock(Function.class));
		when(access.executeMethod(eq("scopes.api"), eq("ws_read"), any(), eq(false))).thenThrow(new JavaScriptException("oops", "src", 1));

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		verify(access).reportError(eq("Error executing rest call"), any());
	}

	@Test
	public void aJavaErrorIsAServerErrorAndReloadsTheSolution() throws Exception
	{
		when(scope.get("ws_read")).thenReturn(mock(Function.class));
		when(access.executeMethod(eq("scopes.api"), eq("ws_read"), any(), eq(false))).thenThrow(new IllegalStateException("bug"));

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		// not a user script exception: the client goes back through the (mocked) reload executor
		assertEquals(0, pool.getNumIdle("sol"));
		verify(serverAccess.getExecutor()).execute(any());
	}

	@Test
	public void noClientIsUnavailable() throws Exception
	{
		method("ws_read", "x");
		pool.borrowObject("sol");

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
	}

	@Test
	public void maintenanceModeIsUnavailable() throws Exception
	{
		when(scope.get("ws_read")).thenReturn(mock(Function.class));
		when(access.executeMethod(eq("scopes.api"), eq("ws_read"), any(), eq(false))).thenThrow(new ServoyException(ServoyException.MAINTENANCE_MODE));

		call("GET", "/rest_ws/sol/api");

		verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
	}

	@Test
	public void requestsAreRefusedWhileShuttingDown() throws Exception
	{
		plugin.beforeShutdown();

		servlet.service(request, response);

		verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
	}

	@Test
	public void withoutTheClientPluginTheRequestStillRuns() throws Exception
	{
		method("ws_read", "x");
		when(access.getPluginManager().getPlugin(IClientPlugin.class, "rest_ws")).thenReturn(null);

		userProperties.put("lang", "nl");

		call("GET", "/rest_ws/sol/api");

		assertEquals("\"x\"", body());
		verify(response, never()).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		// the server setting (default true) decides
		verify(response).setHeader("servoy.userproperties", "{\"lang\":\"nl\"}");
	}

	// --- DELETE ---

	@Test
	public void deleteResults() throws Exception
	{
		method("ws_delete", Boolean.TRUE);
		call("DELETE", "/rest_ws/sol/api/1");
		assertEquals("", body());

		method("ws_delete", Boolean.FALSE);
		call("DELETE", "/rest_ws/sol/api/1");
		verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);

		method("ws_delete", "removed");
		call("DELETE", "/rest_ws/sol/api/1");
		assertEquals("\"removed\"", body());
	}

	@Test
	public void deleteErrors() throws Exception
	{
		call("DELETE", "/rest_ws/sol/api/1");
		verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
	}

	// --- POST, PUT, PATCH bodies ---

	@Test
	public void postDecodesAJsonBody() throws Exception
	{
		method("ws_create", "created");

		call("POST", "/rest_ws/sol/api", "application/json", bytes("{\"a\":1}"));

		Object decoded = argsOf("ws_create")[0];
		assertNotNull(decoded);
		assertTrue(decoded.toString().contains("a"), decoded.toString());
		assertEquals("\"created\"", body());
	}

	@Test
	public void postWithoutResultSendsNothing() throws Exception
	{
		method("ws_create", null);

		call("POST", "/rest_ws/sol/api", "application/json", bytes("{}"));

		assertEquals("", body());
	}

	@Test
	public void textAndFormBodies() throws Exception
	{
		method("ws_create", null);

		call("POST", "/rest_ws/sol/api", "text/plain; charset=UTF-8", bytes("h\u00e9"));
		assertEquals("h\u00e9", argsOf("ws_create")[0]);
	}

	@Test
	public void aFormPostIsSplitInParts() throws Exception
	{
		method("ws_create", null);

		call("POST", "/rest_ws/sol/api", "application/x-www-form-urlencoded", bytes("a=1&b=x+y"));

		Object[] parts = (Object[])argsOf("ws_create")[0];
		assertEquals(2, parts.length);
		assertEquals("b", ((JSMap< ? , ? >)parts[1]).get("name"));
		assertEquals("x y", ((JSMap< ? , ? >)parts[1]).get("value"));
	}

	@Test
	public void anXmlBodyIsConvertedToJson() throws Exception
	{
		method("ws_create", null);

		call("POST", "/rest_ws/sol/api", "application/xml", bytes("<a><b>1</b></a>"));

		assertNotNull(argsOf("ws_create")[0]);
	}

	@Test
	public void binaryIsAnUploadOrBytes() throws Exception
	{
		method("ws_create", null);

		call("POST", "/rest_ws/sol/api", "application/octet-stream", new byte[] { 1, 2 });

		assertInstanceOf(JSUpload.class, argsOf("ws_create")[0]);
	}

	@Test
	public void binaryAsBytesWhenUploadsAreOff() throws Exception
	{
		settings.setProperty("rest_ws_use_jsupload_for_binary_data", "false");
		method("ws_create", null);

		call("POST", "/rest_ws/sol/api", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1, 2 });

		assertArrayEquals(new byte[] { 1, 2 }, (byte[])argsOf("ws_create")[0]);
	}

	@Test
	public void aBodyWithoutContentTypeIsGuessed() throws Exception
	{
		method("ws_create", null);

		call("POST", "/rest_ws/sol/api", null, bytes("{\"a\":1}"));

		assertNotNull(argsOf("ws_create")[0]);
	}

	@Test
	public void anUnknownBodyIsUnsupported() throws Exception
	{
		method("ws_create", null);

		call("POST", "/rest_ws/sol/api", "image/png", new byte[] { 1 });

		verify(response).setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	public void anEmptyPostCallsWithoutBody() throws Exception
	{
		method("ws_create", null);

		call("POST", "/rest_ws/sol/api", null, null);

		assertArrayEquals(new Object[] { null }, argsOf("ws_create"));
	}

	@Test
	public void multipartUploads() throws Exception
	{
		method("ws_create", null);
		String boundary = "XyZ";
		String multipart = "--XyZ\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n" +
			"--XyZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nAAA\r\n" +
			"--XyZ--\r\n";

		call("POST", "/rest_ws/sol/api", "multipart/form-data; boundary=" + boundary, bytes(multipart));

		Object[] parts = (Object[])argsOf("ws_create")[0];
		// with JSUpload on, only the file is a part and the form fields go with it
		assertEquals(1, parts.length);
		JSMap< ? , ? > file = (JSMap< ? , ? >)parts[0];
		assertEquals("a.txt", file.get("fileName"));
		assertEquals("text/plain", file.get("contentType"));
		assertEquals("UTF-8", file.get("charset"));
		assertEquals("file", file.get("name"));
		JSUpload upload = (JSUpload)file.get("value");
		assertArrayEquals(new String[] { "field" }, upload.getFields());
		assertEquals("value", upload.getFieldValue("field"));
	}

	@Test
	public void multipartWithoutUploadObjects() throws Exception
	{
		settings.setProperty("rest_ws_use_jsupload_for_binary_data", "false");
		method("ws_create", null);
		String multipart = "--XyZ\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n" +
			"--XyZ\r\nContent-Disposition: form-data; name=\"json\"\r\nContent-Type: application/json\r\n\r\n{\"a\":1}\r\n" + "--XyZ--\r\n";

		call("POST", "/rest_ws/sol/api", "multipart/form-data; boundary=XyZ", bytes(multipart));

		Object[] parts = (Object[])argsOf("ws_create")[0];
		assertEquals(2, parts.length);
		assertEquals("value", ((JSMap< ? , ? >)parts[0]).get("value"));
		assertEquals("text/plain", ((JSMap< ? , ? >)parts[0]).get("contentType"));
		assertNotNull(((JSMap< ? , ? >)parts[1]).get("value"));
	}

	@Test
	public void putUpdatesAndMapsFalseToNotFound() throws Exception
	{
		method("ws_update", "updated");
		call("PUT", "/rest_ws/sol/api/1", "text/plain", bytes("new"));
		assertEquals("new", argsOf("ws_update")[0]);
		// a text request gets a text response
		assertEquals("updated", body());

		method("ws_update", Boolean.FALSE);
		call("PUT", "/rest_ws/sol/api/1", "text/plain", bytes("new"));
		verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
	}

	@Test
	public void putWithoutBodyOrWithAnUnknownBody() throws Exception
	{
		call("PUT", "/rest_ws/sol/api/1", null, null);
		verify(response).setStatus(HttpServletResponse.SC_NO_CONTENT);

		call("PUT", "/rest_ws/sol/api/1", "image/png", new byte[] { 1 });
		verify(response).setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
		verify(access, never()).executeMethod(anyString(), anyString(), any(), anyBoolean());
	}

	@Test
	public void patchIsHandledByTheServlet() throws Exception
	{
		method("ws_patch", Boolean.FALSE);
		call("PATCH", "/rest_ws/sol/api/1", "application/json", bytes("{\"a\":2}"));
		verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);

		method("ws_patch", "patched");
		call("PATCH", "/rest_ws/sol/api/1", "application/json", bytes("{\"a\":2}"));
		assertEquals("\"patched\"", body());
	}

	@Test
	public void patchWithoutBodyOrWithAnUnknownBody() throws Exception
	{
		call("PATCH", "/rest_ws/sol/api/1", null, null);
		verify(response).setStatus(HttpServletResponse.SC_NO_CONTENT);

		call("PATCH", "/rest_ws/sol/api/1", "image/png", new byte[] { 1 });
		verify(response).setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	public void writeMethodsReportScriptErrors() throws Exception
	{
		when(scope.get("ws_create")).thenReturn(mock(Function.class));
		when(scope.get("ws_update")).thenReturn(mock(Function.class));
		when(scope.get("ws_patch")).thenReturn(mock(Function.class));
		when(scope.get("ws_delete")).thenReturn(mock(Function.class));
		when(access.executeMethod(eq("scopes.api"), anyString(), any(), eq(false))).thenThrow(new JavaScriptException(Double.valueOf(418), "src", 1));

		call("POST", "/rest_ws/sol/api", "text/plain", bytes("a"));
		call("PUT", "/rest_ws/sol/api", "text/plain", bytes("a"));
		call("PATCH", "/rest_ws/sol/api", "text/plain", bytes("a"));
		call("DELETE", "/rest_ws/sol/api");

		verify(response, org.mockito.Mockito.times(4)).setStatus(418);
	}

	@Test
	public void writeMethodsWithAnInvalidPath() throws Exception
	{
		call("POST", "/rest_ws/sol", "text/plain", bytes("a"));
		call("PUT", "/rest_ws/sol", "text/plain", bytes("a"));
		call("PATCH", "/rest_ws/sol", "text/plain", bytes("a"));

		verify(response, org.mockito.Mockito.times(3)).setStatus(HttpServletResponse.SC_BAD_REQUEST);
	}

	// --- OPTIONS and CORS ---

	@Test
	public void optionsListsTheImplementedMethods() throws Exception
	{
		when(scope.get("ws_read")).thenReturn(mock(Function.class));
		when(scope.get("ws_create")).thenReturn(mock(Function.class));
		when(scope.get("ws_delete")).thenReturn(mock(Function.class));
		requestHeaders.put("Access-Control-Request-Method", "POST");
		requestHeaders.put("Access-Control-Request-Headers", "content-type, servoy.nodebug");
		requestHeaders.put("Origin", "https://app.example");

		call("OPTIONS", "/rest_ws/sol/api");

		verify(response).setHeader("Allow", "TRACE, OPTIONS, GET, HEAD, POST, DELETE");
		verify(response).setHeader("Access-Control-Allow-Origin", "https://app.example");
		verify(response).setHeader("Access-Control-Allow-Methods", "GET, DELETE, POST, PUT, OPTIONS");
		verify(response).setHeader("Access-Control-Allow-Headers", "content-type, servoy.nodebug, Allow");
		assertEquals(1, pool.getNumIdle("sol:nodebug"));
	}

	@Test
	public void optionsWithUpdateAndPatch() throws Exception
	{
		when(scope.get("ws_update")).thenReturn(mock(Function.class));
		when(scope.get("ws_patch")).thenReturn(mock(Function.class));

		call("OPTIONS", "/rest_ws/sol/api");

		verify(response).setHeader("Allow", "TRACE, OPTIONS, HEAD, PUT, PATCH");
		verify(response).setHeader("Access-Control-Allow-Headers", "Allow");
	}

	@Test
	public void optionsWithAnInvalidPath() throws Exception
	{
		call("OPTIONS", "/rest_ws/sol");

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
	}

	@Test
	public void theServletIsTheOneRegisteredByThePlugin()
	{
		ArgumentCaptor<jakarta.servlet.http.HttpServlet> registered = ArgumentCaptor.forClass(jakarta.servlet.http.HttpServlet.class);
		verify(serverAccess).registerWebService(eq("rest_ws"), registered.capture());
		assertInstanceOf(RestWSServlet.class, registered.getValue());
	}
}
