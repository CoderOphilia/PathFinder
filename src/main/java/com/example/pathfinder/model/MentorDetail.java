package com.example.pathfinder.model;


import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "mentorDetail")
@Data
public class MentorDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "userprofile_id")
    private UserProfile userProfile;

    private String industry;

    private int yearsOfExperience;

    private float hourlyRate;

    private String jobTitle;

}
