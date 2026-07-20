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
import org.springframework.expression.spel.ast.Assign;
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

    // Call login helper to obtain the jwt
    String jwt = login(instructorEmail, password);

    // Get sections for the instructor
    List<Section> sections = sectionRepository.findByInstructorEmailOrderBySectionNo(
        instructorEmail);
    assertNotNull(sections);
    assertFalse(sections.isEmpty());

    // Grab the first section for testing purposes
    Section section = sections.stream()
        .filter(s -> s.getSectionNo() == 1)
        .findFirst()
        .orElseThrow();

    // Use a student from data.sql
    User student = userRepository.findByEmail("sam@csumb.edu");
    assertNotNull(student);

    // Get an enrollment from enrollment repository
    Enrollment enrollment = enrollmentRepository.findBySectionAndStudent(section.getSectionNo(),
        student.getId());
    assertNotNull(enrollment);
    assertEquals(1, enrollment.getEnrollmentId());
    assertEquals(section.getSectionNo(), enrollment.getSection().getSectionNo());
    assertEquals(student.getId(), enrollment.getStudent().getId());

    // Grabbing the first predictable test assignment from data.sql
    Assignment assignment = assignmentRepository.findById(6000).orElseThrow();
    assertNotNull(assignment);
    assertEquals("Project 1", assignment.getTitle());

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
    // After the endpoint, the grade was already created so it should be the same as the dummy values
    Grade createdGrade = gradeRepository.findByStudentEmailAndAssignmentId(
        "sam@csumb.edu", assignment.getAssignmentId());
    assertNotNull(createdGrade);
    assertNotNull(createdGrade.getScore(), "Score should not be null for the first grade");

    // Verify that the returned GradeDTO has the correct values
    assertEquals(createdGrade.getGradeId(), actual.gradeId());
    assertEquals(enrollment.getStudent().getName(), actual.studentName());
    assertEquals(enrollment.getStudent().getEmail(), actual.studentEmail());
    assertEquals(assignment.getTitle(), actual.assignmentTitle());
    assertEquals(assignment.getSection().getCourse().getCourseId(), actual.courseId());
    assertEquals(assignment.getSection().getSectionId(), actual.sectionId());
    assertEquals(createdGrade.getScore(), actual.score());

    // Testing a second time but without a created grade object, finding assignment will create one
    Section section2 = sections.stream()
        .filter(s -> s.getSectionNo() == 2)
        .findFirst()
        .orElseThrow();

    // Grabs the second enrollment test
    Enrollment enrollment2 = enrollmentRepository.findBySectionAndStudent(section2.getSectionNo(),
        student.getId());
    assertNotNull(enrollment2);

    // Grabs an assignment with the same section
    Assignment assignment2 = assignmentRepository.findById(6002).orElseThrow();
    assertNotNull(assignment2);
    assertEquals("Test Assignment", assignment2.getTitle());

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
    // After the endpoint, the grade is created since correlated assignment grade was null
    Grade createdGrade2 = gradeRepository.findByStudentEmailAndAssignmentId(
        "sam@csumb.edu", assignment2.getAssignmentId());
    assertNotNull(createdGrade2);
    assertNull(createdGrade2.getScore(), "Score should be null for the second grade");

    // Verify that the returned GradeDTO has the correct values
    assertEquals(createdGrade2.getGradeId(), actual2.gradeId());
    assertEquals(enrollment2.getStudent().getName(), actual2.studentName());
    assertEquals(enrollment2.getStudent().getEmail(), actual2.studentEmail());
    assertEquals(assignment2.getTitle(), actual2.assignmentTitle());
    assertEquals(assignment2.getSection().getCourse().getCourseId(), actual2.courseId());
    assertEquals(assignment2.getSection().getSectionId(), actual2.sectionId());
    assertNull(actual2.score(), "Score should be null for the second grade");
  }

  @Test
  public void updateGradesTest() {
    // login as an instructor and get the security token
    // make sure not to self register
    String instructorEmail = "ted@csumb.edu";
    String password = "ted2025";

    // Call login helper to obtain the jwt
    String jwt = login(instructorEmail, password);

    // Get an enrollment for testing
    Enrollment enrollment = enrollmentRepository.findById(1).orElseThrow();
    assertEquals(1, enrollment.getEnrollmentId());

    // Create a dummy GradeDTO to update the score
    List<GradeDTO> dtoList = gradeRepository.findByStudentEmail(enrollment.getStudent().getEmail())
        .stream()
        .map(g -> new GradeDTO(
            g.getGradeId(),
            g.getEnrollment().getStudent().getName(),
            g.getEnrollment().getStudent().getEmail(),
            g.getAssignment().getTitle(),
            g.getAssignment().getSection().getCourse().getCourseId(),
            g.getAssignment().getSection().getSectionId(),
            100 // Update the score to 100 for testing
        )).toList();

    client.put().uri("/grades")
        .headers(headers -> headers.setBearerAuth(jwt))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dtoList)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk();

    // Verify that the grade score was updated in the database
    dtoList.forEach(dto -> {
      Grade updatedGrade = gradeRepository.findById(dto.gradeId()).orElseThrow();
      assertEquals(100, updatedGrade.getScore(), "Score should be updated to 100");
    });

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

  // Helper method to login
  private String login(String email, String password) {
    EntityExchangeResult<LoginDTO> login_dto = client.get()
        .uri("/login")
        .headers(headers -> headers.setBasicAuth(email, password))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody(LoginDTO.class)
        .returnResult();
    String jwt = login_dto.getResponseBody().jwt();
    assertNotNull(jwt);
    return jwt;
  }
}
