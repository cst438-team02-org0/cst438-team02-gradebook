package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.AssignmentDTO;
import com.cst438.dto.AssignmentStudentDTO;
import com.cst438.dto.LoginDTO;
import com.cst438.dto.SectionDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AssignmentControllerUnitTest {

  @Autowired
  private WebTestClient client;

  @Autowired
  private AssignmentRepository assignmentRepository;

  @Autowired
  private SectionRepository sectionRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @Autowired
  private GradeRepository gradeRepository;

  @Autowired
  private UserRepository userRepository;

  private String instructorJwt;
  private String studentJwt;
  private Section section;
  private User student;

  // keep track of test records -> removed after each test
  private final List<Integer> assignmentIds = new ArrayList<>();
  private final List<Integer> gradeIds = new ArrayList<>();
  private Integer enrollmentId;

  @BeforeEach
  public void setUp() {
    // use instructor and student accounts in by data.sql.
    instructorJwt = login("ted@csumb.edu", "ted2025");
    studentJwt = login("sam@csumb.edu", "sam2025");
    // section 1 from data.sql.
    section = sectionRepository.findById(1).orElse(null);
    assertNotNull(section, "test depends on section 1 from data.sql");
    student = userRepository.findByEmail("sam@csumb.edu");
    assertNotNull(student, "test depends on sam@csumb.edu from data.sql");
  }

  @AfterEach
  public void cleanUp() {
    // grades must be deleted before their assignments
    for (Integer id : gradeIds) {
      if (gradeRepository.existsById(id)) {
        gradeRepository.deleteById(id);
      }
    }
    for (Integer id : assignmentIds) {
      if (assignmentRepository.existsById(id)) {
        assignmentRepository.deleteById(id);
      }
    }
    // delete only an enrollment created by test class
    if (enrollmentId != null &&
        enrollmentRepository.existsById(enrollmentId)) {
      enrollmentRepository.deleteById(enrollmentId);
    }
    gradeIds.clear();
    assignmentIds.clear();
    enrollmentId = null;
  }

  // Test getSectionsForInstructor and getAssignments.
  // Instructor should receive sections for the requested term and
  // should be able to view assignments belonging to their own section.
  @Test
  public void instructorGetsSectionsAndAssignments() {
    int year = section.getTerm().getYear();
    String semester = section.getTerm().getSemester();

    EntityExchangeResult<List<SectionDTO>> sectionResult =
        client.get()
            .uri(uriBuilder -> uriBuilder
                .path("/sections")
                .queryParam("year", year)
                .queryParam("semester", semester)
                .build())
            .headers(headers ->
                headers.setBearerAuth(instructorJwt))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(SectionDTO.class)
            .returnResult();

    List<SectionDTO> sections = sectionResult.getResponseBody();
    assertNotNull(sections);
    // check instructor's section appears in response
    boolean sectionFound = false;
    for (SectionDTO dto : sections) {
      if (dto.secNo() == section.getSectionNo()) {
        sectionFound = true;
        assertEquals(year, dto.year());
        assertEquals(semester, dto.semester());
        assertEquals(section.getCourse().getCourseId(),
            dto.courseId());
        assertEquals(section.getInstructorEmail(),
            dto.instructorEmail());
      }
    }
    assertTrue(sectionFound,
        "expected instructor section was not returned");
    // create assignment for assignment-list test
    Assignment assignment = new Assignment();
    assignment.setTitle("Assignment List Test");
    assignment.setDueDate(validDueDate(1));
    assignment.setSection(section);
    assignment = assignmentRepository.save(assignment);
    int assignmentId = assignment.getAssignmentId();
    assignmentIds.add(assignmentId);

    EntityExchangeResult<List<AssignmentDTO>> assignmentResult =
        client.get()
            .uri("/sections/" + section.getSectionNo()
                + "/assignments")
            .headers(headers ->
                headers.setBearerAuth(instructorJwt))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(AssignmentDTO.class)
            .returnResult();

    List<AssignmentDTO> assignments =
        assignmentResult.getResponseBody();
    assertNotNull(assignments);
    // check assignment appears in response
    boolean assignmentFound = false;

    for (AssignmentDTO dto : assignments) {
      if (dto.id() == assignmentId) {
        assignmentFound = true;
        assertEquals("Assignment List Test", dto.title());
        assertEquals(assignment.getDueDate().toString(),
            dto.dueDate());
        assertEquals(section.getSectionNo(), dto.secNo());
      }
    }
    assertTrue(assignmentFound,
        "expected assignment was not returned");
  }

  // Test createAssignment, updateAssignment, and deleteAssignment.
  // response and database are checked after each
  @Test
  public void instructorCreatesUpdatesDeletesAssignment() {
    Date createDate = validDueDate(2);
    AssignmentDTO createDTO = new AssignmentDTO(
        0,
        "Unit Test Assignment",
        createDate.toString(),
        section.getCourse().getCourseId(),
        section.getSectionId(),
        section.getSectionNo()
    );
    // create assignment
    EntityExchangeResult<AssignmentDTO> createResult =
        client.post()
            .uri("/assignments")
            .headers(headers ->
                headers.setBearerAuth(instructorJwt))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createDTO)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AssignmentDTO.class)
            .returnResult();
    AssignmentDTO created = createResult.getResponseBody();
    assertNotNull(created);
    assertTrue(created.id() > 0);
    assertEquals("Unit Test Assignment", created.title());
    assertEquals(createDate.toString(), created.dueDate());
    int assignmentId = created.id();
    assignmentIds.add(assignmentId);
    // verify assignment was saved
    Assignment saved =
        assignmentRepository.findById(assignmentId).orElse(null);
    assertNotNull(saved);
    assertEquals("Unit Test Assignment", saved.getTitle());
    assertEquals(createDate, saved.getDueDate());
    Date updateDate = validDueDate(3);
    AssignmentDTO updateDTO = new AssignmentDTO(
        assignmentId,
        "Updated Unit Test Assignment",
        updateDate.toString(),
        section.getCourse().getCourseId(),
        section.getSectionId(),
        section.getSectionNo()
    );

    // update assignment title and due date
    EntityExchangeResult<AssignmentDTO> updateResult =
        client.put()
            .uri("/assignments")
            .headers(headers ->
                headers.setBearerAuth(instructorJwt))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateDTO)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AssignmentDTO.class)
            .returnResult();
    AssignmentDTO updated = updateResult.getResponseBody();
    assertNotNull(updated);
    assertEquals(assignmentId, updated.id());
    assertEquals("Updated Unit Test Assignment",
        updated.title());
    assertEquals(updateDate.toString(), updated.dueDate());
    // verify update was saved
    saved = assignmentRepository.findById(assignmentId)
        .orElse(null);
    assertNotNull(saved);
    assertEquals("Updated Unit Test Assignment",
        saved.getTitle());
    assertEquals(updateDate, saved.getDueDate());
    // delete assignment
    client.delete()
        .uri("/assignments/" + assignmentId)
        .headers(headers ->
            headers.setBearerAuth(instructorJwt))
        .exchange()
        .expectStatus().isOk();
    assertFalse(assignmentRepository.existsById(assignmentId),
        "assignment was not deleted");
    // controller deleted it, so cleanup should not
    assignmentIds.remove(Integer.valueOf(assignmentId));
  }

  // Test getStudentAssignments.
  // graded assignment should return its score
  // assignment without a Grade entity should return a null score.
  @Test
  public void studentGetsAssignmentsAndGrades() {
    Enrollment enrollment = findEnrollment();
    // create enrollment only if data.sql does not already have one
    if (enrollment == null) {
      enrollment = new Enrollment();
      enrollment.setEnrollmentId(nextEnrollmentId());
      enrollment.setGrade(null);
      enrollment.setSection(section);
      enrollment.setStudent(student);
      enrollment = enrollmentRepository.save(enrollment);
      enrollmentId = enrollment.getEnrollmentId();
    }
    // create one graded assignment
    Assignment gradedAssignment = new Assignment();
    gradedAssignment.setTitle("Graded Assignment Test");
    gradedAssignment.setDueDate(validDueDate(1));
    gradedAssignment.setSection(section);
    gradedAssignment =
        assignmentRepository.save(gradedAssignment);
    int gradedAssignmentId =
        gradedAssignment.getAssignmentId();
    assignmentIds.add(gradedAssignmentId);
    // create one ungraded assignment w/ later due date
    Assignment ungradedAssignment = new Assignment();
    ungradedAssignment.setTitle("Ungraded Assignment Test");
    ungradedAssignment.setDueDate(validDueDate(2));
    ungradedAssignment.setSection(section);
    ungradedAssignment =
        assignmentRepository.save(ungradedAssignment);
    int ungradedAssignmentId =
        ungradedAssignment.getAssignmentId();
    assignmentIds.add(ungradedAssignmentId);
    // add a score for only first assignment
    Grade grade = new Grade();
    grade.setAssignment(gradedAssignment);
    grade.setEnrollment(enrollment);
    grade.setScore(92);
    grade = gradeRepository.save(grade);
    gradeIds.add(grade.getGradeId());
    int year = section.getTerm().getYear();
    String semester = section.getTerm().getSemester();
    EntityExchangeResult<List<AssignmentStudentDTO>> result =
        client.get()
            .uri(uriBuilder -> uriBuilder
                .path("/assignments")
                .queryParam("year", year)
                .queryParam("semester", semester)
                .build())
            .headers(headers ->
                headers.setBearerAuth(studentJwt))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(AssignmentStudentDTO.class)
            .returnResult();
    List<AssignmentStudentDTO> assignments =
        result.getResponseBody();
    assertNotNull(assignments);
    AssignmentStudentDTO gradedDTO = null;
    AssignmentStudentDTO ungradedDTO = null;
    int gradedIndex = -1;
    int ungradedIndex = -1;

    for (int i = 0; i < assignments.size(); i++) {
      AssignmentStudentDTO dto = assignments.get(i);
      if (dto.assignmentId() == gradedAssignmentId) {
        gradedDTO = dto;
        gradedIndex = i;
      }
      if (dto.assignmentId() == ungradedAssignmentId) {
        ungradedDTO = dto;
        ungradedIndex = i;
      }
    }

    assertNotNull(gradedDTO,
        "graded assignment was not returned");
    assertEquals("Graded Assignment Test",
        gradedDTO.title());
    assertEquals(Integer.valueOf(92),
        gradedDTO.score());
    assertNotNull(ungradedDTO,
        "ungraded assignment was not returned");
    assertEquals("Ungraded Assignment Test",
        ungradedDTO.title());
    assertNull(ungradedDTO.score(),
        "ungraded assignment should have a null score");
    // earlier due date should appear first
    assertTrue(gradedIndex < ungradedIndex,
        "assignments were not ordered by due date");
  }

  // Login and return JWT
  private String login(String email, String password) {
    EntityExchangeResult<LoginDTO> result =
        client.get()
            .uri("/login")
            .headers(headers ->
                headers.setBasicAuth(email, password))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(LoginDTO.class)
            .returnResult();
    LoginDTO loginDTO = result.getResponseBody();
    assertNotNull(loginDTO);
    assertNotNull(loginDTO.jwt());
    return loginDTO.jwt();
  }

  // Return a date inside section's term
  private Date validDueDate(int daysAfterStart) {
    LocalDate start =
        section.getTerm().getStartDate().toLocalDate();
    LocalDate end =
        section.getTerm().getEndDate().toLocalDate();
    LocalDate dueDate = start.plusDays(daysAfterStart);
    // use end date if calculated date would be outside the term
    if (dueDate.isAfter(end)) {
      dueDate = end;
    }
    return Date.valueOf(dueDate);
  }

  // Find existing enrollment for Sam in test section
  private Enrollment findEnrollment() {
    for (Enrollment enrollment :
        enrollmentRepository.findAll()) {
      boolean sameStudent =
          enrollment.getStudent().getEmail()
              .equals(student.getEmail());
      boolean sameSection =
          enrollment.getSection().getSectionNo()
              == section.getSectionNo();
      if (sameStudent && sameSection) {
        return enrollment;
      }
    }
    return null;
  }

  // Find an unused ID
  private int nextEnrollmentId() {
    int id = 9000;
    while (enrollmentRepository.existsById(id)) {
      id++;
    }
    return id;
  }
}