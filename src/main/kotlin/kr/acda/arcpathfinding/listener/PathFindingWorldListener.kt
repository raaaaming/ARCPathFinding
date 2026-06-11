package kr.acda.arcpathfinding.listener

import cc.arccore.api.annotation.ARCListener
import kr.acda.arcpathfinding.ARCPathFindingModule
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent

@ARCListener
class PathFindingWorldListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldLoad(event: WorldLoadEvent) {
        val worldName = event.world.name
        if (ARCPathFindingModule.worldConfigs.none { it.worldName == worldName }) return
        ARCPathFindingModule.tryStartWorldInit(worldName)
    }
}
