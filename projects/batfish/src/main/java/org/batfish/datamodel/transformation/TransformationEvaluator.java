package org.batfish.datamodel.transformation;

import static com.google.common.base.Verify.verifyNotNull;
import static org.batfish.datamodel.FlowDiff.flowDiff;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.FlowDiff;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.acl.Evaluator;
import org.batfish.datamodel.flow.Step;
import org.batfish.datamodel.flow.StepAction;
import org.batfish.datamodel.flow.TransformationStep.TransformationStepDetail;
import org.batfish.datamodel.flow.TransformationStep.TransformationType;

/** Evaluates a {@link Transformation} on an input {@link Flow}. */
public class TransformationEvaluator {
  private final String _srcInterface;
  private final Map<String, IpAccessList> _namedAcls;
  private final Map<String, IpSpace> _namedIpSpaces;

  /**
   * StepEvaluator returns true iff the step transforms the packet. It's possible that the step
   * matches but doesn't transform the packet (e.g. Noop). This means transformation trace steps are
   * generated even when the packet isn't transformed.
   */
  @VisibleForTesting
  static class StepEvaluator
      implements TransformationStepVisitor<
          Function<Stream<TransformationState>, Stream<TransformationState>>> {

    @Override
    public Function<Stream<TransformationState>, Stream<TransformationState>>
        visitAssignIpAddressFromPool(AssignIpAddressFromPool step) {
      return stateStream ->
          stateStream.peek(
              state -> {
                state.set(
                    step.getType(),
                    step.getIpField(),
                    state.get(step.getIpField()),
                    step.getIpRanges().asRanges().iterator().next().lowerEndpoint());
              });
    }

    @Override
    public Function<Stream<TransformationState>, Stream<TransformationState>> visitNoop(Noop noop) {
      return stateStream ->
          stateStream.peek(
              state -> {
                state.noop(noop.getType());
              });
    }

    @Override
    public Function<Stream<TransformationState>, Stream<TransformationState>>
        visitShiftIpAddressIntoSubnet(ShiftIpAddressIntoSubnet step) {
      IpField field = step.getIpField();
      return stateStream ->
          stateStream.peek(
              state -> {
                Ip oldValue = state.get(field);
                Prefix targetSubnet = step.getSubnet();
                Prefix currentSubnetPrefix =
                    Prefix.create(oldValue, targetSubnet.getPrefixLength());
                long offset = oldValue.asLong() - currentSubnetPrefix.getStartIp().asLong();
                Ip newValue = Ip.create(targetSubnet.getStartIp().asLong() + offset);
                state.set(step.getType(), field, oldValue, newValue);
              });
    }

    @Override
    public Function<Stream<TransformationState>, Stream<TransformationState>>
        visitAssignPortFromPool(AssignPortFromPool step) {
      return stateStream ->
          stateStream.peek(
              state -> {
                state.set(
                    step.getType(),
                    step.getPortField(),
                    state.get(step.getPortField()),
                    step.getPoolStart());
              });
    }

    @Override
    public Function<Stream<TransformationState>, Stream<TransformationState>> visitApplyAll(
        ApplyAll applyAll) {
      return applyAll.getSteps().stream()
          .map(this::visit)
          .reduce(Function.identity(), Function::andThen);
    }

    @Override
    public Function<Stream<TransformationState>, Stream<TransformationState>> visitApplyAny(
        ApplyAny applyAny) {
      // for each state { for each step { apply step to state }}
      return stateStream ->
          stateStream.flatMap(
              state ->
                  applyAny.getSteps().stream()
                      .flatMap(
                          step -> visit(step).apply(Stream.of(new TransformationState(state)))));
    }
  }

  private TransformationEvaluator(
      String srcInterface,
      Map<String, IpAccessList> namedAcls,
      Map<String, IpSpace> namedIpSpaces) {
    _srcInterface = srcInterface;
    _namedAcls = ImmutableMap.copyOf(namedAcls);
    _namedIpSpaces = ImmutableMap.copyOf(namedIpSpaces);
  }

  /** The result of evaluating a {@link Transformation}. */
  public static final class TransformationResult {
    private final Flow _outputFlow;
    private final List<Step<?>> _traceSteps;

    TransformationResult(Flow outputFlow, List<Step<?>> traceSteps) {
      _outputFlow = outputFlow;
      _traceSteps = ImmutableList.copyOf(traceSteps);
    }

    public Flow getOutputFlow() {
      return _outputFlow;
    }

    public List<Step<?>> getTraceSteps() {
      return _traceSteps;
    }
  }

  private static final class TransformationState {
    private final Function<Flow, Evaluator> _mkEvaluator;
    private Flow.Builder _flowBuilder;
    private Flow _currentFlow;
    private Evaluator _aclEvaluator;
    private final ImmutableList.Builder<Step<?>> _traceSteps;
    private final Map<TransformationType, ImmutableSortedSet.Builder<FlowDiff>> _flowDiffs;

    TransformationState(Flow currentFlow, Function<Flow, Evaluator> mkEvaluator) {
      _currentFlow = currentFlow;
      _flowBuilder = currentFlow.toBuilder();
      _traceSteps = ImmutableList.builder();
      _mkEvaluator = mkEvaluator;
      _aclEvaluator = _mkEvaluator.apply(currentFlow);
      _flowDiffs = new EnumMap<>(TransformationType.class);
    }

    TransformationState(TransformationState other) {
      _currentFlow = other._currentFlow;
      _flowBuilder = _currentFlow.toBuilder();
      _mkEvaluator = other._mkEvaluator;
      _aclEvaluator = other._aclEvaluator;
      _traceSteps = ImmutableList.builder();
      _traceSteps.addAll(other._traceSteps.build());
      _flowDiffs = new EnumMap<>(other._flowDiffs);
    }

