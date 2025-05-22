package com.ssu.poo.code.controller.dto

data class ExecuteCodeRequestDto(
    val code:String,
    val type:String,
    val input:String
)
