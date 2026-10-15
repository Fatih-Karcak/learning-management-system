package com.lms.courseservice.repository;

import com.lms.courseservice.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByParentIsNull();
}