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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;

import com.servoy.j2db.MediaURLStreamHandler;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.Utils;

/**
 * @author jblok
 */
@ServoyDocumented(publicName = HttpPlugin.PLUGIN_NAME, scriptingName = "plugins." + HttpPlugin.PLUGIN_NAME)
public class HttpProvider implements IReturnedTypesProvider, IScriptable
{
	private final HttpPlugin httpPlugin;

	public HttpProvider(HttpPlugin httpPlugin)
	{
		this.httpPlugin = httpPlugin;
	}

	// default constructor
	public HttpProvider()
	{
		this.httpPlugin = null;
	}

	/**
	 * Get all page html in a variable, if this url is an https url that uses certificates unknown to Java
	 * then you have to use the HttpClient so that smart client users will get the unknown certificate dialog that they then can accept
	 * or you must make sure that those server certificates are stored in the cacerts of the java vm that is used (this is required for a web or headless client)
	 *
	 * @sample
	 * // get data using a default connection
	 * var pageData = plugins.http.getPageData('http://www.cnn.com');
	 *
	 * @param url
	 */
	public String js_getPageData(String url)
	{
		return getPageDataOldImplementation(url);
	}


	protected URL createURLFromString(String url) throws MalformedURLException
	{
		return createURLFromString(url, httpPlugin.getClientPluginAccess());
	}

	public static URL createURLFromString(String url, IClientPluginAccess access) throws MalformedURLException
	{
		return url.startsWith(MediaURLStreamHandler.MEDIA_URL_DEF) ? new URL(null, url, access.getMediaURLStreamHandler()) : new URL(url);
	}

	private String getPageDataOldImplementation(String input)
	{
		try
		{
			Pair<String, String> data = getPageDataOldImpl(createURLFromString(input), -1);
			return data.getLeft();
		}
		catch (Exception e)
		{
			Debug.error(e);
			return "";
		}
	}

