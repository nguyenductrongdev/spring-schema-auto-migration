package io.github.nguyenductrongdev.automigration.cassandra.scan;

import io.github.nguyenductrongdev.automigration.cassandra.schema.CqlNames;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Converts common Java and mapped UDT types to Cassandra CQL types. */
public class JavaTypeResolver {

    public String resolve(Type type) {
        return resolve(type, this::annotationUdtName);
    }

    public String resolve(Type type, Function<Class<?>, String> udtNameResolver) {
        if (type instanceof ParameterizedType parameterizedType) {
            return resolveParameterized(parameterizedType, udtNameResolver);
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return "list<" + resolve(genericArrayType.getGenericComponentType(), udtNameResolver) + ">";
        }
        if (!(type instanceof Class<?> javaType)) {
            throw unsupported(type);
        }
        return resolveClass(javaType, udtNameResolver);
    }

    private String resolveParameterized(
            ParameterizedType type,
            Function<Class<?>, String> udtNameResolver) {
        if (!(type.getRawType() instanceof Class<?> rawType)) {
            throw unsupported(type);
        }
        Type[] arguments = type.getActualTypeArguments();
        if (List.class.isAssignableFrom(rawType)) {
            return collectionType("list", arguments, 1, type, udtNameResolver);
        }
        if (Set.class.isAssignableFrom(rawType)) {
            return collectionType("set", arguments, 1, type, udtNameResolver);
        }
        if (Map.class.isAssignableFrom(rawType)) {
            return collectionType("map", arguments, 2, type, udtNameResolver);
        }
        if (Optional.class.isAssignableFrom(rawType) && arguments.length == 1) {
            return resolve(arguments[0], udtNameResolver);
        }
        return resolveClass(rawType, udtNameResolver);
    }

    private String collectionType(
            String cqlName,
            Type[] arguments,
            int count,
            Type source,
            Function<Class<?>, String> udtNameResolver) {
        if (arguments.length != count) {
            throw unsupported(source);
        }
        if (count == 1) {
            return cqlName + "<" + resolve(arguments[0], udtNameResolver) + ">";
        }
        return cqlName + "<" + resolve(arguments[0], udtNameResolver)
                + ", " + resolve(arguments[1], udtNameResolver) + ">";
    }

    private String resolveClass(Class<?> type, Function<Class<?>, String> udtNameResolver) {
        if (type == String.class || type == Character.class || type == char.class || type.isEnum()) {
            return "text";
        }
        if (type == UUID.class) {
            return "uuid";
        }
        if (type == Long.class || type == long.class) {
            return "bigint";
        }
        if (type == Integer.class || type == int.class) {
            return "int";
        }
        if (type == Short.class || type == short.class) {
            return "smallint";
        }
        if (type == Byte.class || type == byte.class) {
            return "tinyint";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        }
        if (type == Double.class || type == double.class) {
            return "double";
        }
        if (type == Float.class || type == float.class) {
            return "float";
        }
        if (type == BigDecimal.class) {
            return "decimal";
        }
        if (type == BigInteger.class) {
            return "varint";
        }
        if (type == Instant.class || type == Date.class) {
            return "timestamp";
        }
        if (type == LocalDate.class) {
            return "date";
        }
        if (type == LocalTime.class) {
            return "time";
        }
        if (type == Duration.class) {
            return "duration";
        }
        if (type == InetAddress.class) {
            return "inet";
        }
        if (type == ByteBuffer.class || type == byte[].class || type == Byte[].class) {
            return "blob";
        }
        if (type.isArray()) {
            return "list<" + resolveClass(type.componentType(), udtNameResolver) + ">";
        }

        UserDefinedType annotation = type.getAnnotation(UserDefinedType.class);
        if (annotation != null) {
            String name = udtNameResolver.apply(type);

            if (name == null || name.isBlank()) {
                name = annotationUdtName(type);
            }
            return "frozen<" + CqlNames.quote(name) + ">";
        }
        throw unsupported(type);
    }

    private String annotationUdtName(Class<?> type) {
        UserDefinedType annotation = type.getAnnotation(UserDefinedType.class);
        if (annotation == null) {
            return null;
        }
        String name = annotation.value().isBlank()
                ? CqlNames.snakeCase(type.getSimpleName())
                : annotation.value();
        return CqlNames.internal(name);
    }

    private IllegalArgumentException unsupported(Type type) {
        return new IllegalArgumentException("No Cassandra CQL mapping is known for Java type " + type.getTypeName());
    }
}

