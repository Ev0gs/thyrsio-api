package com.pierr.thyrsio_api.user;

import com.pierr.thyrsio_api.user.dto.UpdateUserRequest;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id)
    {
        return UserResponse.from(findUserById(id));
    }

    @Transactional(readOnly = true)
    public UserResponse getByEmail(String email)
    {
        return UserResponse.from(findUserByEmail(email));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAll(Pageable pageable){
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request)
    {
        User user = findUserById(id);

        if(request.name() != null) {
            user.setName(UserInputs.normalizeName(request.name()));
        }

        if(request.email() != null) {
            String email = UserInputs.normalizeEmail(request.email());

            if(!email.equals(user.getEmail()) && userRepository.existsByEmail(email))
                throw new EmailAlreadyUsedException(email);

            user.setEmail(email);
        }

        return UserResponse.from(user);
    }

    @Transactional
    public void delete(UUID id){
        userRepository.delete(findUserById(id));
    }

    // --- Méthodes utilitaires ---

    private User findUserById(UUID id)
    {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private User findUserByEmail(String email)
    {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }
}
