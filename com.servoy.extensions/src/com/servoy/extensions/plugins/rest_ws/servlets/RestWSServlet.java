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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileCleaningTracker;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;
import org.mozilla.javascript.xml.XMLObject;

import com.servoy.extensions.plugins.rest_ws.RestWSClientPlugin;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin.ExecFailedException;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin.NoClientsException;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin.NotAuthenticatedException;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin.NotAuthorizedException;
import com.servoy.j2db.persistence.ScriptVariable;
import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IFile;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.FunctionDefinition.Exist;
import com.servoy.j2db.scripting.JSMap;
import com.servoy.j2db.scripting.JSUpload;
import com.servoy.j2db.server.shared.IHeadlessClient;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.HTTPUtils;
import com.servoy.j2db.util.MimeTypes;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.Utils;

/**
 * Servlet for mapping RESTfull Web Service request to Servoy methods.
 * <p>
 * Resources are addressed via path
 *
 * <pre>
 * /servoy-service/rest_ws/mysolution/myform/arg1/arg2
 * </pre>.
 * <p>
 * HTTP methods are
 * <ul>
 * <li>POST<br>
 * call the method mysolution.myform.ws_create(post-data), return the method result in the response
 * <li>GET<br>
 * call the method mysolution.myform.ws_read(args), return the method result in the response or set status NOT_FOUND when null was returned
 * <li>UPDATE<br>
 * call the method mysolution.myform.ws_update(post-data, args), set status NOT_FOUND when FALSE was returned
 * <li>PATCH<br>
 * call the method mysolution.myform.ws_patch(patch-data, args), set status NOT_FOUND when FALSE was returned
 * <li>DELETE<br>
 * call the method mysolution.myform.ws_delete(args), set status NOT_FOUND when FALSE was returned
 * </ul>
 *
 * <p>
 * The solution is opened via a Servoy Headless Client which is shared across multiple requests, requests are assumed to be stateless. Clients are managed via a
 * pool, 1 client per concurrent request is used.
 *
 * @author rgansevles
 *
 */
@SuppressWarnings("nls")
public class RestWSServlet extends HttpServlet
{
	// solution method names
	private static final String WS_UPDATE = "ws_update";
	private static final String WS_PATCH = "ws_patch";
	private static final String WS_CREATE = "ws_create";
	private static final String WS_DELETE = "ws_delete";
	private static final String WS_READ = "ws_read";
	private static final String WS_AUTHENTICATE = "ws_authenticate";
	private static final String WS_RESPONSE_HEADERS = "ws_response_headers";
	private static final String WS_NODEBUG_HEADER = "servoy.nodebug";
	private static final String WS_USER_PROPERTIES_HEADER = "servoy.userproperties";
	private static final String WS_USER_PROPERTIES_COOKIE_PREFIX = "servoy.userproperty.";

	private static final int CONTENT_OTHER = 0;
	private static final int CONTENT_JSON = 1;
	private static final int CONTENT_XML = 2;
	private static final int CONTENT_BINARY = 3;
	private static final int CONTENT_MULTIPART = 4;
	private static final int CONTENT_TEXT = 5;
	private static final int CONTENT_FORMPOST = 6;

	private static final int CONTENT_DEFAULT = CONTENT_JSON;
	private static final String CHARSET_DEFAULT = "UTF-8";

	/**
	 * Just a convention used by Servoy in ws_response_headers() return value to define the name/key of a header to be returned. (must be String)
	 */
	private static final String HEADER_NAME = "name";
	/**
	 * Just a convention used by Servoy in ws_response_headers() return value to define the value of a header to be returned. (must be String)
	 */
	private static final String HEADER_VALUE = "value";

	private final RestWSPlugin plugin;

	private final String webServiceName;

	private final FileCleaningTracker FILE_CLEANING_TRACKER = new FileCleaningTracker();
	private final DiskFileItemFactory diskFileItemFactory;

	public RestWSServlet(String webServiceName, RestWSPlugin restWSPlugin)
	{
		this.webServiceName = webServiceName;
		this.plugin = restWSPlugin;

		String uploadDir = plugin.getServerAccess().getSettings().getProperty("servoy.ng_web_client.temp.uploadir");
		File fileUploadDir = null;
		if (uploadDir != null)
		{
			fileUploadDir = new File(uploadDir);
			if (!fileUploadDir.exists() && !fileUploadDir.mkdirs())
			{
				fileUploadDir = null;
				Debug.error("Couldn't use the property 'servoy.ng_web_client.temp.uploadir' value: '" + uploadDir +
					"', directory could not be created or doesn't exists");
			}
		}
		int tempFileThreshold = Utils.getAsInteger(plugin.getServerAccess().getSettings().getProperty("servoy.ng_web_client.tempfile.threshold", "50"), false) *
			1000;
		diskFileItemFactory = new DiskFileItemFactory(tempFileThreshold, fileUploadDir);
		diskFileItemFactory.setFileCleaningTracker(FILE_CLEANING_TRACKER);
	}

