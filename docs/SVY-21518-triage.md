# Triage Report — SVY-21518

**Verdict:** PROCEED

## Reported problem
Multipart `POST` requests made through the `http` plugin lose part headers and
boundaries when the connection negotiates HTTP/2. The server receives a corrupt
multipart body — e.g. Jira Cloud attachment upload fails with
`400 "Required part 'file' is not present."`. The same call succeeds over HTTP/1.1
(`HttpClientConfig.forceHttp1 = true`). The reporter says it worked in 2026.06 and
broke in 2026.09 after an httpclient5/httpcore5 upgrade, and points at
`MultiPartEntityProducer.writeBytes()` ignoring the return value of `channel.write()`.

## Root-cause assessment
The ticket's diagnosis is correct and the fault is in this project's code:
`com.servoy.extensions/src/com/servoy/extensions/plugins/http/MultiPartEntityProducer.java`.

httpcore5's `DataStreamChannel.write(ByteBuffer)` is explicitly documented as a
*partial* write: "If the underlying byte stream is temporarily unable to accept more
data it can return zero ... The number of bytes written, possibly zero." (confirmed in
the httpcore5 5.4.3 sources, `org/apache/hc/core5/http/nio/DataStreamChannel.java`
lines 56-68). The caller is responsible for looping until the buffer is drained.

The producer ignores that contract in two places:

1. `writeBytes(ByteArrayBuffer, DataStreamChannel)` (lines 260-264) does a single
   `channel.write(ByteBuffer.wrap(b.array()))` and discards the return value. This is
   the method that emits every boundary, `Content-Disposition`/`Content-Type` header
   line, and CR_LF separator (see `writePartHeader`, lines 277-312, and `produce`,
   lines 182-185). If the channel accepts only some of those bytes, the remainder is
   silently dropped and the next `writeBytes` call writes a *different* buffer — so the
   part header/boundary is truncated and the multipart framing is corrupt.

2. The inner `DataStreamChannel.write(src)` at line 162 returns `channel.write(src)`
   directly. For the *body* bytes this return value is propagated back to the wrapped
   inner producer (BasicAsyncEntityProducer / file producer), which does honour partial
   writes and will retry the unwritten remainder — so body content largely survives.
   The header path (case 1) has no such retry, which is why headers/boundaries are the
   visible casualty.

Why HTTP/1.1 works and HTTP/2 doesn't: over HTTP/1.1 the session buffer almost always
accepts the whole small header buffer in one `write`, so the ignored return value is
`== remaining()` and nothing is lost. Under HTTP/2 flow control (connection/stream
windows, `SETTINGS_INITIAL_WINDOW_SIZE`) the channel frequently accepts only part of a
buffer and returns a short count, exposing the latent bug. This is a classic
"works until the transport back-pressures" defect, not an HTTP/2-specific regression in
Apache's code.

Regression window: the bug has existed since the producer was written
(`c856216e`, SVY-17509, 2022-11-11; `writeBytes` unchanged since). It only became
*observable* after the dependency bumps. Git history on `com.servoy.extensions/pom.xml`
shows httpcore5 moved `5.4.1 → 5.4.2 → 5.4.3` and httpclient5 `5.6 → 5.6.2 → 5.6.4`
across 2026, matching the "worked in 2026.06, broke in 2026.09" report. The newer
httpcore5 is simply more aggressive about returning short writes under H2 flow control.
Current pinned versions: `httpclient.version=5.6.4`, `httpcore.version=5.4.3`
(pom.xml lines 43-44).

Note on content-length: `MultiPartEntityProducer` computes a fixed `Content-Length`
(`initContentLength`, lines 314-378) and reports non-chunked when all inner producers
have a known length. If bytes are dropped the body is shorter than the advertised
length, which is a second reason H2 servers reject the request.

## Ticket premise check
- Is it in this project's code? **Yes** — `MultiPartEntityProducer`.
- Is the proposed approach right? **Yes.** The ticket asks to stop ignoring
  `channel.write()`'s return value. That is exactly the fix: loop until each buffer is
  fully written, calling `channel.requestOutput()` and returning control when the
  channel reports back-pressure (zero/short write) so the async engine re-invokes
  `produce()`. The one caveat is that a naive busy-loop inside `writeBytes` is wrong for
  a non-blocking channel — it must cooperate with the async produce/requestOutput cycle,
  which requires tracking a "pending bytes" position across `produce()` invocations
  rather than assuming each buffer drains in one pass.
