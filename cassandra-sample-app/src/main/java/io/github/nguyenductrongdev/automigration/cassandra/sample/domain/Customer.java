package io.github.nguyenductrongdev.automigration.cassandra.sample.domain;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("customers")
public class Customer {

    @PrimaryKey
    private UUID id;

    @Column("email_address")
    private String emailAddress;

    private Address address;

    @Column("created_at")
    private Instant createdAt;

    public Customer() {
    }

    public Customer(UUID id, String emailAddress, Address address, Instant createdAt) {
        this.id = id;
        this.emailAddress = emailAddress;
        this.address = address;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
