package kr.raaaaming.arcpathfinding.config

data class WorldNavConfig(
    val worldName: String,
    val centerX: Int,
    val centerZ: Int,
    val radiusChunks: Int
)

class WorldProgress {
    @Volatile var stepIndex: Int = 0
    @Volatile var stepDesc: String = "대기 중"
    @Volatile var failed: Boolean = false
    @Volatile var nodeCount: Int = 0
}
