package io.github.dockyardmc.tide

import kotlin.reflect.KClass

abstract class PrimitiveCodec<T : Any>(override val type: KClass<T>) : Codec<T>