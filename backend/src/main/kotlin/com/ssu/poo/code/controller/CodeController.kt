package com.ssu.poo.code.controller

import com.ssu.poo.code.controller.dto.ExecuteCodeRequestDto
import com.ssu.poo.code.service.CodeService
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
    fun executeCode(@RequestBody executeCodeRequestDto: ExecuteCodeRequestDto):String {
        val type :String = executeCodeRequestDto.type
        val code:String = executeCodeRequestDto.code

        val result:String = codeService.executeCode(executeCodeRequestDto)
        return result
    }
}