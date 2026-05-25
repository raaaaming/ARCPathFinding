package kr.raaaaming.arcpathfinding.command

import cc.arccore.api.command.ARCCommand
import cc.arccore.api.command.CommandContext
import cc.arccore.api.command.CommandResult
import cc.arccore.api.command.CommandSpec
import kr.raaaaming.arcpathfinding.ARCPathFindingModule
import kr.raaaaming.arcpathfinding.ARCPathFindingModule.Companion.INIT_TOTAL_STEPS
import kr.raaaaming.arcpathfinding.config.WorldNavConfig
import kr.raaaaming.arcpathfinding.core.QueryEngineFastSnap
import kr.raaaaming.arcpathfinding.navigation.NavigationManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

@CommandSpec(
    name = "apf",
    description = "ARC PathFinding commands",
    aliases = ["arcpf"],
    permission = "arcpathfinding.use"
)
class APFCommand : ARCCommand {

    override fun execute(context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty()) {
            context.sender.sendMessage("Usage: /apf <status|find|stop|debug|dumpNodes|world>")
            return CommandResult.Success
        }

        return when (context.subCommand) {
            "status"    -> handleStatus(context)
            "world"    -> handleConfig(context)
            "find"      -> handleFind(context)
            "stop"      -> handleStop(context)
            "debug"     -> handleDebug(context)
            "dumpnodes" -> handleDumpNodes(context)
            else -> {
                context.sender.sendMessage("알 수 없는 서브커맨드: ${args[0]}")
                CommandResult.Success
            }
        }
    }

    // /apf config add <world name> — config에 새 월드 추가
    private fun handleConfig(context: CommandContext): CommandResult {
        val args = context.args
        if (args.size < 2) {
            context.sender.sendMessage("사용법: /apf config <add> <world name>")
            return CommandResult.Success
        }
        return when (args[1].lowercase()) {
            "add"    -> handleConfigAdd(context)
            "remove" -> handleConfigRemove(context)
            "list"   -> handleConfigList(context)
            else     -> {
                context.sender.sendMessage("알 수 없는 config 서브커맨드: ${args[1]}")
                CommandResult.Success
            }
        }
    }

    private fun handleConfigAdd(context: CommandContext): CommandResult {
        val args = context.args
        if (args.size < 3) {
            context.sender.sendMessage("사용법: /apf config add <world name> [centerX] [centerZ] [radius]")
            return CommandResult.Success
        }
        val worldName = args[2]
        val sender = context.sender

        val centerX = args.getOrNull(3)?.toIntOrNull() ?: 0
        val centerZ = args.getOrNull(4)?.toIntOrNull() ?: 0
        val radius  = args.getOrNull(5)?.toIntOrNull()?.takeIf { it > 0 } ?: 20

        if (args.getOrNull(3) != null && args[3].toIntOrNull() == null) {
            sender.sendMessage("§c[ARCPathFinding] centerX는 정수로 입력해주세요.")
            return CommandResult.Success
        }
        if (args.getOrNull(4) != null && args[4].toIntOrNull() == null) {
            sender.sendMessage("§c[ARCPathFinding] centerZ는 정수로 입력해주세요.")
            return CommandResult.Success
        }
        if (args.getOrNull(5) != null && (args[5].toIntOrNull() == null || args[5].toInt() <= 0)) {
            sender.sendMessage("§c[ARCPathFinding] radius는 양의 정수로 입력해주세요.")
            return CommandResult.Success
        }

        if (ARCPathFindingModule.worldConfigs.any { it.worldName == worldName }) {
            sender.sendMessage("§c[ARCPathFinding] '${worldName}'은(는) 이미 config에 있습니다.")
            return CommandResult.Success
        }

        val cfg = WorldNavConfig(worldName = worldName, centerX = centerX, centerZ = centerZ, radiusChunks = radius)
        if (!ARCPathFindingModule.addWorldConfig(cfg)) {
            sender.sendMessage("§c[ARCPathFinding] config 저장에 실패했습니다.")
            return CommandResult.Success
        }

        sender.sendMessage("§a[ARCPathFinding] '${worldName}'이(가) config에 추가되었습니다. (centerX=$centerX, centerZ=$centerZ, radius=$radius)")

        if (Bukkit.getWorld(worldName) != null) {
            ARCPathFindingModule.tryStartWorldInit(worldName)
            sender.sendMessage("§e  월드가 로드되어 있어 초기화를 시작합니다.")
        } else {
            sender.sendMessage("§7  월드가 로드되면 자동으로 초기화됩니다.")
        }
        return CommandResult.Success
    }

    private fun handleConfigList(context: CommandContext): CommandResult {
        val configs = ARCPathFindingModule.worldConfigs
        val sender = context.sender
        if (configs.isEmpty()) {
            sender.sendMessage("§7[ARCPathFinding] 설정된 월드가 없습니다.")
            return CommandResult.Success
        }
        sender.sendMessage("§6[ARCPathFinding] 설정된 월드 (${configs.size}개):")
        for (cfg in configs) {
            val loaded = if (Bukkit.getWorld(cfg.worldName) != null) "§a로드됨" else "§7미로드"
            sender.sendMessage("  §f${cfg.worldName} §8| $loaded §8| §7center=(${cfg.centerX},${cfg.centerZ}) radius=${cfg.radiusChunks}")
        }
        return CommandResult.Success
    }

    private fun handleConfigRemove(context: CommandContext): CommandResult {
        val args = context.args
        if (args.size < 3) {
            context.sender.sendMessage("사용법: /apf config remove <world name>")
            return CommandResult.Success
        }
        val worldName = args[2]
        val sender = context.sender

        if (!ARCPathFindingModule.removeWorldConfig(worldName)) {
            sender.sendMessage("§c[ARCPathFinding] '${worldName}'은(는) config에 없습니다.")
            return CommandResult.Success
        }

        sender.sendMessage("§a[ARCPathFinding] '${worldName}'이(가) config에서 제거되었습니다.")
        return CommandResult.Success
    }

    // /apf status — 월드별 초기화 진행률 표시
    private fun handleStatus(context: CommandContext): CommandResult {
        val m = ARCPathFindingModule
        val sender = context.sender
        if (m.worldConfigs.isEmpty()) {
            sender.sendMessage("§7[ARCPathFinding] 설정된 월드가 없습니다. config.yml을 확인하세요.")
            return CommandResult.Success
        }
        sender.sendMessage("§6[ARCPathFinding] 초기화 상태:")
        for (cfg in m.worldConfigs) {
            val progress = m.worldProgress[cfg.worldName]
            val msg = when {
                progress?.failed == true ->
                    "§c  ${cfg.worldName}: 초기화 실패"
                m.engines.containsKey(cfg.worldName) ->
                    "§a  ${cfg.worldName}: 완료 (${progress?.nodeCount ?: 0}개 노드)"
                progress != null ->
                    "§e  ${cfg.worldName}: [${progress.stepIndex}/$INIT_TOTAL_STEPS] ${progress.stepDesc} ${buildProgressBar(progress.stepIndex)}"
                else ->
                    "§7  ${cfg.worldName}: 대기 중"
            }
            sender.sendMessage(msg)
        }
        return CommandResult.Success
    }

    private fun buildProgressBar(current: Int): String {
        val filled = (current.toDouble() / INIT_TOTAL_STEPS * 20).toInt()
        return "§a${"█".repeat(filled)}§8${"░".repeat(20 - filled)}"
    }

    // /apf find <x> <y> <z> — 도착할 때까지 다음 노드에 파티클 반복
    private fun handleFind(context: CommandContext): CommandResult {
        val player = requirePlayer(context) ?: return CommandResult.Success
        val engine = requireEngine(context, player.world.name) ?: return CommandResult.Success
        val (tx, ty, tz) = parseXYZ(context) ?: return CommandResult.Success

        val path = engine.routeFast(
            player.location.blockX, player.location.blockY, player.location.blockZ,
            tx, ty, tz
        )

        if (path.isEmpty()) {
            context.sender.sendMessage("§c[ARCPathFinding] 경로를 찾을 수 없습니다.")
            return CommandResult.Success
        }

        NavigationManager.startNavigation(player.uniqueId, path)
        context.sender.sendMessage("§a[ARCPathFinding] 내비게이션 시작 (${path.size}개 노드). /apf stop 으로 중단.")
        return CommandResult.Success
    }

    // /apf stop — 진행 중인 내비게이션 중단
    private fun handleStop(context: CommandContext): CommandResult {
        val player = requirePlayer(context) ?: return CommandResult.Success
        if (NavigationManager.stopNavigation(player.uniqueId)) {
            context.sender.sendMessage("§e[ARCPathFinding] 내비게이션이 중단되었습니다.")
        } else {
            context.sender.sendMessage("§7[ARCPathFinding] 진행 중인 내비게이션이 없습니다.")
        }
        return CommandResult.Success
    }

    // /apf debug <x> <y> <z> — 좌표 근처 노드 이웃 정보 출력
    private fun handleDebug(context: CommandContext): CommandResult {
        val player = requirePlayer(context) ?: return CommandResult.Success
        val engine = requireEngine(context, player.world.name) ?: return CommandResult.Success
        val spatialIndex = ARCPathFindingModule.spatialIndexes[player.world.name] ?: return CommandResult.Success
        val (x, y, z) = parseXYZ(context) ?: return CommandResult.Success

        val g = engine.g
        val nodeId = spatialIndex.nearestNode(x, y, z)
        if (nodeId < 0) {
            context.sender.sendMessage("§c[ARCPathFinding] ($x, $y, $z) 근처에 노드가 없습니다.")
            return CommandResult.Success
        }

        val nx = g.nodeX(nodeId); val ny = g.nodeY(nodeId); val nz = g.nodeZ(nodeId)
        val s = g.csrOff[nodeId]; val e = g.csrOff[nodeId + 1]
        var flat = 0; var up = 0; var down = 0
        for (i in s until e) {
            val vy = g.nodeY(g.csrTo[i])
            when { vy == ny -> flat++; vy > ny -> up++; else -> down++ }
        }

        context.sender.sendMessage("§7[ARCPathFinding] 노드 id=$nodeId at ($nx,$ny,$nz)")
        context.sender.sendMessage("  이웃: ${e - s}개 (flat=$flat, up=$up, down=$down)")
        return CommandResult.Success
    }

    // /apf dumpNodes — 전체 노드 덤프
    private fun handleDumpNodes(context: CommandContext): CommandResult {
        val player = requirePlayer(context) ?: return CommandResult.Success
        val spatialIndex = ARCPathFindingModule.spatialIndexes[player.world.name] ?: run {
            context.sender.sendMessage("§c[ARCPathFinding] 이 월드는 초기화되지 않았습니다.")
            return CommandResult.Success
        }
        spatialIndex.dumpAllNodes { line -> context.sender.sendMessage(line) }
        return CommandResult.Success
    }

    override fun onTabComplete(context: CommandContext): List<String> {
        val args = context.args
        val last = args.lastOrNull() ?: ""
        val sub  = args.getOrNull(0)?.lowercase() ?: ""
        val sub2 = args.getOrNull(1)?.lowercase() ?: ""

        val xyzSubs = listOf("find", "debug")

        return when (args.size) {
            1 -> listOf("status", "find", "stop", "debug", "dumpNodes", "config")
                .filter { it.startsWith(last, ignoreCase = true) }

            2 -> when (sub) {
                "config" -> listOf("add", "remove", "list").filter { it.startsWith(last, ignoreCase = true) }
                else     -> emptyList()
            }

            3 -> when (sub) {
                "config" -> when (sub2) {
                    "add"    -> Bukkit.getWorlds()
                        .map { it.name }
                        .filter { name -> ARCPathFindingModule.worldConfigs.none { it.worldName == name } }
                        .filter { it.startsWith(last, ignoreCase = true) }
                    "remove" -> ARCPathFindingModule.worldConfigs
                        .map { it.worldName }
                        .filter { it.startsWith(last, ignoreCase = true) }
                    else -> emptyList()
                }
                in xyzSubs -> playerCoord(context) { it.blockX }.filter { it.startsWith(last) }
                else -> emptyList()
            }

            4 -> when (sub) {
                "config"    -> if (sub2 == "add") playerCoord(context) { it.blockX }.filter { it.startsWith(last) } else emptyList()
                in xyzSubs  -> playerCoord(context) { it.blockY }.filter { it.startsWith(last) }
                else        -> emptyList()
            }

            5 -> when (sub) {
                "config"    -> if (sub2 == "add") playerCoord(context) { it.blockZ }.filter { it.startsWith(last) } else emptyList()
                in xyzSubs  -> playerCoord(context) { it.blockZ }.filter { it.startsWith(last) }
                else        -> emptyList()
            }

            6 -> if (sub == "config" && sub2 == "add") listOf("20").filter { it.startsWith(last) } else emptyList()

            else -> emptyList()
        }
    }

    private fun playerCoord(context: CommandContext, coord: (Location) -> Int): List<String> {
        val loc = Bukkit.getPlayer(context.sender.name)?.location ?: return emptyList()
        return listOf(coord(loc).toString())
    }

    private fun requirePlayer(context: CommandContext): Player? {
        val player = Bukkit.getPlayer(context.sender.name)
        if (player == null) context.sender.sendMessage("플레이어만 사용할 수 있는 커맨드입니다.")
        return player
    }

    private fun requireEngine(context: CommandContext, worldName: String): QueryEngineFastSnap? {
        if (ARCPathFindingModule.worldConfigs.none { it.worldName == worldName }) {
            context.sender.sendMessage("§c[ARCPathFinding] 이 월드(${worldName})는 설정되지 않았습니다.")
            return null
        }
        val engine = ARCPathFindingModule.engines[worldName]
        if (engine == null) {
            context.sender.sendMessage("§c[ARCPathFinding] 아직 초기화 중입니다. /apf status 로 확인하세요.")
        }
        return engine
    }

    private fun parseXYZ(context: CommandContext): Triple<Int, Int, Int>? {
        val args = context.args
        if (args.size < 4) {
            context.sender.sendMessage("사용법: /apf ${args.getOrElse(0) { "<sub>" }} <x> <y> <z>")
            return null
        }
        val x = args[1].toIntOrNull()
        val y = args[2].toIntOrNull()
        val z = args[3].toIntOrNull()
        if (x == null || y == null || z == null) {
            context.sender.sendMessage("좌표는 정수로 입력해주세요.")
            return null
        }
        return Triple(x, y, z)
    }
}
