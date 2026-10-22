package com.moviecatalog.gateway

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The hand-written JSON reader and writer of the multipart route (S-303). */
class JsonTest {
    @Test
    fun `strings decode every escape, including surrogate pairs and unicode`() {
        val text = """{"s":"a\nb\tc\"d\\e\/fé🎬 é"}"""
        assertEquals("a\nb\tc\"d\\e/fé🎬 é", Json.parseObject(text)["s"])
    }

    @Test
    fun `numbers booleans null arrays and nesting`() {
        val parsed = Json.parseObject("""{"a":[1,-2,3.5,1e2],"b":true,"c":false,"d":null,"e":{"f":[]}}""")
        assertEquals(listOf<Any?>(1L, -2L, 3.5, 100.0), parsed["a"])
        assertEquals(true, parsed["b"])
        assertEquals(false, parsed["c"])
        assertNull(parsed["d"])
        assertEquals(mapOf("f" to emptyList<Any?>()), parsed["e"])
    }

    @Test
    fun `write then parse returns what was written`() {
        val original = mapOf("q" to "line1\n\tline2 \"x\" \\ é \u0001", "n" to 7L, "v" to mapOf("file" to null, "l" to listOf(1L, "a")))
        assertEquals(original, Json.parseObject(Json.write(original)))
    }

    @Test
    fun `bad input is rejected, not guessed at`() {
        listOf(
            "", "[]", "{", """{"a":}""", """{"a":1,}""", """{"a":1} x""", """{"a":"\x"}""", "{\"a\":\"line\nbreak\"}",
            """{"a":"\u12"}""", """{"a":tru}""", """{"a":NaN}""", """{a:1}""",
        ).forEach { bad -> assertFailsWith<Json.JsonException>(bad) { Json.parseObject(bad) } }
    }

    @Test
    fun `absurd nesting is refused`() {
        val deep = "[".repeat(100) + "]".repeat(100)
        assertFailsWith<Json.JsonException> { Json.parse(deep) }
    }
}
