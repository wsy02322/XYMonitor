package com.xymonitor.app

data class InspectOutcome(
    val ok: Boolean,
    val baseline: Boolean,
    val changed: Boolean,
    val firstId: String,
    val error: String?,
)

object Inspector {
    fun compare(previousFirstId: String, currentFirstId: String): InspectOutcome {
        if (currentFirstId.isBlank()) {
            return fail("第一页没有商品")
        }
        if (previousFirstId.isBlank()) {
            return InspectOutcome(
                ok = true,
                baseline = true,
                changed = false,
                firstId = currentFirstId,
                error = null,
            )
        }
        val changed = previousFirstId != currentFirstId
        return InspectOutcome(
            ok = true,
            baseline = false,
            changed = changed,
            firstId = currentFirstId,
            error = null,
        )
    }

    fun fail(message: String): InspectOutcome {
        return InspectOutcome(
            ok = false,
            baseline = false,
            changed = false,
            firstId = "",
            error = message,
        )
    }
}
