package com.lms.enrollmentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInfoResponse(UUID id, String email, String firstName, String lastName,String role,String status,boolean emailVerified) {
    public UserInfoResponse(UUID id,String email,String firstName,String lastName) {this(id,email,firstName,lastName,"STUDENT","ACTIVE",true);}
}
