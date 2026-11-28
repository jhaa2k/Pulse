package com.vexo.studio
import com.vexo.studio.data.VixoFileName
import org.junit.Assert.*
import org.junit.Test
class VixoFileNameTest{
    @Test fun replacesLegacySuffix(){
        assertEquals("My Project.vixo",VixoFileName.create("My Project.VEX"))
        assertEquals("My Project.vixo",VixoFileName.create("My Project.vixo"))
        assertEquals("My Project.vixo",VixoFileName.create("My Project.vix"))
    }
    @Test fun preservesUnicodeAndRejectsPathSeparators(){
        assertEquals("ସଙ୍ଗୀତ.vixo",VixoFileName.create("ସଙ୍ଗୀତ"))
        assertFalse(VixoFileName.create("../project").contains('/'))
        assertEquals("Vexo Project.vixo",VixoFileName.create(""))
    }
}
