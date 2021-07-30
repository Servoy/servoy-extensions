/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2021 Servoy BV

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

package com.servoy.extensions.plugins.jwt.client;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IConstantsObject;

/**
 * @author emera
 */
@ServoyDocumented(scriptingName = "JWTAlgorithms")
@ServoyClientSupport(ng = true, wc = true, sc = true)
@SuppressWarnings("nls")
public class JWTAlgorithms implements IConstantsObject
{
	public static final String HS256 = "HS256";
	public static final String HS384 = "HS384";
	public static final String HS512 = "HS512";
	public static final String RS256 = "RS256";
	public static final String RS384 = "RS384";
	public static final String RS512 = "RS512";
	public static final String ES256 = "ES256";
	public static final String ES384 = "ES384";
	public static final String ES512 = "ES512";
}
