package kr.acda.arcpathfinding.chunk

data class NavChunk(
    val pos: ChunkPos,
    val coords: IntArray,
    val adjOff: IntArray,
    val adjTo: IntArray,
    val adjTag: ShortArray,
    val gateN: IntArray,
    val gateS: IntArray,
    val gateW: IntArray,
    val gateE: IntArray,
    val standableCrc: Int
) {
    val nodeCount get() = coords.size / 3
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as NavChunk

		if (standableCrc != other.standableCrc) return false
		if (pos != other.pos) return false
		if (!coords.contentEquals(other.coords)) return false
		if (!adjOff.contentEquals(other.adjOff)) return false
		if (!adjTo.contentEquals(other.adjTo)) return false
		if (!adjTag.contentEquals(other.adjTag)) return false
		if (!gateN.contentEquals(other.gateN)) return false
		if (!gateS.contentEquals(other.gateS)) return false
		if (!gateW.contentEquals(other.gateW)) return false
		if (!gateE.contentEquals(other.gateE)) return false
		if (nodeCount != other.nodeCount) return false

		return true
	}

	override fun hashCode(): Int {
		var result = standableCrc
		result = 31 * result + pos.hashCode()
		result = 31 * result + coords.contentHashCode()
		result = 31 * result + adjOff.contentHashCode()
		result = 31 * result + adjTo.contentHashCode()
		result = 31 * result + adjTag.contentHashCode()
		result = 31 * result + gateN.contentHashCode()
		result = 31 * result + gateS.contentHashCode()
		result = 31 * result + gateW.contentHashCode()
		result = 31 * result + gateE.contentHashCode()
		result = 31 * result + nodeCount
		return result
	}
}
