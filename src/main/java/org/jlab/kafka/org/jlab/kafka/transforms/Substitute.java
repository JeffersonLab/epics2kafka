package org.jlab.kafka.org.jlab.kafka.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Substitute <R extends ConnectRecord<R>> implements Transformation<R> {
    public static final String OVERVIEW_DOC =
            "Substitute string in field value using regex";

    private interface ConfigName {
        String SUBSTITUTE_FIELD_NAME = "substitute.field.name";
        String SUBSTITUTE_REGEX = "substitute.regex";
        String SUBSTITUTE_REPLACEMENT = "substitute.replacement";
    }

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(ConfigName.SUBSTITUTE_FIELD_NAME, ConfigDef.Type.STRING, null, ConfigDef.Importance.HIGH,
                    "Field name to perform search and replace")
            .define(ConfigName.SUBSTITUTE_REGEX, ConfigDef.Type.STRING, null, ConfigDef.Importance.HIGH,
                    "Regex to perform search and replace")
            .define(ConfigName.SUBSTITUTE_REPLACEMENT, ConfigDef.Type.STRING, null, ConfigDef.Importance.HIGH,
                    "Value to replace");

    private static final String PURPOSE = "substitute field value";

    private Pattern pattern;
    private String fieldName;
    private String regex;
    private String replacement;

    /**
     * Apply transformation to the {@code record} and return another record object (which may be {@code record} itself) or {@code null},
     * corresponding to a map or filter operation respectively.
     * <p>
     * The implementation must be thread-safe.
     *
     * @param record
     */
    @Override
    public R apply(R record) {
        if (operatingSchema(record) == null) {
            return applySchemaless(record);
        } else {
            return applyWithSchema(record);
        }
    }

    private R applySchemaless(R record) {
        final Map<String, Object> value = requireMap(operatingValue(record), PURPOSE);

        final Map<String, Object> updatedValue = new HashMap<>(value);

        doUpdate(updatedValue);

        return newRecord(record, null, updatedValue);
    }

    private R applyWithSchema(R record) {
        final Struct value = requireStruct(operatingValue(record), PURPOSE);

        Schema updatedSchema = value.schema(); // The schema is never updated, just reuse existing!

        final Struct updatedValue = new Struct(updatedSchema);

        for (Field field : value.schema().fields()) {
            updatedValue.put(field.name(), value.get(field));
        }

        // TODO: the below code is a duplicate of doUpdate() method, but with Struct instead of Map.  Duplicate code...
        Object originalValue = updatedValue.get(fieldName);

        if(originalValue != null && originalValue instanceof String) {
            String originalString = (String)originalValue;

            Matcher matcher = pattern.matcher(originalString);

            if(matcher.find()) { // Look if first match exists
                String newValue = matcher.replaceFirst(replacement);

                updatedValue.put(fieldName, newValue);
            }
        }

        return newRecord(record, updatedSchema, updatedValue);
    }

    private void doUpdate(Map updatedValue) {
        Object originalValue = updatedValue.get(fieldName);

        if(originalValue != null && originalValue instanceof String) {
            String originalString = (String)originalValue;

            Matcher matcher = pattern.matcher(originalString);

            if(matcher.find()) { // Look if first match exists
                String newValue = matcher.replaceFirst(replacement);

                updatedValue.put(fieldName, newValue);
            }
        }
    }

    /**
     * Configuration specification for this transformation.
     **/
    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    /**
     * Signal that this transformation instance will no longer will be used.
     **/
    @Override
    public void close() {
    }

    /**
     * Configure this class with the given key-value pairs
     *
     * @param configs
     */
    @Override
    public void configure(Map<String, ?> configs) {
        final SimpleConfig config = new SimpleConfig(CONFIG_DEF, configs);
        fieldName = config.getString(ConfigName.SUBSTITUTE_FIELD_NAME);
        regex = config.getString(ConfigName.SUBSTITUTE_REGEX);
        replacement = config.getString(ConfigName.SUBSTITUTE_REPLACEMENT);

        pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    protected abstract Schema operatingSchema(R record);

    protected abstract Object operatingValue(R record);

    protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);

    public static class Key<R extends ConnectRecord<R>> extends Substitute<R> {

        @Override
        protected Schema operatingSchema(R record) {
            return record.keySchema();
        }

        @Override
        protected Object operatingValue(R record) {
            return record.key();
        }

        @Override
        protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
            return record.newRecord(record.topic(), record.kafkaPartition(), updatedSchema, updatedValue, record.valueSchema(), record.value(), record.timestamp());
        }

    }

    public static class Value<R extends ConnectRecord<R>> extends Substitute<R> {

        @Override
        protected Schema operatingSchema(R record) {
            return record.valueSchema();
        }

        @Override
        protected Object operatingValue(R record) {
            return record.value();
        }

        @Override
        protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
            return record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(), record.key(), updatedSchema, updatedValue, record.timestamp());
        }
    }
}
