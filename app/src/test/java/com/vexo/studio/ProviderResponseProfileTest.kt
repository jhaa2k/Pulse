package com.vexo.studio
import com.vexo.studio.engine.scene.ProviderResponseProfile
import com.vexo.studio.model.AudioProviderConfig
import org.junit.Assert.*
import org.junit.Test
class ProviderResponseProfileTest {
    @Test fun profilesPreserveIdentityAndRouting(){
        val base=AudioProviderConfig(id="provider-test",index=7,tag="Custom",enabled=false,mirrorSamples=true,repeatSamples=2)
        ProviderResponseProfile.entries.forEach{profile->
            val next=profile.applyTo(base)
            assertEquals(base.id,next.id)
            assertEquals(base.index,next.index)
            assertEquals(base.tag,next.tag)
            assertEquals(base.enabled,next.enabled)
            assertEquals(base.mirrorSamples,next.mirrorSamples)
            assertEquals(base.repeatSamples,next.repeatSamples)
            assertTrue(next.lowerHz<next.higherHz)
            assertTrue(next.hopSize<=next.fftSize)
        }
    }
}
