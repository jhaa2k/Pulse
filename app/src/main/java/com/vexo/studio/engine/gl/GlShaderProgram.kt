package com.vexo.studio.engine.gl

import android.opengl.GLES20

/**
 * Compiles and caches GLSL ES 2.0 shader programs used across both live preview
 * and offline EGL MediaCodec export.
 */
class GlShaderProgram(vertexSrc: String, fragmentSrc: String) {
    val programId: Int

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            error("GLSL link failed: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        programId = id
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    private val attributes = HashMap<String,Int>()
    private val uniforms = HashMap<String,Int>()
    fun attrib(name: String): Int = attributes.getOrPut(name) { GLES20.glGetAttribLocation(programId, name) }

    fun uniform(name: String): Int = uniforms.getOrPut(name) { GLES20.glGetUniformLocation(programId, name) }

    fun release() {
        GLES20.glDeleteProgram(programId)
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("GLSL compile failed: $log")
        }
        return shader
    }
}

object VexoShaders {
    const val COLOR_VERTEX = """
        attribute vec2 aPos;
        attribute vec4 aColor;
        uniform mat4 uMvp;
        varying vec4 vColor;
        void main() {
            vColor = aColor;
            gl_Position = uMvp * vec4(aPos, 0.0, 1.0);
        }
    """

    const val COLOR_FRAGMENT = """
        precision mediump float;
        varying vec4 vColor;
        uniform float uOpacity;
        void main() {
            float a = vColor.a * uOpacity;
            gl_FragColor = vec4(vColor.rgb * a, a);
        }
    """

    const val TEXTURE_VERTEX = """
        attribute vec2 aPos;
        attribute vec2 aUv;
        uniform mat4 uMvp;
        varying vec2 vUv;
        void main() {
            vUv = aUv;
            gl_Position = uMvp * vec4(aPos, 0.0, 1.0);
        }
    """

