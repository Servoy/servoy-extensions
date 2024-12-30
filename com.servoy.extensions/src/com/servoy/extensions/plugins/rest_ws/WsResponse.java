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

import java.io.IOException;
import java.util.Locale;

import javax.servlet.http.HttpServletResponse;

import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>WsResponse</code> object represents a REST-WS response and is only valid while handling a
 * REST-WS request. It allows manipulation of response headers, content type, status, and other properties
 * essential for crafting HTTP responses.</p>
 *
 * <p>The <code>WsResponse</code> object provides several properties to configure the HTTP response. For
 * instance, <code>characterEncoding</code> sets the MIME charset, such as UTF-8. The <code>contentType</code>
 * allows specifying the content type of the response, including optional character encoding. The
 * <code>localeLanguageTag</code> retrieves the locale of the response, and the <code>status</code> property
 * sets the HTTP status code for the response.</p>
 *
 * <p>Methods like <code>addCookie</code>, <code>addHeader</code>, and <code>setHeader</code> are available
 * for adding or setting cookies and headers in the response. Additional methods, such as <code>sendError</code>,
 * allow setting error codes and messages, while <code>setDateHeader</code> and <code>addDateHeader</code>
 * handle date-related headers. Other methods like <code>getHeader</code> and <code>getHeaderNames</code>
 * facilitate inspecting headers in the response.</p>
 *
 * <p>For overall REST-WS operations, refer to the
 * <a href="../../../../guides/develop/programming-guide/creating-rest-apis.md">Creating REST API</a> section.</p>
 *
 * @author rgansevles
 *
 */
@ServoyDocumented(scriptingName = "WsResponse")
public class WsResponse implements IScriptable, IJavaScriptType
{
	private final RestWSClientPlugin plugin;

	public WsResponse(RestWSClientPlugin plugin)
	{
		this.plugin = plugin;
	}

	/**
	* Adds the specified cookie to the response.  This method can be called
	* multiple times to set more than one cookie.
	*
	* @param cookie the Cookie to return to the client
	*
	*/
	@JSFunction
	public void addCookie(WsCookie cookie)
	{
		getResponse().addCookie(cookie.getCookie());
	}

	/**
	* Sets the status code for this response.
	*
	* <p>This method is used to set the return status code when there is
	* no error (for example, for the SC_OK or SC_MOVED_TEMPORARILY status
	* codes).
	*
	* <p>If this method is used to set an error code, then the container's
	* error page mechanism will not be triggered. If there is an error and
	* the caller wishes to invoke an error page defined in the web
	* application, then {@link #sendError} must be used instead.
	*
	* <p>This method preserves any cookies and other response headers.
	*
	* <p>Valid status codes are those in the 2XX, 3XX, 4XX, and 5XX ranges.
	* Other status codes are treated as container specific.
	*
	* @param	sc	the status code
	*
	* @return The current HTTP status code of this response.
	*
	* @see #sendError
	*/
	@JSGetter
	public int getStatus()
	{
		return getResponse().getStatus();
	}

	@JSSetter
	public void setStatus(int sc)
	{
		getResponse().setStatus(sc);
	}

	@JSFunction
	public void sendError(int sc) throws IOException
	{
		getResponse().sendError(sc);
	}

	@JSFunction
	public void sendError(int sc, String msg) throws IOException
	{
		getResponse().sendError(sc, msg);
	}

	private HttpServletResponse getResponse()
	{
		return plugin.getResponse();
	}

	/**
	 * Sets the character encoding (MIME charset) of the response
	 * being sent to the client, for example, to UTF-8.
	 * If the character encoding has already been set by
	 * {@link #setContentType} or {@link #setLocale},
	 * this method overrides it.
	 * Calling {@link #setContentType} with the <code>String</code>
	 * of <code>text/html</code> and calling
	 * this method with the <code>String</code> of <code>UTF-8</code>
	 * is equivalent with calling
	 * <code>setContentType</code> with the <code>String</code> of
	 * <code>text/html; charset=UTF-8</code>.
	 * <p>This method can be called repeatedly to change the character
	 * encoding.
	 * This method has no effect if it is called after
	 * <code>getWriter</code> has been
	 * called or after the response has been committed.
	 * <p>Containers must communicate the character encoding used for
	 * the servlet response's writer to the client if the protocol
	 * provides a way for doing so. In the case of HTTP, the character
	 * encoding is communicated as part of the <code>Content-Type</code>
	 * header for text media types. Note that the character encoding
	 * cannot be communicated via HTTP headers if the servlet does not
	 * specify a content type; however, it is still used to encode text
	 * written via the servlet response's writer.
	 *
	 * @param charset a String specifying only the character set
	 * defined by IANA Character Sets
	 * (http://www.iana.org/assignments/character-sets)
	 *
	 * @return The character encoding (MIME charset) of the response.
	 *
	 * @see #setContentType
	 * @see #setLocale
	 *
	 */
	@JSGetter
	public String getCharacterEncoding()
	{
		return getResponse().getCharacterEncoding();
	}

