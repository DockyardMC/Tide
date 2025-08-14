package io.github.dockyardmc.tide

interface Codec<T> {
    fun encode(transcoder: Transcoder<T>)
}