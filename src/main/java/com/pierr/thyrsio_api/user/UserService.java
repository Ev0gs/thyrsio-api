package com.pierr.thyrsio_api.user;

import com.pierr.thyrsio_api.user.dto.CreateUserRequest;
import com.pierr.thyrsio_api.user.dto.UpdateUserRequest;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request)
    {
        String name = normalizeName(request.name());
        String email = normalizeEmail(request.email());

        if(userRepository.existsByEmail(email))
            throw new EmailAlreadyUsedException(email);

        User saved = userRepository.save(new User(name, email));
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id)
    {
        return UserResponse.from(findUser(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAll(Pageable pageable){
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request)
    {
        User user = findUser(id);

        if(request.name() != null) {
            user.setName(normalizeName(request.name()));
        }

        if(request.email() != null) {
            String email = normalizeEmail(request.email());

            if(!email.equals(user.getEmail()) && userRepository.existsByEmail(email))
                throw new EmailAlreadyUsedException(email);

            user.setEmail(email);
        }

        return UserResponse.from(user);
    }

    @Transactional
    public void delete(UUID id){
        userRepository.delete(findUser(id));
    }

    // --- Méthodes utilitaires ---

    private User findUser(UUID id)
    {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private static String normalizeName(String name){
        String trimmed = name.trim();
        if(trimmed.isEmpty()) {
            throw new IllegalArgumentException("Le nom ne peut pas être vide");
        }
        return trimmed;
    }

    private static String normalizeEmail(String email)
    {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
