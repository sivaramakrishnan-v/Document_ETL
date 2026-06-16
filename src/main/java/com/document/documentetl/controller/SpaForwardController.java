package com.document.documentetl.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/dashboard",
            "/documents",
            "/upload",
            "/pipeline",
            "/ask",
            "/results",
            "/tokens",
            "/observability"
    })
    public String forwardAngularRoutes() {
        return "forward:/index.html";
    }
}
