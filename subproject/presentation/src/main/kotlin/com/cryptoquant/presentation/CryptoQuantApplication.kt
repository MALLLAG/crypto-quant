package com.cryptoquant.presentation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.cryptoquant"])
class CryptoQuantApplication

fun main(args: Array<String>) {
    runApplication<CryptoQuantApplication>(*args)
}
