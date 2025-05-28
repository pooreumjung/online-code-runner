package com.ssu.poo.code.service

import com.ssu.poo.code.controller.dto.CodeExecuteRequestDto
import com.ssu.poo.code.service.dto.CodeExecuteSettingValueReturnDto
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
    private fun runPython(code: String, input: String): String {
        val command = if (input.isBlank()) {
            "python code.py"
        } else {
            "python code.py < input.txt"
        }

        return runCodeWithInterpreter(
            code = code,
            type = "py",
            image = "python-runner",
            command = command,
            input = input
        )
    }


    // 자바 코드 실행
    private fun runJava(code: String, input: String): String {
        val runCommand = if (input.isBlank()) {
            "java code"
        } else {
            "java code < input.txt"
        }

        return runCodeWithCompiler(
            code = code,
            type = "java",
            image = "java-runner",
            compileCommand = "javac code.java",
            runCommand = runCommand,
            input = input
        )
    }

    // C 코드 실행
    private fun runC(code: String, input: String): String {
        val runCommand = if (input.isBlank()) {
            "./code.out"
        } else {
            "./code.out < input.txt"
        }

        return runCodeWithCompiler(
            code = code,
            type = "c",
            image = "c-runner",
            compileCommand = "gcc code.c -o code.out",
            runCommand = runCommand,
            input = input
        )
    }


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

            val settingValue: CodeExecuteSettingValueReturnDto = prepareExecutionEnvironment(type, code, input)
            val tempDir: File = settingValue.tempDir
            val containerName: String = settingValue.containerName

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
            checkCompileSuccess(compileProcess, containerName, tempDir)
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

            // 런타임 오류 판단
            checkRuntimeSuccess(runProcess, containerName, tempDir, true)

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
    private fun runCodeWithInterpreter(
        code: String,
        type: String,
        image: String,
        command: String,
        input: String
    ): String {

        try {
            log.debug { "$type 코드 실행" }

            val settingValue: CodeExecuteSettingValueReturnDto = prepareExecutionEnvironment(type, code, input)
            val tempDir: File = settingValue.tempDir
            val containerName: String = settingValue.containerName

            // 명령어 저장
            val runProcess = ProcessBuilder(
                "/opt/homebrew/bin/docker", "run", "--rm",
                "--name", containerName,
                "-v", "${tempDir.absolutePath}:/app",
                "-w", "/app",
                image,
                "sh", "-c", command
            ).start()

            checkRuntimeSuccess(runProcess, containerName, tempDir, false)

            // 결과 가져오기
            val output = readStdOutput(runProcess)


            tempDir.deleteRecursively()
            runProcess.destroy()
            return output
        } catch (e: Exception) {
            log.error { e.message }
            throw e
        }
    }

    // 실행환경 세팅 함수
    private fun prepareExecutionEnvironment(
        type: String,
        code: String,
        input: String
    ): CodeExecuteSettingValueReturnDto {
        // 디렉토리 생성
        val tempDir = File("backend/code/$type")
        tempDir.mkdirs()

        // 파일 생성
        val sourceFile = File(tempDir, "code.$type")
        sourceFile.writeText(code)
        val containerName = "${type}_env_${System.currentTimeMillis()}"

        // input.txt 생성
        when {
            input.isNotEmpty() -> {
                val inputFile = File(tempDir, "input.txt")
                inputFile.writeText(input)
            }
        }
        return CodeExecuteSettingValueReturnDto(tempDir, containerName)
    }

    // 컴파일 체크 함수
    private fun checkCompileSuccess(compileProcess: Process, containerName: String, tempDir: File) {
        var s: String?
        val compileSuccess = compileProcess.waitFor(3, TimeUnit.SECONDS)
        if (!compileSuccess || compileProcess.exitValue() != 0) {

            val compileError = readErrorOrOutput(compileProcess)
            cleanupExecutionEnvironment(tempDir, compileProcess, containerName)
            throw CodeCompileException("컴파일 에러\n$compileError")
        }
    }

    // 코드 실행 체크 함수
    private fun checkRuntimeSuccess(
        runProcess: Process,
        containerName: String,
        tempDir: File,
        isCompileLanguage: Boolean
    ) {
        var s: String?
        val runSuccess: Boolean = runProcess.waitFor(5, TimeUnit.SECONDS)
        // 무한 루프인 경우
        if (!runSuccess) {
            cleanupExecutionEnvironment(tempDir, runProcess, containerName)
            throw CodeRuntimeException("런타임 에러\n 시간 초과")
        }

        // 런타임 실행 오류인 경우
        if (runProcess.exitValue() != 0) {

            val error = readErrorOrOutput(runProcess)
            // C, JAVA
            if (isCompileLanguage) {
                cleanupExecutionEnvironment(tempDir, runProcess, containerName)
                throw CodeRuntimeException("런타임 에러\n$error")
            }
            // Python, JavaScript
            else {
                val isSyntaxError = "SyntaxError" in error
                val isRuntimeError = "ReferenceError" in error ||
                        "TypeError" in error ||
                        "RangeError" in error ||
                        "Error" in error ||
                        "Traceback" in error
                when {
                    isSyntaxError -> throw CodeCompileException("컴파일 에러\n$error")
                    isRuntimeError -> throw CodeRuntimeException("런타임 에러\n$error")
                }
            }
        }
    }

    // 실행 환경 초기화 함수
    private fun cleanupExecutionEnvironment(tempDir: File, process: Process, containerName: String) {
        tempDir.deleteRecursively()
        process.destroy()
        Runtime.getRuntime().exec("/opt/homebrew/bin/docker rm -f $containerName")
    }

    // 에러 읽어오는 함수
    private fun readErrorOrOutput(process: Process): String {
        val output = StringBuilder()
        val stderr = BufferedReader(InputStreamReader(process.errorStream))
        while (true) {
            val line = stderr.readLine() ?: break
            output.appendLine("ERROR: $line")
        }
        return output.toString()
    }

    // 코드 실행 결과 읽어오는 함수
    private fun readStdOutput(process: Process): String {
        val output = StringBuilder()
        val stdout = BufferedReader(InputStreamReader(process.inputStream))
        while (true) {
            val line = stdout.readLine() ?: break
            output.appendLine("STDOUT: $line")
        }
        return output.toString()
    }
}