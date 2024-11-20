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

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * <p>The <code>GetRequest</code> class facilitates the creation and execution of
 * HTTP GET requests with support for custom headers, authentication, and both
 * synchronous and asynchronous operations.</p>
 *
 * <h2>Functionality</h2>
 *
 * <p>Headers can be added using the <code>addHeader(headerName, value)</code> method.
 * Requests support execution in synchronous mode with <code>executeRequest()</code>
 * or asynchronously via <code>executeAsyncRequest()</code>, which includes success
 * and error callbacks for handling responses. Authentication options include
 * username-password combinations and Windows authentication. Preemptive authentication
 * can be enabled using <code>usePreemptiveAuthentication(b)</code> to send credentials
 * in the request header for scenarios requiring immediate authorization.</p>
 *
 * <p>For further details, refer to the
 * <a href="../../../../guides/develop/programming-guide/creating-rest-apis.md#supporting-get-requests">
 * Supporting GET Requests</a> section.</p>
 *
 * @author pbakker
 *
 */
@ServoyDocumented
public class GetRequest extends BaseRequest
{
	//only used by script engine
	public GetRequest()
	{
		super();
	}

	public GetRequest(String url, CloseableHttpAsyncClient hc, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, new HttpGet(url), httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
	}
}