	@JSSetter
	public void setCharacterEncoding(String charset)
	{
		getResponse().setCharacterEncoding(charset);
	}

	/**
	* Sets the content type of the response being sent to
	* the client, if the response has not been committed yet.
	* The given content type may include a character encoding
	* specification, for example, <code>text/html;charset=UTF-8</code>.
	* The response's character encoding is only set from the given
	* content type if this method is called before <code>getWriter</code>
	* is called.
	* <p>This method may be called repeatedly to change content type and
	* character encoding.
	* This method has no effect if called after the response
	* has been committed. It does not set the response's character
	* encoding if it is called after <code>getWriter</code>
	* has been called or after the response has been committed.
	* <p>Containers must communicate the content type and the character
	* encoding used for the servlet response's writer to the client if
	* the protocol provides a way for doing so. In the case of HTTP,
	* the <code>Content-Type</code> header is used.
	*
	* @param type a <code>String</code> specifying the MIME
	* type of the content
	*
	* @return The MIME type of the content of this response.
	*
	* @see #setLocale
	* @see #setCharacterEncoding
	* @see #getOutputStream
	* @see #getWriter
	*
	*/
	@JSGetter
	public String getContentType()
	{
		return getResponse().getContentType();
	}

	@JSSetter
	public void setContentType(String type)
	{
		getResponse().setContentType(type);
	}

