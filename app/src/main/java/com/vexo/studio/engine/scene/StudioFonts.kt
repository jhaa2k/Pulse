package com.vexo.studio.engine.scene
import android.content.Context
import android.graphics.Typeface
import android.os.Build

object StudioFonts{
    data class Family(val name:String,val key:String,val variable:Boolean)
    val families=listOf(
        Family("Poppins","poppins",false),
        Family("Inter","inter",true),
        Family("Montserrat","montserrat",true),
        Family("Roboto","roboto",true),
        Family("Oswald","oswald",true),
        Family("Bebas Neue","bebasneue",false),
        Family("Anton","anton",false),
        Family("Raleway","raleway",true),
        Family("Lato","lato",false),
        Family("Open Sans","opensans",true),
        Family("Nunito","nunito",true),
        Family("Rubik","rubik",true),
        Family("Outfit","outfit",true),
        Family("Space Grotesk","spacegrotesk",true),
        Family("Playfair Display","playfairdisplay",true),
        Family("Merriweather","merriweather",true),
        Family("Lora","lora",true),
        Family("JetBrains Mono","jetbrainsmono",true),
        Family("Fira Code","firacode",true),
        Family("DM Sans","dmsans",true),
        Family("Barlow","barlow",false),
        Family("Archivo Black","archivoblack",false),
        Family("Cinzel","cinzel",true)
    )
    val weights=listOf(100,200,300,400,500,600,700,800,900)
    fun weightLabel(weight:Int)=when(weight){100->"Thin";200->"Extra Light";300->"Light";400->"Regular";500->"Medium";600->"Semi Bold";700->"Bold";800->"Extra Bold";900->"Black";else->"Legacy"}
    private fun weightFile(weight:Int)=when(weight){100->"thin";200->"extralight";300->"light";500->"medium";600->"semibold";700->"bold";800->"extrabold";900->"black";else->"regular"}
    fun resolve(context:Context,name:String,weight:Int,italic:Boolean):Typeface{
        val family=families.firstOrNull{it.name.equals(name,true)}?:families.first()
        val w=weights.minByOrNull{kotlin.math.abs(it-weight)}?:400
        val staticWeights=family.key in setOf("poppins","lato","barlow")
        val path="fonts/"+family.key+(if(staticWeights&&w!=400)"_"+weightFile(w) else "")+".ttf"
        return runCatching{
            if(Build.VERSION.SDK_INT>=26&&family.variable){
                val base=Typeface.Builder(context.assets,path).setWeight(w).setFontVariationSettings("'wght' "+w).build()?:Typeface.DEFAULT
                if(italic)Typeface.create(base,Typeface.ITALIC) else base
            }else{
                val base=Typeface.createFromAsset(context.assets,path)
                if(staticWeights&&!italic)base else Typeface.create(base,if(italic)Typeface.ITALIC or (if(w>=700&&!staticWeights)Typeface.BOLD else 0) else if(w>=700&&!staticWeights)Typeface.BOLD else Typeface.NORMAL)
            }
        }.getOrDefault(Typeface.create(Typeface.SANS_SERIF,if(w>=700)Typeface.BOLD else Typeface.NORMAL))
    }
}
