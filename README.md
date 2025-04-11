![Tide Server Banner](https://github.com/user-attachments/assets/a6cc4eb0-3b6b-486b-8e08-d49c51f791d3)

[![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmvn.devos.one%2Freleases%2Fio%2Fgithub%2Fdockyardmc%2Ftide%2Fmaven-metadata.xml&style=for-the-badge&logo=maven&logoColor=%23FFFFFF&label=Latest%20Version&color=%23afff87)](https://mvn.devos.one/#/releases/io/github/dockyardmc/dockyard)
[![Static Badge](https://img.shields.io/badge/Language-Kotlin-Kotlin?style=for-the-badge&color=%23963cf4)](https://kotlinlang.org/)

[![wakatime](https://wakatime.com/badge/user/7398c6f6-bec2-4b9c-b8b9-578d4a500952/project/d3ab2e30-2512-46ae-a8e5-6655e53da514.svg?style=for-the-badge)](https://wakatime.com/badge/github/DockyardMC/Dockyard)
[![Discord](https://img.shields.io/discord/1242845647892123650?label=Discord%20Server&color=%237289DA&style=for-the-badge&logo=discord&logoColor=%23FFFFFF)](https://discord.gg/SA9nmfMkdc)
[![Static Badge](https://img.shields.io/badge/Donate-Ko--Fi-pink?style=for-the-badge&logo=ko-fi&logoColor=%23FFFFFF&color=%23ff70c8)](https://ko-fi.com/LukynkaCZE)

Tide is a Kotlin codec library that allows for serializing to network protocol and JSON while allowing other formats to be integrated via use custom Transcoders. 

It is purpose built for the [DockyardMC](https://github.com/DockyardMC/Dockyard) project which is reimplementation of the Minecraft server protocol so **some primitive types like **`Strings`**, **`Lists`** and **`Maps`** are not using the standard format, but they are following the Minecraft protocol's implementation**

## Installation

<img src="https://cdn.worldvectorlogo.com/logos/kotlin-2.svg" width="16px"></img>
**Kotlin DSL**
```kotlin
repositories {
    maven {
        name = "devOS"
        url = uri("https://mvn.devos.one/releases")
    }
}

dependencies {
    implementation("io.github.dockyardmc:tide:1.0")
}
```
---

## Usage

You can create a codec like this:
```kotlin
data class Person(val name: String, val age: Int) {
    companion object {
        val codec = Codec.of<Person> {
            field("name", Primitives.String, Person::name)
            field("age", Primitives.Int, Person::age)
        }
    }
}
```
You can either serialize it into a network type by calling the `Codec#writeNetwork` or into json by calling `Codec#writeJson`

---

You can include other codecs inside a codec to create more complex types:

```kotlin
data class Bus(val model: String, val driver: Person, val passengers: List<Person>) {
    companion object {
        val codec = Codec.of<Bus> {
            field("name", Primitives.String, Bus::model)
            field("driver", Person.codec, Bus::driver)
            field("passengers", Person.codec.list(), Bus::passengers)
        }
    }
}
```

---

To use lists you can simply call `Codec#list` on existing codec like this:

```kotlin
data class Book(val pages: List<String>) {
    companion object {
        val codec = Codec.of<Book> {
            field("pages", Primitives.String.list(), Book::pages)
        }
    }
}
```

---

To use maps, you can either use `Codec#mapAsKeyTo` which will create map of the current codec as key and provided codec as value or Codec#mapAsValueTo which will use the current coded as value and provided coded as key:

```kotlin
data class WarehouseInventory(val iceCreamFlavours: Map<String, Int>, val cookieFlavours: Map<String, Int>) {
    companion object {
        val codec = Codec.of<WarehouseInventory> {
            field("ice_cream_flavours", Primitives.String.mapAsKeyTo(Primitives.VarInt), WarehouseInventory::iceCreamFlavours) //uses current as key of the map 
            field("cookie_flavours", Primitives.Int.mapAsValueTo(Primitives.String), WarehouseInventory::cookieFlavours) // uses current as value of the map
        }
    }
}
```

---

Optionals are Minecraft protocol specific type. They represent the Java `Optional<T>` class. Optionals in protocol consist of a `boolean` field which indicates if the value is present or not and the actual value. To make things simple, Tide returns **nullable values** instead of the java `Optional<T>` classes

You can use the `Codec#optional` to make the field optional:

```kotlin
data class Book(val pages: List<String>, val synopsis: String?) {
    companion object {
        val codec = Codec.of<Book> {
            field("pages", Primitives.String.list(), Book::pages)
            field("synopsis", Primitives.String.optional(), Book::synopsis)
        }
    }
}
```

---

Enums are a special case because in the Minecraft protocol they are represented by VarInt type which is a variable length integer. The value is the ordinal (the index in the entries) of the enum value

You will need to use `Codec#enum` to create this codec which has a class field to provide the class of the enum for inner reflection shenanigans:

```kotlin
data class Book(val pages: List<String>, val synopsis: String?, val type: Book.Type) {

    enum class Type {
        HORROR,
        SCI_FI,
        MEDIEVAL,
        FANTASY,
        FICTION,
        ADVENTURE,
        THRILLER,
        MANGA,
        HENTAI
    }

    companion object {
        val codec = Codec.of<Book> {
            field("pages", Primitives.String.list(), Book::pages)
            field("synopsis", Primitives.String.optional(), Book::synopsis)
            field("type", Codec.enum(Book.Type::class), Book::type)
        }
    }
}
```


