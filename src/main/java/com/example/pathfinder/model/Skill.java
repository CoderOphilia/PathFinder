package com.example.pathfinder.model;

import com.example.pathfinder.enums.SkillLevel;
import jakarta.persistence.*;
import lombok.Data;



@Entity
@Table(name = "skills")
@Data
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String skillName;


    @Enumerated(EnumType.STRING)
    private SkillLevel level;





    
}
