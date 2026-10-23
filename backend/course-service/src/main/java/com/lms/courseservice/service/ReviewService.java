package com.lms.courseservice.service;

import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.domain.entity.Review;
import com.lms.courseservice.dto.request.CreateReviewRequest;
import com.lms.courseservice.dto.response.ReviewResponse;
import com.lms.courseservice.exception.CourseNotFoundException;
import com.lms.courseservice.mapper.ReviewMapper;
import com.lms.courseservice.repository.CourseRepository;
import com.lms.courseservice.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final ReviewMapper reviewMapper;
    private final CourseAccessPolicy accessPolicy;

    @Transactional
    public ReviewResponse createReview(UUID courseId, CreateReviewRequest request,
                                       UUID studentId, String studentName,
                                       String studentAvatarUrl) {
        accessPolicy.requireEnrolled(courseId, studentId);
        if (reviewRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw new IllegalStateException("Bu kursa zaten yorum yaptınız");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Kurs bulunamadı"));

        Review review = Review.builder()
                .course(course)
                .studentId(studentId)
                .studentName(studentName)
                .studentAvatarUrl(studentAvatarUrl)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        review = reviewRepository.save(review);

        // Ortalama puanı güncelle
        updateCourseRating(course);

        return reviewMapper.toResponse(review);
    }

    public Page<ReviewResponse> getCourseReviews(UUID courseId, Pageable pageable) {
        return reviewRepository.findByCourseId(courseId, pageable)
                .map(reviewMapper::toResponse);
    }

    @Transactional
    public void deleteReview(UUID reviewId, UUID studentId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CourseNotFoundException("Yorum bulunamadı"));

        if (!review.getStudentId().equals(studentId)) {
            throw new IllegalStateException("Bu yorumu silme yetkiniz yok");
        }

        Course course = review.getCourse();
        reviewRepository.delete(review);
        updateCourseRating(course);
    }

    private void updateCourseRating(Course course) {
        Double avg = reviewRepository.calculateAverageRating(course.getId());
        long count = reviewRepository.countByCourseId(course.getId());
        course.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
        course.setTotalReviews((int) count);
        courseRepository.save(course);
    }
}
