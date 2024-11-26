/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2018 Servoy BV

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

package com.servoy.extensions.plugins.oauth;

import com.github.scribejava.core.model.Verb;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IPrefixedConstantsObject;

/**
 * <p><code>OAuth request types</code> are implemented as enumerated constants,
 * each representing a standard HTTP method. These constants provide a clear
 * and structured approach to defining request types, ensuring consistency
 * and reducing ambiguity in OAuth-related API interactions.</p>
 *
 * <p>The request types include commonly used HTTP methods such as
 * <code>DELETE</code>, <code>GET</code>, <code>POST</code>, and <code>PUT</code>,
 * which handle resource deletion, retrieval, creation, and updates, respectively.
 * Additional methods like <code>HEAD</code> and <code>OPTIONS</code> are used for
 * metadata retrieval and discovering supported operations, while <code>PATCH</code>
 * allows partial updates to resources. <code>TRACE</code> serves diagnostic purposes
 * by echoing the received request.</p>
 *
 * @author emera
 */
@ServoyDocumented(publicName = "RequestType", scriptingName = "RequestType")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthRequestType implements IPrefixedConstantsObject
{
	public static final Verb GET = Verb.GET;
	public static final Verb PUT = Verb.PUT;
	public static final Verb POST = Verb.POST;
	public static final Verb DELETE = Verb.DELETE;
	public static final Verb HEAD = Verb.HEAD;
	public static final Verb OPTIONS = Verb.OPTIONS;
	public static final Verb PATCH = Verb.PATCH;
	public static final Verb TRACE = Verb.TRACE;

	@Override
	public String getPrefix()
	{
		return "RequestType";
	}

}
