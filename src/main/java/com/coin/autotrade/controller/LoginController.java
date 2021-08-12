package com.coin.autotrade.controller;

import com.coin.autotrade.common.ServiceCommon;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// main controller
@Controller
public class LoginController {

    /**
     * Login main page controller
     */
    @GetMapping(value = {"/","/index","/login"} )
    public ModelAndView login(
            HttpServletRequest request,
            HttpServletResponse response) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("login/login");

        ServiceCommon.initRsa(request);

        return mav;
    }

    /**
     * logout
     * @param request
     * @param response
     * @param status
     * @return
     */
    @GetMapping(value ="/logout")
    public String logout(HttpServletRequest request,
                         HttpServletResponse response,
                         SessionStatus status) {
        request.getSession().removeAttribute("userId");
        ServiceCommon.initRsa(request);     // Remake RSA Key
        return "login/login";
    }

}
