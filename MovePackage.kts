/**
 * the script to move packages
 * ```
 * cd <path-to-this-project>
 * kotlinc-jvm -script './MovePackage.kts' <command>
 * ```
 *
 * commands:
 * - `move`
 *   > move files
 * - `replace`
 *   > replace package name
 * - `run-and-commit`
 *   > run and commit both action
 *   > This is used in CI.
 */

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.experimental.and

val replacements = listOf(
    "org.mozilla.javascript" to "com.anatawa12.sai",
    "org.mozilla.classfile" to "com.anatawa12.sai.classfile",
)

val srcDirectories = listOf(
    "benchmarks",
    "src",
    "testsrc",
    "toolsrc",
    "xmlimplsrc",
    "examples",
    "spotbugs-exclude.xml",
    "build.gradle",
)

when(args[0]) {
    "move" -> moveFiles()
    "replace" -> replacePackageName()
    "run-and-commit" -> runAndCommit()
}

fun moveFiles() {
    runEachReplaceDir().forEach { (srcDir, dstDir) ->
        if (!srcDir.exists()) return@forEach
        dstDir.parentFile.mkdirs()
        Files.move(srcDir.toPath(), dstDir.toPath())
    }
}

fun replacePackageName() {
    runEachFile { (_, dirOrFile) ->
        if (dirOrFile.isDirectory) {
            return@runEachFile
        }

        val sourceBytes = dirOrFile.readBytes()
        val sourceText = when {
            sourceBytes.isValidUtf8() -> sourceBytes.toString(Charsets.UTF_8)
            sourceBytes.isValidIso8859One() -> sourceBytes.toString(Charsets.ISO_8859_1)
            else -> {
                println("$dirOrFile is binary")
                return@runEachFile
            }
        }
        val replacedText = replacements.reduce(sourceText) { s, (from, to) ->
            s.replace(from, to)
                .replace(from.asPath(), to.asPath())
                .replace(from.replace(".", "\\."), to.replace(".", "\\."))
        }
        dirOrFile.writeText(replacedText)
    }
}

fun runAndCommit() {
    println("checking modified files...")

    // -s: silent, -uno: without untracked
    execCommand("git", "status", "-s", "-uno")
        .expectEmpty("there's tracking modified files")
        .run()

    println("moving files...")

    moveFiles()

    println("adding files...")

    runEachReplaceDir()
        .flatMap { (srcDir, dstDir) -> sequenceOf(srcDir, dstDir) }
        .forEach { dir ->
            execCommand(listOf("git", "add", dir.path))
                .allowNonZeroExit()
                .run()
        }

    execCommand("git", "add", "-u").expectEmpty().run()

    println("committing move...")

    execCommand("git", "commit", "-m", "move packages (1: move files)").run()

    println("modifying files...")

    replacePackageName()

    println("adding modify...")

    execCommand("git", "add", "-u").expectEmpty().run()

    println("committing modify...")

    execCommand("git", "commit", "-m", "move packages (2: modify files)").run()
}

fun runEachFile(): Sequence<EachFile> {
    return srcDirectories
        .asSequence()
        .map(::File)
        .map(File::getAbsoluteFile)
        .onEach { println("running $it") }
        .flatMap { srcFile -> srcFile.walkBottomUp().map { EachFile(srcFile, it) } }
        .onEach { println("> running ${it.dirOrFile}") }
}

fun runEachReplaceDir(): Sequence<Pair<File, File>> {
    return srcDirectories
        .asSequence()
        .map(::File)
        .map(File::getAbsoluteFile)
        .onEach { println("running $it") }
        .flatMap { srcFile ->
            replacements
                .map { (replaceFrom, replaceTo) ->
                    srcFile.resolve(replaceFrom.asPath()) to srcFile.resolve(replaceTo.asPath())
                }
                .onEach { (fromDir, _) -> println("> running $fromDir") }
        }
}

inline fun runEachFile(block: (EachFile) -> Unit) {
    runEachFile().forEach(block)
}

data class EachFile(val srcFile: File, val dirOrFile: File)

fun String.asPath() = replace('.', '/')
fun String.doReplacement(replacement: Pair<String, String>): String {
    require(startsWith(replacement.first.asPath())) { "'$this' is not starts with '${replacement.first.asPath()}'" }
    return replacement.second.asPath() + substring(replacement.first.asPath().length)
}

inline fun <S, T> Iterable<T>.reduce(initial: S, operation: (acc: S, T) -> S): S {
    val iterator = this.iterator()
    var accumulator: S = initial
    while (iterator.hasNext()) {
        accumulator = operation(accumulator, iterator.next())
    }
    return accumulator
}