	@Override
	public void destroy()
	{
		FILE_CLEANING_TRACKER.exitWhenFinished();
		super.destroy();
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		RestWSServletResponse restWSServletResponse = new RestWSServletResponse(response);

		String value = request.getHeader("Origin");
		if (value == null)
		{
			value = "*";
		}
		restWSServletResponse.setHeader("Access-Control-Allow-Origin", value);
		restWSServletResponse.setHeader("Access-Control-Max-Age", "1728000");
		restWSServletResponse.setHeader("Access-Control-Allow-Credentials", "true");

		if (request.getHeader("Access-Control-Request-Method") != null)
		{
			restWSServletResponse.setHeader("Access-Control-Allow-Methods", "GET, DELETE, POST, PUT, OPTIONS");
		}

		if (getNodebugHeadderValue(request))
		{
			restWSServletResponse.setHeader("Access-Control-Expose-Headers", WS_NODEBUG_HEADER + ", " + WS_USER_PROPERTIES_HEADER);
		}
		else
		{
			restWSServletResponse.setHeader("Access-Control-Expose-Headers", WS_USER_PROPERTIES_HEADER);
		}
		value = request.getHeader("Access-Control-Request-Headers");
		if (value != null)
		{
			restWSServletResponse.setHeader("Access-Control-Allow-Headers", value);
		}

		if (request.getMethod().equals("PATCH"))
		{
			doPatch(request, restWSServletResponse);
		}
		else
		{
			super.service(request, restWSServletResponse);
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Pair<IHeadlessClient, String> client = null;
		boolean reloadSolution = plugin.shouldReloadSolutionAfterRequest();
		try
		{
			plugin.log.trace("GET");
			client = getClient(request);
			Object result = wsService(WS_READ, null, request, response, client.getLeft());
			if (result == null)
			{
				sendError(response, HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			HTTPUtils.setNoCacheHeaders(response);
			sendResult(request, response, result, CONTENT_DEFAULT);
		}
		catch (ExecFailedException e)
		{
			handleException(e.getCause(), request, response, client != null ? client.getLeft() : null);
			// do not reload solution when the error was thrown in solution code
			if (!reloadSolution) reloadSolution = !e.isUserScriptException();
		}
		catch (Exception e)
		{
			handleException(e, request, response, client != null ? client.getLeft() : null);
		}
		finally
		{
			if (client != null)
			{
				plugin.releaseClient(client.getRight(), client.getLeft(), reloadSolution);
			}
		}
	}

	/**
	 *
	 * @param request HttpServletRequest
	 * @return  a pair of IHeadlessClient object and the keyname from the objectpool ( the keyname depends if it nodebug mode is enabled)
	 * @throws Exception
	 */
	private Pair<IHeadlessClient, String> getClient(HttpServletRequest request) throws Exception
	{
		WsRequestPath wsRequestPath = parsePath(request);
		boolean nodebug = getNodebugHeadderValue(request);
		String solutionName = nodebug ? wsRequestPath.solutionName + ":nodebug" : wsRequestPath.solutionName;
		IHeadlessClient client;
		try
		{
			client = plugin.getClient(solutionName.toString());
		}
		catch (IllegalArgumentException e)
		{
			// solution not found
			throw new NoClientsException();
		}
		return new Pair<IHeadlessClient, String>(client, solutionName);
	}

	private void handleException(Exception e, HttpServletRequest request, HttpServletResponse response, IHeadlessClient headlessClient) throws IOException
	{
		final int errorCode;
		String errorResponse = null;
		if (e instanceof NotAuthenticatedException)
		{
			if (plugin.log.isDebugEnabled()) plugin.log.debug(request.getRequestURI() + ": Not authenticated");
			response.setHeader("WWW-Authenticate", "Basic realm=\"" + ((NotAuthenticatedException)e).getRealm() + '"');
			errorCode = HttpServletResponse.SC_UNAUTHORIZED;
		}
		else if (e instanceof NotAuthorizedException)
		{
			plugin.log.info(request.getRequestURI() + ": Not authorised: " + e.getMessage());
			errorCode = HttpServletResponse.SC_FORBIDDEN;
		}
		else if (e instanceof NoClientsException)
		{
			plugin.log.error(
				"Client could not be found. Possible reasons: 1.Client could not be created due to maximum number of licenses reached. 2.Client could not be created due to property mustAuthenticate=true in ws solution. 3.The client pool reached maximum number of clients. 4.An internal error occured. " +
					request.getRequestURI(),
				e);
			errorCode = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
		}
		else if (e instanceof IllegalArgumentException)
		{
			plugin.log.info("Could not parse path '" + e.getMessage() + '\'');
			errorCode = HttpServletResponse.SC_BAD_REQUEST;
		}
		else if (e instanceof WebServiceException)
		{
			plugin.log.info(request.getRequestURI(), e);
			errorCode = ((WebServiceException)e).httpResponseCode;
		}
		else if (e instanceof JavaScriptException)
		{
			plugin.log.info("ws_ method threw an exception '" + e.getMessage() + '\'');
			if (((JavaScriptException)e).getValue() instanceof Double)
			{
				errorCode = ((Double)((JavaScriptException)e).getValue()).intValue();

			}
			else if (((JavaScriptException)e).getValue() instanceof Wrapper && ((Wrapper)((JavaScriptException)e).getValue()).unwrap() instanceof Object[])
			{
				Object[] throwval = (Object[])((Wrapper)((JavaScriptException)e).getValue()).unwrap();
				errorCode = Utils.getAsInteger(throwval[0]);
				errorResponse = throwval[1] != null ? throwval[1].toString() : null;
			}
			else
			{
				if (headlessClient != null) headlessClient.getPluginAccess().reportError("Error executing rest call", e);
				errorCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			}
		}
		else
		{
			plugin.log.error(request.getRequestURI(), e);
			if (headlessClient != null) headlessClient.getPluginAccess().reportError("Error executing rest call", e);
			errorCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		sendError(response, errorCode, errorResponse);
	}

	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Pair<IHeadlessClient, String> client = null;
		boolean reloadSolution = plugin.shouldReloadSolutionAfterRequest();
		try
		{
			plugin.log.trace("DELETE");

			client = getClient(request);
			if (Boolean.FALSE.equals(wsService(WS_DELETE, null, request, response, client.getLeft())))
			{
				sendError(response, HttpServletResponse.SC_NOT_FOUND);
			}
			HTTPUtils.setNoCacheHeaders(response);
		}
		catch (ExecFailedException e)
		{
			handleException(e.getCause(), request, response, client != null ? client.getLeft() : null);
			// do not reload solution when the error was thrown in solution code
			if (!reloadSolution) reloadSolution = !e.isUserScriptException();
		}
		catch (Exception e)
		{
			handleException(e, request, response, client != null ? client.getLeft() : null);
		}
		finally
		{
			if (client != null)
			{
				plugin.releaseClient(client.getRight(), client.getLeft(), reloadSolution);
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Pair<IHeadlessClient, String> client = null;
		boolean reloadSolution = plugin.shouldReloadSolutionAfterRequest();
		try
		{
			FileItem contents = null;
			int contentType = CONTENT_OTHER;
			// if it is a mult part don't read in the body, thats done later on by file-upload
			if (ServletFileUpload.isMultipartContent(request))
			{
				contentType = CONTENT_MULTIPART;
			}
			else
			{
				contents = getBody(request);
				if (contents != null && contents.getSize() != 0)
				{
					contentType = getRequestContentType(request, "Content-Type", contents, CONTENT_OTHER);
					if (contentType == CONTENT_OTHER && contents != null)
					{
						sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
						return;
					}
				}
			}
			client = getClient(request);
			String charset = getHeaderKey(request.getHeader("Content-Type"), "charset", CHARSET_DEFAULT);
			Object result = wsService(WS_CREATE, new Object[] { decodeContent(request.getContentType(), contentType, contents, charset, request) }, request,
				response,
				client.getLeft());
			HTTPUtils.setNoCacheHeaders(response);
			if (result != null && result != Undefined.instance)
			{
				sendResult(request, response, result, contentType);
			}
		}
		catch (ExecFailedException e)
		{
			handleException(e.getCause(), request, response, client != null ? client.getLeft() : null);
			// do not reload solution when the error was thrown in solution code
			if (!reloadSolution) reloadSolution = !e.isUserScriptException();
		}
		catch (Exception e)
		{
			handleException(e, request, response, client != null ? client.getLeft() : null);
		}
		finally
		{
			if (client != null)
			{
				plugin.releaseClient(client.getRight(), client.getLeft(), reloadSolution);
			}
		}
	}

	@Override
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Pair<IHeadlessClient, String> client = null;
		boolean reloadSolution = plugin.shouldReloadSolutionAfterRequest();
		try
		{
			FileItem contents = null;
			int contentType = CONTENT_OTHER;
			// if it is a mult part don't read in the body, thats done later on by file-upload
			if (ServletFileUpload.isMultipartContent(request))
			{
				contentType = CONTENT_MULTIPART;
			}
			else
			{
				contents = getBody(request);
				if (contents == null || contents.getSize() == 0)
				{
					sendError(response, HttpServletResponse.SC_NO_CONTENT);
					return;
				}
				contentType = getRequestContentType(request, "Content-Type", contents, CONTENT_OTHER);
				if (contentType == CONTENT_OTHER)
				{
					sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
					return;
				}
			}
			client = getClient(request);
			String charset = getHeaderKey(request.getHeader("Content-Type"), "charset", CHARSET_DEFAULT);
			Object result = wsService(WS_UPDATE, new Object[] { decodeContent(request.getContentType(), contentType, contents, charset, request) }, request,
				response,
				client.getLeft());
			if (Boolean.FALSE.equals(result))
			{
				sendError(response, HttpServletResponse.SC_NOT_FOUND);
			}
			else
			{
				sendResult(request, response, result, contentType);
			}
			HTTPUtils.setNoCacheHeaders(response);
		}
		catch (ExecFailedException e)
		{
			handleException(e.getCause(), request, response, client != null ? client.getLeft() : null);
			// do not reload solution when the error was thrown in solution code
			if (!reloadSolution) reloadSolution = !e.isUserScriptException();
		}
		catch (Exception e)
		{
			handleException(e, request, response, client != null ? client.getLeft() : null);
		}
		finally
		{
			if (client != null)
			{
				plugin.releaseClient(client.getRight(), client.getLeft(), reloadSolution);
			}
		}
	}

	private void doPatch(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		Pair<IHeadlessClient, String> client = null;
		boolean reloadSolution = plugin.shouldReloadSolutionAfterRequest();
		try
		{
			FileItem contents = null;
			int contentType = CONTENT_OTHER;
			// if it is a mult part don't read in the body, thats done later on by file-upload
			if (ServletFileUpload.isMultipartContent(request))
			{
				contentType = CONTENT_MULTIPART;
			}
			else
			{
				contents = getBody(request);
				if (contents == null || contents.getSize() == 0)
				{
					sendError(response, HttpServletResponse.SC_NO_CONTENT);
					return;
				}
				contentType = getRequestContentType(request, "Content-Type", contents, CONTENT_OTHER);
				if (contentType == CONTENT_OTHER)
				{
					sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
					return;
				}
			}
			client = getClient(request);
			String charset = getHeaderKey(request.getHeader("Content-Type"), "charset", CHARSET_DEFAULT);
			Object result = wsService(WS_PATCH, new Object[] { decodeContent(request.getContentType(), contentType, contents, charset, request) }, request,
				response,
				client.getLeft());
			if (Boolean.FALSE.equals(result))
			{
				sendError(response, HttpServletResponse.SC_NOT_FOUND);
			}
			else
			{
				sendResult(request, response, result, contentType);
			}
			HTTPUtils.setNoCacheHeaders(response);
		}
		catch (ExecFailedException e)
		{
			handleException(e.getCause(), request, response, client != null ? client.getLeft() : null);
			// do not reload solution when the error was thrown in solution code
			if (!reloadSolution) reloadSolution = !e.isUserScriptException();
		}
		catch (Exception e)
		{
			handleException(e, request, response, client != null ? client.getLeft() : null);
		}
		finally
		{
			if (client != null)
			{
				plugin.releaseClient(client.getRight(), client.getLeft(), reloadSolution);
			}
		}
	}

	@Override
	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		IHeadlessClient client = null;
		WsRequestPath wsRequestPath = null;
		boolean nodebug = getNodebugHeadderValue(request);
		boolean reloadSolution = plugin.shouldReloadSolutionAfterRequest();
		try
		{
			plugin.log.trace("OPTIONS");
			wsRequestPath = parsePath(request);

			client = getClient(request).getLeft();

			setApplicationUserProperties(request, client.getPluginAccess());
			String retval = "TRACE, OPTIONS";
			if (testWsMethod(client, wsRequestPath, WS_READ))
			{
				retval += ", GET";
			}
			//TODO: implement HEAD?
			retval += ", HEAD";
			if (testWsMethod(client, wsRequestPath, WS_CREATE))
			{
				retval += ", POST";
			}
			if (testWsMethod(client, wsRequestPath, WS_UPDATE))
			{
				retval += ", PUT";
			}
			if (testWsMethod(client, wsRequestPath, WS_PATCH))
			{
				retval += ", PATCH";
			}
			if (testWsMethod(client, wsRequestPath, WS_DELETE))
			{
				retval += ", DELETE";
			}

			response.setHeader("Allow", retval);

			String value = request.getHeader("Access-Control-Request-Headers");
			if (value == null)
			{
				value = "Allow";
			}
			else if (!value.contains("Allow"))
			{
				value += ", Allow";
			}
			response.setHeader("Access-Control-Allow-Headers", value);
			response.setHeader("Access-Control-Expose-Headers", value + ", " + WS_NODEBUG_HEADER + ", " + WS_USER_PROPERTIES_HEADER);
			setResponseUserProperties(request, response, client.getPluginAccess());
		}
		catch (ExecFailedException e)
		{
			handleException(e.getCause(), request, response, client);
			// do not reload solution when the error was thrown in solution code
			if (!reloadSolution) reloadSolution = !e.isUserScriptException();
		}
		catch (Exception e)
		{
			handleException(e, request, response, client);
		}
		finally
		{
			if (client != null)
			{
				plugin.releaseClient(nodebug ? wsRequestPath.solutionName + ":nodebug" : wsRequestPath.solutionName, client, reloadSolution);
			}
		}
	}

	/**
	 * @param client
	 * @param wsRequestPath
	 * @return
	 */
	private static boolean testWsMethod(IHeadlessClient client, WsRequestPath wsRequestPath, String wsMethod)
	{
		String context = getContext(client, wsRequestPath.scope_or_form);
		Pair<FunctionDefinition, String[]> call = getFunctioncall(wsMethod, client.getPluginAccess(), wsRequestPath, context);
		return call.getLeft().exists(client.getPluginAccess()) == FunctionDefinition.Exist.METHOD_FOUND;
	}

	public WsRequestPath parsePath(HttpServletRequest request)
	{
		String path = request.getPathInfo(); //without servlet name

		if (plugin.log.isDebugEnabled()) plugin.log.debug("Request '" + path + '\'');

		// parse the path: servoy-service/webServiceName/mysolution/myform_or_scope/arg1/arg2/...
		// parse the path: servoy-service/webServiceName/mysolution/myform_or_scope/methodname/arg1/arg2/...
		// parse the path: servoy-service/webServiceName/mysolution/v2/myform_or_scope/arg1/arg2/...
		// parse the path: servoy-service/webServiceName/mysolution/v2/myform_or_scope/methodname/arg1/arg2/...
		String[] segments = path == null ? null : path.split("/");
		if (segments == null || segments.length < 4 || !webServiceName.equals(segments[1]))
		{
			throw new IllegalArgumentException(path);
		}

		String version = null;
		int argumentsStart = 2;
		// if segment 2 is a "vX", so starts with a v and then an integer then it is a version string, should be appended to the scope or form name
		if (isVersionSegment(segments, argumentsStart))
		{
			version = segments[argumentsStart++];
		}
		String solution = segments[argumentsStart++];
		// if segment 3 is a "vX", so starts with a v and then an integer then it is a version string, should be appended to the scope or form name
		if (isVersionSegment(segments, argumentsStart))
		{
			version = segments[argumentsStart++];
		}
		String scope_or_form = segments[argumentsStart++];

		if (version != null)
		{
			scope_or_form = scope_or_form + "_" + version;
		}
		return new WsRequestPath(solution, scope_or_form, Utils.arraySub(segments, argumentsStart, segments.length));
	}

	/**
	 *
	 * @param segments
	 * @param index
	 */
	private static boolean isVersionSegment(String[] segments, int index)
	{
		String segment = segments[index];
		return segment.startsWith("v") && Utils.getAsInteger(segment.substring(1), -1) != -1 && index < segments.length + 1;
	}

	/**
	 * call the service method, make the request and response available for the client-plugin.
	 * Throws {@link NoClientsException} when no license is available
	 * @param wsMethod
	 * @param fixedArgs
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	private Object wsService(String wsMethod, Object[] fixedArgs, HttpServletRequest request, HttpServletResponse response, IHeadlessClient client)
		throws Exception
	{
		RestWSClientPlugin clientPlugin = (RestWSClientPlugin)client.getPluginAccess().getPluginManager().getPlugin(IClientPlugin.class,
			RestWSClientPlugin.PLUGIN_NAME);
		try
		{
			if (clientPlugin == null)
			{
				plugin.log.warn("Could not find client plugin " + RestWSClientPlugin.PLUGIN_NAME);
			}
			else
			{
				clientPlugin.setRequestResponse(request, response);
			}
			return doWsService(wsMethod, fixedArgs, request, response, client, clientPlugin.isSendUserPropertiesHeaders());
		}
		finally
		{
			if (clientPlugin != null)
			{
				clientPlugin.setRequestResponse(null, null);
			}
		}
	}

	private Object doWsService(String wsMethod, Object[] fixedArgs, HttpServletRequest request, HttpServletResponse response, IHeadlessClient client,
		boolean sendUserProperties)
		throws Exception
	{
		// update cookies in the application from request
		setApplicationUserProperties(request, client.getPluginAccess());
		String path = request.getPathInfo(); // without servlet name

		if (plugin.log.isDebugEnabled()) plugin.log.debug("Request '" + path + '\'');

		WsRequestPath wsRequestPath = parsePath(request);

		Object ws_authenticate_result = checkAuthorization(request, client, wsRequestPath.solutionName, wsRequestPath.scope_or_form);

		String context = getContext(client, wsRequestPath.scope_or_form);

		Pair<FunctionDefinition, String[]> functionCall = getExistingFunctioncall(wsMethod, request.getPathInfo(), client.getPluginAccess(), wsRequestPath,
			context);

		FunctionDefinition fd_headers = new FunctionDefinition(context, WS_RESPONSE_HEADERS);
		if (fd_headers.exists(client.getPluginAccess()) == FunctionDefinition.Exist.METHOD_FOUND)
		{
			Object result = fd_headers.executeSync(client.getPluginAccess(), null);

			if (result instanceof Object[])
			{
				Object[] resultArray = (Object[])result;
				for (Object element : resultArray)
				{
					addHeaderToResponse(response, element, wsRequestPath);
				}
			}
			else addHeaderToResponse(response, result, wsRequestPath);
		}

		Object[] methodArgs = functionCall.getRight();
		Object[] args = null;
		if (fixedArgs != null || methodArgs.length > 0 || request.getParameterMap().size() > 0 || ws_authenticate_result != null)
		{
			args = new Object[((fixedArgs == null) ? 0 : fixedArgs.length) + methodArgs.length +
				((request.getParameterMap().size() > 0 || ws_authenticate_result != null) ? 1 : 0)];
			int idx = 0;
			if (fixedArgs != null)
			{
				System.arraycopy(fixedArgs, 0, args, 0, fixedArgs.length);
				idx += fixedArgs.length;
			}
			if (methodArgs.length > 0)
			{
				System.arraycopy(methodArgs, 0, args, idx, methodArgs.length);
				idx += methodArgs.length;
			}
			if (request.getParameterMap().size() > 0 || ws_authenticate_result != null)
			{
				JSMap<String, Object> jsMap = new JSMap<String, Object>();
				jsMap.putAll(request.getParameterMap());
				if (ws_authenticate_result != null)
				{
					jsMap.put(WS_AUTHENTICATE, new Object[] { ws_authenticate_result });
				}
				args[idx++] = jsMap;
			}
		}

		if (plugin.log.isDebugEnabled()) plugin.log.debug("executeMethod('" + context + "', '" + wsMethod + "', <args>)");
		// DO NOT USE FunctionDefinition here! we want to be able to catch possible exceptions!
		Object result;
		try
		{
			result = client.getPluginAccess().executeMethod(context, functionCall.getLeft().getMethodName(), args, false);
		}
		catch (Exception e)
		{
			plugin.log.info("Method execution failed: executeMethod('" + context + "', '" + functionCall.getLeft().getMethodName() + "', <args>)", e);
			throw new ExecFailedException(e);
		}
		if (plugin.log.isDebugEnabled()) plugin.log.debug("result = " + (result == null ? "<NULL>" : ("'" + result + '\'')));
		// flush updated cookies from the application
		if (sendUserProperties) setResponseUserProperties(request, response, client.getPluginAccess());
		return result;
	}

	/**
	 * @param wsMethod
	 * @param pathInfo
	 * @param pluginAccess
	 * @param wsRequestPath
	 * @param context
	 * @return
	 * @throws WebServiceException
	 */
	private static Pair<FunctionDefinition, String[]> getExistingFunctioncall(String wsMethod, String pathInfo, IClientPluginAccess pluginAccess,
		WsRequestPath wsRequestPath, String context) throws WebServiceException
	{
		Pair<FunctionDefinition, String[]> call = getFunctioncall(wsMethod, pluginAccess, wsRequestPath, context);

		Exist functionExists = call.getLeft().exists(pluginAccess);
		if (functionExists == FunctionDefinition.Exist.NO_SOLUTION)
		{
			throw new WebServiceException("Solution " + wsRequestPath.solutionName + " not loaded", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
		if (functionExists != FunctionDefinition.Exist.METHOD_FOUND)
		{
			throw new WebServiceException(
				"Path " + pathInfo + " not found" + (wsRequestPath.scope_or_form != null ? " for form or scope " + wsRequestPath.scope_or_form : ""),
				HttpServletResponse.SC_NOT_FOUND);
		}
		return call;
	}

	/**
	 * @param wsMethod
	 * @param pluginAccess
	 * @param wsRequestPath
	 * @param context
	 * @return
	 */
	private static Pair<FunctionDefinition, String[]> getFunctioncall(String wsMethod, IClientPluginAccess pluginAccess, WsRequestPath wsRequestPath,
		String context)
	{
		return wsRequestPath.getPossibleMethods() //
			.map(method -> new Pair<>(new FunctionDefinition(context, wsMethod + method.name), method.getArgs())) //
			.filter(pair -> pair.getLeft().exists(pluginAccess) == FunctionDefinition.Exist.METHOD_FOUND) //
			.findFirst() //
			.orElseGet(() -> new Pair<>(new FunctionDefinition(context, wsMethod), wsRequestPath.args));
	}

	private static String getContext(IHeadlessClient client, String scope_or_form)
	{
		String[] retVal = new String[] { scope_or_form };
		client.invokeAndWait(() -> {
			if (client.getPluginAccess().getFormManager().getForm(scope_or_form) == null)
			{
				// the form is not found, test then as a scope.
				retVal[0] = ScriptVariable.SCOPES_DOT_PREFIX + scope_or_form;
			}
		});
		return retVal[0];
	}

	private void addHeaderToResponse(HttpServletResponse response, Object headerItem, WsRequestPath wsRequestPath)
	{
		boolean done = false;
		if (headerItem instanceof String)
		{
			// something like 'Content-Disposition=attachment;filename="test.txt"'
			String headerString = (String)headerItem;
			int equalSignIndex = headerString.indexOf('=');
			if (equalSignIndex > 0)
			{
				response.addHeader(headerString.substring(0, equalSignIndex), headerString.substring(equalSignIndex + 1));
				done = true;
			}
		}
		else if (headerItem instanceof Scriptable)
		{
			// something like {
			// 			name: "Content-Disposition",
			// 			value: 'attachment;filename="test.txt"'
			// }
			Scriptable headerItemObject = (Scriptable)headerItem;
			if (headerItemObject.has(HEADER_NAME, headerItemObject) && headerItemObject.has(HEADER_VALUE, headerItemObject))
			{
				response.addHeader(String.valueOf(headerItemObject.get(HEADER_NAME, headerItemObject)),
					String.valueOf(headerItemObject.get(HEADER_VALUE, headerItemObject)));
				done = true;
			}
		}

		if (!done) Debug.error(
			"Cannot send back header value from 'ws_response_headers'; it should be either a String containing a key-value pair separated by an equal sign or an object with 'name' and 'value' in it, but it is: '" +
				headerItem + "'. Solution/form/scope: " + wsRequestPath.solutionName + " -> " + wsRequestPath.scope_or_form);
	}

	private Object checkAuthorization(HttpServletRequest request, IHeadlessClient client, String solutionName, String scope_or_form) throws Exception
	{
		String context = getContext(client, scope_or_form);

		String[] authorizedGroups = plugin.getAuthorizedGroups();
		FunctionDefinition fd = new FunctionDefinition(context, WS_AUTHENTICATE);
		Exist authMethodExists = fd.exists(client.getPluginAccess());
		if (authorizedGroups == null && authMethodExists != FunctionDefinition.Exist.METHOD_FOUND)
		{
			plugin.log.debug("No authorization to check, allow all access");
			return null;
		}

		//Process authentication Header
		String authorizationHeader = request.getHeader("Authorization");
		String user = null;
		String password = null;
		if (authorizationHeader != null)
		{
			if (authorizationHeader.toLowerCase().startsWith("basic "))
			{
				String authorization = authorizationHeader.substring(6);
				// TODO: which encoding to use? see http://tools.ietf.org/id/draft-reschke-basicauth-enc-05.xml
				// we assume now we get UTF-8 , we need to define a standard due to mobile client usage
				authorization = new String(Utils.decodeBASE64(authorization), "UTF-8");
				int index = authorization.indexOf(':');
				if (index > 0)
				{
					user = authorization.substring(0, index);
					password = authorization.substring(index + 1);
				}
			}
			else
			{
				plugin.log.debug("No or unsupported Authorization header");
			}
		}
		else
		{
			plugin.log.debug("No Authorization header");
		}

		if (user == null || password == null || user.trim().length() == 0 || password.trim().length() == 0)
		{
			plugin.log.debug("No credentials to proceed with authentication");
			throw new NotAuthenticatedException(solutionName);
		}

		//Process the Authentication Header values
		if (authMethodExists == FunctionDefinition.Exist.METHOD_FOUND)
		{
			//TODO: we should cache the (user,pass,retval) for an hour (across all rest clients), and not invoke WS_AUTHENTICATE function each time! (since authenticate might be expensive like LDAP)
			Object retval = fd.executeSync(client.getPluginAccess(), new String[] { user, password });
			if (retval != null && !Boolean.FALSE.equals(retval) && retval != Undefined.instance)
			{
				return retval instanceof Boolean ? null : retval;
			}
			if (plugin.log.isDebugEnabled()) plugin.log.debug("Authentication method " + WS_AUTHENTICATE + " denied authentication");
			throw new NotAuthenticatedException(solutionName);
		}

		String userUid = plugin.getServerAccess().checkPasswordForUserName(user, password);
		if (userUid == null)
		{
			plugin.log.debug("Supplied credentails not valid");
			throw new NotAuthenticatedException(user);
		}

		String[] userGroups = plugin.getServerAccess().getUserGroups(userUid);
		// find a match in groups
		if (userGroups != null)
		{
			for (String ug : userGroups)
			{
				for (String ag : authorizedGroups)
				{
					if (ag.trim().equals(ug))
					{
						if (plugin.log.isDebugEnabled())
						{
							plugin.log.debug("Authorized access for user " + user + ", group " + ug);
						}
						return null;
					}
				}
			}
		}

		// no match
		throw new NotAuthorizedException("User not authorized");
	}

	private FileItem getBody(HttpServletRequest request) throws IOException
	{
		return createFileItem(request.getInputStream());
	}

	/**
	 * @param servletInputStream
	 * @return
	 * @throws IOException
	 */
	private FileItem createFileItem(InputStream inputStream) throws IOException
	{
		FileItem fileItem = diskFileItemFactory.createItem(null, null, false, "restws_" + UUID.randomUUID().toString().replace("-", "_"));
		IOUtils.copy(inputStream, fileItem.getOutputStream());
		return fileItem;
	}

	private int getContentType(String headerValue)
	{
		if (headerValue != null)
		{
			String header = headerValue.toLowerCase();
			if (header.indexOf("json") >= 0)
			{
				return CONTENT_JSON;
			}
			if (header.indexOf("vnd.openxmlformats-officedocument") >= 0)
			{
				//  note: this content type contains 'xml' but is not XML.
				return CONTENT_BINARY;
			}
			if (header.indexOf("xml") >= 0)
			{
				return CONTENT_XML;
			}
			if (header.indexOf("text") >= 0)
			{
				return CONTENT_TEXT;
			}
			if (header.indexOf("multipart") >= 0)
			{
				return CONTENT_MULTIPART;
			}
			if (header.indexOf("application/x-www-form-urlencoded") >= 0)
			{
				return CONTENT_FORMPOST;
			}
			if (header.indexOf("octet-stream") >= 0 || header.indexOf("application") >= 0)
			{
				return CONTENT_BINARY;
			}
		}

		return CONTENT_OTHER;
	}

	private int getRequestContentType(HttpServletRequest request, String header, FileItem contents, int defaultContentType) throws UnsupportedEncodingException
	{
		String contentTypeHeaderValue = request.getHeader(header);
		int contentType = getContentType(contentTypeHeaderValue);
		if (contentType != CONTENT_OTHER) return contentType;
		if (contents != null)
		{
			String stringContent = "";
			try (InputStream inputStream = contents.getInputStream())
			{
				// only read in the first 512 bytes.
				byte[] bytes = new byte[512];
				inputStream.read(bytes);
				String charset = getHeaderKey(request.getHeader("Content-Type"), "charset", CHARSET_DEFAULT);
				Charset cs = Charset.forName(charset);
				CharBuffer cb = CharBuffer.allocate(512);
				CoderResult decode = cs.newDecoder().decode(ByteBuffer.wrap(bytes), cb, false);
				if (decode == CoderResult.OVERFLOW || decode == CoderResult.UNDERFLOW)
				{
					cb.flip();
					stringContent = cb.toString();
				}
			}
			catch (Exception e)
			{
				// ignore and return default
			}
			return guessContentType(stringContent, defaultContentType);
		}
		return defaultContentType;
	}

	private int guessContentType(String stringContent, int defaultContentType)
	{
		if (stringContent != null && stringContent.length() > 0)
		{
			// start guessing....
			if (stringContent.charAt(0) == '<')
			{
				return CONTENT_XML;
			}
			if (stringContent.charAt(0) == '{')
			{
				return CONTENT_JSON;
			}
		}
		return defaultContentType;
	}

	/**
	 *
	 * Gets the key from a header . For example, the following header :<br/>
	 * <b>Content-Disposition: form-data; name="myFile"; filename="SomeRandomFile.txt"</b>
	 * <br/>
	 * calling getHeaderKey(header,"name","--") will return <b>myFile<b/>
	 */
	private String getHeaderKey(String header, String key, String defaultValue)
	{
		if (header != null)
		{
			String[] split = header.split("; *");
			for (String element : split)
			{
				if (element.toLowerCase().startsWith(key + "="))
				{
					String charset = element.substring(key.length() + 1);
					if (charset.length() > 1 && charset.charAt(0) == '"' && charset.charAt(charset.length() - 1) == '"')
					{
						charset = charset.substring(1, charset.length() - 1);
					}
					return charset;
				}
			}
		}
		return defaultValue;
	}

	/**
	 *  Gets the custom header's : servoy.userproperties  value and sets the user properties with its value.
	 *  This custom header simulates a session cookie.
	 *  happens at the beginning  of each request (before application is invoked)
	 */
	void setApplicationUserProperties(HttpServletRequest request, IClientPluginAccess client)
	{
		String headerValue = request.getHeader(WS_USER_PROPERTIES_HEADER);
		if (headerValue != null)
		{
			Map<String, String> map = new HashMap<String, String>();
			org.json.JSONObject object;
			try
			{
				object = new org.json.JSONObject(headerValue);
				for (Object key : Utils.iterate(object.keys()))
				{
					String value = object.getString((String)key);
					map.put((String)key, value);
				}
				client.setUserProperties(map);
			}
			catch (JSONException e)
			{
				Debug.error("cannot get json object from " + WS_USER_PROPERTIES_HEADER + " header: ", e);
			}
		}
		else
		{
			Cookie[] cookies = request.getCookies();
			Map<String, String> map = new HashMap<String, String>();
			if (cookies != null)
			{
				for (Cookie cookie : cookies)
				{
					String name = cookie.getName();
					if (name.startsWith(WS_USER_PROPERTIES_COOKIE_PREFIX))
					{
						String value = cookie.getValue();
						map.put(name.substring(WS_USER_PROPERTIES_COOKIE_PREFIX.length()), Utils.decodeCookieValue(value));
					}
				}
				client.setUserProperties(map);
			}
		}

	}

	/**
	 * Serializes user properties as a json string header   ("servoy.userproperties" header)
	 * AND besides the custom header they are also serialized cookies for non mobile clients
	 * @param request TODO
	 *
	 */
	void setResponseUserProperties(HttpServletRequest request, HttpServletResponse response, IClientPluginAccess client)
	{
		Map<String, String> map = client.getUserProperties();
		if (map.keySet().size() > 0)
		{
			// set custom header
			try
			{
				org.json.JSONStringer stringer = new org.json.JSONStringer();
				org.json.JSONWriter writer = stringer.object();
				for (String propName : map.keySet())
				{
					writer = writer.key(propName).value(map.get(propName));
				}
				writer.endObject();
				response.setHeader(WS_USER_PROPERTIES_HEADER, writer.toString());
			}
			catch (JSONException e)
			{
				Debug.error("cannot serialize json object to " + WS_USER_PROPERTIES_HEADER + " headder: ", e);
			}
			//set cookie
			for (String propName : map.keySet())
			{
				Cookie cookie = new Cookie(WS_USER_PROPERTIES_COOKIE_PREFIX + propName, Utils.encodeCookieValue(map.get(propName)));
				String ctxPath = request.getContextPath();
				if (ctxPath == null || ctxPath.equals("/") || ctxPath.length() < 1) ctxPath = "";
				cookie.setPath(ctxPath + request.getServletPath() + "/" + RestWSPlugin.WEBSERVICE_NAME + "/" + client.getSolutionName());
				if (request.isSecure()) cookie.setSecure(true);
				response.addCookie(cookie);
			}
		}
	}

	private Object decodeContent(String contentTypeStr, int contentType, FileItem contents, String charset, HttpServletRequest request) throws Exception
	{
		switch (contentType)
		{
			case CONTENT_JSON :
				return plugin.getJSONSerializer().fromJSON(contents.getString(charset));

			case CONTENT_XML :
				return plugin.getJSONSerializer().fromJSON(XML.toJSONObject(contents.getString(charset)));

			case CONTENT_MULTIPART :
				return getMultipartContent(request);

			case CONTENT_FORMPOST :
				return parseQueryString(contents.getString(charset));

			case CONTENT_BINARY :
				return plugin.useJSUploadForBinaryData() ? new JSUpload(contents, Collections.emptyMap()) : contents.get();

			case CONTENT_TEXT :
				return contents.getString(charset);

			case CONTENT_OTHER :
				return plugin.useJSUploadForBinaryData() ? new JSUpload(contents, Collections.emptyMap()) : contents.get();
		}

		// should not happen, content type was checked before
		throw new IllegalStateException();
	}

	private Object getMultipartContent(HttpServletRequest request) throws FileUploadException
	{
		ServletFileUpload upload = new ServletFileUpload(diskFileItemFactory);
		upload.setHeaderEncoding("UTF-8");
		long maxUpload = Utils.getAsLong(plugin.getServerAccess().getSettings().getProperty("servoy.webclient.maxuploadsize", "0"), false);
		if (maxUpload > 0) upload.setFileSizeMax(maxUpload * 1000);
		Iterator<FileItem> iterator = upload.parseRequest(request).iterator();
		ArrayList<JSMap<String, Object>> parts = new ArrayList<JSMap<String, Object>>();
		Map<String, String> formFields = new JSMap<>();
		while (iterator.hasNext())
		{
			FileItem item = iterator.next();
			if (item.isFormField())
			{
				String contentType = item.getContentType();
				String charset = getHeaderKey(contentType, "charset", null);
				if (charset == null) charset = getHeaderKey(request.getContentType(), "charset", null);
				if (charset != null)
				{
					try
					{
						formFields.put(item.getFieldName(), item.getString(charset));
					}
					catch (UnsupportedEncodingException e)
					{
						formFields.put(item.getFieldName(), item.getString());
					}
				}
				else
				{
					formFields.put(item.getFieldName(), item.getString());
				}
			}
			else
			{
				JSMap<String, Object> partObj = new JSMap<String, Object>();
				parts.add(partObj);
				//filename
				if (item.getName() != null) partObj.put("fileName", item.getName());
				String partContentType = "";
				//charset
				if (item.getContentType() != null) partContentType = item.getContentType();
				String _charset = getHeaderKey(partContentType, "charset", "");
				partContentType = partContentType.replaceAll("(.*?);\\s*\\w+=.*", "$1");
				//contentType
				if (partContentType.length() > 0)
				{
					partObj.put("contentType", partContentType);
				}
				if (_charset.length() > 0)
				{
					partObj.put("charset", _charset);
				}
				else
				{
					_charset = "UTF-8"; // still use a valid default encoding in case it's not specified for reading it - it is ok that it will not be reported to JS I guess (this happens almost all the time)
				}

				partObj.put("value", plugin.useJSUploadForBinaryData() ? new JSUpload(item, formFields) : item.get());
				formFields = new JSMap<>();

				// Get name header
				String nameHeader = "";
				Iterator<String> nameHeaders = item.getHeaders().getHeaders("Content-Disposition");
				if (nameHeaders != null)
				{
					while (nameHeaders.hasNext())
					{
						String name = getHeaderKey(nameHeaders.next(), "name", "");
						if (name.length() > 0) nameHeader = name;
						break;
					}
				}
				if (nameHeader.length() > 0) partObj.put("name", nameHeader);
			}
		}

		return parts.toArray();
	}

	private Object parseQueryString(String queryString)
	{
		List<JSMap<String, Object>> args = new ArrayList<>();
		List<NameValuePair> values = URLEncodedUtils.parse(queryString, Charset.forName("UTF-8"));
		for (NameValuePair pair : values)
		{
			// create an array of objects, similar to multipart form posts
			JSMap<String, Object> jsmap = new JSMap<String, Object>();
			jsmap.put("value", pair.getValue());
			jsmap.put("name", pair.getName());
			jsmap.put("contentType", "text/plain");
			args.add(jsmap);
		}

		return args.toArray();
	}

	private boolean getNodebugHeadderValue(HttpServletRequest request)
	{
		// when DOING cross to an url the browser first sends and extra options request with the request method  and
		//headers it will set ,before sending the actual request
		//http://stackoverflow.com/questions/1256593/jquery-why-am-i-getting-an-options-request-insted-of-a-get-request
		if (request.getMethod().equalsIgnoreCase("OPTIONS"))
		{
			String header = request.getHeader("Access-Control-Request-Headers");
			if (header != null && header.contains(WS_NODEBUG_HEADER))
			{
				return true;
			}
		}
		return request.getHeader(WS_NODEBUG_HEADER) != null;
	}

	private void sendResult(HttpServletRequest request, HttpServletResponse response, Object result, int defaultContentType) throws Exception
	{
		byte[] bytes;

		String charset;
		if (response instanceof RestWSServletResponse && ((RestWSServletResponse)response).characterEncodingSet)
		{
			// characterEncoding was set using rest_ws client plugin
			charset = response.getCharacterEncoding();
		}
		else
		{
			String contentTypeCharset = getHeaderKey(request.getHeader("Content-Type"), "charset", CHARSET_DEFAULT);
			charset = getHeaderKey(request.getHeader("Accept"), "charset", contentTypeCharset);
		}

		String resultContentType = response.getContentType();
		if (resultContentType != null)
		{
			// content type was set using rest_ws client plugin
			String content = getContent(response, result, false, getContentType(resultContentType));
			bytes = content.getBytes(charset);
		}
		else if (result instanceof byte[])
		{
			bytes = (byte[])result;
			resultContentType = getBytesContentType(request, bytes);
		}
		else if (result instanceof IFile)
		{
			resultContentType = ((IFile)result).getContentType();
			bytes = null;

			response.setHeader("Content-Type", resultContentType);
			response.setContentLengthLong(((IFile)result).getSize());

			try (ServletOutputStream os = response.getOutputStream(); InputStream is = ((IFile)result).getInputStream())
			{
				IOUtils.copyLarge(is, os);
				os.flush();
			}
			return;
		}
		else
		{
			int contentType = getRequestContentType(request, "Accept", null, defaultContentType);
			if (contentType == CONTENT_BINARY)
			{
				plugin.log.error("Request for binary data was made, but the return data is not a byte array; return data is " + result);
				sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				return;
			}

			String content = getContent(response, result, true, contentType);

			switch (contentType)
			{
				case CONTENT_JSON :
					String callback = request.getParameter("callback");
					if (callback != null && !callback.equals(""))
					{
						content = callback + '(' + content + ')';
					}
					break;

				case CONTENT_XML :
					content = "<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\n" + content;
					break;
				case CONTENT_MULTIPART :
				case CONTENT_FORMPOST :
					content = "";
					break;
				case CONTENT_TEXT :
				case CONTENT_OTHER : // if here still other then just send a string. could be a post without body
					content = result != null ? result.toString() : "";
					break;
				default :
					// how can this happen...
					throw new IllegalStateException();
			}

			switch (contentType)
			{
				// multipart requests cannot respond multipart responses so treat response as json
				case CONTENT_MULTIPART :
				case CONTENT_FORMPOST :
				case CONTENT_JSON :
					resultContentType = "application/json";
					break;

				case CONTENT_XML :
					resultContentType = "application/xml";
					break;
				case CONTENT_TEXT :
				case CONTENT_OTHER : // if here still other then just send a string. could be a post without body
					resultContentType = "text/plain";
					break;

				default :
					// how can this happen...
					throw new IllegalStateException();
			}

			resultContentType = resultContentType + ";charset=" + charset;

			bytes = content.getBytes(charset);

		}
		if (bytes != null)
		{
			response.setHeader("Content-Type", resultContentType);
			response.setContentLength(bytes.length);

			try (ServletOutputStream outputStream = response.getOutputStream())
			{
				outputStream.write(bytes);
				outputStream.flush();
			}
		}
	}

	/**
	 *
	 * @param response
	 * @param result
	 * @param interpretResult
	 * @param contentType
	 * @return
	 * @throws Exception
	 */
	private String getContent(HttpServletResponse response, Object result, boolean interpretResult, int contentType) throws Exception
	{
		if (result == null)
		{
			return "";
		}

		if (result instanceof XMLObject)
		{
			if (contentType == CONTENT_JSON)
			{
				return XML.toJSONObject(result.toString()).toString();
			}
			return result.toString();
		}

		if (contentType == CONTENT_XML)
		{
			if (result instanceof JSONObject || result instanceof JSONArray)
			{
				return XML.toString(result.toString(), null);
			}
			Object json = plugin.getJSONSerializer().toJSON(result);
			return XML.toString(json, null);
		}

		if (interpretResult && !(result instanceof JSONObject || result instanceof JSONArray))
		{
			try
			{
				return plugin.getJSONSerializer().toJSON(result).toString();
			}
			catch (Exception e)
			{
				Debug.error("Failed to convert " + result + " to a json structure", e);
				throw e;
			}
		}

		return result.toString();
	}

	private String getBytesContentType(HttpServletRequest request, byte[] bytes)
	{
		String resultContentType;
		resultContentType = MimeTypes.getContentType(bytes);

		if (request.getHeader("Accept") != null)
		{
			String[] acceptContentTypes = request.getHeader("Accept").split(",");

			if (resultContentType == null)
			{
				// cannot determine content type, just use first from accept header
				resultContentType = getFirstNonpatternContentType(acceptContentTypes);
				if (resultContentType != null && acceptContentTypes.length > 1)
				{
					plugin.log.warn("Could not determine byte array content type, using {} from accept header {}", resultContentType,
						request.getHeader("Accept"));
				}
			}

			if (resultContentType == null)
			{
				resultContentType = "application/octet-stream"; // if still null, then set to standard
			}

			// check if content type based on bytes is in accept header
			boolean found = false;
			for (String acc : acceptContentTypes)
			{
				if (matchContentType(acc.trim().split(";")[0], resultContentType))
				{
					found = true;
					break;
				}
			}

			if (!found)
			{
				plugin.log.warn("Byte array content type {} not found in accept header {}", resultContentType, request.getHeader("Accept"));
			}
		}

		if (resultContentType == null)
		{
			resultContentType = "application/octet-stream"; // if still null, then set to standard
		}

		return resultContentType;
	}

	private static String getFirstNonpatternContentType(String[] contentTypes)
	{
		for (String acc : contentTypes)
		{
			String contentType = acc.trim().split(";")[0];
			String[] split = contentType.split("/");
			if (split.length == 2 && !split[0].equals("*") && !split[1].equals("*"))
			{
				return contentType;
			}
		}
		return null;
	}

	private static boolean matchContentType(String contentTypePattern, String contentType)
	{
		String[] patSplit = contentTypePattern.split("/");
		if (patSplit.length != 2)
		{
			return false;
		}

		String[] typeSplit = contentType.split("/");
		if (typeSplit.length != 2)
		{
			return false;
		}

		return (patSplit[0].equals("*") || patSplit[0].equalsIgnoreCase(typeSplit[0])) &&
			(patSplit[1].equals("*") || patSplit[1].equalsIgnoreCase(typeSplit[1]));
	}

	/**
	 * Send the error response but prevent output of the default (html) error page
	 * @param response
	 * @param error
	 * @throws IOException
	 */
	private void sendError(HttpServletResponse response, int error) throws IOException
	{
		sendError(response, error, null);
	}

	/**
	 * Send the error response with specified error response msg
	 * @param response
	 * @param error
	 * @param errorResponse
	 * @throws IOException
	 */
	private void sendError(HttpServletResponse response, int error, String errorResponse) throws IOException
	{
		response.setStatus(error);
		if (errorResponse == null)
		{
			response.setContentLength(0);
		}
		else
		{
			int contentType = guessContentType(errorResponse, CONTENT_TEXT);
			switch (contentType)
			{
				case CONTENT_JSON :
					response.setContentType("application/json");
					break;

				case CONTENT_XML :
					response.setContentType("application/xml");
					break;

				default :
			}

			try (Writer w = response.getWriter())
			{
				w.write(errorResponse);
			}
		}
	}

	private static class RestWSServletResponse extends HttpServletResponseWrapper
	{
		boolean characterEncodingSet;

		public RestWSServletResponse(HttpServletResponse response)
		{
			super(response);
		}

		@Override
		public void setCharacterEncoding(String charset)
		{
			characterEncodingSet = true;
			super.setCharacterEncoding(charset);
		}
	}

	public static class WsRequestPath
	{
		public final String solutionName;
		public final String scope_or_form;
		public final String[] args;

		/**
		 * @param solutionName
		 * @param formName
		 * @param args
		 */
		public WsRequestPath(String solutionName, String scope_or_form, String[] args)
		{
			this.solutionName = solutionName;
			this.scope_or_form = scope_or_form;
			this.args = args;
		}

		public Stream<Method> getPossibleMethods()
		{
			if (args == null || args.length == 0) return Stream.empty();
			return IntStream.range(0, args.length).mapToObj(i -> new Method(args, i)).sorted();
		}

		@Override
		public String toString()
		{
			return "WsRequest [solutionName=" + solutionName + ", ScopeOrForm=" + scope_or_form + ", args=" + Arrays.toString(args) + "]";
		}
	}

	private final static class Method implements Comparable<Method>
	{
		public final String name;
		private final String[] args;
		private final int index;

		Method(String[] args, int index)
		{
			this.args = args;
			this.index = index;
			this.name = "_" + IntStream.rangeClosed(0, index).mapToObj(i -> args[i]).collect(Collectors.joining("_"));
		}

		String[] getArgs()
		{
			if (args.length <= index + 1) return new String[0];
			return Utils.arraySub(args, index + 1, args.length);
		}

		@Override
		public int compareTo(Method o)
		{
			return o.index - index;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (obj == this) return true;
			if (obj instanceof Method)
			{
				if (((Method)obj).index == index)
				{
					return Arrays.equals(args, ((Method)obj).args);
				}
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			return index;
		}
	}

	public static class WebServiceException extends Exception
	{
		public final int httpResponseCode;

		public WebServiceException(String message, int httpResponseCode)
		{
			super(message);
			this.httpResponseCode = httpResponseCode;
		}
	}
}
