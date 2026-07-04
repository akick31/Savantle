package com.savantle.backend.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping(value = ["/{path:[^\\.]*}", "/{path:[^\\.]*}/**"])
class SpaController {
    @GetMapping
    fun forward(): String = "forward:/index.html"
}
