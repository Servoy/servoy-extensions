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

import static java.util.Arrays.stream;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.scripting.JSMap;

/**
 * The representation of a rest-ws response, only valid while running in a REST-WS request.
 *
 * @author rgansevles
 *
 */
@ServoyDocumented(scriptingName = "WsRequest")
public class WsRequest implements IScriptable, IJavaScriptType
{
	private static final WsCookie[] NO_COOKIES = new WsCookie[0];
	private static final WsContents[] NO_CONTENTS = new WsContents[0];

	private final RestWSClientPlugin plugin;

	public WsRequest(RestWSClientPlugin plugin)
	{
		this.plugin = plugin;
	}

	/**
	 * Returns the value of the specified request header as a String.
	 * If the request did not include a header of the specified name, this method returns null.
	 * If there are multiple headers with the same name, this method returns the first head in the request.
	 * The header name is case insensitive. You can use this method with any request header.
	 * @sample
	 * var request = plugins.rest_ws.getRequest();
	 * var header = request.getHeader('');
	 */
	@JSFunction
	public String getHeader(String name)
	{
		return getRequest().getHeader(name);
	}

	private HttpServletRequest getRequest()
	{
		return plugin.getRequest();
	}

	/**
	* Returns the name of the character encoding used in the body of this
	* request. This method returns <code>null</code> if the request
	* does not specify a character encoding
	*
	* @return a <code>String</code> containing the name of the character
	* encoding, or <code>null</code> if the request does not specify a
	* character encoding
	*/
	@JSGetter
	public String getCharacterEncoding()
	{
		return getRequest().getCharacterEncoding();
	}

	@JSSetter
	public void setCharacterEncoding(String env) throws UnsupportedEncodingException
	{
		getRequest().setCharacterEncoding(env);
	}

	/**
	* Returns the length, in bytes, of the request body and made available by
	* the input stream, or -1 if the length is not known. For HTTP servlets,
	* same as the value of the CGI variable CONTENT_LENGTH.
	*
	* @return a long containing the length of the request body or -1L if
	* the length is not known
	*/
	@JSFunction
	public long getContentLength()
	{
		return getRequest().getContentLengthLong();
	}

	/**
	 * Returns the MIME type of the body of the request, or
	 * <code>null</code> if the type is not known. For HTTP servlets,
	 * same as the value of the CGI variable CONTENT_TYPE.
	 *
	 * @return a <code>String</code> containing the name of the MIME type
	 * of the request, or null if the type is not known
	 */
	@JSFunction
	public String getContentType()
	{
		return getRequest().getContentType();
	}

	/**
	* Returns the value of a request parameter as a <code>String</code>,
	* or <code>null</code> if the parameter does not exist. Request parameters
	* are extra information sent with the request.  For HTTP servlets,
	* parameters are contained in the query string or posted form data.
	*
	* <p>You should only use this method when you are sure the
	* parameter has only one value. If the parameter might have
	* more than one value, use {@link #getParameterValues}.
	*
	* <p>If you use this method with a multivalued
	* parameter, the value returned is equal to the first value
	* in the array returned by <code>getParameterValues</code>.
	*
	* <p>If the parameter data was sent in the request body, such as occurs
	* with an HTTP POST request, then reading the body directly via {@link
	* #getInputStream} or {@link #getReader} can interfere
	* with the execution of this method.
	*
	* @param name a <code>String</code> specifying the name of the parameter
	*
	* @return a <code>String</code> representing the single value of
	* the parameter
	*
	* @see #getParameterValues
	*/
	@JSFunction
	public String getParameter(String name)
	{
		return getRequest().getParameter(name);
	}

	/**
	* Returns an array of <code>String</code>
	* objects containing the names of the parameters contained
	* in this request. If the request has
	* no parameters, the method returns an empty array.
	*
	* @return an array of <code>String</code>
	* objects, each <code>String</code> containing the name of
	* a request parameter; or an empty array
	* if the request has no parameters
	*/
	@JSFunction
	public String[] getParameterNames()
	{
		return Collections.list(getRequest().getParameterNames()).toArray(new String[0]);
	}

