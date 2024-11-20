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

import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * <p>The <code>HeadRequest</code> class enables the creation and execution of
 * HTTP HEAD requests, allowing retrieval of metadata for resources without
 * fetching their full content. It supports headers, authentication, and both
 * synchronous and asynchronous execution.</p>
 *
 * <h2>Functionality</h2>
 *
 * <p>Headers can be added using the <code>addHeader(headerName, value)</code> method.
 * Requests can be executed synchronously with <code>executeRequest()</code> or
 * asynchronously through <code>executeAsyncRequest()</code>, which supports success
 * and error callbacks. Authentication methods include username-password and windows
 * authentication. The <code>usePreemptiveAuthentication(b)</code> method enables
 * credentials to be sent in the header, useful for specific server configurations
 * requiring immediate authorization.</p>
 *
 * @author pbakker
 */
@ServoyDocumented
public class HeadRequest extends BaseRequest
{
	//only used by script engine
	public HeadRequest()
	{
		super();
	}

	public HeadRequest(String url, CloseableHttpAsyncClient hc, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, new HttpHead(url), httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
	}
}
