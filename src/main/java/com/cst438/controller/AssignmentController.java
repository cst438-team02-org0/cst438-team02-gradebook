package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.AssignmentDTO;
import com.cst438.dto.AssignmentStudentDTO;
import com.cst438.dto.SectionDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.sql.Date;
import java.util.List;

@RestController
public class AssignmentController {

  private final SectionRepository sectionRepository;
  private final AssignmentRepository assignmentRepository;
  private final GradeRepository gradeRepository;
  private final UserRepository userRepository;

  public AssignmentController(
      SectionRepository sectionRepository,
      AssignmentRepository assignmentRepository,
      GradeRepository gradeRepository,
      UserRepository userRepository
  ) {
    this.sectionRepository = sectionRepository;
    this.assignmentRepository = assignmentRepository;
    this.gradeRepository = gradeRepository;
    this.userRepository = userRepository;
  }

  // get Sections for an instructor
  @GetMapping("/sections")
  @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
  public List<SectionDTO> getSectionsForInstructor(
      @RequestParam("year") int year,
      @RequestParam("semester") String semester,
      Principal principal) {
    // return the Sections that have instructorEmail for the
    // logged in instructor user for the given term.
    List<Section> sections =
        sectionRepository.findByInstructorEmailAndYearAndSemester(
            principal.getName(),
            year,
            semester
        );
    User instructor = userRepository.findByEmail(principal.getName());
    return sections.stream().map(section -> new SectionDTO(
        section.getSectionNo(),
        section.getTerm().getYear(),
        section.getTerm().getSemester(),
        section.getCourse().getCourseId(),
        section.getCourse().getTitle(),
        section.getSectionId(),
        section.getBuilding(),
        section.getRoom(),
        section.getTimes(),
        instructor.getName(),
        section.getInstructorEmail()
    )).toList();
  }

