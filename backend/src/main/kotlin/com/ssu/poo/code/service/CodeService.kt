package com.ssu.poo.code.service

import com.ssu.poo.code.controller.dto.CodeExecuteRequestDto
import com.ssu.poo.common.exception.CodeCompileException
import com.ssu.poo.common.exception.CodeRuntimeException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Service
class CodeService {

    private val log = KotlinLogging.logger {}

    fun executeCode(codeExecuteRequestDto: CodeExecuteRequestDto): String =
        when (codeExecuteRequestDto.type.lowercase()) {
            "python" -> runPython(codeExecuteRequestDto.code, codeExecuteRequestDto.input)
            "java" -> runJava(codeExecuteRequestDto.code, codeExecuteRequestDto.input)
            "c" -> runC(codeExecuteRequestDto.code, codeExecuteRequestDto.input)
            else -> "error: unknown language"
        }

    // Python 실행
    private fun runPython(code: String, input: String): String = runCodeWithInterpreter(
        code = code,
        type = "py",
        image = "python-runner",
        command = "python code.py < input.txt",
        input = input
    )

    // 자바 코드 실행
    private fun runJava(code: String, input: String): String = runCodeWithCompiler(
        code = code,
        type = "java",
        image = "java-runner",
        compileCommand = "javac code.java",
        runCommand = "java code < input.txt",
        input = input
    )

    // C 코드 실행
    private fun runC(code: String, input: String): String = runCodeWithCompiler(
        code = code,
        type = "c",
        image = "c-runner",
        compileCommand = "gcc code.c -o code.out",
        runCommand = "./code.out < input.txt",
        input = input
    )

    // 컴파일과 실행 명렁어가 분리된 언어 실행 함수
    private fun runCodeWithCompiler(
        code: String,
        type: String,
        image: String,
        compileCommand: String,
        runCommand: String,
        input: String,
    ): String {
        var s: String?
        val output = StringBuilder(1024)

        try {
            log.debug { "$type 코드 실행" }

            // 디렉토리 생성
            val tempDir = File("backend/code/$type")
            tempDir.mkdirs()

            // 파일 생성
            val sourceFile = File(tempDir, "code.$type")
            sourceFile.writeText(code)
            val containerName = "${type}_environment"

            // input.txt 생성
            val inputFile = File(tempDir, "input.txt")
            inputFile.writeText(input)

            // 컴파일 먼저 실행
            val compileProcess = ProcessBuilder(
                "/opt/homebrew/bin/docker", "run", "--rm",
                "--name", containerName,
                "-v", "${tempDir.absolutePath}:/app",
                "-w", "/app",
                image,
                "sh", "-c", compileCommand
            ).start()

            // 컴파일 오류 판단
            val compileSuccess = compileProcess.waitFor(3, TimeUnit.SECONDS)
            if (!compileSuccess || compileProcess.exitValue() != 0) {
                val compileError = BufferedReader(InputStreamReader(compileProcess.errorStream))
                val error = StringBuilder(1024)
                while (compileError.readLine().also { s = it } != null) {
                    error.appendLine("ERROR: $s")
                }
                tempDir.deleteRecursively()
                compileProcess.destroy()
                Runtime.getRuntime().exec("/opt/homebrew/bin/docker rm -f $containerName")
                throw CodeCompileException("컴파일 에러\n$error")
            }
            compileProcess.destroy()

            // 코드 실행
            val runProcess = ProcessBuilder(
                "/opt/homebrew/bin/docker", "run", "--rm",
                "--name", containerName,
                "-v", "${tempDir.absolutePath}:/app",
                "-w", "/app",
                image,
                "sh", "-c", runCommand
            ).start()

            // 무한 루프인 경우
            val runSuccess: Boolean = runProcess.waitFor(5, TimeUnit.SECONDS)
            if(!runSuccess){
                tempDir.deleteRecursively()
                runProcess.destroy()
                Runtime.getRuntime().exec("/opt/homebrew/bin/docker rm -f $containerName")
                throw CodeRuntimeException("런타임 에러\n 시간 초과")
            }

            // 런타임 실행 오류인 경우
            if (runProcess.exitValue() != 0) {
                val runtimeError = BufferedReader(InputStreamReader(compileProcess.errorStream))
                val error = StringBuilder(1024)
                while (runtimeError.readLine().also { s = it } != null) {
                    error.appendLine("ERROR: $s")
                }
                tempDir.deleteRecursively()
                runProcess.destroy()
                Runtime.getRuntime().exec("/opt/homebrew/bin/docker rm -f $containerName")
                throw CodeRuntimeException("런타임 에러\n$error")
            }

            // 결과 가져오기
            val stdOutput = BufferedReader(InputStreamReader(runProcess.inputStream))
            while (stdOutput.readLine().also { s = it } != null) {
                output.appendLine(s)
            }

            tempDir.deleteRecursively()
            runProcess.destroy()
            return output.toString()
        } catch (e: Exception) {
            log.error { e.message }
            throw e
        }
    }

    // 인터프리터 방식의 언어 실행 함수
    private fun runCodeWithInterpreter(code: String, type: String, image: String, command: String, input: String): String {
        var s: String?
        val output = StringBuilder(1024)

        try {
            log.debug { "$type 코드 실행" }

            // 디렉토리 생성
            val tempDir = File("backend/code/$type")
            tempDir.mkdirs()

            // 파일 생성
            val sourceFile = File(tempDir, "code.$type")
            sourceFile.writeText(code)

            val containerName = "${type}_environment"

            // 명령어 저장
            val process = ProcessBuilder(
                "/opt/homebrew/bin/docker", "run", "--rm",
                "--name", containerName,
                "-v", "${tempDir.absolutePath}:/app",
                "-w", "/app",
                image,
                "sh", "-c", command
            ).start()

            // 입력값 쓰기
            val inputFile = File(tempDir, "input.txt")
            inputFile.writeText(input)

            val exitCode: Boolean = process.waitFor(5, TimeUnit.SECONDS)
            if (exitCode)
                log.debug { "실행 성공" }
            else {
                log.error { "시간 초과로 인한 실행 실패" }
                tempDir.deleteRecursively()
                process.destroy()
                Runtime.getRuntime().exec("/opt/homebrew/bin/docker rm -f $containerName")
                throw CodeRuntimeException("시간 초과")
            }

            // 결과와 에러 가져오기
            val stdOutput = BufferedReader(InputStreamReader(process.inputStream))
            val stdError = BufferedReader(InputStreamReader(process.errorStream))

            while (stdOutput.readLine().also { s = it } != null) {
                log.debug { "STDOUT: $s" }
                output.appendLine(s)
            }

            while (stdError.readLine().also { s = it } != null) {
                log.error { "STDERR: $s" }
                output.appendLine("ERROR: $s")
            }

            tempDir.deleteRecursively()
            process.destroy()

            return output.toString()
        } catch (e: Exception) {
            log.error { e.message }
            throw e
        }
    }

}

