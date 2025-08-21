![Tide Server Banner](https://github.com/user-attachments/assets/a6cc4eb0-3b6b-486b-8e08-d49c51f791d3)

[![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmvn.devos.one%2Freleases%2Fio%2Fgithub%2Fdockyardmc%2Ftide%2Fmaven-metadata.xml&style=for-the-badge&logo=maven&logoColor=%23FFFFFF&label=Latest%20Version&color=%23afff87)](https://mvn.devos.one/#/releases/io/github/dockyardmc/dockyard)
[![Static Badge](https://img.shields.io/badge/Language-Kotlin-Kotlin?style=for-the-badge&color=%23963cf4)](https://kotlinlang.org/)

[![wakatime](https://wakatime.com/badge/user/7398c6f6-bec2-4b9c-b8b9-578d4a500952/project/d3ab2e30-2512-46ae-a8e5-6655e53da514.svg?style=for-the-badge)](https://wakatime.com/badge/github/DockyardMC/Dockyard)
[![Discord](https://img.shields.io/discord/1242845647892123650?label=Discord%20Server&color=%237289DA&style=for-the-badge&logo=discord&logoColor=%23FFFFFF)](https://discord.gg/SA9nmfMkdc)
[![Static Badge](https://img.shields.io/badge/Donate-Ko--Fi-pink?style=for-the-badge&logo=ko-fi&logoColor=%23FFFFFF&color=%23ff70c8)](https://ko-fi.com/LukynkaCZE)

Tide is a Kotlin Codecs library inspired by Mojang's [DataFixerUpper](https://github.com/Mojang/DataFixerUpper) that
allows for serializing both network and custom objects via use of transcoders (Json, NBT, etc.)

It is purpose built for the [DockyardMC](https://github.com/DockyardMC/Dockyard) project which is reimplementation of
the Minecraft server protocol so **some primitive types like **`Strings`**, **`Lists`** and **`Maps`** are not using the
standard format, but they are following the Minecraft protocol's implementation**

## Installation

<img src="https://cdn.worldvectorlogo.com/logos/kotlin-2.svg" width="16px"></img>
**Kotlin DSL**

```kotlin
repositories {
    maven("https://mvn.devos.one/releases")
}

dependencies {
    implementation("io.github.dockyardmc:tide:<version>")
}
```

---

## Usage

You can create a codec like this:

```kotlin
data class Player(val username: String, val uuid: UUID) {
    companion object {
        // Network serialization into netty ByteBuf
        val STREAM_CODEC = StreamCodec.of(
            StreamCodec.STRING, Player::username,
            StreamCodec.UUID, Player::uuid,
            ::Player
        )

        // Custom serialization into JSON, NBT, etc.
        val CODEC = StructCodec.of(
            "username", Codec.STRING, Player::username,
            "uuid", Codec.UUID, Player::uuid,
            ::Player
        )
    }
}
```

And then you can either use the `STREAM_CODEC` to write to network buffer:
```kotlin
fun writeToNetwork() {
    val buffer = Unpooled.buffer()
    val player = Player("LukynkaCZE", UUID.fromString("0c9151e4-7083-418d-a29c-bbc58f7c741b"))
    Player.STREAM_CODEC.write(buffer, player)
}
```
or use the `CODEC` to write to custom format:
```kotlin
fun writeJson() {
    val player = Player("LukynkaCZE", UUID.randomUUID())
    val json = Player.CODEC.encode(JsonTranscoder, player)
}
```

---

You can include other codecs inside a codec to create more complex types:

```kotlin
data class Bus(val model: String, val driver: Player, val passengers: List<Player>) {
    companion object {
        val STREAM_CODEC = StreamCodec.of(
            StreamCodec.STRING, Bus::model,
            Player.STREAM_CODEC, Bus::driver,
            Player.STREAM_CODEC.list(), Bus::passengers,
            ::Bus
        )

        val CODEC = StructCodec.of(
            "model", Codec.STRING, Bus::model,
            "driver", Player.CODEC, Bus::driver,
            "passengers", Player.CODEC.list(), Bus::passengers,
            ::Bus
        )
    }
}
```
---

You can also easily implement your own Transcoders by implementing the `Transcoder` interface