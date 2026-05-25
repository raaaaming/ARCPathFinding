package kr.raaaaming.arcpathfinding.chunk

data class ChunkSig(
    val cx: Int,
    val cz: Int,
    val yMin: Int,
    val yMax: Int,
    val policyHash: Int,
    val standableCrc: Int
)