	/**
	* Returns an array of <code>String</code> objects containing
	* all of the values the given request parameter has, or
	* <code>null</code> if the parameter does not exist.
	*
	* <p>If the parameter has a single value, the array has a length
	* of 1.
	*
	* @param name a <code>String</code> containing the name of
	* the parameter whose value is requested
	*
	* @return an array of <code>String</code> objects
	* containing the parameter's values
	*
	* @see #getParameter
	*/
	@JSFunction
	public String[] getParameterValues(String name)
	{
		return getRequest().getParameterValues(name);
	}

	/**
	 * Returns an object of the parameters of this request.
	 *
	 * <p>Request parameters are extra information sent with the request.
	 * For HTTP servlets, parameters are contained in the query string or
	 * posted form data.
	 *
	 * @return an object containing parameter names as
	 * keys and parameter values as map values. The keys in the parameter
	 * map are of type String. The values in the parameter map are of type
	 * String array.
	 */
	@JSFunction
	public Map<String, String[]> getParameterMap()
	{
		JSMap<String, String[]> map = new JSMap<>();
		map.putAll(getRequest().getParameterMap());
		return map;
	}

	/**
	* Returns the name and version of the protocol the request uses
	* in the form <i>protocol/majorVersion.minorVersion</i>, for
	* example, HTTP/1.1. For HTTP servlets, the value
	* returned is the same as the value of the CGI variable
	* <code>SERVER_PROTOCOL</code>.
	*
	* @return a <code>String</code> containing the protocol
	* name and version number
	*/
	@JSFunction
	public String getProtocol()
	{
		return getRequest().getProtocol();
	}

	/**
	* Returns the name of the scheme used to make this request,
	* for example,
	* <code>http</code>, <code>https</code>, or <code>ftp</code>.
	* Different schemes have different rules for constructing URLs,
	* as noted in RFC 1738.
	*
	* @return a <code>String</code> containing the name
	* of the scheme used to make this request
	*/
	@JSFunction
	public String getScheme()
	{
		return getRequest().getScheme();
	}

	/**
	* Returns the host name of the server to which the request was sent.
	* It is the value of the part before ":" in the <code>Host</code>
	* header value, if any, or the resolved server name, or the server IP
	* address.
	*
	* @return a <code>String</code> containing the name of the server
	*/
	@JSFunction
	public String getServerName()
	{
		return getRequest().getServerName();
	}

	/**
	* Returns the port number to which the request was sent.
	* It is the value of the part after ":" in the <code>Host</code>
	* header value, if any, or the server port where the client connection
	* was accepted on.
	*
	* @return an integer specifying the port number
	*/
	@JSFunction
	public int getServerPort()
	{
		return getRequest().getServerPort();
	}

	/**
	* Returns the Internet Protocol (IP) address of the client
	* or last proxy that sent the request.
	* For HTTP servlets, same as the value of the
	* CGI variable <code>REMOTE_ADDR</code>.
	*
	* @return a <code>String</code> containing the
	* IP address of the client that sent the request
	*/
	@JSFunction
	public String getRemoteAddr()
	{
		return getRequest().getRemoteAddr();
	}

	/**
	* Returns the fully qualified name of the client
	* or the last proxy that sent the request.
	* If the engine cannot or chooses not to resolve the hostname
	* (to improve performance), this method returns the dotted-string form of
	* the IP address. For HTTP servlets, same as the value of the CGI variable
	* <code>REMOTE_HOST</code>.
	*
	* @return a <code>String</code> containing the fully
	* qualified name of the client
	*/
	@JSFunction
	public String getRemoteHost()
	{
		return getRequest().getRemoteHost();
	}

	/**
	* Returns the preferred <code>Locale</code> that the client will
	* accept content in, based on the Accept-Language header.
	* If the client request doesn't provide an Accept-Language header,
	* this method returns the default locale for the server.
	*
	* Returns a well-formed IETF BCP 47 language tag representing
	* this locale.
	*
	* @return the preferred <code>Locale</code> for the client
	*/
	@JSFunction
	public String getLocaleLanguageTag()
	{
		return getRequest().getLocale().toLanguageTag();
	}

