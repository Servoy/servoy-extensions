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

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.message.BufferedHeader;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.mozilla.javascript.Function;

import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * @author pbakker
 *
 */
@SuppressWarnings("nls")
public abstract class BaseRequest implements IScriptable, IJavaScriptType
{
	protected CloseableHttpAsyncClient client;
	protected final HttpUriRequestBase method;
	protected String url;
	protected Map<String, String[]> headers;
	private HttpPlugin httpPlugin;
	protected boolean usePreemptiveAuthentication = false;
	private Builder requestConfigBuilder;
	private BasicCredentialsProvider proxyCredentialsProvider;

	public BaseRequest()
	{
		method = null;
	}//only used by script engine

	public BaseRequest(String url, CloseableHttpAsyncClient hc, HttpUriRequestBase method, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		this.url = url;
		if (hc == null)
		{
			client = HttpAsyncClientBuilder.create().build();
		}
		else
		{
			client = hc;
		}
		headers = new HashMap<String, String[]>();
		this.method = method;
		this.httpPlugin = httpPlugin;
		this.requestConfigBuilder = requestConfigBuilder;
		this.proxyCredentialsProvider = proxyCredentialsProvider;
	}

	/**
	 * Add a header to the request.
	 *
	 * @sample
	 * method.addHeader('Content-type','text/xml; charset=ISO-8859-1')
	 *
	 * @param headerName
	 * @param value
	 */
	public boolean js_addHeader(String headerName, String value)
	{
		if (headerName != null)
		{
			if (headers.containsKey(headerName))
			{
				String[] values = headers.get(headerName);
				String[] newValues = new String[values.length + 1];
				System.arraycopy(values, 0, newValues, 0, values.length);
				newValues[values.length] = value;
				headers.put(headerName, newValues);
			}
			else
			{
				headers.put(headerName, new String[] { value });
			}
			return true;
		}
		return false;
	}

	/**
	 * Execute the request method.
	 *
	 * @sample
	 * var response = method.executeRequest()
	 *
	 * To be able to reuse the client, the response must be
	 * closed if the content is not read via getResponseBody
	 *  or getMediaData:
	 *
	 * response.close()
	 *
	 */
	public Response js_executeRequest()
	{
		return js_executeRequest(null, null);
	}

	/**
	 * @clonedesc js_executeRequest()
	 * @sampleas js_executeRequest()
	 *
	 * @param userName the user name
	 * @param password the password
	 */
	public Response js_executeRequest(String userName, String password)
	{
		try
		{
			return executeRequest(userName, password, null, null, false, null, null, null, true);
		}
		catch (Exception ex)
		{
			logError(ex, userName, null, null);
			return new Response(ex.getMessage());
		}
	}

	/**
	 * Execute a request method using windows authentication.
	 * @sample
	 * var response = method.executeRequest('username','password','mycomputername','domain');
	 *
	 * @param userName the user name
	 * @param password the password
	 * @param workstation The workstation the authentication request is originating from.
	 * @param domain The domain to authenticate within.
	 */
	public Response js_executeRequest(String userName, String password, String workstation, String domain)
	{
		try
		{
			return executeRequest(userName, password, workstation, domain, true, null, null, null, true);
		}
		catch (Exception ex)
		{
			logError(ex, userName, workstation, domain);
			return new Response(ex.getMessage());
		}
	}

	/**
	 * Whatever to use preemptive authentication (sending the credentials in the header, avoiding the server request to
	 * the client - useful when uploading files, as some http servers would cancel the first request from the client, if too big,
	 * as the authentication request to the client was not yet sent)
	 * @param b
	 */
	public void js_usePreemptiveAuthentication(boolean b)
	{
		this.usePreemptiveAuthentication = b;
	}

	protected AsyncEntityProducer buildEntityProducer() throws Exception
	{
		return null;
	}

