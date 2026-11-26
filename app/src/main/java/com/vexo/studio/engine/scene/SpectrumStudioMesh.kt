package com.vexo.studio.engine.scene
import com.vexo.studio.model.*
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
    private fun closed(c:LayerConfig.Bars)=c.pathGap<=.0001f&&when(c.creativePath){
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
        fun point(t:Float):P{
            val u=if(closed)((t%1f)+1f)%1f else t.coerceIn(0f,1f)
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
        val samples=ArrayList<Sample>(n)
        val gain=c.heightScale*c.barHeightMultiplier
        val lo=minOf(abs(c.minHeightScale),abs(c.maxHeightScale));val hi=maxOf(abs(c.minHeightScale),abs(c.maxHeightScale))
        for(i in 0 until n){
            val t=(i+.5f)/n
            val origin=point(t);val d=point(t+.25f/n)-point(t-.25f/n);val tangent=d.unit()
            val size=(sqrt(d.x*d.x+d.y*d.y)*2f*c.barWidth.coerceIn(.05f,1.5f)*(1f-c.barSpacing.coerceIn(0f,.95f))).coerceIn(.5f,maxOf(.5f,dim*.15f))
            val src=(i.toLong()*bars.size/n).toInt().coerceIn(0,bars.lastIndex)
            val idx=if(c.flipInput)bars.lastIndex-src else src
            val raw=bars[idx].takeIf{it.isFinite()}?:0f
            val amplitude=(raw*abs(gain)).coerceIn(lo,hi)
            val direction=(if(gain<0f)-1f else 1f)*(if(c.alternatingDirection&&i%2==1)-1f else 1f)
            val length=(amplitude*dim*.42f+c.fixedHeight*2f)*direction
            val base=if(c.mirror)-length*.5f else 0f
            val peak=((peaks?.getOrNull(idx)?:raw)*abs(gain)).coerceIn(0f,hi)*dim*.42f*direction*(if(c.mirror).5f else 1f)
            val color=MeasureEvaluator.blendArgb(c.colorFromArgb,c.colorToArgb,(t+c.colorBlendOffset).coerceIn(0f,1f))
            samples.add(Sample(origin,tangent,tangent.normal(),size,base,base+length,peak,color,amplitude))
        }
        fun tri(a:P,b:P,d:P,ca:Int,cb:Int=ca,cd:Int=ca){vertex(a.x,a.y,ca);vertex(b.x,b.y,cb);vertex(d.x,d.y,cd)}
        fun quad(a:P,b:P,d:P,e:P,ca:Int,cb:Int=ca){tri(a,b,d,ca,ca,cb);tri(d,b,e,cb,ca,cb)}
        fun line(a:P,b:P,thick:Float,col:Int,end:Int=col){
            val off=(b-a).unit().normal()*(thick*.5f)
            quad(a-off,a+off,b-off,b+off,col,end)
        }
        fun disk(p:P,r:Float,col:Int,hollow:Boolean=false){
            for(j in 0 until 12){
                val a=j*PI.toFloat()/6f;val b=(j+1)*PI.toFloat()/6f
                val u=P(cos(a),sin(a));val v=P(cos(b),sin(b))
                if(hollow)quad(p+u*(r*.62f),p+v*(r*.62f),p+u*r,p+v*r,col)else tri(p,p+u*r,p+v*r,col)
            }
        }
        fun alpha(col:Int,f:Float)=(((col ushr 24)*f.coerceIn(0f,1f)).roundToInt() shl 24) or (col and 0xFFFFFF)
        val connected=style in setOf(CreativeSpectrumStyle.LINE,CreativeSpectrumStyle.FILL,CreativeSpectrumStyle.DOUBLE_LINE,CreativeSpectrumStyle.RIBBON,CreativeSpectrumStyle.ZIGZAG,CreativeSpectrumStyle.WAVE_POINTS)
        if(connected&&n>1){
            val segments=if(closed)n else n-1
            for(i in 0 until segments){
                val a=samples[i];val b=samples[(i+1)%n]
                val ac=a.p(0f,a.tip);val bc=b.p(0f,b.tip);val ab=a.p(0f,a.base);val bb=b.p(0f,b.base)
                when(style){
                    CreativeSpectrumStyle.FILL->{quad(ab,bb,ac,bc,alpha(a.color,c.creativeFillAlpha*.35f),alpha(b.color,c.creativeFillAlpha));line(ac,bc,stroke,a.color,b.color)}
                    CreativeSpectrumStyle.DOUBLE_LINE->{line(ac,bc,stroke,a.color,b.color);line(a.p(0f,a.tip-stroke*2.5f),b.p(0f,b.tip-stroke*2.5f),stroke,a.color,b.color);if(c.mirror)line(ab,bb,stroke,a.color,b.color)}
                    CreativeSpectrumStyle.RIBBON->{val aw=a.width*.5f;val bw=b.width*.5f;quad(a.p(0f,a.tip-aw),b.p(0f,b.tip-bw),a.p(0f,a.tip+aw),b.p(0f,b.tip+bw),alpha(a.color,c.creativeFillAlpha),alpha(b.color,c.creativeFillAlpha));line(ac,bc,stroke,a.color,b.color)}
                    CreativeSpectrumStyle.ZIGZAG->{val pa=if(i%2==0)ac else ab;val pb=if((i+1)%2==0)bc else bb;line(pa,pb,stroke,a.color,b.color)}
                    else->{line(ac,bc,stroke,a.color,b.color);if(c.mirror)line(ab,bb,stroke,a.color,b.color)}
                }
            }
        }
        for(s in samples){
            val q=s.width*.5f;val c0=s.color;val base=s.base;val tip=s.tip
            val radius=(q*c.creativeTipSize.coerceIn(.25f,3f)).coerceAtLeast(stroke*.5f)
            fun rect(lo:Float,hi:Float,half:Float=q){quad(s.p(-half,lo),s.p(half,lo),s.p(-half,hi),s.p(half,hi),c0)}
            fun localLine(x:Float,y:Float,xx:Float,yy:Float){line(s.p(x,y),s.p(xx,yy),stroke,c0)}
            when(style){
                CreativeSpectrumStyle.BARS->rect(base,tip)
                CreativeSpectrumStyle.DOTS->disk(s.p(0f,tip),radius,c0)
                CreativeSpectrumStyle.HOLLOW_DOTS->disk(s.p(0f,tip),radius,c0,true)
                CreativeSpectrumStyle.STEPS,CreativeSpectrumStyle.STACK_DOTS,CreativeSpectrumStyle.STRIPES->{
                    val steps=c.stepCount.coerceIn(2,16);val lit=ceil(s.amplitude.coerceIn(0f,1f)*steps).toInt().coerceIn(0,steps)
                    for(j in 0 until lit){
                        val a=j.toFloat()/lit;val b=(j+.68f)/lit
                        when(style){
                            CreativeSpectrumStyle.STACK_DOTS->disk(s.p(0f,s.mix((a+b)*.5f)),minOf(radius,abs(tip-base)/(2f*lit)).coerceAtLeast(.35f),c0)
                            CreativeSpectrumStyle.STRIPES->localLine(-q,s.mix(a),q,s.mix(b))
                            else->rect(s.mix(a),s.mix(b))
                        }
                    }
                }
                CreativeSpectrumStyle.OUTLINE->{localLine(-q,base,q,base);localLine(-q,tip,q,tip);localLine(-q,base,-q,tip);localLine(q,base,q,tip)}
                CreativeSpectrumStyle.TRIANGLE->tri(s.p(-q,base),s.p(q,base),s.p(0f,tip),c0)
                CreativeSpectrumStyle.DIAMOND->{val mid=s.mix(.5f);tri(s.p(0f,base),s.p(-q,mid),s.p(0f,tip),c0);tri(s.p(0f,base),s.p(0f,tip),s.p(q,mid),c0)}
                CreativeSpectrumStyle.CAPSULE->{val r=minOf(q,abs(tip-base)*.5f)*c.roundness.coerceIn(0f,1f);if(r<=.001f)rect(base,tip)else{val dir=if(tip>=base)1f else -1f;val a=base+r*dir;val b=tip-r*dir;rect(a,b);disk(s.p(0f,a),r,c0);disk(s.p(0f,b),r,c0)}}
                CreativeSpectrumStyle.DASH->rect(s.mix(.68f),tip)
                CreativeSpectrumStyle.LOLLIPOP->{localLine(0f,base,0f,tip);disk(s.p(0f,tip),radius,c0)}
                CreativeSpectrumStyle.CROSS->{localLine(0f,tip-radius,0f,tip+radius);localLine(-radius,tip,radius,tip)}
                CreativeSpectrumStyle.CHEVRON->{localLine(-q,s.mix(.65f),0f,tip);localLine(0f,tip,q,s.mix(.65f))}
                CreativeSpectrumStyle.TAPERED->quad(s.p(-q,base),s.p(q,base),s.p(-q*.18f,tip),s.p(q*.18f,tip),c0)
                CreativeSpectrumStyle.CAPPED->{rect(base,s.mix(.88f),q*.55f);localLine(-q,tip,q,tip)}
                CreativeSpectrumStyle.WAVE_POINTS->disk(s.p(0f,tip),radius*.65f,c0)
                CreativeSpectrumStyle.SAWTOOTH->tri(s.p(-q,base),s.p(q,base),s.p(q,tip),c0)
                CreativeSpectrumStyle.FAN->tri(s.p(0f,base),s.p(-q*1.5f,tip),s.p(q*1.5f,tip),c0)
                CreativeSpectrumStyle.TICKS->localLine(-q,tip,q,tip)
                CreativeSpectrumStyle.PEAKS->{localLine(-q,s.peak,q,s.peak);rect(base,s.mix(.2f),q*.4f)}
                else->if(n==1)rect(base,tip)
            }
            if(c.showPeaks&&peaks!=null&&style!=CreativeSpectrumStyle.PEAKS)localLine(-q,s.peak,q,s.peak)
        }
    }
}