	/**
	 * Returns an array of <code>Locale</code> objects
	 * indicating, in decreasing order starting with the preferred locale, the
	 * locales that are acceptable to the client based on the Accept-Language
	 * header.
	 * If the client request doesn't provide an Accept-Language header,
	 * this method returns an array containing one
	 * <code>Locale</code>, the default locale for the server.
	 *
	 * Returns well-formed IETF BCP 47 language tags representing
	 * the locales.
	
	 * @return an array of preferred
	 * <code>Locale</code> objects for the client
	 */
	@JSFunction
	public String[] getLocalesLanguageTags()
	{
		List<String> localesLanguageTags = new ArrayList<>();
		Enumeration<Locale> locales = getRequest().getLocales();
		while (locales.hasMoreElements())
		{
			localesLanguageTags.add(locales.nextElement().toLanguageTag());
		}
		return localesLanguageTags.toArray(new String[0]);
	}

	/**
	*
	* Returns a boolean indicating whether this request was made using a
	* secure channel, such as HTTPS.
	*
	* @return a boolean indicating if the request was made using a
	* secure channel
	*/
	@JSFunction
	public boolean isSecure()
	{
		return getRequest().isSecure();
	}

	/**
	* Gets the <i>real</i> path corresponding to the given
	* <i>virtual</i> path.
	*
	* @param path the <i>virtual</i> path to be translated to a
	* <i>real</i> path
	*
	* @return the <i>real</i> path, or <tt>null</tt> if the
	* translation cannot be performed
	*/
	@SuppressWarnings("deprecation")
	@JSFunction
	public String getRealPath(String path)
	{
		return getRequest().getRealPath(path);
	}

	/**
	* Returns the Internet Protocol (IP) source port of the client
	* or last proxy that sent the request.
	*
	* @return an integer specifying the port number
	*/
	@JSFunction
	public int getRemotePort()
	{
		return getRequest().getRemotePort();
	}

	/**
	* Returns the host name of the Internet Protocol (IP) interface on
	* which the request was received.
	*
	* @return a <code>String</code> containing the host
	*         name of the IP on which the request was received.
	*/
	@JSFunction
	public String getLocalName()
	{
		return getRequest().getLocalName();
	}

	/**
	* Returns the Internet Protocol (IP) address of the interface on
	* which the request  was received.
	*
	* @return a <code>String</code> containing the
	* IP address on which the request was received.
	*/
	@JSFunction
	public String getLocalAddr()
	{
		return getRequest().getLocalAddr();
	}

	/**
	 * Returns the Internet Protocol (IP) port number of the interface
	 * on which the request was received.
	 *
	 * @return an integer specifying the port number
	 */
	@JSFunction
	public int getLocalPort()
	{
		return getRequest().getLocalPort();
	}

	/**
	* Returns an array containing all of the <code>Cookie</code>
	* objects the client sent with this request.
	* This method returns an empty array if no cookies were sent.
	*
	* @return		an array of all the <code>Cookies</code>
	*			included with this request
	*/
	@JSFunction
	public WsCookie[] getCookies()
	{
		Cookie[] cookies = getRequest().getCookies();
		if (cookies == null || cookies.length == 0)
		{
			return NO_COOKIES;
		}

		return stream(cookies).map(WsCookie::new).toArray(WsCookie[]::new);

	}

	/** Get raw the contents of the request.
	 *
	 * In case of multipart request, all uploaded items are listed separately.
	 * In case of a request with a single body, the contents array consists of one item, the body.
	 *
	 * This method returns an empty array if the request has no contents.
	 * @sample
	 * var request = plugins.rest_ws.getRequest();
	 * var contents = request.getContents();
	 * @return		an array of WsContents objects.
	 */
	@JSFunction
	public WsContents[] getContents()
	{
		List<FileItem> contents = plugin.getContents();
		if (contents == null || contents.isEmpty())
		{
			return NO_CONTENTS;
		}

		return contents.stream().map(WsContents::new).toArray(WsContents[]::new);
	}

