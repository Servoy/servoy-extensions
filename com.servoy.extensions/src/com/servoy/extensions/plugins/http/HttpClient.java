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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;

import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.NegotiateSchemeFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.ISmartRuntimeWindow;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

@ServoyDocumented
public class HttpClient implements IScriptable, IJavaScriptType
{
	DefaultHttpClient client;

	private String proxyUser = null;
	private String proxyPassword = null;
	private final HttpPlugin httpPlugin;

	public HttpClient(HttpPlugin httpPlugin)
	{
		ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager();
		manager.setDefaultMaxPerRoute(5);
		client = new DefaultHttpClient(manager);
		client.getParams().setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, Boolean.TRUE);
		client.getAuthSchemes().register(AuthPolicy.NTLM, new NTLMSchemeFactory());
		client.getAuthSchemes().register(AuthPolicy.SPNEGO, new NegotiateSchemeFactory());
		this.httpPlugin = httpPlugin;


		try
		{
			final AllowedCertTrustStrategy allowedCertTrustStrategy = new AllowedCertTrustStrategy();
			SSLSocketFactory sf = new SSLSocketFactory(allowedCertTrustStrategy)
			{
				@Override
				public Socket connectSocket(Socket socket, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpParams params)
					throws IOException, UnknownHostException, ConnectTimeoutException
				{
					if (socket instanceof SSLSocket)
					{
						try
						{
							Method s = socket.getClass().getMethod("setHost", String.class);
							s.invoke(socket, remoteAddress.getHostName());
						}
						catch (NoSuchMethodException ex)
						{
						}
						catch (IllegalAccessException ex)
						{
						}
						catch (InvocationTargetException ex)
						{
						}
						catch (IllegalArgumentException ex)
						{
						}
						catch (SecurityException ex)
						{
						}
					}
					try
					{
						return super.connectSocket(socket, remoteAddress, localAddress, params);
					}
					catch (SSLPeerUnverifiedException ex)
					{
						X509Certificate[] lastCertificates = allowedCertTrustStrategy.getAndClearLastCertificates();
						if (lastCertificates != null)
						{
							// allow for next time
							if (HttpClient.this.httpPlugin.getClientPluginAccess().getApplicationType() == IClientPluginAccess.CLIENT ||
								HttpClient.this.httpPlugin.getClientPluginAccess().getApplicationType() == IClientPluginAccess.RUNTIME)
							{
								// show dialog
								CertificateDialog dialog = new CertificateDialog(
									((ISmartRuntimeWindow)HttpClient.this.httpPlugin.getClientPluginAccess().getCurrentRuntimeWindow()).getWindow(),
									remoteAddress, lastCertificates);
								if (dialog.shouldAccept())
								{
									allowedCertTrustStrategy.add(lastCertificates);
									// try it again now with the new chain.
									return super.connectSocket(socket, remoteAddress, localAddress, params);
								}
							}
							else
							{
								Debug.error("Couldn't connect to " + remoteAddress +
									", please make sure that the ssl certificates of that site are added to the java keystore." +
									"Download the keystore in the browser and update the java cacerts file in jre/lib/security: " +
									"keytool -import -file downloaded.crt -keystore cacerts");
							}
						}
						throw ex;
					}
					finally
					{
						// always just clear the last request.
						allowedCertTrustStrategy.getAndClearLastCertificates();
					}

				}
			};
			Scheme https = new Scheme("https", 443, sf); //$NON-NLS-1$
			client.getConnectionManager().getSchemeRegistry().register(https);
		}
		catch (Exception e)
		{
			Debug.error("Can't register a https scheme", e); //$NON-NLS-1$
		}
	}

	/**
	 * Sets a timeout in milliseconds for retrieving of data (when 0 there is no timeout).
	 *
	 * @sample
	 * client.setTimeout(1000)
	 *
	 * @param msTimeout
	 */
	public void js_setTimeout(int timeout)
	{
		HttpParams params = client.getParams();
		HttpConnectionParams.setConnectionTimeout(params, timeout);
		HttpConnectionParams.setSoTimeout(params, timeout);
	}

	/**
	 * Add cookie to the this client.
	 *
	 * @sample
	 * var cookieSet = client.setCookie('JSESSIONID', 'abc', 'localhost', '/', -1, false)
	 * if (cookieSet)
	 * {
	 * 	//do something
	 * }
	 *
	 * @param cookieName the name of the cookie
	 * @param cookieValue the value of the cookie
	 */
	public boolean js_setCookie(String cookieName, String cookieValue)
	{
		return js_setCookie(cookieName, cookieValue, ""); //$NON-NLS-1$
	}

	/**
	 * @clonedesc js_setCookie(String, String)
	 * @sampleas js_setCookie(String, String)
	 *
	 * @param cookieName the name of the cookie
	 * @param cookieValue the value of the cookie
	 * @param domain the domain
	 */
	public boolean js_setCookie(String cookieName, String cookieValue, String domain)
	{
		return js_setCookie(cookieName, cookieValue, domain, ""); //$NON-NLS-1$
	}

	/**
	 * @clonedesc js_setCookie(String, String)
	 * @sampleas js_setCookie(String, String)
	 *
	 * @param cookieName the name of the cookie
	 * @param cookieValue the value of the cookie
	 * @param domain the domain
	 * @param path the path
	 */
	public boolean js_setCookie(String cookieName, String cookieValue, String domain, String path)
	{
		return js_setCookie(cookieName, cookieValue, domain, path, -1);
	}

	/**
	 * @clonedesc js_setCookie(String, String)
	 * @sampleas js_setCookie(String, String)
	 *
	 * @param cookieName the name of the cookie
	 * @param cookieValue the value of the cookie
	 * @param domain the domain
	 * @param path the path
	 * @param maxAge maximum age of cookie
	 */
	public boolean js_setCookie(String cookieName, String cookieValue, String domain, String path, int maxAge)
	{
		return js_setCookie(cookieName, cookieValue, domain, path, maxAge, false);
	}

	/**
	 * @clonedesc js_setCookie(String, String)
	 * @sampleas js_setCookie(String, String)
	 *
	 * @param cookieName the name of the cookie
	 * @param cookieValue the value of the cookie
	 * @param domain the domain
	 * @param path the path
	 * @param maxAge maximum age of cookie
	 * @param secure true if it is a secure cookie, false otherwise
	 */
	public boolean js_setCookie(String cookieName, String cookieValue, String domain, String path, int maxAge, boolean secure)
	{
		//Correct to disallow empty Cookie values? how to clear a Cookie then?
		if (Utils.stringIsEmpty(cookieName) || Utils.stringIsEmpty(cookieValue))
		{
			return false;
		}
		int age = maxAge;
		if (maxAge == 0)
		{
			age = -1;
		}

		BasicClientCookie cookie;
		cookie = new BasicClientCookie(cookieName, cookieValue);
		if (!Utils.stringIsEmpty(path))
		{
			cookie.setPath(path);
			cookie.setExpiryDate(new Date(System.currentTimeMillis() + age));
			cookie.setSecure(secure);
		}
		cookie.setDomain(domain);
		client.getCookieStore().addCookie(cookie);
		return true;
	}

	/**
	 * Get a cookie by name.
	 *
	 * @sample
	 * var cookie = client.getCookie('JSESSIONID');
	 * if (cookie != null)
	 * {
	 * 	// do something
	 * }
	 * else
	 * 	client.setCookie('JSESSIONID', 'abc', 'localhost', '/', -1, false)
	 *
	 * @param cookieName
	 */
	public Cookie js_getCookie(String cookieName)
	{
		List<org.apache.http.cookie.Cookie> cookies = client.getCookieStore().getCookies();
		for (org.apache.http.cookie.Cookie element : cookies)
		{
			if (element.getName().equals(cookieName)) return new com.servoy.extensions.plugins.http.Cookie(element);
		}
		return null;
	}

	/**
	 * Get all cookies from this client.
	 *
	 * @sample
	 * var cookies = client.getHttpClientCookies()
	 */
	public Cookie[] js_getCookies()
	{
		List<org.apache.http.cookie.Cookie> cookies = client.getCookieStore().getCookies();
		Cookie[] cookieObjects = new Cookie[cookies.size()];
		for (int i = 0; i < cookies.size(); i++)
		{
			cookieObjects[i] = new Cookie(cookies.get(i));
		}
		return cookieObjects;
	}

	/**
	 * Create a new post request ( Origin server should accept/process the submitted data.)
	 * If this url is a https ssl encrypted url which certificates are not in the java certificate store.
	 * (Like a self signed certificate or a none existing root certificate)
	 * Then for a smart client a dialog will be given, to give the user the ability to accept this certificate for the next time.
	 * For a Web or Headless client the system administrator does have to add that certificate (chain) to the java install on the server.
	 * See http://wiki.servoy.com/display/tutorials/Import+a+%28Root%29+certificate+in+the+java+cacerts+file
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var poster = client.createPostRequest('https://twitter.com/statuses/update.json');
	 * poster.addParameter('status',globals.textToPost);
	 * poster.addParameter('source','Test Source');
	 * poster.setCharset('UTF-8');
	 * var httpCode = poster.executeRequest(globals.twitterUserName, globals.twitterPassword).getStatusCode(); // httpCode 200 is ok
	 *
	 * @param url
	 */
	public PostRequest js_createPostRequest(String url)
	{
		HttpProvider.setHttpClientProxy(client, url, proxyUser, proxyPassword);
		return new PostRequest(url, client, httpPlugin);
	}

	/**
	 * Creates a new get request (retrieves whatever information is stored on specified url).
	 * If this url is a https ssl encrypted url which certificates are not in the java certificate store.
	 * (Like a self signed certificate or a none existing root certificate)
	 * Then for a smart client a dialog will be given, to give the user the ability to accept this certificate for the next time.
	 * For a Web or Headless client the system administrator does have to add that certificate (chain) to the java install on the server.
	 * See http://wiki.servoy.com/display/tutorials/Import+a+%28Root%29+certificate+in+the+java+cacerts+file
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var request = client.createGetRequest('http://www.servoy.com');
	 * var response = request.executeRequest();
	 * var httpCode = response.getStatusCode(); // httpCode 200 is ok"
	 * var content = response.getResponseBody();
	 *
	 * @param url
	 */
	public GetRequest js_createGetRequest(String url)
	{
		HttpProvider.setHttpClientProxy(client, url, proxyUser, proxyPassword);
		return new GetRequest(url, client, httpPlugin);
	}

	/**
	 * Creates a new delete request (a request to delete a resource on server).
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var request = client.createDeleteRequest('http://www.servoy.com/delete.me');
	 * var response = request.executeRequest();
	 * var httpCode = response.getStatusCode(); // httpCode 200 is ok"
	 * var content = response.getResponseBody();
	 *
	 * @param url
	 */
	public DeleteRequest js_createDeleteRequest(String url)
	{
		HttpProvider.setHttpClientProxy(client, url, proxyUser, proxyPassword);
		return new DeleteRequest(url, client, httpPlugin);
	}

	/**
	 * Creates a new patch request (used for granular updates).
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var request = client.createPatchRequest('http://jakarta.apache.org');
	 * request.setBodyContent('{"email": "newemail@newdomain.com"}','application/json');
	 * var httpCode = request.executeRequest().getStatusCode() // httpCode 200 is ok
	 *
	 * @param url
	 */
	public PatchRequest js_createPatchRequest(String url)
	{
		HttpProvider.setHttpClientProxy(client, url, proxyUser, proxyPassword);
		return new PatchRequest(url, client, httpPlugin);
	}

	/**
	 * Creates a new put request (similar to post request, contains information to be submitted).
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var request = client.createPutRequest('http://jakarta.apache.org');
	 * request.setFile('UploadMe.gif');
	 * var httpCode = putRequest.executeRequest().getStatusCode() // httpCode 200 is ok
	 *
	 * @param url
	 */
	public PutRequest js_createPutRequest(String url)
	{
		HttpProvider.setHttpClientProxy(client, url, proxyUser, proxyPassword);
		return new PutRequest(url, client, httpPlugin);
	}

	/**
	 * Creates a new options request (a request for information about communication options).
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var request = client.createOptionsRequest('http://www.servoy.com');
	 * var methods = request.getAllowedMethods(request.executeRequest());
	 *
	 * @param url
	 */
	public OptionsRequest js_createOptionsRequest(String url)
	{
		HttpProvider.setHttpClientProxy(client, url, proxyUser, proxyPassword);
		return new OptionsRequest(url, client, httpPlugin);
	}

	/**
	 * Creates a new head request (similar to get request, must not contain body content).
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var request = client.createHeadRequest('http://www.servoy.com');
	 * var response = request.executeRequest();
	 * var httpCode = response.getStatusCode(); // httpCode 200 is ok
	 * var header = response.getResponseHeaders('last-modified');
	 *
	 * @param url
	 */
	public HeadRequest js_createHeadRequest(String url)
	{
		HttpProvider.setHttpClientProxy(client, url, proxyUser, proxyPassword);
		return new HeadRequest(url, client, httpPlugin);
	}

	/**
	 * Creates a new trace request (debug request, server will just echo back).
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var response = request.executeRequest();
	 * var httpCode = response.getStatusCode(); // httpCode 200 is ok"
	 * var content = response.getResponseBody();
	 *
	 * @param url
	 */
	public TraceRequest js_createTraceRequest(String url)
	{
		HttpProvider.setHttpClientProxy(client, url, proxyUser, proxyPassword);
		return new TraceRequest(url, client, httpPlugin);
	}

	/**
	 * Set proxy credentials.
	 *
	 * @sample
	 * client.setClientProxyCredentials('my_proxy_username','my_proxy_password');
	 *
	 * @param userName
	 * @param password
	 */
	public void js_setClientProxyCredentials(String userName, String password)
	{
		if (!Utils.stringIsEmpty(userName))
		{
			this.proxyUser = userName;
			this.proxyPassword = password;
		}
	}

}
