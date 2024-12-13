/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2022 Servoy BV

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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

import com.servoy.j2db.util.Pair;

/**
 * @author lvostinar
 *
 */
public class FileOrTextResponseConsumer extends AbstractAsyncResponseConsumer<FileOrTextHttpResponse, Pair<byte[], File>>
{

	FileOrTextResponseConsumer(final AsyncEntityConsumer<Pair<byte[], File>> entityConsumer)
	{
		super(entityConsumer);
	}

	public static FileOrTextResponseConsumer create()
	{
		return new FileOrTextResponseConsumer(new FileOrBinAsyncEntityConsumer());
	}

	@Override
	public void informationResponse(final HttpResponse response, final HttpContext context) throws HttpException, IOException
	{
	}

	@Override
	protected FileOrTextHttpResponse buildResult(final HttpResponse response, Pair<byte[], File> content, final ContentType contentType)
	{
		final FileOrTextHttpResponse copy = new FileOrTextHttpResponse(response.getCode());
		copy.setVersion(response.getVersion());
		copy.setReasonPhrase(response.getReasonPhrase());
		for (final Iterator<Header> it = response.headerIterator(); it.hasNext();)
		{
			copy.addHeader(it.next());
		}
		copy.setContentType(contentType);
		if (content != null)
		{
			copy.setBodyBytes(content.getLeft());
			copy.setFile(content.getRight());
		}
		return copy;
	}

}
