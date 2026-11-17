# Spec: SVY-21803 — AMQP databroadcaster channel recovery and payload size guard

## 1. Goal

Make the AMQP databroadcaster plugin (`com.servoy.extensions`, package
`com.servoy.extensions.plugins.broadcaster`) survive a broker-initiated **channel-level**
close without a server restart, and stop the plugin from ever sending a databroadcast large
enough to trigger that close.

Concretely:

1. When the broker closes the single shared channel for a channel-level protocol error
   (e.g. `precondition_failed`, reply-code 406) while the TCP **connection stays open**,
   rebuild the channel, re-declare the exchange/queue, re-register the consumer and drain the
   publisher's `failedList` — so both publishing and receiving of databroadcasts resume
   automatically.
2. Before `basic.publish`, guard the serialized payload size against the broker frame max (or
   a configurable `amqpbroadcaster.maxmessagesize`); when a `NotifyData` payload would be
   oversized, send a coarse datasource/table **flush** `NotifyData` instead of the raw row
   data, so the channel is never killed by an app-level oversize and consumers still
   invalidate their caches.
3. Emit diagnostic logging that distinguishes channel-shutdown vs. recovery vs. oversize, and
   that identifies the datasource and payload size, so a recurrence is diagnosable from logs.

Everything must stay testable without a live broker (`Channel`/`Connection` are interfaces).

## 2. Background

### 2.1 Current design

`DataNotifyBroadCaster` is an `IServerPlugin`. In `initialize(IServerAccess)` it:

- Builds a `ConnectionFactory` from `amqpbroadcaster.*` settings and opens **one**
  `Connection` (`DataNotifyBroadCaster.java` ~line 235).
- Opens **one** `Channel` from that connection (~line 261), declares a **fanout** exchange,
  and on that same channel: declares/binds a queue and registers a `DefaultConsumer`
  (`basicConsume`, ~line 337) for the **consume** side.
- Registers a `DataNotifyListener` (constructed with that same `channel` and `connection`,
  ~line 264) for the **publish** side.
- `registerMessageBroadcastConsumer(IBroadcastMessageConsumer)` returns an
  `IBroadcastMessageSender` lambda that serializes a `BroadcastMessage` and publishes it on
  **the same shared channel** (~line 346–386).

So a **single `Connection` and a single `Channel`** are shared by the publisher, the message
sender and the consumer.

Recovery today is **connection-level only**:

- `DataNotifyBroadCaster` adds a `RecoveryListener` to the connection (~line 238) that, on
  `handleRecovery`, flushes all used datasources (`dataNotifyService.getUsedDataSources()` →
  `flushCachedDatabaseData`).
- `DataNotifyListener` adds its own connection `RecoveryListener` (~line 60) that calls
  `sendFailedList()` on `handleRecovery`.

`DataNotifyListener` holds `channel` and `connection` as **`final` fields** (lines 43–44).
`sendBytes(byte[], boolean)` (lines 123–160):

- If `!connection.isOpen()` → queue into `failedList` and return.
- Else if there is a backlog and `!channel.isOpen()` → queue into `failedList` and return;
  if the channel is open → `sendFailedList()` first.
- Then `channel.basicPublish(exchangeName, routingKey, null, bytes)`; on any exception,
  `Debug.error(e)` and queue into `failedList`.

`failedList` is **only** drained by `sendFailedList()`, which is called from the connection
`RecoveryListener` or from a subsequent **healthy** publish.

`getRequiredPropertyNames()` documents all current `amqpbroadcaster.*` keys; any new key must
be added there.

### 2.2 The failure

RabbitMQ Java-client automatic recovery is **connection-level**: it fires on I/O errors,
missed heartbeats or an unexpected socket close. A channel closed **by the broker for a
channel-level protocol error** (`precondition_failed`, reply-code 406 — raised when a
`basic.publish` body exceeds the negotiated frame max) is terminal for that channel, and
because the **connection stays open** the connection `RecoveryListener` never fires.

With the single shared channel:

- **Publish side:** `connection.isOpen()` is still `true` but `channel.isOpen()` is `false`.
  `sendBytes` takes the `channel`-closed branch and appends to `failedList`, which can now
  never be drained (connection recovery never fires; no healthy publish is ever possible on
  the dead channel). The list grows forever and **no databroadcast is published again** from
  that pod.
- **Consume side:** the `DefaultConsumer` was registered on that same dead channel, so
  `flushCachedDatabaseData` / `notifyDataChange` deliveries stop arriving — every client on
  the pod loses automatic cache invalidation / screen refresh.

