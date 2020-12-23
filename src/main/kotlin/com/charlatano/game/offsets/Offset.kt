/*
 * Charlatano: Free and open-source (FOSS) cheat for CS:GO/CS:CO
 * Copyright (C) 2017 - Thomas G. P. Nappo, Jonathan Beaudoin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.charlatano.game.offsets

import com.charlatano.utils.extensions.readForced
import com.charlatano.utils.extensions.unsign
import com.sun.jna.Memory
import com.sun.jna.Pointer
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import org.jire.kna.Addressed
import org.jire.kna.attach.AttachedModule
import org.jire.kna.attach.windows.WindowsAttachedModule
import org.jire.kna.attach.windows.WindowsAttachedProcess
import kotlin.reflect.KProperty

class Offset(
	val module: AttachedModule, private val patternOffset: Long, private val addressOffset: Long,
	val read: Boolean, private val subtract: Boolean, private val mask: ByteArray
) : Addressed {
	
	companion object {
		val memoryByModule = Object2ObjectArrayMap<AttachedModule, Memory>()
		
		private fun Offset.cachedMemory(): Memory {
			var memory = memoryByModule[module]
			if (memory == null) {
				memory = Memory(module.size)
				if (module is WindowsAttachedModule) {
					module.readForced(0, memory, module.size.toInt())
				}
				memoryByModule[module] = memory
			}
			return memory
		}
	}
	
	private val memory = cachedMemory()
	
	override val address: Long = run {
		val offset = module.size - mask.size
		val process = module.process as WindowsAttachedProcess
		val readMemory = Memory(4)
		
		var currentAddress = 0L
		while (currentAddress < offset) {
			if (memory.mask(currentAddress, mask)) {
				currentAddress += module.address + patternOffset
				if (read) {
					if (process.readForced(
							currentAddress,
							readMemory,
							4
						) == 0L
					) throw IllegalStateException("Couldn't resolve currentAddress pointer")
					currentAddress = readMemory.getInt(0).unsign()
				}
				if (subtract) currentAddress -= module.address
				return@run currentAddress + addressOffset
			}
			currentAddress++
		}
		
		//IllegalStateException("Failed to resolve offset, module=$module, memory=$memory, read=$read, subtract=$subtract, currentAddress=$currentAddress").printStackTrace()
		return@run -1L
	}
	
	private var value = -1L
	
	operator fun getValue(thisRef: Any?, property: KProperty<*>): Long {
		if (value == -1L)
			value = address
		return value
	}
	
	operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
		this.value = value
	}
	
}

fun Pointer.mask(offset: Long, mask: ByteArray, skipZero: Boolean = true): Boolean {
	for (i in 0..mask.lastIndex) {
		val value = mask[i]
		if (skipZero && 0 == value.toInt()) continue
		if (value != getByte(offset + i))
			return false
	}
	return true
}