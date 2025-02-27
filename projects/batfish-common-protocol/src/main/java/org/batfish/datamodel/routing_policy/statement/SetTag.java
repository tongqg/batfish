package org.batfish.datamodel.routing_policy.statement;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.datamodel.routing_policy.Environment;
import org.batfish.datamodel.routing_policy.Result;
import org.batfish.datamodel.routing_policy.expr.LongExpr;

@ParametersAreNonnullByDefault
public final class SetTag extends Statement {
  private static final String PROP_TAG = "tag";

  @Nonnull private final LongExpr _tag;

  @JsonCreator
  private static SetTag jsonCreator(@Nullable @JsonProperty(PROP_TAG) LongExpr expr) {
    checkArgument(expr != null, "%s must be provided", PROP_TAG);
    return new SetTag(expr);
  }

  public SetTag(LongExpr expr) {
    _tag = expr;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (!(obj instanceof SetTag)) {
      return false;
    }
    SetTag other = (SetTag) obj;
    return _tag.equals(other._tag);
  }

  @Override
  public Result execute(Environment environment) {
    Result result = new Result();
    long tag = _tag.evaluate(environment);
    environment.getOutputRoute().setTag(tag);
    if (environment.getWriteToIntermediateBgpAttributes()) {
      environment.getIntermediateBgpAttributes().setTag(tag);
    }
    return result;
  }

  @JsonProperty(PROP_TAG)
  @Nonnull
  public LongExpr getTag() {
    return _tag;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + _tag.hashCode();
    return result;
  }
}