    private void set(IpField field, Ip ip) {
      switch (field) {
        case DESTINATION:
          _flowBuilder.setDstIp(ip);
          break;
        case SOURCE:
          _flowBuilder.setSrcIp(ip);
          break;
        default:
          throw new IllegalArgumentException("unknown IpField " + field);
      }
    }

    void set(PortField field, int port) {
      switch (field) {
        case DESTINATION:
          _flowBuilder.setDstPort(port);
          break;
        case SOURCE:
          _flowBuilder.setSrcPort(port);
          break;
        default:
          throw new IllegalArgumentException("unknown PortField " + field);
      }
    }

    Ip get(IpField field) {
      switch (field) {
        case DESTINATION:
          return _flowBuilder.getDstIp();
        case SOURCE:
          return _flowBuilder.getSrcIp();
        default:
          throw new IllegalArgumentException("unknown IpField " + field);
      }
    }

    int get(PortField field) {
      switch (field) {
        case DESTINATION:
          return verifyNotNull(_flowBuilder.getDstPort(), "Missing destination port");
        case SOURCE:
          return verifyNotNull(_flowBuilder.getSrcPort(), "Missing source port");
        default:
          throw new IllegalArgumentException("unknown PortField " + field);
      }
    }

    void set(TransformationType type, IpField ipField, Ip oldValue, Ip newValue) {
      if (oldValue.equals(newValue)) {
        noop(type);
      } else {
        set(ipField, newValue);
        getFlowDiffs(type).add(flowDiff(ipField, oldValue, newValue));
      }
    }

    void set(TransformationType type, PortField portField, int oldValue, int newValue) {
      if (oldValue == newValue) {
        noop(type);
      } else {
        set(portField, newValue);
        getFlowDiffs(type).add(flowDiff(portField, oldValue, newValue));
      }
    }

    // when no values change, simply apply noop
    private void noop(TransformationType type) {
      getFlowDiffs(type);
    }

    private ImmutableSortedSet.Builder<FlowDiff> getFlowDiffs(TransformationType type) {
      return _flowDiffs.computeIfAbsent(type, k -> ImmutableSortedSet.naturalOrder());
    }

    public void buildTraceSteps() {
      AtomicReference<Boolean> transformed = new AtomicReference<>(false);

      _flowDiffs.entrySet().stream()
          .map(
              entry -> {
                ImmutableSortedSet<FlowDiff> flowDiffs = entry.getValue().build();
                if (!flowDiffs.isEmpty()) {
                  transformed.set(true);
                }
                TransformationStepDetail detail =
                    new TransformationStepDetail(entry.getKey(), flowDiffs);
                StepAction action =
                    flowDiffs.isEmpty() ? StepAction.PERMITTED : StepAction.TRANSFORMED;
                return new org.batfish.datamodel.flow.TransformationStep(detail, action);
              })
          .forEach(_traceSteps::add);

      if (transformed.get()) {
        _currentFlow = _flowBuilder.build();
        _aclEvaluator = _mkEvaluator.apply(_currentFlow);
      }

      _flowDiffs.clear();
    }
  }

  public static Stream<TransformationResult> evalAll(
      Transformation transformation,
      Flow flow,
      String srcInterface,
      Map<String, IpAccessList> namedAcls,
      Map<String, IpSpace> namedIpSpaces) {
    TransformationEvaluator evaluator =
        new TransformationEvaluator(srcInterface, namedAcls, namedIpSpaces);
    return evaluator.eval(transformation, flow);
  }

  public static TransformationResult eval(
      Transformation transformation,
      Flow flow,
      String srcInterface,
      Map<String, IpAccessList> namedAcls,
      Map<String, IpSpace> namedIpSpaces) {
    Stream<TransformationResult> results =
        evalAll(transformation, flow, srcInterface, namedAcls, namedIpSpaces);

    return results.limit(1).findFirst().get();
    /*
    // horrible hack to avoid evaluating the entire stream (findFirst and findAny don't work)
    AtomicReference<TransformationResult> result = new AtomicReference<>();
    try {
      results.forEach(
          r -> {
            result.set(r);
            throw new BatfishException("");
          });
    } catch (BatfishException e) {
      return result.get();
    }

    return results.findFirst().get();
    */
  }

  private Stream<TransformationResult> eval(Transformation transformation, Flow inputFlow) {
    Stream<TransformationState> states =
        Stream.of(
            new TransformationState(
                inputFlow, flow -> new Evaluator(flow, _srcInterface, _namedAcls, _namedIpSpaces)));

    return eval(transformation, states)
        .map(
            state -> {
              TransformationResult result =
                  new TransformationResult(state._currentFlow, state._traceSteps.build());
              return result;
            });
  }

  private static Stream<TransformationState> eval(
      Transformation transformation, Stream<TransformationState> states) {
    if (transformation == null) {
      return states;
    }
    return states.flatMap(
        state -> {
          if (state._aclEvaluator.visit(transformation.getGuard())) {
            Function<Stream<TransformationState>, Stream<TransformationState>> stepsFunction =
                transformation.getTransformationSteps().stream()
                    .map(new StepEvaluator()::visit)
                    .reduce(Function.identity(), Function::andThen);
            Stream<TransformationState> stateStream =
                stepsFunction.apply(Stream.of(state)).peek(TransformationState::buildTraceSteps);
            return eval(transformation.getAndThen(), stateStream);
          } else {
            return eval(transformation.getOrElse(), Stream.of(state));
          }
        });
  }
}