	private Response executeRequest(String userName, String password, String workstation, String domain, boolean windowsAuthentication,
		FunctionDefinition successFunctionDef, FunctionDefinition errorFunctionDef, Object[] callbackArgs, boolean waitForResult) throws Exception
	{
		HttpClientContext context = null;

		for (String name : headers.keySet())
		{
			String[] values = headers.get(name);
			for (String value : values)
			{
				method.addHeader(name, value);
			}
		}
		if (proxyCredentialsProvider != null)
		{
			context = HttpClientContext.create();
			context.setCredentialsProvider(proxyCredentialsProvider);
		}
		if (!Utils.stringIsEmpty(userName))
		{
			if (context == null) context = HttpClientContext.create();

			BasicCredentialsProvider bcp = new BasicCredentialsProvider();
			URL _url = HttpProvider.createURLFromString(url, httpPlugin.getClientPluginAccess());
			Credentials cred = null;
			if (windowsAuthentication)
			{
				cred = new NTCredentials(userName, password != null ? password.toCharArray() : null, workstation, domain);
			}
			else
			{
				cred = new UsernamePasswordCredentials(userName, password != null ? password.toCharArray() : null);
			}
			bcp.setCredentials(new AuthScope(_url.getHost(), _url.getPort()), cred);
			context.setCredentialsProvider(bcp);

			if (usePreemptiveAuthentication)
			{
				BasicScheme scheme = new BasicScheme();
				scheme.initPreemptive(cred);
				String authHeader = scheme.generateAuthResponse(null, method, context);
				CharArrayBuffer buffer = new CharArrayBuffer(authHeader.length());
				buffer.append(authHeader);
				method.addHeader(new BufferedHeader(buffer));
			}
		}
		method.setConfig(requestConfigBuilder.build());
		final Response[] finalResponse = new Response[1];
		final Future<SimpleHttpResponse> future = client.execute(
			new BasicRequestProducer(method, buildEntityProducer()),
			SimpleResponseConsumer.create(),
			new FutureCallback<SimpleHttpResponse>()
			{

				@Override
				public void completed(final SimpleHttpResponse response)
				{
					finalResponse[0] = new Response(response, method);
					if (successFunctionDef != null)
					{
						IClientPluginAccess access = httpPlugin.getClientPluginAccess();
						if (access != null)
						{
							callbackArgs[0] = finalResponse[0];
							successFunctionDef.executeAsync(access, callbackArgs);
						}
						else
						{
							Debug.log(
								"Callback for request: " + method.getRequestUri() + " was given: " + successFunctionDef + " but the client was already closed");
						}
					}
				}

				@Override
				public void failed(final Exception ex)
				{
					logError(ex, userName, workstation, domain);
					if (errorFunctionDef != null)
					{
						IClientPluginAccess access = httpPlugin.getClientPluginAccess();
						if (access != null)
						{
							callbackArgs[0] = ex.getMessage();
							errorFunctionDef.executeAsync(access, callbackArgs);
						}
						else
						{
							Debug.log(
								"Error callback for request: " + method.getRequestUri() + " was given: " + errorFunctionDef +
									" but the client was already closed");
						}
					}
					else
					{
						finalResponse[0] = new Response(ex.getMessage());
					}
				}

				@Override
				public void cancelled()
				{
					Debug.error("Request was cancelled while executing " + method.getRequestUri() + " with method " + method.getMethod() + " with user: " +
						userName + ", workstation: " +
						workstation + ", domain: " + domain);
					finalResponse[0] = new Response("Request was cancelled");
				}

			});
		if (waitForResult)
		{
			future.get();
			return finalResponse[0];
		}
		return null;
	}

