# Spec: SVY-21780 — Expose full SMTP error in getLastSendMailExceptionMsg

## 1. Goal
When `plugins.mail.sendMail` (or bulk send) fails, `plugins.mail.getLastSendMailExceptionMsg()` must contain the real SMTP error (for example `550 5.7.1 Service unavailable, Client host [...] blocked using Spamhaus`) and not only the generic "Invalid Addresses".

## 2. Background
- Triage: `docs/SVY-21780-triage.md` (verdict PROCEED, approved approach 1).
- `MailServer.sendBulkMail` (lines ~146-169) and `MailServer.sendMail` (lines ~171-193) in `com.servoy.extensions/src/com/servoy/extensions/plugins/mail/MailServer.java` catch every `Exception` and rethrow `new Exception(ex.getMessage())` ("not all exceptions are serializable"). This drops the JavaMail `getNextException()`/`getCause()` chain, where the SMTP reply lives (`SendFailedException` -> `SMTPAddressFailedException`).
- Client side (`mail/client/MailProvider.java` ~1154-1163, ~1191-1200) tries to append `getCause().getMessage()`, but the cause is always null after the server-side rethrow. That code is correct and stays unchanged.
- Reporter is on 2025.3.4 LTS; fix versions include 2025.3.8 LTS (this branch).

### Git history
Triage found only older unrelated commits on `MailServer.java` (SVY-17899, SVY-16194, starttls, content-type header). The wrapper is long-standing, not a recent regression. No blame was run on the target lines.

## 3. Design
### 3.1 Server-side message flattening
Add a private helper in `MailServer`:

```java
private static String getFullMessage(Exception ex)
```

- Start with `ex.getMessage()`.
- Walk the chain: for a `MessagingException` follow `getNextException()`, and for any `Throwable` follow `getCause()`. Use an identity-based visited set (`Collections.newSetFromMap(new IdentityHashMap<>())`) as loop guard, plus a sane depth limit (e.g. 10).
- Append each non-null, non-empty message that is not already contained in the result so far, separated by `"; "` (same separator as the client).
- For `SendFailedException`, optionally append `getInvalidAddresses()` as `"invalid addresses: a, b"` when non-empty.
- If nothing yields a message, fall back to `ex.toString()` so the message is never null.

Both catch blocks then do `throw new Exception(getFullMessage(ex));`, still a plain serializable `Exception`. The existing `Debug.error(..., ex)` logging stays and may use the same full message.

### 3.2 Unchanged
- No new JS API. Client-side `MailProvider` is not modified.
- Mail receiving code and other `catch` blocks are not touched.

### 3.3 Verification (no test infrastructure)
This branch has no test folder or surefire/JUnit setup. Test infrastructure is out of scope (see open questions). Verification:
- Compile check through the Eclipse workspace: `getCompilationErrors` on `com.servoy.extensions`, zero errors.
- Manual check with a fake SMTP server (or a real server) that rejects `RCPT TO` with `550 5.7.1 ...`: call `plugins.mail.sendMail(...)`, confirm it returns false and `getLastSendMailExceptionMsg()` contains the 550 text.
- Also check a plain failure (wrong host/connection refused) still gives a sensible, non-null message.

## 4. Implementation plan
1. In `MailServer.java`, add the private static helper `getFullMessage(Exception)` (Java 17, tabs, `javax.mail.SendFailedException` import, `java.util.*` imports as needed; keep file header untouched).
2. Replace `throw new Exception(ex.getMessage());` in `sendBulkMail` and `sendMail` with `throw new Exception(getFullMessage(ex)); //not all exceptions are serializable`.
3. Run the compile check and fix any errors.
4. Run the manual 550 check above.

## 5. Acceptance criteria
- [ ] A recipient rejected with SMTP 550 yields a `getLastSendMailExceptionMsg()` containing the server's 550 text, not only "Invalid Addresses".
- [ ] Both `sendMail` and `sendBulkMail` behave this way.
- [ ] The thrown exception is still a plain `java.lang.Exception` (serializable).
- [ ] Cyclic cause chains do not loop forever; duplicates are not repeated.
- [ ] No new JS API; `MailProvider` unchanged.
- [ ] Project `com.servoy.extensions` compiles with zero errors.

## 6. Out of scope
- New JS method returning structured error details.
- Changing exception types or preserving causes across RMI.
- Client-side `MailProvider` changes.
- Adding unit-test infrastructure.

## 7. Open questions
| Question | Owner | Status |
|----------|-------|--------|
| Add test infrastructure (test folder, surefire, JUnit Jupiter) to cover `getFullMessage`? Default: no, manual verification only. | User | Open |
| Include invalid addresses for `SendFailedException` in the message? Default: yes, appended when present. | User | Open (assumption made) |
