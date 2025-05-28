package com.ssu.poo.code.service.dto

import java.io.File
import java.nio.file.Path

data class CodeExecuteSettingValueReturnDto(
    val tempDir:File,
    val containerName: String,
)
