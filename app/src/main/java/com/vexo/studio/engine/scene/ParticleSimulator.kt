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

    private val emission=ParticleEmissionClock()
    private val burstGate=ParticleBurstGate()
    private var stepAccumulator=0.0
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
    val outVx = FloatArray(capacity)
    val outVy = FloatArray(capacity)

    fun reset(seed: Int) {
        active.fill(false)
        emission.reset()
        burstGate.reset()
        stepAccumulator=0.0
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
        isPlaying: Boolean,
        burstSnapshot: AudioProviderSnapshot? = snapshot,
        trackPositionNorm: Float = 0f,
        speedOverride:Float?=null
    ) {
        if (cfg.seed != lastSeed) reset(cfg.seed)
        val dt=deltaSec.takeIf{it.isFinite()}?.coerceIn(0f,.1f)?:0f
        stepAccumulator+=dt.toDouble()
        val fixed=1.0/120.0
        val steps=((stepAccumulator+1e-8)/fixed).toInt().coerceAtMost(12)
        for(i in 0 until steps){
            integrate(cfg,snapshot,fixed.toFloat(),totalTimeSec-(steps-1-i)*fixed.toFloat(),playingTimeSec,isPlaying,burstSnapshot,trackPositionNorm,speedOverride)
        }
        stepAccumulator=(stepAccumulator-steps*fixed).coerceIn(0.0,fixed)
    }

    private fun integrate(
        cfg:LayerConfig.Particles,snapshot:AudioProviderSnapshot?,cappedDt:Float,
        totalTimeSec:Float,playingTimeSec:Float,isPlaying:Boolean,
        burstSnapshot:AudioProviderSnapshot?,trackPositionNorm:Float,speedOverride:Float?
    ){
        val limit = cfg.countLimit.coerceIn(1, capacity)
        val speedFactor = (1f + (speedOverride ?: MeasureEvaluator.evaluateScalar(
            measure = cfg.measureOverallSpeed,
            snapshot = snapshot,
            totalTimeSec = totalTimeSec,
            playingTimeSec = playingTimeSec,
            trackPositionNorm = trackPositionNorm,
            isPlaying = isPlaying
        )) * 2.5f).coerceIn(-20f,20f)

        val energy=ParticleBurstGate.signal(cfg.burstSource,burstSnapshot)
        val rate=1f+cfg.reactiveEmission.coerceIn(0f,5f)*energy
        val spawnInterval = (cfg.spawnTimeSec.coerceIn(0.004f, 0.25f)/rate).coerceAtLeast(.001f)
        var spawnsRemaining = emission.advance(cappedDt,spawnInterval)
        var burstRemaining=if(burstGate.update(energy,cfg.burstThreshold,cfg.burstCooldownMs,cappedDt,isPlaying))cfg.burstCount.coerceIn(0,256)else 0
        for(i in limit until capacity)active[i]=false

        val angleRad = Math.toRadians(cfg.vectorAngleDeg.toDouble()).toFloat()
        val fovScale = 1f / tan(Math.toRadians((cfg.perspectiveFov.coerceIn(20f, 150f) * 0.5).toDouble())).toFloat()
        val depth = cfg.perspectiveDepth.coerceIn(0f, 2000f)

        val damping=kotlin.math.exp(-cfg.drag.coerceIn(0f,10f)*cappedDt)
        val centerForce=cfg.centerForce.coerceIn(-3f,3f)
        val turbulence=cfg.turbulence.coerceIn(0f,3f)
        var currentCount = 0
        for (i in 0 until limit) {
            if (!active[i] && (burstRemaining>0||spawnsRemaining>0)) {
                val burst=burstRemaining>0
                spawnParticle(i,cfg,angleRad,if(burst)cfg.burstSpeedMultiplier.coerceIn(0f,5f)else 1f)
                if(burst)burstRemaining-- else spawnsRemaining--
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

            ax+=(cfg.emitterX-x[i])*centerForce
            ay+=(cfg.emitterY-y[i])*centerForce
            if(turbulence>0f){
                ax+=sin(age[i]*7f+phase[i]+y[i]*9f)*turbulence*.3f
                ay+=cos(age[i]*5f+phase[i]+x[i]*8f)*turbulence*.3f
            }
            vx[i] = (vx[i]+ax*cappedDt)*damping
            vy[i] = (vy[i]+ay*cappedDt)*damping

            val sineOffset = if (cfg.sideSineWaveMag != 0f) {
                sin(age[i] * cfg.sideSineWaveFreq + phase[i]) * cfg.sideSineWaveMag * 0.01f * cappedDt
            } else 0f

            val oldX=x[i];val oldY=y[i];val oldZ=z[i]
            x[i] += vx[i] * cappedDt * speedFactor + sineOffset
            y[i] += vy[i] * cappedDt * speedFactor
            z[i] += vz[i] * cappedDt * speedFactor

            val p = (age[i] / life[i]).coerceIn(0f, 1f)
            val zCoord = (depth + z[i]).coerceAtLeast(10f)
            val near=cfg.nearCameraFadeOutDistance.coerceAtLeast(0f)
            val nearAlpha=if(depth>0f&&near>0f)((zCoord-near*.5f)/(near*.5f)).coerceIn(0f,1f)else 1f
            if(nearAlpha<=0f)continue

            val persp = if(depth<=0f)1f else (depth / zCoord) * fovScale * 0.55f
            val sx = 0.5f + (x[i] - 0.5f) * persp
            val sy = 0.5f + (y[i] - 0.5f) * persp

            val fadeTime = cfg.fadeInAndOutTime.coerceIn(0.02f, 0.49f)
            val alphaEnv = when {
                p < fadeTime -> p / fadeTime
                p > 1f - fadeTime -> (1f - p) / fadeTime
                else -> 1f
            }.coerceIn(0f, 1f)

            val size = if(p<fadeTime) cfg.startSize+(cfg.fadeInSize-cfg.startSize)*(p/fadeTime) else cfg.fadeInSize+(cfg.endSize+sizeRand[i]-cfg.fadeInSize)*((p-fadeTime)/(1f-fadeTime))
            val sz = size * cfg.particleScale * persp * 0.012f

            outX[currentCount] = sx
            outY[currentCount] = sy
            outSize[currentCount] = sz.coerceIn(0f, 0.25f)
            outAlpha[currentCount] = alphaEnv*nearAlpha
            outProgress[currentCount] = p
            // Actual screen-space velocity includes audio speed, depth and side-wave motion.
            val oldPersp=if(depth<=0f)1f else (depth/(depth+oldZ).coerceAtLeast(10f))*fovScale*.55f
            outVx[currentCount]=(sx-(.5f+(oldX-.5f)*oldPersp))/cappedDt
            outVy[currentCount]=(sy-(.5f+(oldY-.5f)*oldPersp))/cappedDt
            currentCount++
        }
        activeCount = currentCount
    }

    private fun spawnParticle(i: Int, cfg: LayerConfig.Particles, angleRad: Float, speedMultiplier:Float) {
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
        if(cfg.emitter!=com.vexo.studio.model.ParticleEmitter.LEGACY){
            val pos=CreativeGeometry.emitter(cfg.emitter,rng.nextFloat(),rng.nextFloat(),cfg.emitterX,cfg.emitterY,cfg.emitterWidth,cfg.emitterHeight)
            x[i]=pos.x;y[i]=pos.y
        }
        z[i] = (rng.nextFloat() - 0.5f) * 350f

        val dir = if (cfg.useRandomVectorInstead) {
            rng.nextFloat() * 2f * PI.toFloat()
        } else if (cfg.vectorRelativeToCenter) {
            kotlin.math.atan2(y[i] - cfg.emitterY, x[i] - cfg.emitterX)
        } else {
            angleRad + (rng.nextFloat() - 0.5f) * cfg.directionSpread.coerceIn(0f,360f)*PI.toFloat()/180f
        }

        val spd = (cfg.speed + (rng.nextFloat() - 0.5f) * cfg.speedRandom) * 0.0018f * speedMultiplier
        vx[i] = cos(dir) * spd
        vy[i] = sin(dir) * spd
        vz[i] = (rng.nextFloat() - 0.5f) * spd * 140f * cfg.vectorAngleZ
    }
}
