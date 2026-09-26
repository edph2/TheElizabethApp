package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.text.MessageEditor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageEditorTest {
    @Test
    fun `typing capitalises the start of a sentence`() {
        val e = MessageEditor()
        "hi".forEach(e::typeCharacter)
        assertEquals("Hi", e.text)
    }

    @Test
    fun `inserting a word replaces the partial word and adds a space`() {
        val e = MessageEditor("I want a cu")
        e.insertWord("cup")
        assertEquals("I want a cup ", e.text)
        assertEquals("", e.currentWordPrefix)
        assertEquals(listOf("i", "want", "a", "cup"), e.precedingWords)
    }

    @Test
    fun `phrases become their own sentences`() {
        val e = MessageEditor()
        e.insertPhrase("I'm tired")
        e.insertPhrase("Thank you")
        assertEquals("I'm tired. Thank you ", e.text)
    }

    @Test
    fun `undo reverses each change`() {
        val e = MessageEditor()
        e.insertWord("hello")
        e.insertWord("there")
        e.clear()
        assertTrue(e.undo())
        assertEquals("Hello there ", e.text)
        assertTrue(e.undo())
        assertEquals("Hello ", e.text)
        e.undo()
        assertFalse(e.undo())
    }

    @Test
    fun `delete word removes the last word`() {
        val e = MessageEditor("Could I have tea ")
        e.deleteWord()
        assertEquals("Could I have ", e.text)
    }

    @Test
    fun `tokenize keeps apostrophes`() {
        assertEquals(listOf("don't", "go", "o'clock"), MessageEditor.tokenize("Don't go, 'o'clock'!"))
    }
}
