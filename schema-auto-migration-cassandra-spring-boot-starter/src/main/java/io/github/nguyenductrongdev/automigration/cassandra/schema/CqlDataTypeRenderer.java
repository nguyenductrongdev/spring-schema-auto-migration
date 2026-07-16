package io.github.nguyenductrongdev.automigration.cassandra.schema;

import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.ListType;
import com.datastax.oss.driver.api.core.type.MapType;
import com.datastax.oss.driver.api.core.type.SetType;
import com.datastax.oss.driver.api.core.type.TupleType;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.VectorType;

import java.util.stream.Collectors;

/** Renders driver data types consistently for desired and existing schemas. */
public final class CqlDataTypeRenderer {

    private CqlDataTypeRenderer() {
    }

    public static String render(DataType type) {
        if (type instanceof UserDefinedType udt) {
            String name = CqlNames.quote(udt.getName().asInternal());
            return frozen(udt.isFrozen(), name);
        }
        if (type instanceof ListType listType) {
            return frozen(listType.isFrozen(), "list<" + render(listType.getElementType()) + ">");
        }
        if (type instanceof SetType setType) {
            return frozen(setType.isFrozen(), "set<" + render(setType.getElementType()) + ">");
        }
        if (type instanceof MapType mapType) {
            String map = "map<" + render(mapType.getKeyType())
                    + ", " + render(mapType.getValueType()) + ">";
            return frozen(mapType.isFrozen(), map);
        }
        if (type instanceof TupleType tupleType) {
            return "tuple<" + tupleType.getComponentTypes().stream()
                    .map(CqlDataTypeRenderer::render)
                    .collect(Collectors.joining(", ")) + ">";
        }
        if (type instanceof VectorType vectorType) {
            return "vector<" + render(vectorType.getElementType())
                    + ", " + vectorType.getDimensions() + ">";
        }
        return type.asCql(true, false);
    }

    private static String frozen(boolean frozen, String type) {
        return frozen ? "frozen<" + type + ">" : type;
    }
}
