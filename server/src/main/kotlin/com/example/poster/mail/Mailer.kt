package com.example.poster.mail

/**
 * Sending one message.
 *
 * Deliberately one method and no provider in the signature. Everything this app
 * sends is a short note to one person about their own account, and the day that
 * stops being true is the day this interface should grow — not before.
 *
 * Failure is a returned value rather than an exception. Every caller is in the
 * middle of something more important than the email: registering somebody, or
 * answering a reset request. None of them should fall over because a third
 * party is having a bad afternoon, and none should tell the person at the other
 * end whether the address exists.
 */
interface Mailer {
    suspend fun send(to: String, subject: String, body: String): Boolean
}

/**
 * What runs when nothing is configured: development, tests, and any deployment
 * where the key has not been set.
 *
 * It prints instead of sending. The alternative — refusing to start without a
 * mail provider — would make the server undeployable for the family test it is
 * for, where verification is a nicety and the admin panel is the real everyday
 * story.
 */
class LoggingMailer(private val log: (String) -> Unit = ::println) : Mailer {
    override suspend fun send(to: String, subject: String, body: String): Boolean {
        log("mail (not sent, no provider configured): to=$to subject=$subject\n$body")
        return true
    }
}
