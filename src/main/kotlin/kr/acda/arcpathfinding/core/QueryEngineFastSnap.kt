package kr.acda.arcpathfinding.core

import kr.acda.arcpathfinding.cch.CCHIndex
import kr.acda.arcpathfinding.chunk.ChunkSpatialIndex
import kr.acda.arcpathfinding.graph.NavWorldGraph

class QueryEngineFastSnap(
    override val g: NavWorldGraph,
    override val idx: CCHIndex,
    private val snap: ChunkSpatialIndex
) : QueryEngine(g, idx) {
    fun routeFast(sx: Int, sy: Int, sz: Int, tx: Int, ty: Int, tz: Int) =
        super.routeWithSnap({ x, y, z -> snap.nearestNode(x, y, z) }, sx, sy, sz, tx, ty, tz)
}
