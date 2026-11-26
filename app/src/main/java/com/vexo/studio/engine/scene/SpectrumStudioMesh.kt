package com.vexo.studio.engine.scene
import com.vexo.studio.model.*
import com.vexo.studio.engine.gl.SpectrumGeometry
import kotlin.math.*

/** Shared triangle geometry for live preview, export and sample gallery. */
object SpectrumStudioMesh {
    private data class P(val x:Float,val y:Float){
        operator fun plus(p:P)=P(x+p.x,y+p.y)
        operator fun minus(p:P)=P(x-p.x,y-p.y)
        operator fun times(f:Float)=P(x*f,y*f)
        fun unit():P{val d=sqrt(x*x+y*y);return if(d>.0001f)P(x/d,y/d) else P(1f,0f)}
        fun normal()=P(y,-x)
    }
    private data class Sample(val origin:P,val tangent:P,val normal:P,val width:Float,val base:Float,val tip:Float,val peak:Float,val color:Int,val amplitude:Float){
        fun p(x:Float,y:Float)=origin+tangent*x+normal*y
        fun mix(t:Float)=base+(tip-base)*t
    }
    private fun closed(c:LayerConfig.Bars)=if(c.customPathEnabled && c.customPathPoints.size>=2) c.customPathClosed else c.pathGap<=.0001f&&when(c.creativePath){
        CreativeSpectrumPath.LEGACY->c.shapePath==BarsShapePath.CIRCLE||c.shapePath==BarsShapePath.SIDED_POLYGON
        CreativeSpectrumPath.ARC,CreativeSpectrumPath.SPIRAL,CreativeSpectrumPath.SINE,CreativeSpectrumPath.DIAGONAL,CreativeSpectrumPath.ZIGZAG->false
        else->true
    }
    fun styleFor(c:LayerConfig.Bars):CreativeSpectrumStyle=if(c.creativeStyle!=CreativeSpectrumStyle.BARS)c.creativeStyle else when(c.segment1){
        BarsSegmentType.LINE->CreativeSpectrumStyle.LINE
        BarsSegmentType.ROUND_BARS->CreativeSpectrumStyle.CAPSULE
        BarsSegmentType.SHARP_BARS->CreativeSpectrumStyle.TAPERED
        else->CreativeSpectrumStyle.BARS
    }
    fun emit(c:LayerConfig.Bars,bars:FloatArray,peaks:FloatArray?,width:Int,height:Int,vertex:(Float,Float,Int)->Unit){
        if(bars.isEmpty()||width<=0||height<=0)return
        val n=bars.size.coerceAtMost(512)
        val w=width.toFloat();val h=height.toFloat();val dim=minOf(w,h)
        val style=styleFor(c);val closed=closed(c)
        val stroke=(c.creativeStrokeWidth.coerceIn(.25f,24f)*dim/720f).coerceAtLeast(.65f)
        val horizontal=c.creativePath in setOf(CreativeSpectrumPath.SINE,CreativeSpectrumPath.DIAGONAL,CreativeSpectrumPath.ZIGZAG)
        val custom=if(c.customPathEnabled) CustomSpectrumPath.create(c.customPathPoints,c.customPathClosed) else null
        fun rawPoint(t:Float):P{
            val u=if(closed)((t%1f)+1f)%1f else t.coerceIn(0f,1f)
            if(custom!=null){
                val pt=custom.at(u)
                val angle=c.pathAngleDeg*PI.toFloat()/180f
                return P(w*.5f+(pt.x*cos(angle)-pt.y*sin(angle))*dim,h*.5f+(pt.x*sin(angle)+pt.y*cos(angle))*dim)
            }
            if(c.creativePath!=CreativeSpectrumPath.LEGACY){
                val p=CreativeGeometry.path(c.creativePath,u,c.pathTurns,c.pathSpread,c.circleRadius,c.pathGap,c.pathAngleDeg)
                return P(w*.5f+p.x*(if(horizontal)w else dim),h*.5f+p.y*dim)
            }
            if(c.shapePath==BarsShapePath.VERTICAL_LINE)return P(w*.5f,h*(.07f+.86f*u))
            if(c.shapePath==BarsShapePath.HORIZONTAL_LINE||c.shapePath==BarsShapePath.LETTER)return P(w*(.07f+.86f*u),h*.5f)
            val a=u*2f*PI.toFloat()*(1f-c.pathGap.coerceIn(0f,.9f))+c.pathAngleDeg*PI.toFloat()/180f
            var r=c.circleRadius*dim
            if(c.shapePath==BarsShapePath.SIDED_POLYGON){val sector=2f*PI.toFloat()/c.polygonSides.coerceIn(3,24);val local=((a%sector)+sector)%sector-sector*.5f;r*=cos(sector*.5f)/cos(local).coerceAtLeast(.2f)}
            return P(w*.5f+cos(a)*r,h*.5f+sin(a)*r)
        }
        fun point(t:Float):P {
            val p=rawPoint(t)
            return P(w*.5f+(p.x-w*.5f)*c.pathStretchX.coerceIn(.1f,3f),h*.5f+(p.y-h*.5f)*c.pathStretchY.coerceIn(.1f,3f))
        }
        val samples=ArrayList<Sample>(n)
        val gain=c.heightScale*c.barHeightMultiplier
        val lo=minOf(abs(c.minHeightScale),abs(c.maxHeightScale));val hi=maxOf(abs(c.minHeightScale),abs(c.maxHeightScale))
        for(i in 0 until n){
            val t=(i+.5f)/n
            val origin=point(t);val d=point(t+.25f/n)-point(t-.25f/n);val tangent=d.unit()
            val size=(sqrt(d.x*d.x+d.y*d.y)*2f*c.barWidth.coerceIn(.05f,1.5f)*(1f-c.barSpacing.coerceIn(0f,.95f))).coerceIn(.5f,maxOf(.5f,dim*.15f))
            val phase = (i.toDouble()/n*c.sampleRepeats.coerceIn(1,8) + c.sampleOffset.coerceIn(-1f,1f))
            val wrapped = phase - floor(phase)
            val src = (wrapped * bars.size).toInt().coerceIn(0,bars.lastIndex)
            val idx=if(c.flipInput)bars.lastIndex-src else src
            val raw=bars[idx].takeIf{it.isFinite()}?:0f
            val amplitude=((if(c.signedWaveform)abs(raw) else raw.coerceAtLeast(0f)).pow(c.amplitudeCurve.coerceIn(.25f,3f))*abs(gain)).coerceIn(if(c.signedWaveform)0f else lo,hi)
            val direction=(if(gain<0f)-1f else 1f)*(if(c.alternatingDirection&&i%2==1)-1f else 1f)*(if(c.signedWaveform&&raw<0f)-1f else 1f)
            val length=(amplitude*dim*.42f+c.fixedHeight*2f)*direction
            val base=c.baselineOffset.coerceIn(-1f,1f)*dim*.3f + if(c.mirror)-length*.5f else 0f
            val peak=c.baselineOffset.coerceIn(-1f,1f)*dim*.3f+((peaks?.getOrNull(idx)?:raw).coerceAtLeast(0f).pow(c.amplitudeCurve.coerceIn(.25f,3f))*abs(gain)).coerceIn(0f,hi)*dim*.42f*direction*(if(c.mirror).5f else 1f)
            val phaseColor=t*c.gradientRepeats.coerceIn(1,8)+c.colorBlendOffset
            val blend=if(c.gradientRepeats<=1)phaseColor.coerceIn(0f,1f) else 1f-abs(2f*(phaseColor-floor(phaseColor))-1f)
            val color=MeasureEvaluator.blendArgb(c.colorFromArgb,c.colorToArgb,when(c.barGradientMode){
                SpectrumGradientMode.PATH->blend
                SpectrumGradientMode.HEIGHT->0f
                SpectrumGradientMode.AMPLITUDE->amplitude.coerceIn(0f,1f)
                SpectrumGradientMode.ALTERNATING->if(i%2==0)0f else 1f
            })
            samples.add(Sample(origin,tangent,tangent.normal(),size,base,base+length,peak,color,amplitude))
        }
        fun tri(a:P,b:P,d:P,ca:Int,cb:Int=ca,cd:Int=ca){vertex(a.x,a.y,ca);vertex(b.x,b.y,cb);vertex(d.x,d.y,cd)}
        fun quad(a:P,b:P,d:P,e:P,ca:Int,cb:Int=ca){tri(a,b,d,ca,ca,cb);tri(d,b,e,cb,ca,cb)}
        fun line(a:P,b:P,thick:Float,col:Int,end:Int=col){
            val off=(b-a).unit().normal()*(thick*.5f)
            quad(a-off,a+off,b-off,b+off,col,end)
        }
        fun disk(p:P,r:Float,col:Int,hollow:Boolean=false){
            val n=SpectrumGeometry.quarterSegments(r)*4
            for(j in 0 until n){
                val a=j*2f*PI.toFloat()/n;val b=(j+1)*2f*PI.toFloat()/n
                val u=P(cos(a),sin(a));val v=P(cos(b),sin(b))
                if(hollow)quad(p+u*(r*.62f),p+v*(r*.62f),p+u*r,p+v*r,col)else tri(p,p+u*r,p+v*r,col)
            }
        }
        fun alpha(col:Int,f:Float)=(((col ushr 24)*f.coerceIn(0f,1f)).roundToInt() shl 24) or (col and 0xFFFFFF)
        // Convex fill and inset stroke rings. The ring never covers the interior,
        // so zero fill opacity leaves a genuine transparent hole.
        val outline=(c.barStrokeWidth.coerceIn(0f,24f)*dim/720f)
        val styled=outline>0f || c.barFillOpacity!=1f || c.barTipWidth!=1f || c.barGradientMode==SpectrumGradientMode.HEIGHT
        fun polygon(s:Sample,points:List<P>){
            if(points.size<3)return
            val center=points.reduce{a,b->a+b}*(1f/points.size)
            fun cross(a:P,b:P)=a.x*b.y-a.y*b.x
            val area=points.indices.sumOf{ i->cross(points[i]-center,points[(i+1)%points.size]-center).toDouble() }
            if(abs(area)<.00001)return
            val orientation=if(area>0)1f else -1f
            fun inward(i:Int):P{
                val d=(points[(i+1)%points.size]-points[i]).unit()
                return P(-d.y*orientation,d.x*orientation)
            }
            fun dot(a:P,b:P)=a.x*b.x+a.y*b.y
            fun fillColor(p:P):Int{
                val fraction=if(abs(s.tip-s.base)>.0001f)((dot(p-s.origin,s.normal)-s.base)/(s.tip-s.base)).coerceIn(0f,1f) else 0f
                val col=if(c.barGradientMode==SpectrumGradientMode.HEIGHT)MeasureEvaluator.blendArgb(c.colorFromArgb,c.colorToArgb,fraction) else s.color
                return alpha(col,c.barFillOpacity)
            }
            var inner=points
            if(outline>0f){
                val distance=points.indices.minOf{i->dot(center-points[i],inward(i))}.coerceAtLeast(0f)
                val inset=minOf(outline,distance*.95f)
                if(inset>0.0001f){
                    inner=points.indices.map{i->
                        val a=inward((i+points.size-1)%points.size);val b=inward(i)
                        val sum=a+b
                        points[i]+sum*(inset/dot(sum,b).coerceAtLeast(.05f))
                    }
                    for(i in points.indices){
                        val j=(i+1)%points.size
                        quad(points[i],points[j],inner[i],inner[j],c.barStrokeColorArgb)
                    }
                }
            }
            if(c.barFillOpacity>0f)for(i in 1 until inner.lastIndex){
                tri(inner[0],inner[i],inner[i+1],fillColor(inner[0]),fillColor(inner[i]),fillColor(inner[i+1]))
            }
        }
        val connected=style in setOf(CreativeSpectrumStyle.LINE,CreativeSpectrumStyle.FILL,CreativeSpectrumStyle.DOUBLE_LINE,CreativeSpectrumStyle.RIBBON,CreativeSpectrumStyle.ZIGZAG,CreativeSpectrumStyle.WAVE_POINTS)
        if(connected&&n>1){
            val segments=if(closed)n else n-1
            val divisions=c.curveSubdivisions.coerceIn(1,8)
            fun sampleAt(index:Int)=samples[if(closed)((index%n)+n)%n else index.coerceIn(0,n-1)]
            fun curve(p0:P,p1:P,p2:P,p3:P,t:Float):P {
                if(divisions==1)return p1+(p2-p1)*t
                val t2=t*t;val t3=t2*t;val tension=c.waveCurveTension.coerceIn(0f,1f)
                return p1*(2f*t3-3f*t2+1f)+(p2-p0)*(tension*(t3-2f*t2+t))+
                    p2*(-2f*t3+3f*t2)+(p3-p1)*(tension*(t3-t2))
            }
            fun at(i:Int,t:Float,offset:(Sample)->Float):P{
                fun p(index:Int):P{val s=sampleAt(index);return s.p(0f,offset(s))}
                return curve(p(i-1),p(i),p(i+1),p(i+2),t)
            }
            fun color(i:Int,t:Float)=MeasureEvaluator.blendArgb(sampleAt(i).color,sampleAt(i+1).color,t)
            // Dashes are positioned along normalized path progress, not per mesh triangle.
            fun waveLine(i:Int,aT:Float,bT:Float,a:P,b:P,ca:Int,cb:Int){
                val ratio=c.waveDashRatio.coerceIn(.05f,1f)
                if(ratio>=.999f){line(a,b,stroke,ca,cb);return}
                val repeat=c.waveDashCount.coerceIn(1,64).toFloat()
                val start=(i+aT)/segments*repeat;val end=(i+bT)/segments*repeat
                if(end-start<.000001f)return
                for(k in floor(start).toInt()..floor(end).toInt()){
                    val lo=maxOf(start,k.toFloat());val hi=minOf(end,k+ratio)
                    if(hi<=lo)continue
                    val f0=(lo-start)/(end-start);val f1=(hi-start)/(end-start)
                    line(a+(b-a)*f0,a+(b-a)*f1,stroke,MeasureEvaluator.blendArgb(ca,cb,f0),MeasureEvaluator.blendArgb(ca,cb,f1))
                }
            }
            fun smooth(i:Int, offset:(Sample)->Float){
                for(j in 0 until divisions){
                    val t0=j.toFloat()/divisions;val t1=(j+1f)/divisions
                    waveLine(i,t0,t1,at(i,t0,offset),at(i,t1,offset),color(i,t0),color(i,t1))
                }
            }
            fun strip(i:Int,low:(Sample)->Float,high:(Sample)->Float,lowAlpha:Float,highAlpha:Float){
                for(j in 0 until divisions){
                    val t0=j.toFloat()/divisions;val t1=(j+1f)/divisions
                    val a=at(i,t0,low);val b=at(i,t1,low);val d=at(i,t0,high);val e=at(i,t1,high)
                    val ca=color(i,t0);val cb=color(i,t1)
                    tri(a,b,d,alpha(ca,lowAlpha),alpha(cb,lowAlpha),alpha(ca,highAlpha))
                    tri(d,b,e,alpha(ca,highAlpha),alpha(cb,lowAlpha),alpha(cb,highAlpha))
                }
            }
            for(i in 0 until segments){
                val a=samples[i];val b=samples[(i+1)%n]
                val ac=a.p(0f,a.tip);val bc=b.p(0f,b.tip);val ab=a.p(0f,a.base);val bb=b.p(0f,b.base)
                when(style){
                    CreativeSpectrumStyle.FILL->{strip(i,{it.base},{it.tip},c.creativeFillAlpha*.35f,c.creativeFillAlpha);smooth(i){it.tip}}
                    CreativeSpectrumStyle.DOUBLE_LINE->{smooth(i){it.tip};smooth(i){it.tip-stroke*c.waveLineGap.coerceIn(.25f,12f)};if(c.mirror)smooth(i){it.base}}
                    CreativeSpectrumStyle.RIBBON->{val factor=c.waveRibbonWidth.coerceIn(.1f,5f)*.5f;strip(i,{it.tip-it.width*factor},{it.tip+it.width*factor},c.creativeFillAlpha,c.creativeFillAlpha);smooth(i){it.tip}}
                    CreativeSpectrumStyle.ZIGZAG->{val pa=if(i%2==0)ac else ab;val pb=if((i+1)%2==0)bc else bb;waveLine(i,0f,1f,pa,pb,a.color,b.color)}
                    else->{smooth(i){it.tip};if(c.mirror)smooth(i){it.base}}
                }
            }
        }
        for(s in samples){
            val q=s.width*.5f;val c0=s.color;val base=s.base;val tip=s.tip
            val radius=(q*c.creativeTipSize.coerceIn(.25f,3f)).coerceAtLeast(stroke*.5f)
            fun shape(vararg p:P){polygon(s,p.toList())}
            fun rect(lo:Float,hi:Float,half:Float=q){
                val top=half*c.barTipWidth.coerceIn(.05f,2f)
                if(styled)shape(s.p(-half,lo),s.p(half,lo),s.p(top,hi),s.p(-top,hi))
                else quad(s.p(-half,lo),s.p(half,lo),s.p(-half,hi),s.p(half,hi),c0)
            }
            fun dot(at:P,r:Float,hollow:Boolean=false){
                if(styled&&!hollow){
                    val n=SpectrumGeometry.quarterSegments(r)*4
                    polygon(s,List(n){j->val a=j*2f*PI.toFloat()/n;at+P(cos(a),sin(a))*r})
                } else disk(at,r,c0,hollow)
            }
            fun capsule(){
                val low=minOf(base,tip);val high=maxOf(base,tip)
                val r=minOf(q,(high-low)*.5f)*c.roundness.coerceIn(0f,1f)
                if(r<=.001f){rect(base,tip);return}
                val center=(low+high)*.5f
                val contour=SpectrumGeometry.roundedContour(q,(high-low)*.5f,r).map{(x,y)->s.p(x,center+y)}
                polygon(s,contour)
            }
            fun localLine(x:Float,y:Float,xx:Float,yy:Float){line(s.p(x,y),s.p(xx,yy),stroke,c0)}
            when(style){
                CreativeSpectrumStyle.BARS->rect(base,tip)
                CreativeSpectrumStyle.DOTS->dot(s.p(0f,tip),radius)
                CreativeSpectrumStyle.HOLLOW_DOTS->disk(s.p(0f,tip),radius,c0,true)
                CreativeSpectrumStyle.STEPS,CreativeSpectrumStyle.STACK_DOTS,CreativeSpectrumStyle.STRIPES->{
                    val steps=c.stepCount.coerceIn(2,16);val lit=ceil(s.amplitude.coerceIn(0f,1f)*steps).toInt().coerceIn(0,steps)
                    for(j in 0 until lit){
                        val a=j.toFloat()/lit;val b=(j+.68f)/lit
                        when(style){
                            CreativeSpectrumStyle.STACK_DOTS->dot(s.p(0f,s.mix((a+b)*.5f)),minOf(radius,abs(tip-base)/(2f*lit)).coerceAtLeast(.35f))
                            CreativeSpectrumStyle.STRIPES->localLine(-q,s.mix(a),q,s.mix(b))
                            else->rect(s.mix(a),s.mix(b))
                        }
                    }
                }
                CreativeSpectrumStyle.OUTLINE->{localLine(-q,base,q,base);localLine(-q,tip,q,tip);localLine(-q,base,-q,tip);localLine(q,base,q,tip)}
                CreativeSpectrumStyle.TRIANGLE->if(styled)shape(s.p(-q,base),s.p(q,base),s.p(0f,tip))else tri(s.p(-q,base),s.p(q,base),s.p(0f,tip),c0)
                CreativeSpectrumStyle.DIAMOND->{val mid=s.mix(.5f);if(styled)shape(s.p(0f,base),s.p(-q,mid),s.p(0f,tip),s.p(q,mid))else{tri(s.p(0f,base),s.p(-q,mid),s.p(0f,tip),c0);tri(s.p(0f,base),s.p(0f,tip),s.p(q,mid),c0)}}
                CreativeSpectrumStyle.CAPSULE->capsule()
                CreativeSpectrumStyle.DASH->rect(s.mix(.68f),tip)
                CreativeSpectrumStyle.LOLLIPOP->{localLine(0f,base,0f,tip);dot(s.p(0f,tip),radius)}
                CreativeSpectrumStyle.CROSS->{localLine(0f,tip-radius,0f,tip+radius);localLine(-radius,tip,radius,tip)}
                CreativeSpectrumStyle.CHEVRON->{localLine(-q,s.mix(.65f),0f,tip);localLine(0f,tip,q,s.mix(.65f))}
                CreativeSpectrumStyle.TAPERED->{val top=q*.18f*c.barTipWidth.coerceIn(.05f,2f);if(styled)shape(s.p(-q,base),s.p(q,base),s.p(top,tip),s.p(-top,tip))else quad(s.p(-q,base),s.p(q,base),s.p(-top,tip),s.p(top,tip),c0)}
                CreativeSpectrumStyle.CAPPED->{rect(base,s.mix(.88f),q*.55f);localLine(-q,tip,q,tip)}
                CreativeSpectrumStyle.WAVE_POINTS->dot(s.p(0f,tip),radius*.65f)
                CreativeSpectrumStyle.SAWTOOTH->if(styled)shape(s.p(-q,base),s.p(q,base),s.p(q,tip))else tri(s.p(-q,base),s.p(q,base),s.p(q,tip),c0)
                CreativeSpectrumStyle.FAN->if(styled)shape(s.p(0f,base),s.p(-q*1.5f,tip),s.p(q*1.5f,tip))else tri(s.p(0f,base),s.p(-q*1.5f,tip),s.p(q*1.5f,tip),c0)
                CreativeSpectrumStyle.TICKS->localLine(-q,tip,q,tip)
                CreativeSpectrumStyle.PEAKS->{localLine(-q,s.peak,q,s.peak);rect(base,s.mix(.2f),q*.4f)}
                else->if(n==1)rect(base,tip)
            }
            if(c.showPeaks&&peaks!=null&&style!=CreativeSpectrumStyle.PEAKS)localLine(-q,s.peak,q,s.peak)
        }
    }
}
