package com.vexo.studio

import com.vexo.studio.export.ExportEncoderPolicy
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase69EncoderPolicyTest {
    @Test fun automaticScalesWithResolutionAndFps(){
        val s=ExportSettings(adaptiveVideoBitrate=true)
        val hd=ExportEncoderPolicy.targetBitrate(s.copy(resolution=VideoResolution.HD_720))
        val fhd=ExportEncoderPolicy.targetBitrate(s)
        val uhd=ExportEncoderPolicy.targetBitrate(s.copy(resolution=VideoResolution.UHD_4K,fps=60))
        assertTrue(hd<fhd&&fhd<uhd)
        assertEquals(fhd,ExportEncoderPolicy.targetBitrate(s.copy(aspectRatio=AspectRatioPreset.RATIO_9_16)))
    }
    @Test fun exactManualBitrateIsNeverSilentlyChanged(){
        assertEquals(12_000_000,ExportEncoderPolicy.targetBitrate(ExportSettings(videoBitrateMbps=12)))
        assertNull(ExportEncoderPolicy.fitBitrate(12_000_000,2_000_000,8_000_000,false))
        assertEquals(8_000_000,ExportEncoderPolicy.fitBitrate(12_000_000,2_000_000,8_000_000,true))
        assertNull(ExportEncoderPolicy.fitBitrate(12_000_000,81_000_000,100_000_000,true))
    }
    @Test fun automaticSettingIsPersistedAndOptIn(){
        assertFalse(ExportSettings().adaptiveVideoBitrate)
        val p=VexoProject(exportSettings=ExportSettings(adaptiveVideoBitrate=true))
        assertTrue(NativeVexCodec.decode(NativeVexCodec.encode(p)).exportSettings.adaptiveVideoBitrate)
    }
}
