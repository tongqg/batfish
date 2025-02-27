package org.batfish.datamodel.routing_policy.expr;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.routing_policy.Environment;
import org.batfish.datamodel.routing_policy.Result;

@ParametersAreNonnullByDefault
public final class HasRoute extends BooleanExpr {
  private static final String PROP_EXPR = "expr";

  @Nonnull private final PrefixSetExpr _expr;

  @JsonCreator
  private static HasRoute jsonCreator(@Nullable @JsonProperty(PROP_EXPR) PrefixSetExpr expr) {
    checkArgument(expr != null, "%s must be provided", PROP_EXPR);
    return new HasRoute(expr);
  }

  public HasRoute(PrefixSetExpr expr) {
    _expr = expr;
  }

  @Override
  public Result evaluate(Environment environment) {
    throw new BatfishException("No implementation for HasRoute.evaluate()");
  }

  @JsonProperty(PROP_EXPR)
  @Nonnull
  public PrefixSetExpr getExpr() {
    return _expr;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    } else if (!(obj instanceof HasRoute)) {
      return false;
    }
    HasRoute other = (HasRoute) obj;
    return Objects.equals(_expr, other._expr);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_expr);
  }
}
