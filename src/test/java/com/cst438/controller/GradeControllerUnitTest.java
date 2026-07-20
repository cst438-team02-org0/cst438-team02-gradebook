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
    List<Section> sections = sectionRepository.findByInstructorEmail(instructorEmail);
    assertNotNull(sections);
    assertFalse(sections.isEmpty());

    // Grab the first one for test purposes
    Section section = sections.get(0);

    // Use student from data.sql
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

    EntityExchangeResult<List<GradeDTO>> result = client.get()
        .uri("/assignments/" + assignment.getAssignmentId() + "/grades")
        .headers(headers -> headers.setBearerAuth(jwt))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBodyList(GradeDTO.class)
        .returnResult();

    List<GradeDTO> grades = result.getResponseBody();
    assertNotNull(grades);

    // Check if the grade for the student exists
    Assignment finalAssignment = assignment;
    GradeDTO actual = grades.stream()
        .filter(g -> g.studentEmail().equals(student.getEmail()) && g.assignmentTitle().equals(
            finalAssignment.getTitle()))
        .findFirst()
        .orElse(null);
    assertNotNull(actual);

    // Find grade by enrollment id
    Grade createdGrade = gradeRepository.findByStudentEmailAndAssignmentId(
        "sam@csumb.edu", assignment.getAssignmentId());
    assertNotNull(createdGrade);

    // Check that the returned GradeDTO has the correct values
    // Check if a gradeid was created
    assertEquals(createdGrade.getGradeId(), actual.gradeId());
    assertEquals(student.getName(), actual.studentName());
    assertEquals(student.getEmail(), actual.studentEmail());
    assertEquals(assignment.getTitle(), actual.assignmentTitle());
    assertEquals(assignment.getSection().getCourse().getCourseId(), actual.courseId());
    assertEquals(assignment.getSection().getSectionId(), actual.sectionId());
    assertNull(actual.score(), "Score should be null for a new grade");


    //
  }
}