	public static Pair<String, String> getPageDataOldImpl(URL url, int timeout)
	{
		StringBuffer sb = new StringBuffer();
		String charset = null;
		try
		{
			URLConnection connection = url.openConnection();
			if (timeout >= 0) connection.setConnectTimeout(timeout);
			InputStream is = connection.getInputStream();
			final String type = connection.getContentType();
			if (type != null)
			{
				final String[] parts = type.split(";");
				for (int i = 1; i < parts.length && charset == null; i++)
				{
					final String t = parts[i].trim();
					final int index = t.toLowerCase().indexOf("charset=");
					if (index != -1) charset = t.substring(index + 8);
				}
			}
			InputStreamReader isr = null;
			if (charset != null) isr = new InputStreamReader(is, charset);
			else isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			int read = 0;
			while ((read = br.read()) != -1)
			{
				sb.append((char)read);
			}
			br.close();
			isr.close();
			is.close();
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		return new Pair<String, String>(sb.toString(), charset);
	}

	public static Pair<String, String> getPageDataOldImpl(String input, int timeout)
	{
		try
		{
			return getPageDataOldImpl(new URL(input), timeout);
		}
		catch (Exception e)
		{
			Debug.error(e);
			return new Pair<String, String>("", null);
		}
	}

	public static BasicCredentialsProvider setHttpClientProxy(Builder requestConfigBuilder, String url, String proxyUser, String proxyPassword,
		String proxyHostName, int port)
	{
		String proxyHost = null;
		int proxyPort = 8080;

		if (proxyHostName != null)
		{
			proxyHost = proxyHostName;
			proxyPort = port;
			HttpHost host = new HttpHost(proxyHost, proxyPort);
			requestConfigBuilder.setProxy(host);
		}
		else
		{
			try
			{
				System.setProperty("java.net.useSystemProxies", "true");
				URI uri = new URI(url);
				List<Proxy> proxies = ProxySelector.getDefault().select(uri);
				if (proxies != null && requestConfigBuilder != null)
				{
					for (Proxy proxy : proxies)
					{
						if (proxy.address() != null && proxy.address() instanceof InetSocketAddress)
						{
							InetSocketAddress address = (InetSocketAddress)proxy.address();
							proxyHost = address.getHostName();
							HttpHost host = new HttpHost(address.getHostName(), address.getPort());
							requestConfigBuilder.setProxy(host);
							break;
						}
					}
				}
			}
			catch (Exception ex)
			{
				Debug.log(ex);
			}
			if (proxyHost == null && System.getProperty("http.proxyHost") != null && !"".equals(System.getProperty("http.proxyHost")))
			{
				proxyHost = System.getProperty("http.proxyHost");
				try
				{
					proxyPort = Integer.parseInt(System.getProperty("http.proxyPort"));
				}
				catch (Exception ex)
				{
					//ignore
				}
				HttpHost host = new HttpHost(proxyHost, proxyPort);
				requestConfigBuilder.setProxy(host);
			}
		}

		if (proxyUser != null)
		{
			BasicCredentialsProvider bcp = new BasicCredentialsProvider();
			bcp.setCredentials(new AuthScope(proxyHost, proxyPort),
				new UsernamePasswordCredentials(proxyUser, proxyPassword != null ? proxyPassword.toCharArray() : null));
			return bcp;
		}
		return null;
	}

	/**
	 * Get media (binary data) such as images in a variable. It also supports gzip-ed content.
	 * If this url is an https url that uses certificates unknown to Java
	 * then you have to use the HttpClient so that smart client users will get the unknown certificate dialog that they then can accept
	 * or you must make sure that those server certificates are stored in the cacerts of the java vm that is used (this is required for a web or headless client)
	 *
	 * @sample
	 * var image_byte_array = plugins.http.getMediaData('http://www.cnn.com/cnn.gif');
	 *
	 * @param url
	 */
	public byte[] js_getMediaData(String url)
	{
		if (url == null) return null;
		ByteArrayOutputStream sb = new ByteArrayOutputStream();
		try
		{
			URLConnection connection = createURLFromString(url).openConnection();

			InputStream is = connection.getInputStream();
			BufferedInputStream bis = new BufferedInputStream(is);
			Utils.streamCopy(bis, sb);
			bis.close();
			is.close();
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		return sb.toByteArray();
	}


	/**
	 * Create an http client (like a web browser with session binding) usable todo multiple request/posts in same server session
	 * .
	 * WARNING: Make sure you call client.close() on it after you used this client object to clean up resources.
	 * Starting a HTTPClient is the same as starting an actual browser without UI!
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 */
	public HttpClient js_createNewHttpClient()
	{
		HttpClient httpClient = new HttpClient(httpPlugin);
		httpPlugin.clientCreated(httpClient);
		return httpClient;
	}

	/**
	 * Create an http client (like a web browser with session binding) usable todo multiple request/posts in same server session.
	 *
	 * WARNING: Make sure you call client.close() on it after you used this client object to clean up resources.
	 * Starting a HTTPClient is the same as starting an actual browser without UI!
	 *
	 * @param config httpclient config
	 * @sample
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public HttpClient js_createNewHttpClient(HttpClientConfig config)
	{
		HttpClient httpClient = new HttpClient(httpPlugin, config);
		httpPlugin.clientCreated(httpClient);
		return httpClient;
	}

	/**
	 * Create a http client config
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 */
	public HttpClientConfig js_createNewHttpClientConfig()
	{
		return new HttpClientConfig();
	}

	/**
	 * @see com.servoy.j2db.scripting.IScriptObject#getAllReturnedTypes()
	 */
	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { PatchRequest.class, PostRequest.class, PutRequest.class, GetRequest.class, DeleteRequest.class, OptionsRequest.class, HeadRequest.class, TraceRequest.class, Cookie.class, Response.class, HttpClient.class, HttpClientConfig.class, HTTP_STATUS.class };
	}
}
