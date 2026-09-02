package dev.flinku.sdk

class FlinkuHttpException(message: String, val statusCode: Int, cause: Throwable? = null) :
    Exception(message, cause)
