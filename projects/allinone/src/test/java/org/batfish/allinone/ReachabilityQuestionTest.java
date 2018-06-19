package org.batfish.allinone;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.SortedSet;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.FlowHistory;
import org.batfish.datamodel.ReachabilityType;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.main.Batfish;
import org.batfish.question.ReachabilityQuestionPlugin.ReachabilityAnswerer;
import org.batfish.question.ReachabilityQuestionPlugin.ReachabilityQuestion;
import org.batfish.utils.BatfishTestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReachabilityQuestionTest {

  @Rule public TemporaryFolder _testFolder = new TemporaryFolder();;

  @Rule public TemporaryFolder _baseFolder = new TemporaryFolder();;

  @Rule public TemporaryFolder _deltaFolder = new TemporaryFolder();;

  private String[] _configurationNames =
      new String[] {
        "as1border1.cfg",
        "as1border2.cfg",
        "as1core1.cfg",
        "as2border1.cfg",
        "as2border2.cfg",
        "as2core1.cfg",
        "as2core2.cfg",
        "as2dept1.cfg",
        "as2dist1.cfg",
        "as2dist2.cfg",
        "as3border1.cfg",
        "as3border2.cfg",
        "as3core1.cfg",
      };

  @Test
  public void testStandardReachability() throws IOException {
    // setup
    SortedMap<String, Configuration> baseConfigs =
        getConfigurations("example", _configurationNames, _baseFolder);
    Batfish batfish = BatfishTestUtils.getBatfish(baseConfigs, _testFolder);
    batfish.computeDataPlane(false);

    // creating the question
    ReachabilityQuestion reachabilityQuestion = new ReachabilityQuestion();
    reachabilityQuestion.setReachabilityType(ReachabilityType.STANDARD);
    reachabilityQuestion.setIngressNodeRegex("as1border1");

    // getting the answer
    ReachabilityAnswerer reachabilityAnswerer =
        new ReachabilityAnswerer(reachabilityQuestion, batfish);
    AnswerElement answerElement = reachabilityAnswerer.answer();

    // validating the answer
    assertThat(answerElement, instanceOf(FlowHistory.class));
    FlowHistory flowHistory = (FlowHistory) answerElement;

    SortedSet<String> ingressNodeNames =
        flowHistory
            .getTraces()
            .values()
            .stream()
            .map(flowHistoryInfo -> flowHistoryInfo.getFlow().getIngressNode())
            .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));

    // flows in the result should only have as1border1 as the ingressNode
    assertThat(ingressNodeNames, hasSize(1));
    assertThat(ingressNodeNames, hasItem("as1border1"));
  }

  @Test
  public void testReducedReachability() throws IOException {
    // setup
    SortedMap<String, Configuration> baseConfigs =
        getConfigurations("example", _configurationNames, _baseFolder);
    SortedMap<String, Configuration> deltaConfigs =
        getConfigurations("example-with-delta", _configurationNames, _deltaFolder);

    Batfish batfish = BatfishTestUtils.getBatfish(baseConfigs, deltaConfigs, _testFolder);
    batfish.pushBaseEnvironment();
    batfish.computeDataPlane(false);
    batfish.popEnvironment();

    batfish.pushDeltaEnvironment();
    batfish.computeDataPlane(false);
    batfish.popEnvironment();

    batfish.checkDifferentialDataPlaneQuestionDependencies();

    // creating the question
    ReachabilityQuestion reachabilityQuestion = new ReachabilityQuestion();
    reachabilityQuestion.setReachabilityType(ReachabilityType.REDUCED_REACHABILITY);
    reachabilityQuestion.setIngressNodeRegex("as1border1");

    // getting the answer
    ReachabilityAnswerer reachabilityAnswerer =
        new ReachabilityAnswerer(reachabilityQuestion, batfish);
    AnswerElement answerElement = reachabilityAnswerer.answerDiff();

    // validating the answer
    assertThat(answerElement, instanceOf(FlowHistory.class));
    FlowHistory flowHistory = (FlowHistory) answerElement;

    SortedSet<String> ingressNodeNames =
        flowHistory
            .getTraces()
            .values()
            .stream()
            .map(flowHistoryInfo -> flowHistoryInfo.getFlow().getIngressNode())
            .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));

    // flows in the result should only have as1border1 as the ingressNode
    assertThat(ingressNodeNames, hasSize(1));
    assertThat(ingressNodeNames, hasItem("as1border1"));
  }

  /**
   * Helper to get the configuration nodes
   */
  private SortedMap<String, Configuration> getConfigurations(
      String testrigName, String[] configurationNames, TemporaryFolder folder) throws IOException {
    String testrigsPrefix = "org/batfish/allinone/testrigs/";
    return BatfishTestUtils.getBatfishFromTestrigResource(
            testrigsPrefix + testrigName, configurationNames, null, null, null, null, folder)
        .loadConfigurations();
  }
}
