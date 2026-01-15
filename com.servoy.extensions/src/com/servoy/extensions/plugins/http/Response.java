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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.message.BasicHeaderValueParser;
import org.apache.hc.core5.http.message.ParserCursor;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.scripting.JSMap;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * <p>The <code>Response</code> class provides utilities for managing HTTP responses in the Servoy
 * environment, enabling efficient handling of HTTP interactions. It integrates with Servoy's
 * scripting and JavaScript support, allowing for seamless debugging and interaction with HTTP
 * data.</p>
 *
 * <p>The class allows retrieval of HTTP status codes and their corresponding reason phrases, which
 * is useful for identifying issues such as permission errors or malformed requests. For example, it
 * can return status codes like <code>403</code> along with a reason phrase explaining the specific
 * error. The response body can be accessed as a string or as binary data, with support for
 * gzip-encoded content to handle a wide range of response formats.</p>
 *
 * <p>Headers can be retrieved in a structured manner as key-value mappings. Additionally,
 * developers can filter headers by specific names to extract targeted information. The
 * <code>getCharset</code> method enables retrieval of the response body's character set, ensuring
 * proper decoding for textual content. The class also provides error handling through the
 * <code>getException()</code> method, which returns exception messages related to failed
 * requests.</p>
 *
 * @author pbakker
 *
 */
@ServoyDocumented
public class Response implements IScriptable, IJavaScriptType
{
	private FileOrTextHttpResponse response;
	private Object response_body = null;
	private HttpUriRequest request;
	private String exceptionMessage;

	public Response()
	{

	}

	public Response(String exceptionMessage)
	{
		this.exceptionMessage = exceptionMessage;
	}

	public Response(FileOrTextHttpResponse response, HttpUriRequest request)
	{
		this.response = response;
		this.request = request;
	}

	public String[] getAllowedMethods()
	{
		if (this.response == null)
		{
			Debug.error("getAllowedMethods API was called while response is null due to request exception: " + exceptionMessage);
			return new String[0];
		}
		Iterator<Header> it = response.headerIterator(OptionsRequest.OPTIONS_HEADER);
		Set<String> methods = new HashSet<String>();
		while (it.hasNext())
		{
			Header header = it.next();
			ParserCursor cursor = new ParserCursor(0, header.getValue().length());
			HeaderElement[] elements = BasicHeaderValueParser.INSTANCE.parseElements(header.getValue(), cursor);
			for (HeaderElement element : elements)
			{
				methods.add(element.getName());
			}
		}
		return methods.toArray(new String[0]);
	}

	/**
	 * Gets the status code of the response, the list of the possible values is in HTTP_STATUS constants.<br/><br/>
	 *
	 * In case there was an exception executing the request, please ignore/do not use this value (it will be 0).
	 * You can check that situation using response.getException().
	 *
	 * @sample
	 * var status = response.getStatusCode();// compare with HTTP_STATUS constants
	 *
	 * @return the HTTP status code of the response. Returns 0 if an exception occurred during the request.
	 */
	public int js_getStatusCode()
	{
		if (response != null)
		{
			return response.getCode();
		}
		else
		{
			Debug.error("response.getStatusCode API was called while response is null due to request exception: " + exceptionMessage);
		}
		return 0;
	}

	/**
	 * Gets the status code's reason phrase. For example if a response contains status code 403 (Forbidden) it might be useful to know why.
	 *
	 * For example a Jenkins API req. could answer with "403 No valid crumb was included in the request" which will let you know
	 * that you simply have to reques a crumb and then put that in the request headers as "Jenkins-Crumb". But you could not know that from 403 status alone...
	 *
	 * @sample
	 * var statusReasonPhrase = response.getStatusReasonPhrase();
	 *
	 * @return the reason phrase associated with the HTTP status code of the response. Returns `null` if an exception occurred.
	 */
	public String js_getStatusReasonPhrase()
	{
		if (response != null)
		{
			return response.getReasonPhrase();
		}
		else
		{
			Debug.error("response.getStatusReasonPhrase API was called while response is null due to request exception: " + exceptionMessage);
		}
		return null;
	}

	/**
	 * Get the content of the response as String.
	 *
	 * @sample
	 * var pageData = response.getResponseBody();
	 *
	 * @return the response body content as a string. Returns an empty string if an exception occurred.
	 */
	public String js_getResponseBody()
	{
		if (response_body == null)
		{
			try
			{
				if (this.response != null)
				{
					response_body = response.getBodyText();
				}
				else
				{
					Debug.error("response.getResponseBody API was called while response is null due to request exception: " + exceptionMessage);
				}
			}
			catch (Exception e)
			{
				Debug.error("Error when getting response body for: " + (request != null ? request.getRequestUri() : "unknown request"), e); //$NON-NLS-1$
				response_body = "";
			}
		}
		return response_body instanceof String ? (String)response_body : "";

	}

