package org.batfish.datamodel.table;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.common.BatfishException;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.answers.SchemaUtils;
import org.batfish.datamodel.questions.Exclusion;

/**
 * Represents one row of the table answer. Each row is basically a map of key value pairs, where the
 * key is the column name and the value (currently) is JsonNode.
 */
@ParametersAreNonnullByDefault
public class Row implements Comparable<Row> {

  @ParametersAreNonnullByDefault
  public static class RowBuilder {

    /** @Nullable for backwards compatiblity -- will be enforced later */
    @Nullable ImmutableMap<String, ColumnMetadata> _columns;

    private final ObjectNode _data;

    private RowBuilder(@Nullable ImmutableMap<String, ColumnMetadata> columns) {
      _columns = columns;
      _data = BatfishObjectMapper.mapper().createObjectNode();
    }

    private RowBuilder(
        @Nullable ImmutableMap<String, ColumnMetadata> columns, // temporarily null
        Row row,
        Collection<String> selectColumns) {
      this(columns);
      selectColumns.forEach(col -> put(col, row.get(col)));
    }

    public Row build() {
      return new Row(_columns, _data);
    }

    /**
     * Sets the value for the specified column to the specified value. Any existing values for the
     * column are overwritten
     *
     * @param columnName The column to set
     * @param value The value to set
     * @return The RowBuilder object itself (to aid chaining)
     */
    public RowBuilder put(String columnName, @Nullable Object value) {
      JsonNode jsonNode = BatfishObjectMapper.mapper().valueToTree(value);
      if (_columns != null) {
        Row.checkColumn(columnName, _columns.keySet());
        SchemaUtils.convertType(jsonNode, _columns.get(columnName).getSchema());
      }
      _data.set(columnName, jsonNode);
      return this;
    }
  }

  /** @Nullable for backwards compatiblity -- will be enforced later */
  @Nullable ImmutableMap<String, ColumnMetadata> _columns;

  private final ObjectNode _data;

  private static void checkColumn(String columnName, Set<String> columns) {
    if (!columns.contains(columnName)) {
      throw new NoSuchElementException(Row.getMissingColumnErrorMessage(columnName, columns));
    }
  }

  /**
   * Returns a new {@link Row} with the given entries.
   *
   * <p>This function requires an even number of parameters, where the 0th and every even parameter
   * is a {@link String} representing the name of a column.
   */
  @Deprecated
  public static Row of(Object... objects) {
    checkArgument(
        objects.length % 2 == 0, "expecting an even number of parameters, not %s", objects.length);
    Row.RowBuilder builder = Row.builder();
    for (int i = 0; i + 1 < objects.length; i += 2) {
      checkArgument(
          objects[i] instanceof String, "argument %s must be a string, but is: %s", i, objects[i]);
      builder.put((String) objects[i], objects[i + 1]);
    }
    return builder.build();
  }

  /**
   * Returns a new {@link Row} with the given entries.
   *
   * <p>This function requires an even number of parameters, where the 0th and every even parameter
   * is a {@link String} representing the name of a column.
   */
  public static Row of(ImmutableMap<String, ColumnMetadata> columns, Object... objects) {
    checkArgument(
        objects.length % 2 == 0, "expecting an even number of parameters, not %s", objects.length);
    Row.RowBuilder builder = Row.builder(columns);
    for (int i = 0; i + 1 < objects.length; i += 2) {
      checkArgument(
          objects[i] instanceof String, "argument %s must be a string, but is: %s", i, objects[i]);
      builder.put((String) objects[i], objects[i + 1]);
    }
    return builder.build();
  }

  @JsonCreator
  private Row(@Nullable ImmutableMap<String, ColumnMetadata> columns, ObjectNode data) {
    _columns = columns;
    _data = data;
  }

  /** Returns a builder object for Row */
  @Deprecated
  public static RowBuilder builder() {
    return builder((ImmutableMap<String, ColumnMetadata>) null);
  }

  /** Returns a builder object for Row */
  public static RowBuilder builder(ImmutableMap<String, ColumnMetadata> columns) {
    return new RowBuilder(columns);
  }

  /** Returns a builder object for Row seeded by the contents of {@code otheRow} */
  @Deprecated
  public static RowBuilder builder(Row otherRow) {
    return new RowBuilder(null, otherRow, otherRow.getColumnNames());
  }

  /** Returns a builder object for Row seeded by the contents of {@code otheRow} */
  public static RowBuilder builder(ImmutableMap<String, ColumnMetadata> columns, Row otherRow) {
    return new RowBuilder(columns, otherRow, otherRow.getColumnNames());
  }

  /** Returns a {@link RowBuilder} object seeded by {@code keyColumns} from {@code otherRow} */
  @Deprecated
  public static RowBuilder builder(Row otherRow, Collection<String> selectColumns) {
    return new RowBuilder(null, otherRow, selectColumns);
  }

  /** Returns a {@link RowBuilder} object seeded by {@code keyColumns} from {@code otherRow} */
  public static RowBuilder builder(
      ImmutableMap<String, ColumnMetadata> columns,
      Row otherRow,
      Collection<String> selectColumns) {
    return new RowBuilder(columns, otherRow, selectColumns);
  }

