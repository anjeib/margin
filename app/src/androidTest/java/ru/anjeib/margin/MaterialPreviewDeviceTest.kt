package ru.anjeib.margin

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.anjeib.margin.data.PageMetadata

@RunWith(AndroidJUnit4::class)
class MaterialPreviewDeviceTest {
    @Test fun readsOpenGraphInEitherAttributeOrderAndResolvesImages() {
        val meta = PageMetadata.parse("""<meta content='A &amp; B' property='og:title'>
            <meta property="og:site_name" content="Example">
            <meta property='og:image' content='/image.jpg'>""", "https://example.com/story")
        assertEquals("A & B", meta.title)
        assertEquals("Example", meta.site)
        assertEquals("https://example.com/image.jpg", meta.image)
    }
    @Test fun fallsBackToTitleAndRejectsNonWebImages() {
        val meta = PageMetadata.parse("""<title>Readable title</title><meta property='og:image' content='file:///secret'><meta name='empty' content=''>""", "https://example.com")
        assertEquals("Readable title", meta.title)
        assertNull(meta.image)
    }
}
