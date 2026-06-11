package kr.acda.arcpathfinding

import kr.acda.arccore.api.module.BaseModule
import kr.acda.arccore.api.module.ModuleSpec
import kr.acda.arccore.runtime.context.RuntimeModuleContext
import kr.acda.arcpathfinding.cch.contractUltra
import kr.acda.arcpathfinding.cch.customizeWeightsPerfectFast
import kr.acda.arcpathfinding.chunk.ChunkSigCache
import kr.acda.arcpathfinding.chunk.ChunkSpatialIndex
import kr.acda.arcpathfinding.config.WorldNavConfig
import kr.acda.arcpathfinding.config.WorldProgress
import kr.acda.arcpathfinding.core.QueryEngineFastSnap
import kr.acda.arcpathfinding.graph.mergeChunks
import kr.acda.arcpathfinding.navigation.NavigationManager
import kr.acda.arcpathfinding.path.PathService
import kr.acda.arcpathfinding.path.PathServiceImpl
import kr.acda.arcpathfinding.policy.MovePolicy
import kr.acda.arcpathfinding.policy.WeightPolicy
import kr.acda.arcpathfinding.policy.makeAttrWeightFn
import kr.acda.arcpathfinding.policy.node.buildNodeAttrs
import kr.acda.arcpathfinding.preprocess.PreprocessService
import kr.acda.arcpathfinding.query.getBlockQuery
import kr.acda.arcpathfinding.separator.buildOrderByChunkSeparatorFast
import kr.acda.arcpathfinding.wnm.WNMHeader
import kr.acda.arcpathfinding.wnm.WNMStore
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@ModuleSpec(
    id = "arc-pathfinding", name = "ARC PathFinding", version = "1.0.0",
    description = "Pathfinding module using CCH algorithm",
    authors = ["raaaaming"],
    exports = ["kr.acda.arcpathfinding.path"]
)
class ARCPathFindingModule : BaseModule() {

    companion object {
        val engines = ConcurrentHashMap<String, QueryEngineFastSnap>()
        val spatialIndexes = ConcurrentHashMap<String, ChunkSpatialIndex>()
        val worldProgress = ConcurrentHashMap<String, WorldProgress>()

        @Volatile var worldConfigs: List<WorldNavConfig> = emptyList()
        @Volatile var instance: ARCPathFindingModule? = null
        @Volatile var runtimeCtx: RuntimeModuleContext? = null

        private val initializedWorlds = ConcurrentHashMap.newKeySet<String>()

        val movePolicy = MovePolicy(allowDiag = true, maxDrop = 3, stepUp = 1, forbidCornerClip = true)
        val weightPolicy = WeightPolicy()

        const val Y_MIN = -64
        const val Y_MAX = 320
        const val INIT_TOTAL_STEPS = 9

        val isReady: Boolean get() = engines.isNotEmpty()

        fun removeWorldConfig(worldName: String): Boolean {
            if (worldConfigs.none { it.worldName == worldName }) return false
            val dataFolder = runtimeCtx?.dataFolder ?: return false
            val configFile = dataFolder.resolve("config.yml").toFile()
            val yaml = YamlConfiguration.loadConfiguration(configFile)
            yaml.set("worlds.${worldName}", null)
            yaml.save(configFile)
            worldConfigs = worldConfigs.filter { it.worldName != worldName }
            engines.remove(worldName)
            spatialIndexes.remove(worldName)
            worldProgress.remove(worldName)
            initializedWorlds.remove(worldName)
            return true
        }

        fun addWorldConfig(cfg: WorldNavConfig): Boolean {
            if (worldConfigs.any { it.worldName == cfg.worldName }) return false
            val dataFolder = runtimeCtx?.dataFolder ?: return false
            val configFile = dataFolder.resolve("config.yml").toFile()
            val yaml = YamlConfiguration.loadConfiguration(configFile)
            yaml.set("worlds.${cfg.worldName}.centerX", cfg.centerX)
            yaml.set("worlds.${cfg.worldName}.centerZ", cfg.centerZ)
            yaml.set("worlds.${cfg.worldName}.radius", cfg.radiusChunks)
            yaml.save(configFile)
            worldConfigs = worldConfigs + cfg
            return true
        }

        fun tryStartWorldInit(worldName: String) {
            if (worldConfigs.none { it.worldName == worldName }) return
            if (!initializedWorlds.add(worldName)) return
            val inst = instance ?: return
            val ctx = runtimeCtx ?: return
            val cfg = worldConfigs.find { it.worldName == worldName } ?: return
            inst.startWorldInitThread(ctx, cfg)
        }
    }

    override fun onEnable() {
        val ctx = context as RuntimeModuleContext
        instance = this
        runtimeCtx = ctx

        val dataFolder = ctx.dataFolder
        Files.createDirectories(dataFolder)
        worldConfigs = loadWorldConfigs(dataFolder)



        ctx.services.register(PathService::class, PathServiceImpl())
        logger.info("ARCPathFinding service registered.")

        NavigationManager.init(ctx)

        if (worldConfigs.isEmpty()) {
            logger.warn("No worlds configured. Edit config.yml and restart.")
            return
        }

        worldConfigs.forEach { cfg ->
            if (Bukkit.getWorld(cfg.worldName) != null) tryStartWorldInit(cfg.worldName)
            else logger.info("World '${cfg.worldName}' not loaded yet. Waiting for WorldLoadEvent...")
        }
    }

