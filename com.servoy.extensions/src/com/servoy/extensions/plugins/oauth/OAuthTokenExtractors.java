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

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IConstantsObject;

/**
 * <p>The <code>OAuthTokenExtractors</code> class provides constants representing supported token
 * extractors for OAuth implementations. These include <code>OAuth2</code>, <code>OAuthJson</code>,
 * and <code>OpenIdJson</code> extractors, which handle parsing access tokens in different formats
 * such as standard OAuth2 and JSON.</p>
 *
 * @author emera
 */
@ServoyDocumented(scriptingName = "OAuthTokenExtractors")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthTokenExtractors implements IConstantsObject
{
	public static final String OAuth2 = "com.github.scribejava.core.extractors.OAuth2AccessTokenExtractor";
	public static final String OAuthJson = "com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor";
	public static final String OpenIdJson = "com.github.scribejava.apis.openid.OpenIdJsonTokenExtractor";
}
