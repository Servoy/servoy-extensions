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
 * JWT claims define constants representing specific claims within a JSON Web Token (JWT), commonly used to identify aspects such as audience, issuer, and subject of the token.
 *
 * @author emera
 */
@ServoyDocumented(scriptingName = "JWTClaims")
@ServoyClientSupport(ng = true, wc = true, sc = true)
public class JWTClaims implements IConstantsObject
{
	public static final String SUB = "sub";
	public static final String ISS = "iss";
	public static final String AUD = "aud";
}
