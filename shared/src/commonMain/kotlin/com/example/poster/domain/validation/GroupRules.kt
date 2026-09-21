package com.example.poster.domain.validation

/**
 * How a group may be named.
 *
 * Short, because the name rides in a pill on every post card shared with the
 * group; a long one would stretch the pill and push the card's layout around.
 * Shared so the field that caps it and the server that rejects it agree — a name
 * accepted by one and refused by the other is a create that fails for no reason
 * the person can see.
 */
object GroupRules {
    const val NAME_LIMIT = 30

    fun nameTooLong(name: String): Boolean = name.length > NAME_LIMIT
}
