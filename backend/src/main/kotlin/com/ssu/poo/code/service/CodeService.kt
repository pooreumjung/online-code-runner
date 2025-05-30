package com.ssu.poo.code.service

import com.ssu.poo.code.controller.dto.CodeExecuteRequestDto
import com.ssu.poo.code.service.dto.CodeExecuteSettingValueReturnDto
import com.ssu.poo.common.exception.CodeCompileException
import com.ssu.poo.common.exception.CodeRuntimeException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Service
class CodeService {

    private val log = KotlinLogging.logger {}

    // 언어에 맞게 실행
    fun executeCode(codeExecuteRequestDto: CodeExecuteRequestDto): String =
        when (codeExecuteRequestDto.type.lowercase()) {
            "python" -> runPython(codeExecuteRequestDto.code, codeExecuteRequestDto.input)
            "java" -> runJava(codeExecuteRequestDto.code, codeExecuteRequestDto.input)
            "c" -> runC(codeExecuteRequestDto.code, codeExecuteRequestDto.input)
            "javascript" -> runJavaScript(codeExecuteRequestDto.code, codeExecuteRequestDto.input)
            else -> "error: unknown language"
        }

    // Python
    private fun runPython(code: String, input: String): String {
        val command = if (input.isBlank()) "python code.py" else "python code.py < input.txt"
        return runCodeWithInterpreter(code, "py", "python-runner", command, input)
    }

    // Java
    private fun runJava(code: String, input: String): String {
        val runCommand = if (input.isBlank()) "java code" else "java code < input.txt"
        return runCodeWithCompiler(code, "java", "java-runner", "javac code.java", runCommand, input)
    }

    // C
    private fun runC(code: String, input: String): String {
        val runCommand = if (input.isBlank()) "./code.out" else "./code.out < input.txt"
        return runCodeWithCompiler(code, "c", "c-runner", "gcc code.c -o code.out", runCommand, input)
    }

    // JavaScript
    private fun runJavaScript(code: String, input: String): String {
        val command = if (input.isBlank()) "node code.js" else "node code.js < input.txt"
        return runCodeWithInterpreter(code, "js", "js-runner", command, input)
    }

    // 컴파일러 언어(C, Java)
    private fun runCodeWithCompiler(code: String, type: String, image: String, compileCommand: String, runCommand: String, input: String): String {
        log.debug { "runCodeWithCompiler code = $code" }
        val output = StringBuilder(1024)
        val (tempDir, containerName) = prepareExecutionEnvironment(type, code, input)

        try {
            val compileProcess = createProcess(image, containerName, tempDir, compileCommand)
            checkCompileSuccess(compileProcess)
            compileProcess.destroy()

            val runProcess = createProcess(image, containerName, tempDir, runCommand)
            checkRuntimeSuccess(runProcess, true)
            output.append(readStream(runProcess.inputStream, "결과: "))

            return output.toString()
        } finally {
            cleanupExecutionEnvironment(tempDir, containerName)
        }
    }

    // 인터프리터 언어(JS, Python)
    private fun runCodeWithInterpreter(code: String, type: String, image: String, command: String, input: String): String {
        log.debug { "runCodeWithInterpreter code = $code" }
        val output = StringBuilder(1024)
        val (tempDir, containerName) = prepareExecutionEnvironment(type, code, input)

        try {
            val runProcess = createProcess(image, containerName, tempDir, command)
            checkRuntimeSuccess(runProcess, false)
            output.append(readStream(runProcess.inputStream, "결과: "))
            return output.toString()
        } finally {
            cleanupExecutionEnvironment(tempDir, containerName)
        }
    }

    // Dokcer 관련 명령어 실행 함수
    private fun createProcess(image: String, containerName: String, tempDir: File, command: String): Process =
        ProcessBuilder("/opt/homebrew/bin/docker", "run", "--rm", "--name", containerName, "-v", "${tempDir.absolutePath}:/app", "-w", "/app", image, "sh", "-c", command).start()

    // 코드 실행 환경 세팅
    private fun prepareExecutionEnvironment(type: String, code: String, input: String): CodeExecuteSettingValueReturnDto {
        val tempDir = File("backend/code/$type").apply { mkdirs() }
        File(tempDir, "code.$type").writeText(code)
        File(tempDir, "input.txt").writeText(input)
        val containerName = "${type}_env_${System.currentTimeMillis()}"
        return CodeExecuteSettingValueReturnDto(tempDir, containerName)
    }

    // 컴파일 중 오류가 발생 시 오류 출력
    private fun checkCompileSuccess(process: Process) {
        log.debug { "checkCompileSuccess code = $process" }
        if (!process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() != 0) {
            val error = readStream(process.errorStream, "ERROR: ")
            throw CodeCompileException("컴파일 에러\n$error")
        }
    }

    // 런타임 중 오류 발생 시 오류 출력
    private fun checkRuntimeSuccess(process: Process, isCompileLanguage: Boolean) {
        log.debug { "checkRuntimeSuccess code = $process" }
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            throw CodeRuntimeException("런타임 에러\n시간 초과")
        }

        if (process.exitValue() != 0) {
            val error = readStream(process.errorStream, "ERROR: ")
            if (isCompileLanguage) {
                throw CodeRuntimeException("런타임 에러\n$error")
            } else {
                val isSyntaxError = "SyntaxError" in error
                val isRuntimeError = listOf("ReferenceError", "TypeError", "RangeError", "Error", "Traceback").any { it in error }
                when {
                    isSyntaxError -> throw CodeCompileException("컴파일 에러\n$error")
                    isRuntimeError -> throw CodeRuntimeException("런타임 에러\n$error")
                }
            }
        }
    }

    private fun cleanupExecutionEnvironment(tempDir: File, containerName: String) {
        log.debug { "cleanupExecutionEnvironment" }
        tempDir.deleteRecursively()
        Runtime.getRuntime().exec("/opt/homebrew/bin/docker rm -f $containerName")
    }

    private fun readStream(stream: InputStream, prefix: String = ""): String {
        log.debug { "readStream code = $stream" }
        val output = StringBuilder()
        BufferedReader(InputStreamReader(stream)).useLines { lines ->
            lines.forEach { output.appendLine("$prefix$it") }
        }
        return output.toString()
    }
}
