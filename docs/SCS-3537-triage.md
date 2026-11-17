# Triage Report — SCS-3537

**Verdict:** PROCEED

## Reported problem

VHC's NGDesktop print client (`dd_printserverng`, a Windows VM running the Servoy
NGDesktop client against the `vhcjongens-prod` cloud) intermittently stops processing
its print queue. Jobs stay `PENDING` / `IN_PROCESS` and only clear on a client restart or
when a new job "nudges" the queue. The ticket was opened as a follow-up to SCS-3492 (which
was about HTML-404 PDFs) and initially suspected a session/websocket/load-balancer
stickiness issue tied to the DERP-2561 deploy.

During triage the symptom broadened:

- **14 Nov** stall correlated with a single broker-side error on the databroadcast fanout:
  `basic.publish precondition_failed: message size 22132111 is larger than configured max
  size 16777216`. A ~22 MB databroadcast exceeded RabbitMQ's 16 MB frame limit, the publish
  was rejected, **the channel was closed and never reopened**. Cloud Ops (Victor Laza) can
  still see the pod's single app-server AMQP connection on the broker **with no channel and
  no consumer**.
- **17 Nov** the problem recurred several times and — critically — **a regular D-ERP user
  also lost automatic screen refresh** at the same time (08:36 CET). That makes this a
  **pod-wide databroadcast outage**, not something specific to the print client.
- Recovery each time was a new job on 17 Nov, or a client restart earlier; a restart
  rebuilds the AMQP connection/channel.

Cloud Ops ruled out SCS-3492 for these stalls: PDF downloads were HTTP 200, both pods stayed
healthy in the load balancer, no pod/process/AMQP-container restarts, no session eviction,
no network hiccups. The one hard broker-side finding is the `precondition_failed` rejection.

The customer (Gert Knibbe) explicitly reframed the question for R&D: *"how does the Servoy
application server handle the closure of an AMQP channel after a rejected databroadcast —
does Servoy recreate the channel, and could a dead channel stop both publishing and
receiving databroadcasts while the client stays connected?"* Johan Compagner asked how a
22 MB payload can arise given blobs are normally stripped, and wondered about large
text/CLOB columns.

## Root-cause assessment

I read the plugin source (`DataNotifyBroadCaster`, `DataNotifyListener`, `NotifyData`),
the core `SQLEngine.DistributeDataChange`, the git history, and confirmed the RabbitMQ Java
client's recovery semantics against its documentation. The reported behaviour is fully
explained by a **plugin bug**, and it is two independent defects that combine:

**1. The single shared channel dies and is never rebuilt (the lasting-outage cause).**

- `DataNotifyBroadCaster.initialize()` creates **one** `Connection` and **one** `Channel`
  (`DataNotifyBroadCaster.java` lines 235–261) and uses that same channel for **both** the
  publish side (`DataNotifyListener`) and the consume side (the `DefaultConsumer` registered
  at line 337).
