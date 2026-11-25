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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.TimeValue;

/**
 * An async http client without network: it lets the real request producer write the request (headers and body are
 * recorded) and feeds a queued response through the real response consumer, all on the calling thread.
 */
final class FakeAsyncClient extends CloseableHttpAsyncClient
{
	record Exchange(HttpRequest request, EntityDetails entity, byte[] body, HttpContext context)
	{
		String bodyText()
		{
			return body == null ? null : new String(body, java.nio.charset.StandardCharsets.ISO_8859_1);
		}
	}

	private record Canned(int code, String reason, String contentType, String contentEncoding, byte[] body, Header[] headers)
	{
	}

	private static final Object CANCEL = new Object();

	final List<Exchange> exchanges = new ArrayList<>();
	private final Deque<Object> responses = new ArrayDeque<>();

	FakeAsyncClient respond(int code, String contentType, byte[] body, String... headerNameValues)
	{
		return respond(code, contentType, null, body, headerNameValues);
	}

	FakeAsyncClient respond(int code, String contentType, String contentEncoding, byte[] body, String... headerNameValues)
	{
		Header[] headers = new Header[headerNameValues.length / 2];
		for (int i = 0; i < headers.length; i++)
		{
			headers[i] = new BasicHeader(headerNameValues[i * 2], headerNameValues[i * 2 + 1]);
		}
		responses.add(new Canned(code, "Reason " + code, contentType, contentEncoding, body, headers));
		return this;
	}

	FakeAsyncClient fail(Exception e)
	{
		responses.add(e);
		return this;
	}

	FakeAsyncClient cancel()
	{
		responses.add(CANCEL);
		return this;
	}

	Exchange last()
	{
		return exchanges.get(exchanges.size() - 1);
	}

	@Override
	protected <T> Future<T> doExecute(HttpHost target, AsyncRequestProducer requestProducer, AsyncResponseConsumer<T> responseConsumer,
		HandlerFactory<AsyncPushConsumer> pushHandlerFactory, HttpContext context, FutureCallback<T> callback)
	{
		BasicFuture<T> future = new BasicFuture<>(callback);
		try
		{
			HttpRequest[] request = new HttpRequest[1];
			EntityDetails[] entity = new EntityDetails[1];
			requestProducer.sendRequest((req, details, ctx) -> {
				request[0] = req;
				entity[0] = details;
			}, context);
			byte[] body = entity[0] == null ? null : produce(requestProducer);
			exchanges.add(new Exchange(request[0], entity[0], body, context));

			Object next = responses.poll();
			if (next == null) throw new IllegalStateException("No response queued for " + request[0]);
			if (next == CANCEL)
			{
				future.cancel();
			}
			else if (next instanceof Exception e)
			{
				responseConsumer.failed(e);
				future.failed(e);
			}
			else
			{
				respond((Canned)next, responseConsumer, context, future);
			}
		}
		catch (Exception e)
		{
			future.failed(e);
		}
		return future;
	}

	private static byte[] produce(AsyncRequestProducer producer) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		boolean[] ended = { false };
		DataStreamChannel channel = new DataStreamChannel()
		{
			@Override
			public void requestOutput()
			{
			}

			@Override
			public int write(ByteBuffer src)
			{
				int n = src.remaining();
				byte[] bytes = new byte[n];
				src.get(bytes);
				out.write(bytes, 0, n);
				return n;
			}

			@Override
			public void endStream()
			{
				ended[0] = true;
			}

			@Override
			public void endStream(List< ? extends Header> trailers)
			{
				ended[0] = true;
			}
		};
		for (int i = 0; !ended[0] && i < 10_000; i++)
		{
			producer.produce(channel);
		}
		if (!ended[0]) throw new IllegalStateException("the request body never ended");
		producer.releaseResources();
		return out.toByteArray();
	}

	private static <T> void respond(Canned canned, AsyncResponseConsumer<T> consumer, HttpContext context, BasicFuture<T> future) throws Exception
	{
		BasicHttpResponse response = new BasicHttpResponse(canned.code(), canned.reason());
		for (Header h : canned.headers())
		{
			response.addHeader(h);
		}
		EntityDetails details = canned.body() == null ? null : new EntityDetails()
		{
			@Override
			public long getContentLength()
			{
				return canned.body().length;
			}

			@Override
			public String getContentType()
			{
				return canned.contentType();
			}

			@Override
			public String getContentEncoding()
			{
				return canned.contentEncoding();
			}

			@Override
			public boolean isChunked()
			{
				return false;
			}

			@Override
			public Set<String> getTrailerNames()
			{
				return null;
			}
		};
		consumer.consumeResponse(response, details, context, new FutureCallback<T>()
		{
			@Override
			public void completed(T result)
			{
				future.completed(result);
			}

			@Override
			public void failed(Exception ex)
			{
				future.failed(ex);
			}

			@Override
			public void cancelled()
			{
				future.cancel();
			}
		});
		if (details != null)
		{
			consumer.updateCapacity(increment -> {
			});
			if (canned.body().length > 0) consumer.consume(ByteBuffer.wrap(canned.body()));
			consumer.streamEnd(null);
		}
	}

	@Override
	public void start()
	{
	}

	@Override
	public IOReactorStatus getStatus()
	{
		return IOReactorStatus.ACTIVE;
	}

	@Override
	public void awaitShutdown(TimeValue waitTime)
	{
	}

	@Override
	public void initiateShutdown()
	{
	}

	@Override
	public void register(String hostname, String uriPattern, Supplier<AsyncPushConsumer> supplier)
	{
	}

	@Override
	public void close(CloseMode closeMode)
	{
	}

	@Override
	public void close()
	{
	}
}
