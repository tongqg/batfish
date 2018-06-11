package org.batfish.datamodel.table;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.datamodel.answers.AnswerSummary;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.Assertion;
import org.batfish.datamodel.questions.Assertion.AssertionType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Tests for {@link TableAnswerElement} */
public class TableAnswerElementTest {

  @Rule public ExpectedException _thrown = ExpectedException.none();

  private TableMetadata defaultMetadata() {
    return new TableMetadata(
        ImmutableList.of(new ColumnMetadata("col", Schema.INTEGER, "desc")), null);
  }

  /** Does computerSummary compute the correct summary? */
  @Test
  public void testComputeSummary() {
    // generate an answer with two rows
    TableAnswerElement answer = new TableAnswerElement(defaultMetadata());
    ImmutableMap<String, ColumnMetadata> columnMap =
        TableMetadata.toColumnMap(answer.getMetadata().getColumnMetadata());

    answer.addRow(Row.of(columnMap, "col", 1));
    answer.addRow(Row.of(columnMap, "col", 2));

    Assertion assertion = new Assertion(AssertionType.countequals, new IntNode(1)); // wrong count
    AnswerSummary summary = answer.computeSummary(assertion);

    assertThat(summary.getNumResults(), equalTo(2));
    assertThat(summary.getNumFailed(), equalTo(1));
    assertThat(summary.getNumPassed(), equalTo(0));
  }

  /** Does evaluateAssertion do the right thing for counting assertions? */
  @Test
  public void testEvaluateAssertionCount() throws IOException {
    Assertion twoCount = new Assertion(AssertionType.countequals, new IntNode(2));

    ImmutableMap<String, ColumnMetadata> columnMap =
        TableMetadata.toColumnMap(defaultMetadata().getColumnMetadata());

    TableAnswerElement oneRow = new TableAnswerElement(defaultMetadata());
    oneRow.addRow(Row.of(columnMap));

    TableAnswerElement twoRows = new TableAnswerElement(defaultMetadata());
    twoRows.addRow(Row.of(columnMap, "col", 1));
    twoRows.addRow(Row.of(columnMap, "col", 2));

    assertThat(oneRow.evaluateAssertion(twoCount), equalTo(false));
    assertThat(twoRows.evaluateAssertion(twoCount), equalTo(true));
  }

  /** Does evaluateAssertion do the right thing for equality assertions? */
  @Test
  public void testEvaluateAssertionEqualsFalse() throws IOException {
    Assertion assertion =
        new Assertion(
            AssertionType.equals,
            BatfishObjectMapper.mapper()
                .readValue("[{\"key1\": \"value1\"}, {\"key2\": \"value2\"}]", JsonNode.class));

    TableMetadata tableMetadata =
        new TableMetadata(
            ImmutableList.of(
                new ColumnMetadata("key1", Schema.STRING, "desc1"),
                new ColumnMetadata("key2", Schema.STRING, "desc2")),
            null);
    ImmutableMap<String, ColumnMetadata> columnMap =
        TableMetadata.toColumnMap(tableMetadata.getColumnMetadata());

    // adding rows in different order shouldn't matter
    TableAnswerElement otherRows = new TableAnswerElement(tableMetadata);
    otherRows.addRow(Row.of(columnMap, "key2", "value2"));
    otherRows.addRow(Row.of(columnMap, "key1", "value1"));

    assertThat(otherRows.evaluateAssertion(assertion), equalTo(true));

    // adding another duplicate row should matter
    otherRows.addRow(Row.of(columnMap, "key1", "value1"));

    assertThat(otherRows.evaluateAssertion(assertion), equalTo(false));
  }
}
