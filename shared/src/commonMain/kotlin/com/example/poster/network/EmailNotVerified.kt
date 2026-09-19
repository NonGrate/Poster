package com.example.poster.network

/**
 * The server declined to write because the address has not been confirmed.
 *
 * A distinct type rather than a message, because the app does something
 * different with it: this is the one refusal that has a way out — a screen
 * offering to send the email again — rather than something to apologise for.
 */
class EmailNotVerified : Exception("Confirm your email address before sharing a post")
