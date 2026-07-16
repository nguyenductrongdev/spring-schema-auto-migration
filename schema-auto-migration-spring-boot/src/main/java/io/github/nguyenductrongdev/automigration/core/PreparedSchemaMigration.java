package io.github.nguyenductrongdev.automigration.core;

/** A provider plan that has already completed remote inspection and schema comparison. */
public interface PreparedSchemaMigration {

    /** Reports and rejects unsupported differences without changing the database. */
    void validate();

    /** Applies the validated plan. Implementations keep dependent operations sequential. */
    void execute();

    /** Returns a migration that performs no validation or execution work. */
    static PreparedSchemaMigration noOp() {
        return new PreparedSchemaMigration() {
            @Override
            public void validate() {
            }

            @Override
            public void execute() {
            }
        };
    }
}
