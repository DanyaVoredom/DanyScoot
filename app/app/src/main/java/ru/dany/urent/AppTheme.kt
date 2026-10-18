package ru.dany.urent

import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import ru.dany.urent.core.Prefs

enum class AppTheme(
    val id: String,
    @param:StringRes val labelRes: Int,
    @param:StyleRes val styleRes: Int,
) {
    BLACK("black", R.string.theme_black, R.style.Theme_DanyUrent_Black),
    WHITE("white", R.string.theme_white, R.style.Theme_DanyUrent_White),
    LIGHT("light", R.string.theme_light, R.style.Theme_DanyUrent_Light),
    DARK("dark", R.string.theme_dark, R.style.Theme_DanyUrent_Dark),
    AMOLED("amoled", R.string.theme_amoled, R.style.Theme_DanyUrent_Amoled);

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: DARK

        fun current(context: Context): AppTheme = fromId(Prefs.get(context).theme)
    }
}
