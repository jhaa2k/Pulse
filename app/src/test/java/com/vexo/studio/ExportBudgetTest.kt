package com.vexo.studio
import com.vexo.studio.export.ExportBudget
import org.junit.Assert.*
import org.junit.Test
class ExportBudgetTest{
 @Test fun estimate(){assertEquals(91500000L,ExportBudget.encodedBytes(60000000L,12000000,200000))}
 @Test fun workingIncludesPcm(){assertTrue(ExportBudget.workingBytes(60000000L,12000000,200000,44100,2)>91500000L+10584000L)}
}
