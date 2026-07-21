package com.cst438.domain;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EnrollmentRepository extends CrudRepository<Enrollment, Integer> {
    @Query("select e from Enrollment e where e.section.sectionNo=:sectionNo order by e.student.name")
    List<Enrollment> findEnrollmentsBySectionNoOrderByStudentName(int sectionNo);

    @Query("select e from Enrollment e where e.section.sectionNo=:sectionNo and e.student.id =:studentId")
    Enrollment findBySectionAndStudent(int sectionNo, int studentId);

}
