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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;

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
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.mozilla.javascript.NativePromise;
import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.ISmartRuntimeWindow;
import com.servoy.j2db.scripting.Deferred;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * <p>A wrapper for <code>Apache HttpClient</code> for executing requests like GET, POST, PUT, and more, with
 * support for cookies, proxies, and timeouts.</p>
 *
 * <h2>Features</h2>
 *
 * <p>The client facilitates various HTTP requests (e.g., GET, POST, DELETE) and
 * supports configuration options like cookies, proxy servers, and timeouts. It
 * enables both synchronous and asynchronous communication, offering flexibility
 * for different use cases.</p>
 *
 * <p>For configuration details, see the
 * <a href="https://docs.servoy.com/reference/servoyextensions/server-plugins/http/httpclientconfig">Http client configuration</a> section.</p>
 */
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
			SSLContext sslContext = null;
			HostnameVerifier hostnameVerifier = null;

			// Determine SSLContext
			if (config != null)
			{
				if (config.certPath != null)
				{
					sslContext = createSSLContextWithCert(config);
				}
				else if (!config.hostValidation)
				{
					sslContext = createTrustAllSSLContext();
					hostnameVerifier = new NoopHostnameVerifier();
				}
			}
			else
			{
				sslContext = createAllowedCertSSLContext();
			}

			// Create and configure the TLS strategy
			ClientTlsStrategyBuilder tlsFactory = createTlsFactory(sslContext, hostnameVerifier, config);

			// Build and configure the ConnectionManager
			PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create();
			if (config != null && config.maxTotalConnections >= 0)
			{
				connectionManagerBuilder.setMaxConnTotal(config.maxTotalConnections);
			}
			int maxConnPerRoute = config != null && config.maxConnectionsPerRoute >= 0 ? config.maxConnectionsPerRoute : 5;
			connectionManagerBuilder.setMaxConnPerRoute(maxConnPerRoute);
			connectionManagerBuilder.setTlsStrategy(tlsFactory.build());
			builder.setConnectionManager(connectionManagerBuilder.build());
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
			if (config.multiPartLegacyMode)
			{
				// write everything in one chunk
				builder.setHttp1Config(Http1Config.custom().setChunkSizeHint(Integer.MAX_VALUE).build());
			}
		}

		client = builder.build();
		client.start();
	}

	private SSLContext createSSLContextWithCert(HttpClientConfig config) throws Exception
	{
		File pKeyFile = new File(config.certPath);
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream keyInput = new FileInputStream(pKeyFile))
		{
			keyStore.load(keyInput, config.certPassword != null ? config.certPassword.toCharArray() : null);
		}

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, config.certPassword != null ? config.certPassword.toCharArray() : null);

		KeyStore trustStore = KeyStore.getInstance("JKS");
		String relativeCacertsPath = "/lib/security/cacerts/".replace("/", File.separator);
		String cacerts = System.getProperty("java.home") + relativeCacertsPath;
		try (InputStream trustInput = new FileInputStream(cacerts))
		{
			String password = config.trustStorePassword != null ? config.trustStorePassword : "changeit";
			trustStore.load(trustInput, password.toCharArray());
		}

		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);

		SSLContext sslContext = SSLContext.getInstance(config.protocol != null ? config.protocol : "TLSv1.2");
		sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

		return sslContext;
	}

	private SSLContext createTrustAllSSLContext() throws Exception
	{
		return SSLContexts.custom().loadTrustMaterial(new TrustStrategy()
		{
			@Override
			public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException
			{
				return true; // Trust all certificates
			}
		}).build();
	}

	private SSLContext createAllowedCertSSLContext() throws Exception
	{
		AllowedCertTrustStrategy allowedCertTrustStrategy = new AllowedCertTrustStrategy();
		return SSLContexts.custom().loadTrustMaterial(allowedCertTrustStrategy).build();
	}

	private ClientTlsStrategyBuilder createTlsFactory(SSLContext sslContext, HostnameVerifier hostnameVerifier, HttpClientConfig config)
	{
		ClientTlsStrategyBuilder tlsFactory = ClientTlsStrategyBuilder.create()
			.useSystemProperties()
			.setSslContext(sslContext)
			.setTlsDetailsFactory(createFactoryForJava11());

		if (hostnameVerifier != null)
		{
			tlsFactory.setHostnameVerifier(hostnameVerifier);
		}

		if (config != null && config.protocol != null)
		{
			tlsFactory.setTlsVersions(config.protocol);
		}

		return tlsFactory;
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
	 *
	 * @return true if the cookie was successfully set; otherwise, false.
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
	 *
	 * @return true if the cookie was successfully set; otherwise, false.
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
	 *
	 * @return true if the cookie was successfully set; otherwise, false.
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
	 *
	 * @return true if the cookie was successfully set; otherwise, false.
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
	 *
	 * @return true if the cookie was successfully set; otherwise, false.
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
	 *
	 * @return the cookie with the specified name, or null if it does not exist.
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
	 *
	 * @return an array of all cookies currently stored in the client.
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
	 * Execute multiple requests asynchronously, it assumes that all request are to the same server (it gives the same username, password, workstation and domain to all requests).
	 *
	 * A Promise is returned that resolves with an array of Response objects when all requests are complete in the same order as the Request objects.
	 * Because some request can fail and others can just work, this promise will always just resolve (not reject) with response object having the message of the error or the actual normal response.
	 *
	 * BaseRequest[] request
	 *
	 * @param requests
	 * @param username the user name
	 * @param password the password
	 * @param workstation The workstation the authentication request is originating from.
	 * @param domain The domain to authenticate within.
	 *
	 * @return The promise object that resolves with an array of Response objects when all requests are complete in the same order as the Request objects.
	 */
	@JSFunction
	public NativePromise executeRequest(final BaseRequest[] requests, final String username, final String password, final String workstation,
		final String domain)
	{

		Deferred deferred = new Deferred(httpPlugin.getClientPluginAccess());

		httpPlugin.getClientPluginAccess().getExecutor().execute(() -> {
			List<Object> responses = new ArrayList<>(requests.length);

			for (BaseRequest request : requests)
			{
				try
				{
					responses.add(request.executeRequest(username, password, workstation, domain, workstation != null, null, null, null));

				}
				catch (Exception e)
				{
					responses.add(e);
				}
			}

			List<Response> results = new ArrayList<>(requests.length);
			for (int i = 0; i < responses.size(); i++)
			{
				try
				{
					Object object = responses.get(i);
					if (object instanceof Future future)
					{
						FileOrTextHttpResponse baseRequest = (FileOrTextHttpResponse)future.get();
						results.add(new Response(baseRequest, requests[i].getMethod()));
					}
					else if (object instanceof Exception e)
					{
						results.add(new Response(e.getMessage()));
					}
				}
				catch (ExecutionException | InterruptedException ee)
				{
					results.add(new Response(ee.getMessage()));
				}
				catch (CancellationException ce)
				{
					results.add(new Response("Request was cancelled"));
				}
			}
			deferred.resolve(results.toArray());
		});
		return deferred.getPromise();


	}

	/**
	 * Execute multiple requests asynchronously, it assumes that all request are to the same server (it gives the same username, password to all requests).
	 *
	 * A Promise is returned that resolves with an array of Response objects when all requests are complete in the same order as the Request objects.
	 * Because some request can fail and others can just work, this promise will always just resolve (not reject) with response object having the message of the error or the actual normal response.
	 *
	 * @sampleas execute(BaseRequest[])
	 *
	 * @param requests
	 * @param username the user name
	 * @param password the password
	 *
	 * @return The promise object that resolves with an array of Response objects when all requests are complete in the same order as the Request objects.
	 */
	@JSFunction
	public NativePromise executeRequest(final BaseRequest[] requests, final String username, final String password)
	{
		return executeRequest(requests, username, password, null, null);
	}

	/**
	 * Execute multiple requests asynchronously.
	 * A Promise is returned that resolves with an array of Response objects when all requests are complete in the same order as the Request objects.
	 * Because some request can fail and others can just work, this promise will always just resolve (not reject) with response object having the message of the error or the actual normal response.
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var requests = [];
	 * requests.push(client.createGetRequest("https://www.google.com"))
	 * requests.push(client.createGetRequest("https://servoy.com"))
	 * var promise = client.execute(requests)
	 * promise.then(/** @type {Array<plugins.http.Response>} *&#47;
	 * responses => {
	 *    for (var index = 0; index < responses.length; index++) {
	 *      application.output(responses[index].getStatusCode())
	 *      application.output(responses[index].getResponseBody().substring(0,100));
	 *      application.output(responses[index].getFileUpload());
	 *      responses[index].close();
	 *    }
	 * });
	 *
	 * @param requests
	 *
	 * @return The promise object that resolves with an array of Response objects when all requests are complete in the same order as the Request objects.
	 */
	@JSFunction
	public NativePromise execute(BaseRequest[] requests)
	{
		return executeRequest(requests, null, null, null, null);
	}

	/**
	 * Create a new post request ( Origin server should accept/process the submitted data.)
	 * If this url is a https ssl encrypted url which certificates are not in the java certificate store.
	 * (Like a self signed certificate or a none existing root certificate)
	 * The system administrator does have to add that certificate (chain) to the java install on the server.
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
	 *
	 * @return a PostRequest object for creating a POST request to the specified URL.
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
	 * The system administrator does have to add that certificate (chain) to the java install on the server.
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
	 *
	 * @return a GetRequest object for creating a GET request to the specified URL.
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
	 *
	 * @return a DeleteRequest object for creating a DELETE request to the specified URL.
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
	 *
	 * @return a PatchRequest object for creating a PATCH request to the specified URL.
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
	 *
	 * @return a PutRequest object for creating a PUT request to the specified URL.
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
	 *
	 * @return an OptionsRequest object for creating an OPTIONS request to the specified URL.
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
	 *
	 * @return a HeadRequest object for creating a HEAD request to the specified URL.
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
	 *
	 * @return a TraceRequest object for creating a TRACE request to the specified URL.
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
