package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.GradeDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
public class GradeController {

  private final AssignmentRepository assignmentRepository;
  private final GradeRepository gradeRepository;

  public GradeController(
      AssignmentRepository assignmentRepository,
      GradeRepository gradeRepository
  ) {
    this.assignmentRepository = assignmentRepository;
    this.gradeRepository = gradeRepository;
  }

  @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
  @GetMapping("/assignments/{assignmentId}/grades")
  public List<GradeDTO> getAssignmentGrades(@PathVariable("assignmentId") int assignmentId,
      Principal principal) {

    // Query the assignment and check if it exists
    Assignment assignment = assignmentRepository.findById(assignmentId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

    // Get the section associated with the assignment
    Section section = assignment.getSection();

    // Check that the Section of the assignment belongs to the
    // logged in instructor
    String instructor = section.getInstructorEmail();
    if (!instructor.equals(principal.getName())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Invalid access, instructor only");
    }

    // return a list of GradeDTOs containing student scores for an assignment
    // Get enrollments for the section associated with the assignment
    List<Enrollment> enrollments = section.getEnrollments();

    // For each enrollment get the grade queries by the student email and assignment id
    return enrollments.stream().map(enrollment -> {
      Grade grade = gradeRepository.findByStudentEmailAndAssignmentId(
          enrollment.getStudent().getEmail(), assignmentId);
      // if a Grade entity does not exist, then create the Grade entity
      // with a null score and return the gradeId.
      if (grade == null) {
        grade = new Grade();
        grade.setAssignment(assignment);
        grade.setEnrollment(enrollment);
        grade.setScore(null);
        grade = gradeRepository.save(grade);
      }
      return new GradeDTO(
          grade.getGradeId(),
          grade.getEnrollment().getStudent().getName(),
          grade.getEnrollment().getStudent().getEmail(),
          grade.getAssignment().getTitle(),
          grade.getAssignment().getSection().getCourse().getCourseId(),
          grade.getAssignment().getSection().getSectionId(),
          grade.getScore()
      );
    }).toList();
  }


  @PutMapping("/grades")
  @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
  public void updateGrades(@Valid @RequestBody List<GradeDTO> dtoList, Principal principal) {
    // for each GradeDTO
    dtoList.forEach(dto -> {
      // check that the logged in instructor is the owner of the section
      Grade grade = gradeRepository.findById(dto.gradeId()).orElseThrow(() ->
          new ResponseStatusException(HttpStatus.NOT_FOUND, "Grade not found"));

      String instructor = grade.getAssignment().getSection().getInstructorEmail();
      if (!instructor.equals(principal.getName())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Invalid access, instructor only");
      }
      // update the assignment score
      grade.setScore(dto.score());
      gradeRepository.save(grade);
    });
  }
}
