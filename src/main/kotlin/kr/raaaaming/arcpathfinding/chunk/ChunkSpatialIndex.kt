package kr.raaaaming.arcpathfinding.chunk

import kr.raaaaming.arcpathfinding.graph.NavWorldGraph
import java.lang.Math.floorDiv

class ChunkSpatialIndex(
    private val g: NavWorldGraph,
    private val chunkSize: Int = 16
) {
    private val map = HashMap<ChunkKey, IntArray>()

    init {
        val buckets = HashMap<ChunkKey, MutableList<Int>>()
        for (u in 0 until g.nodeCount) {
            val cx = floorDiv(g.nodeX(u), chunkSize)
            val cz = floorDiv(g.nodeZ(u), chunkSize)
            buckets.computeIfAbsent(ChunkKey(cx, cz)) { mutableListOf() }.add(u)
        }
        for ((k, lst) in buckets) map[k] = lst.toIntArray()
    }

    fun nearestNode(x: Int, y: Int, z: Int, maxRadius: Int = 3): Int {
        if (map.isEmpty()) return -1
        val cx0 = floorDiv(x, chunkSize); val cz0 = floorDiv(z, chunkSize)
        var best = -1; var bestD = Long.MAX_VALUE

        fun consider(nodeId: Int) {
            val dx = (g.nodeX(nodeId) - x).toLong()
            val dy = (g.nodeY(nodeId) - y).toLong()
            val dz = (g.nodeZ(nodeId) - z).toLong()
            val d2 = dx * dx + dy * dy + dz * dz
            if (d2 < bestD) { bestD = d2; best = nodeId }
        }

        fun scanChunk(cx: Int, cz: Int) {
            val arr = map[ChunkKey(cx, cz)] ?: return
            for (u in arr) consider(u)
        }

        var r = 0
        while (r <= maxRadius) {
            for (cx in cx0 - r..cx0 + r) {
                scanChunk(cx, cz0 - r)
                if (r > 0) scanChunk(cx, cz0 + r)
            }
            if (r > 0) {
                for (cz in cz0 - r + 1..<cz0 + r) {
                    scanChunk(cx0 - r, cz)
                    scanChunk(cx0 + r, cz)
                }
            }
            if (best != -1) return best
            r++
        }

        for (u in 0 until g.nodeCount) consider(u)
        return best
    }

    fun dumpAllNodes(emit: (String) -> Unit = ::println): List<String> {
        val lines = ArrayList<String>()
        var total = 0

        if (map.isEmpty()) {
            lines += "ChunkSpatialIndex: no nodes stored"
        } else {
            val chunks = map.entries.sortedWith(compareBy({ it.key.cx }, { it.key.cz }))
            for ((key, nodes) in chunks) {
                lines += "chunk[${key.cx},${key.cz}] (${nodes.size} nodes)"
                val sorted = nodes.sortedArray()
                for (nodeId in sorted) {
                    val x = g.nodeX(nodeId); val y = g.nodeY(nodeId); val z = g.nodeZ(nodeId)
                    lines += "  id=$nodeId -> ($x,$y,$z)"
                }
                total += nodes.size
            }
        }

        lines += "total nodes: $total across ${map.size} chunks"
        lines.forEach(emit)
        return lines
    }
}
