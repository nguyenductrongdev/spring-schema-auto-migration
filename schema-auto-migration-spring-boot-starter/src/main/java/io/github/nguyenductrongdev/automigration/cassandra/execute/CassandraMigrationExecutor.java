package io.github.nguyenductrongdev.automigration.cassandra.execute;

import com.datastax.oss.driver.api.core.CqlSession;

import java.util.List;

/** Executes an already validated list of additive CQL statements in order. */
public class CassandraMigrationExecutor {

    public void execute(CqlSession session, List<String> statements) {
        statements.forEach(session::execute);
    }
}

