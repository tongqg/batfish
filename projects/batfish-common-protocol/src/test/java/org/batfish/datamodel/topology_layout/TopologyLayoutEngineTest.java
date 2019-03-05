package org.batfish.datamodel.topology_layout;

import org.batfish.common.BatfishException;
import org.batfish.specifier.IpSpaceSpecifierFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Tests for {@link org.batfish.datamodel.topology_layout.TopologyLayoutEngine} */
public class TopologyLayoutEngineTest {

  @Rule public final ExpectedException exception = ExpectedException.none();

  @Test
  public void testLoadUnknown() {
    exception.expect(BatfishException.class);
    IpSpaceSpecifierFactory.load("");
  }
}
