package com.vexo.studio.data

/** .vixo is the public suffix of the existing versioned, portable VEX archive.
 * Decoding remains content-based so legacy .vex and supported Avee imports still work.
 */
object VixoFileName {
    const val EXTENSION = ".vixo"
    fun create(raw:String):String {
        var name=raw.trim()
        for(ext in listOf(".vixo",".vix",".vex")){
            if(name.endsWith(ext,ignoreCase=true)){name=name.dropLast(ext.length);break}
        }
        name=name.map{c->if(c.isISOControl()||c in "/\\:*?\"<>|") '_' else c}.joinToString("").trim().trim('.')
        while(name.toByteArray(Charsets.UTF_8).size>200){
            name=name.substring(0,name.length-Character.charCount(name.codePointBefore(name.length)))
        }
        return name.ifBlank{"Vexo Project"}+EXTENSION
    }
}
