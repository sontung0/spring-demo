package com.example.demo.user;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")

public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  public Integer id;

  public String name;
  
  public String email;
}