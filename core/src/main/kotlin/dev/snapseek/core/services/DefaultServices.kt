package dev.snapseek.core.services

import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import dev.snapseek.core.model.ServiceRegion

/**
 * The sites SnapSeek ships with: the Electron app's seven plus Danbooru's safe mirror. Every one of them now
 * browses natively; the card keeps "Open website instead" for the old experience.
 */
object DefaultServices {
    val pinterest = Service(
        id = "pinterest",
        name = "Pinterest",
        url = "https://ru.pinterest.com/",
        icon = "pinterest",
        kind = ServiceKind.PINTEREST,
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
        Service("danbooru_safe", "Safebooru (Danbooru)", "https://safebooru.donmai.us/", icon = "danbooru", kind = ServiceKind.DANBOORU),
        Service("pixiv", "Pixiv", "https://www.pixiv.net/", icon = "pixiv", kind = ServiceKind.PIXIV),
        Service("deviantart", "DeviantArt", "https://www.deviantart.com/", icon = "deviantart", kind = ServiceKind.DEVIANTART),
        Service("giphy", "Giphy", "https://giphy.com/", icon = "giphy", kind = ServiceKind.GIPHY),
        Service("tenor", "Tenor", "https://tenor.com/", icon = "tenor", kind = ServiceKind.TENOR),
        Service("wallpapers", "Wallpapers", "https://wallpapers.com/{q}", icon = "wallpapers", kind = ServiceKind.WEB_GRID),
    )

    val byId: Map<String, Service> = all.associateBy { it.id }
}
