package com.ssu.poo.common

import lombok.RequiredArgsConstructor
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

@RestControllerAdvice
class GlobalControllerAdvice : ResponseBodyAdvice<Any> {

    private val log = LoggerFactory.getLogger(GlobalControllerAdvice::class.java)

    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>
    ): Boolean = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse
    ): Any? {
        val path = request.uri.path

        // Swagger, HTML 응답은 그대로
        if (
            path.startsWith("/swagger-ui") || path.startsWith("/api-docs") ||
            selectedContentType.includes(MediaType.TEXT_HTML)
        ) return body

        // CommonResponse 상태 코드 매핑
        if (body is ApiResponse) {
            val httpCode = body.code.toInt() ?: 500
            response.setStatusCode(org.springframework.http.HttpStatusCode.valueOf(httpCode))
            return body
        }

        // 그 외는 500 오류로 감싸기
        response.setStatusCode(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
        return ApiResponse(
            code = "5000",
            result = "실행 시간 초과",
            message = "INTERNAL_SERVER_ERROR",
        )
    }

    @ExceptionHandler(InterruptedException::class)
    fun handleInterruptedException(e:InterruptedException): ApiResponse {
        log.error("InterruptedException", e)
        return ApiResponse("500", e.message.toString(), "INTERNAL_SERVER_ERROR")
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(e:RuntimeException): ApiResponse {
        log.error("InterruptedException", e)
        return ApiResponse("500", e.message.toString(), "INTERNAL_SERVER_ERROR")
    }


}