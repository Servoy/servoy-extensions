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

import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * <p>The <code>TraceRequest</code> object enables executing HTTP TRACE requests with advanced configuration
 * options. It allows adding custom headers, managing authentication credentials, and handling both synchronous
 * and asynchronous request executions.</p>
 *
 * <p>For asynchronous requests, success and error callbacks can be defined to process responses or handle
 * errors effectively.</p>
 *
 * <p>Notable methods include <code>addHeader</code>, which adds custom headers to the request, and
 * <code>executeRequest</code>, which performs the TRACE request synchronously. The object also supports
 * asynchronous execution through <code>executeAsyncRequest</code>, providing flexibility for non-blocking
 * operations. The <code>usePreemptiveAuthentication</code> method can enable credentials to be sent in the
 * initial request, optimizing performance for specific scenarios.</p>
 *
 * <p>For broader context on HTTP operations, refer to the
 * <a href="https://docs.servoy.com/reference/servoyextensions/server-plugins/http">Http plugin</a> documentation.</p>
 *
 * @author pbakker
 */
@ServoyDocumented
public class TraceRequest extends BaseRequest
{
	//only used by script engine
	public TraceRequest()
	{
		super();
	}

	public TraceRequest(String url, CloseableHttpAsyncClient hc, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, new HttpTrace(url), httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
	}
}
