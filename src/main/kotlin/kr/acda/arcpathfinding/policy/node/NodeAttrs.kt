package kr.acda.arcpathfinding.policy.node

import kr.acda.arcpathfinding.graph.NavWorldGraph
import kr.acda.arcpathfinding.query.BlockQuery

data class NodeAttrs(
    val isLadder: BooleanArray,
    val danger: IntArray
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as NodeAttrs

		if (!isLadder.contentEquals(other.isLadder)) return false
		if (!danger.contentEquals(other.danger)) return false

		return true
	}

	override fun hashCode(): Int {
		var result = isLadder.contentHashCode()
		result = 31 * result + danger.contentHashCode()
		return result
	}
}

fun buildNodeAttrs(g: NavWorldGraph, bq: BlockQuery): NodeAttrs {
    val n = g.nodeCount
    val isLadder = BooleanArray(n)
    val danger = IntArray(n)

    for (u in 0 until n) {
        val x = g.nodeX(u); val y = g.nodeY(u); val z = g.nodeZ(u)
        isLadder[u] = bq.ladder(x, y, z)

        var d = 0
        for (dx in -1..1) for (dz in -1..1) {
            if (bq.liquid(x + dx, y - 1, z + dz)) d += 20
        }
        danger[u] = d
    }

    return NodeAttrs(isLadder, danger)
}
