package com.ssu.poo.code.controller

import com.ssu.poo.code.controller.dto.CodeExecuteRequestDto
import com.ssu.poo.code.service.CodeService
import com.ssu.poo.common.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/v1/code")
class CodeController (
    private val codeService: CodeService
){

    @PostMapping("/execute")
    fun executeCode(@RequestBody codeExecuteRequestDto: CodeExecuteRequestDto):ApiResponse<Any> {
        val result:String = codeService.executeCode(codeExecuteRequestDto)
        return ApiResponse("200", result, "code execute success")
    }
}