	/**
	 * Get the content of response as binary data. It also supports gzip-ed content.
	 * Note this loads all content in memory at once, for large files you should use getFileUpload which allows usage of a temporary file and streaming.
	 *
	 * @sample
	 * var mediaData = response.getMediaData();
	 *
	 * @return the response body content as a byte array, supporting gzip-ed content. Returns `null` if an exception occurred.
	 */
	public byte[] js_getMediaData()
	{
		if (response_body == null)
		{
			if (this.response != null)
			{
				response_body = response.getBodyBytes();
			}
			else
			{
				Debug.error("response.getMediaData API was called while response is null due to request exception: " + exceptionMessage);
			}
		}
		return response_body instanceof byte[] ? (byte[])response_body : null;
	}

	/**
	 * Gets the headers of the response as name/value arrays.
	 *
	 * @sample
	 * var allHeaders = response.getResponseHeaders();
	 * var header;
	 *
	 * for (header in allHeaders) application.output(header + ': ' + allHeaders[header]);
	 *
	 * @return {Map<String,Array<String>>} a `JSMap` of all headers in the response or a specific header if `headerName` is provided. Returns `null` if an exception occurred.
	 */
	public JSMap js_getResponseHeaders()
	{
		return js_getResponseHeaders(null);
	}

	/**
	 * @clonedesc js_getResponseHeaders()
	 * @sample
	 * var contentLength = response.getResponseHeaders("Content-Length");
	 *
	 * @param headerName
	 *
	 * @return {Map<String,Array<String>>} a `JSMap` of all headers in the response or a specific header if `headerName` is provided. Returns `null` if an exception occurred.
	 */
	public JSMap js_getResponseHeaders(String headerName)
	{
		try
		{
			Header[] ha;
			JSMap sa = new JSMap();
			if (this.response != null)
			{
				if (headerName == null)
				{
					ha = response.getHeaders();
				}
				else
				{
					ha = response.getHeaders(headerName);
				}
				for (Header element : ha)
				{
					if (sa.containsKey(element.getName()))
					{
						sa.put(element.getName(), Utils.arrayAdd((String[])sa.get(element.getName()), element.getValue(), true));
					}
					else
					{
						sa.put(element.getName(), new String[] { element.getValue() });
					}
				}
			}
			else
			{
				Debug.error("response.getResponseHeaders API was called while response is null due to request exception: " + exceptionMessage);
			}
			return sa;
		}
		catch (Exception e)
		{
			Debug.error("Error when getting response headers for: " + (request != null ? request.getRequestUri() : "unknown request"), e); //$NON-NLS-1$
		}
		return null;
	}

	/**
	 * Get the charset of the response body.
	 *
	 * @sample
	 * var charset = response.getCharset();
	 *
	 * @return the character set of the response body. Returns `null` if an exception occurred or the character set is not defined.
	 */
	public String js_getCharset()
	{
		if (this.response != null)
		{
			ContentType contentType = response.getContentType();
			if (contentType != null && contentType.getCharset() != null)
			{
				return contentType.getCharset().displayName();
			}
		}
		else
		{
			Debug.error("response.getCharset API was called while response is null due to request exception: " + exceptionMessage);
		}
		return null;
	}

	/**
	 * Should be called to delete temporary file that holds the response content. The temporary file is created only for bigger responses.
	 *
	 * @return true if the temporary file with response data was deleted
	 */
	public boolean js_close()
	{
		if (response.getFile() != null)
		{
			return response.getFile().delete();
		}
		return true;
	}

	/**
	 * Getter for the exception message.
	 *
	 * @sample
	 * var exception = response.getException();
	 * @return the exception message
	 */
	public String js_getException()
	{
		return this.exceptionMessage;
	}

	/**
	 * Gets a file upload object (that contains a temporary file), which can be transformed to a JSFile and then used by file plugin.
	 * If response body is too small, there is no file available (this should be used only for large files), otherwise use getResponseBody or getMediaData.
	 *
	 * @sample
	 * plugins.file.onvertToJSFile(response.getFileUpload());
	 *
	 * @return fileupload object
	 */
	public JSFileUpload js_getFileUpload()
	{
		if (response.getFile() != null)
		{
			return new JSFileUpload(response.getFile(), response.getContentType() != null ? response.getContentType().getMimeType() : null);
		}
		return null;
	}
}
