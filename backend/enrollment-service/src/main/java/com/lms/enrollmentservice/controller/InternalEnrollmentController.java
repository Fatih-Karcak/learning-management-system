package com.lms.enrollmentservice.controller;


import com.lms.enrollmentservice.dto.response.ApiResponse;
import com.lms.enrollmentservice.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

 // Sadece servisler-arası kullanım — Gateway bu path'i dışa asla açmaz. */

@RestController
@RequestMapping("/api/v1/internal/enrollments")
@RequiredArgsConstructor
public class InternalEnrollmentController {
private final EnrollmentService enrollmentService;
@GetMapping("/course/{courseId}/learning")
public ApiResponse<EnrollmentService.LearningSnapshot> learning(@PathVariable UUID courseId,@RequestParam UUID studentId) {
    return ApiResponse.success(enrollmentService.learningSnapshot(studentId,courseId),"Learning status");
}
@GetMapping("/{enrollmentId}/verify")
public ResponseEntity<ApiResponse<Boolean>> verify(@PathVariable UUID enrollmentId,@RequestParam UUID courseId,@RequestParam UUID studentId){
    return ResponseEntity.ok(ApiResponse.success(enrollmentService.verifyEnrollment(enrollmentId,courseId,studentId),"Successful"));
}
@GetMapping("/course/{courseId}/check")
public ResponseEntity<ApiResponse<Boolean>> checkEnrollment(@PathVariable UUID courseId,@RequestParam UUID studentId){
    return ResponseEntity.ok(ApiResponse.success(enrollmentService.isEnrolled(studentId,courseId),"Success"));

}



}
