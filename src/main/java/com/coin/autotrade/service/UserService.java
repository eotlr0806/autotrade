package com.coin.autotrade.service;

import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

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
    public boolean isCorrectIdAndPw(String userId, String userPw) {
        boolean validation = false;
        try{
            Optional<User> optionalUser = repository.findById(userId);
            User user                   = optionalUser.get();
            if(user.getUserPw().equals(userPw)){
                validation = true;
            }
        }catch (NoSuchElementException e){
            log.error("[USER_SERVICE] NOT FOUND USER ID: {}", userId);
            e.printStackTrace();
        }catch(Exception e){
            log.error("[USER_SERVICE] OCCUR ERROR USER ID: {}", userId);
            e.printStackTrace();
        }
        return validation;
    }
}
