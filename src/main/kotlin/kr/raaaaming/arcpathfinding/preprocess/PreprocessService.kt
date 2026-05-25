package kr.raaaaming.arcpathfinding.preprocess

import kr.raaaaming.arcpathfinding.chunk.ChunkPos
import kr.raaaaming.arcpathfinding.chunk.ChunkSig
import kr.raaaaming.arcpathfinding.chunk.ChunkSigCache
import kr.raaaaming.arcpathfinding.chunk.NavChunk
import kr.raaaaming.arcpathfinding.chunk.NavChunkBuilder
import kr.raaaaming.arcpathfinding.policy.MovePolicy
import kr.raaaaming.arcpathfinding.query.BlockQuery
import kotlin.math.abs

class PreprocessService(
    private val bq: BlockQuery,
    private val policy: MovePolicy
) {
    fun preprocessRadius(
        x0: Int, z0: Int, radiusChunks: Int,
        yMin: Int, yMax: Int,
        existing: ChunkSigCache
    ): List<NavChunk> {
        val out = ArrayList<NavChunk>()
        val c0x = Math.floorDiv(x0, 16); val c0z = Math.floorDiv(z0, 16)
        val policyHash = policy.hash()

        fun rebuildIfChanged(cx: Int, cz: Int) {
            val pos = ChunkPos(cx, cz)
            val mask = NavChunkBuilder.computeStandableMask(bq, pos, yMin, yMax)
            val crc = mask.crc32()
            val cur = ChunkSig(cx, cz, yMin, yMax, policyHash, crc)
            val old = existing.get(cx, cz)
            if (old == null || old != cur) {
                val ch = NavChunkBuilder.buildFromMask(bq, pos, mask, policy)
                out.add(ch)
                existing.put(cur)
            }
        }

        var r = 0
        while (r <= radiusChunks) {
            var x = -r
            while (x <= r) {
                val cx = c0x + x
                val northZ = c0z - r; val southZ = c0z + r
                if (abs(cx - c0x) <= r) {
                    if (r == 0) {
                        rebuildIfChanged(cx, northZ)
                    } else {
                        rebuildIfChanged(cx, northZ); rebuildIfChanged(cx, southZ)
                    }
                }
                x++
            }
            if (r > 0) {
                var z = -r + 1
                while (z <= r - 1) {
                    val cz = c0z + z
                    rebuildIfChanged(c0x - r, cz); rebuildIfChanged(c0x + r, cz)
                    z++
                }
            }
            r++
        }
        return out
    }
}