    const val IMAGE_FRAGMENT = """
        precision mediump float;
        varying vec2 vUv;
        uniform sampler2D uTex;
        uniform vec4 uTint;
        uniform float uSaturation;
        uniform int uShape;
        uniform int uColorKeyEnabled;
        uniform vec3 uKeyColor;
        uniform float uOpacity;
        uniform sampler2D uMaskTex;
        uniform int uMaskEnabled;
        uniform int uMaskMode;
        uniform int uMaskFlipV;
        uniform int uImageFxEnabled;
        uniform vec2 uUvScale;
        uniform vec2 uImageBlurStep;
        uniform float uKeyStrength;
        uniform float uReveal;
        uniform int uCreativeGradient;
        uniform int uCreativeDistortion;
        uniform vec4 uGradientFrom;
        uniform vec4 uGradientTo;
        uniform vec4 uGradientGeometry;
        uniform float uGradientMix;
        uniform vec4 uDistort;
        uniform vec4 uGrade;
        uniform vec3 uFinishing;
        uniform vec2 uCreativeResolution;
        uniform vec4 uPattern;
        uniform vec4 uAperture;
        uniform vec2 uApertureCenter;
        vec3 hueShift(vec3 c,float a){
            vec3 axis=normalize(vec3(1.0,1.0,1.0));
            return c*cos(a)+cross(axis,c)*sin(a)+axis*dot(axis,c)*(1.0-cos(a));
        }
        float noise(vec2 p){
            return fract(sin(dot(p,vec2(12.9898,78.233)))*437.58545);
        }
        void main() {
            vec2 uv = vUv;
            if(uv.x>uReveal)discard;
            if (uShape == 1) {
                vec2 d = uv - vec2(0.5);
                if (dot(d, d) > 0.25) discard;
            } else if (uShape == 2) {
                vec2 d = abs(uv - vec2(0.5)) * 2.0;
                if (d.x * 0.866 + d.y * 0.5 > 0.866 || d.y > 0.866) discard;
            }
            vec2 sampleUv = uImageFxEnabled == 1 ? (uv - 0.5) * uUvScale + 0.5 : uv;
            if(uCreativeDistortion==1){
                sampleUv.x+=sin(sampleUv.y*uDistort.y*6.2831853+uDistort.z)*uDistort.x*0.15;
                sampleUv.y+=cos(sampleUv.x*uDistort.y*6.2831853-uDistort.z)*uDistort.x*0.08;
            }else if(uCreativeDistortion==2){
                vec2 p=sampleUv-0.5;
                float sector=6.2831853/max(2.0,floor(uDistort.y+0.5));
                float a=mod(atan(p.y,p.x)+uDistort.z,sector);
                a=abs(a-sector*0.5);
                vec2 folded=vec2(cos(a),sin(a))*length(p)+0.5;
                sampleUv=mix(sampleUv,folded,clamp(uDistort.x,0.0,1.0));
            }else if(uCreativeDistortion==3){
                float blocks=max(2.0,mix(256.0,4.0,clamp(uDistort.x,0.0,1.0)));
                vec2 dims=vec2(blocks,max(2.0,blocks*uCreativeResolution.y/max(1.0,uCreativeResolution.x)));
                sampleUv=(floor(sampleUv*dims)+0.5)/dims;
            }else if(uCreativeDistortion==4){
                sampleUv=(sampleUv-0.5)/max(0.25,1.0+uDistort.x*(0.5+0.5*sin(uDistort.z)))+0.5;
            }
            sampleUv=clamp(sampleUv,0.0,1.0);
            vec4 tex = texture2D(uTex, sampleUv);
            if (uImageFxEnabled == 1 && (uImageBlurStep.x > 0.0 || uImageBlurStep.y > 0.0)) {
                tex *= 0.25;
                tex += texture2D(uTex, sampleUv + vec2(uImageBlurStep.x, 0.0)) * 0.125;
                tex += texture2D(uTex, sampleUv - vec2(uImageBlurStep.x, 0.0)) * 0.125;
                tex += texture2D(uTex, sampleUv + vec2(0.0, uImageBlurStep.y)) * 0.125;
                tex += texture2D(uTex, sampleUv - vec2(0.0, uImageBlurStep.y)) * 0.125;
                tex += texture2D(uTex, sampleUv + uImageBlurStep) * 0.0625;
                tex += texture2D(uTex, sampleUv - uImageBlurStep) * 0.0625;
                tex += texture2D(uTex, sampleUv + vec2(uImageBlurStep.x, -uImageBlurStep.y)) * 0.0625;
                tex += texture2D(uTex, sampleUv + vec2(-uImageBlurStep.x, uImageBlurStep.y)) * 0.0625;
            }
            if(uCreativeGradient!=0){
                vec2 p=(uv-uGradientGeometry.xy)/max(0.01,uGradientGeometry.w);
                float a=uGradientGeometry.z;
                float t=0.0;
                if(uCreativeGradient==1)t=dot(p,vec2(cos(a),sin(a)))+0.5;
                else if(uCreativeGradient==2)t=length(p)*2.0;
                else t=fract((atan(p.y,p.x)+a)/6.2831853+0.5);
                vec4 grad=mix(uGradientFrom,uGradientTo,clamp(t,0.0,1.0));
                grad.rgb*=grad.a;
                tex=mix(tex,grad,clamp(uGradientMix,0.0,1.0));
            }
            if(uPattern.x>0.5){
                vec2 p=uv*uPattern.y;
                float f=0.0;
                if(uPattern.x<1.5){
                    vec2 d=abs(fract(p+uPattern.w*0.1)-0.5);
                    f=smoothstep(0.40,0.48,max(d.x,d.y));
                }else if(uPattern.x<2.5){
                    f=mod(floor(p.x+uPattern.w*0.15)+floor(p.y),2.0);
                }else if(uPattern.x<3.5){
                    f=0.5+0.5*sin(length(uv-0.5)*uPattern.y*6.2831853-uPattern.w);
                }else if(uPattern.x<4.5){
                    f=0.5+0.25*sin(p.x+uPattern.w+sin(p.y))+0.25*cos(p.y-uPattern.w+sin(p.x));
                }else{
                    vec2 cell=floor(p);
                    vec2 jitter=vec2(noise(cell),noise(cell+17.0))*0.65+0.175;
                    float d=length(fract(p)-jitter);
                    f=(1.0-smoothstep(0.02,0.13,d))*noise(cell+3.0)*(0.65+0.35*sin(uPattern.w+noise(cell)*6.2831853));
                }
                vec4 pattern=mix(uGradientFrom,uGradientTo,clamp(f,0.0,1.0));
                pattern.rgb*=pattern.a;
                tex=mix(tex,pattern,uPattern.z);
            }
            vec3 straightColor = tex.a > 0.0001 ? tex.rgb / tex.a : vec3(0.0);
            float luma = dot(straightColor, vec3(0.299, 0.587, 0.114));
            vec3 satColor = mix(vec3(luma), straightColor, uSaturation);
            if(abs(uGrade.w)>0.0001)satColor=hueShift(satColor,uGrade.w);
            satColor=(satColor-0.5)*uGrade.y+0.5+uGrade.x;
            satColor+=vec3(uGrade.z*0.15,0.0,-uGrade.z*0.15);
            float vignette=1.0-uFinishing.x*smoothstep(0.2,0.72,length(uv-0.5));
            float scan=1.0-uFinishing.y*(0.5+0.5*sin(uv.y*uCreativeResolution.y*1.5707963));
            satColor=satColor*vignette*scan;
            if(uFinishing.z>0.0)satColor+=(noise(floor(uv*uCreativeResolution)+floor(uDistort.z*24.0))-0.5)*uFinishing.z*0.25;
            satColor=clamp(satColor,0.0,1.0);
            float alpha = tex.a * uTint.a * uOpacity;
            if (uColorKeyEnabled == 1) {
                float dist = distance(straightColor, uKeyColor);
                alpha *= mix(1.0, smoothstep(0.12, 0.32, dist), uImageFxEnabled == 1 ? clamp(uKeyStrength, 0.0, 1.0) : 1.0);
            }
            if (uMaskEnabled == 1) {
                vec2 maskUv = vec2(uv.x, uMaskFlipV == 1 ? 1.0 - uv.y : uv.y);
                vec4 mask = texture2D(uMaskTex, maskUv);
                float light = dot(mask.rgb, vec3(0.299, 0.587, 0.114));
                float coverage = mask.a;
                if (uMaskMode == 1) coverage = min(mask.a, light);
                else if (uMaskMode == 2) coverage = max(0.0, mask.a - light);
                else if (uMaskMode == 3) coverage = 1.0 - mask.a;
                alpha *= coverage;
            }
            if(uAperture.x>0.5){
                vec2 p=abs(uv-uApertureCenter)*2.0;
                float d=length(p);
                if(uAperture.x>1.5 && uAperture.x<2.5)d=max(p.x,p.y);
                else if(uAperture.x>2.5 && uAperture.x<3.5)d=p.x+p.y;
                else if(uAperture.x>3.5)d=p.x;
                float coverage=1.0-smoothstep(max(0.0,uAperture.y-uAperture.z),uAperture.y,d);
                alpha*=mix(coverage,1.0-coverage,uAperture.w);
            }
            gl_FragColor = vec4(satColor * uTint.rgb * alpha, alpha);
        }
    """

