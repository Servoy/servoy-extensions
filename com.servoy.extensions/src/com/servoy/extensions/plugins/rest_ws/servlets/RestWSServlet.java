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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.mail.BodyPart;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;
import org.mozilla.javascript.xml.XMLObject;

import com.servoy.extensions.plugins.rest_ws.RestWSPlugin;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin.ExecFailedException;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin.NoClientsException;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin.NotAuthenticatedException;
import com.servoy.extensions.plugins.rest_ws.RestWSPlugin.NotAuthorizedException;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.FunctionDefinition.Exist;
import com.servoy.j2db.scripting.JSMap;
import com.servoy.j2db.server.shared.IHeadlessClient;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.HTTPUtils;
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

	private static final int CONTENT_DEFAULT = CONTENT_JSON;
	private static final String CHARSET_DEFAULT = "UTF-8";

	private final RestWSPlugin plugin;

	private final String webServiceName;

	public RestWSServlet(String webServiceName, RestWSPlugin restWSPlugin)
	{
		this.webServiceName = webServiceName;
		this.plugin = restWSPlugin;
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		String value = request.getHeader("Origin");
		if (value == null)
		{
			value = "*";
		}
		response.setHeader("Access-Control-Allow-Origin", value);
		response.setHeader("Access-Control-Max-Age", "1728000");
		response.setHeader("Access-Control-Allow-Credentials", "true");

		if (request.getHeader("Access-Control-Request-Method") != null)
		{
			response.setHeader("Access-Control-Allow-Methods", "GET, DELETE, POST, PUT, OPTIONS");
		}

		if (getNodebugHeadderValue(request))
		{
			response.setHeader("Access-Control-Expose-Headers", WS_NODEBUG_HEADER + ", " + WS_USER_PROPERTIES_HEADER);
		}
		else
		{
			response.setHeader("Access-Control-Expose-Headers", WS_USER_PROPERTIES_HEADER);
		}
		value = request.getHeader("Access-Control-Request-Headers");
		if (value != null)
		{
			response.setHeader("Access-Control-Allow-Headers", value);
		}

		super.service(request, response);
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
		WsRequest wsRequest = parsePath(request);
		boolean nodebug = getNodebugHeadderValue(request);
		String solutionName = nodebug ? wsRequest.solutionName + ":nodebug" : wsRequest.solutionName;
		IHeadlessClient client = plugin.getClient(solutionName.toString());
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
				errorResponse = (String)throwval[1];
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
			byte[] contents = getBody(request);
			if (contents == null || contents.length == 0)
			{
				sendError(response, HttpServletResponse.SC_NO_CONTENT);
				return;
			}
			int contentType = getRequestContentType(request, "Content-Type", contents, CONTENT_OTHER);
			if (contentType == CONTENT_OTHER)
			{
				sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				return;
			}
			client = getClient(request);
			String charset = getHeaderKey(request.getHeader("Content-Type"), "charset", CHARSET_DEFAULT);
			Object result = wsService(WS_CREATE, new Object[] { decodeContent(request.getContentType(), contentType, contents, charset) }, request, response,
				client.getLeft());
			HTTPUtils.setNoCacheHeaders(response);
			if (result != null)
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
			byte[] contents = getBody(request);
			if (contents == null || contents.length == 0)
			{
				sendError(response, HttpServletResponse.SC_NO_CONTENT);
				return;
			}
			int contentType = getRequestContentType(request, "Content-Type", contents, CONTENT_OTHER);
			if (contentType == CONTENT_OTHER)
			{
				sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				return;
			}
			client = getClient(request);
			String charset = getHeaderKey(request.getHeader("Content-Type"), "charset", CHARSET_DEFAULT);
			Object result = wsService(WS_UPDATE, new Object[] { decodeContent(request.getContentType(), contentType, contents, charset) }, request, response,
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
		WsRequest wsRequest = null;
		boolean nodebug = getNodebugHeadderValue(request);
		boolean reloadSolution = plugin.shouldReloadSolutionAfterRequest();
		try
		{
			plugin.log.trace("OPTIONS");
			wsRequest = parsePath(request);

			client = plugin.getClient(nodebug ? wsRequest.solutionName + ":nodebug" : wsRequest.solutionName);
			setApplicationUserProperties(request, client.getPluginAccess());
			String retval = "TRACE, OPTIONS";
			if (new FunctionDefinition(wsRequest.formName, WS_READ).exists(client.getPluginAccess()) == FunctionDefinition.Exist.METHOD_FOUND)
			{
				retval += ", GET";
			}
			//TODO: implement HEAD?
			retval += ", HEAD";
			if (new FunctionDefinition(wsRequest.formName, WS_CREATE).exists(client.getPluginAccess()) == FunctionDefinition.Exist.METHOD_FOUND)
			{
				retval += ", POST";
			}
			if (new FunctionDefinition(wsRequest.formName, WS_UPDATE).exists(client.getPluginAccess()) == FunctionDefinition.Exist.METHOD_FOUND)
			{
				retval += ", PUT";
			}
			if (new FunctionDefinition(wsRequest.formName, WS_DELETE).exists(client.getPluginAccess()) == FunctionDefinition.Exist.METHOD_FOUND)
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
				plugin.releaseClient(nodebug ? wsRequest.solutionName + ":nodebug" : wsRequest.solutionName, client, reloadSolution);
			}
		}
	}

	public WsRequest parsePath(HttpServletRequest request)
	{
		String path = request.getPathInfo(); //without servlet name

		if (plugin.log.isDebugEnabled()) plugin.log.debug("Request '" + path + '\'');

		// parse the path: /webServiceName/mysolution/myform/arg1/arg2/...
		String[] segments = path == null ? null : path.split("/");
		if (segments == null || segments.length < 4 || !webServiceName.equals(segments[1]))
		{
			throw new IllegalArgumentException(path);
		}

		return new WsRequest(segments[2], segments[3], Utils.arraySub(segments, 4, segments.length));
	}

	/**
	 * call the service method.
	 * Throws {@link NoClientsException} when no license is available
	 * @param methodName
	 * @param fixedArgs
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	protected Object wsService(String methodName, Object[] fixedArgs, HttpServletRequest request, HttpServletResponse response, IHeadlessClient client)
		throws Exception
	{
		//update cookies in the application from request
		setApplicationUserProperties(request, client.getPluginAccess());
		String path = request.getPathInfo(); //without servlet name

		if (plugin.log.isDebugEnabled()) plugin.log.debug("Request '" + path + '\'');

		WsRequest wsRequest = parsePath(request);

		Object ws_authenticate_result = checkAuthorization(request, client.getPluginAccess(), wsRequest.solutionName, wsRequest.formName);

		FunctionDefinition fd = new FunctionDefinition(wsRequest.formName, methodName);
		Exist functionExists = fd.exists(client.getPluginAccess());
		if (functionExists == FunctionDefinition.Exist.NO_SOLUTION)
		{
			throw new WebServiceException("Solution " + wsRequest.solutionName + " not loaded", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
		if (functionExists == FunctionDefinition.Exist.FORM_NOT_FOUND)
		{
			throw new WebServiceException("Form " + wsRequest.formName + " not found", HttpServletResponse.SC_NOT_FOUND);
		}
		if (functionExists != FunctionDefinition.Exist.METHOD_FOUND)
		{
			throw new WebServiceException("Method " + methodName + " not found" + (wsRequest.formName != null ? " on form " + wsRequest.formName : ""),
				HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		}

		FunctionDefinition fd_headers = new FunctionDefinition(wsRequest.formName, WS_RESPONSE_HEADERS);
		if (fd_headers.exists(client.getPluginAccess()) == FunctionDefinition.Exist.METHOD_FOUND)
		{
			Object result = fd_headers.executeSync(client.getPluginAccess(), null);
			if (result instanceof String)
			{
				String[] l_r = String.valueOf(result).split("=");
				if (l_r.length == 2) response.addHeader(l_r[0], l_r[1]);
			}
			else if (result instanceof Object[])
			{
				Object[] resultArray = (Object[])result;
				for (Object element : resultArray)
				{
					String[] l_r = String.valueOf(element).split("=");
					if (l_r.length == 2) response.addHeader(l_r[0], l_r[1]);
				}
			}
		}

		Object[] args = null;
		if (fixedArgs != null || wsRequest.args.length > 0 || request.getParameterMap().size() > 0)
		{
			args = new Object[((fixedArgs == null) ? 0 : fixedArgs.length) + wsRequest.args.length +
				((request.getParameterMap().size() > 0 || ws_authenticate_result != null) ? 1 : 0)];
			int idx = 0;
			if (fixedArgs != null)
			{
				System.arraycopy(fixedArgs, 0, args, 0, fixedArgs.length);
				idx += fixedArgs.length;
			}
			if (wsRequest.args.length > 0)
			{
				System.arraycopy(wsRequest.args, 0, args, idx, wsRequest.args.length);
				idx += wsRequest.args.length;
			}
			if (request.getParameterMap().size() > 0 || ws_authenticate_result != null)
			{
				JSMap<String, Object> jsMap = new JSMap<String, Object>();
				Iterator<Entry<String, Object>> parameters = request.getParameterMap().entrySet().iterator();
				while (parameters.hasNext())
				{
					Entry<String, Object> entry = parameters.next();
					if (entry.getValue() instanceof String)
					{
						jsMap.put(entry.getKey(), new String[] { (String)entry.getValue() });
					}
					else if (entry.getValue() instanceof String[] && ((String[])entry.getValue()).length > 0)
					{
						jsMap.put(entry.getKey(), entry.getValue());
					}
				}
				if (ws_authenticate_result != null)
				{
					jsMap.put(WS_AUTHENTICATE, new Object[] { ws_authenticate_result });
				}
				args[idx++] = jsMap;
			}
		}

		if (plugin.log.isDebugEnabled()) plugin.log.debug("executeMethod('" + wsRequest.formName + "', '" + methodName + "', <args>)");
		// DO NOT USE FunctionDefinition here! we want to be able to catch possible exceptions!
		Object result;
		try
		{
			result = client.getPluginAccess().executeMethod(wsRequest.formName, methodName, args, false);
		}
		catch (Exception e)
		{
			plugin.log.info("Method execution failed: executeMethod('" + wsRequest.formName + "', '" + methodName + "', <args>)", e);
			throw new ExecFailedException(e);
		}
		if (plugin.log.isDebugEnabled()) plugin.log.debug("result = " + (result == null ? "<NULL>" : ("'" + result + '\'')));
		// flush updated cookies from the application
		setResponseUserProperties(request, response, client.getPluginAccess());
		return result;


	}

	private Object checkAuthorization(HttpServletRequest request, IClientPluginAccess client, String solutionName, String formName) throws Exception
	{
		String[] authorizedGroups = plugin.getAuthorizedGroups();
		FunctionDefinition fd = new FunctionDefinition(formName, WS_AUTHENTICATE);
		Exist authMethodExists = fd.exists(client);
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
			Object retval = fd.executeSync(client, new String[] { user, password });
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

	protected byte[] getBody(HttpServletRequest request) throws IOException
	{
		InputStream is = null;
		try
		{
			is = request.getInputStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			byte[] buffer = new byte[128];
			int length;
			while ((length = is.read(buffer)) >= 0)
			{
				baos.write(buffer, 0, length);
			}

			return baos.toByteArray();
		}
		finally
		{
			if (is != null)
			{
				is.close();
			}
		}
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
				return CONTENT_OTHER;
			}
			if (header.indexOf("octet-stream") >= 0 || header.indexOf("application") >= 0)
			{
				return CONTENT_BINARY;
			}
		}

		return CONTENT_OTHER;
	}

	private int getRequestContentType(HttpServletRequest request, String header, byte[] contents, int defaultContentType) throws UnsupportedEncodingException
	{
		String contentTypeHeaderValue = request.getHeader(header);
		int contentType = getContentType(contentTypeHeaderValue);
		if (contentType != CONTENT_OTHER) return contentType;
		if (contents != null)
		{
			String stringContent = new String(contents, getHeaderKey(request.getHeader("Content-Type"), "charset", CHARSET_DEFAULT));
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
	protected String getHeaderKey(String header, String key, String defaultValue)
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
				Debug.error("cannot get json object from " + WS_USER_PROPERTIES_HEADER + " headder: ", e);
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
						map.put(name.substring(WS_USER_PROPERTIES_COOKIE_PREFIX.length()), value);
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
				Cookie cookie = new Cookie(WS_USER_PROPERTIES_COOKIE_PREFIX + propName, map.get(propName));
				String ctxPath = request.getContextPath();
				if (ctxPath == null || ctxPath.equals("/") || ctxPath.length() < 1) ctxPath = "";
				cookie.setPath(ctxPath + request.getServletPath() + "/" + RestWSPlugin.WEBSERVICE_NAME + "/" + client.getSolutionName());
				if (request.isSecure()) cookie.setSecure(true);
				response.addCookie(cookie);
			}
		}
	}

	private Object decodeContent(String contentTypeStr, int contentType, byte[] contents, String charset) throws Exception
	{
		switch (contentType)
		{
			case CONTENT_JSON :
				return plugin.getJSONSerializer().fromJSON(new String(contents, charset));

			case CONTENT_XML :
				return plugin.getJSONSerializer().fromJSON(XML.toJSONObject(new String(contents, charset)));

			case CONTENT_MULTIPART :
				javax.mail.internet.MimeMultipart m = new MimeMultipart(new ServletMultipartDataSource(new ByteArrayInputStream(contents), contentTypeStr));
				Object[] partArray = new Object[m.getCount()];
				for (int i = 0; i < m.getCount(); i++)
				{
					BodyPart bodyPart = m.getBodyPart(i);
					JSMap<String, Object> partObj = new JSMap<String, Object>();
					//filename
					if (bodyPart.getFileName() != null) partObj.put("fileName", bodyPart.getFileName());
					String partContentType = "";
					//charset
					if (bodyPart.getContentType() != null) partContentType = bodyPart.getContentType();

					String _charset = getHeaderKey(partContentType, "charset", "");
					partContentType = partContentType.replaceAll("(.*?);\\s*\\w+=.*", "$1");
					//contentType
					if (partContentType.length() > 0) partObj.put("contentType", partContentType);
					if (_charset.length() > 0) partObj.put("charset", _charset);
					else _charset = "UTF-8"; // still use a valid default encoding in case it's not specified for reading it - it is ok that it will not be reported to JS I guess (this happens almost all the time)
					InputStream contentStream = bodyPart.getInputStream();
					try
					{
						if (contentStream.available() > 0)
						{
							//Get content value
							Object decodedBodyPart = decodeContent(partContentType, getContentType(partContentType),
								Utils.getBytesFromInputStream(contentStream), _charset);
							contentStream.close();
							partObj.put("value", decodedBodyPart);
						}
					}
					finally
					{
						contentStream.close();
					}

					// Get name header
					String nameHeader = "";
					String[] nameHeaders = bodyPart.getHeader("Content-Disposition");
					if (nameHeaders != null)
					{
						for (String bodyName : nameHeaders)
						{
							String name = getHeaderKey(bodyName, "name", "");
							if (name.length() > 0) nameHeader = name;
							break;
						}
					}
					if (nameHeader.length() > 0) partObj.put("name", nameHeader);
					partArray[i] = partObj;
				}
				return partArray;

			case CONTENT_BINARY :
				return contents;
			case CONTENT_TEXT :
				return new String(contents, charset);
			case CONTENT_OTHER :
				return contents;
		}

		// should not happen, content type was checked before
		throw new IllegalStateException();
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
		return request.getHeader(WS_NODEBUG_HEADER) != null ? true : false;
	}

	protected void sendResult(HttpServletRequest request, HttpServletResponse response, Object result, int defaultContentType) throws Exception
	{
		int contentType = getRequestContentType(request, "Accept", null, (result instanceof byte[]) ? CONTENT_BINARY : defaultContentType);

		String resultContentType;
		byte[] bytes;

		if (result instanceof byte[])
		{
			if (contentType == CONTENT_BINARY)
			{
				bytes = (byte[])result;
				//get content type from accept header (if multiple types specified in accept header, take the first), if not guess from response content
				resultContentType = request.getHeader("Accept") != null ? request.getHeader("Accept").split(";")[0] : MimeTypes_getContentType(bytes);
				if (resultContentType == null) resultContentType = "application/octet-stream";//if still null, then set to standard
			}
			else
			{
				//requested type is json or xml
				plugin.log.error("Request for non-binary data was made, but the return data is a byte array.");
				sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				return;
			}
		}
		else
		{
			if (contentType == CONTENT_BINARY)
			{
				plugin.log.error("Request for binary data was made, but the return data is not a byte array; return data is " + result);
				sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				return;
			}
			boolean isXML = (result instanceof XMLObject);
			boolean isJSON = (result instanceof JSONObject || result instanceof JSONArray);

			Object json = null;
			if (isXML)
			{
				json = XML.toJSONObject(result.toString());
			}
			else if (isJSON)
			{
				json = result;
			}
			else
			{
				json = plugin.getJSONSerializer().toJSON(result);
			}

			String content;
			String contentTypeCharset = getHeaderKey(request.getHeader("Content-Type"), "charset", CHARSET_DEFAULT);
			String charset = getHeaderKey(request.getHeader("Accept"), "charset", contentTypeCharset);
			switch (contentType)
			{
				case CONTENT_JSON :
					String callback = request.getParameter("callback");
					if (callback != null && !callback.equals(""))
					{
						content = callback + '(' + json.toString() + ')';
					}
					else
					{
						content = json.toString();
					}
					break;

				case CONTENT_XML :
					content = "<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\n" + ((isXML) ? result.toString() : XML.toString(json, null));
					break;
				case CONTENT_MULTIPART :
					content = "";
					break;
				case CONTENT_TEXT :
					content = result != null ? result.toString() : "";
					break;
				default :
					// how can this happen...
					throw new IllegalStateException();
			}

			switch (contentType)
			{
				//multipart requests cannot respond multipart responses so treat response as json
				case CONTENT_MULTIPART :
				case CONTENT_JSON :
					resultContentType = "application/json";
					break;

				case CONTENT_XML :
					resultContentType = "application/xml";
					break;
				case CONTENT_TEXT :
					resultContentType = "text/plain";
					break;

				default :
					// how can this happen...
					throw new IllegalStateException();
			}

			resultContentType = resultContentType + ";charset=" + charset;
			bytes = content.getBytes(charset);
		}

		response.setHeader("Content-Type", resultContentType);
		response.setContentLength(bytes.length);

		ServletOutputStream outputStream = null;
		try
		{
			outputStream = response.getOutputStream();
			outputStream.write(bytes);
		}
		finally
		{
			if (outputStream != null)
			{
				outputStream.flush();
				outputStream.close();
			}
		}
	}

	/**
	 * Copied MimeTypes.getContentType(byte[]) from Servoy 8.0
	 */
	private String MimeTypes_getContentType(byte[] data)
	{
		{
			if (data == null)
			{
				return null;
			}
			byte[] header = new byte[11];
			System.arraycopy(data, 0, header, 0, Math.min(data.length, header.length));
			int c1 = header[0] & 0xff;
			int c2 = header[1] & 0xff;
			int c3 = header[2] & 0xff;
			int c4 = header[3] & 0xff;
			int c5 = header[4] & 0xff;
			int c6 = header[5] & 0xff;
			int c7 = header[6] & 0xff;
			int c8 = header[7] & 0xff;
			int c9 = header[8] & 0xff;
			int c10 = header[9] & 0xff;
			int c11 = header[10] & 0xff;

			if (c1 == 0xCA && c2 == 0xFE && c3 == 0xBA && c4 == 0xBE)
			{
				return "application/java-vm";
			}

			if (c1 == 0xD0 && c2 == 0xCF && c3 == 0x11 && c4 == 0xE0 && c5 == 0xA1 && c6 == 0xB1 && c7 == 0x1A && c8 == 0xE1)
			{
				// if the name is set then check if it can be validated by name, because it could be a xls or powerpoint
//				String contentType = guessContentTypeFromName(name);
//				if (contentType != null)
//				{
//					return contentType;
//				}
				return "application/msword";
			}
			if (c1 == 0x25 && c2 == 0x50 && c3 == 0x44 && c4 == 0x46 && c5 == 0x2d && c6 == 0x31 && c7 == 0x2e)
			{
				return "application/pdf";
			}

			if (c1 == 0x38 && c2 == 0x42 && c3 == 0x50 && c4 == 0x53 && c5 == 0x00 && c6 == 0x01)
			{
				return "image/photoshop";
			}

			if (c1 == 0x25 && c2 == 0x21 && c3 == 0x50 && c4 == 0x53)
			{
				return "application/postscript";
			}

			if (c1 == 0xff && c2 == 0xfb && c3 == 0x30)
			{
				return "audio/mp3";
			}

			if (c1 == 0x49 && c2 == 0x44 && c3 == 0x33)
			{
				return "audio/mp3";
			}

			if (c1 == 0xAC && c2 == 0xED)
			{
				// next two bytes are version number, currently 0x00 0x05
				return "application/x-java-serialized-object";
			}

			if (c1 == '<')
			{
				if (c2 == '!' ||
					((c2 == 'h' && (c3 == 't' && c4 == 'm' && c5 == 'l' || c3 == 'e' && c4 == 'a' && c5 == 'd') ||
						(c2 == 'b' && c3 == 'o' && c4 == 'd' && c5 == 'y'))) ||
					((c2 == 'H' && (c3 == 'T' && c4 == 'M' && c5 == 'L' || c3 == 'E' && c4 == 'A' && c5 == 'D') ||
						(c2 == 'B' && c3 == 'O' && c4 == 'D' && c5 == 'Y'))))
				{
					return "text/html";
				}

				if (c2 == '?' && c3 == 'x' && c4 == 'm' && c5 == 'l' && c6 == ' ')
				{
					return "application/xml";
				}
			}

			// big and little endian UTF-16 encodings, with byte order mark
			if (c1 == 0xfe && c2 == 0xff)
			{
				if (c3 == 0 && c4 == '<' && c5 == 0 && c6 == '?' && c7 == 0 && c8 == 'x')
				{
					return "application/xml";
				}
			}

			if (c1 == 0xff && c2 == 0xfe)
			{
				if (c3 == '<' && c4 == 0 && c5 == '?' && c6 == 0 && c7 == 'x' && c8 == 0)
				{
					return "application/xml";
				}
			}

			if (c1 == 'B' && c2 == 'M')
			{
				return "image/bmp";
			}

			if (c1 == 0x49 && c2 == 0x49 && c3 == 0x2a && c4 == 0x00)
			{
				return "image/tiff";
			}

			if (c1 == 0x4D && c2 == 0x4D && c3 == 0x00 && c4 == 0x2a)
			{
				return "image/tiff";
			}

			if (c1 == 'G' && c2 == 'I' && c3 == 'F' && c4 == '8')
			{
				return "image/gif";
			}

			if (c1 == '#' && c2 == 'd' && c3 == 'e' && c4 == 'f')
			{
				return "image/x-bitmap";
			}

			if (c1 == '!' && c2 == ' ' && c3 == 'X' && c4 == 'P' && c5 == 'M' && c6 == '2')
			{
				return "image/x-pixmap";
			}

			if (c1 == 137 && c2 == 80 && c3 == 78 && c4 == 71 && c5 == 13 && c6 == 10 && c7 == 26 && c8 == 10)
			{
				return "image/png";
			}

			if (c1 == 0xFF && c2 == 0xD8 && c3 == 0xFF)
			{
				if (c4 == 0xE0)
				{
					return "image/jpeg";
				}

				/**
				 * File format used by digital cameras to store images. Exif Format can be read by any application supporting JPEG. Exif Spec can be found at:
				 * http://www.pima.net/standards/it10/PIMA15740/Exif_2-1.PDF
				 */
				if ((c4 == 0xE1) && (c7 == 'E' && c8 == 'x' && c9 == 'i' && c10 == 'f' && c11 == 0))
				{
					return "image/jpeg";
				}

				if (c4 == 0xEE)
				{
					return "image/jpg";
				}
			}

			/**
			 * According to http://www.opendesign.com/files/guestdownloads/OpenDesign_Specification_for_.dwg_files.pdf
			 * first 6 bytes are of type "AC1018" (for example) and the next 5 bytes are 0x00.
			 */
			if ((c1 == 0x41 && c2 == 0x43) && (c7 == 0x00 && c8 == 0x00 && c9 == 0x00 && c10 == 0x00 && c11 == 0x00))
			{
				return "application/acad";
			}

			if (c1 == 0x2E && c2 == 0x73 && c3 == 0x6E && c4 == 0x64)
			{
				return "audio/basic"; // .au
				// format,
				// big
				// endian
			}

			if (c1 == 0x64 && c2 == 0x6E && c3 == 0x73 && c4 == 0x2E)
			{
				return "audio/basic"; // .au
				// format,
				// little
				// endian
			}

			if (c1 == 'R' && c2 == 'I' && c3 == 'F' && c4 == 'F')
			{
				/*
				 * I don't know if this is official but evidence suggests that .wav files start with "RIFF" - brown
				 */
				return "audio/x-wav";
			}

			if (c1 == 'P' && c2 == 'K')
			{
				// its application/zip but this could be a open office thing if name is given
//				String contentType = guessContentTypeFromName(name);
//				if (contentType != null)
//				{
//					return contentType;
//				}
				return "application/zip";
			}
			return null; // guessContentTypeFromName(name);
		}
	}

	/**
	 * Send the error response but prevent output of the default (html) error page
	 * @param response
	 * @param error
	 * @throws IOException
	 */
	protected void sendError(HttpServletResponse response, int error) throws IOException
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
	protected void sendError(HttpServletResponse response, int error, String errorResponse) throws IOException
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

			Writer w = response.getWriter();
			w.write(errorResponse);
			w.close();
		}
	}

	public static class WsRequest
	{
		public final String solutionName;
		public final String formName;
		public final String[] args;

		/**
		 * @param solutionName
		 * @param formName
		 * @param args
		 */
		public WsRequest(String solutionName, String formName, String[] args)
		{
			this.solutionName = solutionName;
			this.formName = formName;
			this.args = args;
		}

		@Override
		public String toString()
		{
			return "WsRequest [solutionName=" + solutionName + ", formName=" + formName + ", args=" + Arrays.toString(args) + "]";
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
