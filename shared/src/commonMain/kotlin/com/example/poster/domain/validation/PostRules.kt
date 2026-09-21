package com.example.poster.domain.validation

/**
 * How long a post may be.
 *
 * One paragraph. Long enough to say what happened and what is being asked for,
 * short enough that a feed stays readable and nobody has to scroll past one
 * person's essay to reach the next request.
 *
 * Here rather than in either end, because the form counting to a different
 * number than the server accepts is a request somebody types out in full and
 * then loses.
 */
object PostRules {
    const val MESSAGE_LIMIT = 500

    /**
     * The title is a line, not a paragraph — a card gives it one. Kept short so a
     * long title cannot push the card's layout around; the label calls it "a short
     * title" and this holds people to it.
     */
    const val TITLE_LIMIT = 60

    fun messageTooLong(message: String): Boolean = message.length > MESSAGE_LIMIT

    fun titleTooLong(title: String): Boolean = title.length > TITLE_LIMIT
}
