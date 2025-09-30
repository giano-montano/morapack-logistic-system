package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.ProcessResult;

import java.io.InputStream;
import java.time.LocalDate;

public interface VueloService {

    ProcessResult procesarArchivoPlanesVueloDelProfe(InputStream inputStream);

    @Transactional
    ProcessResult createConcreteFlights(LocalDate startDate, int days, boolean skipIfExists);
}
