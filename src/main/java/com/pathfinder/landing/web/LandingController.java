package com.pathfinder.landing.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {

  @GetMapping("/")
  public String index() {
    return "landing/index";
  }
}
