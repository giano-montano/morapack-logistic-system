package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Setter
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Audited
public abstract class BaseAuditable {

    @CreatedDate
    @Column(columnDefinition = "datetime(6) DEFAULT  CURRENT_TIMESTAMP(6)", insertable = false, updatable = false)
    private Instant fechaInsercion;

    @LastModifiedDate
    private Instant fechaUltimaModificacion;

    @CreatedBy
    @Column(/*name = "created_by",*/ updatable = false, length = 100)
    private String insertadoPor;

    @LastModifiedBy
    @Column(/*name = "modified_by"*/, length = 100)
    private String modificadoPor;

    // getters and setters

}