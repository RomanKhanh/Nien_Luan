package com.ctu.hehe.controller;

import com.ctu.hehe.dto.request.CreateUserRequestTesting;
import com.ctu.hehe.dto.response.ApiResponse;
import com.ctu.hehe.entity.User;
import com.ctu.hehe.service.UserServiceTesting;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserControllerTesting {
    private final UserServiceTesting userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> registerUser(@RequestBody CreateUserRequestTesting request){
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(user));
    }
}
