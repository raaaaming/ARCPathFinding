package kr.raaaaming.arcpathfinding.graph

class NavWorldGraph(
    val nodeCount: Int,
    val xyzt: IntArray,
    val chunkOf: IntArray,
    val csrOff: IntArray,
    val csrTo: IntArray
) {
    fun nodeX(i: Int) = xyzt[i * 3]
    fun nodeY(i: Int) = xyzt[i * 3 + 1]
    fun nodeZ(i: Int) = xyzt[i * 3 + 2]
}
