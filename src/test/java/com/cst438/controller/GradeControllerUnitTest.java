package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.*;

import com.cst438.service.RegistrarServiceProxy;
import java.util.List;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GradeControllerUnitTest {

  @Autowired
  private WebTestClient client;

  @Autowired
  private SectionRepository sectionRepository;

  @Autowired
  private GradeRepository gradeRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AssignmentRepository assignmentRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @MockitoBean
  private RegistrarServiceProxy registrarServiceProxy;

  @Test
  public void getAssignmentGradesTest() {

    // login as an instructor and get the security token
    // make sure not to self register
    String instructorEmail = "ted@csumb.edu";
    String password = "ted2025";

    // Get endpoint to receive the JWT token for the instructor user
    EntityExchangeResult<LoginDTO> login_dto = client.get()
        .uri("/login")
        .headers(headers -> headers.setBasicAuth(instructorEmail, password))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody(LoginDTO.class)
        .returnResult();

    String jwt = login_dto.getResponseBody().jwt();
    assertNotNull(jwt);

    // Get sections for the instructor
    List<Section> sections = sectionRepository.findByInstructorEmailOrderBySectionNo(
        instructorEmail);
    assertNotNull(sections);
    assertFalse(sections.isEmpty());

    // Grab the section for course cst489 from the list of sections
    Section section = sections.stream()
        .filter(s -> s.getCourse().getCourseId().equals("cst489"))
        .findFirst()
        .orElseThrow();

    // Use a student from data.sql
    User student = userRepository.findByEmail("sam@csumb.edu");
    assertNotNull(student);

    // Create dummy enrollment
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(100);
    enrollment.setGrade(null);
    enrollment.setSection(section);
    enrollment.setStudent(student);
    enrollmentRepository.save(enrollment);

    // Create dummy assignment
    Assignment assignment = new Assignment();
    assignment.setTitle("Test Assignment");
    assignment.setDueDate(Date.valueOf(LocalDate.now().plusDays(7)));
    assignment.setSection(section);
    assignment = assignmentRepository.save(assignment);

    // Call helper method to utilize webclient for the get mapping endpoint
    List<GradeDTO> grades = getGradesForAssignment(assignment.getAssignmentId(), jwt);
    assertNotNull(grades);

    // Receive the GradeDTO from the list where student email and assignment title is same as
    // the dummy versions. Checking one returned GradeDTO and comparing it to dummy values.
    Assignment finalAssignment = assignment;
    GradeDTO actual = grades.stream()
        .filter(g -> g.studentEmail().equals(student.getEmail()) && g.assignmentTitle().equals(
            finalAssignment.getTitle()))
        .findFirst()
        .orElse(null);
    assertNotNull(actual);

    // Find grade by student email and the assignment id,
    // After the endpoint, a grade should be created if no grade existed,
    // it should contain the assignment and enrollment and a null score
    // Query with the grade repository
    Grade createdGrade = gradeRepository.findByStudentEmailAndAssignmentId(
        "sam@csumb.edu", assignment.getAssignmentId());
    assertNotNull(createdGrade);

    // Verify that the returned GradeDTO has the correct values
    assertEquals(createdGrade.getGradeId(), actual.gradeId());
    assertEquals(student.getName(), actual.studentName());
    assertEquals(student.getEmail(), actual.studentEmail());
    assertEquals(assignment.getTitle(), actual.assignmentTitle());
    assertEquals(assignment.getSection().getCourse().getCourseId(), actual.courseId());
    assertEquals(assignment.getSection().getSectionId(), actual.sectionId());
    assertNull(actual.score(), "Score should be null for a new grade");

    // Testing a second time but with a created grade entity so upon requesting endpoint
    // it won't create a new grade
    Section section2 = sections.stream()
        .filter(s -> s.getCourse().getCourseId().equals("cstTest"))
        .findFirst()
        .orElseThrow();

    // Create second dummy enrollment with SAME STUDENT but second section in data.sql
    Enrollment enrollment2 = new Enrollment();
    enrollment2.setEnrollmentId(101);
    enrollment2.setGrade(null);
    enrollment2.setSection(section2);
    enrollment2.setStudent(student);
    enrollmentRepository.save(enrollment2);

    // Create second dummy assignment
    Assignment assignment2 = new Assignment();
    assignment2.setTitle("Test Assignment");
    assignment2.setDueDate(Date.valueOf(LocalDate.now().plusDays(7)));
    assignment2.setSection(section2);
    assignment2 = assignmentRepository.save(assignment2);

    // Create a grade object
    Grade grade = new Grade();
    grade.setAssignment(assignment2);
    grade.setEnrollment(enrollment2);
    grade.setScore(95);
    gradeRepository.save(grade);

    // Call helper method with the WebClient for requesting endpoint
    List<GradeDTO> grades2 = getGradesForAssignment(assignment2.getAssignmentId(), jwt);
    assertNotNull(grades2);

    // Receive the GradeDTO from the list where student email and assignment title is same as
    // the dummy versions. Checking one returned GradeDTO and comparing it to dummy values.
    Assignment finalAssignment2 = assignment2;
    GradeDTO actual2 = grades2.stream()
        .filter(g -> g.studentEmail().equals(student.getEmail()) && g.assignmentTitle().equals(
            finalAssignment2.getTitle()))
        .findFirst()
        .orElse(null);
    assertNotNull(actual2);

    // Find grade by student email and the assignment id,
    // After the endpoint, the grade was already created so it should be the same as the
    // dummy values
    Grade createdGrade2 = gradeRepository.findByStudentEmailAndAssignmentId(
        "sam@csumb.edu", assignment2.getAssignmentId());
    assertNotNull(createdGrade2);

    // Verify that the returned GradeDTO has the correct values
    assertEquals(createdGrade2.getGradeId(), actual2.gradeId());
    assertEquals(grade.getGradeId(), actual2.gradeId());
    assertEquals(enrollment2.getStudent().getName(), actual2.studentName());
    assertEquals(enrollment2.getStudent().getEmail(), actual2.studentEmail());
    assertEquals(grade.getAssignment().getTitle(), actual2.assignmentTitle());
    assertEquals(grade.getAssignment().getSection().getCourse().getCourseId(), actual2.courseId());
    assertEquals(grade.getAssignment().getSection().getSectionId(), actual2.sectionId());
    assertNotNull(actual2.score(), "Score should not be null for a grade with a score");
    assertEquals(95, actual2.score(), "Score should be 95 for the second grade");
  }

  // Helper method to initiate the endpoint
  private List<GradeDTO> getGradesForAssignment(int assignmentId, String jwt) {
    EntityExchangeResult<List<GradeDTO>> result = client.get()
        .uri("/assignments/" + assignmentId + "/grades")
        .headers(headers -> headers.setBearerAuth(jwt))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBodyList(GradeDTO.class)
        .returnResult();

    return result.getResponseBody();
  }
}
