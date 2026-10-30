package com.zeroflux.cprcoach.engine

/** What is wrong with the rescuer's position. Keys are in assets/strings/<language>.json. */
enum class PostureIssue(val key: String) {
    /** The view can't support a judgement, so nothing else may be said until this is fixed. */
    NOT_SIDE_ON("posture_turn_phone"),
    ELBOWS_BENT("posture_straighten_arms"),
    SHOULDERS_NOT_OVER_HANDS("posture_shoulders_over_hands"),
}

/** One judgement of the rescuer's position. Say less when unsure: a null verdict means stay silent. */
data class PostureReading(
    /** True for good, false for a problem named by [issue], null when not sure enough to say. */
    val good: Boolean?,
    val issue: PostureIssue?,
    /** Shown on the overlay only while the view is trustworthy. */
    val elbowDegrees: Double?,
    /** Horizontal shoulder-to-wrist offset over arm length, signed. Null when not measured. */
    val shoulderOverWrist: Double?,
)