  /**
   * Compares two Rows. The current implementation ignores primary keys of the table and compares
   * everything, mainly to provide consistent ordering of answers. This will need to change when we
   * start using the primary keys for something.
   *
   * @param o The other Row to compare against.
   * @return The result of the comparison
   */
  @Override
  public int compareTo(Row o) {
    try {
      String myStr = BatfishObjectMapper.mapper().writeValueAsString(_data);
      String oStr = BatfishObjectMapper.mapper().writeValueAsString(o._data);
      return myStr.compareTo(oStr);
    } catch (JsonProcessingException e) {
      throw new BatfishException("Exception in row comparison", e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof Row)) {
      return false;
    }
    return Objects.equals(_columns, ((Row) o)._columns) && Objects.equals(_data, ((Row) o)._data);
  }

  /**
   * Gets the (raw) Json representation of the object stored in the row
   *
   * @param columnName The column to fetch
   * @return The {@link JsonNode} object that represents the stored object
   * @throws {@link NoSuchElementException} if this column does not exist
   */
  public Object get(String columnName) {
    checkState(_columns != null, "This function cannot be called when columns is null");
    checkColumn(columnName, _columns.keySet());
    if (!_data.has(columnName) || _data.get(columnName).isNull()) {
      return null;
    }
    return SchemaUtils.convertType(_data.get(columnName), _columns.get(columnName).getSchema());
  }

  /**
   * Gets the value of specified column
   *
   * @param columnName The column to fetch
   * @return The result
   * @throws NoSuchElementException if this column is not present
   * @throws ClassCastException if the recovered data cannot be cast to the expected object
   */
  @Deprecated
  public Object get(String columnName, Schema columnSchema) {
    if (!_data.has(columnName)) {
      throw new NoSuchElementException(getMissingColumnErrorMessage(columnName, getColumnNames()));
    }
    if (_data.get(columnName).isNull()) {
      return null;
    }
    return SchemaUtils.convertType(_data.get(columnName), columnSchema);
  }

  /**
   * Fetch the names of the columns in this Row
   *
   * @return The {@link Set} of names
   */
  public Set<String> getColumnNames() {
    HashSet<String> columns = new HashSet<>();
    _data.fieldNames().forEachRemaining(column -> columns.add(column));
    return columns;
  }

  @JsonValue
  private ObjectNode getData() {
    return _data;
  }

  /**
   * Returns the list of values in all columns declared as key in the metadata.
   *
   * @return The list
   */
  public List<Object> getKey() {
    checkState(_columns != null, "This function cannot be used when column metadata is null");
    List<Object> keyList = new LinkedList<>();
    for (ColumnMetadata column : _columns.values()) {
      if (column.getIsKey()) {
        keyList.add(get(column.getName()));
      }
    }
    return keyList;
  }

  /**
   * Returns the list of values in all columns declared as key in the metadata.
   *
   * @param metadata Provides information on which columns are key and their {@link Schema}
   * @return The list
   */
  @Deprecated
  public List<Object> getKey(List<ColumnMetadata> metadata) {
    List<Object> keyList = new LinkedList<>();
    for (ColumnMetadata column : metadata) {
      if (column.getIsKey()) {
        keyList.add(get(column.getName(), column.getSchema()));
      }
    }
    return keyList;
  }

  /** This used to be the old signature, changed now to {@link #getKey(List)} */
  @Deprecated
  public List<Object> getKey(TableMetadata metadata) {
    return getKey(metadata.getColumnMetadata());
  }

  private static String getMissingColumnErrorMessage(String columnName, Set<String> columns) {
    return String.format("Column '%s' is not present. Valid columns are: %s", columnName, columns);
  }

  /**
   * Returns the list of values in all columns declared as value in the metadata.
   *
   * @return The list
   */
  public List<Object> getValue() {
    checkArgument(_columns != null, "This function cannot be used when column metadata is null");
    List<Object> valueList = new LinkedList<>();
    for (ColumnMetadata column : _columns.values()) {
      if (column.getIsValue()) {
        valueList.add(get(column.getName(), column.getSchema()));
      }
    }
    return valueList;
  }

  /**
   * Returns the list of values in all columns declared as value in the metadata.
   *
   * @param metadata Provides information on which columns are key and their {@link Schema}
   * @return The list
   */
  @Deprecated
  public List<Object> getValue(List<ColumnMetadata> metadata) {
    List<Object> valueList = new LinkedList<>();
    for (ColumnMetadata column : metadata) {
      if (column.getIsValue()) {
        valueList.add(get(column.getName(), column.getSchema()));
      }
    }
    return valueList;
  }

  /** This used to be the old signature, changed now to {@link #getValue(List)} */
  @Deprecated
  public List<Object> getValue(TableMetadata metadata) {
    return getValue(metadata.getColumnMetadata());
  }

  @Override
  public int hashCode() {
    return Objects.hash(_columns, _data);
  }

  /**
   * Checks is this row is covered by the provided exclusion.
   *
   * @param exclusion The exclusion to check against.
   * @return The result of the check
   */
  public boolean isCovered(ObjectNode exclusion) {
    return Exclusion.firstCoversSecond(exclusion, _data);
  }

  /**
   * Returns a new {@link Row} that has only the specified columns from this row.
   *
   * @param columns The columns to keep.
   * @return A new {@link Row} object
   * @throws {@link NoSuchElementException} if one of the specified columns are not present
   */
  @Deprecated
  public static Row selectColumns(Row inputRow, Set<String> columns) {
    RowBuilder retRow = Row.builder();
    columns.forEach(col -> retRow.put(col, inputRow.get(col)));
    return retRow.build();
  }

  @Override
  public String toString() {
    return _data.toString();
  }
}
