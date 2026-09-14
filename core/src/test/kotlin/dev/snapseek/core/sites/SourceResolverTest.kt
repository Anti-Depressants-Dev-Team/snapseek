package dev.snapseek.core.sites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceResolverTest {

    @Test
    fun `pinterest thumbnails resolve to originals with the thumbnail as fallback`() {
        val thumb = "https://i.pinimg.com/236x/ab/cd/ef/abcdef0123456789.jpg"
        val source = PinterestResolver.resolve(thumb, "https://www.pinterest.com/pin/1/")!!
        assertEquals(listOf("https://i.pinimg.com/originals/ab/cd/ef/abcdef0123456789.jpg", thumb), source.candidates)

        val rs = PinterestResolver.resolve("https://i.pinimg.com/140x140_RS/ab/cd/ef/x.png", "")!!
        assertEquals("https://i.pinimg.com/originals/ab/cd/ef/x.png", rs.candidates.first())
    }

    @Test
    fun `pinterest originals and foreign hosts are left alone`() {
        assertNull(PinterestResolver.resolve("https://i.pinimg.com/originals/ab/cd/ef/x.jpg", ""))
        assertNull(PinterestResolver.resolve("https://example.com/236x/ab/cd/ef/x.jpg", ""))
    }

    @Test
    fun `pixiv master renders resolve to img-original jpg then png`() {
        val master = "https://i.pximg.net/c/600x1200_90_webp/img-master/img/2024/01/02/03/04/05/12345_p0_master1200.jpg"
        val source = PixivResolver.resolve(master, "https://www.pixiv.net/artworks/12345")!!
        assertEquals(
            listOf(
                "https://i.pximg.net/img-original/img/2024/01/02/03/04/05/12345_p0.jpg",
                "https://i.pximg.net/img-original/img/2024/01/02/03/04/05/12345_p0.png",
                master,
            ),
            source.candidates,
        )
        assertEquals("https://www.pixiv.net/", source.referer)
    }

    @Test
    fun `pixiv square thumbnails without a c prefix also resolve`() {
        val square = "https://i.pximg.net/img-master/img/2023/12/31/23/59/59/999_p3_square1200.jpg"
        val source = PixivResolver.resolve(square, "")!!
        assertEquals("https://i.pximg.net/img-original/img/2023/12/31/23/59/59/999_p3.jpg", source.candidates.first())
    }

    @Test
    fun `safebooru samples resolve to the post file trying common extensions`() {
        val sample = "https://safebooru.org//samples/4567/sample_0123abcd.jpg?4567"
        val source = SafebooruResolver.resolve(sample, "https://safebooru.org/index.php?page=post")!!
        assertEquals("https://safebooru.org//images/4567/0123abcd.jpg", source.candidates.first())
        assertEquals(sample, source.candidates.last())
        assertEquals(source.candidates.size, source.candidates.distinct().size)
    }

    @Test
    fun `unknown hosts resolve to nothing`() {
        assertNull(PixivResolver.resolve("https://cdn.example.com/a.jpg", ""))
        assertNull(SafebooruResolver.resolve("https://cdn.example.com/a.jpg", ""))
    }
}
