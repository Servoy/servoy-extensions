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
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IPrefixedConstantsObject;

/**
 * @author emera
 */
@ServoyDocumented(publicName = "RequestType", scriptingName = "RequestType")
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
