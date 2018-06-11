package org.batfish.datamodel.table;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.pojo.Node;
import org.batfish.datamodel.table.Row.RowBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RowTest {

  @Rule public ExpectedException _thrown = ExpectedException.none();

  public ImmutableMap<String, ColumnMetadata> initMetadata(Schema schema, String... columnNames) {
    LinkedHashMap<String, ColumnMetadata> columns = new LinkedHashMap<>();
    for (int i = 0; i < columnNames.length; i += 1) {
      String columnName = columnNames[i];
      columns.put(columnName, new ColumnMetadata(columnName, schema, "desc"));
    }
    return ImmutableMap.copyOf(columns);
  }

  ImmutableMap<String, ColumnMetadata> initMetadataThree(
      boolean keyCol1,
      boolean keyCol2,
      boolean keyCol3,
      boolean valueCol1,
      boolean valueCol2,
      boolean valueCol3) {
    return ImmutableMap.of(
        "col1", new ColumnMetadata("col1", Schema.INTEGER, "desc", keyCol1, valueCol1),
        "col2", new ColumnMetadata("col2", Schema.INTEGER, "desc", keyCol2, valueCol2),
        "col3", new ColumnMetadata("col3", Schema.INTEGER, "desc", keyCol3, valueCol3));
  }

  Row initRowThree(ImmutableMap<String, ColumnMetadata> columns) {
    return Row.builder(columns).put("col1", 1).put("col2", 2).put("col3", 3).build();
  }

  @Test
  public void builderWithOtherRow() {
    Row row =
        Row.builder(initMetadataThree(false, false, false, false, false, false))
            .put("col1", 20)
            .put("col2", 21)
            .put("col3", 24)
            .build();

    ImmutableMap<String, ColumnMetadata> columns = initMetadata(Schema.INTEGER, "col1", "col3");
    // check expected results after selecting two columns
    Row newRow = Row.builder(columns, row, ImmutableSet.of("col1", "col3")).build();
    assertThat(newRow, equalTo(Row.builder(columns).put("col1", 20).put("col3", 24).build()));

    // selecting a non-existent column throws an exception
    _thrown.expect(NoSuchElementException.class);
    _thrown.expectMessage("is not present");
    Row.builder(columns, newRow, ImmutableSet.of("col2"));
  }

  @Test
  public void builderPutBadColumnName() {
    RowBuilder rowBuilder =
        Row.builder(initMetadataThree(false, false, false, false, false, false));

    // selecting a non-existent column throws an exception
    _thrown.expect(NoSuchElementException.class);
    _thrown.expectMessage("is not present");
    rowBuilder.put("nocol", 32);
  }

  @Test
  public void builderPutCorrect() {
    RowBuilder rowBuilder =
        Row.builder(initMetadataThree(false, false, false, false, false, false));

    // putting an integer should succeed
    assertThat(rowBuilder.put("col1", 32), equalTo(rowBuilder));

    // putting a new value for the same column should succeed
    assertThat(rowBuilder.put("col1", 42), equalTo(rowBuilder));

    // we should get back the new value
    assertThat(rowBuilder.build().get("col1"), equalTo(42));
  }

  @Test
  public void get() {
    // check that non-list values are same after put and get
    assertThat(
        Row.builder(initMetadata(Schema.INTEGER, "col")).put("col", 42).build().get("col"),
        equalTo(42));
    assertThat(
        Row.builder(initMetadata(Schema.NODE, "col"))
            .put("col", new Node("node"))
            .build()
            .get("col"),
        equalTo(new Node("node")));

    // check the same for lists
    assertThat(
        Row.builder(initMetadata(Schema.list(Schema.INTEGER), "col"))
            .put("col", ImmutableList.of(4, 2))
            .build()
            .get("col"),
        equalTo(ImmutableList.of(4, 2)));
    assertThat(
        Row.builder(initMetadata(Schema.list(Schema.NODE), "col"))
            .put("col", ImmutableList.of(new Node("n1"), new Node("n2")))
            .build()
            .get("col"),
        equalTo(ImmutableList.of(new Node("n1"), new Node("n2"))));
  }

  @Test
  public void getKey() {
    ImmutableMap<String, ColumnMetadata> metadataNoKeys =
        initMetadataThree(false, false, false, false, false, false);
    ImmutableMap<String, ColumnMetadata> metadataOneKey =
        initMetadataThree(false, true, false, false, false, false);
    ImmutableMap<String, ColumnMetadata> metadataTwoKeys =
        initMetadataThree(true, false, true, false, false, false);

    assertThat(initRowThree(metadataNoKeys).getKey(), equalTo(ImmutableList.of()));
    assertThat(initRowThree(metadataOneKey).getKey(), equalTo(ImmutableList.of(2)));
    assertThat(initRowThree(metadataTwoKeys).getKey(), equalTo(ImmutableList.of(1, 3)));
  }

  @Test
  public void getValue() {
    ImmutableMap<String, ColumnMetadata> metadataNoValues =
        initMetadataThree(false, false, false, false, false, false);
    ImmutableMap<String, ColumnMetadata> metadataOneValue =
        initMetadataThree(false, false, false, false, true, false);
    ImmutableMap<String, ColumnMetadata> metadataTwoValues =
        initMetadataThree(true, false, true, true, false, true);

    assertThat(initRowThree(metadataNoValues).getValue(), equalTo(ImmutableList.of()));
    assertThat(initRowThree(metadataOneValue).getValue(), equalTo(ImmutableList.of(2)));
    assertThat(initRowThree(metadataTwoValues).getValue(), equalTo(ImmutableList.of(1, 3)));
  }

  @Test
  public void testOfCorrect() {
    assertThat(
        Row.of(initMetadata(Schema.INTEGER)),
        equalTo(Row.builder(initMetadata(Schema.INTEGER)).build()));
    assertThat(
        Row.of(initMetadata(Schema.INTEGER, "a"), "a", 5),
        equalTo(Row.builder(initMetadata(Schema.INTEGER, "a")).put("a", 5).build()));
    assertThat(
        Row.of(initMetadata(Schema.INTEGER, "a", "b"), "a", 5, "b", 7),
        equalTo(
            Row.builder(initMetadata(Schema.INTEGER, "a", "b")).put("a", 5).put("b", 7).build()));
  }

  @Test
  public void testOfOddElements() {
    _thrown.expect(IllegalArgumentException.class);
    _thrown.expectMessage("expecting an even number of parameters, not 1");
    Row.of(initMetadata(Schema.INTEGER), "a");
  }

  @Test
  public void testOfArgumentsWrong() {
    _thrown.expect(IllegalArgumentException.class);
    _thrown.expectMessage("argument 2 must be a string, but is: 7");
    Row.of(initMetadata(Schema.INTEGER, "a", "b"), "a", 5, 7, "b");
  }
}
