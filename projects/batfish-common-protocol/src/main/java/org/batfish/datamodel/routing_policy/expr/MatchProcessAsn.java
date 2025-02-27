package org.batfish.datamodel.routing_policy.expr;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.batfish.datamodel.EigrpRoute;
import org.batfish.datamodel.routing_policy.Environment;
import org.batfish.datamodel.routing_policy.Result;

/**
 * Boolean expression that evaluates whether an {@link Environment} has an EIGRP route with an ASN
 * equal to some given ASN.
 */
public final class MatchProcessAsn extends BooleanExpr {

  private static final String PROP_PROCESS_ASN = "asn";

  private final long _asn;

  @JsonCreator
  public MatchProcessAsn(@JsonProperty(PROP_PROCESS_ASN) long asn) {
    _asn = asn;
  }

  @Override
  public Result evaluate(Environment environment) {
    EigrpRoute route = (EigrpRoute) environment.getOriginalRoute();
    return new Result(route.getProcessAsn() == _asn);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MatchProcessAsn)) {
      return false;
    }
    return _asn == ((MatchProcessAsn) obj)._asn;
  }

  @Override
  public int hashCode() {
    return Objects.hash(_asn);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "<" + _asn + ">";
  }
}
