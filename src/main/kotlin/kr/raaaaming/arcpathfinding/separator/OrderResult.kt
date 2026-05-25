package kr.raaaaming.arcpathfinding.separator

data class OrderResult(val order: IntArray, val level: IntArray) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as OrderResult

		if (!order.contentEquals(other.order)) return false
		if (!level.contentEquals(other.level)) return false

		return true
	}

	override fun hashCode(): Int {
		var result = order.contentHashCode()
		result = 31 * result + level.contentHashCode()
		return result
	}
}
