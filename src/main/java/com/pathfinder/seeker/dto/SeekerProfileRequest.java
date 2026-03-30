package com.pathfinder.seeker.dto;


import com.pathfinder.seeker.domain.SeekerExperienceLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class SeekerProfileRequest {

    @NotBlank
    private String fullName;         // → split into user.firstName / lastName

    @NotBlank
    @Email
    private String email;            // → update on User

    @NotBlank
    private String targetRole;

    private SeekerExperienceLevel experienceLevel;

    private String timezone;



    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public SeekerExperienceLevel getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(SeekerExperienceLevel experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCurrentGoals() {
        return currentGoals;
    }

    public void setCurrentGoals(String currentGoals) {
        this.currentGoals = currentGoals;
    }

    private String currentGoals;



}
