package me.rerere.rikkahub.data.db

import androidx.room.TypeConverter
import me.rerere.ai.core.TokenUsage
import me.rerere.rikkahub.utils.JsonInstant

class TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String {
        return JsonInstant.encodeToString(usage)
    }

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? {
        return JsonInstant.decodeFromString(usage)
    }
}
