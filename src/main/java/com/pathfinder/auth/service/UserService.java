package com.pathfinder.auth.service;


import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UserDetailsService;

@Service
public class UserService {
    private final UserRepository userRepo;

    public UserService(UserRepository repo) {
        this.userRepo = repo;
    }

    public User createUser(User user){

        if (userRepo.findByEmail(user.getEmail()).isPresent()) {

            throw new IllegalArgumentException("A user with this email already exists");
        }
        return userRepo.save(user);
    }


    public  User findUserByEmail(String email) {
        return userRepo.findByEmail(email).orElse(null);
    }

    public  boolean emailExists(String email)
    {
        return userRepo.findByEmail(email).isPresent();
    }













}











