package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.*;
import com.cst438.service.RegistrarServiceProxy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EnrollmentControllerUnitTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    RegistrarServiceProxy registrarServiceMock;

    // instructor logs in to view enrollment for their section
    @Test
    public void instructorGetsEnrollments() throws Exception {

        Section section = sectionRepository.findById(1).orElse(null);
        assertNotNull(section, "test depends on section_no=1 from data.sql");

        User sam = userRepository.findByEmail("sam@csumb.edu");
        assertNotNull(sam, "test depends on sam@csumb.edu from data.sql");

        // create enrollment test data
        Enrollment enrollment = new Enrollment();
        enrollment.setEnrollmentId(9001);
        enrollment.setSection(section);
        enrollment.setStudent(sam);
        enrollment.setGrade(null); // grade left null for now
        enrollmentRepository.save(enrollment);

        // login as the instructor with permission
        EntityExchangeResult<LoginDTO> login = client.get().uri("/login")
                .headers(h -> h.setBasicAuth("ted@csumb.edu", "ted2025"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginDTO.class).returnResult();
        String jwt = login.getResponseBody().jwt();
        assertNotNull(jwt);

        // call the endpoint and check student sam's enrollment shows in the result
        EntityExchangeResult<EnrollmentDTO[]> result = client.get().uri("/sections/1/enrollments")
                .headers(h -> h.setBearerAuth(jwt))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(EnrollmentDTO[].class).returnResult();

        EnrollmentDTO[] enrollments = result.getResponseBody();
        assertNotNull(enrollments);

        boolean found = false;
        for (EnrollmentDTO dto : enrollments) {
            if (dto.enrollmentId() == 9001) {
                found = true;
                assertEquals("sam@csumb.edu", dto.email());
                assertEquals("cst489", dto.courseId());
                assertEquals(1, dto.sectionNo());
            }
        }
        assertTrue(found, "expected enrollment for sam not found in response");
    }

    // instructor updates a final grade
    @Test
    public void instructorUpdatesEnrollmentGrade() throws Exception {

        Section section = sectionRepository.findById(1).orElse(null);
        User sam = userRepository.findByEmail("sam@csumb.edu");

        Enrollment enrollment = new Enrollment();
        enrollment.setEnrollmentId(9002);
        enrollment.setSection(section);
        enrollment.setStudent(sam);
        enrollment.setGrade(null);
        enrollmentRepository.save(enrollment);

        EntityExchangeResult<LoginDTO> login = client.get().uri("/login")
                .headers(h -> h.setBasicAuth("ted@csumb.edu", "ted2025"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginDTO.class).returnResult();
        String jwt = login.getResponseBody().jwt();

        // everything other than enrollmentId and grade is a placeholder
        EnrollmentDTO updateDto = new EnrollmentDTO(
                9002, "A", 2, "sam", "sam@csumb.edu",
                "cst489", "Software Engineering", 1, 1,
                "90", "B104", "W F 10-11", 4, 2026, "Fall"
        );

        client.put().uri("/enrollments")
                .headers(h -> h.setBearerAuth(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(updateDto))
                .exchange()
                .expectStatus().isOk();

        // confirm the grade was actually saved
        Enrollment updated = enrollmentRepository.findById(9002).orElse(null);
        assertNotNull(updated);
        assertEquals("A", updated.getGrade());

        // check if registrar was notified
        verify(registrarServiceMock, times(1)).sendMessage(eq("updateEnrollment"), any());
    }

    // an instructor who doesn't own the section is denied access
    @Test
    public void incorrectInstructorDeny() throws Exception {

        // create instructor not tied to the tested section 1
        User otherInstructor = new User();
        otherInstructor.setName("sally");
        otherInstructor.setEmail("sally@csumb.edu");
        otherInstructor.setPassword(passwordEncoder.encode("sally2025"));
        otherInstructor.setType("INSTRUCTOR");
        userRepository.save(otherInstructor);

        EntityExchangeResult<LoginDTO> login = client.get().uri("/login")
                .headers(h -> h.setBasicAuth("sally@csumb.edu", "sally2025"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginDTO.class).returnResult();
        String jwt = login.getResponseBody().jwt();
        assertNotNull(jwt);

        // sally tries to view ted's section roster and is denied
        client.get().uri("/sections/1/enrollments")
                .headers(h -> h.setBearerAuth(jwt))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();

        // registrar should not be notified
        verify(registrarServiceMock, times(0)).sendMessage(eq("updateEnrollment"), any());
    }
}