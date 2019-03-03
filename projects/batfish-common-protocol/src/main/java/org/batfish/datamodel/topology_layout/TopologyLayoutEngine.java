package org.batfish.datamodel.topology_layout;

import com.google.common.collect.Multimap;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import org.batfish.role.NodeRoleDimension;

/** An interface offered by classes that provide topology layout services. */
public interface TopologyLayoutEngine {
  /** Returns the first LayoutEngine found */
  static Optional<TopologyLayoutEngine> load() {
    for (TopologyLayoutEngine engine : ServiceLoader.load(TopologyLayoutEngine.class)) {
      return Optional.of(engine);
    }
    return Optional.empty();
  }

  /** Returns the first LayoutEngine with name {@code name}. */
  static Optional<TopologyLayoutEngine> load(String name) {
    for (TopologyLayoutEngine engine : ServiceLoader.load(TopologyLayoutEngine.class)) {
      if (engine.getName().equals(name)) {
        return Optional.of(engine);
      }
    }
    return Optional.empty();
  }

  String getName();

  /**
   * Compute {@link TopologyPositions} using the supplied input.
   *
   * @param nodes The set of node names
   * @param edges The set of edges names
   * @param roleDimensions The order in which to consider node role dimensions if the layout is
   *     hierarchical
   */
  TopologyPositions compute(
      Set<String> nodes, Multimap<String, String> edges, List<NodeRoleDimension> roleDimensions);
}
