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

package com.servoy.extensions.plugins.oauth;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IConstantsObject;

/**
 * <p>The <code>ClientAuthentication</code> class defines constants for OAuth
 * authentication schemes, enabling standardized handling of client authentication
 * methods.</p>
 *
 * <h2>Constants</h2>
 *
 * <ul>
 *   <li><b>Http_Basic_AuthenticationScheme</b>: Represents the HTTP Basic
 *   Authentication scheme.</li>
 *   <li><b>Request_Body_AuthenticationScheme</b>: Represents the Request Body
 *   Authentication scheme.</li>
 * </ul>
 *
 * <p>For more information, refer to the
 * <a href="../../../../guides/develop/security/oauth.md">ClientAuthentication OAuth guide</a>.</p>
 *
 * @author emera
 */
@ServoyDocumented(scriptingName = "ClientAuthentication")
public class ClientAuthentication implements IConstantsObject
{
	public static final String Http_Basic_AuthenticationScheme = "com.github.scribejava.core.oauth2.clientauthentication.HttpBasicAuthenticationScheme";
	public static final String Request_Body_AuthenticationScheme = "com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme";
}
