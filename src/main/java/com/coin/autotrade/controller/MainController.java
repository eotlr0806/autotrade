package com.coin.autotrade.controller;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.User;
import com.coin.autotrade.service.ExchangeService;
import com.coin.autotrade.service.UserService;
import com.coin.autotrade.service.function.FlataFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

// main controller
@Controller
public class MainController {

    @Autowired
    UserService userService;

    @Autowired
    ExchangeService exchangeService;

    /**
     * 혹시 몰라서..
     * @param request
     * @param user
     * @return
     * @throws Exception
     */
    @GetMapping(value ="/main")
    public ModelAndView main( HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();

        if(request.getSession().getAttribute("userId") != null){
            mav.setViewName("layout/main");
        }else{
            ServiceCommon.initRsa(request);     // Remake RSA Key
            mav.setViewName("login/login");
        }
        return mav;
    }

    /**
     * Login action controller
     */
    @PostMapping(value ="/main")
    public ModelAndView main( HttpServletRequest request,  HttpServletResponse response) throws Exception{

        ModelAndView mav = new ModelAndView();
        // Session 이 있을경우 pass

        HttpSession session = request.getSession();
        if(session.getAttribute("userId") != null  && request.getParameter("user_id").equals(session.getAttribute("userId").toString())){

            // flat의 경우 세션 생성이 필요.

            mav.setViewName("layout/main");
            return mav;
        }

        // RSA 키를 이용한 암복호화
        String userId = ServiceCommon.decryptRsa(request, "user_id");
        String userPw = ServiceCommon.decryptRsa(request, "user_pw");
        ServiceCommon.removePrivateKey(request); // Remove private key

        User user = null;
        if( (user = userService.validateUser(userId, userPw)) != null ){
            session.setAttribute("userId",user.getUserId());    // save userId in session
            mav.setViewName("layout/main");
        }else{
            mav.setViewName("login/login");
            request.setAttribute("login_action", DataCommon.CODE_ERROR_LOGIN);
            ServiceCommon.initRsa(request);
        }
        return mav;
    }

    /**
     * main/auto trading
     * @return
     */
    @GetMapping(value = "/main/auto_trading")
    public ModelAndView autoTrading(HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("contents/auto_trading");
        mav.addObject("userId",request.getSession().getAttribute("userId"));
        return mav;
    }

    /**
     * main/coin coinfig
     * @return
     */
    @GetMapping(value = "/main/coin_config")
    public ModelAndView coinConfig(HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("contents/coin_config");
        mav.addObject("userId",request.getSession().getAttribute("userId"));
        return mav;
    }


}