Only a full restart (which rebuilds the connection and therefore the channel) recovers it.
This is the **channel-level** gap left open by commit
`f30e3f25 — "SVY-21204 prevent RabbitMQ connection failure from blocking server [ai]"`
(lvostinar, 30 Jul 2026), which formalised the connection-level queue-and-replay design but
did not handle a channel death with the connection still open. It is an original design gap,
not a regression. Reported via support ticket **SCS-3537** (a pod-wide databroadcast outage
after a ~22 MB databroadcast was rejected by the 16 MB broker frame max).

### 2.3 Why core doesn't prevent the oversize

Core `SQLEngine.DistributeDataChange.run()` (read-only from this repo) strips large values to
a `BlobMarker` **only** when all of the following hold: the action is `INSERT_ACTION`, the pk
dataset has exactly one row, and the value is a `byte[]` longer than 16 bytes. Therefore:

- **Only `byte[]` is capped** — large `String`/CLOB/text column values are serialized
  verbatim into `NotifyData`.
- **Only single-row inserts are stripped** — multi-row changes and the serialized **pk
  dataset** itself (`IDataSet pks`) are never size-capped.

So a large String/CLOB insert or a large pk set produces a multi-MB `NotifyData`, which the
plugin hands to `basic.publish` with **no size check**, triggering the broker rejection. The
core-side capping of large String/pk payloads is a **separate follow-up SVY case** (see
Out of scope); this spec fixes the plugin so the oversize is handled gracefully regardless.

## 3. Design

Keep the **single-channel, single-connection, fanout** design. Add channel-level recovery and
a pre-publish size guard, both driven from the plugin, and route the publisher's channel
reference through the plugin so a rebuilt channel is actually used.

### 3.1 Channel-level recovery

Add a channel-level `ShutdownListener` (`channel.addShutdownListener(...)`) when the channel
is created in `initialize`. On shutdown, decide whether to rebuild:

- **Rebuild only when** the connection is still open (`connection.isOpen()`) **and** the
  shutdown was **not** application-initiated (`ShutdownSignalException.isInitiatedByApplication()`
  is `false`, i.e. not our own `unload()`/`channel.close()`). This specifically targets
  broker-initiated channel closes (`precondition_failed` and similar) while the connection
  lives.
- **Do not rebuild** when the connection itself is closing/closed — the existing connection
  `RecoveryListener` path handles that (and will re-flush), so channel rebuild here would race
  it.

Rebuild sequence (must run off the shutdown callback thread — use the plugin's
`sharedExecutor`/`shutdownExecutor`, never block the RabbitMQ shutdown dispatch thread, which
can deadlock):

1. Create a fresh channel from the still-open connection (`connection.createChannel()`).
2. Re-declare the fanout exchange (`channel.exchangeDeclare(exchangeName, "fanout")`).
3. Re-declare + re-bind the consumer queue with the same `queuetype` /
   `singleactiveconsumer` args as the original declare, and re-register the `DefaultConsumer`
   (`basicConsume`) — reuse the existing consumer-construction code (extract it to a method so
   both the initial `initialize` path and the rebuild path share it).
4. Add a fresh `ShutdownListener` to the new channel.
5. Publish the **new channel reference** into the `DataNotifyListener` (see below) and into
   the message-sender path, then **drain `failedList`** (`sendFailedList()` on the listener).

**Channel reference swap.** `DataNotifyListener` currently stores `channel` as a `final`
field. Rebuild requires the listener to publish on the **new** channel. Design decision:
**the plugin owns the channel and the listener reads it through a holder**, rather than the
listener owning recovery:

- Introduce a tiny mutable holder the plugin updates and the listener (and the message-sender
  lambda) read on every publish, e.g. a `ChannelHolder` with a `volatile Channel` field and
  `getChannel()` / `setChannel(Channel)`. (Equivalent acceptable form: pass the
  `DataNotifyBroadCaster` itself or a `Supplier<Channel>` to the listener; keep it minimal.)
- `DataNotifyListener`'s `channel` field becomes the holder (or a supplier), and every place
  that today reads `channel` reads `holder.getChannel()` at call time. `connection` can remain
  a `final` field (the connection is not rebuilt here).
- The `registerMessageBroadcastConsumer` sender lambda must likewise read the current channel
  from the same holder (today it captures `this.channel` implicitly via the field — change it
  to read the holder so it publishes on the rebuilt channel).
