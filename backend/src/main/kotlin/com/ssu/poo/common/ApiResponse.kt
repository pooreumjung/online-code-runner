package com.ssu.poo.common

data class ApiResponse<Any>(
    val code : String,
    val data:Any?=null,
    val message:String
)
