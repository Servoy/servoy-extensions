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

	public HttpClientConfig()
	{
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
}
