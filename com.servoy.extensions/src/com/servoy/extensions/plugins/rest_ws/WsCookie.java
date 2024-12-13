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

package com.servoy.extensions.plugins.rest_ws;

import javax.servlet.http.Cookie;

import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.scripting.annotations.JSReadonlyProperty;

/**
 * <p>An HTTP cookie representation provides methods to manage and manipulate various attributes
 * of cookies in an application. The cookie includes properties such as <code>name</code> and
 * <code>value</code>, which hold its key and content respectively. Attributes like
 * <code>domain</code>, <code>path</code>, and <code>maxAge</code> define the cookie's scope and
 * lifetime, while flags such as <code>secure</code> and <code>httpOnly</code> enhance security by
 * restricting the transmission protocol and JavaScript accessibility.</p>
 *
 * <p>Additional attributes like <code>comment</code> provide metadata about the cookie's purpose,
 * and <code>version</code> ensures compatibility with specific cookie protocols. This
 * representation also supports methods for checking and modifying these attributes dynamically.</p>
 *
 * @author rgansevles
 *
 */
@ServoyDocumented(scriptingName = "WsCookie")
public class WsCookie implements IScriptable, IJavaScriptType
{
	private final Cookie cookie;

	/**
	 * @param cookie
	 */
	public WsCookie(Cookie cookie)
	{
		if (cookie == null) throw new NullPointerException("cookie");
		this.cookie = cookie;
	}

	Cookie getCookie()
	{
		return cookie;
	}

	/**
	 * Cookie comment.
	 * Specifies a comment that describes a cookie's purpose.
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * cookie.comment = 'yummy';
	 */
	@JSGetter
	public String getComment()
	{
		return cookie.getComment();
	}

	@JSSetter
	public void setComment(String purpose)
	{
		cookie.setComment(purpose);
	}

	/**
	 * Cookie domain.
	 * Specifies the domain within which this cookie should be presented.
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * cookie.domain = 'example.com';
	 */
	@JSGetter
	public String getDomain()
	{
		return cookie.getDomain();
	}

	@JSSetter
	public void setDomain(String domain)
	{
		cookie.setDomain(domain);
	}

	/**
	 * Cookie maxAge.
	 * Sets the maximum age in seconds for this Cookie.
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * cookie.maxAge = 3600;
	 */
	@JSGetter
	public int getMaxAge()
	{
		return cookie.getMaxAge();
	}

	@JSSetter
	public void setMaxAge(int expiry)
	{
		cookie.setMaxAge(expiry);
	}

	/**
	 * Cookie path.
	 * Specifies a path for the cookie to which the client should return the cookie.
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * cookie.path = '/subfolder';
	 */
	@JSGetter
	public String getPath()
	{
		return cookie.getPath();
	}

	@JSSetter
	public void setPath(String uri)
	{
		cookie.setPath(uri);
	}

	/**
	 * Cookie secure flag.
	 * Indicates to the browser whether the cookie should only be sent using a secure protocol, such as HTTPS or SSL.
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * cookie.secure = true;
	 */
	@JSGetter
	public boolean getSecure()
	{
		return cookie.getSecure();
	}

	@JSSetter
	public void setSecure(boolean flag)
	{
		cookie.setSecure(flag);
	}

	/**
	 * Cookie name.
	 *
	 * The cookie name allows only a sequence of non-special, non-white space characters, see
	 * the cookie spec https://tools.ietf.org/html/rfc2965
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * cookie.name = 'doublechocolate';
	 */
	@JSReadonlyProperty
	public String getName()
	{
		return cookie.getName();
	}

	/**
	 * Cookie value.
	 *
	 * The cookie value allows only a sequence of non-special, non-white space characters, see
	 * the cookie spec https://tools.ietf.org/html/rfc2965
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * cookie.value = 'mint';
	 */
	@JSGetter
	public String getValue()
	{
		return cookie.getValue();
	}

	@JSSetter
	public void setValue(String newValue)
	{
		cookie.setValue(newValue);
	}

	/**
	 * Cookie version.
	 * Sets the version of the cookie protocol that this Cookie complies with.
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * cookie.version = 1;
	 */
	@JSGetter
	public int getVersion()
	{
		return cookie.getVersion();
	}

	@JSSetter
	public void setVersion(int v)
	{
		cookie.setVersion(v);
	}

	/**
	 * Cookie httpOnly flag.
	 * Marks or unmarks this Cookie as HttpOnly
	 * @sample
	 * var cookie = plugins.rest_ws.createCookie('chocolate', 'chip');
	 * cookie.httpOnly = true;
	 */
	@JSGetter
	public boolean isHttpOnly()
	{
		return cookie.isHttpOnly();
	}

	@JSSetter
	public void setHttpOnly(boolean isHttpOnly)
	{
		cookie.setHttpOnly(isHttpOnly);
	}

	@Override
	public String toString()
	{
		return cookie.toString();
	}

	@Override
	public int hashCode()
	{
		return cookie.hashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj != null && obj.getClass() == getClass() && cookie.equals(((WsCookie)obj).cookie);
	}
}
