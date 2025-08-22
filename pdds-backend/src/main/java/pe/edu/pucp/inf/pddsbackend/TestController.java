package pe.edu.pucp.inf.pddsbackend;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    private final TestService testService;

    public TestController(TestService testService) {
        this.testService = testService; // Inyección de dependencias por constructor
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        String response = testService.test();
        return ResponseEntity.ok(response);
    }
}
