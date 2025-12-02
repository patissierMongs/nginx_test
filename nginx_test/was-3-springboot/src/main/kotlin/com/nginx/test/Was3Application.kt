package com.nginx.test

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class Was3Application

fun main(args: Array<String>) {
    runApplication<Was3Application>(*args)
}