- On rebuild, the plugin calls `holder.setChannel(newChannel)` **before** draining
  `failedList`.

**Guard against recovery storms / recursion.** Because step 5 drains `failedList` by
publishing, and an oversized entry could (absent the size guard) immediately re-trip a close,
and a rebuild attempt can itself fail:

- Serialize rebuilds: use an `AtomicBoolean rebuilding` (or a single-permit lock) so only one
  rebuild runs at a time; if a rebuild is already in progress, the shutdown callback returns
  without starting another.
- Back off on repeated failures: cap consecutive rebuild attempts within a short window (e.g.
  N attempts, then log an error and stop auto-rebuilding until the next connection-level
  recovery). Do **not** retry in a tight loop.
- The drain in step 5 must go through the normal publish path so the **size guard (3.2)**
  applies to every drained entry — this is what actually stops the rebuild→oversize→close
  churn loop.
- Suppress rebuild when `unload()` is in progress (a shutdown flag the plugin sets in
  `unload()`), since that close is application-initiated anyway.

### 3.2 Serialized payload size guard + flush fallback

Add a size check **before** every `basic.publish` of a `NotifyData`:

- Determine the maximum allowed body size:
  - Read `amqpbroadcaster.maxmessagesize` if set (bytes). Otherwise derive from the broker:
    `connection.getFrameMax()` minus a safety margin for AMQP framing/headers (frame header +
    body-frame overhead). If `getFrameMax()` returns 0 (unlimited) fall back to a safe default
    (e.g. the classic 16 MB minus margin) so we still protect against the common broker config.
  - Compute the effective limit once (or per publish, cheap) and reuse.
- The payload is already serialized to a `byte[]` before publish, so the size is
  `bytes.length` — no extra serialization pass is needed on the publish path.
- **When within the limit:** publish as today.
- **When oversized:** do **not** publish the raw row data. Instead publish a coarse
  **flush** `NotifyData` for the affected datasource/table so receivers refetch, and log the
  datasource + original payload size at `warn`. Specifically:
  - For a `notifyDataChange`-origin `NotifyData` (row-level: `server_name`/`table_name`/`pks`),
    build the datasource string for that server/table and send
    `new NotifyData(originServerUUID, dataSource, broadcastFilters)` (the flush constructor),
    preserving `broadcastFilters`. The receiver's consumer already routes a `dataSource`-only
    `NotifyData` to `flushCachedDatabaseData`.
  - A `flushCachedDatabaseData`-origin `NotifyData` (already datasource-only) that is somehow
    oversized is pathological (a flush payload is tiny); if it ever happens, log and publish a
    plain flush with the same datasource.
  - The flush payload must itself be re-checked against the limit (it will be far smaller) to
    guarantee we never hand an oversized frame to the broker.
  - **Build the datasource consistently.** The row-level `NotifyData` carries `server_name` +
    `table_name`; use the project's standard datasource-string construction
    (`DataSourceUtils`-style `db:/server/table`) so the flush targets the correct datasource.
    Confirm the exact helper during implementation (do not hand-roll the format).
- **Apply to both publish paths:**
  - `DataNotifyListener.sendBytes` (the databroadcast path) — size-check before `basicPublish`,
    and the oversize fallback needs the originating `NotifyData` (not just the raw bytes), so
    the guard belongs on the `NotifyData` overload (`sendBytes(NotifyData)`) where the typed
    fields are still available, deciding there whether to serialize-and-publish or to serialize
    the flush form.
  - `registerMessageBroadcastConsumer` sender — a `BroadcastMessage` has no "flush" fallback
    (it is an opaque application message). For that path, if the serialized message exceeds the
    limit, **skip the publish and log an error** identifying the oversize (never hand it to the
    broker and kill the channel). This keeps the channel alive; the application is responsible
    for not broadcasting oversized messages.

### 3.3 Diagnostics / logging

Replace the generic swallowed `Debug.error(e)` on the publish/consume paths with contextful
messages:

- **Channel shutdown detected:** log (at `warn`) that the AMQP channel was shut down, whether
  it was broker- or application-initiated, the `ShutdownSignalException` reason (reply-code /
  reply-text, e.g. `precondition_failed`), and that a rebuild is being attempted.
- **Channel rebuild:** log start, success (exchange/queue re-declared, consumer re-registered,
  `failedList` size drained) and failure (with the exception) distinctly.
