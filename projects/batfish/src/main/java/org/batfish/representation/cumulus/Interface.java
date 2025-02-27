package org.batfish.representation.cumulus;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.ConcreteInterfaceAddress;

/** A physical or logical interface */
public class Interface implements Serializable {

  static final String NULL_INTERFACE_NAME = "null0";

  private @Nullable String _alias;
  private final @Nonnull InterfaceBridgeSettings _bridge;
  private @Nullable InterfaceClagSettings _clag;
  private final @Nullable Integer _encapsulationVlan;
  private final @Nonnull List<ConcreteInterfaceAddress> _ipAddresses;
  private final @Nonnull String _name;
  private @Nullable Integer _speed;
  private final @Nullable String _superInterfaceName;
  private final @Nonnull CumulusInterfaceType _type;
  private @Nullable String _vrf;

  /**
   * Construct an Interface
   *
   * @param name Name of the interface
   * @param type Type of the interface
   * @param superInterfaceName Name of the super interface if this is a subinterface, or else {@code
   *     null}
   * @param encapsulationVlan Encapsulation VLAN number for this interface if this is a
   *     subinterface, or else {@code null}
   */
  public Interface(
      String name,
      CumulusInterfaceType type,
      @Nullable String superInterfaceName,
      @Nullable Integer encapsulationVlan) {
    _name = name;
    _bridge = new InterfaceBridgeSettings();
    _ipAddresses = new LinkedList<>();
    _type = type;
    _superInterfaceName = superInterfaceName;
    _encapsulationVlan = encapsulationVlan;
  }

  /** Interface alias (description) */
  @Nullable
  public String getAlias() {
    return _alias;
  }

  public @Nonnull InterfaceBridgeSettings getBridge() {
    return _bridge;
  }

  public @Nullable InterfaceClagSettings getClag() {
    return _clag;
  }

  public @Nullable Integer getEncapsulationVlan() {
    return _encapsulationVlan;
  }

  public @Nonnull List<ConcreteInterfaceAddress> getIpAddresses() {
    return _ipAddresses;
  }

  public @Nonnull String getName() {
    return _name;
  }

  public @Nonnull InterfaceClagSettings getOrInitClag() {
    if (_clag == null) {
      _clag = new InterfaceClagSettings();
    }
    return _clag;
  }

  public void setAlias(@Nullable String alias) {
    _alias = alias;
  }

  /** Speed in Mbps */
  @Nullable
  public Integer getSpeed() {
    return _speed;
  }

  /** Set speed (assumed to be in Mbps) */
  public void setSpeed(@Nullable Integer speed) {
    _speed = speed;
  }

  public @Nullable String getSuperInterfaceName() {
    return _superInterfaceName;
  }

  public @Nonnull CumulusInterfaceType getType() {
    return _type;
  }

  public @Nullable String getVrf() {
    return _vrf;
  }

  public void setVrf(@Nullable String vrf) {
    _vrf = vrf;
  }
}
