package kr.acda.arcpathfinding.query

import kr.acda.arcpathfinding.query.impl.BlockQueryImpl
import org.bukkit.World

fun World.getBlockQuery() = BlockQueryImpl(this)
