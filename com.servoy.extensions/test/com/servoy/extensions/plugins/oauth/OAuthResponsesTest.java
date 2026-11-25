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
package com.servoy.extensions.plugins.oauth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.github.scribejava.core.model.Response;

public class OAuthResponsesTest
{
	private static Response text(int code, String contentType, String body)
	{
		return new Response(code, "msg", contentType == null ? Map.of() : Map.of("Content-Type", contentType), body);
	}

	@Test
	public void successfulResponsesAreTypedByContentType()
	{
		assertInstanceOf(OAuthResponseJSON.class, ResponseFactory.create(text(200, "application/json", "{}")));
		assertEquals(OAuthResponseText.class, ResponseFactory.create(text(200, "text/html", "x")).getClass());
		assertEquals(OAuthResponseText.class, ResponseFactory.create(text(200, "application/xml", "<a/>")).getClass());
		assertEquals(OAuthResponseBinary.class,
			ResponseFactory.create(new Response(200, "OK", Map.of("Content-Type", "image/png"), new ByteArrayInputStream(new byte[] { 1 }))).getClass());
	}

	@Test
	public void errorResponsesAreAlwaysText()
	{
		OAuthResponse response = ResponseFactory.create(text(404, null, "not found"));

		assertEquals(OAuthResponseText.class, response.getClass());
		assertEquals(404, response.getCode());
		assertEquals("not found", ((OAuthResponseText)response).getBody());
	}

	@Test
	public void aSuccessfulResponseWithoutContentTypeFails()
	{
		// current behaviour (bug): the Content-Type header is used without a null check
		assertThrows(NullPointerException.class, () -> ResponseFactory.create(text(200, null, "x")));
	}

	@Test
	public void jsonObjectsAndArraysAreParsed()
	{
		JSONObject object = new OAuthResponseJSON(text(200, "application/json", "{\"a\":1}")).getAsJSON();
		JSONObject array = new OAuthResponseJSON(text(200, "application/json", "[1,2]")).getAsJSON();

		assertEquals(1, object.getInt("a"));
		assertEquals(2, array.getJSONArray("array").length());
	}

	@Test
	public void emptyOrInvalidJsonIsNull()
	{
		assertNull(new OAuthResponseJSON(text(200, "application/json", "")).getAsJSON());
		assertNull(new OAuthResponseJSON(text(200, "application/json", null)).getAsJSON());
		assertNull(new OAuthResponseJSON(text(200, "application/json", "{broken")).getAsJSON());
	}

	@Test
	public void binaryContentIsReadFromTheStream()
	{
		byte[] data = { 1, 2, 3, (byte)255 };

		assertArrayEquals(data, new OAuthResponseBinary(new Response(200, "OK", Map.of(), new ByteArrayInputStream(data))).getContent());
	}

	@Test
	public void unreadableContentIsNull()
	{
		InputStream broken = new InputStream()
		{
			@Override
			public int read() throws IOException
			{
				throw new IOException("broken");
			}
		};

		assertNull(new OAuthResponseBinary(new Response(200, "OK", Map.of(), broken)).getContent());
		assertNull(new OAuthResponseBinary(text(200, "image/png", "no stream")).getContent());
		assertNull(new OAuthResponseText(new Response(200, "OK", Map.of(), broken)).getBody());
		assertNull(new OAuthResponseJSON(new Response(200, "OK", Map.of(), broken)).getAsJSON());
	}
}
