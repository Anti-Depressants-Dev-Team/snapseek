package dev.snapseek.core.sites

import dev.snapseek.core.download.ImageSource

/**
 * Turns the URL a page happens to display into the URL of the best available file.
 * Return null when the URL isn't yours; the pipeline then downloads exactly what the page showed.
 * Always put the page's own URL last in the candidate list so a broken rule degrades, never fails.
 */
fun interface SourceResolver {
    fun resolve(imageUrl: String, pageUrl: String): ImageSource?
}

/** i.pinimg.com/236x/... and friends are resized copies; /originals/ has the upload. */
object PinterestResolver : SourceResolver {
    private val sized = Regex("""^(https?://i\.pinimg\.com)/\d+x(?:\d+)?(?:_[A-Za-z0-9]+)?/(.+)$""")

    override fun resolve(imageUrl: String, pageUrl: String): ImageSource? {
        val m = sized.matchEntire(imageUrl) ?: return null
        return ImageSource(listOf("${m.groupValues[1]}/originals/${m.groupValues[2]}", imageUrl))
    }
}

/**
 * Pixiv serves "master" renders capped at 1200px. The original lives under img-original with the same
 * path but no size suffix, and may be .jpg or .png; we don't know which, so we try both.
 */
object PixivResolver : SourceResolver {
    private val master = Regex(
        """^(https?://i\.pximg\.net)/(?:c/[^/]+/)?(?:img-master|custom-thumb)/(img/\d{4}/\d{2}/\d{2}/\d{2}/\d{2}/\d{2}/\d+_p\d+)_(?:master|square|custom)\d+\.\w+$""",
    )

    override fun resolve(imageUrl: String, pageUrl: String): ImageSource? {
        val m = master.matchEntire(imageUrl) ?: return null
        val base = "${m.groupValues[1]}/img-original/${m.groupValues[2]}"
        return ImageSource(
            candidates = listOf("$base.jpg", "$base.png", imageUrl),
            referer = "https://www.pixiv.net/",
        )
    }
}

/** safebooru.org//samples/1234/sample_<hash>.jpg is a downscale; //images/1234/<hash>.<ext> is the post file. */
object SafebooruResolver : SourceResolver {
    private val sample = Regex(
        """^(https?://safebooru\.org)/+(?:samples|thumbnails)/(\d+)/(?:sample|thumbnail)_([0-9a-f]+)\.(\w+)(?:\?.*)?$""",
    )

    override fun resolve(imageUrl: String, pageUrl: String): ImageSource? {
        val m = sample.matchEntire(imageUrl) ?: return null
        val (origin, dir, hash, ext) = m.destructured
        val base = "$origin//images/$dir/$hash"
        val exts = listOf(ext, "jpg", "png", "jpeg", "gif").distinct()
        return ImageSource(exts.map { "$base.$it" } + imageUrl)
    }
}
