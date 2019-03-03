package org.batfish.datamodel.topology_layout;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.batfish.datamodel.answers.AnswerElement;

public class TopologyPositions extends AnswerElement {

  public static class Coordinates {
    @JsonProperty("fx")
    private final double _fx;

    @JsonProperty("fy")
    private final double _fy;

    /** Paired "x,y" positions */
    public Coordinates(String positions) {
      String[] coordinates = positions.split(",");
      checkArgument(coordinates.length == 2, "Illegal positions string " + positions);
      _fx = Double.parseDouble(coordinates[0]);
      _fy = Double.parseDouble(coordinates[1]);
    }
  }

  private final Map<String, Coordinates> _coordinates;

  public TopologyPositions(Map<String, Coordinates> coordinates) {
    _coordinates = ImmutableMap.copyOf(coordinates);
  }

  public Map<String, Coordinates> getCoordinates() {
    return _coordinates;
  }
}
