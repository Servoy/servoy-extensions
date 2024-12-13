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

package com.servoy.extensions.plugins.rest_ws;

import java.io.UnsupportedEncodingException;

import org.apache.commons.fileupload.FileItem;
import org.mozilla.javascript.annotations.JSGetter;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>WsContents</code> class represents the contents or parts of an HTTP request. It
 * provides functionality to access metadata and content details of uploaded files or form data
 * in a request. The contents include attributes such as the content name, field name, content
 * type, size, and raw byte data. Additionally, it allows retrieval of the content as a string
 * with a specified encoding.</p>
 *
 * <p>Key methods include <code>getName()</code> to get the content name, <code>getFieldName()</code>
 * to retrieve the field name, <code>getContentType()</code> to access the MIME type, and
 * <code>getSize()</code> to get the content size in bytes. The class also provides methods like
 * <code>getBytes()</code> to obtain raw byte data and <code>getString(encoding)</code> to convert
 * the content into a string using a specified encoding.</p>
 *
 * @author rgansevles
 *
 */
@ServoyDocumented(scriptingName = "WsContents")
public class WsContents implements IScriptable, IJavaScriptType
{
	private final FileItem fileItem;

	/**
	 * @param fileItem
	 */
	public WsContents(FileItem fileItem)
	{
		if (fileItem == null) throw new NullPointerException("fileItem");
		this.fileItem = fileItem;
	}

	/**
	 * Get contents name.
	 * @sample
	 * var request = plugins.rest_ws.getRequest();
	 * var contents = request.getContents();
	 * if (contents.length > 0) {
	 *    var name = contents[0].getName();
	 * }
	 */
	@JSGetter
	public String getName()
	{
		return fileItem.getName();
	}

	/**
	 * Get contents field name.
	 * @sample
	 * var request = plugins.rest_ws.getRequest();
	 * var contents = request.getContents();
	 * if (contents.length > 0) {
	 *    var fieldName = contents[0].getFieldName();
	 * }
	 */
	@JSGetter
	public String getFieldName()
	{
		return fileItem.getFieldName();
	}

	/**
	 * Get contents bytes.
	 * @sample
	 * var request = plugins.rest_ws.getRequest();
	 * var contents = request.getContents();
	 * if (contents.length > 0) {
	 *    var bytes = contents[0].getBytes();
	 * }
	 */
	@JSGetter
	public byte[] getBytes()
	{
		return fileItem.get();
	}

	/**
	 * Get contents content type.
	 * @sample
	 * var request = plugins.rest_ws.getRequest();
	 * var contents = request.getContents();
	 * if (contents.length > 0) {
	 *    var contentType = contents[0].getContentType();
	 * }
	 */
	@JSGetter
	public String getContentType()
	{
		return fileItem.getContentType();
	}

	/**
	 * Get contents size.
	 * @sample
	 * var request = plugins.rest_ws.getRequest();
	 * var contents = request.getContents();
	 * if (contents.length > 0) {
	 *    var size = contents[0].getSize();
	 * }
	 */
	@JSGetter
	public long getSize()
	{
		return fileItem.getSize();
	}

	/**
	 * Get contents as string.
	 * @sample
	 * var request = plugins.rest_ws.getRequest();
	 * var contents = request.getContents();
	 * if (contents.length > 0) {
	 *    var string = contents[0].getString('UTF-8');
	 * }
	 */
	@JSGetter
	public String getString(String encoding) throws UnsupportedEncodingException
	{
		return fileItem.getString(encoding);
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj != null && obj.getClass() == getClass() && fileItem.equals(((WsContents)obj).fileItem);
	}
}
