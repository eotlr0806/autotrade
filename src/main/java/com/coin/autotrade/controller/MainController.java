package com.coin.autotrade.controller;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.SessionKey;
import com.coin.autotrade.service.RsaService;
import com.coin.autotrade.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

// main controller
@Controller
public class MainController {

    @Autowired
    UserService userService;

    @Autowired
    RsaService rsaService;

    /* Login action controller */
    @PostMapping(value ="/main")
    public ModelAndView main( HttpServletRequest request) throws Exception{

        ModelAndView mav = new ModelAndView();
        mav.addObject("serverIp", Utils.getIp());

        if(hasUserIdInSession(request)){
            mav.setViewName("layout/main");
            return mav;
        }else{
            // RSA 키를 이용한 암복호화
            String userId = rsaService.decryptRsa(request, "userId");
            String userPw = rsaService.decryptRsa(request, "userPw");
            rsaService.removePrivateKey(request); // Remove private key

            HttpSession session = request.getSession();
            if(userService.isCorrectIdAndPw(userId, userPw)){
                session.setAttribute(SessionKey.USER_ID.toString(),userId);    // save userId in session
                mav.setViewName("layout/main");
            }else{
                mav.setViewName("login/login");
                request.setAttribute("login_action", ReturnCode.FAIL.getCode());
                rsaService.makeRsaAndSaveSession(request);
            }
            return mav;
        }
    }


    /* main/auto trading */
    @GetMapping(value = "/main/auto_trading")
    public ModelAndView autoTrading(HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("contents/auto_trading");
        mav.addObject("userId",request.getSession().getAttribute(SessionKey.USER_ID.toString()));
        return mav;
    }

    /* main/coin coinfig */
    @GetMapping(value = "/main/coin_config")
    public ModelAndView coinConfig(HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("contents/coin_config");
        mav.addObject("userId",request.getSession().getAttribute(SessionKey.USER_ID.toString()));
        return mav;
    }

    /* main/trade_config trading */
    @GetMapping(value = "/main/trade_config")
    public ModelAndView tradeConfig(HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("contents/trade_config");
        mav.addObject("userId",request.getSession().getAttribute(SessionKey.USER_ID.toString()));
        return mav;
    }

    /* main/trade_config trading */
    @GetMapping(value = "/main/trade_basic")
    public ModelAndView tradeBasic(HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("contents/trade_basic");
        mav.addObject("userId",request.getSession().getAttribute(SessionKey.USER_ID.toString()));
        return mav;
    }

    /* main/trade_config trading */
    @GetMapping(value = "/main/trade_immediate")
    public ModelAndView tradeImmediate(HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("contents/trade_immediate");
        mav.addObject("userId",request.getSession().getAttribute(SessionKey.USER_ID.toString()));
        return mav;
    }


    /* main/trade trading */
    @GetMapping(value = "/main/trade_schedule")
    public ModelAndView trade(HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("contents/trade_schedule");
        mav.addObject("userId",request.getSession().getAttribute(SessionKey.USER_ID.toString()));
        return mav;
    }

    /* main/account trading */
    @GetMapping(value = "/main/account")
    public ModelAndView account(HttpServletRequest request) throws Exception{
        ModelAndView mav = new ModelAndView();
        mav.setViewName("contents/account");
        mav.addObject("userId",request.getSession().getAttribute(SessionKey.USER_ID.toString()));
        return mav;
    }


    /* chekc user id in session */
    private boolean hasUserIdInSession(HttpServletRequest request){
        HttpSession session = request.getSession();
        if(session.getAttribute(SessionKey.USER_ID.toString()) != null
                && request.getParameter("userId").equals(session.getAttribute(SessionKey.USER_ID.toString()).toString())){
            return true;
        }else{
            return false;
        }
    }
}