    const val PARTICLE_VERTEX = """
        attribute vec2 aPos;
        attribute vec4 aColor;
        attribute vec2 aUv;
        uniform mat4 uMvp;
        varying vec4 vColor;
        varying vec2 vUv;
        void main() {
            vColor=aColor;
            vUv=aUv;
            gl_Position=uMvp*vec4(aPos,0.0,1.0);
        }
    """
    const val PARTICLE_FRAGMENT = """
        precision mediump float;
        uniform sampler2D uTex;
        uniform float uOpacity;
        varying vec4 vColor;
        varying vec2 vUv;
        void main(){
            vec4 sprite=texture2D(uTex,vUv);
            float a=vColor.a*uOpacity;
            gl_FragColor=vec4(sprite.rgb*vColor.rgb*a,sprite.a*a);
        }
    """
    const val BLUR_FRAGMENT = """
        precision mediump float;
        varying vec2 vUv;
        uniform sampler2D uTex;
        uniform vec2 uDirection;
        uniform float uRadius;
        void main() {
            vec4 sum = texture2D(uTex, vUv) * 0.227027;
            vec2 off1 = uDirection * uRadius * 1.384615;
            vec2 off2 = uDirection * uRadius * 3.230769;
            sum += texture2D(uTex, clamp(vUv + off1, 0.0, 1.0)) * 0.316216;
            sum += texture2D(uTex, clamp(vUv - off1, 0.0, 1.0)) * 0.316216;
            sum += texture2D(uTex, clamp(vUv + off2, 0.0, 1.0)) * 0.070270;
            sum += texture2D(uTex, clamp(vUv - off2, 0.0, 1.0)) * 0.070270;
            gl_FragColor = sum;
        }
    """

