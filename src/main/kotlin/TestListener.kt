import cc.arccore.api.annotation.ARCListener
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

@ARCListener
class TestListener(val testService: TestService) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player.sendMessage("§a[TestModule] Welcome, ${event.player.name}!")
        event.player.sendMessage(testService.greet(event.player.name))
    }
}
