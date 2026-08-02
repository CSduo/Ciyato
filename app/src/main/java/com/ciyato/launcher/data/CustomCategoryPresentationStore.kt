package com.ciyato.launcher.data

import org.json.JSONObject

/** Version-tolerant JSON helpers for persisted custom category presentation. */
object CustomCategoryPresentationStore {
    fun presentationFor(raw: String, name: String): CustomCategoryPresentation =
        runCatching {
            CustomCategoryPresentation.valueOf(
                jsonObject(raw).optString(name, CustomCategoryPresentation.GROUP.name),
            )
        }.getOrDefault(CustomCategoryPresentation.GROUP)

    fun update(raw: String, name: String, presentation: CustomCategoryPresentation): String =
        jsonObject(raw).apply { put(name, presentation.name) }.toString()

    fun rename(raw: String, source: String, destination: String): String {
        val json = jsonObject(raw)
        val presentation = presentationFor(raw, source)
        json.remove(source)
        json.put(destination, presentation.name)
        return json.toString()
    }

    fun remove(raw: String, name: String): String =
        jsonObject(raw).apply { remove(name) }.toString()

    private fun jsonObject(raw: String): JSONObject =
        runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
}