	/**
	 * Execute the request method asynchronous. Success callback method will be called when response is received.
	 * Response is sent as parameter in callback.
	 * This Response can be a response with a different status code then just 200, it could also be 500, which is still a valid response from the server, this won't go into the error callback.
	 * So you need to test the Reponse.getStatusCode() for that to know if everything did go OK.
	 * If no response is received (request errors out), the errorCallbackMethod is called with exception message as parameter.
	 *
	 * @sample
	 * method.executeAsyncRequest(globals.successCallback,globals.errorCallback)
	 *
	 * @param successCallbackMethod callbackMethod to be called after response is received
	 * @param errorCallbackMethod callbackMethod to be called if request errors out
	 *
	 */
	public void js_executeAsyncRequest(Function successCallbackMethod, Function errorCallbackMethod)
	{
		executeAsyncRequest(null, null, null, null, successCallbackMethod, errorCallbackMethod, false, null);
	}

	/**
	 * @clonedesc js_executeAsyncRequest(Function,Function)
	 * @sampleas js_executeAsyncRequest(Function,Function)
	 *
	 * @param username the user name
	 * @param password the password
	 * @param successCallbackMethod callbackMethod to be called after response is received
	 * @param errorCallbackMethod callbackMethod to be called if request errors out
	 */

	public void js_executeAsyncRequest(final String username, final String password, Function successCallbackMethod, Function errorCallbackMethod)
	{
		executeAsyncRequest(username, password, null, null, successCallbackMethod, errorCallbackMethod, false, null);
	}

	/**
	 * @clonedesc js_executeAsyncRequest(Function,Function)
	 *
	 * @sample
	 * method.executeAsyncRequest('username','password','mycomputername','domain',globals.successCallback,globals.errorCallback)
	 *
	 * @param username the user name
	 * @param password the password
	 * @param workstation The workstation the authentication request is originating from.
	 * @param domain The domain to authenticate within.
	 * @param successCallbackMethod callbackMethod to be called after response is received
	 * @param errorCallbackMethod callbackMethod to be called if request errors out
	 */

	public void js_executeAsyncRequest(final String username, final String password, final String workstation, final String domain,
		Function successCallbackMethod, Function errorCallbackMethod)
	{
		executeAsyncRequest(username, password, workstation, domain, successCallbackMethod, errorCallbackMethod, true, null);
	}

	/**
	 * Execute the request method asynchronous using windows authentication.
	 * Success callback method will be called when response is received. Response is sent as parameter in callback followed by any 'callbackExtraArgs' that were given.
	 * This Response can be a response with a different status code then just 200, it could also be 500, which is still a valid response from the server, this won't go into the error callback.
	 * So you need to test the Reponse.getStatusCode() for that to know if everything did go OK.
	 * If no response is received (request errors out, network errors), the errorCallbackMethod is called with exception message as parameter followed by any 'callbackExtraArgs' that were given.
	 *
	 * @sample
	 * method.executeAsyncRequest(globals.successCallback,globals.errorCallback, [callIDInt])
	 *
	 * @param successCallbackMethod callbackMethod to be called after response is received
	 * @param errorCallbackMethod callbackMethod to be called if request errors out
	 * @param callbackExtraArgs extra arguments that will be passed to the callback methods; can be used to identify from which request the response arrived when
	 * using the same callback method for multiple requests. Please use only simple JSON arguments (primitive types or array/objects of primitive types)
	 *
	 */
	public void js_executeAsyncRequest(Function successCallbackMethod, Function errorCallbackMethod, final Object[] callbackExtraArgs)
	{
		executeAsyncRequest(null, null, null, null, successCallbackMethod, errorCallbackMethod, false, callbackExtraArgs);
	}

	/**
	 * @clonedesc js_executeAsyncRequest(Function,Function,Object[])
	 * @sampleas js_executeAsyncRequest(Function,Function,Object[])
	 *
	 * @param username the user name
	 * @param password the password
	 * @param successCallbackMethod callbackMethod to be called after response is received
	 * @param errorCallbackMethod callbackMethod to be called if request errors out
	 * @param callbackExtraArgs extra arguments that will be passed to the callback methods; can be used to identify from which request the response arrived when
	 * using the same callback method for multiple requests. Please use only simple JSON arguments (primitive types or array/objects of primitive types)
	 */

