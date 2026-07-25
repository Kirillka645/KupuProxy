package com.kupuproxy.app.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Логика сравнения версий (зеркало [UpdateChecker.parseVersion] / isNewerVersion).
 * Не требует Android Context.
 */
class UpdateCheckerVersionTest {

    @Test
    fun fixSuffixIsNewerThanSameNumbers() {
        assertTrue(isNewer("1.3.2", "v1.3.2-fix"))
        assertTrue(isNewer("1.3.1", "v1.3.2-fix"))
        assertFalse(isNewer("1.3.2-fix", "v1.3.2-fix"))
        assertFalse(isNewer("1.3.3", "v1.3.2-fix"))
    }

    @Test
    fun plainSemver() {
        assertTrue(isNewer("1.3.2", "v1.3.3"))
        assertTrue(isNewer("1.2.9", "1.3.0"))
        assertFalse(isNewer("1.3.3", "1.3.2"))
        assertFalse(isNewer("1.3.3", "v1.3.3"))
    }

    @Test
    fun fourPartBeatsThree() {
        assertTrue(isNewer("1.3.2", "1.3.2.1"))
    }

    @Test
    fun oldBugRegression_fixWasZero() {
        // Старый код: "2-fix".toIntOrNull()==null → 0, 1.3.2-fix считался 1.3.0
        assertTrue(isNewer("1.3.1", "v1.3.2-fix"))
        assertTrue(isNewer("1.3.0", "v1.3.2-fix"))
    }

    private fun isNewer(current: String, latest: String): Boolean =
        parse(latest) > parse(current)

    private fun parse(raw: String): Pair<List<Int>, Int> {
        val s = raw.trim().removePrefix("v").removePrefix("V").trim()
        val match = Regex("""^(\d+(?:\.\d+)*)(?:[-_.+]?(.*))?$""").matchEntire(s)
        val numPart = match?.groupValues?.getOrNull(1) ?: s
        val suffix = (match?.groupValues?.getOrNull(2) ?: "").lowercase().trim()
        val nums = numPart.split('.').map {
            Regex("""^\d+""").find(it)?.value?.toIntOrNull() ?: 0
        }
        val w = when {
            suffix.isBlank() -> 0
            suffix.startsWith("fix") -> 10 + (Regex("""\d+""").find(suffix)?.value?.toIntOrNull() ?: 0)
            suffix.startsWith("hotfix") -> 11
            suffix.startsWith("patch") -> 9
            suffix.startsWith("rc") -> -2
            suffix.startsWith("beta") -> -3
            suffix.startsWith("alpha") -> -4
            else -> 5
        }
        return nums to w
    }

    private operator fun Pair<List<Int>, Int>.compareTo(other: Pair<List<Int>, Int>): Int {
        val max = maxOf(first.size, other.first.size)
        for (i in 0 until max) {
            val a = first.getOrElse(i) { 0 }
            val b = other.first.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return second.compareTo(other.second)
    }
}