	/**
	* Returns the value of the specified request header
	* as a <code>long</code> value that represents a
	* <code>Date</code> object. Use this method with
	* headers that contain dates, such as
	* <code>If-Modified-Since</code>.
	*
	* <p>The date is returned as
	* the number of milliseconds since January 1, 1970 GMT.
	* The header name is case insensitive.
	*
	* <p>If the request did not have a header of the
	* specified name, this method returns -1. If the header
	* can't be converted to a date, the method throws
	* an <code>IllegalArgumentException</code>.
	*
	* @param name		a <code>String</code> specifying the
	*				name of the header
	*
	* @return			a <code>long</code> value
	*				representing the date specified
	*				in the header expressed as
	*				the number of milliseconds
	*				since January 1, 1970 GMT,
	*				or -1 if the named header
	*				was not included with the
	*				request
	*
	* @exception	IllegalArgumentException	If the header value
	*							can't be converted
	*							to a date
	*/
	@JSFunction
	public long getDateHeader(String name)
	{
		return getRequest().getDateHeader(name);
	}

	/**
	* Returns all the values of the specified request header
	* as an array of <code>String</code> objects.
	*
	* <p>Some headers, such as <code>Accept-Language</code> can be sent
	* by clients as several headers each with a different value rather than
	* sending the header as a comma separated list.
	*
	* <p>If the request did not include any headers
	* of the specified name, this method returns an empty
	* array.
	* The header name is case insensitive. You can use
	* this method with any request header.
	*
	* @param name		a <code>String</code> specifying the
	*				header name
	*
	* @return			an array containing
	*                  	the values of the requested header. If
	*                  	the request does not have any headers of
	*                  	that name return an empty
	*                  	enumeration. If
	*                  	the container does not allow access to
	*                  	header information, return null
	*/
	@JSFunction
	public String[] getHeaders(String name)
	{
		Enumeration<String> headers = getRequest().getHeaders(name);
		return headers == null ? null : Collections.list(headers).toArray(new String[0]);
	}

	/**
	* Returns an enumeration of all the header names
	* this request contains. If the request has no
	* headers, this method returns an empty enumeration.
	*
	* <p>Some servlet containers do not allow
	* servlets to access headers using this method, in
	* which case this method returns <code>null</code>
	*
	* @return			an enumeration of all the
	*				header names sent with this
	*				request; if the request has
	*				no headers, an empty enumeration;
	*				if the servlet container does not
	*				allow servlets to use this method,
	*				<code>null</code>
	*/
	@JSFunction
	public String[] getHeaderNames()
	{
		Enumeration<String> headerNames = getRequest().getHeaderNames();
		return headerNames == null ? null : Collections.list(headerNames).toArray(new String[0]);
	}

	/**
	* Returns the value of the specified request header
	* as an <code>int</code>. If the request does not have a header
	* of the specified name, this method returns -1. If the
	* header cannot be converted to an integer, this method
	* throws a <code>NumberFormatException</code>.
	*
	* <p>The header name is case insensitive.
	*
	* @param name		a <code>String</code> specifying the name
	*				of a request header
	*
	* @return			an integer expressing the value
	* 				of the request header or -1
	*				if the request doesn't have a
	*				header of this name
	*
	* @exception	NumberFormatException		If the header value
	*							can't be converted
	*							to an <code>int</code>
	*/
	@JSFunction
	public int getIntHeader(String name)
	{
		return getRequest().getIntHeader(name);
	}

	/**
	* Returns the name of the HTTP method with which this
	* request was made, for example, GET, POST, or PUT.
	* Same as the value of the CGI variable REQUEST_METHOD.
	*
	* @return			a <code>String</code>
	*				specifying the name
	*				of the method with which
	*				this request was made
	*/
	@JSFunction
	public String getMethod()
	{
		return getRequest().getMethod();
	}

	/**
	 * Returns any extra path information associated with
	 * the URL the client sent when it made this request.
	 * The extra path information follows the servlet path
	 * but precedes the query string and will start with
	 * a "/" character.
	 *
	 * <p>This method returns <code>null</code> if there
	 * was no extra path information.
	 *
	 * <p>Same as the value of the CGI variable PATH_INFO.
	 *
	 * @return		a <code>String</code>, decoded by the
	 *			web container, specifying
	 *			extra path information that comes
	 *			after the servlet path but before
	 *			the query string in the request URL;
	 *			or <code>null</code> if the URL does not have
	 *			any extra path information
	 */
	@JSFunction
	public String getPathInfo()
	{
		return getRequest().getPathInfo();
	}

