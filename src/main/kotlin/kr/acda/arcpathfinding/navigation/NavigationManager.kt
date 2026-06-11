package kr.acda.arcpathfinding.navigation

import kr.acda.arccore.runtime.context.RuntimeModuleContext
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NavigationManager {

    data class NavSession(
        val path: List<IntArray>,
        @Volatile var targetIdx: Int = 0
    )

    private val sessions = ConcurrentHashMap<UUID, NavSession>()
    private val dustOptions = Particle.DustOptions(Color.LIME, 1.5f)

    fun init(ctx: RuntimeModuleContext) {
        // 4 ticks = 200ms
        val handle = ctx.scheduler.runAsyncRepeating(0L, 4L) { tick() }
        ctx.cleanupScope.onClose {
            handle.cancel()
            sessions.clear()
        }
    }

    fun startNavigation(uuid: UUID, path: List<IntArray>) {
        sessions[uuid] = NavSession(path)
    }

    fun stopNavigation(uuid: UUID): Boolean = sessions.remove(uuid) != null

    fun isNavigating(uuid: UUID) = sessions.containsKey(uuid)

    private fun tick() {
        if (sessions.isEmpty()) return
        val toRemove = mutableListOf<UUID>()

        for ((uuid, session) in sessions) {
            val player = Bukkit.getPlayer(uuid) ?: run { toRemove += uuid; continue }

            while (session.targetIdx < session.path.size) {
                val t = session.path[session.targetIdx]
                val dx = player.location.x - (t[0] + 0.5)
                val dz = player.location.z - (t[2] + 0.5)
                if (dx * dx + dz * dz < 2.25) {
                    session.targetIdx++
                } else break
            }

            if (session.targetIdx >= session.path.size) {
                player.sendMessage("§a[ARCPathFinding] 목적지에 도착했습니다!")
                toRemove += uuid
                continue
            }

            val t = session.path[session.targetIdx]
            val world = player.world
            val x = t[0] + 0.5
            val z = t[2] + 0.5
            for (dy in 0..2) {
                world.spawnParticle(Particle.DUST, x, t[1] + dy.toDouble(), z, 2, 0.1, 0.0, 0.1, 0.0, dustOptions)
            }
        }

        toRemove.forEach { sessions.remove(it) }
    }
}