    const val RGB_SPLIT_FRAGMENT = """
        precision mediump float;
        varying vec2 vUv;
        uniform sampler2D uTex;
        uniform vec2 uOffset;
        uniform vec3 uColor0;
        uniform vec3 uColor1;
        uniform vec3 uColor2;
        uniform float uOpacity;
        void main() {
            vec4 r = texture2D(uTex, clamp(vUv + uOffset, 0.0, 1.0));
            vec4 g = texture2D(uTex, vUv);
            vec4 b = texture2D(uTex, clamp(vUv - uOffset, 0.0, 1.0));
            vec3 rgb = r.rgb * uColor0 * 0.5 + g.rgb * uColor1 * 0.5 + b.rgb * uColor2 * 0.5 +
                       vec3(r.r, g.g, b.b) * 0.5;
            float a = max(r.a, max(g.a, b.a)) * uOpacity;
            gl_FragColor = vec4(rgb * uOpacity, a);
        }
    """

    const val MOTION_FRAGMENT = """
        precision mediump float;
        uniform sampler2D uTex;
        uniform vec2 uMotion;
        uniform float uOpacity;
        varying vec2 vUv;
        void main(){
            vec4 sum=vec4(0.0);
            for(int i=0;i<9;i++){
                float t=float(i)/8.0;
                sum+=texture2D(uTex,clamp(vUv-uMotion*t,0.0,1.0))*(1.0-t*0.5);
            }
            gl_FragColor=sum*(uOpacity/6.75);
        }
    """
    const val MIRROR_FRAGMENT = """
        precision mediump float;
        varying vec2 vUv;
        uniform sampler2D uTex;
        uniform int uMode;
        uniform int uFlip;
        uniform float uRepeat;
        uniform vec4 uTint;
        void main() {
            vec2 uv = vUv;
            if (uMode == 0 || uMode == 2) {
                float x = fract(uv.x * max(1.0, uRepeat));
                uv.x = min(x, 1.0 - x);
                if (uFlip == 1) uv.x = 1.0 - uv.x;
            }
            if (uMode == 1 || uMode == 2) {
                float y = fract(uv.y * max(1.0, uRepeat));
                uv.y = min(y, 1.0 - y);
                if (uFlip == 1) uv.y = 1.0 - uv.y;
            }
            vec4 src = texture2D(uTex, clamp(uv, 0.0, 1.0));
            gl_FragColor = vec4(src.rgb * uTint.rgb * uTint.a, src.a * uTint.a);
        }
    """
}
