package be.drakarah.intonation.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The note-naming display: proper musical glyphs, the sharp/flat tables, and the guarantee
 * that only the five black keys are ever respelled (never Do → Si♯). */
class EnharmonicSpellingTest {

    @Test
    fun usesRealMusicalGlyphsNotAsciiHash() {
        // U+266F ♯, U+266D ♭ — not the ASCII "#".
        val laSharp = NoteSpec(69 + 1).displayName(NoteNameStyle.SOLFEGE, Accidental.SHARP)
        val laFlatSpelling = NoteSpec(69 + 1).displayName(NoteNameStyle.SOLFEGE, Accidental.FLAT)
        assertTrue("expected ♯ glyph, got $laSharp", laSharp.contains('♯'))
        assertTrue("expected ♭ glyph, got $laFlatSpelling", laFlatSpelling.contains('♭'))
        assertTrue("must not use ASCII #", !laSharp.contains('#'))
    }

    @Test
    fun blackKeysSpellBothWays() {
        // La♯ (A♯) and its flat name Si♭ (B♭) — MIDI 70 = A♯4.
        val note = NoteSpec(70)
        assertEquals("La♯4", note.displayName(NoteNameStyle.SOLFEGE, Accidental.SHARP))
        assertEquals("Si♭4", note.displayName(NoteNameStyle.SOLFEGE, Accidental.FLAT))
        assertEquals("A♯4", note.displayName(NoteNameStyle.LETTERS, Accidental.SHARP))
        assertEquals("B♭4", note.displayName(NoteNameStyle.LETTERS, Accidental.FLAT))
    }

    @Test
    fun naturalsAreNeverRespelled() {
        // Every natural note reads identically under both accidental preferences — so the mix
        // can never turn Do into "Si♯" or Fa into "Mi♯" (the spellings Sarah's teacher drilled
        // but that are out of scope here).
        for (midi in 12..108) {
            val pc = midi % 12
            val isNatural = pc in intArrayOf(0, 2, 4, 5, 7, 9, 11)
            if (!isNatural) continue
            for (style in NoteNameStyle.entries) {
                val sharp = NoteSpec(midi).displayName(style, Accidental.SHARP)
                val flat = NoteSpec(midi).displayName(style, Accidental.FLAT)
                assertEquals("natural midi $midi changed spelling", sharp, flat)
            }
        }
    }

    @Test
    fun defaultSpellingIsSharp() {
        assertEquals(
            NoteSpec(70).displayName(NoteNameStyle.SOLFEGE, Accidental.SHARP),
            NoteSpec(70).displayName(NoteNameStyle.SOLFEGE),
        )
    }
}
