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

import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

import com.servoy.extensions.plugins.file.JSFile;
import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * <p>The <code>PutRequest</code> class provides functionality for constructing and executing HTTP PUT requests.
 * It supports adding files, parameters, and headers, with automatic MIME type detection based on file names
 * or content. Users can override MIME types when needed and include single or multiple files in a PUT request.
 * Multipart formatting can be enforced to meet server requirements even when only a single item is included.</p>
 *
 * <p>The class allows requests to be executed synchronously or asynchronously. Asynchronous execution includes
 * support for callbacks to handle success and error cases, with optional arguments for distinguishing between
 * requests in complex scenarios. Synchronous execution returns a <code>Response</code> object for direct
 * interaction with server replies.</p>
 *
 * <p>Authentication options include preemptive credential submission to optimize uploads where servers
 * require immediate verification.</p>
 *
 * <p>The class also provides methods to set body content, specify charsets, and directly assign files for
 * PUT operations. A sample for creating a <code>PutRequest</code> instance can be found here:
 * <a href="./httpclient.md#createputrequesturl">createPutRequest</a>.</p>
 *
 * @author pbakker
 *
 */
@ServoyDocumented
public class PutRequest extends BaseEntityEnclosingRequest
{
	//only used by script engine
	public PutRequest()
	{
		super();
	}

	public PutRequest(String url, CloseableHttpAsyncClient hc, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, new HttpPut(url), httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
	}

	/**
	 * Set a file to put.
	 *
	 * @sample
	 * putRequest.setFile('c:/temp/manual_01a.doc')
	 *
	 * @param filePath
	 */
	public boolean js_setFile(String filePath)
	{
		clearFiles();
		return js_addFile(null, null, filePath);
	}

	/**
	 * Set a file to put.
	 *
	 * @sample
	 * putRequest.setFile(jsFileInstance)
	 *
	 * @param file
	 */
	public boolean js_setFile(JSFile file)
	{
		clearFiles();
		return js_addFile(null, file);
	}

}
