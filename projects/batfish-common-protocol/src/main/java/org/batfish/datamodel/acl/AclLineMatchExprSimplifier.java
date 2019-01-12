package org.batfish.datamodel.acl;

import static com.google.common.collect.Ordering.natural;
import static org.batfish.datamodel.acl.AclLineMatchExprs.match;
import static org.batfish.datamodel.acl.AclLineMatchExprs.matchDst;
import static org.batfish.datamodel.acl.AclLineMatchExprs.matchSrc;
import static org.batfish.datamodel.acl.AclLineMatchExprs.not;

import com.google.common.collect.ImmutableSortedSet;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.IpSpace;

public class AclLineMatchExprSimplifier {
  private static final GenericAclLineMatchExprVisitor<AclLineMatchExpr> VISITOR =
      new GenericAclLineMatchExprVisitor<AclLineMatchExpr>() {
        @Override
        public AclLineMatchExpr visitAndMatchExpr(AndMatchExpr andMatchExpr) {
          return new AndMatchExpr(
              andMatchExpr
                  .getConjuncts()
                  .stream()
                  .map(this::visit)
                  .collect(ImmutableSortedSet.toImmutableSortedSet(natural())));
        }

        @Override
        public AclLineMatchExpr visitFalseExpr(FalseExpr falseExpr) {
          return falseExpr;
        }

        @Override
        public AclLineMatchExpr visitMatchHeaderSpace(MatchHeaderSpace matchHeaderSpace) {
          HeaderSpace hs = matchHeaderSpace.getHeaderspace();

          IpSpace notDstIps = hs.getNotDstIps();
          IpSpace notSrcIps = hs.getNotSrcIps();

          if (notDstIps == null || notSrcIps == null) {
            return matchHeaderSpace;
          }

          ImmutableSortedSet.Builder<AclLineMatchExpr> conjunctsBuilder =
              ImmutableSortedSet.naturalOrder();
          if (notDstIps != null) {
            conjunctsBuilder.add(not(matchDst(notDstIps)));
          }
          if (notSrcIps != null) {
            conjunctsBuilder.add(not(matchSrc(notSrcIps)));
          }
          IpSpace nullIpSpace = null;
          conjunctsBuilder.add(
              match(hs.toBuilder().setNotDstIps(nullIpSpace).setNotSrcIps(nullIpSpace).build()));

          return new AndMatchExpr(conjunctsBuilder.build());
        }

        @Override
        public AclLineMatchExpr visitMatchSrcInterface(MatchSrcInterface matchSrcInterface) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitNotMatchExpr(NotMatchExpr notMatchExpr) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitOriginatingFromDevice(
            OriginatingFromDevice originatingFromDevice) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitOrMatchExpr(OrMatchExpr orMatchExpr) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitPermittedByAcl(PermittedByAcl permittedByAcl) {
          return null;
        }

        @Override
        public AclLineMatchExpr visitTrueExpr(TrueExpr trueExpr) {
          return null;
        }
      };
}
