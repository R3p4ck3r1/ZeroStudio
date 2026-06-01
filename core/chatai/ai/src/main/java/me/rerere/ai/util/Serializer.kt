package me.rerere.ai.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import kotlin.time.Instant as KotlinInstant

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val isoString = decoder.decodeString()
        return Instant.parse(isoString)
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        val isoString = value.toString()
        encoder.encodeString(isoString)
    }
}

object KotlinInstantSerializer : KSerializer<KotlinInstant> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("KotlinInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): KotlinInstant {
        val isoString = decoder.decodeString()
        return KotlinInstant.parse(isoString)
    }

    override fun serialize(encoder: Encoder, value: KotlinInstant) {
        val isoString = value.toString()
        encoder.encodeString(isoString)
    }
}
