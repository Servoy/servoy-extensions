# Spec: SVY-21518 — http plugin: multipart POST loses part headers over HTTP/2 (MultiPartEntityProducer ignores partial writes)

## 1. Goal
Make multipart `POST`/`PUT` requests sent through the `http` plugin produce a well-formed
multipart body over HTTP/2, so uploads that previously only worked with
`HttpClientConfig.forceHttp1 = true` succeed on the default (HTTP/2) transport.

The fix is to make `MultiPartEntityProducer` honour the `DataStreamChannel.write(ByteBuffer)`
return-value contract across its header, boundary and body paths, cooperating with the async
`produce()` / `requestOutput()` cycle rather than assuming each buffer drains in a single
`write` call. The deliberate BROWSER_COMPATIBLE header policy and the computed
`Content-Length` must stay intact.

## 2. Background

### 2.1 Reported problem
Multipart requests through the `http` plugin lose part headers and boundaries when the
connection negotiates HTTP/2. The server receives a corrupt multipart body — e.g. a Jira
Cloud attachment upload fails with `400 "Required part 'file' is not present."`. The same
call succeeds over HTTP/1.1. The reporter says it worked in 2026.06 and broke in 2026.09
after an httpclient5/httpcore5 upgrade, and points at
`MultiPartEntityProducer.writeBytes()` ignoring the return value of `channel.write()`.

Jira issue state: `In Progress`, Major, fix version `2026.12`. No comments, linked issues
or attachments on the ticket.

### 2.2 Root cause (from triage, authoritative)
`com.servoy.extensions/src/com/servoy/extensions/plugins/http/MultiPartEntityProducer.java`.

httpcore5's `DataStreamChannel.write(ByteBuffer)` is documented as a *partial* write: it
can return zero or a count smaller than `remaining()`, and the caller must loop until the
buffer is drained. The producer ignores that contract in two places:

1. `writeBytes(ByteArrayBuffer, DataStreamChannel)` (lines 260-264) does a single
   `channel.write(ByteBuffer.wrap(b.array()))` and discards the return value. This method
   emits every boundary, `Content-Disposition` / `Content-Type` header line and `CR_LF`
   separator (via `writePartHeader`, lines 277-312, and the closing-boundary branch in
   `produce`, lines 182-185). If the channel accepts only part of a buffer, the remainder
   is silently dropped and the next `writeBytes` writes a *different* buffer — truncating
   the part header/boundary and corrupting the multipart framing.
2. The inner anonymous `DataStreamChannel.write(src)` at line 162 returns `channel.write(src)`
   directly. For the *body* bytes that return value is propagated back to the wrapped inner
   producer (BasicAsyncEntityProducer / file producer), which does honour partial writes and
   retries the unwritten remainder — so body content largely survives. The header path
   (case 1) has no such retry, which is why headers/boundaries are the visible casualty.

Why HTTP/1.1 works and HTTP/2 doesn't: over HTTP/1.1 the session buffer almost always
accepts the whole small header buffer in one `write`, so the ignored return value equals
`remaining()` and nothing is lost. Under HTTP/2 flow control (connection/stream windows,
`SETTINGS_INITIAL_WINDOW_SIZE`) the channel frequently accepts only part of a buffer and
returns a short count, exposing the latent bug.

### 2.3 Content-Length interaction
`initContentLength` (lines 314-378) computes a fixed `Content-Length` and reports the entity
as non-chunked when every inner producer has a known length. When bytes are dropped the body
is shorter than the advertised length, which is a second reason an HTTP/2 server rejects the
request. The fix must preserve the exact advertised `Content-Length` — the reassembled body
must be byte-for-byte the same length as `getContentLength()` reports.

### 2.4 Regression window
The bug has existed since the producer was written and only became observable after the
dependency bumps. The newer httpcore5 is more aggressive about returning short writes under
H2 flow control. Current pinned versions: `httpclient.version=5.6.4`,
`httpcore.version=5.4.3` (`com.servoy.extensions/pom.xml` lines 43-44). Pinning an old
dependency is not the fix — the producer must respect the write contract.

## 3. Design