- **Oversize:** log (at `warn`) the datasource, the original serialized size, and the
  effective limit, and that a flush fallback (or, for `BroadcastMessage`, a skip) was sent.
- Keep messages greppable and include exchange/routingKey where useful. Use
  `com.servoy.j2db.util.Debug`.

### 3.4 New settings

Any new key must be documented in `getRequiredPropertyNames()`:

- `amqpbroadcaster.maxmessagesize` — "Maximum serialized databroadcast payload size in bytes
  before falling back to a datasource flush instead of the raw row data (default: derived from
  the broker frame max with a safety margin)."

If a bounded rebuild-attempt count or window is made configurable, document those keys too
(e.g. `amqpbroadcaster.channelrebuild.maxattempts`); prefer sensible hard-coded defaults and
only expose settings if needed.

## 4. Implementation plan

Ordered, file by file. All files keep the AGPL v3 header, tabs, UTF-8, Java 21.

1. **`broadcaster/ChannelHolder.java` (new, small).** A minimal holder with a
   `volatile Channel channel`, `getChannel()` and `setChannel(Channel)`. (Alternatively a
   `Supplier<Channel>` wired from the plugin — pick one and keep it consistent.) This is the
   seam that lets a rebuilt channel reach the publisher and the message sender.

2. **`broadcaster/DataNotifyListener.java`.**
   - Replace the `final Channel channel` field with the `ChannelHolder` (keep `connection`
     final). Read `holder.getChannel()` in `sendBytes(byte[], boolean)` and `sendFailedList()`.
   - Move the size guard into `sendBytes(NotifyData)`: compute the effective limit (from the
     plugin/connection/setting), serialize, and if oversized build+serialize the flush-form
     `NotifyData` instead, logging datasource + size (3.2 / 3.3). Then call
     `sendBytes(byte[], boolean)` as today.
   - Expose a way for the plugin to trigger `sendFailedList()` after a rebuild (make it
     package-visible or add a `drainFailedList()` method).
   - Keep the connection `RecoveryListener` as-is.

3. **`broadcaster/DataNotifyBroadCaster.java`.**
   - Extract the queue-declare + consumer-construction + `basicConsume` block from
     `initialize` into a private method (e.g. `declareAndConsume(Channel)`), so both initial
     setup and rebuild share it.
   - Extract the fanout `exchangeDeclare` into the same or a sibling helper.
   - Create the `ChannelHolder`, set it to the initial channel, pass it to the
     `DataNotifyListener` constructor, and have `registerMessageBroadcastConsumer` read the
     channel from the holder (not the field).
   - Add a channel-level `ShutdownListener` (a `installShutdownListener(Channel)` helper so the
     rebuilt channel gets one too). On shutdown: if rebuild is warranted (3.1), submit the
     rebuild to the plugin executor; otherwise log and return.
   - Implement `rebuildChannel()`: `rebuilding` guard, bounded attempts, create channel →
     re-declare exchange → `declareAndConsume` → `installShutdownListener` →
     `holder.setChannel(newChannel)` → `listener.drainFailedList()`; log per 3.3.
   - Set a `boolean unloading` flag in `unload()` and check it in the shutdown listener so our
     own close does not trigger a rebuild.
   - Add the `amqpbroadcaster.maxmessagesize` entry (and any rebuild-tuning keys) to
     `getRequiredPropertyNames()`.
   - Provide the effective max-size computation (reads the setting, else
     `connection.getFrameMax()` minus margin, else default) — expose it so the listener can use
     it (method on the plugin or pass the value/holder to the listener).

4. **`broadcaster/NotifyData.java`.** Likely **no change**. If building the flush form needs a
   datasource derived from `server_name`/`table_name` and that is cleaner as a helper on
   `NotifyData`, add a small package-visible accessor/helper — but prefer using the standard
   datasource-string utility externally and leave `NotifyData` untouched. Do not alter its
   serialization (the `serialVersionUID` / field-compat comment must be respected).

5. **Tests** in `com.servoy.extensions.test` (`src/test/java`, JUnit 4, `**/*Test.java`): see
   §5. Add broker-free fakes for `Channel`/`Connection`.

## 5. Acceptance criteria (broker-free, with fake `Channel`/`Connection`)

Using hand-written fakes implementing `com.rabbitmq.client.Channel` /
`com.rabbitmq.client.Connection` (and capturing `ShutdownListener`s, `basicPublish` calls and
`basicConsume` registrations):

