package io.github.nguyenductrongdev.automigration.cassandra.execute;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;

import java.util.List;

/** Executes an already validated list of additive CQL statements in order. */
public class CassandraMigrationExecutor {

    public void execute(CqlSession session, List<String> statements) {
        for (int index = 0; index < statements.size(); index++) {
            ResultSet result = session.execute(statements.get(index));
            if (!result.getExecutionInfo().isSchemaInAgreement()) {
                throw new IllegalStateException(
                        "Cassandra schema agreement was not reached after migration statement "
                                + (index + 1) + " of " + statements.size());
            }
        }
    }
}