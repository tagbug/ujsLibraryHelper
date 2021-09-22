package com.tagbug.ujslibraryhelper.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*

object ObjectToBase64 {
    fun encode(obj: Any?): String? {
        if (obj == null) {
            return null
        }
        val byteOutput = ByteArrayOutputStream()
        val objOutput = ObjectOutputStream(byteOutput)
        objOutput.writeObject(obj)
        val encodedBytes = Base64.getEncoder().encode(byteOutput.toByteArray())
        return String(encodedBytes)
    }

    fun decode(base64Str: String?): Any? {
        if (base64Str == null || base64Str.isEmpty()) {
            return null
        }
        val decodedBytes = Base64.getDecoder().decode(base64Str)
        val byteInput = ByteArrayInputStream(decodedBytes)
        val objInput = ObjectInputStream(byteInput)
        return objInput.readObject()
    }
}