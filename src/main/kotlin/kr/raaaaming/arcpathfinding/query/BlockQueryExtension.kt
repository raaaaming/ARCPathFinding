package kr.raaaaming.arcpathfinding.query

import kr.raaaaming.arcpathfinding.query.impl.BlockQueryImpl
import org.bukkit.World

fun World.getBlockQuery() = BlockQueryImpl(this)
