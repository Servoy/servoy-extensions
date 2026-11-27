# Triage Report — SVY-21895

**Subject:** rest_ws: unauthenticated unsafe deserialization with sender-chosen javaClass (RCE)

**Verdict:** PROCEED

## Reported problem
A customer penetration test (Critical, CVSS 9.8) reports unauthenticated remote code execution through the rest_ws plugin. The JSON request body is deserialized with a class name chosen by the sender (`javaClass` hint), and setters of that class are called with body values. This was reached with an unusual request charset (EBCDIC), which got past the customer's WAF. Reported evidence is a class-existence oracle (500 vs 404), a setter side effect seen through an out-of-band DNS canary, and further stages on a test system. The ticket has no attachments or comments.

Reported behaviour, kept separate from the proposed fix. The ticket proposes four things: decode only after authorization, a class allowlist in servoy-client, a charset restriction in rest_ws, and an audit of other `fromJSON` callers.

## Root-cause assessment
I confirmed the root cause in two places by reading the code. The core part is outside this repo.

1. **Unrestricted class loading (core, servoy-client repo, `servoy_shared`).** `JSONSerializerWrapper.getSerializer()` overrides `getClassFromHint()` (about lines 181-233). It calls `Class.forName(className)` on the `javaClass` value from the JSON and has no allowlist. The same applies to array hints, which use `Class.forName("[L" + ... + ";")`. The `BeanSerializer` is registered as the least specific serializer, so any bean-like class can be instantiated and have its setters called. This is the real vulnerability, and it must be fixed in core.
2. **Decode before authorization (this repo).** In `RestWSServlet.doPost`, `doPut` and `doPatch` (about lines 435, 482 and 533), the argument expression `decodeContent(...)` is evaluated before `wsService(...)` is called. The call chain `wsService -> doWsService -> checkAuthorization` (about lines 709-748) therefore runs after deserialization. Anonymous requests get deserialized even when `ws_authenticate` or authorized groups are configured. The ticket's claim matches the code.
3. **Unchecked charset (this repo).** `decodeContent` takes the `charset` parameter from the Content-Type header with default `CHARSET_DEFAULT` (about line 1245). `decode()` passes it straight to `Charset.forName(ch)` (about line 1260). There is no whitelist. An unknown charset gives a 500 from an exception. Any JDK-supported charset, including IBM037, is accepted. Multipart parts reach `decode()` through `getMultipartContent` (about line 1409), using a per-part charset header with no check.
4. **Deserialization entry points in this repo.** The only rest_ws call sites are `RestWSServlet.decode()` lines 1264 (JSON) and 1267 (XML converted to JSON, so it is equally affected). Other users of `JSONSerializerWrapper.fromJSON` are in the serialize plugin (`SerializeProvider.js_fromJSON`, `StringSerializer`, `BlobSerializer`). Their input comes from solution code and database values, not directly from an unauthenticated HTTP body. They are still exposed if a solution passes untrusted data. They are fixed by the core allowlist and need no change in this repo.

Important consequence: reordering authorization does not remove the vulnerability.
- Solutions with no `ws_authenticate` and no authorized groups allow anonymous access by design (`checkAuthorization` returns early with "allow all access"). They stay exploitable.
- Authenticated users, and users who can pass a weak `ws_authenticate`, remain able to use it.

The reorder only shrinks who can reach the sink. The allowlist in core is the actual fix, so both parts belong in this work.

## Ticket premise check
The premise holds. The code confirms the ticket's description of the root cause. Two additions:
- The ticket calls the reorder a fix. It is hardening only (see above). The core allowlist is mandatory, and that is a separate case in the servoy-client repo (the ticket also says so).
- Restricting charsets is defense in depth against WAF bypass. It does not remove the vulnerability, but it is cheap and low risk. Rejecting unknown or unsupported charsets with 400/415 is also better than the current 500. Need to decide the accepted set (UTF-8, US-ASCII, ISO-8859-1, plus likely UTF-16 variants and windows-1252, to avoid breaking existing clients).

I did not reproduce the exploit and did not check the interim WAF details.

## Approaches considered
1. **Decode after authorization in rest_ws** — pros: removes anonymous exposure for protected solutions; small change. Needs restructuring: pass a lazy decoder (supplier) into `wsService/doWsService` and call it after `checkAuthorization`, for POST, PUT, PATCH and multipart parts. Cons: not a full fix on its own, because anonymous solutions and authenticated users stay exposed. Behaviour change: invalid body or charset errors now come after the 401 instead of before it.
2. **Charset restriction in rest_ws** — pros: simple, blocks the reported WAF-bypass form; clean 400/415 instead of 500. Cons: only defense in depth; risk of rejecting legitimate clients using other charsets, so the allowed list needs care.
3. **Class allowlist in `JSONSerializerWrapper.getClassFromHint` (servoy-client, separate case)** — pros: removes the root cause for every caller (rest_ws, serialize plugin, others). Cons: lives in another repo; the allowlist must cover the types Servoy really round-trips (NativeObject, NativeArray, JSMap, primitives, dates, Servoy data types, arrays of those), otherwise it can break the serialize plugin. A rest_ws-only deny of `javaClass` hints is also possible but is a different behaviour change.
4. **No code change** — pros: none. Cons: the issue is a confirmed critical, unauthenticated RCE path (CVSS 9.8 as rated by the customer). Not acceptable. The customer WAF/auth workaround is only interim.

## Recommendation
PROCEED with a combination:
- In this repo (`com.servoy.extensions`, rest_ws): (a) decode request bodies only after `checkAuthorization()` has succeeded, for POST, PUT, PATCH and multipart parts; (b) restrict accepted charsets and reject others with 400 or 415, including part-level charsets; (c) add broker-free JUnit 6 tests (mock the servlet request/response and the plugin) proving that a request without valid credentials is rejected before any decoding or class loading, and that disallowed charsets are rejected.
- Raise or link a separate SVY case against servoy-client for the `getClassFromHint` allowlist (mandatory for the actual fix), and for auditing other `fromJSON` callers. This repo cannot change `servoy_shared`. The allowlist unit tests the ticket asks for belong in that repo.
- Coordinate release: the rest_ws change alone should not be described as resolving the issue.
- Because the case is Private security level, keep payloads out of tests, commit messages and docs.

## Git history findings
- `RestWSServlet.decode`/`decodeContent` come from long-standing code: multipart support (SVY-4547), PATCH (SVY-6019), jakarta conversion (SVY-19812). The decode-before-auth ordering is in the original design, not a recent regression. I did not run line-level `git blame`, so exact introducing commits for the argument-evaluation order are not confirmed.
- Recent rest_ws commits (SVY-21824, SVY-21436) are unrelated.
- `JSONSerializerWrapper` in servoy-client is based on jabsorb (SVY-8146 upgraded to jabsorb 1.3.2). The `Class.forName` in `getClassFromHint` is Servoy's own override of the library behaviour.
- No dependency bump in `pom.xml` is involved; this is not a version regression.
- No earlier spec for this key exists in `docs/`.
