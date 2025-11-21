package pe.edu.pucp.inf.pddsbackend.dto.otros;

public record RevisionResponse<T>(
        Number revisionNumber,
        java.time.Instant revisionInstant,
        T entitySnapshot,
        Object revisionMetadata // puedes mapear metadata
) {
}
