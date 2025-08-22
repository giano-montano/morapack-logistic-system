package pe.edu.pucp.inf.pddsbackend;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestService { // Debería ser un Impl con su interfaz aparte
    TestRepository testRepository;
    public TestService(TestRepository testRepository) {
        this.testRepository = testRepository;
    }

    @Transactional
    public String test() {
        Test testSaved = testRepository.save(Test.builder().testText("Invadan Polonia").build());
        Test testRetrieved = testRepository.getReferenceById(testSaved.getId());
        return "¡Hola mundo! Mi mensaje es: " +testRetrieved.getTestText()  ;
    }
}
