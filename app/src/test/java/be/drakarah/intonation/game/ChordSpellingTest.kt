package be.drakarah.intonation.game

import be.drakarah.intonation.music.NoteNameStyle
import be.drakarah.intonation.music.NoteSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Chord tones are spelled by music theory (root → third → fifth of the named chord), not by the
 * mic and not by the "mix sharps & flats" setting. Locked against Sarah's fixed-do reference
 * table — including the clean-enharmonic pick per pitch class (D♭ major not C♯ major, G♯ minor
 * not A♭ minor, …) so no Mi♯ / Fa♭ / double-flats ever appear. */
class ChordSpellingTest {

    /** The three tone names of a triad rooted on [rootMidi], in the given style. */
    private fun tones(rootMidi: Int, quality: ChordQuality, style: NoteNameStyle): List<String> =
        chordToneSpellings(NoteSpec(rootMidi), quality).mapIndexed { i, acc ->
            NoteSpec(rootMidi + quality.intervals[i]).pitchClassName(style, acc)
        }

    // MIDI pitch classes: C=60, so 60+pc.
    private fun solf(pc: Int, q: ChordQuality) = tones(60 + pc, q, NoteNameStyle.SOLFEGE)
    private fun letters(pc: Int, q: ChordQuality) = tones(60 + pc, q, NoteNameStyle.LETTERS)

    @Test
    fun majorTriadsMatchReferenceTable() {
        assertEquals(listOf("Do", "Mi", "Sol"), solf(0, ChordQuality.MAJOR))
        assertEquals(listOf("Ré♭", "Fa", "La♭"), solf(1, ChordQuality.MAJOR)) // D♭, not C♯/Mi♯
        assertEquals(listOf("Ré", "Fa♯", "La"), solf(2, ChordQuality.MAJOR))
        assertEquals(listOf("Mi♭", "Sol", "Si♭"), solf(3, ChordQuality.MAJOR))
        assertEquals(listOf("Mi", "Sol♯", "Si"), solf(4, ChordQuality.MAJOR))
        assertEquals(listOf("Fa", "La", "Do"), solf(5, ChordQuality.MAJOR))
        assertEquals(listOf("Sol♭", "Si♭", "Ré♭"), solf(6, ChordQuality.MAJOR)) // G♭ (the chosen pick)
        assertEquals(listOf("Sol", "Si", "Ré"), solf(7, ChordQuality.MAJOR))
        assertEquals(listOf("La♭", "Do", "Mi♭"), solf(8, ChordQuality.MAJOR))
        assertEquals(listOf("La", "Do♯", "Mi"), solf(9, ChordQuality.MAJOR))
        assertEquals(listOf("Si♭", "Ré", "Fa"), solf(10, ChordQuality.MAJOR)) // B♭, not A♯
        assertEquals(listOf("Si", "Ré♯", "Fa♯"), solf(11, ChordQuality.MAJOR))
    }

    @Test
    fun minorTriadsMatchReferenceTable() {
        assertEquals(listOf("Do", "Mi♭", "Sol"), solf(0, ChordQuality.MINOR))
        assertEquals(listOf("Do♯", "Mi", "Sol♯"), solf(1, ChordQuality.MINOR)) // C♯m, not D♭m/Fa♭
        assertEquals(listOf("Ré", "Fa", "La"), solf(2, ChordQuality.MINOR))
        assertEquals(listOf("Mi♭", "Sol♭", "Si♭"), solf(3, ChordQuality.MINOR))
        assertEquals(listOf("Mi", "Sol", "Si"), solf(4, ChordQuality.MINOR))
        assertEquals(listOf("Fa", "La♭", "Do"), solf(5, ChordQuality.MINOR))
        assertEquals(listOf("Fa♯", "La", "Do♯"), solf(6, ChordQuality.MINOR))
        assertEquals(listOf("Sol", "Si♭", "Ré"), solf(7, ChordQuality.MINOR))
        assertEquals(listOf("Sol♯", "Si", "Ré♯"), solf(8, ChordQuality.MINOR)) // G♯m, not A♭m/Do♭
        assertEquals(listOf("La", "Do", "Mi"), solf(9, ChordQuality.MINOR))
        assertEquals(listOf("Si♭", "Ré♭", "Fa"), solf(10, ChordQuality.MINOR))
        assertEquals(listOf("Si", "Ré", "Fa♯"), solf(11, ChordQuality.MINOR))
    }

    @Test
    fun lettersModeSpellsCorrectly() {
        assertEquals(listOf("B♭", "D", "F"), letters(10, ChordQuality.MAJOR))
        assertEquals(listOf("B", "D♯", "F♯"), letters(11, ChordQuality.MAJOR))
        assertEquals(listOf("G♯", "B", "D♯"), letters(8, ChordQuality.MINOR))
    }

    @Test
    fun neverUsesAsciiHashOrOutOfScopeSpellings() {
        for (pc in 0..11) for (q in ChordQuality.entries) {
            (solf(pc, q) + letters(pc, q)).forEach { name ->
                assertFalse("ASCII # in $name", name.contains('#'))
                // no double accidentals could occur — the tables only hold single ones
                assertFalse("double sharp in $name", name.contains("♯♯"))
                assertFalse("double flat in $name", name.contains("♭♭"))
            }
        }
    }

    @Test
    fun chordNameUsesRootSpelling() {
        // B♭ major reads "Si♭ Majeur", never "La♯ Majeur".
        val root = NoteSpec(60 + 10)
        val rootAcc = chordToneSpellings(root, ChordQuality.MAJOR)[0]
        assertEquals("Si♭ Majeur", chordName(root, ChordQuality.MAJOR, NoteNameStyle.SOLFEGE, rootAcc))
    }
}
