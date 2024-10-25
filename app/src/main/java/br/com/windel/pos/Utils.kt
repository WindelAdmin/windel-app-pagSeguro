package br.com.windel.pos

import java.util.Properties

class Utils {
    fun convertToIntVero(value: String): Int {
        return value.replace(",", "").toInt()
    }

    fun getPropertie(prop: String): String? {
        val properties = Properties()
        val inputStream = javaClass.classLoader?.getResourceAsStream("gradle.properties")
        inputStream?.use { properties.load(it) }
        return properties.getProperty(prop)
    }
}