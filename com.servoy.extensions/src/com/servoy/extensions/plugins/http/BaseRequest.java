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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativePromise;
import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.Deferred;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.scripting.JSMap;
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
		client = hc;
		headers = new HashMap<String, String[]>();
		this.method = method;
		this.httpPlugin = httpPlugin;
		this.requestConfigBuilder = requestConfigBuilder;
		this.proxyCredentialsProvider = proxyCredentialsProvider;
	}

	/**
	 * Get the HTTP method of the request.
	 *
	 * @return The HTTP method (e.g., "GET", "POST").
	 */
	public String js_getHttpMethod()
	{
		return method.getMethod();
	}

	/**
	 * Get the URL of the request.
	 *
	 * @return The request URL.
	 */
	public String js_getUrl()
	{
		return url;
	}

	/**
	 * This returns a Object with header names as keys and arrays of values as values.
	 *
	 * @return An Object with "name" > "value"
	 */
	public Map<String, String[]> js_getHeaders()
	{
		JSMap<String, String[]> map = new JSMap<>();
		map.putAll(headers);
		return map;
	}

	/**
	 * Get query parameters from the request URL.
	 *
	 * @return A map of parameter names to arrays of values.
	 */
	public Map<String, String[]> js_getQueryParameters()
	{
		Map<String, List<String>> temp = new HashMap<>();
		try
		{
			URIBuilder builder = new URIBuilder(this.url);
			List<NameValuePair> pairs = builder.getQueryParams();
			for (NameValuePair p : pairs)
			{
				temp.computeIfAbsent(p.getName(), k -> new ArrayList<>()).add(p.getValue());
			}
		}
		catch (Exception ignored)
		{
			// ignore parse errors
		}
		Map<String, String[]> result = new JSMap<>();
		temp.forEach((key, list) -> result.put(key, list.toArray(new String[0])));
		return result;
	}

	/**
	 * Get the configured response timeout in milliseconds.
	 *
	 * @return Timeout in ms, or –1 if none.
	 */
	public int js_getTimeout()
	{
		Timeout t = requestConfigBuilder.build().getResponseTimeout();
		return (t != null ? (int)t.toMilliseconds() : -1);
	}

	/**
	 * Get the Apache RequestConfig for this request.
	 *
	 * @return The built RequestConfig.
	 */
	public RequestConfig js_getRequestConfig()
	{
		return requestConfigBuilder.build();
	}


	HttpUriRequestBase getMethod()
	{
		return method;
	}

	/**
	 * Add a header to the request.
	 *
	 * @sample
	 * method.addHeader('Content-type','text/xml; charset=ISO-8859-1')
	 *
	 * @param headerName
	 * @param value
	 *
	 * @return A boolean indicating whether the header was successfully added to the request.
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
	 * @return A Response object containing the result of the executed HTTP request.
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
	 *
	 * @return A Response object containing the result of the HTTP request, authenticated with the provided username and password.
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
	 *
	 * @return A Response object containing the result of the HTTP request, authenticated using Windows authentication with the provided credentials.
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
	 *
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
		final Future<FileOrTextHttpResponse> future = executeRequest(userName, password, workstation, domain, windowsAuthentication, successFunctionDef,
			errorFunctionDef, callbackArgs);
		if (waitForResult)
		{
			try
			{
				FileOrTextHttpResponse response = future.get();
				return new Response(response, method);

			}
			catch (ExecutionException ee)
			{
				return new Response(ee.getMessage());
			}
			catch (CancellationException ce)
			{
				return new Response("Request was cancelled");
			}
			catch (Exception ex)
			{
				logError(ex, userName, workstation, domain);
			}
		}
		return null;
	}

	/**
	 * @param userName
	 * @param password
	 * @param workstation
	 * @param domain
	 * @param windowsAuthentication
	 * @param successFunctionDef
	 * @param errorFunctionDef
	 * @param callbackArgs
	 * @return
	 * @throws MalformedURLException
	 * @throws Exception
	 */
	Future<FileOrTextHttpResponse> executeRequest(String userName, String password, String workstation, String domain, boolean windowsAuthentication,
		FunctionDefinition successFunctionDef, FunctionDefinition errorFunctionDef, Object[] callbackArgs) throws MalformedURLException, Exception
	{
		HttpClientContext context = null;

		boolean acceptEncodingAdded = false;
		for (String name : headers.keySet())
		{
			String[] values = headers.get(name);
			for (String value : values)
			{
				method.addHeader(name, value);
				acceptEncodingAdded = acceptEncodingAdded || "accept-encoding".equalsIgnoreCase(name);
			}
		}

		if (!acceptEncodingAdded)
		{
			method.addHeader("Accept-Encoding", "gzip");
		}

		if (proxyCredentialsProvider != null)
		{
			context = HttpClientContext.create();
			context.setCredentialsProvider(proxyCredentialsProvider);
		}
		if (!Utils.stringIsEmpty(userName))
		{
			if (context == null) context = HttpClientContext.create();

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
			HttpHost targetHost = new HttpHost(_url.getProtocol(), _url.getHost(), _url.getPort());
			context.setCredentialsProvider(CredentialsProviderBuilder.create().add(new AuthScope(targetHost), cred).build());

			if (usePreemptiveAuthentication)
			{
				BasicScheme scheme = new BasicScheme();
				scheme.initPreemptive(cred);
				context.resetAuthExchange(targetHost, scheme);
			}
		}
		method.setConfig(requestConfigBuilder.build());
		final Future<FileOrTextHttpResponse> future = client.execute(
			new BasicRequestProducer(method, buildEntityProducer()),
			FileOrTextResponseConsumer.create(),
			context,
			new FutureCallback<FileOrTextHttpResponse>()
			{

				@Override
				public void completed(final FileOrTextHttpResponse response)
				{
					if (successFunctionDef != null)
					{
						IClientPluginAccess access = httpPlugin.getClientPluginAccess();
						if (access != null)
						{
							callbackArgs[0] = new Response(response, method);
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
				}

				@Override
				public void cancelled()
				{
					Debug.error("Request was cancelled while executing " + method.getRequestUri() + " with method " + method.getMethod() + " with user: " +
						userName + ", workstation: " +
						workstation + ", domain: " + domain);
				}

			});
		return future;
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
	 * Execute the request method asynchronous a Promise is returned which will be resolved or rejected when the request is completed or failed.
	 *
	 * This Response can be a response with a different status code then just 200, it could also be 500, which is still a valid response from the server.
	 * So you need to test the Reponse.getStatusCode() for that to know if everything did go OK.
	 * If no response is received (request errors out), the Promise is rejected with exception message as the value.
	 *
	 * You can use Promise.all([promise1, promise2, promise3]) to wait for multiple promises to complete.
	 * A shortcut for this would be to use httpClient.executeRequest(requestArray) that returns 1 promise that is called when all of them are done.
	 *
	 * @sample
	 * request.executeAsyncRequest().then(response => { // handle the response }).catch(errorMessage => { // handle the error });})
	 *
	 * @return {Promise} A Promise that resolves with a Response object upon request completion or rejects with an error message if the request fails.
	 *
	 */
	@JSFunction
	public NativePromise executeAsyncRequest()
	{
		return executeAsyncRequest(null, null, null, null);
	}

	/**
	 * @clonedesc executeAsyncRequest()
	 * @sampleas executeAsyncRequest()
	 *
	 * @param {String} username The user name used for authentication.
	 * @param {String} password The password used for authentication.
	 *
	 * @return {Promise} A Promise that resolves with a Response object upon request completion or rejects with an error message if the request fails.
	 */
	@JSFunction
	public NativePromise executeAsyncRequest(final String username, final String password)
	{
		return executeAsyncRequest(username, password, null, null);
	}

	/**
	 * @clonedesc executeAsyncRequest()
	 * @sampleas executeAsyncRequest()
	 *
	 * @param {String} username The user name used for authentication.
	 * @param {String} password The password used for authentication.
	 * @param {String} workstation The workstation the authentication request originates from.
	 * @param {String} domain The domain to authenticate within.
	 *
	 * @return {Promise} A Promise that resolves with a Response object upon request completion or rejects with an error message if the request fails.
	 */
	@JSFunction
	public NativePromise executeAsyncRequest(final String username, final String password, final String workstation, final String domain)
	{
		Deferred deferred = new Deferred(httpPlugin.getClientPluginAccess());

		httpPlugin.getClientPluginAccess().getExecutor().execute(() -> {
			try
			{
				Future<FileOrTextHttpResponse> future = executeRequest(username, password, workstation, domain, workstation != null, null, null, null);
				FileOrTextHttpResponse response = future.get();
				deferred.resolve(new Response(response, method));
			}
			catch (ExecutionException ee)
			{
				deferred.reject(new Response(ee.getMessage()));
			}
			catch (CancellationException ce)
			{
				deferred.reject((new Response("Request was cancelled")));
			}
			catch (Exception ex)
			{
				deferred.reject(new Response(ex.getMessage()));
			}
		});
		return deferred.getPromise();
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

	private void logError(Throwable ex, String username, String workstation, String domain)
	{
		Debug.error(
			"Error executing a request to " + method.getRequestUri() + " with method " + method.getMethod() + " with user: " + username + ", workstation: " +
				workstation + ", domain: " + domain,
			ex);
	}

}
