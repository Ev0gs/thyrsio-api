package com.pierr.thyrsio_api.user;

import com.pierr.thyrsio_api.security.AppUserPrincipal;
import com.pierr.thyrsio_api.user.dto.UpdateUserRequest;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse get(@AuthenticationPrincipal AppUserPrincipal me) {
        return userService.getById(me.id());
    }

    @PatchMapping
    public UserResponse udpate(@AuthenticationPrincipal AppUserPrincipal me,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(me.id(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal me,
                       HttpServletRequest request) {
        userService.delete(me.id());

        HttpSession session = request.getSession(false);
        if(session != null)
            session.invalidate();
        SecurityContextHolder.clearContext();
    }
}
