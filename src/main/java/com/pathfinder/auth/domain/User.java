package com.pathfinder.auth.domain;


import jakarta.persistence.*;

import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Data
public class User {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String confirmPassword;
    private String role;
    private String account_status;
    private LocalDate created_at;
    private LocalDate updated_at;



}
