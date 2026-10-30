package com.pierr.thyrsio_api.auth;

import com.pierr.thyrsio_api.auth.dto.RegisterRequest;
import com.pierr.thyrsio_api.user.EmailAlreadyUsedException;
import com.pierr.thyrsio_api.user.User;
import com.pierr.thyrsio_api.user.UserInputs;
import com.pierr.thyrsio_api.user.UserRepository;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = UserInputs.normalizeEmail(request.email());
        String name = UserInputs.normalizeName(request.name());

        if(userRepository.existsByEmail(email))
            throw new EmailAlreadyUsedException(email);

        String passwordHash = passwordEncoder.encode(request.password());

        User saved = userRepository.save(new User(name, email, passwordHash));
        return UserResponse.from(saved);
    }

}
