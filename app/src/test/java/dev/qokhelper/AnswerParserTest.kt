package dev.qokhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerParserTest {

    @Test
    fun parsesWellFormedLine() {
        assertEquals(
            Answer.Found(position = 3, text = "تهران", confidence = 90),
            AnswerParser.parse("3|تهران|90"),
        )
    }

    @Test
    fun toleratesWhitespaceAndSurroundingText() {
        assertEquals(
            Answer.Found(position = 2, text = "Mount Everest", confidence = 75),
            AnswerParser.parse("Sure.\n 2 | Mount Everest | 75 \n"),
        )
    }

    @Test
    fun takesTheLastAnswerLine() {
        assertEquals(
            Answer.Found(position = 1, text = "Canberra", confidence = 95),
            AnswerParser.parse("My first guess: 3|Sydney|40\nThe search says otherwise.\n1|Canberra|95"),
        )
    }

    @Test
    fun acceptsPersianDigits() {
        assertEquals(
            Answer.Found(position = 4, text = "۱۳۵۷", confidence = 60),
            AnswerParser.parse("۴|۱۳۵۷|۶۰"),
        )
    }

    @Test
    fun missingConfidenceIsNull() {
        assertEquals(
            Answer.Found(position = 1, text = "Paris", confidence = null),
            AnswerParser.parse("1|Paris"),
        )
    }

    @Test
    fun noneMeansNoQuestion() {
        assertEquals(Answer.NoQuestion, AnswerParser.parse("NONE"))
    }

    @Test
    fun garbageFallsBackToRawText() {
        assertEquals(Answer.Unparsed("I am not sure"), AnswerParser.parse("I am not sure"))
    }
}
