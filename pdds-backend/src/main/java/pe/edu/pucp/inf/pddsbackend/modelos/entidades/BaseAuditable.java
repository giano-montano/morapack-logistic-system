package pe.edu.pucp.inf.pddsbackend.modelos.entidades;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
@MappedSuperclass
// @EntityListeners(AuditingEntityListener.class)
// @Audited
public abstract class BaseAuditable
{

    // @CreatedDate
    @Column(columnDefinition = "datetime(6) DEFAULT  CURRENT_TIMESTAMP(6)", insertable = false, updatable = false)
    private Instant fechaInsercion;

    // @LastModifiedDate
    private Instant fechaUltimaModificacion;

    // @CreatedBy
    @Column(/* name = "created_by", */ updatable = false, length = 100)
    private String insertadoPor;

    // @LastModifiedBy
    @Column(/* name = "modified_by", */ length = 100)
    private String modificadoPor;

    // getters and setters

}
