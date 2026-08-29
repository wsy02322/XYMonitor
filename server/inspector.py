from dataclasses import dataclass


@dataclass(frozen=True)
class InspectOutcome:
    ok: bool
    baseline: bool
    changed: bool
    first_id: str
    error: str | None


def compare(previous_first_id: str, current_first_id: str) -> InspectOutcome:
    if not current_first_id:
        return fail("第一页没有商品")
    if not previous_first_id:
        return InspectOutcome(
            ok=True,
            baseline=True,
            changed=False,
            first_id=current_first_id,
            error=None,
        )
    return InspectOutcome(
        ok=True,
        baseline=False,
        changed=previous_first_id != current_first_id,
        first_id=current_first_id,
        error=None,
    )


def fail(message: str) -> InspectOutcome:
    return InspectOutcome(
        ok=False,
        baseline=False,
        changed=False,
        first_id="",
        error=message,
    )
