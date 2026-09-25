package com.infusory.modelviewer.filament

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LabelInfo(
    val nodeName: String,
    val text: String
)

object GlbLabelParser {

    private const val GLB_MAGIC = 0x46546C67
    private const val CHUNK_TYPE_JSON = 0x4E4F534A

    fun parseLabels(glb: ByteBuffer): List<LabelInfo> {
        val buf = glb.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        buf.rewind()
        if (buf.remaining() < 20) return emptyList()

        val magic = buf.int
        if (magic != GLB_MAGIC) return emptyList()
        buf.int
        buf.int

        val chunkLength = buf.int
        val chunkType = buf.int
        if (chunkType != CHUNK_TYPE_JSON) return emptyList()
        if (buf.remaining() < chunkLength) return emptyList()

        val jsonBytes = ByteArray(chunkLength)
        buf.get(jsonBytes)
        val json = JSONObject(String(jsonBytes, Charsets.UTF_8))

        val nodes = json.optJSONArray("nodes") ?: return emptyList()
        val result = mutableListOf<LabelInfo>()
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            val name = node.optString("name", null) ?: continue
            val extras = node.optJSONObject("extras") ?: continue
            val prop = extras.optString("prop", null) ?: continue
            if (prop.isNotBlank()) {
                result.add(LabelInfo(nodeName = name, text = prop))
            }
        }
        return result
    }
}
