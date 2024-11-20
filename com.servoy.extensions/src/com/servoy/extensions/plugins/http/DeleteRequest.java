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

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * <p>The <code>DeleteRequest</code> object facilitates the creation and execution of
 * HTTP DELETE requests, offering configurable parameters, headers, and authentication
 * options. It supports both synchronous and asynchronous workflows while accommodating
 * authentication needs like preemptive authentication and domain-based mechanisms.</p>
 *
 * <p>Asynchronous execution options enable efficient response and error handling through
 * callbacks, while support for enforcing formats such as <code>multipart/form-data</code>
 * enhances compatibility with various server requirements.</p>
 *
 * @author pbakker
 */
@ServoyDocumented
public class DeleteRequest extends BaseEntityEnclosingRequest
{
	//only used by script engine
	public DeleteRequest()
	{
		super();
	}

	public DeleteRequest(String url, CloseableHttpAsyncClient hc, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, new HttpDelete(url), httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
	}
}
