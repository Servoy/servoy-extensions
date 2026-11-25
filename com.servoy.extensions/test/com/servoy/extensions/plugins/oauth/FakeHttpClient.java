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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import com.github.scribejava.core.httpclient.HttpClient;
import com.github.scribejava.core.httpclient.multipart.MultipartPayload;
import com.github.scribejava.core.model.OAuthAsyncRequestCallback;
import com.github.scribejava.core.model.OAuthRequest.ResponseConverter;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;

/**
 * Transport for scribejava that only records the requests and answers with queued responses. No network.
 */
final class FakeHttpClient implements HttpClient
{
	record Request(Verb verb, String url, Map<String, String> headers, String body)
	{
	}

	final List<Request> requests = new ArrayList<>();
	private final Deque<Object> responses = new ArrayDeque<>();

	FakeHttpClient respond(int code, String contentType, String body)
	{
		Map<String, String> headers = new HashMap<>();
		if (contentType != null) headers.put("Content-Type", contentType);
		responses.add(new Response(code, "msg", headers, body));
		return this;
	}

	FakeHttpClient respondBinary(String contentType, byte[] content)
	{
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", contentType);
		responses.add(new Response(200, "OK", headers, new ByteArrayInputStream(content)));
		return this;
	}

	FakeHttpClient token(String accessToken, Integer expiresIn, String refreshToken, String scope)
	{
		StringBuilder json = new StringBuilder("{\"access_token\":\"").append(accessToken).append("\",\"token_type\":\"bearer\"");
		if (expiresIn != null) json.append(",\"expires_in\":").append(expiresIn);
		if (refreshToken != null) json.append(",\"refresh_token\":\"").append(refreshToken).append('"');
		if (scope != null) json.append(",\"scope\":\"").append(scope).append('"');
		return respond(200, "application/json", json.append('}').toString());
	}

	FakeHttpClient fail(IOException e)
	{
		responses.add(e);
		return this;
	}

	Request last()
	{
		return requests.get(requests.size() - 1);
	}

	private Response next(Verb verb, String url, Map<String, String> headers, String body) throws IOException
	{
		requests.add(new Request(verb, url, new HashMap<>(headers), body));
		Object next = responses.poll();
		if (next == null) throw new IllegalStateException("No response queued for " + verb + " " + url);
		if (next instanceof IOException e) throw e;
		return (Response)next;
	}

	@Override
	public Response execute(String userAgent, Map<String, String> headers, Verb httpVerb, String completeUrl, byte[] bodyContents) throws IOException
	{
		return next(httpVerb, completeUrl, headers, bodyContents == null ? null : new String(bodyContents, StandardCharsets.UTF_8));
	}

	@Override
	public Response execute(String userAgent, Map<String, String> headers, Verb httpVerb, String completeUrl, String bodyContents) throws IOException
	{
		return next(httpVerb, completeUrl, headers, bodyContents);
	}

	@Override
	public Response execute(String userAgent, Map<String, String> headers, Verb httpVerb, String completeUrl, MultipartPayload bodyContents)
		throws IOException
	{
		return next(httpVerb, completeUrl, headers, "<multipart>");
	}

	@Override
	public Response execute(String userAgent, Map<String, String> headers, Verb httpVerb, String completeUrl, File bodyContents) throws IOException
	{
		return next(httpVerb, completeUrl, headers, "<file " + bodyContents.getName() + ">");
	}

	@Override
	public <T> Future<T> executeAsync(String userAgent, Map<String, String> headers, Verb httpVerb, String completeUrl, byte[] bodyContents,
		OAuthAsyncRequestCallback<T> callback, ResponseConverter<T> converter)
	{
		throw new UnsupportedOperationException("the plugin only uses synchronous requests");
	}

	@Override
	public <T> Future<T> executeAsync(String userAgent, Map<String, String> headers, Verb httpVerb, String completeUrl,
		MultipartPayload bodyContents, OAuthAsyncRequestCallback<T> callback, ResponseConverter<T> converter)
	{
		throw new UnsupportedOperationException("the plugin only uses synchronous requests");
	}

	@Override
	public <T> Future<T> executeAsync(String userAgent, Map<String, String> headers, Verb httpVerb, String completeUrl, String bodyContents,
		OAuthAsyncRequestCallback<T> callback, ResponseConverter<T> converter)
	{
		throw new UnsupportedOperationException("the plugin only uses synchronous requests");
	}

	@Override
	public <T> Future<T> executeAsync(String userAgent, Map<String, String> headers, Verb httpVerb, String completeUrl, File bodyContents,
		OAuthAsyncRequestCallback<T> callback, ResponseConverter<T> converter)
	{
		throw new UnsupportedOperationException("the plugin only uses synchronous requests");
	}

	@Override
	public void close()
	{
	}
}