	/**
	* Returns the locale specified for this response
	* using the {@link #setLocale} method. Calls made to
	* <code>setLocale</code> after the response is committed
	* have no effect. If no locale has been specified,
	* the container's default locale is returned.
	*
	 * <p>If the specified language tag contains any ill-formed subtags,
	 * the first such subtag and all following subtags are ignored.  Compare
	 * to {@link Locale.Builder#setLanguageTag} which throws an exception
	 * in this case.
	 *
	 * <p>The following <b>conversions</b> are performed:<ul>
	 *
	 * <li>The language code "und" is mapped to language "".
	 *
	 * <li>The language codes "he", "yi", and "id" are mapped to "iw",
	 * "ji", and "in" respectively. (This is the same canonicalization
	 * that's done in Locale's constructors.)
	 *
	 * <li>The portion of a private use subtag prefixed by "lvariant",
	 * if any, is removed and appended to the variant field in the
	 * result locale (without case normalization).  If it is then
	 * empty, the private use subtag is discarded:
	 *
	 * <pre>
	 *     Locale loc;
	 *     loc = Locale.forLanguageTag("en-US-x-lvariant-POSIX");
	 *     loc.getVariant(); // returns "POSIX"
	 *     loc.getExtension('x'); // returns null
	 *
	 *     loc = Locale.forLanguageTag("de-POSIX-x-URP-lvariant-Abc-Def");
	 *     loc.getVariant(); // returns "POSIX_Abc_Def"
	 *     loc.getExtension('x'); // returns "urp"
	 * </pre>
	 *
	 * <li>When the languageTag argument contains an extlang subtag,
	 * the first such subtag is used as the language, and the primary
	 * language subtag and other extlang subtags are ignored:
	 *
	 * <pre>
	 *     Locale.forLanguageTag("ar-aao").getLanguage(); // returns "aao"
	 *     Locale.forLanguageTag("en-abc-def-us").toString(); // returns "abc_US"
	 * </pre>
	 *
	 * <li>Case is normalized except for variant tags, which are left
	 * unchanged.  Language is normalized to lower case, script to
	 * title case, country to upper case, and extensions to lower
	 * case.
	 *
	 * <li>If, after processing, the locale would exactly match either
	 * ja_JP_JP or th_TH_TH with no extensions, the appropriate
	 * extensions are added as though the constructor had been called:
	 *
	 * <pre>
	 *    Locale.forLanguageTag("ja-JP-x-lvariant-JP").toLanguageTag();
	 *    // returns "ja-JP-u-ca-japanese-x-lvariant-JP"
	 *    Locale.forLanguageTag("th-TH-x-lvariant-TH").toLanguageTag();
	 *    // returns "th-TH-u-nu-thai-x-lvariant-TH"
	 * <pre></ul>
	 *
	 * <p>This implements the 'Language-Tag' production of BCP47, and
	 * so supports grandfathered (regular and irregular) as well as
	 * private use language tags.  Stand alone private use tags are
	 * represented as empty language and extension 'x-whatever',
	 * and grandfathered tags are converted to their canonical replacements
	 * where they exist.
	 *
	 * <p>Grandfathered tags with canonical replacements are as follows:
	 *
	 * <table>
	 * <tbody align="center">
	 * <tr><th>grandfathered tag</th><th>&nbsp;</th><th>modern replacement</th></tr>
	 * <tr><td>art-lojban</td><td>&nbsp;</td><td>jbo</td></tr>
	 * <tr><td>i-ami</td><td>&nbsp;</td><td>ami</td></tr>
	 * <tr><td>i-bnn</td><td>&nbsp;</td><td>bnn</td></tr>
	 * <tr><td>i-hak</td><td>&nbsp;</td><td>hak</td></tr>
	 * <tr><td>i-klingon</td><td>&nbsp;</td><td>tlh</td></tr>
	 * <tr><td>i-lux</td><td>&nbsp;</td><td>lb</td></tr>
	 * <tr><td>i-navajo</td><td>&nbsp;</td><td>nv</td></tr>
	 * <tr><td>i-pwn</td><td>&nbsp;</td><td>pwn</td></tr>
	 * <tr><td>i-tao</td><td>&nbsp;</td><td>tao</td></tr>
	 * <tr><td>i-tay</td><td>&nbsp;</td><td>tay</td></tr>
	 * <tr><td>i-tsu</td><td>&nbsp;</td><td>tsu</td></tr>
	 * <tr><td>no-bok</td><td>&nbsp;</td><td>nb</td></tr>
	 * <tr><td>no-nyn</td><td>&nbsp;</td><td>nn</td></tr>
	 * <tr><td>sgn-BE-FR</td><td>&nbsp;</td><td>sfb</td></tr>
	 * <tr><td>sgn-BE-NL</td><td>&nbsp;</td><td>vgt</td></tr>
	 * <tr><td>sgn-CH-DE</td><td>&nbsp;</td><td>sgg</td></tr>
	 * <tr><td>zh-guoyu</td><td>&nbsp;</td><td>cmn</td></tr>
	 * <tr><td>zh-hakka</td><td>&nbsp;</td><td>hak</td></tr>
	 * <tr><td>zh-min-nan</td><td>&nbsp;</td><td>nan</td></tr>
	 * <tr><td>zh-xiang</td><td>&nbsp;</td><td>hsn</td></tr>
	 * </tbody>
	 * </table>
	 *
	 * <p>Grandfathered tags with no modern replacement will be
	 * converted as follows:
	 *
	 * <table>
	 * <tbody align="center">
	 * <tr><th>grandfathered tag</th><th>&nbsp;</th><th>converts to</th></tr>
	 * <tr><td>cel-gaulish</td><td>&nbsp;</td><td>xtg-x-cel-gaulish</td></tr>
	 * <tr><td>en-GB-oed</td><td>&nbsp;</td><td>en-GB-x-oed</td></tr>
	 * <tr><td>i-default</td><td>&nbsp;</td><td>en-x-i-default</td></tr>
	 * <tr><td>i-enochian</td><td>&nbsp;</td><td>und-x-i-enochian</td></tr>
	 * <tr><td>i-mingo</td><td>&nbsp;</td><td>see-x-i-mingo</td></tr>
	 * <tr><td>zh-min</td><td>&nbsp;</td><td>nan-x-zh-min</td></tr>
	 * </tbody>
	 * </table>
	 *
	 * <p>For a list of all grandfathered tags, see the
	 * IANA Language Subtag Registry (search for "Type: grandfathered").
	 *
	 * <p><b>Note</b>: there is no guarantee that <code>toLanguageTag</code>
	 * and <code>forLanguageTag</code> will round-trip.
	 *
	 * @return The language tag of the locale specified for this response.
	*/
	@JSGetter
	public String getLocaleLanguageTag()
	{
		return getResponse().getLocale().toLanguageTag();
	}

	@JSSetter
	public void setLocaleLanguageTag(String languageTag)
	{
		Locale locale = Locale.forLanguageTag(languageTag);
		getResponse().setLocale(locale);
	}

	/**
	* Returns a boolean indicating whether the named response header
	* has already been set.
	*
	* @param	name	the header name
	* @return		<code>true</code> if the named response header
	*			has already been set;
	* 			<code>false</code> otherwise
	*
	* @return True if the specified header is already set in the response; false otherwise.
	*/
	@JSFunction
	public boolean containsHeader(String name)
	{
		return getResponse().containsHeader(name);
	}

