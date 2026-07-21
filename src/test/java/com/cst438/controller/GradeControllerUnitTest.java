package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.*;

import com.cst438.service.RegistrarServiceProxy;
import java.util.List;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
  private TermRepository termRepository;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AssignmentRepository assignmentRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @MockitoBean
  private RegistrarServiceProxy registrarServiceProxy;

  private String jwt;
  private Section sectionTest;
  private Enrollment enrollmentTest;
  private Assignment assignmentTest;
  private Grade gradeTest;
  private Term term;
  private Course course;
  private Assignment assignmentTest2;

  @BeforeEach
  public void setUp () {

    // login as instructor and get the security token
    String instructorEmail = "ted@csumb.edu";
    String password = "ted2025";
    String studentEmail = "sam@csumb.edu";

    // Use a student from data.sql
    User user = userRepository.findByEmail(studentEmail);
    assertNotNull(user);

    jwt = login(instructorEmail, password);

    // create temporary term, section, enrollment, assignment, grade
    term = new Term();
    int termId = nextAvailableId(20000, termRepository::existsById);
    term.setTermId(termId);
    term.setYear(2025);
    term.setSemester("Fall");
    term.setAddDate(Date.valueOf(LocalDate.of(2025, 11, 1)));
    term.setAddDeadline(Date.valueOf(LocalDate.of(2026, 4, 30)));
    term.setDropDeadline(Date.valueOf(LocalDate.of(2026, 4, 30)));
    term.setStartDate(Date.valueOf(LocalDate.of(2026, 1, 15)));
    term.setEndDate(Date.valueOf(LocalDate.of(2026, 5, 17)));
    term = termRepository.save(term);

    course = new Course();
    course.setCourseId("cstTester");
    course.setTitle("Test Course");
    course.setCredits(3);
    course = courseRepository.save(course);

    sectionTest = new Section();
    int sectionNo = nextAvailableId(3, sectionRepository::existsById);
    sectionTest.setSectionNo(sectionNo);
    sectionTest.setSectionId(sectionNo);
    sectionTest.setCourse(course);
    sectionTest.setTerm(term);
    sectionTest.setInstructorEmail(instructorEmail);
    sectionTest = sectionRepository.save(sectionTest);

    enrollmentTest = new Enrollment();
    int enrollmentId = nextAvailableId(3, enrollmentRepository::existsById);
    enrollmentTest.setEnrollmentId(enrollmentId);
    enrollmentTest.setGrade(null);
    enrollmentTest.setSection(sectionTest);
    enrollmentTest.setStudent(user);
    enrollmentTest = enrollmentRepository.save(enrollmentTest);

    assignmentTest = new Assignment();
    assignmentTest.setSection(sectionTest);
    assignmentTest.setTitle("Test Assignment Raw");
    assignmentTest.setDueDate(Date.valueOf(LocalDate.of(2025, 11, 15)));
    assignmentTest = assignmentRepository.save(assignmentTest);

    assignmentTest2 = new Assignment();
    assignmentTest2.setSection(sectionTest);
    assignmentTest2.setTitle("Test Assignment Raw 2");
    assignmentTest2.setDueDate(Date.valueOf(LocalDate.of(2025, 12, 15)));
    assignmentTest2 = assignmentRepository.save(assignmentTest2);

    gradeTest = new Grade();
    gradeTest.setEnrollment(enrollmentTest);
    gradeTest.setAssignment(assignmentTest);
    gradeTest.setScore(85);
    gradeTest = gradeRepository.save(gradeTest);
  }

  @AfterEach
  public void cleanUp () {
    gradeRepository.delete(gradeTest);
    assignmentRepository.delete(assignmentTest2);
    assignmentRepository.delete(assignmentTest);
    enrollmentRepository.delete(enrollmentTest);
    sectionRepository.delete(sectionTest);
    courseRepository.delete(course);
    termRepository.delete(term);
  }

  @Test
  public void getAssignmentGradesTest() {

    // Get grades for the assignment
    List<GradeDTO> grades = getGradesForAssignment(assignmentTest.getAssignmentId(), jwt);

    // Verify that the grade is returned correctly
    assertNotNull(grades);
    assertEquals(1, grades.size());
    GradeDTO gradeDTO = grades.get(0);
    assertEquals(gradeTest.getGradeId(), gradeDTO.gradeId());
    assertEquals(enrollmentTest.getStudent().getName(), gradeDTO.studentName());
    assertEquals(enrollmentTest.getStudent().getEmail(), gradeDTO.studentEmail());
    assertEquals(assignmentTest.getTitle(), gradeDTO.assignmentTitle());
    assertEquals(sectionTest.getCourse().getCourseId(), gradeDTO.courseId());
    assertEquals(sectionTest.getSectionId(), gradeDTO.sectionId());
    assertEquals(gradeTest.getScore(), gradeDTO.score());

    // Second Test Verify a new grade has been created since old one did not exist
    List<GradeDTO> grades2 = getGradesForAssignment(assignmentTest2.getAssignmentId(), jwt);
    GradeDTO gradeDTO2 = grades2.get(0);
    assertNull(gradeDTO2.score(), "Score should be null for assignment with no grades");

  }

  @Test
  public void updateGradesTest() {
    // For each Grade from the returned query, transform to a GradeDTO
    List<GradeDTO> dtoList = gradeRepository.findByStudentEmail(enrollmentTest.getStudent().getEmail())
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

  private int nextAvailableId(int start, java.util.function.IntPredicate exists) {
    int candidate = start;
    while (exists.test(candidate)) {
      candidate++;
    }
    return candidate;
  }
}