fun ByteArray.isValidUtf8() = Utf8Verifier.isValidUtf8(this)

object Iso8859One {
    val table = kotlin.run {
        val bits = BitSet(256)
        for (i in 0x20..0x7E) bits.set(i)
        for (i in 0xA0..0xFF) bits.set(i)
        bits.set('\r'.toInt())
        bits.set('\n'.toInt())
        bits.set('\t'.toInt())
        bits
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
fun ByteArray.isValidIso8859One(): Boolean {
    for (byte in this) {
        val isVaild = Iso8859One.table[byte.toInt() and 0xFF]
        if (!isVaild) return false
    }
    return true
}

object Utf8Verifier {
    const val START_STATE = 0
    const val REQUEST_1ELEMENTS = 1
    const val REQUEST_2ELEMENTS = 2
    const val REQUEST_3ELEMENTS = 3

    @OptIn(ExperimentalUnsignedTypes::class)
    @Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
    fun isValidUtf8(bytes: ByteArray): Boolean {
        var state = START_STATE
        for (byte in bytes) {
            if (byte and 0b1000_0000u.toByte() == 0b0000_0000u.toByte()) {
                if (state != START_STATE) return false
            } else if (byte and 0b1110_0000u.toByte() == 0b1100_0000u.toByte()) {
                if (state != START_STATE) return false
                state = REQUEST_1ELEMENTS
            } else if (byte and 0b1111_0000u.toByte() == 0b1110_0000u.toByte()) {
                if (state != START_STATE) return false
                state = REQUEST_2ELEMENTS
            } else if (byte and 0b1111_1000u.toByte() == 0b1111_0000u.toByte()) {
                if (state != START_STATE) return false
                state = REQUEST_3ELEMENTS
            } else if (byte and 0b1100_0000u.toByte() == 0b1000_0000u.toByte()) {
                if (state == START_STATE) return false
                state -= 1
            } else {
                return false
            }
        }
        return true
    }
}

fun execCommand(vararg args: String): CommandExecutor = CommandExecutor(args.asList())
fun execCommand(args: List<String>): CommandExecutor = CommandExecutor(args)

class CommandExecutor(val args: List<String>) {
    private var expectEmptyMessage: String? = null
    private var allowNonZero = false

    fun expectEmpty(message: String? = null) = apply {
        expectEmptyMessage = message ?: "{streamName} is not empty."
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE)
        builder.redirectError(ProcessBuilder.Redirect.PIPE)
    }

    fun allowNonZeroExit() = apply {
        allowNonZero = true
    }

    fun run() {
        val process = builder.start()
        process.outputStream.close()
        val waitingStreamCount = AtomicInteger(0)
        val errorMessage = AtomicString()
        val lock = ReentrantLock()
        val condition = lock.newCondition()
        if (expectEmptyMessage != null) {
            waitingStreamCount.getAndAdd(2)
            thread(start = true) {
                checkEmpty(process.inputStream, errorMessage, waitingStreamCount, lock, condition, "stdout")
            }
            thread(start = true) {
                checkEmpty(process.errorStream, errorMessage, waitingStreamCount, lock, condition, "stderr")
            }
        }
        if (waitingStreamCount.get() != 0) {
            lock.withLock {
                while (true) {
                    condition.await()
                    val cnt = waitingStreamCount.get()
                    if (cnt == 0) break
                }
            }
        }
        process.waitFor()
        if (!allowNonZero && process.exitValue() != 0) {
            errorMessage.append("returns non zero value: ${process.exitValue()}\n")
        }
        val msg = errorMessage.toString()
        if (msg != "") {
            error("execute ${args.joinToString(" ")} fail: \n$msg")
        }
    }

    private fun checkEmpty(
        stream: InputStream,
        errorMessage: AtomicString,
        waitingStreamCount: AtomicInteger,
        lock: Lock,
        condition: Condition,
        streamName: String,
    ) {
        val args = ByteArray(1024)
        while (true) {
            val size = stream.read(args)
            if (size == -1) {
                lock.withLock {
                    waitingStreamCount.getAndDecrement()
                    condition.signal()
                    return
                }
            } else {
                errorMessage.append("${expectEmptyMessage!!.replace("{streamName}", streamName)}\n")
                System.out.write(args, 0, size)
            }
        }
    }

    val builder = ProcessBuilder(args)

    init {
        builder.inheritIO()
        builder.redirectInput(ProcessBuilder.Redirect.PIPE)
    }

    private class AtomicString {
        private val buf = AtomicReference("")

        fun append(str: String) {
            while (true) {
                val got = buf.get()
                if (buf.compareAndSet(got, got + str)) break
            }
        }

        override fun toString(): String = buf.get()
    }
}
