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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ConsumerShutdownSignalCallback;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.ReturnCallback;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * Hand-written broker-free fake of the RabbitMQ {@link Channel} interface used
 * by the databroadcaster tests. It records the operations the plugin performs
 * so a test can assert on them without a live broker:
 * <ul>
 * <li>every {@code basicPublish} body (and its size) in order - so a test can
 * prove a flush was published instead of oversized row data, and that no
 * oversized body ever reaches the broker;</li>
 * <li>every {@code exchangeDeclare}, {@code queueDeclare}/{@code queueBind} and
 * {@code basicConsume} so a rebuild can be observed re-declaring the topology
 * and re-registering the consumer;</li>
 * <li>the installed {@link ShutdownListener}s, with
 * {@link #fireShutdown(ShutdownSignalException)} to simulate a broker- or
 * application-initiated channel close that also flips {@link #isOpen()} to
 * {@code false}, exactly like the real client.</li>
 * </ul>
 *
 * Only the methods the plugin actually calls are implemented; everything else
 * throws so an accidental new dependency in the plugin is caught by the test
 * rather than silently ignored.
 *
 * @author jcompagner
 */
@SuppressWarnings("nls")
public class FakeChannel implements Channel {
	/** Records a single basicPublish call. */
	static final class Publish {
		final String exchange;
		final String routingKey;
		final byte[] body;

		Publish(String exchange, String routingKey, byte[] body) {
			this.exchange = exchange;
			this.routingKey = routingKey;
			this.body = body;
		}

		int size() {
			return body.length;
		}
	}

	private static final AtomicInteger CHANNEL_NUMBERS = new AtomicInteger(0);

	private final int channelNumber = CHANNEL_NUMBERS.incrementAndGet();
	private volatile boolean open = true;

	final List<Publish> publishes = new ArrayList<>();
	final List<String> declaredExchanges = new ArrayList<>();
	final List<String> boundQueues = new ArrayList<>();
	final List<Consumer> consumers = new ArrayList<>();
	final List<ShutdownListener> shutdownListeners = new ArrayList<>();
	final List<Map<String, Object>> queueArguments = new ArrayList<>();

	/**
	 * When set, basicPublish throws this once, simulating a transient publish
	 * failure.
	 */
	IOException throwOnNextPublish;

	int publishCount() {
		return publishes.size();
	}

	Publish lastPublish() {
		return publishes.isEmpty() ? null : publishes.get(publishes.size() - 1);
	}

	/**
	 * Simulates the broker (or the application) closing this channel: flips
	 * {@link #isOpen()} to {@code false} and fires every installed shutdown
	 * listener with the given cause, just like the real client dispatches a channel
	 * shutdown.
	 */
	void fireShutdown(ShutdownSignalException cause) {
		this.open = false;
		for (ShutdownListener l : new ArrayList<>(shutdownListeners)) {
			l.shutdownCompleted(cause);
		}
	}

	@Override
	public int getChannelNumber() {
		return channelNumber;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public void addShutdownListener(ShutdownListener listener) {
		shutdownListeners.add(listener);
	}

	@Override
	public void removeShutdownListener(ShutdownListener listener) {
		shutdownListeners.remove(listener);
	}

	@Override
	public ShutdownSignalException getCloseReason() {
		return null;
	}

	@Override
	public void notifyListeners() {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type) throws IOException {
		declaredExchanges.add(exchange);
		return null;
	}

	@Override
	public AMQP.Queue.DeclareOk queueDeclare() throws IOException {
		return new FakeDeclareOk("amq.gen-fake" + channelNumber);
	}

	@Override
	public AMQP.Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete,
			Map<String, Object> arguments) throws IOException {
		queueArguments.add(arguments);
		return new FakeDeclareOk("amq.gen-fake" + channelNumber);
	}

	@Override
	public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException {
		boundQueues.add(queue);
		return null;
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive,
			Map<String, Object> arguments, Consumer callback) throws IOException {
		consumers.add(callback);
		return "consumerTag" + consumers.size();
	}

	@Override
	public void basicPublish(String exchange, String routingKey, AMQP.BasicProperties props, byte[] body)
			throws IOException {
		if (throwOnNextPublish != null) {
			IOException e = throwOnNextPublish;
			throwOnNextPublish = null;
			throw e;
		}
		publishes.add(new Publish(exchange, routingKey, body));
	}

	@Override
	public void close() throws IOException, TimeoutException {
		this.open = false;
	}

	private static UnsupportedOperationException notNeeded() {
		return new UnsupportedOperationException("not needed for the databroadcaster tests");
	}

	/** Minimal AMQP.Queue.DeclareOk carrying only the generated queue name. */
	private static final class FakeDeclareOk implements AMQP.Queue.DeclareOk {
		private final String queue;

		FakeDeclareOk(String queue) {
			this.queue = queue;
		}

		@Override
		public String getQueue() {
			return queue;
		}

		@Override
		public int getMessageCount() {
			return 0;
		}

		@Override
		public int getConsumerCount() {
			return 0;
		}

		@Override
		public int protocolClassId() {
			throw notNeeded();
		}

		@Override
		public int protocolMethodId() {
			throw notNeeded();
		}

		@Override
		public String protocolMethodName() {
			return "queue.declare-ok";
		}
	}

	// ---- everything below is unused by the plugin and throws ----

	@Override
	public Connection getConnection() {
		throw notNeeded();
	}

	@Override
	public void close(int closeCode, String closeMessage) throws IOException, TimeoutException {
		throw notNeeded();
	}

	@Override
	public void abort() throws IOException {
		throw notNeeded();
	}

	@Override
	public void abort(int closeCode, String closeMessage) throws IOException {
		throw notNeeded();
	}

	@Override
	public void addReturnListener(ReturnListener listener) {
		throw notNeeded();
	}

	@Override
	public ReturnListener addReturnListener(ReturnCallback returnCallback) {
		throw notNeeded();
	}

	@Override
	public boolean removeReturnListener(ReturnListener listener) {
		throw notNeeded();
	}

	@Override
	public void clearReturnListeners() {
		throw notNeeded();
	}

	@Override
	public void addConfirmListener(ConfirmListener listener) {
		throw notNeeded();
	}

	@Override
	public ConfirmListener addConfirmListener(ConfirmCallback ackCallback, ConfirmCallback nackCallback) {
		throw notNeeded();
	}

	@Override
	public boolean removeConfirmListener(ConfirmListener listener) {
		throw notNeeded();
	}

	@Override
	public void clearConfirmListeners() {
		throw notNeeded();
	}

	@Override
	public Consumer getDefaultConsumer() {
		throw notNeeded();
	}

	@Override
	public void setDefaultConsumer(Consumer consumer) {
		throw notNeeded();
	}

	@Override
	public void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException {
		throw notNeeded();
	}

	@Override
	public void basicQos(int prefetchCount, boolean global) throws IOException {
		throw notNeeded();
	}

	@Override
	public void basicQos(int prefetchCount) throws IOException {
		throw notNeeded();
	}

	@Override
	public void basicPublish(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props,
			byte[] body) throws IOException {
		throw notNeeded();
	}

	@Override
	public void basicPublish(String exchange, String routingKey, boolean mandatory, boolean immediate,
			AMQP.BasicProperties props, byte[] body) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, com.rabbitmq.client.BuiltinExchangeType type)
			throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, com.rabbitmq.client.BuiltinExchangeType type,
			boolean durable) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete,
			Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, com.rabbitmq.client.BuiltinExchangeType type,
			boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete,
			boolean internal, Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, com.rabbitmq.client.BuiltinExchangeType type,
			boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public void exchangeDeclareNoWait(String exchange, String type, boolean durable, boolean autoDelete,
			boolean internal, Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public void exchangeDeclareNoWait(String exchange, com.rabbitmq.client.BuiltinExchangeType type, boolean durable,
			boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeclareOk exchangeDeclarePassive(String name) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException {
		throw notNeeded();
	}

	@Override
	public void exchangeDeleteNoWait(String exchange, boolean ifUnused) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.DeleteOk exchangeDelete(String exchange) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey,
			Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public void exchangeBindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments)
			throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey)
			throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey,
			Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public void exchangeUnbindNoWait(String destination, String source, String routingKey,
			Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public void queueDeclareNoWait(String queue, boolean durable, boolean exclusive, boolean autoDelete,
			Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Queue.DeclareOk queueDeclarePassive(String queue) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Queue.DeleteOk queueDelete(String queue) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
		throw notNeeded();
	}

	@Override
	public void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey, Map<String, Object> arguments)
			throws IOException {
		throw notNeeded();
	}

	@Override
	public void queueBindNoWait(String queue, String exchange, String routingKey, Map<String, Object> arguments)
			throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey,
			Map<String, Object> arguments) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Queue.PurgeOk queuePurge(String queue) throws IOException {
		throw notNeeded();
	}

	@Override
	public GetResponse basicGet(String queue, boolean autoAck) throws IOException {
		throw notNeeded();
	}

	@Override
	public void basicAck(long deliveryTag, boolean multiple) throws IOException {
		throw notNeeded();
	}

	@Override
	public void basicNack(long deliveryTag, boolean multiple, boolean requeue) throws IOException {
		throw notNeeded();
	}

	@Override
	public void basicReject(long deliveryTag, boolean requeue) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, Consumer callback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback)
			throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, DeliverCallback deliverCallback,
			ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback,
			ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, Consumer callback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback,
			CancelCallback cancelCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback,
			ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback,
			CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, Consumer callback)
			throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments,
			DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments,
			DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments,
			DeliverCallback deliverCallback, CancelCallback cancelCallback,
			ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, String consumerTag, Consumer callback)
			throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback,
			CancelCallback cancelCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback,
			ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback,
			CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive,
			Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback)
			throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive,
			Map<String, Object> arguments, DeliverCallback deliverCallback,
			ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive,
			Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback,
			ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
		throw notNeeded();
	}

	@Override
	public void basicCancel(String consumerTag) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Basic.RecoverOk basicRecover() throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Basic.RecoverOk basicRecover(boolean requeue) throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Tx.SelectOk txSelect() throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Tx.CommitOk txCommit() throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Tx.RollbackOk txRollback() throws IOException {
		throw notNeeded();
	}

	@Override
	public AMQP.Confirm.SelectOk confirmSelect() throws IOException {
		throw notNeeded();
	}

	@Override
	public long getNextPublishSeqNo() {
		throw notNeeded();
	}

	@Override
	public boolean waitForConfirms() throws InterruptedException {
		throw notNeeded();
	}

	@Override
	public boolean waitForConfirms(long timeout) throws InterruptedException, TimeoutException {
		throw notNeeded();
	}

	@Override
	public void waitForConfirmsOrDie() throws IOException, InterruptedException {
		throw notNeeded();
	}

	@Override
	public void waitForConfirmsOrDie(long timeout) throws IOException, InterruptedException, TimeoutException {
		throw notNeeded();
	}

	@Override
	public void asyncRpc(Method method) throws IOException {
		throw notNeeded();
	}

	@Override
	public Command rpc(Method method) throws IOException {
		throw notNeeded();
	}

	@Override
	public long messageCount(String queue) throws IOException {
		throw notNeeded();
	}

	@Override
	public long consumerCount(String queue) throws IOException {
		throw notNeeded();
	}

	@Override
	public CompletableFuture<Command> asyncCompletableRpc(Method method) throws IOException {
		throw notNeeded();
	}
}