### 3.1 Approach (approved: Approach 1, with Approach 2 folded in)
Introduce explicit write-position state so `MultiPartEntityProducer` can resume a
partially-flushed buffer on the next `produce()` invocation instead of assuming each buffer
drains fully.

Key points:
- `writeBytes` must loop on `channel.write(src)` while bytes remain *and* the channel keeps
  accepting them. When `write` returns `0` (back-pressure), it must stop, retain the unwritten
  remainder as pending state, call `channel.requestOutput()`, and return control so the async
  engine re-invokes `produce()`; on the next `produce()` the pending bytes are flushed first,
  before any new buffer is emitted.
- The producer currently emits ~10 tiny buffers per part header inside a single `produce()`
  call (`writePartHeader`). Rather than track a resume point across many separate buffers,
  fold the whole part header/framing into **one contiguous buffer per logical chunk**
  (Approach 2) so there is a single partial-write seam to resume. A single `ByteBuffer` (or
  `ByteArrayBuffer` wrapped once) carrying the full `--boundary CRLF Content-Disposition …
  CRLF [Content-Type … CRLF] CRLF` sequence replaces the sequence of `writeBytes` calls in
  `writePartHeader`; the trailing `CR_LF` after each part body and the final
  `--boundary-- CRLF` closing framing are each their own single buffer with the same
  pending-remainder handling.
- The inner body channel at line 154-163 must keep returning the real `channel.write(src)`
  count so the wrapped producer continues to honour partial writes. The one change there is
  that the header for the current part must be fully flushed (including any pending remainder
  from a previous back-pressured call) before the body bytes are written — the "write header
  on first body write" trigger (lines 156-161) must be partial-write-safe too.
- `endStream` for a part (line 166-171) writes `CR_LF`; that must also be drained via the
  pending-remainder mechanism, and the producer must not advance to the next producer
  (`goToNextProducer`) until the separator is fully written.
- The closing-boundary branch (lines 180-187) must fully flush its buffer before calling
  `channel.endStream()`.

State to add (names illustrative): a pending `ByteBuffer` (or position into the current
chunk) that survives across `produce()` calls, plus a small per-part phase marker so a
resumed `produce()` knows whether it is mid-header, mid-body-separator or mid-closing-boundary.
`isRepeatable()` currently returns `true`; the added state must be reset on
`releaseResources()` so a repeated send starts clean (confirm whether the client ever reuses
the instance — if repeatability is relied on, resetting `currentIndex`, `initialized` flags
and pending state is required).

### 3.2 Invariants to preserve
- BROWSER_COMPATIBLE header policy: `Content-Type` is emitted only for file parts
  (`writeContentType == true` and `producer.getContentType() != null`), matching the current
  behaviour and the `addProducer(..., writeContentType)` call sites in
  `BaseEntityEnclosingRequest` (lines 215-265).
- Byte-exact `Content-Length`: `getContentLength()` / `initContentLength` must keep returning
  the same value, and the bytes actually written must sum to exactly that value. The folded
  single-buffer header must serialize to the identical bytes that `initContentLength`'s
  dry-run `writePartHeader` measures — if `writePartHeader` is refactored, the length
  measurement must be refactored in lockstep (ideally measure the same single buffer) so the
  two cannot drift.
- Public scripting API is unchanged — this is an internal producer; no `@ServoyDocumented`
  surface changes.
- Charset/encoding of boundary, field names and headers stays ISO-8859-1 as today.

### 3.3 Git history
- `MultiPartEntityProducer.writeBytes` and the `channel.write` seams are original to the
  class: commit `c856216e` "SVY-17509 Followup of SVY-17406: HTTP Post and inputStream vs
  byte[]" (lvostinar, 2022-11-11). `git blame` on lines 260-264 confirms the ignored
  return-value in `writeBytes` has been unchanged since that commit — a latent bug, not a
  recent regression.
- `writePartHeader`'s call site was last touched in `71a3506c` (2023-10-24) for the
  content-type policy; the write mechanics were unchanged.
