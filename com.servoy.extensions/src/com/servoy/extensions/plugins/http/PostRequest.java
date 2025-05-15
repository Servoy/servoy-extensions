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

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * <p>The <code>PostRequest</code> class is designed for constructing and executing HTTP POST requests.
 * It allows adding files, parameters, and headers to requests, with automatic MIME type detection
 * based on file names or content. Users can override MIME type detection when needed. The class
 * supports single-file and multipart configurations, with an option to enforce multipart formatting
 * even for single files or parameters.</p>
 *
 * <p>Requests can be executed synchronously or asynchronously. Asynchronous methods allow
 * non-blocking operations and support callbacks for success and error handling. They also include
 * support for Windows authentication and additional arguments to differentiate callbacks in complex
 * workflows. Synchronous methods return a <code>Response</code> object for direct interaction with
 * server replies.</p>
 *
 * <p>The class includes features for setting authentication, such as preemptive credentials, which
 * are useful for optimizing uploads to servers requiring immediate authentication.</p>
 *
 * <p>A sample for creating a <code>PostRequest</code> instance can be found here:
 * <a href="https://docs.servoy.com/reference/servoyextensions/server-plugins/http/httpclient#createpostrequesturl">createPostRequest</a>.</p>
 *
 * @author jblok
 */
@ServoyDocumented
public class PostRequest extends BaseEntityEnclosingRequest
{
	public PostRequest()
	{
		super();
	}//only used by script engine

	public PostRequest(String url, CloseableHttpAsyncClient hc, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, new HttpPost(url), httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
	}

}
