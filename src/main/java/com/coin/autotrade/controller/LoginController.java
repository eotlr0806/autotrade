package com.coin.autotrade.controller;

import com.coin.autotrade.common.enumeration.SessionKey;
import com.coin.autotrade.service.RsaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

// main controller
@Controller
public class LoginController {

    @Autowired
    RsaService rsaService;

    /* Login main page controller */
    @GetMapping(value = {"/","/index","/login"} )
    public ModelAndView login(HttpServletRequest request) throws Exception{
        rsaService.makeRsaAndSaveSession(request);

        ModelAndView mav = new ModelAndView();
        mav.setViewName("login/login");
        return mav;
    }

    /* logout */
    @GetMapping(value ="/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().removeAttribute(SessionKey.USER_ID.toString());
        rsaService.makeRsaAndSaveSession(request);
        return "login/login";
    }

}
