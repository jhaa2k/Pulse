package com.vexo.studio.ui.screens
/** Section index generated from the inspector's control labels. Matching opens whole sections. */
internal object InspectorSearch {
    private val terms=mapOf(
        "General & Transform" to "Visible Blend Mode opacity pos X pos Y Uniform Scale Anchor X Anchor Y Pivot X Pivot Y scale X scale Y rotation Deg position x y scale rotation blend mode opacity",
        "Audio reactive" to "Measure Scale Scale Amount X Scale Trigger Threshold Scale Amount Y Measure Position Position Amount X Position Trigger Threshold Position Amount Y Measure Rotation Rot Amount",
        "Quick layer tools" to "Horizontal anchor Vertical anchor",
        "Layer timing & fades" to "Start End Fade in Fade out start end trim duration fade",
        "Keyframe animation" to "position scale rotation opacity interpolation timeline",
        "Advanced audio bindings" to "Position Provider Scale Provider Rotation Provider Rotation Threshold Opacity Trigger Opacity Provider Opacity Amount Opacity Threshold",
        "Creative spectrum" to "Creative path Creative style Radius Turns Wave spread Steps Style Gallery Shuffle Line Outline Width Fill Opacity Dot Tip Size",
        "Path & geometry" to "Polygon Sides Path Angle Path Shape Circular Path Gap circle Radius",
        "Primary segment" to "Bar Height Multiplier Fixed Height Segment Style Mirror Symmetry Flip Input Alternating Dir Bar Width Mode Bar Count (0 = Provider) Bar Spacing Roundness Spectrum Softness Peak Hold Markers bar Width height Scale min Height Scale max Height Scale",
        "Secondary segment" to "Secondary Creative Style Secondary Segment Segment 2 Width Segment 2 Height Segment 2 Fixed Height Segment 2 Mirror Segment 2 Width Mode Segment 2 Alternating Direction Inherit Primary Direction Segment 2 Color From Segment 2 Color To",
        "Colors" to "Gradient Offset Color From Color To Particle Tint",
        "Audio response" to "Audio Provider Accumulated Delay Reaction Delay",
        "Procedural backgrounds" to "Pattern Pattern dark Pattern light Density Intensity Motion speed",
        "Feathered aperture" to "Mask shape Invert Size Feather Center X Center Y",
        "Creative gradients" to "Gradient Gradient Start Gradient End Gradient Angle Center X Center Y Gradient Scale Gradient Mix",
        "Creative distortion" to "Effect Intensity Frequency / Sectors Animation Speed Effect audio reactivity",
        "Color grading & film" to "Brightness Contrast Temperature Hue Vignette Scanlines Film Grain",
        "Source & fitting" to "Source Source Composition Video source Loop video Video speed Video offset Decode mode Texture quality Video sample FPS Shape Mask Keep Aspect Ratio Crop To Fit Flip Horizontal Flip Vertical",
        "Image animation" to "GIF Speed Image Animation Audio Binding",
        "Mask & chroma key" to "IMPORT MASK Mask Source Mask Mode Color Key Chroma Auto Key (image corner) Key Color Key Strength",
        "Color & opacity" to "Color Blend Image Opacity Strength Saturation Secondary Tint Tint Color",
        "Blur" to "Blurred Border (fit mode) Image Blur Image Blur Radius Blur Divider",
        "Text animation" to "Motion Loop Duration (s) Delay (s) Amount Text beat scale Shadow color",
        "Content & typography" to "font Size Bold Italic Text Alignment Letter Spacing Line Spacing",
        "Color & effects" to "Stroke Width Stroke Color Shadow Radius Text Color",
        "Shape motion" to "Radius Star inner radius Spin (degrees/sec) Radius pulse Stroke pulse",
        "Geometry" to "Shape Kind Corner Radius Sides",
        "Fill & stroke" to "Stroke Width Stroke Color Gradient End Fill Color",
        "Creative emitter" to "Emitter Emitter X Emitter Y Emitter width Emitter height Direction spread Drag Beat burst count Burst threshold Burst provider",
        "Emission" to "Spawn Interval Lifetime Spawn Area count Limit",
        "Motion & forces" to "Speed Random Gravity X Gravity Y Vector Z Side Wave Frequency Side Wave Magnitude Side Wave Frequency Random Side Wave Magnitude Random Random Vector Vector Relative To Center Force Field speed Vector Angle",
        "Size & fading" to "Fade-in Size Start Size End Size Random End Size Size Aspect Ratio Fade In / Out Particle Scale",
        "Perspective" to "Perspective Depth (0 = 2D) Perspective FOV Near Camera Fade",
        "Texture, mirrors & trails" to "IMPORT PARTICLE TEXTURE Sprite Mirror X Mirror Y Velocity Angle Trail Length Random Seed Overall Speed",
        "Source & compositing" to "Source Composition Show Unblurred Content Show Unblurred Under",
        "Blur parameters" to "IMPORT BLUR MASK Blur Audio Binding Glow Blend Glow 1 Scale X Glow 1 Scale Y Glow 1 Color Glow 2 Scale X Glow 2 Scale Y Glow 2 Color Glow 3 Scale X Glow 3 Scale Y Glow 3 Color blur Radius blur Multiplier",
        "Source & blend" to "Source Composition Effect Blend Source Under Source Above Show Source Under Show Source Above Content Blend",
        "Channel offsets" to "RGB Split Audio Binding RGB Multiplier Audio Binding Split Y split Multiplier Split X",
        "Channel colors" to "Green Channel Blue Channel Channel 0",
        "Motion trail" to "Motion Source Relative Motion Motion X Motion Y Trail Persistence Trail Tint Blur Amount",
        "Source" to "Source Composition",
        "Mirror geometry" to "Mirror Mode Mirror Repeats Flip Mirror",
        "Tint" to "Mirror Tint",
        "GENERAL & SPECTRUM" to "Sample Mode FFT Size Window Channel Mode Provider Enabled Pre-smoothing Smooth Frequency Shift sample Out Count output Multiplier Mirror Samples Repeat Samples Edge Gap",
        "HZ RANGE & FILTERING" to "lower Hz higher Hz Hz Linear Factor A-Weighting Filter Radius Filter Strength",
        "RESPONSE & BEAT ENVELOPE" to "attack Ms release Ms FFT hop (samples) Beat Smooth Beat Range Lower Beat Range Higher Peak Hold Peak Decay beat Sensitivity Beat Range First Beat Range Last"
    )
    private fun normalize(s:String)=s.lowercase().replace(Regex("[^a-z0-9]+")," ").trim()
    fun matches(title:String,query:String):Boolean{
        val words=normalize(query).split(' ').filter{it.isNotBlank()}
        val haystack=normalize(title+" "+terms[title].orEmpty())
        return words.all{it in haystack}
    }
}