- The **only** recovery hook is a connection-level `RecoveryListener`
  (lines 236–260 for the consumer/flush side, and the mirror one in
  `DataNotifyListener` lines 60–82 for the publisher's `failedList`). There is **no**
  channel-level `ShutdownListener` and **no** code that re-creates the channel, re-declares
  the exchange/queue, or re-registers the consumer. `grep` for any size/frame/shutdown guard
  in the package returns nothing relevant.
- RabbitMQ Java-client automatic recovery is **connection-level**: it fires on I/O errors,
  missed heartbeats or an unexpected socket close. A channel closed **by the broker for a
  channel-level protocol error** (`precondition_failed`, reply-code 406) is a terminal event
  for that channel, and because the TCP **connection stays open**, the `RecoveryListener`
  never fires. Confirmed against the client docs / community reports. So after the 22 MB
  rejection the channel is permanently dead until the whole connection is rebuilt (restart).
- Consequence on the **publish** side: `DataNotifyListener.sendBytes(byte[], boolean)`
  (lines 123–160) sees `connection.isOpen() == true` but `channel.isOpen() == false`, so it
  takes the `testFailedList` branch (lines 133–142), finds the channel closed, and appends
  the payload to `failedList` and returns. `failedList` is only ever drained by
  `sendFailedList()`, which is called from the **connection** `RecoveryListener` (never
  fires here) or from a subsequent **healthy** publish (channel is dead, so never). The list
  therefore grows forever and **no databroadcast is published again** from that pod.
- Consequence on the **consume** side: the `DefaultConsumer` was registered on that same
  dead channel, so `onDataBroadcast` / `flushCachedDatabaseData` / `notifyDataChange`
  deliveries stop arriving. That is exactly why a plain D-ERP user also lost auto-refresh —
  it is not print-specific.
- Everything is swallowed as generic `Debug.error(e)` (e.g. line 154) with no identifying
  context, which matches "no RabbitMQ/channel errors we can find in the CCC application
  logs".

This precisely answers the customer's three questions: **no**, Servoy does not recreate the
channel after a `precondition_failed`; **yes**, the dead channel stops both publishing and
receiving; **yes**, `onDataBroadcast` stops while the client stays connected and operational.

**2. The oversize payload is not guarded, and core only strips byte[] blobs (the trigger).**

- Core `SQLEngine.DistributeDataChange.run()` (`SQLEngine.java` lines 2857–2878) strips
  large values to a `BlobMarker` **only** when `statement.getAction() == INSERT_ACTION`
  **and** `pks.getRowCount() == 1` **and** the value `instanceof byte[]` with
  `length > 16`. Verified independently — the user context is correct:
  - **Only `byte[]` is capped.** Large **String**/CLOB/text column values are sent
    verbatim. A large text/CLOB insert (Johan's hypothesis) would flow through uncapped.
  - **Only single-row inserts are stripped.** Multi-row changes and the **pk dataset
    itself** (`IDataSet pks`, serialized whole into `NotifyData`) are never size-capped, so
    a very large pk set can also produce a multi-MB message.
- The plugin serializes the whole `NotifyData` (`DataNotifyListener.sendBytes(NotifyData)`,
  lines 101–117) and publishes the raw bytes with **no size check** against the broker's
  frame max. So a ~22 MB `NotifyData` (large String value, or a large pk set) is handed
  straight to `basic.publish` and rejected — which is defect #1's trigger.

The 22 MB message is therefore entirely plausible from a large String/CLOB insert or a large
pk dataset, not a PDF blob (consistent with Gert finding no large PDFs and no 8 MB+ print
files). The exact producing datasource can only be pinned down by logging the payload
size/datasource before `basic.publish` — which is itself a good reason to add that logging as
part of the fix.

## Ticket premise check

- **Is the lasting outage a plugin bug, a broker config issue, or a hosting issue?**
  It is a **plugin bug**. The broker behaved correctly (rejecting an oversized frame is
  expected). Hosting changes Rene mentioned (orphaned-queue TTL, lower heartbeat) help
  **connection-drop** recovery but, as Rene himself notes, do **not** help a channel-only
  death — confirmed by the code: nothing in the plugin reacts to a channel shutdown.
- **Is "reconnect + don't send > 16 MB" the right fix?** Directionally yes, but the precise
  fix is **channel-level** recovery (not connection reconnect, which already exists and does
  not apply), plus a **payload-size guard** that degrades gracefully instead of killing the
  channel. Raising the broker frame limit is a band-aid: any larger payload would re-trigger
  it, and giant databroadcast frames are undesirable regardless.
- **Is any of it a core (`j2db_server`) responsibility?** Partly. The plugin fix stands on
  its own and fixes the outage. The **uncapped large String / large pk-set** behaviour in
  `SQLEngine.DistributeDataChange` is a separate, arguably-core improvement (cap or
  blob-marker large non-byte[] values, and/or fall back to a table flush when a payload would
  be oversized). That is read-only from this repo and belongs in a **separate SVY case**.

## Approaches considered

**A. Channel-level ShutdownListener + rebuild on the existing single channel.**
Add a `ShutdownListener` to the channel; on an application-initiated/unexpected channel
close (while the connection is still open), rebuild the channel, re-declare the
exchange/queue, re-register the consumer, and drain `failedList`.
*Pros:* directly fixes the lasting outage (both publish and consume recover without a
restart); minimal surface; keeps the single-channel design.
*Cons:* on its own, the very next oversized publish kills the new channel again (needs B to
avoid a churn loop); must guard against recovery storms / recursive shutdowns.

**B. Also guard serialized payload size and fall back to a flush when oversized.**
Before `basic.publish`, check the serialized `NotifyData` size against the broker frame max
(read `connection.getFrameMax()`, fall back to a safe default / configurable
`amqpbroadcaster.maxmessagesize`); if oversized, publish a datasource/table **flush**
`NotifyData` instead (receivers refetch), and log the datasource + size so the producing
action can be identified.
*Pros:* prevents the channel from ever being killed by an app-level oversize; graceful
degradation (consumers still invalidate their cache); gives the diagnostics Gert asked for.
*Cons:* a flush is coarser than a precise row notify (extra refetch); needs the right
size threshold (frame max vs. a margin).

**C. Split publish and consume onto separate channels.**
*Pros:* a publish-side channel death would no longer silence the consumer; better isolation.
*Cons:* larger change; does not by itself rebuild either channel, so still needs A; more
state to manage. Reasonable as a follow-up hardening, not required for the fix.

**D. Raise the broker frame limit / hosting-only.**
*Pros:* no code change.
*Cons:* does not fix the dead-channel outage at all; only postpones the size trip to a
bigger payload; large databroadcast frames remain undesirable. **Rejected as a solution**
(acceptable only as a temporary mitigation alongside a real fix).

**E. Core-side PK/String capping in `SQLEngine.DistributeDataChange` (separate SVY case).**
Extend the strip logic beyond single-row-insert `byte[]` to cap large String/CLOB values and
large pk datasets (or fall back to a table flush). *Pros:* removes the oversize payload at
the source for all broadcasters. *Cons:* core change, read-only from this repo, broader blast
radius; must not change single-pod (non-AMQP) semantics. **Track as a follow-up**, not part
of the plugin case.

## Recommendation

**PROCEED** with a plugin-only SVY case in `com.servoy.extensions` implementing **A + B
together** (they are interdependent: A restores recovery, B stops the channel being killed
in the first place and provides the diagnostics the customer needs):

1. Add a channel-level `ShutdownListener`; on an unexpected channel close while the
   connection is still open, rebuild the channel, re-declare exchange + queue, re-register
   the consumer, and drain `failedList` (guard against recursion / recovery storms; keep the
   single-channel design to stay minimal).
2. Before `basic.publish`, guard the serialized size against the broker frame max (or a
   configurable `amqpbroadcaster.maxmessagesize`); when oversized, send a datasource/table
   **flush** `NotifyData` instead of the raw row data, and log the datasource + payload size.
3. Replace the generic swallowed `Debug.error` on the publish/consume paths with messages
   that identify channel shutdown vs. recovery vs. oversize, so a recurrence is diagnosable
   from logs.

Keep **C** (split channels) as optional hardening and open a **separate SVY case** for
**E** (core `SQLEngine` capping of large String/pk payloads) linked to this one. Do **not**
pursue **D** as the solution.

All of this is testable without a live broker: `Channel`/`Connection` are interfaces and can
be driven with hand-written fakes (per project context) to simulate a `precondition_failed`
channel close and assert the rebuild + drain and the oversize fallback.

## Git history findings

- The broadcaster files were last touched by **`f30e3f25` — "SVY-21204 prevent RabbitMQ
  connection failure from blocking server [ai]"** (lvostinar, 30 Jul 2026). That commit added
  the bounded shared/shutdown executors, the `isOpen()` guards in `unload()`, the
  **connection**-state guard in `DataNotifyListener.sendBytes` that queues into `failedList`,
  and stored the `Connection` reference for state checks.
- That change is **directly relevant**: it hardened the plugin against **connection**-level
  failure (queue-and-replay-on-recovery), but it did **not** address a **channel**-level
  death with the connection still open. In fact the `failedList` + connection-only recovery
  design it formalised is exactly what strands messages forever in this scenario — the guard
  it added (`!connection.isOpen()` → queue) never trips here because the connection *is*
  open; instead the `channel.isOpen()` branch queues with no path to drain. So SCS-3537 is
  the **channel-level** gap left open by SVY-21204, not a regression introduced by it.
- Earlier history shows the single-channel/fanout design is long-standing (SVY-16421 message
  broadcast reuse of the same channel, SVY-20463 quorum-queue support, SVY-16030 TLS). The
  single channel was never built with channel-level recovery, so this is an original design
  gap rather than something recently broken.

## Questions for the reporter (NEEDS_INPUT only)

Not applicable — verdict is PROCEED.
