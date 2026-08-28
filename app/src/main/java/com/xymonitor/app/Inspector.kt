package com.xymonitor.app

data class InspectOutcome(
    val ok: Boolean,
    val baseline: Boolean,
    val itemIds: List<String>,
    val newIds: List<String>,
    val knownCount: Int,
    val error: String?,
)

object Inspector {
    fun compare(known: Set<String>, current: List<String>): InspectOutcome {
        if (known.isEmpty()) {
            return InspectOutcome(
                ok = true,
                baseline = true,
                itemIds = current,
                newIds = emptyList(),
                knownCount = current.toSet().size,
                error = null,
            )
        }
        val newIds = current.filter { it.isNotBlank() && it !in known }.distinct()
        return InspectOutcome(
            ok = true,
            baseline = false,
            itemIds = current,
            newIds = newIds,
            knownCount = known.size + newIds.size,
            error = null,
        )
    }

    fun fail(message: String): InspectOutcome {
        return InspectOutcome(
            ok = false,
            baseline = false,
            itemIds = emptyList(),
            newIds = emptyList(),
            knownCount = -1,
            error = message,
        )
    }
}
