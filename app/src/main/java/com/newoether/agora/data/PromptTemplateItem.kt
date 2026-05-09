package com.newoether.agora.data

import kotlinx.serialization.Serializable
import java.util.UUID

enum class PromptItemType { CUSTOM, PREDEFINED }

@Serializable
data class PromptTemplateItem(
    val id: String = UUID.randomUUID().toString(),
    val type: PromptItemType,
    val value: String
)

object PredefinedVariables {
    const val TIME = "time"
    const val DATE = "date"
    const val ACTIVE_MEMORY = "active_memory"
    const val MODEL_ID = "model_id"

    val ALL = listOf(TIME, DATE, ACTIVE_MEMORY, MODEL_ID)

    val EXAMPLE_VALUES = mapOf(
        TIME to "14:30:00",
        DATE to "2026-05-10",
        ACTIVE_MEMORY to "[Example memory content]",
        MODEL_ID to "gemini-1.5-flash"
    )

    fun compile(
        items: List<PromptTemplateItem>,
        runtimeValues: Map<String, String>,
        exampleValues: Map<String, String> = EXAMPLE_VALUES
    ): String {
        return items.joinToString("") { item ->
            when (item.type) {
                PromptItemType.CUSTOM -> item.value
                PromptItemType.PREDEFINED -> runtimeValues[item.value]
                    ?: exampleValues[item.value]
                    ?: "{${item.value}}"
            }
        }
    }
}
