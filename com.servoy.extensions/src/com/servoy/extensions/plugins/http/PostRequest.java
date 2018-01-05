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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.util.EntityUtils;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.util.Debug;

/**
 * @author jblok
 */
@ServoyDocumented
public class PostRequest extends BaseEntityEnclosingRequest
{
	public PostRequest()
	{
		super();
	}//only used by script engine

	public PostRequest(String url, DefaultHttpClient hc, HttpPlugin httpPlugin)
	{
		super(url, hc, new HttpPost(url), httpPlugin);
	}

	/**
	 * @deprecated Replaced by {@link #setCharset(String)}
	 */
	@Deprecated
	public void js_setEncoding(String encoding)
	{
		js_setCharset(encoding);
	}


	/**
	 * @deprecated Replaced by {@link #executeRequest(String,String)}
	 *
	 * @sample
	 * //null
	 * var httpCode = poster.doPost()
	 *
	 * @param username optional
	 * @param password optional
	 */
	@Deprecated
	public int js_doPost(Object[] args)
	{

		String username = null;
		String password = null;
		if (args.length == 2)
		{
			username = "" + args[0]; //$NON-NLS-1$
			password = "" + args[1]; //$NON-NLS-1$
		}
		try
		{
			Response res = js_executeRequest(username, password);
			return res.js_getStatusCode();
		}
		catch (Exception ex)
		{
			Debug.error(ex);
			return 0;
		}
	}


	/**
	 * Get the result page data after a post.
	 *
	 * @deprecated Replaced by {@link #executeRequest(String,String)}
	 *
	 * @sample
	 * var pageData = poster.getPageData()
	 */
	@Deprecated
	public String js_getPageData()
	{
		try
		{
			if (context != null)
			{
				HttpResponse response = (HttpResponse)context.getAttribute(ExecutionContext.HTTP_RESPONSE);
				context = null;
				return EntityUtils.toString(response.getEntity());
			}
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		return ""; //$NON-NLS-1$
	}

}
