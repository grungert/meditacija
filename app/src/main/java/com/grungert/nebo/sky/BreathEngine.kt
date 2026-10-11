package com.grungert.nebo.sky

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

enum class Phase(val label: String) { IN("Udah"), HOLD("Zadrži"), OUT("Izdah"), REST("Zadrži") }

data class BreathPattern(val id: String, val ratio: String, val name: String, val phases: List<Pair<Phase, Float>>)

val PATTERNS = listOf(
    BreathPattern("46", "4–6", "smirenje", listOf(Phase.IN to 4f, Phase.OUT to 6f)),
    BreathPattern("55", "5,5–5,5", "koherentno", listOf(Phase.IN to 5.5f, Phase.OUT to 5.5f)),
    BreathPattern("478", "4–7–8", "za san", listOf(Phase.IN to 4f, Phase.HOLD to 7f, Phase.OUT to 8f)),
    BreathPattern("box", "4–4–4–4", "kvadrat", listOf(Phase.IN to 4f, Phase.HOLD to 4f, Phase.OUT to 4f, Phase.REST to 4f)),
)

fun patternById(id: String) = PATTERNS.firstOrNull { it.id == id } ?: PATTERNS[0]

/**
 * Everything that moves with the breath: the breath itself, the wind (which stills during holds),
 * the cloud drift, the gust phase shared by the grass, and the walk that steps on the out-breath.
 */
class BreathEngine(var pattern: BreathPattern = PATTERNS[0]) {
    var paced = true          // false for sessions without a breathing guide (thought clouds, silence)
    var running = true
    var walkOn = false
    var reducedMotion = false

    var phaseIdx = 0; private set
    var phaseT = 0f; private set
    var breath = 0f; private set
    var wind = 1f; private set
    var drift = 0f; private set
    var morph = 0f; private set
    var windPhase = 0f; private set
    var walk = 0f; private set
    var walkV = 0f; private set
    var breaths = 1; private set
    var onPhaseChange: ((Phase) -> Unit)? = null

    val phase: Phase get() = pattern.phases[phaseIdx].first
    val phaseDuration: Float get() = pattern.phases[phaseIdx].second
    val progress: Float get() = phaseT / phaseDuration

    fun setPatternAndRestart(p: BreathPattern) {
        pattern = p; phaseIdx = 0; phaseT = 0f
        onPhaseChange?.invoke(phase)
    }

    /** Advances by dt seconds and returns how far the walk moved (for spawning things along the way). */
    fun step(dt: Float): Float {
        if (running && paced) {
            phaseT += dt
            while (phaseT >= phaseDuration) {
                phaseT -= phaseDuration
                phaseIdx = (phaseIdx + 1) % pattern.phases.size
                if (phase == Phase.IN) breaths++
                onPhaseChange?.invoke(phase)
            }
        }
        val x = progress
        var target = when (phase) {
            Phase.IN -> ease(x)
            Phase.OUT -> 1f - ease(x)
            Phase.HOLD -> 1f
            Phase.REST -> 0f
        }
        if (!running || !paced) target = 0.35f + if (!paced) 0.15f * kotlin.math.sin(drift * 30f) else 0f
        breath += (target - breath) * min(1f, dt * if (running && paced) 20f else 1.5f)

        val still = running && paced && (phase == Phase.HOLD || phase == Phase.REST)
        wind += ((if (still) 0.22f else 1f) - wind) * min(1f, dt * 1.6f)
        val m = if (reducedMotion) 0.4f else 1f
        drift += 0.018f * wind * m * dt
        morph += 0.020f * wind * m * dt
        windPhase += 1.1f * wind * m * dt

        // walking follows the breath: steps on the out-breath, a near-stop on the in-breath, still during holds
        val walkTarget = if (!walkOn || !running) 0f else if (!paced) 0.6f else when (phase) {
            Phase.OUT -> 1f
            Phase.IN -> 0.18f
            else -> 0f
        }
        walkV += (walkTarget - walkV) * min(1f, dt * 1.2f)
        val dWalk = walkV * WALK_SPEED * m * dt
        walk += dWalk
        drift += dWalk * 0.08f
        return dWalk
    }

    private fun ease(x: Float) = 0.5f - 0.5f * cos(PI.toFloat() * x.coerceIn(0f, 1f))

    companion object {
        /** Screen heights per second for the nearest grass while breathing out. */
        const val WALK_SPEED = 0.05f
    }
}
