package com.vexo.studio.engine.scene

import com.vexo.studio.model.CompositionNode

/** Output texture lifetimes, including shared sources and composition masks.
 * A source remains live through its last consumer's entire draw, so its
 * framebuffer can never be reused as that consumer's render target.
 */
data class CompositionSlotPlan(val slotByComposition:Map<Int,Int>,val retireBefore:Map<Int,List<Int>>,val slotCount:Int){
    companion object{
        fun create(order:List<CompositionNode>,reachable:Set<Int>,root:Int):CompositionSlotPlan{
            val sequence=order.filter{it.index in reachable&&it.index!=root}
            val final=order.firstOrNull{it.index==root}
            val ordered=sequence+listOfNotNull(final)
            val indices=ordered.mapIndexed{i,c->c.index to i}.toMap()
            val lastUse=mutableMapOf<Int,Int>()
            for((i,c) in ordered.withIndex()){
                for(dep in SceneGraph.dependenciesOf(c)){
                    val made=indices[dep]?:continue
                    if(dep!=root&&made<i)lastUse[dep]=maxOf(lastUse[dep]?:made,i)
                }
            }
            val slots=linkedMapOf<Int,Int>()
            val live=linkedMapOf<Int,Int>()
            val retired=linkedMapOf<Int,List<Int>>()
            var count=0
            for((i,c) in sequence.withIndex()){
                val done=live.keys.filter{(lastUse[it]?:indices.getValue(it))<i}
                if(done.isNotEmpty())retired[c.index]=done
                done.forEach{live.remove(it)}
                val used=live.values.toSet()
                val slot=(0 until count).firstOrNull{it !in used}?:count++
                slots[c.index]=slot
                live[c.index]=slot
            }
            return CompositionSlotPlan(slots,retired,count)
        }
    }
}
