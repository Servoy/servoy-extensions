/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2020 Servoy BV

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

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * The <code>HttpClientConfig</code> object provides configurable properties to manage the behavior of an HTTP client,
 * including settings for SSL/TLS, connection limits, protocol preferences, and user agent configuration.
 *
 * <h2>Functionality</h2>
 *
 * <h3>SSL/TLS and Security</h3>
 * <ul>
 *   <li><code>certPath</code> and <code>certPassword</code> specify the client certificate location and password.</li>
 *   <li><code>trustStorePath</code> and <code>trustStorePassword</code> define the truststore for trusted certificates.</li>
 *   <li><code>protocol</code> sets the TLS protocol, defaulting to TLS.</li>
 *   <li><code>hostValidation</code> disables hostname validation, primarily for testing purposes.</li>
 * </ul>
 *
 * <h3>Connection Management</h3>
 * <ul>
 *   <li><code>keepAliveDuration</code> sets the duration (in seconds) for keeping connections alive.</li>
 *   <li><code>maxConnectionsPerRoute</code> and <code>maxTotalConnections</code> limit the number of connections managed by the client.</li>
 *   <li><code>maxIOThreadCount</code> determines the number of input/output threads for the client.</li>
 * </ul>
 *
 * <h3>Additional Features</h3>
 * <ul>
 *   <li><code>enableRedirects</code> enables or disables automatic following of HTTP redirects.</li>
 *   <li><code>forceHttp1</code> forces HTTP/1.1 usage when HTTP/2 compatibility issues arise.</li>
 *   <li><code>multiPartLegacyMode</code> switches multipart request handling to a non-buffered mode.</li>
 *   <li><code>userAgent</code> allows customization of the HTTP client’s user agent string.</li>
 * </ul>
 *
 * @author lvostinar
 */
@ServoyDocumented
public class HttpClientConfig implements IScriptable, IJavaScriptType
{
	String protocol;
	int keepAliveDuration = -1;
	String userAgent;
	int maxIOThreadCount = -1;
	boolean forceHttp1 = false;
	boolean enableRedirects = true;
	boolean hostValidation = true;
	int maxTotalConnections = -1;
	int maxConnectionsPerRoute = -1;
	boolean multiPartLegacyMode = false;
	String trustStorePath;
	String certPassword;
	String certPath;
	String trustStorePassword;

	public HttpClientConfig()
	{
	}

	/**
	 * Gets/Sets maximum number of input/output threads per client, default value is 2.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.maxIOThreadCount = 5;
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public int js_getMaxIOThreadCount()
	{
		return maxIOThreadCount;
	}

	public void js_setMaxIOThreadCount(int maxIOThreadCount)
	{
		this.maxIOThreadCount = maxIOThreadCount;
	}

	/**
	 * Gets/Sets maximum number of connections used by Connection Manager.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.maxTotalConnections = 5;
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public int js_getMaxTotalConnections()
	{
		return maxTotalConnections;
	}

	public void js_setMaxTotalConnections(int maxTotalConnections)
	{
		this.maxTotalConnections = maxTotalConnections;
	}

	/**
	 * Gets/Sets maximum number of connections per route used by Connection Manager.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.maxConnectionsPerRoute = 2;
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public int js_getMaxConnectionsPerRoute()
	{
		return maxConnectionsPerRoute;
	}

	public void js_setMaxConnectionsPerRoute(int maxConnectionsPerRoute)
	{
		this.maxConnectionsPerRoute = maxConnectionsPerRoute;
	}

	/**
	 * Force the use of http1, use this if there are problems connecting to a server that does use http/2 but uses old cipher suites
	 * or if there are other problems like http/2 not setting the content length and the server still wants it.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.forceHttp1 = true;
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public boolean js_getForceHttp1()
	{
		return forceHttp1;
	}

	public void js_setForceHttp1(boolean force)
	{
		this.forceHttp1 = force;
	}

	public void js_setHostValidation(boolean validation)
	{
		this.hostValidation = validation;
	}

	/**
	 * Disable hostname certificate validation. This should be used only for testing purposes, because this is not secure!
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.hostValidation = false;
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public boolean js_getHostValidation()
	{
		return hostValidation;
	}

	/**
	 * Sets whether client should follow redirects or you want to do it manually. Default value is true.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.enableRedirects = false;
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public boolean js_getEnableRedirects()
	{
		return this.enableRedirects;
	}

	public void js_setEnableRedirects(boolean enableRedirects)
	{
		this.enableRedirects = enableRedirects;
	}

	/**
	 * Gets/Sets which TLS protocol to use, default value is TLS.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.protocol = "TLSv1.2";
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public String js_getProtocol()
	{
		return protocol;
	}

	public void js_setProtocol(String protocol)
	{
		this.protocol = protocol;
	}

	/**
	 * Gets/Sets keep alive duration in seconds for a connection, default is -1 (no duration specified).
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.keepAliveDuration = 5;
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public int js_getKeepAliveDuration()
	{
		return keepAliveDuration;
	}

	public void js_setKeepAliveDuration(int duration)
	{
		this.keepAliveDuration = duration;
	}

	/**
	 * Gets/Sets custom userAgent to use.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.userAgent = "Mozilla/5.0 Firefox/26.0";
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public String js_getUserAgent()
	{
		return userAgent;
	}

	public void js_setUserAgent(String user_agent)
	{
		this.userAgent = user_agent;
	}

	/**
	 * Sets whether multipart request should be written in one go(not using buffering). Default value is false.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.multiPartLegacyMode = true;
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public boolean js_getMultiPartLegacyMode()
	{
		return this.multiPartLegacyMode;
	}

	public void js_setMultiPartLegacyMode(boolean multiPartLegacyMode)
	{
		this.multiPartLegacyMode = multiPartLegacyMode;
	}

	/**
	 * Gets/Sets the certificate path.
	 * The following sample sets up an HttpClient with custom SSL/TLS configuration using a PKCS12 keystore for client certificates and
	 * a JKS truststore for trusted certificate authorities.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.certPath = "";
	 * config.certPassword = "";
	 * config.trustStorePassword = "";
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public String js_getCertPath()
	{
		return certPath;
	}

	public void js_setCertPath(String certPath)
	{
		this.certPath = certPath;
	}

	/**
	 * Gets/Sets the certificate password.
	 * The following sample sets up an HttpClient with custom SSL/TLS configuration using a PKCS12 keystore for client certificates and
	 * a JKS truststore for trusted certificate authorities.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.certPath = "";
	 * config.certPassword = "";
	 * config.trustStorePassword = "";
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public String js_getCertPassword()
	{
		return certPassword;
	}

	public void js_setCertPassword(String certPassword)
	{
		this.certPassword = certPassword;
	}

	/**
	 * Gets/Sets the password to the java truststore where to import the certificate.
	 * The following sample sets up an HttpClient with custom SSL/TLS configuration using a PKCS12 keystore for client certificates and
	 * a JKS truststore for trusted certificate authorities.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.certPath = "";
	 * config.certPassword = "";
	 * config.trustStorePassword = "";
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public String js_getTrustStorePassword()
	{
		return trustStorePassword;
	}

	public void js_setTrustStorePassword(String trustStorePassword)
	{
		this.trustStorePassword = trustStorePassword;
	}
}
