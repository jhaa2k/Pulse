package com.vexo.studio.engine.scene

import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.model.ForceFieldType
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.SpawnAreaType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Pooled zero-allocation particle simulation engine with deterministic seed reset,
 * bounded catch-up fixed-step integration, perspective projection, vortex force field,
 * and beat speed modulation.
 */
class ParticleSimulator(maxPool: Int = 3000) {
    val capacity = maxPool.coerceIn(64, 5000)

    private val active = BooleanArray(capacity)
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val z = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val vz = FloatArray(capacity)
    private val age = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val sizeRand = FloatArray(capacity)
    private val phase = FloatArray(capacity)

    private var spawnAccumulator = 0f
    private var rng = Random(42)
    private var lastSeed = 42

    // Output snapshot arrays (normalized screen coords & size/alpha)
    var activeCount = 0
        private set
    val outX = FloatArray(capacity)
    val outY = FloatArray(capacity)
    val outSize = FloatArray(capacity)
    val outAlpha = FloatArray(capacity)
    val outProgress = FloatArray(capacity)

    fun reset(seed: Int) {
        active.fill(false)
        spawnAccumulator = 0f
        activeCount = 0
        lastSeed = seed
        rng = Random(seed)
    }

    fun step(
        cfg: LayerConfig.Particles,
        snapshot: AudioProviderSnapshot?,
        deltaSec: Float,
        totalTimeSec: Float,
        playingTimeSec: Float,
        isPlaying: Boolean
    ) {
        if (cfg.seed != lastSeed) {
            reset(cfg.seed)
        }

        val cappedDt = deltaSec.coerceIn(0.001f, 0.05f)
        val limit = cfg.countLimit.coerceIn(1, capacity)
        val speedFactor = 1f + MeasureEvaluator.evaluateScalar(
            measure = cfg.measureOverallSpeed,
            snapshot = snapshot,
            totalTimeSec = totalTimeSec,
            playingTimeSec = playingTimeSec,
            trackPositionNorm = 0f,
            isPlaying = isPlaying
        ) * 2.5f

        val spawnInterval = cfg.spawnTimeSec.coerceIn(0.004f, 0.25f)
        spawnAccumulator += cappedDt

        var spawnsRemaining = (spawnAccumulator / spawnInterval).toInt().coerceAtMost(24)
        spawnAccumulator -= spawnsRemaining * spawnInterval

        val angleRad = Math.toRadians(cfg.vectorAngleDeg.toDouble()).toFloat()
        val fovScale = 1f / tan(Math.toRadians((cfg.perspectiveFov.coerceIn(20f, 150f) * 0.5).toDouble())).toFloat()
        val depth = cfg.perspectiveDepth.coerceIn(100f, 2000f)

        var currentCount = 0
        for (i in 0 until limit) {
            if (!active[i] && spawnsRemaining > 0) {
                spawnParticle(i, cfg, angleRad)
                spawnsRemaining--
            }
            if (!active[i]) continue

            age[i] += cappedDt
            if (age[i] >= life[i]) {
                active[i] = false
                continue
            }

            // Force field & gravity
            var ax = cfg.gravityX * 0.0015f
            var ay = cfg.gravityY * 0.0015f
            if (cfg.forceField == ForceFieldType.VORTEX) {
                val dx = x[i] - 0.5f
                val dy = y[i] - 0.5f
                val dist = hypot(dx, dy).coerceAtLeast(0.04f)
                ax += (-dy / dist) * 0.45f - dx * 0.12f
                ay += (dx / dist) * 0.45f - dy * 0.12f
            }

            vx[i] += ax * cappedDt
            vy[i] += ay * cappedDt

            val sineOffset = if (cfg.sideSineWaveMag != 0f) {
                sin(age[i] * cfg.sideSineWaveFreq + phase[i]) * cfg.sideSineWaveMag * 0.01f * cappedDt
            } else 0f

            x[i] += vx[i] * cappedDt * speedFactor + sineOffset
            y[i] += vy[i] * cappedDt * speedFactor
            z[i] += vz[i] * cappedDt * speedFactor

            val p = (age[i] / life[i]).coerceIn(0f, 1f)
            val zCoord = (depth + z[i]).coerceAtLeast(10f)
            if (zCoord < cfg.nearCameraFadeOutDistance) {
                continue
            }

            val persp = (depth / zCoord) * fovScale * 0.55f
            val sx = 0.5f + (x[i] - 0.5f) * persp
            val sy = 0.5f + (y[i] - 0.5f) * persp

            val fadeTime = cfg.fadeInAndOutTime.coerceIn(0.02f, 0.49f)
            val alphaEnv = when {
                p < fadeTime -> p / fadeTime
                p > 1f - fadeTime -> (1f - p) / fadeTime
                else -> 1f
            }.coerceIn(0f, 1f)

            val sz = (cfg.startSize + (cfg.endSize + sizeRand[i] - cfg.startSize) * p) *
                cfg.particleScale * persp * 0.012f

            outX[currentCount] = sx
            outY[currentCount] = sy
            outSize[currentCount] = sz.coerceIn(0.002f, 0.25f)
            outAlpha[currentCount] = alphaEnv
            outProgress[currentCount] = p
            currentCount++
        }
        activeCount = currentCount
    }

    private fun spawnParticle(i: Int, cfg: LayerConfig.Particles, angleRad: Float) {
        active[i] = true
        age[i] = 0f
        life[i] = (cfg.lifetimeSec * (0.7f + rng.nextFloat() * 0.6f)).coerceIn(0.2f, 15f)
        sizeRand[i] = rng.nextFloat() * cfg.endSizeRandom
        phase[i] = rng.nextFloat() * (2f * PI.toFloat())

        when (cfg.spawnArea) {
            SpawnAreaType.HORIZONTAL_LINE -> {
                x[i] = rng.nextFloat()
                y[i] = 0.5f
            }
            SpawnAreaType.RECTANGLE -> {
                x[i] = rng.nextFloat()
                y[i] = rng.nextFloat()
            }
        }
        z[i] = (rng.nextFloat() - 0.5f) * 350f

        val dir = if (cfg.useRandomVectorInstead) {
            rng.nextFloat() * 2f * PI.toFloat()
        } else if (cfg.vectorRelativeToCenter) {
            kotlin.math.atan2(y[i] - 0.5f, x[i] - 0.5f)
        } else {
            angleRad + (rng.nextFloat() - 0.5f) * 0.65f
        }

        val spd = (cfg.speed + (rng.nextFloat() - 0.5f) * cfg.speedRandom) * 0.0018f
        vx[i] = cos(dir) * spd
        vy[i] = sin(dir) * spd
        vz[i] = (rng.nextFloat() - 0.5f) * spd * 140f * cfg.vectorAngleZ
    }
}