- Dependency bumps on `com.servoy.extensions/pom.xml` across 2026 (`bd28b4a6` httpcore5
  `5.4.1→5.4.2`; `910808347` `5.4.2→5.4.3` + httpclient `5.6→5.6.2`; `3b4092ab` httpclient
  `5.6.2→5.6.4`) line up with the "worked in 2026.06 / broke in 2026.09" timeline. The newer
  httpcore5 surfaces short H2 writes that older versions masked.

## 4. Implementation plan
1. In `MultiPartEntityProducer`, replace the fire-and-forget `writeBytes` with a
   partial-write-aware flush that loops on `channel.write`, stops on a zero/short write,
   retains the unwritten remainder as pending state, and calls `channel.requestOutput()`.
2. Add per-instance pending-buffer + phase state and make `produce(channel)` flush any
   pending remainder first on each invocation before producing new bytes.
3. Fold `writePartHeader` into building one contiguous header buffer per part (Approach 2),
   written through the new flush path; keep the BROWSER_COMPATIBLE `Content-Type`-only-for-files
   rule exactly as-is.
4. Make the inner body `DataStreamChannel` (lines 144-178) partial-write-safe: ensure the
   part header is fully flushed before body bytes, keep returning the real `write` count for
   the body, and only run `endStream` → next-producer transition once the trailing `CR_LF`
   is fully drained.
5. Make the closing `--boundary-- CRLF` branch fully flush before `channel.endStream()`.
6. Keep `initContentLength` in sync with the new single-buffer header so the advertised
   `Content-Length` still equals the bytes written (measure the same buffer, or re-derive the
   measurement from the same builder).
7. Reset all added state in `releaseResources()` so `isRepeatable() == true` stays correct.
8. Add a JUnit 4 test in `com.servoy.extensions.test` (package
   `com.servoy.extensions.plugins.http`, mirroring the plugin package) that drives
   `produce()` with a `DataStreamChannel` stub returning short/zero writes.
9. Verify with `mvn -f com.servoy.extensions.test/pom.xml test`.

## 5. Acceptance criteria
- [ ] A `DataStreamChannel` stub that accepts only N bytes per `write` (and occasionally 0,
      then is re-driven via the `requestOutput`/`produce` cycle) is used to drive the full
      producer; the reassembled bytes form a well-formed multipart body with intact
      boundaries and part headers.
- [ ] The reassembled body length equals `getContentLength()` exactly (byte-for-byte), for a
      mix of string parameter parts and file/byte parts.
- [ ] A file part emits a `Content-Type` header; a plain string parameter part (added with
      `writeContentType == false`) does not — BROWSER_COMPATIBLE policy preserved.
- [ ] The test also passes with a full-accept stub (no regression for the HTTP/1.1-style path).
- [ ] `mvn -f com.servoy.extensions.test/pom.xml test` passes, including the new test and the
      existing `RestWSServletMultipartDetectionTest`.
- [ ] End-to-end (manual, by reporter/QA): a Jira Cloud attachment upload over HTTP/2
      (without `forceHttp1`) succeeds — no `400 "Required part 'file' is not present."`.

## 6. Out of scope
- Approach 3: replacing `MultiPartEntityProducer` with httpclient5's `MultipartEntityBuilder`
  / `AsyncEntityProducers.create(HttpEntity)`. Tracked as a separate hardening follow-up.
- Changing or removing the `HttpClientConfig.forceHttp1` option.
- Pinning/downgrading httpclient5 / httpcore5 dependency versions.
- Any change to the public `http` plugin scripting API.
- The jBPM workflow bundle (`com.servoy.extensions.workflow`).

## 7. Open questions
| Question | Owner | Status |
|----------|-------|--------|
| Does any caller actually reuse a `MultiPartEntityProducer` instance (relying on `isRepeatable()`), requiring full state reset on `releaseResources()`? | dev | Open — assume yes and reset defensively |
| Can CI/QA run the end-to-end Jira Cloud upload over real HTTP/2, or is the short-write unit test the only automated gate? | QA | Open |
| Should the single-buffer header refactor also drive `initContentLength` from the same builder to prevent length drift, or keep the separate dry-run measurement? | dev | Open — prefer shared builder |