- Simpler/more correct alternative? Replacing the hand-rolled producer with
  httpclient5's own `MultipartEntityBuilder` + `AsyncEntityProducers` is the most robust
  option but is a larger, higher-risk change with API/behaviour implications. See
  approaches below.

## Approaches considered
1. **Make `writeBytes` and the header/boundary path honour partial writes** —
   Track how much of the current logical "chunk" has been flushed and resume on the next
   `produce()` call when `write()` returns short, using `requestOutput()` to ask for
   another cycle. Fixes the exact reported defect with the smallest blast radius; keeps
   the existing content-length math and public behaviour. Cons: the current design
   writes many small buffers per `produce()` call and assumes each drains fully, so
   making it fully non-blocking-correct means introducing explicit write-position state
   across the boundary/header/body sequence — moderate complexity, needs careful testing
   against a real back-pressuring H2 channel.

2. **Buffer the whole part-header + framing into one contiguous buffer and drain it with
   a retry loop that respects the channel** — Reduces the number of partial-write seams
   from ~10 tiny buffers per part to one, making the state machine simpler. Still must
   handle a short write on that single buffer. Pro: fewer edge cases than approach 1.
   Con: still custom code; large file bodies still go through the inner producer path.

3. **Replace `MultiPartEntityProducer` with httpclient5's `MultipartEntityBuilder` /
   `AsyncEntityProducers.create(HttpEntity)`** — Delegates all framing and partial-write
   handling to Apache's maintained, H2-correct implementation; removes a whole class of
   custom async bugs permanently. Cons: biggest change; the custom producer exists to
   get BROWSER_COMPATIBLE-style behaviour (only emit `Content-Type` for file parts, see
   `addProducer(..., writeContentType)` and `BaseEntityEnclosingRequest` lines 215-265),
   so a drop-in replacement must reproduce that header policy and the streaming-file
   behaviour; risk of subtle wire-format differences for existing integrations. Better as
   a follow-up than the fix for this ticket.

4. **No code change (document `forceHttp1` as the workaround)** —
   Pro: zero risk; the workaround already exists. Con: leaves a real data-corruption bug
   in the default (HTTP/2) path; silently drops bytes on any multipart upload that hits
   H2 flow-control back-pressure, which will recur for larger payloads and other servers,
   not just Jira. Not acceptable as a resolution.

## Recommendation
PROCEED with **Approach 1** (honour the `DataStreamChannel.write` return value across the
header, boundary and body paths, cooperating with the async `produce()`/`requestOutput()`
cycle). It directly fixes the root cause the ticket identified, keeps the deliberate
BROWSER_COMPATIBLE header policy and the computed content-length intact, and has the
smallest compatibility surface. Approach 2's single-buffer framing can be folded in to
simplify the state machine. Treat Approach 3 (switch to Apache's `MultipartEntityBuilder`)
as a separate hardening follow-up, not part of this fix.

Verification note: the fix must be tested against a channel that returns *short* writes,
not just a full-accept fake. A unit test in `com.servoy.extensions.test` can drive
`produce()` with a `DataStreamChannel` stub whose `write` accepts only N bytes per call
(and occasionally 0, then signals via `requestOutput`), and assert the reassembled bytes
form a well-formed multipart body with intact boundaries/headers and the exact advertised
content length. End-to-end, re-run the Jira Cloud attachment upload over HTTP/2 (without
`forceHttp1`).

## Git history findings
- `MultiPartEntityProducer.writeBytes` and the `channel.write` seams are original to the
  class: commit `c856216e` "SVY-17509 Followup of SVY-17406: HTTP Post and inputStream vs
  byte[]" (lvostinar, 2022-11-11). The ignored-return-value pattern was present from day
  one; it is a latent bug, not a recent regression.
- `writePartHeader` call site was last touched in `71a3506c` (2023-10-24) for the
  content-type policy; the write mechanics were unchanged.
- Dependency bumps on `com.servoy.extensions/pom.xml` across 2026 (`bd28b4a6`
  httpcore5 `5.4.1→5.4.2`; `910808347` `5.4.2→5.4.3` + httpclient `5.6→5.6.2`;
  `3b4092ab` httpclient `5.6.2→5.6.4`) line up with the "worked in 2026.06 / broke in
  2026.09" timeline. The newer httpcore5 surfaces short H2 writes that older versions
  masked, which is what made the pre-existing bug visible — fixing the producer, not
  pinning an old dependency, is the correct response.

## Questions for the reporter (NEEDS_INPUT only)
N/A — verdict is PROCEED.
