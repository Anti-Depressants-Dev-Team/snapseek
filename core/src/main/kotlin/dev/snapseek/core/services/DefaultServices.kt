package dev.snapseek.core.services

import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import dev.snapseek.core.model.ServiceRegion

/** The sites SnapSeek ships with. Same set and URLs as the Electron app, so existing users see nothing move. */
object DefaultServices {
    val pinterest = Service(
        id = "pinterest",
        name = "Pinterest",
        url = "https://ru.pinterest.com/",
        icon = "pinterest",
        regions = listOf(
            ServiceRegion("ru", "Russia (ru)", "https://ru.pinterest.com/"),
            ServiceRegion("www", "Global (www)", "https://www.pinterest.com/"),
            ServiceRegion("fr", "France (fr)", "https://www.pinterest.fr/"),
            ServiceRegion("de", "Germany (de)", "https://www.pinterest.de/"),
            ServiceRegion("jp", "Japan (jp)", "https://www.pinterest.jp/"),
            ServiceRegion("uk", "UK (co.uk)", "https://www.pinterest.co.uk/"),
        ),
    )

    val all: List<Service> = listOf(
        pinterest,
        Service("safebooru", "Safebooru", "https://safebooru.org/", icon = "safebooru", kind = ServiceKind.GELBOORU_V2),
        Service("pixiv", "Pixiv", "https://www.pixiv.net/", icon = "pixiv"),
        Service("deviantart", "DeviantArt", "https://www.deviantart.com/", icon = "deviantart"),
        Service("giphy", "Giphy", "https://giphy.com/", icon = "giphy"),
        Service("tenor", "Tenor", "https://tenor.com/", icon = "tenor"),
        Service("wallpapers", "Wallpapers", "https://wallpapers.com/", icon = "wallpapers"),
    )

    val byId: Map<String, Service> = all.associateBy { it.id }
}
