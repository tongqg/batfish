package org.batfish.datamodel.matchers;

import static org.hamcrest.Matchers.equalTo;

import javax.annotation.Nonnull;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.matchers.RowMatchersImpl.HasColumn;
import org.batfish.datamodel.table.Row;
import org.hamcrest.Matcher;

public final class RowMatchers {

  /**
   * Provides a matcher that matches if the {@link Row} has a column whose name is matched by {@code
   * keyMatcher} and whose value is matched by {@code valueMatcher}. Caller should pass in a {@link
   * Schema} suitable for use by {@link Row#get(String, Schema)}.
   */
  public static @Nonnull <V> Matcher<? super Row> hasColumn(
      @Nonnull Matcher<? super String> keyMatcher,
      @Nonnull Matcher<? super V> valueMatcher,
      @Nonnull Schema schema) {
    return new HasColumn<>(keyMatcher, valueMatcher, schema);
  }

  /**
   * Provides a matcher that matches if the {@link Row} has a column named {@code key} whose value
   * is matched by {@code valueMatcher}. Caller should pass in a {@link Schema} suitable for use by
   * {@link Row#get(String, Schema)}.
   */
  public static @Nonnull <V> Matcher<? super Row> hasColumn(
      String key, Matcher<? super V> valueMatcher, Schema schema) {
    return new HasColumn<>(equalTo(key), valueMatcher, schema);
  }

  private RowMatchers() {}
}
