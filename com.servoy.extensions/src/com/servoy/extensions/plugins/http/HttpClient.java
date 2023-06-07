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
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

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
	CloseableHttpAsyncClient client;
	CookieStore cookieStore;
	Builder requestConfigBuilder;
	private final HttpPlugin httpPlugin;
	private String proxyUser;
	private String proxyPassword;
	private String proxyHost;
	private int proxyPort = 8080;

	public HttpClient(HttpPlugin httpPlugin)
	{
		this(httpPlugin, null);
	}

	public HttpClient(HttpPlugin httpPlugin, HttpClientConfig config)
	{
		this.httpPlugin = httpPlugin;

		HttpAsyncClientBuilder builder = HttpAsyncClients.custom();
		builder.setIOReactorConfig(org.apache.hc.core5.reactor.IOReactorConfig.custom().setSoKeepAlive(true)
			.setIoThreadCount((config != null && config.maxIOThreadCount >= 0) ? config.maxIOThreadCount : 2).build());
		requestConfigBuilder = RequestConfig.custom();
		requestConfigBuilder.setCircularRedirectsAllowed(true);
		if (config != null && !config.enableRedirects) requestConfigBuilder.setRedirectsEnabled(false);

		cookieStore = new BasicCookieStore();
		builder.setDefaultCookieStore(cookieStore);
		try
		{
			final SSLContext sslContext;
			final ClientTlsStrategyBuilder tlsFactory;
			if (config != null && !config.hostValidation)
			{
				sslContext = SSLContexts.custom().loadTrustMaterial(new TrustStrategy()
				{
					@Override
					public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException
					{
						return true; // Trust all certificates
					}
				}).build();
				HostnameVerifier allowAllHosts = new NoopHostnameVerifier();
				tlsFactory = ClientTlsStrategyBuilder.create().useSystemProperties()
					.setSslContext(sslContext)
					.setHostnameVerifier(allowAllHosts)
					.setTlsDetailsFactory(createFactoryForJava11());
			}
			else
			{
				final AllowedCertTrustStrategy allowedCertTrustStrategy = new AllowedCertTrustStrategy();
				sslContext = SSLContexts.custom().loadTrustMaterial(allowedCertTrustStrategy).build();
				tlsFactory = ClientTlsStrategyBuilder.create().useSystemProperties()
					.setSslContext(sslContext)
					.setTlsDetailsFactory(createFactoryForJava11());
			}

			if (config != null && config.protocol != null)
			{
				tlsFactory.setTlsVersions(config.protocol);
			}
			TlsStrategy tlsStrat = tlsFactory.build();
			final PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
				.setTlsStrategy(tlsStrat)
				.setMaxConnPerRoute(5)
				.build();
			builder.setConnectionManager(cm);
		}
		catch (Exception ex)
		{
			Debug.error("Can't set up ssl socket factory", ex); //$NON-NLS-1$
		}
		if (config != null)
		{
			if (config.keepAliveDuration >= 0)
			{
				builder.setKeepAliveStrategy(new ConnectionKeepAliveStrategy()
				{
					@Override
					public TimeValue getKeepAliveDuration(HttpResponse response, HttpContext context)
					{
						TimeValue duration = DefaultConnectionKeepAliveStrategy.INSTANCE.getKeepAliveDuration(response, context);
						if (duration != null)
						{
							return duration;
						}
						return TimeValue.ofMilliseconds(config.keepAliveDuration * 1000);
					}

				});
			}
			if (config.userAgent != null)
			{
				builder.setUserAgent(config.userAgent);
			}
			if (config.forceHttp1)
			{
				builder.setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1);
			}
		}
		client = builder.build();
		client.start();
	}

	/**
	 * @return
	 */
	protected Factory<SSLEngine, TlsDetails> createFactoryForJava11()
	{
		try
		{
			final Method method = SSLEngine.class.getMethod("getApplicationProtocol"); //$NON-NLS-1$
			return new Factory<SSLEngine, TlsDetails>()
			{
				@Override
				public TlsDetails create(final SSLEngine sslEngine)
				{
					try
					{
						return new TlsDetails(sslEngine.getSession(), (String)method.invoke(sslEngine, (Object[])null));
					}
					catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
					{
						Debug.error(e);
					}
					return null;
				}
			};
		}
		catch (NoSuchMethodException | SecurityException e)
		{
			return null;
		}
	}

	/**
	 * releases all resources that this client has, should be called after usage.
	 */
	public void js_close()
	{
		try
		{
			client.close();
			httpPlugin.clientClosed(this);
		}
		catch (IOException e)
		{
			Debug.error(e);
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
		requestConfigBuilder.setResponseTimeout(Timeout.ofMilliseconds(timeout));
		requestConfigBuilder.setConnectTimeout(Timeout.ofMilliseconds(timeout));
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
		cookieStore.addCookie(cookie);
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
		List<org.apache.hc.client5.http.cookie.Cookie> cookies = cookieStore.getCookies();
		for (org.apache.hc.client5.http.cookie.Cookie element : cookies)
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
		List<org.apache.hc.client5.http.cookie.Cookie> cookies = cookieStore.getCookies();
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
	 * See https://wiki.servoy.com/display/tutorials/Import+a+%28Root%29+certificate+in+the+java+cacerts+file
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
		return new PostRequest(url, client, httpPlugin, requestConfigBuilder,
			HttpProvider.setHttpClientProxy(requestConfigBuilder, url, proxyUser, proxyPassword, proxyHost, proxyPort));
	}

	/**
	 * Creates a new get request (retrieves whatever information is stored on specified url).
	 * If this url is a https ssl encrypted url which certificates are not in the java certificate store.
	 * (Like a self signed certificate or a none existing root certificate)
	 * Then for a smart client a dialog will be given, to give the user the ability to accept this certificate for the next time.
	 * For a Web or Headless client the system administrator does have to add that certificate (chain) to the java install on the server.
	 * See https://wiki.servoy.com/display/tutorials/Import+a+%28Root%29+certificate+in+the+java+cacerts+file
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
		return new GetRequest(url, client, httpPlugin, requestConfigBuilder,
			HttpProvider.setHttpClientProxy(requestConfigBuilder, url, proxyUser, proxyPassword, proxyHost, proxyPort));
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
		return new DeleteRequest(url, client, httpPlugin, requestConfigBuilder,
			HttpProvider.setHttpClientProxy(requestConfigBuilder, url, proxyUser, proxyPassword, proxyHost, proxyPort));
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
		return new PatchRequest(url, client, httpPlugin, requestConfigBuilder,
			HttpProvider.setHttpClientProxy(requestConfigBuilder, url, proxyUser, proxyPassword, proxyHost, proxyPort));
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
		return new PutRequest(url, client, httpPlugin, requestConfigBuilder,
			HttpProvider.setHttpClientProxy(requestConfigBuilder, url, proxyUser, proxyPassword, proxyHost, proxyPort));
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
		return new OptionsRequest(url, client, httpPlugin, requestConfigBuilder,
			HttpProvider.setHttpClientProxy(requestConfigBuilder, url, proxyUser, proxyPassword, proxyHost, proxyPort));
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
		return new HeadRequest(url, client, httpPlugin, requestConfigBuilder,
			HttpProvider.setHttpClientProxy(requestConfigBuilder, url, proxyUser, proxyPassword, proxyHost, proxyPort));
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
		return new TraceRequest(url, client, httpPlugin, requestConfigBuilder,
			HttpProvider.setHttpClientProxy(requestConfigBuilder, url, proxyUser, proxyPassword, proxyHost, proxyPort));
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

	/**
	 * Set proxy server.
	 *
	 * @sample
	 * client.setClientProxyServer('server',port);
	 *
	 * @param hostname - proxy host // null value will clear proxyHost settings;
	 * @param port - proxy port //null value will clear proxyHost settings;
	 */
	public void js_setClientProxyServer(String hostname, Integer port)
	{
		if (!Utils.stringIsEmpty(hostname) && port != null)
		{
			this.proxyHost = hostname;
			this.proxyPort = Math.abs(port.intValue());
		}
		else
		{
			this.proxyHost = null;
			this.proxyPort = 8080;
		}
	}

	private static final class CertificateSSLSocketFactoryHandler extends SSLConnectionSocketFactory
	{
		private final AllowedCertTrustStrategy allowedCertTrustStrategy;
		private final HttpPlugin httpPlugin;

		/**
		 * @param sslContext
		 * @param allowedCertTrustStrategy
		 */
		private CertificateSSLSocketFactoryHandler(SSLContext sslContext, AllowedCertTrustStrategy allowedCertTrustStrategy, HttpPlugin httpPlugin)
		{
			super(sslContext);
			this.allowedCertTrustStrategy = allowedCertTrustStrategy;
			this.httpPlugin = httpPlugin;
		}

		@Override
		public Socket connectSocket(final TimeValue connectTimeout,
			final Socket socket,
			final HttpHost host,
			final InetSocketAddress remoteAddress,
			final InetSocketAddress localAddress,
			final HttpContext context) throws IOException
		{
			try
			{
				return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
			}
			catch (SSLPeerUnverifiedException ex)
			{
				X509Certificate[] lastCertificates = allowedCertTrustStrategy.getAndClearLastCertificates();
				if (lastCertificates != null)
				{
					// allow for next time
					if (httpPlugin.getClientPluginAccess().getApplicationType() == IClientPluginAccess.CLIENT ||
						httpPlugin.getClientPluginAccess().getApplicationType() == IClientPluginAccess.RUNTIME)
					{
						// show dialog
						CertificateDialog dialog = new CertificateDialog(
							((ISmartRuntimeWindow)this.httpPlugin.getClientPluginAccess().getCurrentRuntimeWindow()).getWindow(), remoteAddress,
							lastCertificates);
						if (dialog.shouldAccept())
						{
							allowedCertTrustStrategy.add(lastCertificates);
							// try it again now with the new chain.
							return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
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
	}


}