  // instructor lists assignments for a section.
  @GetMapping("/sections/{secNo}/assignments")
  @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
  public List<AssignmentDTO> getAssignments(
      @PathVariable("secNo") int secNo,
      Principal principal) {
    Section section = sectionRepository.findById(secNo).orElse(null);
    if (section == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "section not found"
      );
    }
    // verify that user is the instructor for the section
    if (!section.getInstructorEmail().equals(principal.getName())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "User is not the instructor for this section"
      );
    }
    // return list of assignments for the Section
    return section.getAssignments().stream().map(assignment ->
        new AssignmentDTO(
            assignment.getAssignmentId(),
            assignment.getTitle(),
            assignment.getDueDate().toString(),
            assignment.getSection().getCourse().getCourseId(),
            assignment.getSection().getSectionId(),
            assignment.getSection().getSectionNo()
        )
    ).toList();
  }

  @PostMapping("/assignments")
  @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
  public AssignmentDTO createAssignment(
      @Valid @RequestBody AssignmentDTO dto,
      Principal principal) {
    Section section = sectionRepository.findById(dto.secNo()).orElse(null);
    if (section == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "section not found"
      );
    }
    // user must be the instructor for the Section
    if (!section.getInstructorEmail().equals(principal.getName())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "User is not the instructor for this section"
      );
    }
    Date dueDate = convertDueDate(dto.dueDate());
    // check that assignment dueDate is between start date and
    // end date of the term
    validateDueDate(dueDate, section);
    // create and save an Assignment entity
    Assignment assignment = new Assignment();
    assignment.setTitle(dto.title());
    assignment.setDueDate(dueDate);
    assignment.setSection(section);
    assignment = assignmentRepository.save(assignment);
    // return AssignmentDTO with database generated primary key
    return createAssignmentDTO(assignment);
  }

  @PutMapping("/assignments")
  @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
  public AssignmentDTO updateAssignment(
      @Valid @RequestBody AssignmentDTO dto,
      Principal principal) {
    Assignment assignment =
        assignmentRepository.findById(dto.id()).orElse(null);
    if (assignment == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "assignment not found, id=" + dto.id()
      );
    }
    // user must be instructor of the Section
    if (!assignment.getSection().getInstructorEmail()
        .equals(principal.getName())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "User is not the instructor for this section"
      );
    }
    Date dueDate = convertDueDate(dto.dueDate());
    validateDueDate(dueDate, assignment.getSection());
    // update Assignment Entity.
    // Only title and dueDate fields can be changed.
    assignment.setTitle(dto.title());
    assignment.setDueDate(dueDate);
    assignment = assignmentRepository.save(assignment);
    return createAssignmentDTO(assignment);
  }

  @DeleteMapping("/assignments/{assignmentId}")
  @PreAuthorize("hasAuthority('SCOPE_ROLE_INSTRUCTOR')")
  public void deleteAssignment(
      @PathVariable("assignmentId") int assignmentId,
      Principal principal) {
    Assignment assignment =
        assignmentRepository.findById(assignmentId).orElse(null);
    if (assignment == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "assignment not found, id=" + assignmentId
      );
    }
    // verify that user is the instructor of the section
    if (!assignment.getSection().getInstructorEmail()
        .equals(principal.getName())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "User is not the instructor for this section"
      );
    }
    /*
     * Grade has a foreign key to Assignment, and Assignment does not
     * currently declare cascade delete. Delete related grades first.
     */
    if (assignment.getGrades() != null) {
      gradeRepository.deleteAll(assignment.getGrades());
    }
    // delete the Assignment entity
    assignmentRepository.delete(assignment);
  }

  // student lists their assignments/grades ordered by due date
  @GetMapping("/assignments")
  @PreAuthorize("hasAuthority('SCOPE_ROLE_STUDENT')")
  public List<AssignmentStudentDTO> getStudentAssignments(
      @RequestParam("year") int year,
      @RequestParam("semester") String semester,
      Principal principal) {
    String studentEmail = principal.getName();
    List<Assignment> assignments =
        assignmentRepository.findByStudentEmailAndYearAndSemester(
            studentEmail,
            year,
            semester
        );
    // return AssignmentStudentDTOs with scores if a Grade entity exists.
    // If assignment has not been graded, return a null score.
    return assignments.stream().map(assignment -> {
      Grade grade =
          gradeRepository.findByStudentEmailAndAssignmentId(
              studentEmail,
              assignment.getAssignmentId()
          );
      Integer score = grade == null ? null : grade.getScore();
      return new AssignmentStudentDTO(
          assignment.getAssignmentId(),
          assignment.getTitle(),
          assignment.getDueDate(),
          assignment.getSection().getCourse().getCourseId(),
          assignment.getSection().getSectionId(),
          score
      );
    }).toList();
  }

  // helper method to convert an assignment entity into an assignmentDTO for API response
  // avoids repeating the same DTO creation code after creating/updating assignment
  private AssignmentDTO createAssignmentDTO(Assignment assignment) {
    return new AssignmentDTO(
        assignment.getAssignmentId(),
        assignment.getTitle(),
        assignment.getDueDate().toString(),
        assignment.getSection().getCourse().getCourseId(),
        assignment.getSection().getSectionId(),
        assignment.getSection().getSectionNo()
    );
  }

  // helper method converts due date string -> java.sql.Date
  // returns BAD_REQUEST if date is missing or not in yyy-mm-dd format
  private Date convertDueDate(String dueDate) {
    try {
      return Date.valueOf(dueDate);
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "due date must be in yyyy-mm-dd format"
      );
    }
  }

  // helper method to verify assignment due date is w/in start-end of section's term
  // returns BAD_REQUEST if date is outside term
  private void validateDueDate(Date dueDate, Section section) {
    Date startDate = section.getTerm().getStartDate();
    Date endDate = section.getTerm().getEndDate();
    if (dueDate.before(startDate) || dueDate.after(endDate)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "assignment due date must be between the term start and end dates"
      );
    }
  }
}