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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * @author lvostinar
 *
 */
public class FixedSimpleResponseConsumer extends AbstractAsyncResponseConsumer<SimpleHttpResponse, byte[]>
{

	FixedSimpleResponseConsumer(final AsyncEntityConsumer<byte[]> entityConsumer)
	{
		super(entityConsumer);
	}

	public static FixedSimpleResponseConsumer create()
	{
		return new FixedSimpleResponseConsumer(new SimpleAsyncEntityConsumer());
	}

	@Override
	public void informationResponse(final HttpResponse response, final HttpContext context) throws HttpException, IOException
	{
	}

	@Override
	protected SimpleHttpResponse buildResult(final HttpResponse response, final byte[] entity, final ContentType contentType)
	{
		final SimpleHttpResponse simpleResponse = SimpleHttpResponse.copy(response);
		if (entity != null)
		{
			// workaround https://issues.apache.org/jira/browse/HTTPCLIENT-2244
			if (contentType != null && contentType.getCharset() == null)
			{
				Charset defaultCharset = StandardCharsets.ISO_8859_1;
				ContentType mimeContentType = ContentType.getByMimeType(contentType.getMimeType());
				if (mimeContentType != null && mimeContentType.getCharset() != null)
				{
					defaultCharset = mimeContentType.getCharset();
				}
				simpleResponse.setBody(entity, contentType.withCharset(defaultCharset));
			}
			else
			{
				simpleResponse.setBody(entity, contentType);
			}
		}
		return simpleResponse;
	}

}