	/**
	*
	* Sets a response header with the given name and
	* date-value.  The date is specified in terms of
	* milliseconds since the epoch.  If the header had already
	* been set, the new value overwrites the previous one.  The
	* <code>containsHeader</code> method can be used to test for the
	* presence of a header before setting its value.
	*
	* @param	name	the name of the header to set
	* @param	date	the assigned date value
	*
	* @see #containsHeader
	* @see #addDateHeader
	*/
	@JSFunction
	public void setDateHeader(String name, long date)
	{
		getResponse().setDateHeader(name, date);
	}

	/**
	*
	* Adds a response header with the given name and
	* date-value.  The date is specified in terms of
	* milliseconds since the epoch.  This method allows response headers
	* to have multiple values.
	*
	* @param	name	the name of the header to set
	* @param	date	the additional date value
	*
	* @see #setDateHeader
	*/
	@JSFunction
	public void addDateHeader(String name, long date)
	{
		getResponse().addDateHeader(name, date);
	}

	/**
	*
	* Sets a response header with the given name and value.
	* If the header had already been set, the new value overwrites the
	* previous one.  The <code>containsHeader</code> method can be
	* used to test for the presence of a header before setting its
	* value.
	*
	* @param	name	the name of the header
	* @param	value	the header value  If it contains octet string,
	*		it should be encoded according to RFC 2047
	*		(http://www.ietf.org/rfc/rfc2047.txt)
	*
	* @see #containsHeader
	* @see #addHeader
	*/
	@JSFunction
	public void setHeader(String name, String value)
	{
		getResponse().setHeader(name, value);
	}

	/**
	 * Adds a response header with the given name and value.
	 * This method allows response headers to have multiple values.
	 *
	 * @param	name	the name of the header
	 * @param	value	the additional header value   If it contains
	 *		octet string, it should be encoded
	 *		according to RFC 2047
	 *		(http://www.ietf.org/rfc/rfc2047.txt)
	 *
	 * @see #setHeader
	 */
	@JSFunction
	public void addHeader(String name, String value)
	{
		getResponse().addHeader(name, value);
	}

	/**
	* Sets a response header with the given name and
	* integer value.  If the header had already been set, the new value
	* overwrites the previous one.  The <code>containsHeader</code>
	* method can be used to test for the presence of a header before
	* setting its value.
	*
	* @param	name	the name of the header
	* @param	value	the assigned integer value
	*
	* @see #containsHeader
	* @see #addIntHeader
	*/
	@JSFunction
	public void setIntHeader(String name, int value)
	{
		getResponse().setIntHeader(name, value);
	}

	/**
	* Adds a response header with the given name and
	* integer value.  This method allows response headers to have multiple
	* values.
	*
	* @param	name	the name of the header
	* @param	value	the assigned integer value
	*
	* @see #setIntHeader
	*/
	@JSFunction
	public void addIntHeader(String name, int value)
	{
		getResponse().addIntHeader(name, value);
	}

	/**
	* Gets the value of the response header with the given name.
	*
	* <p>If a response header with the given name exists and contains
	* multiple values, the value that was added first will be returned.
	*
	* <p>This method considers only response headers set or added via
	* {@link #setHeader}, {@link #addHeader}, {@link #setDateHeader},
	* {@link #addDateHeader}, {@link #setIntHeader}, or
	* {@link #addIntHeader}, respectively.
	*
	* @param name the name of the response header whose value to return
	*
	* @return the value of the response header with the given name,
	* or <tt>null</tt> if no header with the given name has been set
	* on this response
	*/
	@JSFunction
	public String getHeader(String name)
	{
		return getResponse().getHeader(name);
	}

	/**
	* Gets the values of the response header with the given name.
	*
	* <p>This method considers only response headers set or added via
	* {@link #setHeader}, {@link #addHeader}, {@link #setDateHeader},
	* {@link #addDateHeader}, {@link #setIntHeader}, or
	* {@link #addIntHeader}, respectively.
	*
	* @param name the name of the response header whose values to return
	*
	* @return a (possibly empty) array of the values
	* of the response header with the given name
	*/
	@JSFunction
	public String[] getHeaders(String name)
	{
		return getResponse().getHeaders(name).toArray(new String[0]);
	}

	/**
	* Gets the names of the headers of this response.
	*
	* <p>This method considers only response headers set or added via
	* {@link #setHeader}, {@link #addHeader}, {@link #setDateHeader},
	* {@link #addDateHeader}, {@link #setIntHeader}, or
	* {@link #addIntHeader}, respectively.
	*
	* @return a (possibly empty) array of the names
	* of the headers of this response
	*/
	@JSFunction
	public String[] getHeaderNames()
	{
		return getResponse().getHeaderNames().toArray(new String[0]);
	}
}