1. **Channel rebuild on broker close.** Given an open connection and an open channel with a
   registered consumer and a `DataNotifyListener`, when the channel fires a
   `ShutdownListener` with a **non-application-initiated** shutdown and `connection.isOpen()`
   is `true`, then a **new** channel is created, the fanout exchange is re-declared, the queue
   is re-declared/bound, the consumer is re-registered (`basicConsume` called again), and the
   listener publishes on the **new** channel afterwards.
2. **`failedList` drains on channel recovery.** Given entries queued in `failedList` while the
   channel was closed, after rebuild those entries are published (in order) on the new channel
   and `failedList` is emptied — driven by channel recovery, not connection recovery.
3. **No rebuild on application-initiated close.** When the shutdown is application-initiated
   (our `unload()` / explicit `channel.close()`), **no** new channel is created.
4. **No rebuild when connection is also closed.** When the channel shuts down because the
   connection is closing (`connection.isOpen()` false), the channel is **not** rebuilt here
   (connection recovery path owns that).
5. **Rebuild is single-flighted.** Two overlapping channel-shutdown signals result in **one**
   rebuild (the `rebuilding` guard), and repeated rebuild failures stop after the bounded
   attempt count rather than looping.
6. **Oversize row notify falls back to flush.** Given a `notifyDataChange` whose serialized
   `NotifyData` exceeds the effective limit, the listener publishes a **datasource flush**
   `NotifyData` (datasource derived from server/table, `broadcastFilters` preserved) instead of
   the raw row data, and the published body size is within the limit. A `warn` log records the
   datasource and original size.
7. **In-limit notify publishes normally.** A `NotifyData` under the limit is published
   unchanged (raw row data), exactly as today.
8. **`maxmessagesize` setting honoured.** With `amqpbroadcaster.maxmessagesize` set low, a
   payload above it triggers the flush fallback even though it is below the broker frame max;
   with the setting unset, the broker-`getFrameMax()`-derived limit (minus margin) is used, and
   a 0/unlimited frame max falls back to the safe default.
9. **Oversized `BroadcastMessage` is skipped, not published.** An oversized message from the
   `registerMessageBroadcastConsumer` sender is **not** handed to `basicPublish` (channel stays
   open) and an error is logged.
10. **Channel never killed by the guard.** In a simulated loop (publish an oversized payload,
    then drain), no raw oversized body is ever passed to `basicPublish`, so the fake channel is
    never driven into the `precondition_failed`/shutdown state by the plugin.

## 6. Out of scope

- **Core `SQLEngine.DistributeDataChange` capping** of large `String`/CLOB values and large pk
  datasets at the source — a **separate follow-up SVY case** (read-only from this repo),
  linked to SVY-21803.
- **Splitting publish and consume onto separate channels** — optional hardening; this spec
  keeps the single-channel design and recovers it. Can be a later improvement.
- **Raising the broker frame limit** — rejected as a solution (only postpones the trip to a
  larger payload; large databroadcast frames remain undesirable). Acceptable only as a
  temporary hosting-side mitigation, not part of this change.
- Changing connection-level recovery (already handled by SVY-21204) beyond what the channel
  rebuild needs.

## 7. Open questions

| # | Question | Default if unanswered |
|---|----------|-----------------------|
| 1 | Safety margin between `connection.getFrameMax()` and the effective max body size — exact bytes to subtract for AMQP frame/header overhead? | Subtract a conservative fixed margin (e.g. a few KB) below `getFrameMax()`; make the resulting limit the guard value. |
| 2 | Default max size when `getFrameMax()` is 0/unlimited and `amqpbroadcaster.maxmessagesize` is unset? | 16 MB minus the §7.1 margin (matches the common broker default that triggered SCS-3537). |
| 3 | Bounded rebuild attempts: how many, over what window, and should it be configurable? | Small fixed cap (e.g. 3) with short backoff, hard-coded; expose a setting only if review asks. |
| 4 | Channel-reference seam: `ChannelHolder` vs. passing the plugin / a `Supplier<Channel>` to the listener? | `ChannelHolder` with a `volatile Channel` (smallest, clearest). |
| 5 | Exact datasource-string helper to build the flush datasource from `server_name`/`table_name`? | Use the project's standard `DataSourceUtils` construction; confirm the exact call during implementation. |
| 6 | Should a drained `failedList` entry that is *still* oversized (e.g. queued before the guard existed) be converted to a flush on drain? | Yes — route drains through the guarded publish path so they get the same flush fallback. |
