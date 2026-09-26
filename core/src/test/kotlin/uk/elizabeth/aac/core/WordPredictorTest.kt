package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.predict.WordPredictor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordPredictorTest {
    @Test
    fun `seed vocabulary loads and completes words`() {
        val seed = WordPredictor.loadSeedWords()
        assertTrue(seed.size > 300)
        val p = WordPredictor(seed)
        assertTrue("tea" in p.predict(emptyList(), "te", 5))
    }

    @Test
    fun `learned words are suggested in context`() {
        val p = WordPredictor(WordPredictor.loadSeedWords())
        repeat(3) { p.learn(listOf("please", "call", "margaret")) }
        assertEquals("margaret", p.predict(listOf("please", "call"), "", 3).first())
        assertEquals("margaret", p.predict(listOf("call"), "ma", 3).first())
    }

    @Test
    fun `the prefix itself is not suggested`() {
        val p = WordPredictor(listOf("tea", "tear"))
        assertEquals(listOf("tear"), p.predict(emptyList(), "tea", 5))
    }

    @Test
    fun `forget removes a word everywhere`() {
        val p = WordPredictor()
        p.learn(listOf("see", "margaret", "today"))
        p.forget("margaret")
        assertFalse("margaret" in p.predict(listOf("see"), "", 10))
        assertFalse(p.export().contains("margaret"))
    }

    @Test
    fun `export and import round trip`() {
        val p = WordPredictor()
        p.learn(listOf("a", "cup", "of", "tea"))
        val text = p.export()
        val q = WordPredictor()
        q.import(text + "\nrubbish line\n3\tx\t0\n")
        assertEquals(text, q.export())
        assertEquals(4, q.learnedWordCount)
    }

    @Test
    fun `forget all keeps the seed vocabulary`() {
        val p = WordPredictor(listOf("hello"))
        p.learn(listOf("zebra"))
        p.forgetAll()
        assertEquals(0, p.learnedWordCount)
        assertEquals(listOf("hello"), p.predict(emptyList(), "", 5))
    }
}
