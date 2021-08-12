package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

@Service
@Slf4j
public class UserService {

    @Autowired
    UserRepository repository;

    /**
     * ID/PASSWORD 체크를 위한 로직
     * @param userId
     * @param userPw
     * @return
     */
    public User validateUser(String userId, String userPw) {
        User user = null;
        try{
            user = repository.findByUserId(userId);
            // TODO : 나중에 암복호화 필요.
            if(user == null || !(user.getUserId().equals(userId) && user.getUserPw().equals(userPw)) ){
                return null;
            }
        }catch(Exception e){
            log.error("[ERROR][Validate User] {}", e.getMessage());
        }
        return user;
    }

    /**
     * User 정보를 가져오기 위한 로직
     * @param id
     * @return
     */
    public User getUser(String userId) {
        User user = null;
        try{
            user = repository.findByUserId(userId);
        }catch(Exception e){
            log.error("[ERROR][Get User] {}", e.getMessage());
        }

        return user;
    }



}
