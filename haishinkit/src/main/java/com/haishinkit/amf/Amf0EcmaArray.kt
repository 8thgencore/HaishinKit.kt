package com.haishinkit.amf

/**
 * An object that represents the AMF0 ECMA Array type.
 * 2.10 ECMA Array Type.
 */
class Amf0EcmaArray(list: List<Any?>? = null) : Iterable<String> {
    val size: Int
        get() {
            return properties.keys.filter {
                try {
                    it.toInt()
                    true
                } catch (e: NumberFormatException) {
                    false
                }
            }.size
        }

    private val properties = mutableMapOf<String, Any?>()

    init {
        list?.forEachIndexed { index, any ->
            properties[index.toString()] = any
        }
    }

    operator fun get(k: String): Any? {
        return properties[k]
    }

    operator fun set(k: String, v: Any?) {
        properties[k] = v
    }

    override fun iterator(): Iterator<String> {
        return properties.keys.iterator()
    }

    override fun toString(): String {
        return "Amf0EcmaArray{${properties}}"
    }
}