    internal fun startWorldInitThread(ctx: RuntimeModuleContext, cfg: WorldNavConfig) {
        Thread({
            try {
                initializeWorld(ctx, cfg)
            } catch (e: Exception) {
                worldProgress.getOrPut(cfg.worldName) { WorldProgress() }.failed = true
                logger.warn("ARCPathFinding init failed for '${cfg.worldName}': ${e.message}")
                e.printStackTrace()
            }
        }, "arcpf-init-${cfg.worldName}").start()
    }

    private fun step(worldName: String, index: Int, description: String) {
        val p = worldProgress.getOrPut(worldName) { WorldProgress() }
        p.stepIndex = index
        p.stepDesc = description
        logger.info("[$index/$INIT_TOTAL_STEPS] [$worldName] $description")
    }

    private fun initializeWorld(ctx: RuntimeModuleContext, cfg: WorldNavConfig) {
        val world = Bukkit.getWorld(cfg.worldName)
        if (world == null) {
            logger.warn("World '${cfg.worldName}' not found during initialization.")
            return
        }

        logger.info("Starting ARCPathFinding initialization for world '${cfg.worldName}'...")
        val dataFolder = ctx.dataFolder

        val wnmPath = dataFolder.resolve("${cfg.worldName}.nav.wnm")
        val sigPath = dataFolder.resolve("${cfg.worldName}.nav.sig.gz")
        val store = WNMStore()
        val sigCache = ChunkSigCache()

        step(cfg.worldName, 1, "청크 시그니처 로드")
        if (Files.exists(sigPath)) sigCache.load(sigPath)

        val bq = world.getBlockQuery()
        val preprocessor = PreprocessService(bq, movePolicy)

        step(cfg.worldName, 2, "청크 전처리 (radius=${cfg.radiusChunks})")
        val updatedChunks = preprocessor.preprocessRadius(
            cfg.centerX, cfg.centerZ, cfg.radiusChunks, Y_MIN, Y_MAX, sigCache
        )

        val header = WNMHeader(
            version = 1,
            policyHash = movePolicy.hash(),
            worldUUID = world.uid,
            yMin = Y_MIN,
            yMax = Y_MAX,
            createdAt = System.currentTimeMillis()
        )

        step(cfg.worldName, 3, "WNM 저장 (${updatedChunks.size}개 청크)")
        if (updatedChunks.isNotEmpty()) {
            store.appendOrPatch(wnmPath, header, updatedChunks)
            sigCache.save(sigPath)
        }

        if (!Files.exists(wnmPath)) {
            logger.warn("No WNM data for '${cfg.worldName}'. Pathfinding will not work.")
            return
        }

        step(cfg.worldName, 4, "WNM 데이터 로드")
        val (_, allChunks) = store.readAll(wnmPath)

        step(cfg.worldName, 5, "월드 그래프 병합 (${allChunks.size}개 청크)")
        val graph = mergeChunks(allChunks, movePolicy)

        step(cfg.worldName, 6, "CCH 노드 순서 계산")
        val orderResult = buildOrderByChunkSeparatorFast(graph)

        step(cfg.worldName, 7, "CCH 수축 (${graph.nodeCount}개 노드)")
        val cchIdx = contractUltra(graph, orderResult.order, movePolicy)

        step(cfg.worldName, 8, "엣지 가중치 커스터마이즈")
        val attrs = buildNodeAttrs(graph, bq)
        val weightFn = makeAttrWeightFn(attrs, weightPolicy)
        customizeWeightsPerfectFast(cchIdx, graph, weightFn)

        step(cfg.worldName, 9, "공간 인덱스 구축")
        val snap = ChunkSpatialIndex(graph)

        engines[cfg.worldName] = QueryEngineFastSnap(graph, cchIdx, snap)
        spatialIndexes[cfg.worldName] = snap
        worldProgress.getOrPut(cfg.worldName) { WorldProgress() }.nodeCount = graph.nodeCount

        logger.info("ARCPathFinding initialized for '${cfg.worldName}'. ${graph.nodeCount} nodes ready.")
    }

    override fun onDisable() {
        engines.clear()
        spatialIndexes.clear()
        worldProgress.clear()
        initializedWorlds.clear()
        worldConfigs = emptyList()
        instance = null
        runtimeCtx = null
        logger.info("ARCPathFinding disabled.")
    }

    private fun loadWorldConfigs(dataFolder: Path): List<WorldNavConfig> {
        val configFile = dataFolder.resolve("config.yml").toFile()
        if (!configFile.exists()) saveDefaultConfig(configFile)
        val yaml = YamlConfiguration.loadConfiguration(configFile)
        val worldsSection = yaml.getConfigurationSection("worlds") ?: return emptyList()
        return worldsSection.getKeys(false).mapNotNull { worldName ->
            val sec = worldsSection.getConfigurationSection(worldName) ?: return@mapNotNull null
            WorldNavConfig(
                worldName = worldName,
                centerX = sec.getInt("centerX", 0),
                centerZ = sec.getInt("centerZ", 0),
                radiusChunks = sec.getInt("radius", 20)
            )
        }
    }

    private fun saveDefaultConfig(file: File) {
        file.writeText(
            """
            # ARC PathFinding 설정
            # 각 월드별 경로탐색 범위를 설정합니다.
            worlds:
              world:
                # 탐색 기준 좌표 (블록 단위)
                centerX: 0
                centerZ: 0
                # 전처리 반경 (청크 단위, 예: 20 → 41×41 = 1,681청크)
                radius: 20
            """.trimIndent()
        )
    }
}
