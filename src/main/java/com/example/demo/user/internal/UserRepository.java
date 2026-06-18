package com.example.demo.user.internal;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import com.example.demo.user.User;

@RepositoryRestResource()
public interface UserRepository extends CrudRepository<User, Integer> {

}