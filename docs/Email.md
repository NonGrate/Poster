# Email

The server sends three kinds of message and receives none: address
verification, password reset, group invitation. It calls a provider's HTTPS
API — no SMTP, no mail server, no port 25 for a cloud host to block.

Until configured, every message is printed to the server log:

```
mail (not sent, no provider configured): to=… subject=…
```

which is enough for development: copy the link out of the log.

## Setting up Resend

1. Account at resend.com → **Domains → Add** your domain.
2. Add the DNS records it shows (one DKIM TXT, an SPF TXT and an MX on a
   `send.` subdomain). If your DNS is Cloudflare, Resend's *Auto configure*
   writes them for you. Common failures are silent: the DKIM value pasted with
   a line break, the domain typed twice into a Cloudflare Name field, a CNAME
   left proxied. Check with `dig +short TXT resend._domainkey.<domain>`.
3. Add a DMARC record at `_dmarc.<domain>`: start with `v=DMARC1; p=none; rua=mailto:dmarc@<domain>`
   (Cloudflare's DMARC Management does this and collects the reports).
4. **API Keys → Create**, permission *Sending access*, restricted to the domain.
5. On the server:
   ```bash
   POSTER_RESEND_API_KEY=re_…
   POSTER_MAIL_FROM="Poster <no-reply@your.domain>"
   ```
   The startup log stops saying `mail: no POSTER_RESEND_API_KEY …`.

Test end to end from the app (register a new account) or straight at the API:

```bash
curl -s -X POST https://api.resend.com/emails \
  -H "Authorization: Bearer $POSTER_RESEND_API_KEY" -H 'Content-Type: application/json' \
  -d '{"from":"Poster <no-reply@your.domain>","to":["you@example.com"],"subject":"Test","text":"Hello"}'
```

## Rate limits

`AccountMail` sends at most one verification and one reset per address per two
minutes; `GroupMail` also caps each sender at 20 invitations a day. Both are
in memory — right for one server instance; if you ever run several, move them to
the database.

## Changing provider

`Mailer` has one method: `send(to, subject, body): Boolean`. `ResendMailer` is
the only implementation that sends; `ResendMailer.fromEnvironment()` in
`Application.module` is the one place it is chosen. Postmark, SES, Mailgun would
be a class of the same shape and a different `fromEnvironment`.

## What it does not do

No queue, no retry (a failed send is logged and the person can press the button
again), no bounce handling, no incoming mail. `no-reply@` is not a mailbox.

## Where the links go

`https://<POSTER_BASE_URL>/verify?token=…`, `/reset?token=…`, `/join/CODE` —
pages served by the backend, each offering the `poster://` deep link too.
Opening a page never spends a token; the button on it does.
