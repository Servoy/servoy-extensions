/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2016 Servoy BV

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

import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * <p>The <code>PatchRequest</code> class facilitates handling HTTP PATCH requests for
 * updating resources on a server. It supports adding files, parameters, and headers to
 * requests while providing methods to configure the request body and authentication
 * options.</p>
 *
 * <p>Developers can include files in the request with <code>addFile()</code> methods,
 * which support specifying file details and MIME types. Parameters can be added using
 * <code>addParameter()</code>, with support for custom MIME types when required. For
 * configuring request bodies directly, the <code>setBodyContent()</code> method is
 * available with optional MIME type specifications.</p>
 *
 * <p>Requests can be executed synchronously using <code>executeRequest()</code> or
 * asynchronously with <code>executeAsyncRequest()</code>, offering support for callbacks
 * to handle responses or errors. Authentication options include preemptive authentication,
 * Windows authentication, and user credentials, ensuring compatibility with various server
 * requirements. The <code>forceMultipart()</code> method allows requests to be formatted
 * as <code>multipart/form-data</code> when needed, even for single-file uploads.</p>
 *
 * <p>A sample creating a <code>PatchRequest</code> can be found here:
 * <a href="./httpclient.md#createpatchrequesturl">createPatchRequest</a>.</p>
 *
 * @author lvostinar
 */
@ServoyDocumented
public class PatchRequest extends BaseEntityEnclosingRequest
{
	public PatchRequest()
	{
		super();
	}//only used by script engine

	public PatchRequest(String url, CloseableHttpAsyncClient hc, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, new HttpPatch(url), httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
	}

}
