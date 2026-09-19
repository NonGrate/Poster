# Security

## Reporting a vulnerability

Please do not open a public issue for a security problem. Email the maintainer
(the address in `LICENSE`/`README.md` of your fork; for the template itself, open
a private security advisory on GitHub: Security → Advisories → Report a
vulnerability). Say what you found, how to reproduce it, and what you think the
impact is. You will get an acknowledgement within a few days.

## What is in scope

The server (`server/`), the shared client code (`shared/`), the apps, the admin
panel, the deploy pipeline. Typical things worth reporting: an authorisation
check that trusts a body field, a way to read a post you should not see, a way
to spend somebody else's invite or token, a route that leaks whether an email
address has an account, an injection in the admin panel's HTML.

## Design notes for reviewers

- Authorisation always derives from the JWT subject, never from ids in the body.
- `POST /accounts` applies an allow-list; `role` and `status` are never client-writable.
- Reading a post by id obeys the feed's visibility rules; not-visible is 404.
- Share tokens and invite codes are `SecureRandom` and unrelated to any id.
- Passwords: Argon2id. Login/reset attempts are throttled per address.
- `/debug/fixtures/*` exists only in development mode and the image disables it.
- The admin panel uses a signed session cookie (`SameSite=Strict`, `HttpOnly`,
  `Secure` unless explicitly relaxed) and a per-session CSRF token on every form.
- Email and invitation routes answer identically whether or not an address has
  an account, so they cannot be used to enumerate users.

See `docs/Server.md` → "Security decisions worth knowing before you change things".
