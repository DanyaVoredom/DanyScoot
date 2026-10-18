package ru.dany.scoot.core

enum class Provider(val key: String, val title: String) {
    URENT("urent", "Urent"),
    WHOOSH("whoosh", "Whoosh"),
    YANDEX("yandex", "Yandex Go");

    companion object {
        fun from(value: String?): Provider = entries.firstOrNull { it.key == value } ?: URENT
    }
}

enum class WhooshMode(val key: String, val title: String) {
    OFFLINE("offline", "Offline"),
    ONLINE("online", "Online");

    companion object {
        fun from(value: String?): WhooshMode = entries.firstOrNull { it.key == value } ?: OFFLINE
    }
}