	/**
	* Returns any extra path information after the servlet name
	* but before the query string, and translates it to a real
	* path. Same as the value of the CGI variable PATH_TRANSLATED.
	*
	* <p>If the URL does not have any extra path information,
	* this method returns <code>null</code> or the servlet container
	* cannot translate the virtual path to a real path for any reason
	* (such as when the web application is executed from an archive).
	*
	* The web container does not decode this string.
	*
	* @return		a <code>String</code> specifying the
	*			real path, or <code>null</code> if
	*			the URL does not have any extra path
	*			information
	*/
	@JSFunction
	public String getPathTranslated()
	{
		return getRequest().getPathTranslated();
	}

	/**
	* Returns the portion of the request URI that indicates the context
	* of the request. The context path always comes first in a request
	* URI. The path starts with a "/" character but does not end with a "/"
	* character. For servlets in the default (root) context, this method
	* returns "". The container does not decode this string.
	*
	* <p>It is possible that a servlet container may match a context by
	* more than one context path. In such cases this method will return the
	* actual context path used by the request and it may differ from the
	* path returned by the
	* {@link javax.servlet.ServletContext#getContextPath()} method.
	* The context path returned by
	* {@link javax.servlet.ServletContext#getContextPath()}
	* should be considered as the prime or preferred context path of the
	* application.
	*
	* @return		a <code>String</code> specifying the
	*			portion of the request URI that indicates the context
	*			of the request
	*
	* @see javax.servlet.ServletContext#getContextPath()
	*/
	@JSFunction
	public String getContextPath()
	{
		return getRequest().getContextPath();
	}

	/**
	* Returns the query string that is contained in the request
	* URL after the path. This method returns <code>null</code>
	* if the URL does not have a query string. Same as the value
	* of the CGI variable QUERY_STRING.
	*
	* @return		a <code>String</code> containing the query
	*			string or <code>null</code> if the URL
	*			contains no query string. The value is not
	*			decoded by the container.
	*/
	@JSFunction
	public String getQueryString()
	{
		return getRequest().getQueryString();
	}

	/**
	* Returns the part of this request's URL from the protocol
	* name up to the query string in the first line of the HTTP request.
	* The web container does not decode this String.
	* For example:
	*
	* <table summary="Examples of Returned Values">
	* <tr align=left><th>First line of HTTP request      </th>
	* <th>     Returned Value</th>
	* <tr><td>POST /some/path.html HTTP/1.1<td><td>/some/path.html
	* <tr><td>GET http://foo.bar/a.html HTTP/1.0
	* <td><td>/a.html
	* <tr><td>HEAD /xyz?a=b HTTP/1.1<td><td>/xyz
	* </table>
	*
	* @return		a <code>String</code> containing
	*			the part of the URL from the
	*			protocol name up to the query string
	*/
	@JSFunction
	public String getRequestURI()
	{
		return getRequest().getRequestURI();
	}

	/**
	* Reconstructs the URL the client used to make the request.
	* The returned URL contains a protocol, server name, port
	* number, and server path, but it does not include query
	* string parameters.
	*
	* <p>If this request has been forwarded using
	* {@link javax.servlet.RequestDispatcher#forward}, the server path in the
	* reconstructed URL must reflect the path used to obtain the
	* RequestDispatcher, and not the server path specified by the client.
	*
	* <p>This method is useful for creating redirect messages
	* and for reporting errors.
	*
	* @return		a <code>StringBuffer</code> object containing
	*			the reconstructed URL
	*/
	@JSFunction
	public String getRequestURL()
	{
		return getRequest().getRequestURL().toString();
	}

	/**
	* Returns the part of this request's URL that calls
	* the servlet. This path starts with a "/" character
	* and includes either the servlet name or a path to
	* the servlet, but does not include any extra path
	* information or a query string. Same as the value of
	* the CGI variable SCRIPT_NAME.
	*
	* <p>This method will return an empty string ("") if the
	* servlet used to process this request was matched using
	* the "/*" pattern.
	*
	* @return		a <code>String</code> containing
	*			the name or path of the servlet being
	*			called, as specified in the request URL,
	*			decoded, or an empty string if the servlet
	*			used to process the request is matched
	*			using the "/*" pattern.
	*/
	@JSFunction
	public String getServletPath()
	{
		return getRequest().getServletPath();
	}

}
