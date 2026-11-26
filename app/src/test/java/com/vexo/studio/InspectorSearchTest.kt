package com.vexo.studio
import com.vexo.studio.ui.screens.InspectorSearch
import org.junit.Assert.*
import org.junit.Test
class InspectorSearchTest{
 @Test fun matchesSectionAndProperty(){
   assertTrue(InspectorSearch.matches("General & Transform","rotation"))
   assertTrue(InspectorSearch.matches("Layer timing & fades","fade out"))
   assertFalse(InspectorSearch.matches("General & Transform","particle texture"))
   assertTrue(InspectorSearch.matches("GENERAL & SPECTRUM","FFT size"))
 }
}