	public void js_executeAsyncRequest(final String username, final String password, Function successCallbackMethod, Function errorCallbackMethod,
		final Object[] callbackExtraArgs)
	{
		executeAsyncRequest(username, password, null, null, successCallbackMethod, errorCallbackMethod, false, callbackExtraArgs);
	}

	/**
	 *
	 * 	 @clonedesc js_executeAsyncRequest(Function,Function,Object[])
	 *
	 * @sample
	 * method.executeAsyncRequest('username','password','mycomputername','domain',globals.successCallback,globals.errorCallback, [callIDInt])
	 *
	 * @param username the user name
	 * @param password the password
	 * @param workstation The workstation the authentication request is originating from.
	 * @param domain The domain to authenticate within.
	 * @param successCallbackMethod callbackMethod to be called after response is received
	 * @param errorCallbackMethod callbackMethod to be called if request errors out
	 * @param callbackExtraArgs extra arguments that will be passed to the callback methods; can be used to identify from which request the response arrived when
	 * using the same callback method for multiple requests. Please use only simple JSON arguments (primitive types or array/objects of primitive types)
	 */

	public void js_executeAsyncRequest(final String username, final String password, final String workstation, final String domain,
		Function successCallbackMethod, Function errorCallbackMethod, final Object[] callbackExtraArgs)
	{
		executeAsyncRequest(username, password, workstation, domain, successCallbackMethod, errorCallbackMethod, true, callbackExtraArgs);
	}

	private void executeAsyncRequest(final String username, final String password, final String workstation, final String domain,
		Function successCallbackMethod, Function errorCallbackMethod, final boolean windowsAuthentication, final Object[] callbackExtraArgs)
	{
		final FunctionDefinition successFunctionDef = successCallbackMethod != null ? new FunctionDefinition(successCallbackMethod) : null;
		final FunctionDefinition errorFunctionDef = errorCallbackMethod != null ? new FunctionDefinition(errorCallbackMethod) : null;
		Object[] convertedThereAndBackAgainPlusOne = null;
		if (callbackExtraArgs != null)
		{
			convertedThereAndBackAgainPlusOne = new Object[callbackExtraArgs.length + 1]; // reserve +1 for the response(success) or errorMessage(failure) params that are added first in any case

			for (int i = 0; i < callbackExtraArgs.length; i++)
			{
				try
				{
					// convert to JSON and back to make sure no refs to more complex objects are given here (that could complicate garbage collection)
					convertedThereAndBackAgainPlusOne[i + 1] = httpPlugin.getJSONConverter().convertFromJSON(
						httpPlugin.getJSONConverter().convertToJSON(callbackExtraArgs[i]));
				}
				catch (Exception e)
				{
					Debug.error(
						"Cannot convert extra argument of async callbacks (of an http request) to/from JSON. Please use only JSON: primitives, arrays or nested arrays with primitives, objects or nested objects with primitives",
						e);
				}
			}
		}
		final Object[] callbackArgs = convertedThereAndBackAgainPlusOne != null ? convertedThereAndBackAgainPlusOne : new Object[1];

		try
		{
			executeRequest(username, password, workstation, domain, windowsAuthentication, successFunctionDef, errorFunctionDef,
				callbackArgs, false);
		}
		catch (final Exception ex)
		{
			logError(ex, username, workstation, domain);
			if (errorFunctionDef != null)
			{
				IClientPluginAccess access = httpPlugin.getClientPluginAccess();
				if (access != null)
				{
					callbackArgs[0] = ex.getMessage();
					errorFunctionDef.executeAsync(access, callbackArgs);
				}
				else
				{
					Debug.log(
						"Error callback for request: " + method.getRequestUri() + " was given: " + errorFunctionDef +
							" but the client was already closed");
				}
			}
		}

	}

	private void logError(Exception ex, String username, String workstation, String domain)
	{
		Debug.error(
			"Error executing a request to " + method.getRequestUri() + " with method " + method.getMethod() + " with user: " + username + ", workstation: " +
				workstation + ", domain: " + domain,
			ex);
	}

}
