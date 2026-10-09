package ru.anjeib.margin

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.AppJson
import ru.anjeib.margin.data.LoadProblem
import ru.anjeib.margin.data.Store
import ru.anjeib.margin.data.Task
import java.io.File

/** The saved file never loses what the user wrote: round trip, damaged file, backup, fast changes. */
class StoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun task(id: String, title: String = "Дело $id") = Task(id = id, title = title)
    private fun store(dir: File = tmp.root) = Store(File(dir, "margin.json"))
    private fun saved(dir: File = tmp.root): AppData =
        AppJson.decodeFromString(AppData.serializer(), File(dir, "margin.json").readText())

    @Test fun roundTrip() = runBlocking {
        val s = store()
        s.update { it.copy(tasks = listOf(task("a"), task("b"))) }
        s.flush()
        val again = store()
        assertEquals(listOf("a", "b"), again.data.value.tasks.map { it.id })
        assertNull(again.problem.value)
        assertEquals(Store.SCHEMA, saved().v)
    }

    @Test fun manyFastChangesEndWithTheNewest() = runBlocking {
        val s = store()
        repeat(200) { i -> s.update { d -> d.copy(tasks = d.tasks + task("t$i")) } }
        s.flush()
        assertEquals(200, saved().tasks.size)
        assertEquals("t199", saved().tasks.last().id)
    }

    @Test fun damagedFileFallsBackToPreviousCopy() = runBlocking {
        val s = store()
        s.update { it.copy(tasks = listOf(task("a"))) }
        s.flush()
        s.update { it.copy(tasks = it.tasks + task("b")) }
        s.flush()
        // margin.json.bak now holds the state with «a» only.
        File(tmp.root, "margin.json").writeText("{\"tasks\":[{\"id\":")
        val again = store()
        assertEquals(listOf("a"), again.data.value.tasks.map { it.id })
        assertEquals(LoadProblem.Kind.RESTORED, again.problem.value?.kind)
        val aside = tmp.root.listFiles()!!.filter { it.name.startsWith("margin.broken-") }
        assertEquals(1, aside.size)
        assertEquals("{\"tasks\":[{\"id\":", aside.single().readText())
    }

    @Test fun damagedFileAndNoBackupKeepsTheFileAside() = runBlocking {
        File(tmp.root, "margin.json").writeText("not json at all")
        val s = store()
        assertTrue(s.data.value.tasks.isEmpty())
        assertEquals(LoadProblem.Kind.LOST, s.problem.value?.kind)
        // Writing new things must not touch what was kept aside.
        s.update { it.copy(tasks = listOf(task("new"))) }
        s.flush()
        s.update { it.copy(tasks = it.tasks + task("newer")) }
        s.flush()
        val aside = tmp.root.listFiles()!!.single { it.name.startsWith("margin.broken-") }
        assertEquals("not json at all", aside.readText())
    }

    @Test fun damagedBackupIsKeptAsideToo() = runBlocking {
        File(tmp.root, "margin.json").writeText("broken")
        File(tmp.root, "margin.json.bak").writeText("broken too")
        val s = store()
        assertEquals(LoadProblem.Kind.LOST, s.problem.value?.kind)
        s.update { it.copy(tasks = listOf(task("x"))) }
        s.flush()
        s.update { it.copy(tasks = it.tasks + task("y")) }
        s.flush()
        val names = tmp.root.listFiles()!!.map { it.name }
        assertTrue(names.any { it.startsWith("margin.broken-bak-") })
        assertEquals("broken too", tmp.root.listFiles()!!.single { it.name.startsWith("margin.broken-bak-") }.readText())
    }

    @Test fun cutBetweenRenamesReadsTheBackup() = runBlocking {
        val s = store()
        s.update { it.copy(tasks = listOf(task("a"))) }
        s.flush()
        s.update { it.copy(tasks = it.tasks + task("b")) }
        s.flush()
        File(tmp.root, "margin.json").delete()
        val again = store()
        assertEquals(listOf("a"), again.data.value.tasks.map { it.id })
    }

    @Test fun fileFromNewerVersionIsCopied() = runBlocking {
        File(tmp.root, "margin.json").writeText("{\"v\":${Store.SCHEMA + 1},\"tasks\":[{\"id\":\"a\",\"title\":\"А\"}],\"future\":1}")
        val s = store()
        assertEquals(LoadProblem.Kind.NEWER, s.problem.value?.kind)
        val copy = tmp.root.listFiles()!!.single { it.name.startsWith("margin.newer-") }
        assertTrue(copy.readText().contains("\"future\":1"))
        assertNotNull(s.data.value.tasks.find { it.id == "a" })
    }

    @Test fun freshInstallIsEmptyAndQuiet() {
        val s = store()
        assertTrue(s.data.value.tasks.isEmpty())
        assertNull(s.problem.value)
    }
}
