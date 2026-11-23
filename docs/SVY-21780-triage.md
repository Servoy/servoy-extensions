# Triage Report — SVY-21780

**Verdict:** PROCEED

## Reported problem
`plugins.mail.sendMail` returns false and `plugins.mail.getLastSendMailExceptionMsg()` returns only "Invalid Addresses". The real SMTP reply is `550 5.7.1 Service unavailable, Client host [...] blocked using Spamhaus ...`. The reporter's legacy system showed that real error. They ask for a way to get the full SMTP error from the plugin. They are on 2025.3.4 LTS and moving to 2026.3 LTS.

## Root-cause assessment
This is a real information-loss bug in the plugin. It is not a missing feature.

- `com.servoy.extensions/src/com/servoy/extensions/plugins/mail/MailServer.java:171-193` (`sendMail`) and `:146-169` (`sendBulkMail`) catch every `Exception` from `Transport.send(message)`. They then rethrow `new Exception(ex.getMessage())`, with the comment "not all exceptions are serializable". The cause chain is dropped, and so is JavaMail's `MessagingException.getNextException()` chain.
- When the SMTP server rejects a recipient with a 550, JavaMail throws `SendFailedException`. Its own message is the generic "Invalid Addresses". The real SMTP reply lives in the chained exception, an `SMTPAddressFailedException` reached via `getNextException()`/`getCause()`. It also appears in `getInvalidAddresses()` and `getValidUnsentAddresses()`. This is standard JavaMail behaviour. I did not verify it against the jar in this checkout. It matches the symptom, since the reporter sees exactly "Invalid Addresses".
- `com.servoy.extensions/src/com/servoy/extensions/plugins/mail/client/MailProvider.java:1154-1163` and `:1191-1200` try to append `mex.getCause().getMessage()` to `sendMailException`. By then the exception has been recreated server-side with no cause, so `getCause()` is always null. The client-side code is correct but can never help.
- `js_getLastSendMailExceptionMsg()` (`MailProvider.java:1231-1234`) just returns that string.

## Ticket premise check
- The ticket asks for "a way to get the full error returned from SMTP added to the plugin". A new API is not needed. The existing `getLastSendMailExceptionMsg()` should already contain the detail, and the plugin throws it away. Fixing that is better than adding a second method.
- The problem is in this repo's code. It is not user misconfiguration. The Spamhaus block is on the reporter's side, but the diagnostic loss is ours.
- The same defect hits any `MessagingException` that chains a nested exception, for example auth failures and connection errors. It is not limited to the 550 case.
- The ticket's fix versions are 2026.12, 2026.3.3 LTS and 2025.3.8 LTS, so the fix is expected on this branch too.

## Approaches considered
1. Flatten the exception chain server-side into the message (recommended). In `MailServer.sendMail` and `sendBulkMail`, build the message before throwing the plain `Exception`.
   - Start with `ex.getMessage()`.
   - Walk `MessagingException.getNextException()` and `getCause()`, with a loop guard.
   - Append each distinct non-empty message, separated by "; ", like the client does.
   - For `SendFailedException`, optionally append the invalid addresses.
   - Pros: small change, backwards compatible, still serializable (a plain `Exception`), and fixes both methods. Existing client code keeps working.
   - Cons: the message gets longer, and anyone parsing the old text would see a change (unlikely).
2. Throw an exception that keeps the cause, for example a serializable wrapper.
   - Pros: structured.
   - Cons: the original comment says not all exceptions are serializable, which is risky over RMI. More API surface for no real gain.
3. Add a new JS method such as `getLastSendMailExceptionDetails()` that returns structured data.
   - Pros: matches the literal ask.
   - Cons: new API, and the existing method would still be misleading. Could be a later extension.
4. No code change.
   - Pros: none to speak of.
   - Cons: the reporter cannot diagnose SMTP rejections without access to the server log. `Debug.error` does log the full stack trace server-side, so the info exists there but is not exposed to solution code. Not acceptable for a Major-priority usability bug.

## Recommendation
Approach 1, applied to both `sendMail` and `sendBulkMail` in `MailServer.java`. Extract a small private helper (for example `getFullMessage(Exception)`) so both share it. Optionally leave the client-side cause handling as is. The class has no unit tests on this branch (no test infrastructure), so verify by compile check and manually against an SMTP server that rejects a recipient (or a fake SMTP server returning 550). Check that `getLastSendMailExceptionMsg()` then contains the 550 text.

## Git history findings
- `git log` on `MailServer.java` shows only older, unrelated changes (SVY-17899, SVY-16194, starttls, content-type header). The `throw new Exception(ex.getMessage())` wrapper has been there for a long time and was not introduced by a recent regression. I did not run `git blame` on those lines.
- No existing `docs/` spec for SVY-21780 was found.
- The reporter's "legacy system" shows the real error, consistent with it reading the nested JavaMail exception directly.

## Questions for the reporter
None.
