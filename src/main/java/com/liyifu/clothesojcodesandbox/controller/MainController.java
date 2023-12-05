package com.liyifu.clothesojcodesandbox.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/")
@RestController
public class MainController {

    @GetMapping("/health")
    public String healthCheck(){
        return "ok";
    }
}
