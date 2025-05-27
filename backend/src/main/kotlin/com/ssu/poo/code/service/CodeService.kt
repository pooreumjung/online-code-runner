package com.ssu.poo.code.service

import com.ssu.poo.code.controller.dto.ExecuteCodeRequestDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

@Service
class CodeService {

    private val log = KotlinLogging.logger {}

    fun executeCode(executeCodeRequestDto:ExecuteCodeRequestDto): String = when (executeCodeRequestDto.type.lowercase()) {
        "python" -> runPython(executeCodeRequestDto.code, executeCodeRequestDto.input)
        "java" -> runJava(executeCodeRequestDto.code,executeCodeRequestDto.input)
        "c" -> runC(executeCodeRequestDto.code,executeCodeRequestDto.input)
        else -> "error: unknown language"
    }

    // Python 실행
    private fun runPython(code: String,input:String): String = runCodeInDocker(
        code = code,
        type = "py",
        image = "python-runner",
        command = "python code.py < input.txt",
        input = input
    )

    private fun runJava(code: String,input:String): String = runCodeInDocker(
        code = code,
        type = "java",
        image = "java-runner",
        command = "javac code.java && java code < input.txt",
        input = input
    )

    private fun runC(code: String,input:String): String = runCodeInDocker(
        code = code,
        type = "c",
        image = "c-runner",
        command = "gcc code.c -o code.out && ./code.out < input.txt",
        input = input
    )

    private fun runCodeInDocker(code: String, type: String, image: String, command: String,input:String): String {
        var s: String?
        val output = StringBuilder(1024)

        try {
            log.debug { "$type 코드 실행" }

            // 디렉토리 생성
            val tempDir = File("code/$type")
            tempDir.mkdirs()

            // 파일 생성
            val sourceFile = File(tempDir, "code.$type")
            sourceFile.writeText(code)

            // 명령어 저장
            val process = ProcessBuilder(
                "/opt/homebrew/bin/docker", "run", "--rm",
                "-v", "${tempDir.absolutePath}:/app",
                "-w", "/app",
                image,
                "sh", "-c", command
            ).start()

            // 입력값 쓰기
            val inputFile = File(tempDir,"input.txt")
            inputFile.writeText(input)


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

            process.waitFor()
            process.destroy()
            tempDir.delete()

            return output.toString()
        } catch (e: Exception) {
            log.error { e.message }
            throw e
        }
    }
}

