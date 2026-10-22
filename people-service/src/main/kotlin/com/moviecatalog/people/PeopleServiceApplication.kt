package com.moviecatalog.people

import com.moviecatalog.support.GrpcConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@Import(GrpcConfig::class)
class PeopleServiceApplication

fun main(args: Array<String>) {
    runApplication<PeopleServiceApplication>(*args)
}
