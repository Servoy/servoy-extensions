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

import org.apache.http.client.methods.HttpOptions;
import org.apache.http.impl.client.DefaultHttpClient;

import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * @author pbakker
 *
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

	public OptionsRequest(String url, DefaultHttpClient hc, HttpPlugin httpPlugin)
	{
		super(url, hc, new HttpOptions(url), httpPlugin);
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
