/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2026 Servoy BV

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
package com.servoy.extensions.plugins.broadcaster;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import com.rabbitmq.client.BlockedCallback;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.UnblockedCallback;

/**
 * Hand-written broker-free fake of the RabbitMQ {@link Connection} interface.
 * It toggles {@link #isOpen()}, reports a configurable {@link #getFrameMax()}
 * (so the size-guard tests can exercise the frame-max-derived limit and the
 * 0/unlimited fallback) and hands out a queue of pre-created
 * {@link FakeChannel}s from {@link #createChannel()} so a test can watch the
 * rebuild swap in a new channel.
 *
 * Only the methods the plugin calls are implemented; the rest throw.
 *
 * @author jcompagner
 */
@SuppressWarnings("nls")
public class FakeConnection implements Connection {
	private volatile boolean open = true;
	private int frameMax;

	/**
	 * Channels handed out by createChannel() in order; the test pre-loads the ones
	 * a rebuild should pick up.
	 */
	private final java.util.Deque<FakeChannel> channelsToHandOut = new java.util.ArrayDeque<>();
	int createChannelCount = 0;
	/**
	 * When set, createChannel() throws this the given number of times to simulate a
	 * failing rebuild.
	 */
	int failCreateChannelTimes = 0;

	FakeConnection(int frameMax) {
		this.frameMax = frameMax;
	}

	void setOpen(boolean open) {
		this.open = open;
	}

	void setFrameMax(int frameMax) {
		this.frameMax = frameMax;
	}

	void enqueueChannel(FakeChannel channel) {
		channelsToHandOut.addLast(channel);
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public int getFrameMax() {
		return frameMax;
	}

	@Override
	public Channel createChannel() throws IOException {
		createChannelCount++;
		if (failCreateChannelTimes > 0) {
			failCreateChannelTimes--;
			throw new IOException("simulated createChannel failure");
		}
		if (channelsToHandOut.isEmpty()) {
			throw new IOException("no more fake channels enqueued");
		}
		return channelsToHandOut.removeFirst();
	}

	private static UnsupportedOperationException notNeeded() {
		return new UnsupportedOperationException("not needed for the databroadcaster tests");
	}

	@Override
	public InetAddress getAddress() {
		throw notNeeded();
	}

	@Override
	public int getPort() {
		throw notNeeded();
	}

	@Override
	public int getChannelMax() {
		throw notNeeded();
	}

	@Override
	public int getHeartbeat() {
		throw notNeeded();
	}

	@Override
	public Map<String, Object> getClientProperties() {
		throw notNeeded();
	}

	@Override
	public String getClientProvidedName() {
		throw notNeeded();
	}

	@Override
	public Map<String, Object> getServerProperties() {
		throw notNeeded();
	}

	@Override
	public Channel createChannel(int channelNumber) throws IOException {
		throw notNeeded();
	}

	@Override
	public void close() throws IOException {
		this.open = false;
	}

	@Override
	public void close(int closeCode, String closeMessage) throws IOException {
		throw notNeeded();
	}

	@Override
	public void close(int timeout) throws IOException {
		throw notNeeded();
	}

	@Override
	public void close(int closeCode, String closeMessage, int timeout) throws IOException {
		throw notNeeded();
	}

	@Override
	public void abort() {
		throw notNeeded();
	}

	@Override
	public void abort(int closeCode, String closeMessage) {
		throw notNeeded();
	}

	@Override
	public void abort(int timeout) {
		throw notNeeded();
	}

	@Override
	public void abort(int closeCode, String closeMessage, int timeout) {
		throw notNeeded();
	}

	@Override
	public void addBlockedListener(BlockedListener listener) {
		throw notNeeded();
	}

	@Override
	public BlockedListener addBlockedListener(BlockedCallback blockedCallback, UnblockedCallback unblockedCallback) {
		throw notNeeded();
	}

	@Override
	public boolean removeBlockedListener(BlockedListener listener) {
		throw notNeeded();
	}

	@Override
	public void clearBlockedListeners() {
		throw notNeeded();
	}

	@Override
	public ExceptionHandler getExceptionHandler() {
		throw notNeeded();
	}

	@Override
	public String getId() {
		throw notNeeded();
	}

	@Override
	public void setId(String id) {
		throw notNeeded();
	}

	@Override
	public void addShutdownListener(com.rabbitmq.client.ShutdownListener listener) {
		throw notNeeded();
	}

	@Override
	public void removeShutdownListener(com.rabbitmq.client.ShutdownListener listener) {
		throw notNeeded();
	}

	@Override
	public com.rabbitmq.client.ShutdownSignalException getCloseReason() {
		throw notNeeded();
	}

	@Override
	public void notifyListeners() {
		throw notNeeded();
	}
}
