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

import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * <p>The <code>OptionsRequest</code> class is used to perform HTTP OPTIONS requests,
 * allowing developers to interact with APIs to determine supported operations on a
 * resource. It supports synchronous and asynchronous request execution with various
 * authentication methods, providing flexibility for different use cases.</p>
 *
 * <p>Headers can be added to requests using the <code>addHeader(headerName, value)</code>
 * method, while the <code>executeRequest()</code> and <code>executeAsyncRequest()</code>
 * methods handle synchronous and asynchronous execution, respectively. Asynchronous
 * methods include callbacks for success and error handling, ensuring robust response
 * management. Authentication options, including Windows authentication, are also
 * supported.</p>
 *
 * <p>Additional functionality includes retrieving allowed HTTP methods using
 * <code>getAllowedMethods(res)</code> and configuring preemptive authentication with
 * <code>usePreemptiveAuthentication(b)</code>. These features enable efficient API
 * interaction and streamlined HTTP request handling.</p>
 *
 * <p>For an overview of the HTTP client functionality and configuration options, refer
 * to the <a href="./README.md">HTTP Client</a> and
 * <a href="./httpclientconfig.md">HTTP Client Config</a> documentation.</p>
 *
 * @author pbakker
 */
@ServoyDocumented
public class OptionsRequest extends BaseRequest
{
	/**
	 * Constant for specifying the options header
	 */
	protected static String OPTIONS_HEADER = "Allow"; //$NON-NLS-1$

	//only used by script engine
	public OptionsRequest()
	{
		super();
	}

	public OptionsRequest(String url, CloseableHttpAsyncClient hc, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, new HttpOptions(url), httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
	}

	/**
	 * Returns the supported HTTP Request operations as a String Array
	 *
	 * @param res The response request to get the allowed methods from.
	 *
	 * @sample
	 * var supportedOperations = request.getAllowedMethods()
	 * application.output(supportedOperations.join(','));
	 */
	public String[] js_getAllowedMethods(Response res)
	{
		return res.getAllowedMethods();
	}

}
