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
 * @author lvostinar
 *
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

	/**
	 * Disable SSL certificate validation. Use it only for testing purpose.
	 *
	 * @sample
	 * var config = plugins.http.createNewHttpClientConfig();
	 * config.disableSSL = true;
	 * var client = plugins.http.createNewHttpClient(config);
	 */
	public void js_setHostValidation(boolean validation)
	{
		this.hostValidation = validation;
	}

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
}
