package com.anatawa12.sai.stia


class StiaUnsupportedOperationException : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

fun unsupported(message: String): Nothing {
    throw StiaUnsupportedOperationException("unsupported: $message")
}
