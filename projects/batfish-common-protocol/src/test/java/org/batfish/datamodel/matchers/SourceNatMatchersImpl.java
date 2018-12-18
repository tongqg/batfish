package org.batfish.datamodel.matchers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.SourceNat;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;

final class SourceNatMatchersImpl {
  private SourceNatMatchersImpl() {}

  static final class HasAclName extends FeatureMatcher<SourceNat, String> {
    HasAclName(@Nonnull Matcher<? super String> subMatcher) {
      super(subMatcher, "aclName", "aclName");
    }

    @Nullable
    @Override
    protected String featureValueOf(SourceNat actual) {
      if (actual.getAcl() != null) {
        return actual.getAcl().getName();
      }
      return null;
    }
  }

  static final class HasPoolIpFirst extends FeatureMatcher<SourceNat, Ip> {
    HasPoolIpFirst(@Nonnull Matcher<? super Ip> subMatcher) {
      super(subMatcher, "poolIpFirst", "poolIpFirst");
    }

    @Override
    protected Ip featureValueOf(SourceNat actual) {
      return actual.getPoolIpFirst();
    }
  }

  static final class HasPoolIpLast extends FeatureMatcher<SourceNat, Ip> {
    HasPoolIpLast(@Nonnull Matcher<? super Ip> subMatcher) {
      super(subMatcher, "poolIpLast", "poolIpLast");
    }

    @Override
    protected Ip featureValueOf(SourceNat actual) {
      return actual.getPoolIpLast();
    }
  }
}
