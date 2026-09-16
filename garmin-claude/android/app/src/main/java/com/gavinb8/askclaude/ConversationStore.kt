package com.gavinb8.askclaude

/**
 * The Anthropic Messages API is stateless -- every call needs the full
 * message history. The watch only ever sends the latest prompt plus a
 * conversationId, so this in-memory store (phone has real storage/RAM,
 * unlike the watch) keeps the running transcript per conversation and
 * trims it so token usage/latency stay bounded.
 */
class ConversationStore {

    data class Turn(val role: String, val text: String)

    private val conversations = LinkedHashMap<String, MutableList<Turn>>()

    @Synchronized
    fun historyFor(conversationId: String): List<Turn> =
        conversations[conversationId]?.toList() ?: emptyList()

    @Synchronized
    fun append(conversationId: String, role: String, text: String) {
        val turns = conversations.getOrPut(conversationId) { mutableListOf() }
        turns.add(Turn(role, text))
        while (turns.size > MAX_TURNS) {
            turns.removeAt(0)
        }
        // Keep memory bounded across many distinct conversations too.
        if (conversations.size > MAX_CONVERSATIONS) {
            val oldestKey = conversations.keys.first()
            conversations.remove(oldestKey)
        }
    }

    companion object {
        private const val MAX_TURNS = 20
        private const val MAX_CONVERSATIONS = 10
    }
}
