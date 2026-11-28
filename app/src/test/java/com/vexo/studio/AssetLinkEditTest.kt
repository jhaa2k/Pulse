package com.vexo.studio
import com.vexo.studio.data.ProjectAssets
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class AssetLinkEditTest {
    @Test fun removeOnlySelectedImageReference(){
        val node=LayerNode(LayerType.IMAGE,LayerCommon(name="Test"),LayerConfig.Image(sourceType=ImageSourceType.IMPORTED,customUri="content://old",maskImage="content://mask"))
        val result=ProjectAssets.editLayerAsset(node,"Image","content://old",null,false).config as LayerConfig.Image
        assertNull(result.customUri)
        assertEquals("content://mask",result.maskImage)
        assertEquals(ImageSourceType.INTERNAL,result.sourceType)
    }
    @Test fun staleAndLockedReferencesRemainUnchanged(){
        val node=LayerNode(LayerType.IMAGE,LayerCommon(name="Test"),LayerConfig.Image(sourceType=ImageSourceType.IMPORTED,customUri="content://old"))
        assertEquals(node,ProjectAssets.editLayerAsset(node,"Image","content://other","content://new",true))
        val locked=node.copy(common=node.common.copy(locked=true))
        assertEquals(locked,ProjectAssets.editLayerAsset(locked,"Image","content://old",null,false))
    }
}
